package com.alberto.upgrade.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Algo que el planificador propone añadir. */
data class Proposal(
    val title: String,
    val kind: Kind,
    val areaId: String,
    val subjectId: String?,
    val date: LocalDate?,
    val time: Int?
) {
    fun toTask(): TaskItem = TaskItem(
        id = Repo.newId(),
        title = title,
        kind = kind,
        areaId = areaId,
        subjectId = if (areaId == "insti") subjectId else null,
        due = date?.key(),
        time = time,
        createdAt = System.currentTimeMillis()
    )
}

data class PlanResult(val items: List<Proposal>, val usedAi: Boolean, val note: String?)

object Planner {

    suspend fun plan(d: AppData, text: String, today: LocalDate = LocalDate.now()): PlanResult {
        if (text.isBlank()) return PlanResult(emptyList(), false, null)
        if (d.geminiKey.isBlank()) {
            return PlanResult(LocalParser.parse(d, text, today), false, "Modo básico (sin IA). Pon tu clave de Gemini en Ajustes para que entienda frases largas y te organice el día con horas.")
        }
        return try {
            val items = Gemini.plan(d, text, today)
            PlanResult(items, true, null)
        } catch (e: Exception) {
            PlanResult(LocalParser.parse(d, text, today), false, "La IA ha fallado (${e.message?.take(80)}). He usado el modo básico.")
        }
    }
}

// ------------------------------------------------------------------
// IA: Gemini (clave del propio usuario, se guarda solo en el móvil)
// ------------------------------------------------------------------

object Gemini {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun plan(d: AppData, text: String, today: LocalDate): List<Proposal> = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(d, text, today)
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }.toString()

        val model = d.aiModel.ifBlank { "gemini-flash-latest" }
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                URLEncoder.encode(model, "UTF-8") + ":generateContent"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 45_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", d.geminiKey.trim())
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching {
                    json.parseToJsonElement(resp).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw IllegalStateException("HTTP $code ${msg ?: ""}".trim())
            }
            val out = json.parseToJsonElement(resp).jsonObject["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
                ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
                ?: throw IllegalStateException("respuesta vacía")
            parseItems(d, out)
        } finally {
            conn.disconnect()
        }
    }

    private fun buildPrompt(d: AppData, text: String, today: LocalDate): String {
        val subjects = d.subjects.joinToString(", ") { "${it.id}=${it.name}" }
        val week = (0..6).joinToString("; ") { i ->
            val day = today.plusDays(i.toLong())
            "${dayName(day)} ${day}"
        }
        val schedule = (1..5).joinToString("; ") { dow ->
            val day = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dow)))
            val cls = classesOn(d, day)
            if (cls.isEmpty()) "" else "${dayName(day)} clases ${minStr(cls.first().period.start)}-${minStr(cls.last().period.end)}"
        }
        val events = d.events.joinToString("; ") { e ->
            val whenTxt = e.date ?: e.weekdays.joinToString(",") { DayOfWeek.of(it).name.lowercase() }
            "${e.title} ($whenTxt ${minStr(e.start)}-${minStr(e.end)})"
        }
        return """
Eres el planificador de UpGrade, la agenda de Alberto, estudiante de 1º de Bachillerato que además tiene un negocio de webs (Órbita Webs), una marca personal (BertoProject), está en el equipo de una comunidad de emprendedores (Vaelor) y va al gym 4 días por semana (1 h + 26 min de camino).

Hoy es ${dayName(today)} $today. Próximos días: $week.
Horario de clases: $schedule.
Eventos fijos: ${events.ifBlank { "ninguno" }}.

Convierte el mensaje de Alberto en una lista JSON. Cada elemento es un objeto con:
- "title": texto corto y claro en español (ej. "Examen de Lengua", "Llamada con cliente de la pizzería", "Ejercicios pág. 54 de Mates").
- "kind": uno de "EXAMEN", "DEBERES", "TRABAJO", "TAREA", "LLAMADA".
- "area": uno de "insti" (todo lo del instituto/bachillerato), "orbita" (Órbita Webs, clientes, webs), "berto" (BertoProject, contenido, marca personal), "vaelor" (Vaelor, comunidad, formación), "gym", "personal".
- "subject": solo si area es "insti", uno de [$subjects], si no null.
- "date": "YYYY-MM-DD" o null si no hay fecha. Fechas relativas respecto a hoy: "el jueves" es el próximo jueves; "mañana" es el día siguiente.
- "time": "HH:MM" o null.

Reglas:
- Solo lo que Alberto dice. No inventes tareas.
- Si dice cosas en general para hoy o para mañana sin fecha, pon esa fecha.
- Si pide que le organices el día o la rutina, asigna a cada cosa de ese día una hora realista: fuera de clase (L-V hasta las 15:00), con descansos, sin solapar con eventos fijos ni entre sí, y lo más difícil o urgente primero. Si nombra el gym, inclúyelo como TAREA del área "gym" con hora.
- Una llamada o reunión es "LLAMADA".
- Responde SOLO con el array JSON, sin texto alrededor.

Mensaje de Alberto:
<<<
${text.trim()}
>>>
""".trimIndent()
    }

    private fun parseItems(d: AppData, raw: String): List<Proposal> {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root: JsonElement = json.parseToJsonElement(clean)
        val arr: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root.values.firstOrNull { it is JsonArray } as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun str(k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
            val title = str("title") ?: return@mapNotNull null
            val kind = runCatching { Kind.valueOf(str("kind")?.uppercase() ?: "TAREA") }.getOrDefault(Kind.TAREA)
            val area = str("area")?.lowercase()?.takeIf { a -> d.areas.any { it.id == a } } ?: "personal"
            val subject = str("subject")?.lowercase()?.takeIf { s -> d.subjects.any { it.id == s } }
            val date = parseDate(str("date"))
            val time = str("time")?.let { t ->
                val p = t.split(":")
                val h = p.getOrNull(0)?.toIntOrNull()
                val m = p.getOrNull(1)?.toIntOrNull() ?: 0
                if (h != null && h in 0..23 && m in 0..59) h * 60 + m else null
            }
            Proposal(title.take(120), kind, area, subject, date, time)
        }
    }
}

