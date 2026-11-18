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

/** Qué hace una propuesta de la IA sobre la agenda. */
enum class Op(val label: String) {
    CREAR("Crear"), MOVER("Mover"), EDITAR("Editar"), COMPLETAR("Completar"), BORRAR("Borrar")
}

/**
 * Algo que el planificador propone. Para CREAR, los campos son la tarea nueva. Para MOVER y EDITAR,
 * son el estado final de la tarea [targetId]. Si la IA no sabe a qué tarea se refiere, deja
 * [targetId] vacío y da las candidatas en [candidates].
 */
data class Proposal(
    val title: String,
    val kind: Kind,
    val areaId: String,
    val subjectId: String?,
    val date: LocalDate?,
    val time: Int?,
    val op: Op = Op.CREAR,
    val targetId: String? = null,
    val candidates: List<String> = emptyList(),
    /** Campos de los que la IA no está segura: titulo, tipo, area, asignatura, fecha, hora. */
    val doubts: Set<String> = emptySet(),
    /** Más asignaturas, si la tarea es de varias. */
    val extraSubjects: List<String> = emptyList()
) {
    val subjectIds: List<String> get() = (listOfNotNull(subjectId) + extraSubjects).distinct()

    val needsChoice: Boolean get() = op != Op.CREAR && targetId == null

    fun toTask(): TaskItem = TaskItem(
        id = Repo.newId(),
        title = title,
        kind = kind,
        areaId = areaId,
        subjectId = if (areaId == "insti") subjectId else null,
        extraSubjects = if (areaId == "insti" && subjectId != null) extraSubjects.filter { it != subjectId } else emptyList(),
        due = date?.key(),
        time = time,
        createdAt = System.currentTimeMillis()
    )

    /** Aplica un MOVER/EDITAR sobre la tarea existente. */
    fun applyTo(t: TaskItem): TaskItem = t.copy(
        title = title,
        kind = if (t.kind == Kind.REPASO) t.kind else kind,
        areaId = areaId,
        subjectId = if (areaId == "insti") subjectId else null,
        extraSubjects = if (areaId == "insti" && subjectId != null) extraSubjects.filter { it != subjectId } else emptyList(),
        due = date?.key(),
        time = time
    )
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

    /** Viñetas al principio de una línea: «- », «• », «* », «1. », «2) ». */
    private val bullet = Regex("^\\s*(?:[-–—•*·▪►]+|\\d{1,2}[.)])\\s+")

    /** Verbos que, en la cabecera de una lista («Imprimir:»), se aplican a cada línea. */
    private val actionVerbs = listOf(
        "imprimir", "comprar", "llevar", "traer", "recoger", "enviar", "mandar", "pagar", "pedir", "devolver",
        "preguntar", "buscar", "descargar", "subir", "grabar", "editar", "publicar", "reservar", "escanear", "fotocopiar",
        "entregar", "estudiar", "repasar", "terminar", "hacer", "acabar", "leer", "llamar a", "escribir a", "revisar"
    )

    /**
     * Separa el texto en tareas. Entiende listas con cabecera:
     * «Imprimir:\n- Tema 13 de Historia\n- Filosofía» → «Imprimir tema 13 de Historia», «Imprimir Filosofía».
     * También en una línea: «Imprimir: el tema 13, filosofía y dos PDFs».
     */
    fun parse(d: AppData, text: String, today: LocalDate): List<Proposal> =
        segments(d, text).mapNotNull { (seg, ctx) -> parseOne(d, seg, ctx, today) }

    /**
     * Trozos de texto que son una tarea cada uno, con su contexto (la cabecera que no es un verbo:
     * «Para mañana:», «Historia:»), que sirve para la fecha o la asignatura pero no entra en el título.
     */
    internal fun segments(d: AppData, text: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var header: String? = null
        for (raw in text.replace("\r", "").split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) { header = null; continue }
            val isItem = bullet.containsMatchIn(line)
            val body = line.replace(bullet, "").trim()
            // «Imprimir:» sola en su línea: cabecera de lo que viene debajo (hasta una línea en blanco).
            if (!isItem && body.endsWith(":") && body.length <= 60) { header = body.removeSuffix(":").trim(); continue }
            // «Imprimir: a, b y c» en una sola línea (solo si la cabecera es un verbo).
            val inline = Regex("^([^:]{2,40}):\\s*(.+)$").find(body)
            if (!isItem && inline != null && isVerb(inline.groupValues[1].trim())) {
                val h = inline.groupValues[1].trim()
                inline.groupValues[2].split(Regex(",\\s*|\\s+y\\s+|;\\s*")).map { it.trim() }.filter { it.length >= 2 }
                    .forEach { out += join(d, h, it) to "" }
                continue
            }
            val h = header
            if (h != null) {
                if (isVerb(h)) out += join(d, h, body) to ""
                else splitSentence(body).forEach { out += it to h }
                continue
            }
            splitSentence(body).forEach { out += it to "" }
        }
        header?.let { h -> if (out.isEmpty()) out += h to "" }
        return out.map { (a, b) -> a.trim().trimEnd('.', ',', ';') to b }.filter { it.first.length >= 2 }
    }

    private fun isVerb(h: String): Boolean { val n = norm(h); return actionVerbs.any { n == it || n.startsWith("$it ") } }

    /** Cabecera + elemento, con la primera letra del elemento en minúscula salvo nombres propios y asignaturas. */
    private fun join(d: AppData, header: String, item: String): String {
        val first = item.substringBefore(' ')
        val keep = first.length > 1 && (first.all { it.isUpperCase() || it.isDigit() } ||
            d.subjects.any { norm(it.name) == norm(first) } || d.areas.any { norm(it.name).startsWith(norm(first)) } ||
            subjectWords.any { (_, ws) -> norm(first) in ws })
        val body = if (keep) item else item.replaceFirstChar { it.lowercase() }
        return header.replaceFirstChar { it.titlecase() } + " " + body
    }

    private fun splitSentence(s: String): List<String> = s
        .replace(Regex("(?i)\\s+(y luego|y despues|y después|despues|después|luego|ademas|además)\\s+"), "\n")
        .split(Regex("[\\n.;]+|,\\s+(?=[a-záéíóúñ])"))
        .map { it.trim() }
        .filter { it.length >= 3 }

    private fun parseOne(d: AppData, seg: String, context: String, today: LocalDate): Proposal? {
        val t = norm(if (context.isBlank()) seg else "$seg · $context")
        val subject = subjectWords.firstOrNull { (_, ws) -> ws.any { hasWord(t, it) } }?.first
            ?.takeIf { s -> d.subjects.any { it.id == s } }

        // «Imprimir…», «Comprar…», «Llevar…»: es una tarea aunque nombre una asignatura.
        val action = actionVerbs.filter { it != "estudiar" && it != "repasar" && it != "hacer" && it != "terminar" && it != "acabar" && it != "leer" }
            .any { t.startsWith(it) }
        val kind = when {
            action && !listOf("examen", "control").any { hasWord(t, it) } -> if (t.startsWith("llamar")) Kind.LLAMADA else Kind.TAREA
            listOf("examen", "control", "prueba", "parcial").any { hasWord(t, it) } -> Kind.EXAMEN
            listOf("llamada", "llamar", "reunion", "call", "videollamada").any { t.contains(it) } -> Kind.LLAMADA
            listOf("trabajo", "proyecto", "exposicion", "redaccion", "presentacion").any { hasWord(t, it) } && subject != null -> Kind.TRABAJO
            listOf("deberes", "ejercicios", "ejercicio", "actividades", "resumen", "esquema").any { hasWord(t, it) } || subject != null -> Kind.DEBERES
            else -> Kind.TAREA
        }

        val area = when {
            subject != null || kind == Kind.EXAMEN || kind == Kind.DEBERES || kind == Kind.TRABAJO -> "insti"
            listOf("tema", "apuntes", "libro", "instituto", "profe", "clase").any { hasWord(t, it) } && kind != Kind.LLAMADA -> "insti"
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
