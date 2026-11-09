package com.alberto.upgrade.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Lo que la app apunta de cómo la usas. Solo contadores por día (nada de registros sueltos),
// así el archivo no crece y no hay nada más fino de lo que hace falta para ver patrones.
// ---------------------------------------------------------------------------------------------

@Serializable
data class DayUsage(
    /** Veces que abres UpGrade (dos aperturas a menos de 2 min cuentan como una). */
    val opens: Int = 0,
    val openHours: List<Int> = emptyList(),
    /** Desbloqueos del móvil y minuto del primero del día (-1 si no hay). */
    val unlocks: Int = 0,
    val firstUnlock: Int = -1,
    val unlockHours: List<Int> = emptyList(),
    /** Veces que has intentado abrir una app bloqueada o con espera, y cuáles. */
    val blocks: Int = 0,
    val blockHours: List<Int> = emptyList(),
    val blockApps: Map<String, Int> = emptyMap(),
    /** «Necesito el móvil» pedido. */
    val waits: Int = 0,
    val focusStarts: Int = 0,
    val focusQuits: Int = 0,
    val focusCancels: Int = 0,
    val routineCancels: Int = 0
)

/** Una recomendación de la IA. [status]: "new", "trying" (la estás probando) o "dismissed". */
@Serializable
data class Insight(
    val id: String,
    val title: String,
    val detail: String,
    val evidence: String = "",
    /** foco, rutina, apps, anadir, meta, cerrar_dia, repaso o ninguna. */
    val action: String = "ninguna",
    val priority: Int = 2,
    val status: String = "new",
    val since: Long = 0
)

@Serializable
data class InsightReport(
    val at: Long,
    val summary: String,
    val items: List<Insight>,
    val days: Int = 0,
    val model: String = ""
)

internal fun List<Int>.bump(hour: Int): List<Int> =
    (if (size == 24) toMutableList() else MutableList(24) { 0 }).also { it[hour.coerceIn(0, 23)]++ }

internal fun Map<String, DayUsage>.prune(today: LocalDate, keep: Long = 42): Map<String, DayUsage> {
    val from = today.minusDays(keep).key()
    return filterKeys { it >= from }
}

// ---------------------------------------------------------------------------------------------
// Patrones: se calculan en el móvil, con o sin IA.
// ---------------------------------------------------------------------------------------------

enum class Tone { GOOD, NEUTRAL, WARN }

/** Una línea de «Lo que dicen tus datos». [icon] es una clave de [com.alberto.upgrade.ui.iconByKey] o de Habits. */
data class HabitFact(val icon: String, val title: String, val value: String, val note: String, val tone: Tone = Tone.NEUTRAL)

data class HabitStats(
    val days: Int,
    val facts: List<HabitFact>,
    /** Resumen en texto plano que se manda a Gemini (y que se puede ver en la app). */
    val prompt: String
)

object Habits {
    const val WINDOW = 28

    private fun firstActivity(d: AppData): LocalDate? {
        val c = mutableListOf<LocalDate>()
        d.xpLog.keys.mapNotNullTo(c) { parseDate(it) }
        d.usageDays.keys.mapNotNullTo(c) { parseDate(it) }
        d.tasks.filter { it.createdAt > 0 }.mapTo(c) { dateOf(it.createdAt) }
        return c.minOrNull()
    }

    private fun franja(min: Int): String = when (min / 60) {
        in 6..13 -> "mañana"
        in 14..19 -> "tarde"
        in 20..23 -> "noche"
        else -> "madrugada"
    }

    private fun pct(a: Int, b: Int): Int = if (b == 0) 0 else (a * 100.0 / b).roundToInt()
    private fun dow(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, ES)