// ------------------------------------------------------------------
// Modo básico: reglas sencillas en español, sin internet
// ------------------------------------------------------------------

object LocalParser {
    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private val subjectWords = listOf(
        "mates" to listOf("mates", "matematicas", "matematica"),
        "lengua" to listOf("lengua", "literatura"),
        "ingles" to listOf("ingles", "english"),
        "historia" to listOf("historia"),
        "filosofia" to listOf("filosofia", "filo"),
        "economia" to listOf("economia"),
        "cultura" to listOf("cultura"),
        "tic" to listOf("tic", "informatica"),
        "ef" to listOf("educacion fisica", "e.f", "gimnasia"),
        "religion" to listOf("religion")
    )

    private val weekdays = listOf("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")
    private val months = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    private fun hasWord(t: String, w: String) = Regex("(^|[^a-z])" + Regex.escape(w) + "([^a-z]|$)").containsMatchIn(t)

    fun parse(d: AppData, text: String, today: LocalDate): List<Proposal> {
        val segments = text
            .replace(Regex("(?i)\\s+(y luego|y despues|y después|despues|después|luego|ademas|además)\\s+"), "\n")
            .split(Regex("[\\n.;]+|,\\s+(?=[a-záéíóúñ])"))
            .map { it.trim() }
            .filter { it.length >= 3 }
        return segments.mapNotNull { seg -> parseOne(d, seg, today) }
    }

