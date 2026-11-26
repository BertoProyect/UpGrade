package com.alberto.upgrade.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Lo que va llegando de la IA mientras organiza. */
sealed interface AiEvent {
    data class Transcript(val text: String) : AiEvent
    data class Item(val p: Proposal) : AiEvent
    /** Respuesta en texto a una pregunta o a algo que no es una tarea. */
    data class Answer(val text: String) : AiEvent
    /** Terminado. [usedAi] = false si se ha usado el modo básico. */
    data class Done(val usedAi: Boolean, val note: String?) : AiEvent
}

/** Lo que va llegando de la IA general. */
sealed interface ChatEvent {
    /** La respuesta entera hasta ahora (crece con cada trozo). */
    data class Answer(val text: String) : ChatEvent
    data class Item(val p: Proposal) : ChatEvent
    data class Done(val ok: Boolean, val note: String?) : ChatEvent
}

/** Error explicado en español, listo para enseñar. */
class AiError(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { INVALID_KEY, QUOTA, NETWORK, TIMEOUT, SERVER, BAD_REQUEST, MISSING_MODEL, OTHER }
}

enum class KeyCheck(val label: String) {
    VALID("Válida"), INVALID("No es válida"), NO_QUOTA("Sin cuota ahora mismo"), OFFLINE("Sin conexión"), ERROR("No se ha podido comprobar")
}

/** Una sesión propuesta para el plan de repaso. */
data class StudySlot(val date: LocalDate, val time: Int)

/**
 * Cliente de Gemini (clave del propio usuario, cifrada en el móvil).
 *
 * Rápido por diseño: modelo Flash-Lite con razonamiento mínimo, respuesta en streaming
 * (cada propuesta sale en cuanto llega su objeto JSON), instrucción de sistema fija y
 * conexiones HTTP reutilizadas (no se llama a disconnect(), para que el socket vuelva al pool).
 */
object Gemini {
    private const val HOST = "https://generativelanguage.googleapis.com"
    const val TRANSCRIBE_MODEL = "gemini-3.5-transcribe"
    /** Respaldo rápido y estable si el modelo elegido tarda o falla (el 2.5 Flash-Lite se apagó en octubre de 2026). */
    const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
    /** Más respaldos, por orden. Si ninguno existe, se pregunta a Google qué modelos hay. */
    private val BACKUPS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.6-flash", "gemini-3.5-flash", "gemini-flash-lite-latest", "gemini-flash-latest")
    /** Modelo que Google dijo que existe (se descubre la primera vez que falta uno). */
    @Volatile private var discovered: String? = null
    /** Modelos que han dado 404 en esta sesión: no se vuelven a probar. */
    private val missing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private const val CONNECT_MS = 8_000
    private const val READ_MS = 20_000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun model(d: AppData): String = d.aiModel.ifBlank { DEFAULT_AI_MODEL }
    private fun key(): String = SecretStore.geminiKey

    // ------------------------------------------------------------------
    // Organizar: texto o audio → propuestas en streaming
    // ------------------------------------------------------------------

    /**
     * Organiza [text] (o el audio [audio], AAC) y emite las propuestas una a una.
     * Sin clave usa el modo básico. Si la IA falla, cae al modo básico con el texto que haya.
     */
    fun organize(d: AppData, text: String?, audio: ByteArray? = null, today: LocalDate = LocalDate.now()): Flow<AiEvent> = flow {
        val t0 = System.currentTimeMillis()
        var first = -1L
        var count = 0
        val mode = if (audio != null) "voz" else "texto"
        var model = model(d)

        if (!SecretStore.hasGeminiKey()) {
            val items = LocalParser.parse(d, text.orEmpty(), today)
            items.forEach { emit(AiEvent.Item(it)) }
            emit(AiEvent.Done(false, "Modo básico (sin IA). Pon tu clave de Gemini en Ajustes para que entienda frases largas y dictado."))
            return@flow
        }

        var transcript: String? = null
        var ok = false
        try {
            var input = text
            if (audio != null) {
                // 1) Voz → texto con el modelo de transcripción (es lo más rápido y lo que mejor entiende).
                val tr = transcribe(d, audio)
                if (tr.isBlank()) {
                    emit(AiEvent.Done(false, "No te he entendido. Prueba otra vez o escríbelo."))
                    return@flow
                }
                transcript = tr
                emit(AiEvent.Transcript(tr))
                input = tr
            }
            // 2) Texto → propuestas en streaming.
            val ctx = PromptContext.build(d, today, includePending = looksLikeEdit(input.orEmpty()))
            val scanner = JsonItemScanner()
            model = streamWithFallback(d, { m -> organizeBody(d, ctx, input, m) }) { chunk ->
                scanner.feed(chunk)
                for (raw in scanner.drain()) {
                    val p = parseItem(d, ctx, raw, today) ?: continue
                    if (first < 0) first = System.currentTimeMillis() - t0
                    count++
                    emit(AiEvent.Item(p))
                }
            }
            ok = true
            val answer = answerOf(scanner.full())
            if (answer != null) emit(AiEvent.Answer(answer))
            emit(AiEvent.Done(true, if (count == 0 && answer == null) "No he sacado nada. Prueba con frases tipo «examen de Lengua el jueves» o pregúntame lo que quieras." else null))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = (e as? AiError)?.message ?: "La IA ha fallado."
            val fallbackText = transcript ?: text
            // Si el streaming se corta antes de dar nada, un segundo intento sin streaming (otra vía, mismo modelo
            // o el siguiente de la cadena). Solo se cae al modo básico si este también falla.
            val retryable = e is AiError && e.kind !in setOf(AiError.Kind.INVALID_KEY, AiError.Kind.QUOTA, AiError.Kind.NETWORK)
            if (count == 0 && retryable && !fallbackText.isNullOrBlank()) {
                val retried = runCatching {
                    val ctx2 = PromptContext.build(d, today, includePending = looksLikeEdit(fallbackText))
                    val (full, used) = generateWithFallback(d) { m -> organizeBody(d, ctx2, fallbackText, m) }
                    model = used
                    val sc = JsonItemScanner().apply { feed(full) }
                    val items = sc.drain().mapNotNull { parseItem(d, ctx2, it, today) }
                    items to answerOf(full)
                }.getOrNull()
                if (retried != null && (retried.first.isNotEmpty() || retried.second != null)) {
                    retried.first.forEach { if (first < 0) first = System.currentTimeMillis() - t0; count++; emit(AiEvent.Item(it)) }
                    retried.second?.let { emit(AiEvent.Answer(it)) }
                    ok = true
                    emit(AiEvent.Done(true, null))
                    return@flow
                }
            }
            if (count == 0 && !fallbackText.isNullOrBlank()) {
                // Nunca te quedas sin nada: el modo básico separa lo que pueda del texto.
                LocalParser.parse(d, fallbackText, today).forEach { emit(AiEvent.Item(it)) }
                emit(AiEvent.Done(false, "$msg He usado el modo básico."))
            } else {
                emit(AiEvent.Done(count > 0, if (count == 0) "$msg Prueba otra vez." else msg))
            }
        } finally {
            Repo.recordAiTiming(AiTiming(t0, first, System.currentTimeMillis() - t0, mode, model, ok))
        }
    }.flowOn(Dispatchers.IO)

