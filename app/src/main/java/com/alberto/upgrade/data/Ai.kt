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
    /** Terminado. [usedAi] = false si se ha usado el modo básico. */
    data class Done(val usedAi: Boolean, val note: String?) : AiEvent
}

/** Error explicado en español, listo para enseñar. */
class AiError(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { INVALID_KEY, QUOTA, NETWORK, TIMEOUT, SERVER, BAD_REQUEST, OTHER }
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
    private const val CONNECT_MS = 8_000
    private const val READ_MS = 15_000

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
        val mode = when {
            audio != null && d.aiPrecise -> "voz precisa"
            audio != null -> "voz"
            else -> "texto"
        }
        val model = model(d)

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
            var sendAudio = audio
            if (audio != null && d.aiPrecise) {
                // Modo preciso: transcripción dedicada con vocabulario propio y luego organizar el texto.
                val tr = runCatching { transcribePrecise(d, audio) }.getOrNull()
                if (!tr.isNullOrBlank()) {
                    transcript = tr
                    emit(AiEvent.Transcript(tr))
                    input = tr
                    sendAudio = null
                }
            }
            val ctx = PromptContext.build(d, today)
            val body = organizeBody(d, ctx, input, sendAudio)
            val scanner = JsonItemScanner()
            stream(model, body) { chunk ->
                scanner.feed(chunk)
                if (transcript == null) {
                    scanner.transcript()?.let { transcript = it; emit(AiEvent.Transcript(it)) }
                }
                for (raw in scanner.drain()) {
                    val p = parseItem(d, ctx, raw, today) ?: continue
                    if (first < 0) first = System.currentTimeMillis() - t0
                    count++
                    emit(AiEvent.Item(p))
                }
            }
            if (transcript == null) scanner.transcript(final = true)?.let { transcript = it; emit(AiEvent.Transcript(it)) }
            ok = true
            emit(AiEvent.Done(true, if (count == 0) "No he sacado nada. Prueba con frases tipo «examen de Lengua el jueves»." else null))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = (e as? AiError)?.message ?: "La IA ha fallado."
            val fallbackText = transcript ?: text
            if (count == 0 && !fallbackText.isNullOrBlank()) {
                LocalParser.parse(d, fallbackText, today).forEach { emit(AiEvent.Item(it)) }
                emit(AiEvent.Done(false, "$msg He usado el modo básico."))
            } else {
                emit(AiEvent.Done(count > 0, msg))
            }
        } finally {
            Repo.recordAiTiming(AiTiming(t0, first, System.currentTimeMillis() - t0, mode, model, ok))
        }
    }.flowOn(Dispatchers.IO)

    private fun organizeBody(d: AppData, ctx: PromptContext, text: String?, audio: ByteArray?): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", SYSTEM) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", ctx.render(d)) }
                    if (audio != null) {
                        addJsonObject {
                            put("text", "Mensaje de Alberto (audio). Primero transcribe el audio en «transcripcion», en español de España, sin muletillas ni repeticiones. Después organiza.")
                        }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "audio/aac")
                                put("data", Base64.encodeToString(audio, Base64.NO_WRAP))
                            }
                        }
                    } else {
                        addJsonObject { put("text", "Mensaje de Alberto:\n<<<\n${text.orEmpty().trim()}\n>>>") }
                    }
                }
            }
        }
        put("generationConfig", generationConfig(model(d), itemSchema(d)))
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
        while (true) {
            try {
                streamOnce(model, payload, onText)
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
                if (err.kind == AiError.Kind.NETWORK && attempt == 0) { attempt++; continue }
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
                el["error"]?.let { throw AiError(AiError.Kind.SERVER, "Gemini ha cortado la respuesta.") }
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
            code == 404 -> AiError(AiError.Kind.BAD_REQUEST, "El modelo no existe. Revisa el modelo en Ajustes › IA › Avanzado.")
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

    private fun transcribePrecise(d: AppData, audio: ByteArray): String {
        val uri = uploadAudio(audio)
        val vocab = (listOf("Órbita Webs", "Vaelor", "BertoProject", "UpGrade") +
            d.areas.map { it.name } + d.subjects.map { it.name } + d.aiVocab).distinct().take(100)
        val body = buildJsonObject {
            put("model", TRANSCRIBE_MODEL)
            putJsonArray("input") {
                addJsonObject {
                    put("type", "audio")
                    put("uri", uri)
                    put("mime_type", "audio/aac")
                }
            }
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
        return steps.flatMap { s ->
            val o = s as? JsonObject ?: return@flatMap emptyList()
            val content = o["content"] as? JsonArray
            if (content != null) content.mapNotNull { c -> (c as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            else listOfNotNull((o["text"] as? JsonPrimitive)?.contentOrNull)
        }.joinToString(" ").trim()
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
            val text = generate(model(d), body)
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
        val subject = o.str("asignatura")?.lowercase()?.let { s ->
            d.subjects.firstOrNull { it.id == s || it.name.equals(s, ignoreCase = true) }?.id
        }?.takeIf { area == "insti" }

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
            doubts = doubts.filter { it in DOUBT_FIELDS }.toSet()
        )
        // COMPLETAR y BORRAR no cambian campos: se enseña la tarea tal cual.
        return if ((op == Op.COMPLETAR || op == Op.BORRAR) && target != null) {
            p.copy(title = target.title, kind = target.kind, areaId = target.areaId, subjectId = target.subjectId,
                date = parseDate(target.due), time = target.time, doubts = emptySet())
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
                    put("asignatura", p.subjectId?.let { d.subject(it)?.id })
                    put("fecha", p.date?.toString())
                    put("hora", p.time?.let { minStr(it) })
                }
            }
        }
    }.toString()

    private val SYSTEM = """
Eres el organizador de UpGrade, la agenda de Alberto: estudiante de 1º de Bachillerato en Huelva (España) que además lleva un negocio de webs (Órbita Webs), una marca personal (BertoProject), está en el equipo de una comunidad de emprendedores (Vaelor) y va al gym.

Tu trabajo: convertir lo que Alberto dice en operaciones sobre su agenda. Devuelve JSON con "items": una lista de operaciones.

Cada operación:
- "op": "crear" para algo nuevo; "mover" para cambiar fecha u hora de algo que ya existe; "editar" para cambiar otros datos; "completar" si dice que ya lo ha hecho; "borrar" si dice que lo quites o que se cancela.
- "ref": para mover/editar/completar/borrar, la referencia (t1, t2…) de la tarea de la lista «Pendiente». Si encajan varias y no está claro cuál, deja "ref" en null y pon todas las que encajan en "candidatas".
- "titulo": corto y claro, en español (ej. "Examen de Lengua", "Llamada con el cliente de la pizzería", "Ejercicios pág. 54 de Mates").
- "tipo": EXAMEN, DEBERES, TRABAJO, TAREA o LLAMADA. Una llamada o reunión es LLAMADA.
- "area": una de las áreas dadas. Lo del instituto es "insti".
- "asignatura": solo si el área es "insti": el id de la asignatura; si no, null.
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

Ejemplos (con un calendario donde hoy es lunes 2026-03-02, mañana martes 2026-03-03, jueves 2026-03-05, viernes 2026-03-06):

Mensaje: "el jueves tengo examen de química de los temas 3 y 4 y mañana hay que entregar el writing de inglés"
{"items":[{"op":"crear","ref":null,"candidatas":[],"titulo":"Examen de Química (temas 3 y 4)","tipo":"EXAMEN","area":"insti","asignatura":"quimica","fecha":"2026-03-05","hora":null,"dudas":[]},{"op":"crear","ref":null,"candidatas":[],"titulo":"Entregar writing de inglés","tipo":"DEBERES","area":"insti","asignatura":"ingles","fecha":"2026-03-03","hora":null,"dudas":[]}]}

Mensaje: "llamar a Lola de la pizzería el viernes a las 6 para lo de la web"
{"items":[{"op":"crear","ref":null,"candidatas":[],"titulo":"Llamada con Lola (pizzería) por la web","tipo":"LLAMADA","area":"orbita","asignatura":null,"fecha":"2026-03-06","hora":"18:00","dudas":[]}]}

Mensaje (con «Pendiente: t3 = Llamada Vaelor · mié 2026-03-04 · 17:00 · vaelor · LLAMADA»): "pasa lo de Vaelor al viernes a la misma hora y ya he hecho lo de mates"
{"items":[{"op":"mover","ref":"t3","candidatas":[],"titulo":"Llamada Vaelor","tipo":"LLAMADA","area":"vaelor","asignatura":null,"fecha":"2026-03-06","hora":"17:00","dudas":[]}]}
(«lo de mates» no aparece en Pendiente, así que no se completa nada.)

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
    val refs: Map<String, String>
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
        if (pending.isEmpty()) appendLine("Pendiente: nada.")
        else {
            appendLine("Pendiente (ref = título · fecha · hora · área · tipo):")
            pending.forEach { (ref, t) ->
                val date = parseDate(t.due)?.let { "${dayShort(it)} $it" } ?: "sin fecha"
                appendLine("- $ref = ${t.title} · $date · ${t.time?.let { minStr(it) } ?: "sin hora"} · ${t.areaId} · ${t.kind.name}")
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
        fun build(d: AppData, today: LocalDate): PromptContext {
            val pending = d.tasks.asSequence()
                .filter { !it.done && it.kind != Kind.REPASO }
                .filter { t ->
                    val due = parseDate(t.due)
                    due == null ||
                        (!due.isBefore(today.minusDays(14)) && !due.isAfter(today.plusDays(14))) ||
                        (t.kind == Kind.EXAMEN && !due.isBefore(today) && !due.isAfter(today.plusDays(30)))
                }
                .sortedWith(taskOrder)
                .take(60)
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