    private fun parseOne(d: AppData, seg: String, today: LocalDate): Proposal? {
        val t = norm(seg)
        val subject = subjectWords.firstOrNull { (_, ws) -> ws.any { hasWord(t, it) } }?.first
            ?.takeIf { s -> d.subjects.any { it.id == s } }

        val kind = when {
            listOf("examen", "control", "prueba", "parcial").any { hasWord(t, it) } -> Kind.EXAMEN
            listOf("llamada", "llamar", "reunion", "call", "videollamada").any { t.contains(it) } -> Kind.LLAMADA
            listOf("trabajo", "proyecto", "exposicion", "redaccion", "presentacion").any { hasWord(t, it) } && subject != null -> Kind.TRABAJO
            listOf("deberes", "ejercicios", "ejercicio", "actividades", "resumen", "esquema").any { hasWord(t, it) } || subject != null -> Kind.DEBERES
            else -> Kind.TAREA
        }

        val area = when {
            subject != null || kind == Kind.EXAMEN || kind == Kind.DEBERES || kind == Kind.TRABAJO -> "insti"
            listOf("orbita", "cliente", "web", "pagina").any { t.contains(it) } -> "orbita"
            listOf("vaelor", "comunidad", "formacion").any { t.contains(it) } -> "vaelor"
            listOf("berto", "contenido", "video", "reel", "tiktok", "post", "marca").any { t.contains(it) } -> "berto"
            listOf("gym", "gimnasio", "entreno", "entrenar", "pierna", "torso").any { t.contains(it) } -> "gym"
            else -> "personal"
        }

        val date = findDate(t, today)
        val time = findTime(t)

        val title = when {
            kind == Kind.EXAMEN && subject != null -> "Examen de ${d.subject(subject)?.name ?: subject}"
            else -> seg.trim().trimEnd('.', ',').cap()
        }
        return Proposal(title, kind, area, subject, date, time)
    }

    private fun findDate(t: String, today: LocalDate): LocalDate? {
        if (hasWord(t, "pasado manana")) return today.plusDays(2)
        val tomorrow = Regex("(^|[^a-z])manana([^a-z]|$)").containsMatchIn(t) &&
            !Regex("(por|de|esta|la) manana").containsMatchIn(t)
        if (tomorrow) return today.plusDays(1)
        if (hasWord(t, "hoy") || hasWord(t, "esta tarde") || hasWord(t, "esta noche")) return today
        weekdays.forEachIndexed { i, w ->
            if (hasWord(t, w)) {
                return today.with(TemporalAdjusters.next(DayOfWeek.of(i + 1)))
            }
        }
        Regex("(\\d{1,2}) de (" + months.joinToString("|") + ")").find(t)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = months.indexOf(m.groupValues[2]) + 1
            return runCatching {
                var dt = LocalDate.of(today.year, month, day)
                if (dt.isBefore(today)) dt = dt.plusYears(1)
                dt
            }.getOrNull()
        }
        Regex("(?:el|dia) (\\d{1,2})([^:\\d]|$)").find(t)?.let { m ->
            val day = m.groupValues[1].toInt()
            return runCatching {
                var dt = today.withDayOfMonth(day)
                if (dt.isBefore(today)) dt = dt.plusMonths(1)
                dt
            }.getOrNull()
        }
        return null
    }

    private fun findTime(t: String): Int? {
        Regex("(\\d{1,2})[:.h](\\d{2})").find(t)?.let { m ->
            val h = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt()
            if (h in 0..23 && mm in 0..59) return h * 60 + mm
        }
        Regex("a las (\\d{1,2})(?: y (media|cuarto))?(?: de la (tarde|noche|manana))?").find(t)?.let { m ->
            var h = m.groupValues[1].toInt()
            val extra = when (m.groupValues[2]) { "media" -> 30; "cuarto" -> 15; else -> 0 }
            val part = m.groupValues[3]
            if ((part == "tarde" || part == "noche") && h < 12) h += 12
            if (part.isEmpty() && h in 1..7) h += 12  // "a las 5" casi siempre es por la tarde
            if (h in 0..23) return h * 60 + extra
        }
        return null
    }
}