    fun analyze(d: AppData, today: LocalDate = LocalDate.now()): HabitStats {
        val first = firstActivity(d) ?: today
        val start = maxOf(today.minusDays(WINDOW - 1L), first)
        val all = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        // Hoy aún no ha terminado: para metas y medias se usan los días cerrados.
        val past = all.filter { it.isBefore(today) }
        val inRange = { day: LocalDate -> !day.isBefore(start) && !day.isAfter(today) }
        val facts = mutableListOf<HabitFact>()
        val p = StringBuilder()

        p.appendLine("Hoy es ${dayName(today)} $today. Datos de los últimos ${all.size} días (desde $start).")
        if (past.size < 5) p.appendLine("OJO: hay muy pocos días de datos; sé prudente con las conclusiones.")

        // ---- Meta diaria y racha ----
        val met = past.count { goalMet(d, it) }
        val streak = streakOf(d, today)
        val avgXp = if (past.isEmpty()) 0 else past.sumOf { xpOn(d, it) } / past.size
        if (past.isNotEmpty()) {
            facts += HabitFact(
                "target", "Meta diaria cumplida", "$met de ${past.size} días",
                "Media de $avgXp XP al día (meta ${d.dailyGoal} entre semana, ${d.weekendGoal} el finde).",
                if (pct(met, past.size) >= 70) Tone.GOOD else if (pct(met, past.size) < 40) Tone.WARN else Tone.NEUTRAL
            )
        }
        p.appendLine("Meta diaria: cumplida $met de ${past.size} días cerrados (${pct(met, past.size)} %). Media $avgXp XP/día. Meta ${d.dailyGoal} L-V y ${d.weekendGoal} S-D. Racha actual $streak días, récord ${maxOf(d.bestStreak, streak)}.")

        val byDow = past.groupBy { it.dayOfWeek }.mapValues { (_, ds) -> ds.count { goalMet(d, it) } to ds.size }
        val judged = byDow.filter { it.value.second >= 2 }
        if (judged.size >= 4) {
            val worst = judged.minByOrNull { it.value.first.toFloat() / it.value.second }!!
            val best = judged.maxByOrNull { it.value.first.toFloat() / it.value.second }!!
            if (worst.key != best.key) {
                facts += HabitFact(
                    "calendarDays", "Tu peor día", dow(worst.key).cap(),
                    "Meta cumplida ${worst.value.first} de ${worst.value.second} ${dow(worst.key)}s. El mejor: ${dow(best.key)} (${best.value.first} de ${best.value.second}).",
                    if (worst.value.first == 0) Tone.WARN else Tone.NEUTRAL
                )
            }
            p.appendLine("Meta por día de la semana (cumplidos/total): " + judged.entries.sortedBy { it.key }.joinToString(", ") { "${dow(it.key)} ${it.value.first}/${it.value.second}" } + ".")
        }

        // ---- Tareas: a tiempo, el último día o tarde ----
        val tasks = d.tasks.filter { it.kind != Kind.REPASO }
        val doneIn = tasks.filter { it.done && it.doneAt != null && inRange(dateOf(it.doneAt)) }
        val withDue = doneIn.mapNotNull { t -> parseDate(t.due)?.let { t to it } }
        val early = withDue.count { (t, due) -> dateOf(t.doneAt!!).isBefore(due) }
        val sameDay = withDue.count { (t, due) -> dateOf(t.doneAt!!) == due }
        val late = withDue.count { (t, due) -> dateOf(t.doneAt!!).isAfter(due) }
        if (withDue.size >= 3) {
            val last = pct(sameDay + late, withDue.size)
            facts += HabitFact(
                "hourglass", "Al límite o tarde", "$last %",
                "De ${withDue.size} cosas con fecha: $early con margen, $sameDay el mismo día y $late después.",
                if (last >= 60) Tone.WARN else if (last <= 30) Tone.GOOD else Tone.NEUTRAL
            )
        }
        p.appendLine("Tareas hechas en el periodo: ${doneIn.size}. Con fecha: ${withDue.size} → $early antes del día límite, $sameDay el mismo día límite, $late después de la fecha.")
        val leads = doneIn.filter { it.createdAt > 0 }.map { ChronoUnit.HOURS.between(java.time.Instant.ofEpochMilli(it.createdAt), java.time.Instant.ofEpochMilli(it.doneAt!!)) / 24.0 }
        if (leads.size >= 3) p.appendLine("Desde que apunta una tarea hasta que la hace pasan de media ${"%.1f".format(ES, leads.average())} días.")

        val doneMinutes = doneIn.map { minutesOf(it.doneAt!!) }
        if (doneMinutes.size >= 4) {
            val g = doneMinutes.groupingBy { franja(it) }.eachCount()
            val peak = g.maxByOrNull { it.value }!!
            val late22 = doneMinutes.count { it >= 22 * 60 || it < 5 * 60 }
            facts += HabitFact(
                "clock", "Cuándo tachas tareas", peak.key.cap(),
                "${pct(peak.value, doneMinutes.size)} % por la ${peak.key}" + if (late22 > 0) "; $late22 a partir de las 22:00." else ".",
                if (pct(late22, doneMinutes.size) >= 30) Tone.WARN else Tone.NEUTRAL
            )
            p.appendLine("Hora a la que marca tareas como hechas: " + g.entries.joinToString(", ") { "${it.key} ${it.value}" } + "; después de las 22:00: $late22.")
        }

        val overdue = tasks.filter { !it.done && parseDate(it.due)?.isBefore(today) == true }
        val undated = tasks.count { !it.done && it.due == null }
        val pendingSoon = tasks.count { t -> !t.done && parseDate(t.due)?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(7)) } == true }
        if (overdue.isNotEmpty()) {
            val byArea = overdue.groupingBy { d.area(it.areaId)?.name ?: it.areaId }.eachCount().entries.sortedByDescending { it.value }
            facts += HabitFact(
                "alert", "Atrasadas ahora", "${overdue.size}",
                byArea.take(3).joinToString(" · ") { "${it.key} ${it.value}" },
                Tone.WARN
            )
        }
        p.appendLine("Ahora mismo: ${overdue.size} atrasadas" + (if (overdue.isNotEmpty()) " (" + overdue.take(8).joinToString("; ") { "«${it.title}», ${d.area(it.areaId)?.name ?: it.areaId}, vencía ${it.due}" } + ")" else "") + ", $pendingSoon para los próximos 7 días, $undated sin fecha.")

        val examsNoPlan = pendingExams(d, today, 10).filter { (ex, _) -> d.tasks.none { it.examId == ex.id } }
        if (examsNoPlan.isNotEmpty()) {
            facts += HabitFact(
                "fileText", "Exámenes sin plan de repaso", "${examsNoPlan.size}",
                examsNoPlan.take(3).joinToString(" · ") { (ex, n) -> "${ex.title} (" + (if (n == 0L) "hoy" else if (n == 1L) "mañana" else "en $n días") + ")" },
                Tone.WARN
            )
            p.appendLine("Exámenes en los próximos 10 días SIN plan de repaso: " + examsNoPlan.joinToString("; ") { (ex, n) -> "«${ex.title}» en $n días" } + ".")
        }
        val examsPlan = pendingExams(d, today, 10).filter { (ex, _) -> d.tasks.any { it.examId == ex.id } }
        if (examsPlan.isNotEmpty()) {
            p.appendLine("Exámenes con plan de repaso: " + examsPlan.joinToString("; ") { (ex, _) ->
                val s = d.tasks.filter { it.examId == ex.id }
                "«${ex.title}»: ${s.count { it.done }}/${s.size} sesiones hechas"
            } + ".")
        }

        // ---- Foco ----
        val focusMin = all.sumOf { d.focusLog[it.key()] ?: 0 }
        val focusDays = past.count { (d.focusLog[it.key()] ?: 0) >= 30 }
        val focusEvents = d.xpEvents.filter { it.kind == "focus" && !it.label.startsWith("Tiempo extra") && parseDate(it.date)?.let(inRange) == true }
        val usage = all.mapNotNull { d.usageDays[it.key()] }
        val quits = usage.sumOf { it.focusQuits }
        val cancels = usage.sumOf { it.focusCancels }
        val starts = usage.sumOf { it.focusStarts }
        facts += HabitFact(
            "timer", "Foco", "${focusMin / 60} h ${focusMin % 60} min",
            "$focusDays de ${past.size.coerceAtLeast(1)} días con 30 min o más" + if (quits + cancels > 0) " · $quits rendiciones y $cancels cancelados de $starts empezados." else ".",
            if (past.isNotEmpty() && pct(focusDays, past.size) < 30) Tone.WARN else Tone.NEUTRAL
        )
        p.appendLine("Foco: $focusMin min en total, ${focusEvents.size} sesiones cobradas, $focusDays días con ≥30 min de ${past.size}. Desde que se mide: $starts empezados, $quits rendidos a medias, $cancels cancelados al principio.")
        if (focusEvents.size >= 3) {
            val g = focusEvents.groupingBy { franja(minutesOf(it.at)) }.eachCount()
            p.appendLine("Franja en la que acaba los focos: " + g.entries.joinToString(", ") { "${it.key} ${it.value}" } + ".")
        }

        // ---- Rutinas ----
        val runs = d.routineLog.filter { parseDate(it.date)?.let(inRange) == true }
        if (d.routines.isNotEmpty()) {
            val parts = d.routines.filter { it.enabled }.map { r ->
                val rr = runs.filter { it.routineId == r.id }
                "«${r.name}» (${routineSummary(r)}): ${rr.count { it.status == "done" }} cumplidas, ${rr.count { it.status == "cancelled" }} canceladas"
            }
            if (parts.isNotEmpty()) p.appendLine("Rutinas activas: " + parts.joinToString("; ") + ".")
            val cancelled = runs.count { it.status == "cancelled" }
            val done = runs.count { it.status == "done" }
            if (done + cancelled > 0) {
                facts += HabitFact(
                    "repeat", "Rutinas cumplidas", "$done de ${done + cancelled}",
                    if (cancelled > 0) "$cancelled canceladas en los primeros 5 min." else "Sin cancelar ninguna.",
                    if (cancelled > done) Tone.WARN else Tone.NEUTRAL
                )
            }
        } else p.appendLine("No tiene rutinas de bloqueo.")

        // ---- Gym y fisio ----
        val weeks = (all.size / 7.0).coerceAtLeast(1.0)
        val gym = d.gymLog.keys.count { k -> parseDate(k)?.let(inRange) == true }
        val fisio = d.fisioLog.count { k -> parseDate(k)?.let(inRange) == true }
        if (d.shows("gym")) {
            val gymTarget = (d.gymWeeklyTarget * weeks).roundToInt()
            val fisioTarget = (d.fisioWeeklyTarget * weeks).roundToInt()
            facts += HabitFact(
                "dumbbell", "Gym y fisio", "$gym · $fisio",
                "Gym $gym de ~$gymTarget sesiones previstas · fisio $fisio de ~$fisioTarget días.",
                if (gym < gymTarget * 0.6 || fisio < fisioTarget * 0.6) Tone.WARN else Tone.GOOD
            )
            p.appendLine("Gym: $gym sesiones (objetivo ${d.gymWeeklyTarget}/semana ≈ $gymTarget). Fisio: $fisio días (objetivo ${d.fisioWeeklyTarget}/semana ≈ $fisioTarget).")
        }

        // ---- Móvil y uso de la app ----
        if (usage.isNotEmpty()) {
            val n = usage.size
            val unlocks = usage.sumOf { it.unlocks }
            val firsts = usage.map { it.firstUnlock }.filter { it >= 0 }
            val blocks = usage.sumOf { it.blocks }
            val waits = usage.sumOf { it.waits }
            val opens = usage.sumOf { it.opens }
            val apps = mutableMapOf<String, Int>()
            usage.forEach { u -> u.blockApps.forEach { (k, v) -> apps[k] = (apps[k] ?: 0) + v } }
            val topApps = apps.entries.sortedByDescending { it.value }.take(4)
            val blockHours = IntArray(24).also { a -> usage.forEach { u -> u.blockHours.forEachIndexed { i, v -> if (i < 24) a[i] += v } } }
            val peakBlock = blockHours.indices.maxByOrNull { blockHours[it] }?.takeIf { blockHours[it] > 0 }
            val openHours = IntArray(24).also { a -> usage.forEach { u -> u.openHours.forEachIndexed { i, v -> if (i < 24) a[i] += v } } }
            val peakOpen = openHours.indices.maxByOrNull { openHours[it] }?.takeIf { openHours[it] > 0 }

            if (blocks > 0) {
                facts += HabitFact(
                    "shieldBan", "Intentos de abrir apps bloqueadas", "${"%.1f".format(ES, blocks / n.toDouble())} al día",
                    topApps.joinToString(" · ") { "${it.key} ${it.value}" } + (peakBlock?.let { " · sobre todo a las ${it}h" } ?: ""),
                    if (blocks / n.toDouble() >= 5) Tone.WARN else Tone.NEUTRAL
                )
            }
            if (unlocks > 0) {
                facts += HabitFact(
                    "phone", "Desbloqueos del móvil", "${unlocks / n} al día",
                    if (firsts.isNotEmpty()) "El primero, de media a las ${minStr(firsts.average().roundToInt())}." else "",
                    Tone.NEUTRAL
                )
            }
            p.appendLine("Uso medido en $n días: abre UpGrade $opens veces (${"%.1f".format(ES, opens / n.toDouble())}/día" + (peakOpen?.let { ", sobre todo a las ${it}h" } ?: "") + "). " +
                "Desbloquea el móvil ${unlocks / n} veces/día" + (if (firsts.isNotEmpty()) ", primer desbloqueo de media a las ${minStr(firsts.average().roundToInt())}" else "") + ". " +
                "Intentos de abrir apps bloqueadas o con espera: $blocks (" + topApps.joinToString(", ") { "${it.key} ${it.value}" } + ")" +
                (peakBlock?.let { ", hora pico ${it}h" } ?: "") + ". «Necesito el móvil» pedido $waits veces. Rutinas canceladas: ${usage.sumOf { it.routineCancels }}.")
        } else {
            p.appendLine("Aún no hay datos de uso del móvil (se empiezan a medir con esta versión).")
        }

        // ---- De dónde sale el XP ----
        val xpEv = d.xpEvents.filter { it.amount > 0 && parseDate(it.date)?.let(inRange) == true }
        if (xpEv.isNotEmpty()) {
            val tot = xpEv.sumOf { it.amount }
            val names = mapOf("task" to "tareas", "exam" to "exámenes", "gym" to "gym", "fisio" to "fisio", "focus" to "foco", "routine" to "rutinas", "review" to "cerrar el día", "other" to "otros")
            val byKind = xpEv.groupBy { it.kind }.mapValues { it.value.sumOf { e -> e.amount } }.entries.sortedByDescending { it.value }
            p.appendLine("Reparto del XP: " + byKind.joinToString(", ") { "${names[it.key] ?: it.key} ${pct(it.value, tot)} %" } + ".")
            val capHit = past.count { taskXpOn(d, it.key()) >= d.taskCap && d.taskCap > 0 }
            if (capHit > 0) p.appendLine("Días en los que llegó al tope de XP por tareas (${d.taskCap}): $capHit.")
        }

        // ---- Cerrar el día ----
        val reviews = past.mapNotNull { d.reviews[it.key()] }
        if (past.isNotEmpty()) {
            p.appendLine("Ha cerrado el día ${reviews.size} de ${past.size} días" + if (reviews.isNotEmpty()) ", nota media ${"%.1f".format(ES, reviews.map { it.rating }.average())}/5." else ".")
            if (past.size >= 5) {
                facts += HabitFact(
                    "moon", "Días cerrados", "${reviews.size} de ${past.size}",
                    if (reviews.isNotEmpty()) "Te pones de media un ${"%.1f".format(ES, reviews.map { it.rating }.average())} sobre 5." else "Cerrar el día deja apuntado lo primero de mañana.",
                    if (pct(reviews.size, past.size) < 30) Tone.WARN else Tone.NEUTRAL
                )
            }
        }

        // ---- Recomendaciones anteriores ----
        val prev = (d.insightHistory + (d.insights?.items ?: emptyList()).filter { it.status != "new" }).distinctBy { it.title.lowercase() }
        if (prev.isNotEmpty()) {
            p.appendLine("Recomendaciones anteriores y lo que hizo Alberto:")
            prev.takeLast(8).forEach { i ->
                val st = if (i.status == "trying") "la está probando desde ${if (i.since > 0) dateOf(i.since).toString() else "hace poco"}" else "la descartó"
                p.appendLine("- «${i.title}»: $st")
            }
        }

        return HabitStats(all.size, facts, p.toString().trim())
    }
}