    /** Si el mensaje parece cambiar algo que ya existe, se manda la lista de pendientes; si no, no (más rápido). */
    private fun looksLikeEdit(t: String): Boolean {
        val n = java.text.Normalizer.normalize(t.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return listOf(
            "pasa ", "pasalo", "mueve", "muevelo", "cambia", "cambialo", "quita", "quitalo", "borra", "borralo", "elimina",
            "ya he", "ya hice", "ya esta", "hecho lo", "termine", "cancela", "se cancela", "retrasa", "adelanta", "aplaza",
            "en vez de", "al final no", "lo de "
        ).any { it in n }
    }

    /** Modelos a probar, por orden: el elegido, los de respaldo y el descubierto. Sin los que ya dieron 404. */
    private fun chain(d: AppData): List<String> =
        (listOf(model(d)) + BACKUPS + listOfNotNull(discovered)).distinct().filter { it !in missing }.ifEmpty { listOf(model(d)) }

    /**
     * Pregunta a Google qué modelos hay y se queda con el Flash-Lite (o Flash) más nuevo que genere texto.
     * Así un modelo apagado nunca deja la IA rota.
     */
    private fun discover(): String? {
        discovered?.let { if (it !in missing) return it }
        return runCatching {
            val conn = open("$HOST/v1beta/models?pageSize=200", "GET")
            if (conn.responseCode !in 200..299) return@runCatching null
            val root = json.parseToJsonElement(conn.inputStream.bufferedReader().use { it.readText() }).jsonObject
            val names = (root["models"] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val methods = (o["supportedGenerationMethods"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                val name = o.str("name")?.removePrefix("models/") ?: return@mapNotNull null
                name.takeIf { "generateContent" in methods && it.startsWith("gemini-") && listOf("tts", "image", "live", "embedding", "audio", "robotics").none { k -> k in it } }
            }.filter { it !in missing }
            fun version(n: String) = Regex("gemini-(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val pick = names.filter { "flash-lite" in it && "preview" !in it }.maxByOrNull { version(it) }
                ?: names.filter { "flash" in it && "preview" !in it }.maxByOrNull { version(it) }
                ?: names.filter { "flash" in it }.maxByOrNull { version(it) }
            pick
        }.getOrNull()?.also { discovered = it }
    }

    /** ¿Merece la pena probar otro modelo con este error? */
    private fun switchable(e: AiError): Boolean = e.kind in setOf(AiError.Kind.TIMEOUT, AiError.Kind.SERVER, AiError.Kind.BAD_REQUEST, AiError.Kind.MISSING_MODEL)

    /**
     * Streaming con el modelo elegido; si tarda demasiado, falla el servidor o el modelo no existe
     * (y aún no ha llegado nada), se prueba el siguiente de la cadena. Devuelve el modelo usado.
     */
    private suspend fun streamWithFallback(d: AppData, body: (String) -> JsonObject, onText: suspend (String) -> Unit): String {
        var received = false
        val track: suspend (String) -> Unit = { received = true; onText(it) }
        var last: AiError? = null
        val tried = mutableSetOf<String>()
        var list = chain(d)
        var i = 0
        while (i < list.size) {
            val m = list[i++]
            if (!tried.add(m)) continue
            try {
                stream(m, body(m), track)
                return m
            } catch (e: AiError) {
                last = e
                if (e.kind == AiError.Kind.MISSING_MODEL) missing += m
                if (received || !switchable(e)) throw e
                // Si se acaban los conocidos, se pregunta a Google por uno que exista.
                if (i >= list.size && e.kind == AiError.Kind.MISSING_MODEL) discover()?.let { if (it !in tried) list = list + it }
            }
        }
        throw last ?: AiError(AiError.Kind.OTHER, "La IA no está disponible ahora mismo.")
    }

    /** Lo mismo sin streaming. */
    private fun generateWithFallback(d: AppData, body: (String) -> JsonObject): Pair<String, String> {
        var last: AiError? = null
        val tried = mutableSetOf<String>()
        var list = chain(d)
        var i = 0
        while (i < list.size) {
            val m = list[i++]
            if (!tried.add(m)) continue
            try {
                return generate(m, body(m)) to m
            } catch (e: AiError) {
                last = e
                if (e.kind == AiError.Kind.MISSING_MODEL) missing += m
                if (!switchable(e)) throw e
                if (i >= list.size && e.kind == AiError.Kind.MISSING_MODEL) discover()?.let { if (it !in tried) list = list + it }
            }
        }
        throw last ?: AiError(AiError.Kind.OTHER, "La IA no está disponible ahora mismo.")
    }

    /** Saca «respuesta» del JSON completo, si la hay. */
    private fun answerOf(full: String): String? = runCatching {
        (json.parseToJsonElement(cleanJson(full)) as? JsonObject)?.str("respuesta")?.take(900)
    }.getOrNull()

    private fun organizeBody(d: AppData, ctx: PromptContext, text: String?, model: String): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", SYSTEM) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", ctx.render(d)) }
                    addJsonObject { put("text", "Mensaje de Alberto:\n<<<\n${text.orEmpty().trim()}\n>>>") }
                }
            }
        }
        // Sin esquema: compilar la gramática del esquema retrasa el primer token. El formato lo fijan los ejemplos.
        // 4096: con 2048 una lista larga se quedaba a medias y Gemini cortaba la respuesta.
        put("generationConfig", generationConfig(model, null, maxTokens = 4096))
    }

    /** Configuración rápida: JSON con esquema y el mínimo razonamiento que admita el modelo. */
    private fun generationConfig(model: String, schema: JsonObject?, maxTokens: Int? = null): JsonObject = buildJsonObject {
        put("responseMimeType", "application/json")
        if (schema != null) put("responseSchema", schema)
        if (maxTokens != null) put("maxOutputTokens", maxTokens)
        thinkingFor(model)?.let { put("thinkingConfig", it) }
    }

    /** Gemini 2.x usa presupuesto; los 3.x+ usan nivel. Flash-Lite y 3.6 Flash admiten "minimal". */
    private fun thinkingFor(model: String): JsonObject? {
        val m = model.lowercase()
        return when {
            m.startsWith("gemini-2") && "pro" in m -> null
            m.startsWith("gemini-2") -> buildJsonObject { put("thinkingBudget", 0) }
            "flash-lite" in m || "3.6-flash" in m || "3-flash" in m -> buildJsonObject { put("thinkingLevel", "minimal") }
            "flash" in m || "argon" in m || "gemini-4" in m -> buildJsonObject { put("thinkingLevel", "low") }
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private fun open(url: String, method: String = "POST"): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_MS
        conn.readTimeout = READ_MS
        conn.setRequestProperty("x-goog-api-key", key())
        if (method == "POST") {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return conn
    }

    private fun endpoint(model: String, action: String): String =
        "$HOST/v1beta/models/" + URLEncoder.encode(model, "UTF-8") + ":" + action

    /**
     * streamGenerateContent con SSE. Llama a [onText] con cada trozo de texto.
     * Un reintento automático solo en errores de red. Si el servidor rechaza la configuración
     * (400), se repite una vez con la configuración mínima.
     */
    private suspend fun stream(model: String, body: JsonObject, onText: suspend (String) -> Unit) {
        var attempt = 0
        var payload = body
        var simplified = false
        var received = false
        val track: suspend (String) -> Unit = { received = true; onText(it) }
        while (true) {
            try {
                streamOnce(model, payload, track)
                return
            } catch (e: AiError) {
                if (e.kind == AiError.Kind.BAD_REQUEST && !simplified) {
                    simplified = true
                    payload = simplify(payload)
                    continue
                }
                throw e
            } catch (e: IOException) {
                val err = toAiError(e)
                // Solo se reintenta si aún no ha llegado nada (si no, saldrían propuestas repetidas).
                if (err.kind == AiError.Kind.NETWORK && attempt == 0 && !received) { attempt++; continue }
                throw err
            }
        }
    }

    private suspend fun streamOnce(model: String, body: JsonObject, onText: suspend (String) -> Unit) {
        val conn = open(endpoint(model, "streamGenerateContent") + "?alt=sse")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) throw httpError(code, conn.errorStream)
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                val el = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                el["error"]?.let { err ->
                    val o = err as? JsonObject
                    val code = (o?.get("code") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                    val why = o?.str("message").orEmpty()
                    throw when {
                        code == 429 -> AiError(AiError.Kind.QUOTA, "Se ha agotado la cuota gratuita de Gemini por ahora. Prueba en un rato.")
                        code != null && code >= 500 -> AiError(AiError.Kind.SERVER, "Gemini está saturado ahora mismo.")
                        else -> AiError(AiError.Kind.SERVER, "Gemini ha cortado la respuesta" + if (why.isNotBlank()) " (" + why.take(70) + ")." else ".")
                    }
                }
                val txt = textOf(el)
                if (txt.isNotEmpty()) onText(txt)
            }
        }
    }

    /** Petición normal (sin streaming) que devuelve el texto. */
    private fun generate(model: String, body: JsonObject): String {
        var payload = body
        var simplified = false
        var attempt = 0
        while (true) {
            try {
                val conn = open(endpoint(model, "generateContent"))
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) throw httpError(code, conn.errorStream)
                val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return textOf(json.parseToJsonElement(resp).jsonObject)
            } catch (e: AiError) {
                if (e.kind == AiError.Kind.BAD_REQUEST && !simplified) { simplified = true; payload = simplify(payload); continue }
                throw e
            } catch (e: IOException) {
                val err = toAiError(e)
                if (err.kind == AiError.Kind.NETWORK && attempt == 0) { attempt++; continue }
                throw err
            }
        }
    }

    /** Quita esquema y razonamiento: lo que más a menudo rechaza un modelo distinto al previsto. */
    private fun simplify(body: JsonObject): JsonObject {
        val gc = body["generationConfig"]?.jsonObject ?: return body
        val slim = JsonObject(gc.filterKeys { it != "responseSchema" && it != "thinkingConfig" })
        return JsonObject(body + ("generationConfig" to slim))
    }

    private fun textOf(resp: JsonObject): String =
        resp["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.joinToString("") { p ->
                val o = p.jsonObject
                if ((o["thought"] as? JsonPrimitive)?.contentOrNull == "true") "" else o["text"]?.jsonPrimitive?.contentOrNull ?: ""
            } ?: ""

    private fun httpError(code: Int, err: InputStream?): AiError {
        val raw = runCatching { err?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        val msg = runCatching {
            json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty()
        val m = msg.lowercase()
        return when {
            code == 400 && ("api key" in m || "api_key" in m) -> AiError(AiError.Kind.INVALID_KEY, "La clave de Gemini no es válida. Revísala en Ajustes.")
            code == 401 || code == 403 -> AiError(AiError.Kind.INVALID_KEY, "La clave de Gemini no tiene permiso. Revísala en Ajustes.")
            code == 404 -> AiError(AiError.Kind.MISSING_MODEL, "El modelo de IA ya no existe. He probado con otros y tampoco: revisa el modelo en Ajustes › IA › Avanzado.")
            code == 429 -> AiError(AiError.Kind.QUOTA, "Se ha agotado la cuota gratuita de Gemini por ahora. Prueba en un rato.")
            code == 400 -> AiError(AiError.Kind.BAD_REQUEST, "Gemini ha rechazado la petición${if (msg.isNotBlank()) ": " + msg.take(80) else "."}")
            code >= 500 -> AiError(AiError.Kind.SERVER, "Gemini está saturado ahora mismo.")
            else -> AiError(AiError.Kind.OTHER, "Error $code de Gemini.")
        }
    }

    private fun toAiError(e: IOException): AiError = when (e) {
        is SocketTimeoutException -> AiError(AiError.Kind.TIMEOUT, "Gemini ha tardado demasiado.")
        is UnknownHostException, is ConnectException -> AiError(AiError.Kind.NETWORK, "Sin conexión.")
        else -> AiError(AiError.Kind.NETWORK, "Error de red.")
    }

    /**
     * Abre la conexión con Google mientras el usuario escribe o habla, para que la petición
     * de verdad no pague el apretón de manos TLS. Se lee la respuesta entera y no se desconecta,
     * así el socket queda en el pool para reutilizarse.
     */
    suspend fun prewarm(d: AppData) = withContext(Dispatchers.IO) {
        if (!SecretStore.hasGeminiKey()) return@withContext
        runCatching {
            val conn = open("$HOST/v1beta/models/" + URLEncoder.encode(model(d), "UTF-8"), "GET")
            val s = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            s?.use { it.readBytes() }
        }
    }

    /** Comprueba una clave con una petición mínima. */
    suspend fun checkKey(candidate: String, model: String): KeyCheck = withContext(Dispatchers.IO) {
        if (candidate.isBlank()) return@withContext KeyCheck.INVALID
        try {
            val conn = URL(endpoint(model, "generateContent")).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_MS
            conn.readTimeout = READ_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", candidate.trim())
            val body = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", "Responde solo: ok") } } }
                }
                put("generationConfig", buildJsonObject {
                    put("maxOutputTokens", 8)
                    thinkingFor(model)?.let { put("thinkingConfig", it) }
                })
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.use { it.readBytes() }
                KeyCheck.VALID
            } else {
                when (httpError(code, conn.errorStream).kind) {
                    AiError.Kind.INVALID_KEY -> KeyCheck.INVALID
                    AiError.Kind.QUOTA -> KeyCheck.NO_QUOTA
                    else -> KeyCheck.ERROR
                }
            }
        } catch (e: IOException) {
            if (toAiError(e).kind == AiError.Kind.NETWORK) KeyCheck.OFFLINE else KeyCheck.ERROR
        }
    }

    // ------------------------------------------------------------------
    // Dictado preciso: Files API + modelo de transcripción con vocabulario
    // ------------------------------------------------------------------

    /**
     * Voz → texto con el modelo de transcripción (modo inteligente: quita muletillas) y tu vocabulario.
     * Primero manda el audio dentro de la petición (una sola llamada); si el servidor no lo acepta,
     * lo sube con la Files API. Si el modelo de transcripción falla, lo intenta con Flash-Lite.
     */
    private suspend fun transcribe(d: AppData, audio: ByteArray): String {
        val vocab = (listOf("Órbita Webs", "Vaelor", "BertoProject", "UpGrade") +
            d.areas.map { it.name } + d.subjects.map { it.name } + d.aiVocab).distinct().take(100)
        val inline = buildJsonObject {
            put("type", "audio")
            put("data", Base64.encodeToString(audio, Base64.NO_WRAP))
            put("mime_type", "audio/aac")
        }
        try {
            return interactionsTranscribe(inline, vocab)
        } catch (e: AiError) {
            if (e.kind == AiError.Kind.INVALID_KEY || e.kind == AiError.Kind.QUOTA) throw e
        } catch (_: IOException) { }
        try {
            val uri = uploadAudio(audio)
            return interactionsTranscribe(buildJsonObject { put("type", "audio"); put("uri", uri); put("mime_type", "audio/aac") }, vocab)
        } catch (e: AiError) {
            if (e.kind == AiError.Kind.INVALID_KEY || e.kind == AiError.Kind.QUOTA) throw e
        } catch (_: IOException) { }
        return flashTranscribe(d, audio, vocab)
    }

    /** Voz → texto, para el chat con el dictado en modo preciso. */
    suspend fun transcribeOnly(d: AppData, audio: ByteArray): String = withContext(Dispatchers.IO) { transcribe(d, audio) }

    /** Palabras que el dictado debe reconocer: áreas, asignaturas y las tuyas. */
    fun vocabulary(d: AppData): List<String> =
        (listOf("Órbita Webs", "Vaelor", "BertoProject", "UpGrade") + d.areas.map { it.name } + d.subjects.map { it.name } + d.aiVocab).distinct().take(100)

    private fun interactionsTranscribe(audioItem: JsonObject, vocab: List<String>): String {
        val body = buildJsonObject {
            put("model", TRANSCRIBE_MODEL)
            putJsonArray("input") { add(audioItem) }
            putJsonObject("generation_config") {
                putJsonObject("transcription_config") {
                    put("mode", "smart")
                    putJsonArray("language_codes") { add("es-ES") }
                    putJsonArray("custom_vocabulary") { vocab.forEach { add(it) } }
                }
            }
        }
        val conn = open("$HOST/v1beta/interactions")
        conn.readTimeout = 30_000
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) throw httpError(code, conn.errorStream)
        val resp = json.parseToJsonElement(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).jsonObject
        (resp["output_text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val steps = (resp["steps"] as? JsonArray) ?: (resp["outputs"] as? JsonArray) ?: JsonArray(emptyList())
        val out = steps.flatMap { s ->
            val o = s as? JsonObject ?: return@flatMap emptyList()
            val content = o["content"] as? JsonArray
            if (content != null) content.mapNotNull { c -> (c as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            else listOfNotNull((o["text"] as? JsonPrimitive)?.contentOrNull)
        }.joinToString(" ").trim()
        if (out.isBlank()) throw AiError(AiError.Kind.OTHER, "Transcripción vacía.")
        return out
    }

    /** Último recurso: el modelo de texto (el tuyo o uno de respaldo) transcribe el audio en texto plano. */
    private fun flashTranscribe(d: AppData, audio: ByteArray, vocab: List<String>): String =
        generateWithFallback(d) { m -> flashTranscribeBody(m, audio, vocab) }.first.trim()

    private fun flashTranscribeBody(model: String, audio: ByteArray, vocab: List<String>): JsonObject {
        return buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", "Transcribe este audio en español de España, sin muletillas ni repeticiones. Solo la transcripción, nada más. Palabras que pueden salir: " + vocab.joinToString(", "))
                        }
                        addJsonObject { putJsonObject("inlineData") { put("mimeType", "audio/aac"); put("data", Base64.encodeToString(audio, Base64.NO_WRAP)) } }
                    }
                }
            }
            put("generationConfig", buildJsonObject {
                put("maxOutputTokens", 1024)
                thinkingFor(model)?.let { put("thinkingConfig", it) }
            })
        }
    }

    /** Sube el audio con el protocolo reanudable de la Files API y devuelve su URI. */
    private fun uploadAudio(audio: ByteArray): String {
        val start = URL("$HOST/upload/v1beta/files").openConnection() as HttpURLConnection
        start.requestMethod = "POST"
        start.connectTimeout = CONNECT_MS
        start.readTimeout = READ_MS
        start.doOutput = true
        start.setRequestProperty("x-goog-api-key", key())
        start.setRequestProperty("X-Goog-Upload-Protocol", "resumable")
        start.setRequestProperty("X-Goog-Upload-Command", "start")
        start.setRequestProperty("X-Goog-Upload-Header-Content-Length", audio.size.toString())
        start.setRequestProperty("X-Goog-Upload-Header-Content-Type", "audio/aac")
        start.setRequestProperty("Content-Type", "application/json")
        start.outputStream.use { it.write("""{"file":{"display_name":"dictado"}}""".toByteArray()) }
        if (start.responseCode !in 200..299) throw httpError(start.responseCode, start.errorStream)
        val uploadUrl = start.getHeaderField("x-goog-upload-url") ?: throw AiError(AiError.Kind.OTHER, "Sin URL de subida.")
        start.inputStream.use { it.readBytes() }

        val up = URL(uploadUrl).openConnection() as HttpURLConnection
        up.requestMethod = "POST"
        up.connectTimeout = CONNECT_MS
        up.readTimeout = 30_000
        up.doOutput = true
        up.setFixedLengthStreamingMode(audio.size)
        up.setRequestProperty("X-Goog-Upload-Offset", "0")
        up.setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
        up.outputStream.use { it.write(audio) }
        if (up.responseCode !in 200..299) throw httpError(up.responseCode, up.errorStream)
        val resp = json.parseToJsonElement(up.inputStream.bufferedReader().use { it.readText() }).jsonObject
        return resp["file"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
            ?: throw AiError(AiError.Kind.OTHER, "La subida no devolvió el archivo.")
    }

    // ------------------------------------------------------------------
    // Plan de repaso
    // ------------------------------------------------------------------

    /** Propone [sessions] sesiones de [minutes] min antes del examen. Lanza AiError si falla. */
    suspend fun studyPlan(d: AppData, exam: TaskItem, sessions: Int, minutes: Int, today: LocalDate = LocalDate.now()): List<StudySlot> =
        withContext(Dispatchers.IO) {
            val examDate = parseDate(exam.due) ?: throw AiError(AiError.Kind.OTHER, "El examen no tiene fecha.")
            val days = studyDays(today, examDate)
            val subject = d.subject(exam.subjectId)?.name
            val busy = days.joinToString("\n") { day -> "- ${dayName(day)} $day: " + busyText(d, day) }
            val prompt = """
Planifica $sessions sesiones de repaso de $minutes minutos para el examen «${exam.title}»${subject?.let { " ($it)" } ?: ""} del ${dayName(examDate)} $examDate${exam.time?.let { " a las " + minStr(it) } ?: ""}.
Ahora es ${dayName(today)} $today, ${minStr(nowMin())}.

Días disponibles y lo que ya hay ocupado cada día:
$busy

Reglas:
- Solo en esos días. Nunca el día del examen.
- Reparte las sesiones; más cerca del examen puede haber más, pero no más de 2 por día.
- Entre semana, después de clase y con margen para comer (no antes de las 16:30). Fin de semana, mañana o tarde.
- No solapes con nada de lo ocupado. Entre 07:30 y 22:00.
- Si hoy está en la lista, solo horas que empiecen al menos 30 min después de ahora.
Devuelve {"sesiones":[{"fecha":"YYYY-MM-DD","hora":"HH:MM"}]}.
""".trimIndent()
            val body = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", prompt) } } }
                }
                put("generationConfig", generationConfig(model(d), planSchema()))
            }
            val text = generateWithFallback(d) { body }.first
            val root = runCatching { json.parseToJsonElement(cleanJson(text)) }.getOrNull()
                ?: throw AiError(AiError.Kind.OTHER, "Respuesta ilegible.")
            val arr = when (root) {
                is JsonArray -> root
                is JsonObject -> (root["sesiones"] as? JsonArray) ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            val nowM = nowMin()
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val date = parseDate(o.str("fecha")) ?: return@mapNotNull null
                val time = parseTime(o.str("hora")) ?: return@mapNotNull null
                if (date !in days) return@mapNotNull null
                if (time < 7 * 60 + 30 || time > 22 * 60) return@mapNotNull null
                if (date == today && time < nowM + 30) return@mapNotNull null
                StudySlot(date, time)
            }.distinct().sortedWith(compareBy({ it.date }, { it.time })).take(sessions)
        }

    // ------------------------------------------------------------------
    // Tus hábitos: análisis y recomendaciones
    // ------------------------------------------------------------------

    private val COACH = """
Eres el analista de hábitos de UpGrade, la app de organización de Alberto: estudiante de 1º de Bachillerato en Huelva que además lleva un negocio de webs (Órbita Webs), una marca personal (BertoProject) y está en el equipo de una comunidad de emprendedores (Vaelor). Sus hábitos (gym, fisio, leer…) vienen en los datos.
Su problema declarado: lo deja todo para el final y procrastina con el móvil. Usa UpGrade (XP, meta diaria, una racha total en la que el día cuenta con 3 de 4 (meta, rutinas, hábitos del día, foco) y que perdona un fallo suelto, modo foco con bloqueo de apps, rutinas de bloqueo, hábitos, cerrar el día) para corregirlo.

Alberto ha descartado casi todas las recomendaciones anteriores porque eran obviedades que ya sabía («haz el fisio por la mañana», «usa el foco antes de las 21:00», «crea un plan de repaso»). No es tonto: si un consejo se le ocurriría a cualquiera mirando la cifra, no lo des.

Recibes un resumen con datos reales de sus últimas semanas. Tu trabajo:
1. Detectar los 1-3 patrones que más le están costando (o funcionando), siempre con una cifra del resumen.
2. Darle de 0 a 3 recomendaciones, ordenadas por impacto. Mejor una buena que tres flojas. Si no hay nada nuevo que merezca la pena, devuelve la lista vacía y dilo en el resumen.

Qué es una buena recomendación:
- Un cambio de sistema, no de fuerza de voluntad: algo que la app hace por él una vez configurado. Sus datos lo dicen: lo que arranca solo (las rutinas «al primer desbloqueo») lo cumple siempre; lo que depende de acordarse o de decidir en el momento, no.
- Ejemplos del tipo correcto: cambiar la frecuencia de un hábito que no va a cumplir, sacarlo de la racha o moverlo a los días en que sí puede; una rutina con hora y apps concretas; cambiar la meta o el tope de XP si la regla le empuja a hacer peor las cosas; quitar algo de la app que no usa.
- Concreta: qué tocar en la app, con días, horas o cifras. Que se pueda hacer en un minuto.
- Que tenga en cuenta «Lo que Alberto ha dicho de sí mismo». Si dice que algo no es prioridad o por qué falla, no le insistas: propón adaptar el sistema a eso (bajar, mover o quitar) o no digas nada.

Prohibido:
- Repetir un tema que haya descartado (mira los [temas] de las recomendaciones anteriores), aunque lo redactes distinto.
- Consejos genéricos, motivación, frases bonitas, «intenta», «procura», «recuerda».
- Contar como fallo los reinicios de foco (parar en los 5 primeros minutos para volver a empezar).
- Inventar datos. Usa solo cifras del resumen. Si hay pocos días, dilo y sé prudente.

Si una recomendación anterior la está probando, valora con los datos si está funcionando (en el resumen o en una recomendación).
Español de España, tuteando, con tildes y eñes bien puestas. Sin emojis. Sin markdown.

Devuelve JSON:
{"resumen": "2 o 3 frases: lo más importante que dicen sus datos",
 "recomendaciones": [{"titulo": "máx. 60 caracteres, empieza por verbo", "detalle": "máx. 220 caracteres: qué tocar exactamente en la app y por qué funciona sin fuerza de voluntad", "evidencia": "la cifra del resumen en la que te basas, máx. 90 caracteres", "tema": "una o dos palabras: el hábito concreto (fisio, leer, gym…) o foco, rutinas, meta, tareas, exámenes, móvil, cerrar el día, app", "accion": "foco|rutina|apps|anadir|meta|cerrar_dia|repaso|habitos|ninguna", "prioridad": 1}]}
«accion» es la pantalla de la app que le ayuda a hacerlo: foco (empezar un foco), rutina (crear o ajustar una rutina de bloqueo), apps (Control de apps), anadir (apuntar algo), meta (cambiar la meta o el XP de tareas, en Hoy con el lápiz de la tarjeta del día), cerrar_dia, repaso (plan de repaso de un examen), habitos (la pestaña Hábitos: cambiar frecuencia, quitar o crear) o ninguna. Prioridad 1 = lo primero.
Tu objetivo es cambiar su comportamiento dentro de la app (cuándo abre, qué deja para luego, qué rompe la racha), no enseñarle temario ni resolver deberes.
""".trimIndent()

    private val coachActions = setOf("foco", "rutina", "apps", "anadir", "meta", "cerrar_dia", "repaso", "habitos", "ninguna")

    /** Tema normalizado: minúsculas y sin tildes, para comparar «Fisio» con «fisio» o «exámenes» con «examenes». */
    fun normTopic(s: String): String = java.text.Normalizer.normalize(s.lowercase().trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun words(s: String): Set<String> = normTopic(s).split(' ').filter { it.length >= 4 }.map { it.take(6) }.toSet()

    /**
     * ¿Ya la descartó? Mismo tema en las últimas 3 semanas, o un título que comparte la mitad de las palabras
     * con uno descartado (la IA tiende a repetir lo mismo con otra redacción: «Marca el fisio por la mañana»).
     */
    fun alreadyDismissed(d: AppData, title: String, topic: String, now: Long = System.currentTimeMillis()): Boolean {
        val gone = (d.insightHistory + d.insights?.items.orEmpty()).filter { it.status == "dismissed" }
        val t = normTopic(topic)
        val recent = 21L * 24 * 60 * 60 * 1000
        if (t.isNotEmpty() && gone.any { normTopic(it.topic) == t && (it.dismissedAt == 0L || now - it.dismissedAt < recent) }) return true
        val w = words(title)
        if (w.isEmpty()) return false
        return gone.any { g ->
            val o = words(g.title)
            o.isNotEmpty() && (w intersect o).size * 2 >= minOf(w.size, o.size)
        }
    }

    /** Analiza los hábitos con Gemini. Lanza AiError si falla. */
    suspend fun coach(d: AppData, stats: HabitStats): InsightReport = withContext(Dispatchers.IO) {
        if (!SecretStore.hasGeminiKey()) throw AiError(AiError.Kind.INVALID_KEY, "Añade tu clave de Gemini en Ajustes › IA.")
        fun body(m: String) = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", COACH) } } }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", "Resumen de los datos de Alberto:\n<<<\n${stats.prompt}\n>>>") } }
                }
            }
            put("generationConfig", generationConfig(m, coachSchema(), maxTokens = 2048))
        }
        val (text, used) = generateWithFallback(d) { m -> body(m) }
        val root = runCatching { json.parseToJsonElement(cleanJson(text)) as? JsonObject }.getOrNull()
            ?: throw AiError(AiError.Kind.OTHER, "La respuesta de Gemini no se ha podido leer. Prueba otra vez.")
        val raw = (root["recomendaciones"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val title = o.str("titulo")?.take(80) ?: return@mapNotNull null
            Insight(
                id = Repo.newId(),
                title = title,
                detail = o.str("detalle")?.take(300).orEmpty(),
                evidence = o.str("evidencia")?.take(120).orEmpty(),
                action = o.str("accion")?.lowercase()?.replace("ñ", "n")?.takeIf { it in coachActions } ?: "ninguna",
                priority = (o["prioridad"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.coerceIn(1, 3) ?: 2,
                topic = o.str("tema")?.take(30)?.trim().orEmpty()
            )
        }
        // Lo descartado no vuelve, aunque Gemini se empeñe.
        val items = raw.filterNot { alreadyDismissed(d, it.title, it.topic) }
            .distinctBy { normTopic(it.topic).ifEmpty { it.id } }
            .sortedBy { it.priority }.take(3)
        val summary = root.str("resumen")?.take(400).orEmpty()
        if (items.isEmpty() && summary.isBlank()) throw AiError(AiError.Kind.OTHER, "Gemini no ha devuelto nada útil. Prueba otra vez.")
        InsightReport(
            at = System.currentTimeMillis(),
            summary = summary,
            items = items,
            days = stats.days,
            model = used
        )
    }

    private fun coachSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("resumen") { put("type", "STRING") }
            putJsonObject("recomendaciones") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("titulo") { put("type", "STRING") }
                        putJsonObject("detalle") { put("type", "STRING") }
                        putJsonObject("evidencia") { put("type", "STRING") }
                        putJsonObject("tema") { put("type", "STRING") }
                        putJsonObject("accion") {
                            put("type", "STRING")
                            putJsonArray("enum") { coachActions.forEach { add(it) } }
                        }
                        putJsonObject("prioridad") { put("type", "INTEGER") }
                    }
                    putJsonArray("required") { add("titulo"); add("detalle"); add("evidencia"); add("tema"); add("accion"); add("prioridad") }
                    putJsonArray("propertyOrdering") { add("titulo"); add("detalle"); add("evidencia"); add("tema"); add("accion"); add("prioridad") }
                }
            }
        }
        putJsonArray("required") { add("resumen"); add("recomendaciones") }
        putJsonArray("propertyOrdering") { add("resumen"); add("recomendaciones") }
    }

    fun studyDays(today: LocalDate, examDate: LocalDate): List<LocalDate> {
        val startToday = nowMin() < 20 * 60
        val from = if (startToday) today else today.plusDays(1)
        val n = ChronoUnit.DAYS.between(from, examDate).toInt()
        return if (n <= 0) emptyList() else (0 until n).map { from.plusDays(it.toLong()) }
    }

    private fun busyText(d: AppData, day: LocalDate): String {
        val parts = mutableListOf<String>()
        classesOn(d, day).takeIf { it.isNotEmpty() }?.let { parts += "clase ${minStr(it.first().period.start)}-${minStr(it.last().period.end)}" }
        eventsOn(d, day).forEach { parts += "${it.title} ${minStr(it.start)}-${minStr(it.end)}" }
        d.tasks.filter { !it.done && it.due == day.key() && it.time != null }.forEach { t ->
            val tm = t.time ?: return@forEach
            parts += "${t.title} ${minStr(tm)}" + (t.minutes?.let { m -> "-" + minStr(tm + m) } ?: "")
        }
        val b = d.block
        if (b.enabled && b.scheduleEnabled && day.dayOfWeek.value in b.days) parts += "franja de estudio ${minStr(b.from)}-${minStr(b.to)} (buen momento para estudiar)"
        return parts.joinToString("; ").ifBlank { "libre" }
    }

    // ------------------------------------------------------------------
    // IA general: chat con todo el contexto, fotos y propuestas para la agenda
    // ------------------------------------------------------------------

    /** Marca que separa la respuesta de las operaciones sobre la agenda. */
    const val ACTIONS_MARK = "@@ACCIONES@@"

    /**
     * Pregunta a la IA general. La respuesta llega en streaming ([ChatEvent.Answer] con el texto entero hasta ahora);
     * si hay algo que agendar, llegan [ChatEvent.Item] al final. [image] es un JPEG (foto de la cámara).
     */
    fun chat(d: AppData, history: List<ChatMsg>, message: String, image: ByteArray? = null, today: LocalDate = LocalDate.now()): Flow<ChatEvent> = flow {
        if (!SecretStore.hasGeminiKey()) {
            emit(ChatEvent.Done(false, "Para hablar conmigo necesito tu clave de Gemini (Ajustes › IA). Es gratis y tarda un minuto."))
            return@flow
        }
        val t0 = System.currentTimeMillis()
        var firstMs = -1L
        var model = model(d)
        var ok = false
        val ctx = PromptContext.build(d, today, includePending = true, all = true)
        val big = FullContext.render(d, ctx, today)
        val answer = StringBuilder()
        val scanner = JsonItemScanner()
        var inActions = false
        var buffer = ""
        val ask = message.ifBlank {
            if (image != null) "Mira la foto. Si hay fechas, deberes, exámenes o cosas que hacer, apúntalas. Si no, dime qué ves y en qué te ayudo." else ""
        }
        try {
            model = streamWithFallback(d, { m -> chatBody(big, history, ask, image, m) }) { chunk ->
                if (!inActions) {
                    buffer += chunk
                    val i = buffer.indexOf(ACTIONS_MARK)
                    if (i >= 0) {
                        answer.append(buffer.substring(0, i))
                        inActions = true
                        scanner.feed(buffer.substring(i + ACTIONS_MARK.length))
                        buffer = ""
                    } else {
                        // Lo que podría ser el principio de la marca se guarda hasta el siguiente trozo.
                        val keep = (ACTIONS_MARK.length - 1 downTo 1).firstOrNull { k -> buffer.endsWith(ACTIONS_MARK.substring(0, k)) } ?: 0
                        answer.append(buffer.substring(0, buffer.length - keep))
                        buffer = buffer.takeLast(keep)
                    }
                    if (firstMs < 0 && answer.isNotBlank()) firstMs = System.currentTimeMillis() - t0
                    emit(ChatEvent.Answer(cleanAnswer(answer.toString())))
                } else scanner.feed(chunk)
                for (raw in scanner.drain()) parseItem(d, ctx, raw, today)?.let { emit(ChatEvent.Item(it)) }
            }
            if (!inActions && buffer.isNotEmpty()) { answer.append(buffer); emit(ChatEvent.Answer(cleanAnswer(answer.toString()))) }
            ok = true
            emit(ChatEvent.Done(true, null))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ChatEvent.Done(false, (e as? AiError)?.message ?: "La IA ha fallado. Prueba otra vez."))
        } finally {
            Repo.recordAiTiming(AiTiming(t0, firstMs, System.currentTimeMillis() - t0, if (image != null) "foto" else "chat", model, ok))
        }
    }.flowOn(Dispatchers.IO)

    /** Quita un bloque de JSON que la IA haya dejado suelto fuera de la marca. */
    private fun cleanAnswer(a: String): String = a.replace(Regex("```(?:json)?\\s*\\{\\s*\"items\"[\\s\\S]*$"), "").trim()

    private fun chatBody(context: String, history: List<ChatMsg>, message: String, image: ByteArray?, model: String): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", CHAT_SYSTEM) } } }
        putJsonArray("contents") {
            // El contexto grande va primero y siempre igual dentro del día: así Gemini lo reaprovecha (caché implícita).
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") { addJsonObject { put("text", "Datos de Alberto en UpGrade (actualizados ahora):\n<<<\n$context\n>>>") } }
            }
            addJsonObject {
                put("role", "model")
                putJsonArray("parts") { addJsonObject { put("text", "Tengo tus datos delante. Dime.") } }
            }
            history.takeLast(14).forEach { m ->
                addJsonObject {
                    put("role", if (m.role == "user") "user" else "model")
                    putJsonArray("parts") { addJsonObject { put("text", (if (m.photo) "[Mandé una foto] " else "") + m.text.take(4000)) } }
                }
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    if (image != null) addJsonObject {
                        putJsonObject("inlineData") { put("mimeType", "image/jpeg"); put("data", Base64.encodeToString(image, Base64.NO_WRAP)) }
                    }
                    addJsonObject { put("text", message.trim()) }
                }
            }
        }
        put("generationConfig", buildJsonObject {
            put("maxOutputTokens", 6144)
            put("temperature", 0.6)
            thinkingFor(model)?.let { put("thinkingConfig", it) }
        })
    }

    private val OPS_FORMAT = """
Cada operación:
- "op": "crear" para algo nuevo; "mover" para cambiar fecha u hora de algo que ya existe; "editar" para cambiar otros datos; "completar" si dice que ya lo ha hecho; "borrar" si dice que lo quites o que se cancela.
- "ref": para mover/editar/completar/borrar, la referencia (t1, t2…) de la tarea de la lista «Pendiente». Si encajan varias y no está claro cuál, deja "ref" en null y pon todas en "candidatas".
- "titulo": corto y claro, en español.
- "tipo": EXAMEN, DEBERES, TRABAJO, TAREA o LLAMADA.
- "area": una de las áreas dadas. Lo del instituto es "insti".
- "asignatura": solo si el área es "insti": el id de la asignatura, o una lista de ids si es de varias; si no, null.
- "fecha": "YYYY-MM-DD" o null. Usa SIEMPRE la tabla «Calendario» para fechas relativas.
- "hora": "HH:MM" o null.
- "dudas": campos de los que no estás seguro ("fecha", "area", "hora", "tipo", "asignatura", "titulo").
En mover y editar, da el estado FINAL completo de la tarea.
""".trimIndent()

    private val CHAT_SYSTEM = """
Eres la IA de UpGrade, la app de organización de Alberto: estudiante de 1º de Bachillerato en Huelva (España), que además lleva un negocio de webs (Órbita Webs), una marca personal (BertoProject), está en el equipo de una comunidad de emprendedores (Vaelor), va al gym y hace ejercicios del fisio. Su problema declarado: lo deja todo para el final y procrastina con el móvil.

Tienes delante TODOS sus datos de la app: tareas, exámenes y planes de repaso, hábitos, rutinas, focos, XP, racha, horario, agenda y cómo usa la app. Úsalos siempre que sirvan: responde con lo concreto (fechas, cifras, nombres de tareas), nunca en general. No inventes datos que no estén.

Puedes ayudar con TODO: qué hacer ahora y en qué orden, planes del día o de la semana, estudios (explicar temario, resolver dudas y ejercicios paso a paso, resúmenes, esquemas, preguntas tipo examen para repasar), hábitos, gym, sus negocios o cualquier otra cosa.

Estilo: español de España, tuteando. Directo, sin relleno ni halagos: lo pide así. Si lo que plantea no tiene sentido o choca con sus datos (deja un examen para el último día, se pone 6 horas de foco seguidas), dilo claro y propón algo mejor. Por defecto, corto (2-6 frases); largo solo si hace falta (explicar un tema, un plan). Puedes usar listas con "- " y **negrita** para lo clave; nada más de markdown (sin títulos, sin tablas). Sin emojis.
Cuando recomiendes algo de la app, nombra la pantalla: Foco (temporizador, rutinas, hábitos), Control de apps, Plan de repaso, Cerrar el día.

Agenda: si te pide apuntar, mover, completar o borrar algo, o en una foto hay fechas, deberes, exámenes o tareas, propónlo. Primero tu respuesta (di qué propones apuntar; no digas que ya está apuntado: él lo confirma). Al final, en una línea aparte, exactamente $ACTIONS_MARK seguido de un JSON {"items":[...]}. Si no hay nada que agendar, no pongas esa línea. Nunca pongas el JSON en otro sitio.

$OPS_FORMAT
""".trimIndent()

    // ------------------------------------------------------------------
    // Esquemas y validación
    // ------------------------------------------------------------------

    private fun itemSchema(d: AppData): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("transcripcion") { put("type", "STRING"); put("nullable", true) }
            putJsonObject("items") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("op") { put("type", "STRING"); putJsonArray("enum") { Op.entries.forEach { add(it.name.lowercase()) } } }
                        putJsonObject("ref") { put("type", "STRING"); put("nullable", true) }
                        putJsonObject("candidatas") { put("type", "ARRAY"); putJsonObject("items") { put("type", "STRING") } }
                        putJsonObject("titulo") { put("type", "STRING") }
                        putJsonObject("tipo") { put("type", "STRING"); putJsonArray("enum") { aiKinds.forEach { add(it.name) } } }
                        putJsonObject("area") { put("type", "STRING"); putJsonArray("enum") { d.areas.forEach { add(it.id) } } }
                        putJsonObject("asignatura") { put("type", "STRING"); put("nullable", true) }
                        putJsonObject("fecha") { put("type", "STRING"); put("nullable", true) }
                        putJsonObject("hora") { put("type", "STRING"); put("nullable", true) }
                        putJsonObject("dudas") { put("type", "ARRAY"); putJsonObject("items") { put("type", "STRING") } }
                    }
                    putJsonArray("required") { listOf("op", "titulo", "tipo", "area").forEach { add(it) } }
                    putJsonArray("propertyOrdering") {
                        listOf("op", "ref", "candidatas", "titulo", "tipo", "area", "asignatura", "fecha", "hora", "dudas").forEach { add(it) }
                    }
                }
            }
        }
        putJsonArray("required") { add("items") }
        putJsonArray("propertyOrdering") { add("transcripcion"); add("items") }
    }

    private fun planSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("sesiones") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("fecha") { put("type", "STRING") }
                        putJsonObject("hora") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("fecha"); add("hora") }
                    putJsonArray("propertyOrdering") { add("fecha"); add("hora") }
                }
            }
        }
        putJsonArray("required") { add("sesiones") }
    }

    private val aiKinds = listOf(Kind.EXAMEN, Kind.DEBERES, Kind.TRABAJO, Kind.TAREA, Kind.LLAMADA)

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.strList(k: String): List<String> =
        (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotBlank() } }.orEmpty()

    private fun parseTime(t: String?): Int? {
        if (t == null) return null
        val p = t.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.take(2)?.toIntOrNull() ?: 0
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    private fun cleanJson(raw: String): String = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    /** Valida un objeto de la IA. Nada se fía: fechas, áreas, tipos y referencias se comprueban aquí. */
    internal fun parseItem(d: AppData, ctx: PromptContext, raw: String, today: LocalDate): Proposal? {
        val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val op = runCatching { Op.valueOf(o.str("op")?.uppercase() ?: "CREAR") }.getOrDefault(Op.CREAR)
        val doubts = o.strList("dudas").map { it.lowercase() }.toMutableSet()

        val target = o.str("ref")?.let { ctx.refs[it] }?.let { id -> d.tasks.firstOrNull { it.id == id } }
        val candidates = o.strList("candidatas").mapNotNull { ctx.refs[it] }.filter { id -> d.tasks.any { it.id == id } }.distinct()
        if (op != Op.CREAR && target == null && candidates.isEmpty()) return null
        val base = target ?: candidates.firstOrNull()?.let { id -> d.tasks.firstOrNull { it.id == id } }

        val title = (o.str("titulo") ?: base?.title)?.take(120) ?: return null
        val kindRaw = o.str("tipo")?.uppercase()
        val kind = aiKinds.firstOrNull { it.name == kindRaw } ?: base?.kind ?: Kind.TAREA
        val areaRaw = o.str("area")?.lowercase()
        val area = areaRaw?.takeIf { a -> d.areas.any { it.id == a } } ?: base?.areaId ?: "personal".also { doubts += "area" }
        // Una o varias asignaturas: "asignatura": "historia" o ["historia","filosofia"] (o "asignaturas").
        val subjects = (listOfNotNull(o.str("asignatura")) + o.strList("asignatura") + o.strList("asignaturas"))
            .mapNotNull { raw -> val s = raw.lowercase(); d.subjects.firstOrNull { it.id == s || it.name.equals(s, ignoreCase = true) }?.id }
            .distinct().takeIf { area == "insti" }.orEmpty()
        val subject = subjects.firstOrNull()

        val dateRaw = o.str("fecha")
        var date = parseDate(dateRaw)
        if (dateRaw != null && date == null) doubts += "fecha"
        if (date != null) {
            val tooEarly = if (op == Op.CREAR) date.isBefore(today) else date.isBefore(today.minusDays(60))
            if (tooEarly || date.isAfter(today.plusYears(1))) { date = null; doubts += "fecha" }
        }
        val timeRaw = o.str("hora")
        val time = parseTime(timeRaw)
        if (timeRaw != null && time == null) doubts += "hora"

        val p = Proposal(
            title = title, kind = kind, areaId = area, subjectId = subject, date = date, time = time,
            op = op, targetId = target?.id, candidates = if (target == null) candidates else emptyList(),
            doubts = doubts.filter { it in DOUBT_FIELDS }.toSet(), extraSubjects = subjects.drop(1)
        )
        // COMPLETAR y BORRAR no cambian campos: se enseña la tarea tal cual.
        return if ((op == Op.COMPLETAR || op == Op.BORRAR) && target != null) {
            p.copy(title = target.title, kind = target.kind, areaId = target.areaId, subjectId = target.subjectId,
                extraSubjects = target.extraSubjects, date = parseDate(target.due), time = target.time, doubts = emptySet())
        } else p
    }

    val DOUBT_FIELDS = setOf("titulo", "tipo", "area", "asignatura", "fecha", "hora")

    /** Cómo se ve una propuesta corregida, en el mismo formato que devuelve la IA (para los ejemplos). */
    fun exampleJson(d: AppData, items: List<Proposal>): String = buildJsonObject {
        putJsonArray("items") {
            items.forEach { p ->
                addJsonObject {
                    put("op", p.op.name.lowercase())
                    if (p.op != Op.CREAR) put("ref", "…")
                    put("titulo", p.title)
                    put("tipo", p.kind.name)
                    put("area", p.areaId)
                    val subs = p.subjectIds.mapNotNull { d.subject(it)?.id }
                    if (subs.size > 1) putJsonArray("asignatura") { subs.forEach { add(it) } } else put("asignatura", subs.firstOrNull())
                    put("fecha", p.date?.toString())
                    put("hora", p.time?.let { minStr(it) })
                }
            }
        }
    }.toString()

    private val SYSTEM = """
Eres el organizador de UpGrade, la agenda de Alberto: estudiante de 1º de Bachillerato en Huelva (España) que además lleva un negocio de webs (Órbita Webs), una marca personal (BertoProject), está en el equipo de una comunidad de emprendedores (Vaelor) y va al gym.

Tu trabajo: convertir lo que Alberto dice en operaciones sobre su agenda. Devuelve JSON con "items": una lista de operaciones.
Si además (o en lugar de eso) pregunta algo, pide consejo o dice algo que no es una tarea, añade "respuesta": un texto corto (máx. 3 frases, sin markdown) que le ayude a usar mejor UpGrade y a no procrastinar: qué hacer ahora, qué pantalla usar (Foco, Rutinas, Control de apps, Hábitos, Plan de repaso, Cerrar el día) o qué cambiar en su forma de usar la app, apoyándote en su agenda y en «Pendiente». No des clases ni resuelvas ejercicios de estudio: tu papel es mejorar cómo se organiza y cómo usa la app. Si no hay nada que responder, no pongas "respuesta". Nunca devuelvas solo "items" vacío sin "respuesta".

Cada operación:
- "op": "crear" para algo nuevo; "mover" para cambiar fecha u hora de algo que ya existe; "editar" para cambiar otros datos; "completar" si dice que ya lo ha hecho; "borrar" si dice que lo quites o que se cancela.
- "ref": para mover/editar/completar/borrar, la referencia (t1, t2…) de la tarea de la lista «Pendiente». Si encajan varias y no está claro cuál, deja "ref" en null y pon todas las que encajan en "candidatas".
- "titulo": corto y claro, en español (ej. "Examen de Lengua", "Llamada con el cliente de la pizzería", "Ejercicios pág. 54 de Mates").
- "tipo": EXAMEN, DEBERES, TRABAJO, TAREA o LLAMADA. Una llamada o reunión es LLAMADA.
- "area": una de las áreas dadas. Lo del instituto es "insti".
- "asignatura": solo si el área es "insti": el id de la asignatura; si es de varias, una lista de ids (["historia","filosofia"]); si no, null.
- "fecha": "YYYY-MM-DD" o null si no la dice. USA SIEMPRE la tabla «Calendario» para fechas relativas ("el jueves", "pasado mañana", "la semana que viene"): "el jueves" es el próximo jueves de la tabla. No calcules fechas por tu cuenta.
- "hora": "HH:MM" o null. "A las 6" o "a las 5" por la tarde son 18:00 y 17:00.
- "dudas": lista de campos de los que no estás seguro ("fecha", "area", "hora", "tipo", "asignatura", "titulo"). Vacía si lo tienes claro.
En mover y editar, da el estado FINAL completo de la tarea: copia de la lista lo que no cambia.

Reglas:
- Solo lo que Alberto dice. No inventes tareas.
- Si dice algo en general "para hoy" o "para mañana", pon esa fecha.
- Si pide que le organices el día, asigna horas realistas fuera de clase, sin solapar con la agenda y lo más difícil o urgente primero.
- No dupliques: si algo ya está en «Pendiente» igual, no lo crees otra vez.
- Si da una referencia temporal a una clase ("después de mates"), usa el horario.
- Listas con cabecera («Imprimir:» y debajo líneas con guion, o «Imprimir: a, b y c»): cada línea es UNA tarea y la cabecera se aplica a todas: «Imprimir tema 13 de Historia», «Imprimir Filosofía». Nunca crees la cabecera sola como tarea ni dejes el guion en el título. Imprimir, comprar o llevar algo es TAREA aunque nombre una asignatura (área insti si es del instituto).
- El texto puede venir de un dictado automático con errores: «valor» o «Valor» suele ser Vaelor, «órbita web» es Órbita Webs, «berto proyect» es BertoProject. Corrígelo en el título.

Ejemplos (con un calendario donde hoy es lunes 2026-03-02, mañana martes 2026-03-03, jueves 2026-03-05, viernes 2026-03-06):

Mensaje: "el jueves tengo examen de historia de los temas 3 y 4 y mañana hay que entregar el writing de inglés"
{"items":[{"op":"crear","ref":null,"candidatas":[],"titulo":"Examen de Historia (temas 3 y 4)","tipo":"EXAMEN","area":"insti","asignatura":"historia","fecha":"2026-03-05","hora":null,"dudas":[]},{"op":"crear","ref":null,"candidatas":[],"titulo":"Entregar writing de inglés","tipo":"DEBERES","area":"insti","asignatura":"ingles","fecha":"2026-03-03","hora":null,"dudas":[]}]}

Mensaje: "llamar a Lola de la pizzería el viernes a las 6 para lo de la web"
{"items":[{"op":"crear","ref":null,"candidatas":[],"titulo":"Llamada con Lola (pizzería) por la web","tipo":"LLAMADA","area":"orbita","asignatura":null,"fecha":"2026-03-06","hora":"18:00","dudas":[]}]}

Mensaje (con «Pendiente: t3 = Llamada Vaelor · mié 2026-03-04 · 17:00 · vaelor · LLAMADA»): "pasa lo de Vaelor al viernes a la misma hora y ya he hecho lo de mates"
{"items":[{"op":"mover","ref":"t3","candidatas":[],"titulo":"Llamada Vaelor","tipo":"LLAMADA","area":"vaelor","asignatura":null,"fecha":"2026-03-06","hora":"17:00","dudas":[]}]}
(«lo de mates» no aparece en Pendiente, así que no se completa nada.)

Mensaje: "Imprimir:\n- Tema 13 de Historia\n- Filosofía\n- Dos PDFs"
{"items":[{"op":"crear","ref":null,"candidatas":[],"titulo":"Imprimir tema 13 de Historia","tipo":"TAREA","area":"insti","asignatura":"historia","fecha":null,"hora":null,"dudas":["fecha"]},{"op":"crear","ref":null,"candidatas":[],"titulo":"Imprimir Filosofía","tipo":"TAREA","area":"insti","asignatura":"filosofia","fecha":null,"hora":null,"dudas":["fecha"]},{"op":"crear","ref":null,"candidatas":[],"titulo":"Imprimir dos PDFs","tipo":"TAREA","area":"personal","asignatura":null,"fecha":null,"hora":null,"dudas":["area","fecha"]}]}

Mensaje: "¿qué hago ahora?"
{"items":[],"respuesta":"Lo más urgente es el examen de Historia del jueves. Abre su plan de repaso y empieza un foco de 60 min ahora: con Instagram bloqueado no te vas a despistar."}

Mensaje: "grabar un reel para Berto esta semana y comprar creatina"
{"items":[{"op":"crear","ref":null,"candidatas":[],"titulo":"Grabar un reel","tipo":"TAREA","area":"berto","asignatura":null,"fecha":null,"hora":null,"dudas":["fecha"]},{"op":"crear","ref":null,"candidatas":[],"titulo":"Comprar creatina","tipo":"TAREA","area":"personal","asignatura":null,"fecha":null,"hora":null,"dudas":[]}]}
""".trimIndent()
}

/**
 * Contexto mínimo útil para la IA: calendario de 14 días, áreas, asignaturas, horario, agenda fija,
 * lo pendiente cercano (con referencias cortas t1, t2… en lugar de ids) y las correcciones recientes.
 */
class PromptContext private constructor(
    private val today: LocalDate,
    private val pending: List<Pair<String, TaskItem>>,
    /** ref corta → id real */
    val refs: Map<String, String>,
    /** Sin lista de pendientes (el mensaje solo crea cosas): prompt más corto, respuesta más rápida. */
    private val skipped: Boolean = false
) {
    fun render(d: AppData): String = buildString {
        val now = LocalTime.now()
        appendLine("Ahora: ${dayName(today)} $today, ${minStr(now.hour * 60 + now.minute)} (hora de España).")
        appendLine()
        appendLine("Calendario:")
        for (i in 0..13) {
            val day = today.plusDays(i.toLong())
            val tag = when (i) { 0 -> " (hoy)"; 1 -> " (mañana)"; 2 -> " (pasado mañana)"; else -> "" }
            appendLine("- ${dayName(day)} $day$tag")
        }
        appendLine()
        appendLine("Áreas: " + d.areas.joinToString(", ") { "${it.id} = ${it.name}" })
        appendLine("Asignaturas (área insti): " + d.subjects.joinToString(", ") { "${it.id} = ${it.name}" })
        appendLine()
        appendLine("Horario de clases:")
        val names = listOf("lunes", "martes", "miércoles", "jueves", "viernes")
        for (dow in 1..5) {
            val slots = d.timetable.filter { it.day == dow }.sortedBy { it.period }
            if (slots.isEmpty()) continue
            appendLine("- ${names[dow - 1]}: " + slots.mapNotNull { s ->
                val p = d.periods.getOrNull(s.period) ?: return@mapNotNull null
                "${minStr(p.start)} ${d.subject(s.subjectId)?.name ?: s.subjectId}"
            }.joinToString(", "))
        }
        if (d.events.isNotEmpty()) {
            appendLine()
            appendLine("Agenda fija:")
            d.events.forEach { e ->
                val whenTxt = e.date ?: e.weekdays.joinToString(",") { names.getOrElse(it - 1) { if (it == 6) "sábado" else "domingo" } }
                appendLine("- ${e.title} ($whenTxt ${minStr(e.start)}-${minStr(e.end)})")
            }
        }
        appendLine()
        if (skipped) appendLine("Pendiente: no hace falta (solo crear).")
        else if (pending.isEmpty()) appendLine("Pendiente: nada.")
        else {
            appendLine("Pendiente (ref = título · fecha · hora · área · tipo):")
            pending.forEach { (ref, t) ->
                val date = parseDate(t.due)?.let { "${dayShort(it)} $it" } ?: "sin fecha"
                appendLine("- $ref = ${t.title} · $date · ${t.time?.let { minStr(it) } ?: "sin hora"} · ${t.areaId} · ${t.kind.name}" +
                    (d.subjectLabel(t)?.let { " · $it" } ?: ""))
            }
        }
        if (d.aiExamples.isNotEmpty()) {
            appendLine()
            appendLine("Correcciones recientes de Alberto (aprende de ellas: así lo quiere):")
            d.aiExamples.takeLast(5).forEach { ex ->
                appendLine("Mensaje: \"${ex.input.take(300)}\"")
                appendLine(ex.output.take(800))
            }
        }
    }

    companion object {
        fun build(d: AppData, today: LocalDate, includePending: Boolean = true, all: Boolean = false): PromptContext {
            if (!includePending) return PromptContext(today, emptyList(), emptyMap(), skipped = true)
            val pending = d.tasks.asSequence()
                .filter { !it.done && it.kind != Kind.REPASO }
                .filter { t ->
                    if (all) return@filter true
                    val due = parseDate(t.due)
                    due == null ||
                        (!due.isBefore(today.minusDays(14)) && !due.isAfter(today.plusDays(14))) ||
                        (t.kind == Kind.EXAMEN && !due.isBefore(today) && !due.isAfter(today.plusDays(30)))
                }
                .sortedWith(taskOrder)
                .take(if (all) 120 else 40)
                .toList()
            val pairs = pending.mapIndexed { i, t -> "t${i + 1}" to t }
            return PromptContext(today, pairs, pairs.associate { it.first to it.second.id })
        }
    }
}

/**
 * Lee el JSON de la IA según llega y saca cada objeto completo que esté dentro de una lista
 * (las propuestas), sin esperar al final. Respeta cadenas y escapes.
 */
class JsonItemScanner {
    private val buf = StringBuilder()
    private var pos = 0
    private var inString = false
    private var escape = false
    /** Pila de '{' y '['; para cada '{' guardamos dónde empieza y si su padre es una lista. */
    private val stack = ArrayDeque<Char>()
    private val objStart = ArrayDeque<Int>()
    private val objInArray = ArrayDeque<Boolean>()
    private val ready = mutableListOf<String>()

    /** Todo lo recibido hasta ahora. */
    fun full(): String = buf.toString()

    fun feed(chunk: String) {
        buf.append(chunk)
        while (pos < buf.length) {
            val c = buf[pos]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
            } else when (c) {
                '"' -> inString = true
                '{' -> {
                    objInArray.addLast(stack.lastOrNull() == '[')
                    objStart.addLast(pos)
                    stack.addLast('{')
                }
                '}' -> if (stack.lastOrNull() == '{') {
                    stack.removeLast()
                    val start = objStart.removeLast()
                    val inArray = objInArray.removeLast()
                    if (inArray) ready += buf.substring(start, pos + 1)
                }
                '[' -> stack.addLast('[')
                ']' -> if (stack.lastOrNull() == '[') stack.removeLast()
            }
            pos++
        }
    }

    fun drain(): List<String> {
        if (ready.isEmpty()) return emptyList()
        val out = ready.toList()
        ready.clear()
        return out
    }

    private val transcriptRe = Regex("\"transcripcion\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** La transcripción, en cuanto su cadena está completa. */
    fun transcript(@Suppress("UNUSED_PARAMETER") final: Boolean = false): String? {
        val m = transcriptRe.find(buf) ?: return null
        return runCatching { Json.parseToJsonElement("\"" + m.groupValues[1] + "\"").jsonPrimitive.content }
            .getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }
}
