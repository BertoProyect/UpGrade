package com.alberto.upgrade.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val ES: Locale = Locale.forLanguageTag("es-ES")

fun todayKey(): String = LocalDate.now().toString()
fun LocalDate.key(): String = toString()
fun parseDate(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
fun nowMin(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
fun minStr(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

fun dayName(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, ES)
fun dayShort(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')
fun monthName(d: LocalDate): String = d.month.getDisplayName(TextStyle.FULL_STANDALONE, ES)
fun monthShort(d: LocalDate): String = d.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')
fun String.cap(): String = replaceFirstChar { it.titlecase(ES) }

/** "Hoy", "Mañana", "Jueves 5", "Lun 10 nov", "ayer", "hace 3 días" */
fun dueLabel(due: LocalDate?, today: LocalDate = LocalDate.now()): String {
    if (due == null) return "Sin fecha"
    val diff = ChronoUnit.DAYS.between(today, due)
    return when {
        diff < 0 -> if (diff == -1L) "ayer" else "hace ${-diff} días"
        diff == 0L -> "Hoy"
        diff == 1L -> "Mañana"
        diff < 7 -> "${dayName(due).cap()} ${due.dayOfMonth}"
        else -> "${dayShort(due).cap()} ${due.dayOfMonth} ${monthShort(due)}"
    }
}

fun daysUntil(due: LocalDate, today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, due)

// ---------- Gamificación ----------

data class LevelInfo(val level: Int, val into: Int, val need: Int)

fun levelOf(totalXp: Int): LevelInfo {
    var level = 1
    var need = 100
    var acc = 0
    while (totalXp >= acc + need) {
        acc += need
        level++
        need += 50
    }
    return LevelInfo(level, totalXp - acc, need)
}

/** XP de un bloque de foco de [block] min: [XpConfig.focus30] por cada 30 min, en proporción (mínimo 1). */
fun blockXp(d: AppData, block: Int = d.focusBlock): Int = Math.round(d.xp.focus30 * block / 30f).coerceAtLeast(1)

/** Foco: XP por cada bloque completo; por debajo de un bloque, nada. */
fun focusXp(d: AppData, minutes: Int, block: Int = d.focusBlock): Int {
    val b = block.coerceIn(5, 120)
    return if (minutes < b) 0 else (minutes / b) * blockXp(d, b)
}

/** Tiempo extra: al acabar el foco el reloj sigue y suma [XpConfig.focusExtra10] por cada 10 min, hasta 60 min. */
const val EXTRA_BLOCK_MIN = 10
const val EXTRA_MAX_MIN = 60

fun extraEnd(d: AppData): Long = d.focus.endAt + EXTRA_MAX_MIN * 60_000L

/** ¿Está en tiempo extra? Incluye el segundo justo después de acabar, antes de cobrar el foco. */
fun inFocusExtra(d: AppData, now: Long = System.currentTimeMillis()): Boolean =
    d.focus.endAt in 1..now && (d.focus.extra || !d.focus.rewarded) && now < extraEnd(d)

/** Foco en marcha: el tiempo elegido o el tiempo extra. Mientras dure, se bloquea. */
fun focusOn(d: AppData, now: Long = System.currentTimeMillis()): Boolean = d.focus.endAt > now || inFocusExtra(d, now)

/** Minutos de tiempo extra (0–60). */
fun extraMinutes(d: AppData, now: Long = System.currentTimeMillis()): Int =
    if (d.focus.endAt == 0L) 0 else ((now - d.focus.endAt) / 60_000L).toInt().coerceIn(0, EXTRA_MAX_MIN)

fun isWeekend(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

/** Meta del día: una para lunes a viernes y otra para el fin de semana. */
fun goalFor(d: AppData, date: LocalDate = LocalDate.now()): Int =
    (if (isWeekend(date)) d.weekendGoal else d.dailyGoal).coerceAtLeast(1)

fun xpOn(d: AppData, date: LocalDate): Int = d.xpLog[date.key()] ?: 0
fun goalMet(d: AppData, date: LocalDate): Boolean = xpOn(d, date) >= goalFor(d, date)

/** ¿Este tipo cuenta para el tope diario de tareas? */
fun countsForCap(k: Kind): Boolean = k != Kind.EXAMEN && k != Kind.REPASO

/** XP base de una tarea según su tipo. Los repasos solo dan el XP del foco. */
fun taskXpFor(d: AppData, k: Kind): Int = when (k) {
    Kind.EXAMEN -> Kind.EXAMEN.xp
    Kind.REPASO -> 0
    else -> d.xp.task
}

/** XP ganado hoy con tareas que cuentan para el tope. */
fun taskXpOn(d: AppData, dateKey: String = todayKey()): Int =
    d.tasks.filter { it.done && it.xpDate == dateKey && countsForCap(it.kind) }.sumOf { it.xpAwarded }

/** Cuánto XP de tareas queda hoy antes del tope (Int.MAX_VALUE sin tope). */
fun taskCapLeft(d: AppData, dateKey: String = todayKey()): Int =
    if (d.taskCap <= 0) Int.MAX_VALUE else (d.taskCap - taskXpOn(d, dateKey)).coerceAtLeast(0)

/** Lo que daría ahora mismo completar [t], con el tope aplicado. */
fun xpIfDone(d: AppData, t: TaskItem): Int {
    val base = taskXpFor(d, t.kind)
    return if (countsForCap(t.kind)) minOf(base, taskCapLeft(d)) else base
}

/** Racha total: ver [streakInfo]. */
fun streakOf(d: AppData, today: LocalDate = LocalDate.now()): Int = streakInfo(d, today).days

// ---------- Horario ----------

data class ClassToday(val index: Int, val period: Period, val subject: Subject)

fun classesOn(d: AppData, date: LocalDate): List<ClassToday> {
    val dow = date.dayOfWeek.value
    return d.timetable.filter { it.day == dow }
        .sortedBy { it.period }
        .mapNotNull { slot ->
            val p = d.periods.getOrNull(slot.period) ?: return@mapNotNull null
            val s = d.subject(slot.subjectId) ?: return@mapNotNull null
            ClassToday(slot.period, p, s)
        }
}

fun eventsOn(d: AppData, date: LocalDate): List<EventItem> =
    d.events.filter { e ->
        (e.date != null && e.date == date.key()) ||
            (e.date == null && date.dayOfWeek.value in e.weekdays)
    }.sortedBy { it.start }

fun tasksDueOn(d: AppData, date: LocalDate): List<TaskItem> =
    d.tasks.filter { it.due == date.key() }

fun pendingExams(d: AppData, today: LocalDate = LocalDate.now(), within: Int = 14): List<Pair<TaskItem, Long>> =
    d.tasks.filter { it.kind == Kind.EXAMEN && !it.done }
        .mapNotNull { t -> parseDate(t.due)?.let { t to daysUntil(it, today) } }
        .filter { it.second in 0..within.toLong() }
        .sortedBy { it.second }

/** Lo más urgente que no está hecho: exámenes cercanos, atrasado, hoy, mañana. */
fun mostUrgent(d: AppData, today: LocalDate = LocalDate.now()): TaskItem? {
    val pending = d.tasks.filter { !it.done }
    val exam = pendingExams(d, today, 3).firstOrNull()?.first
    if (exam != null) return exam
    return pending.filter { it.due != null }
        .sortedBy { it.due }
        .firstOrNull { (parseDate(it.due) ?: today) <= today.plusDays(1) }
        ?: pending.minByOrNull { it.due ?: "9999" }
}

// ---------- Áreas ----------

/** Qué tipos tiene sentido apuntar en cada área. */
fun kindsFor(areaId: String?): List<Kind> = when (areaId) {
    "insti" -> listOf(Kind.EXAMEN, Kind.DEBERES, Kind.TRABAJO)
    "orbita", "vaelor" -> listOf(Kind.TAREA, Kind.LLAMADA)
    "berto", "gym" -> listOf(Kind.TAREA)
    // Personal, "Otro" y las áreas propias: Tarea, Llamada (y Evento, que va aparte) y Otro.
    else -> listOf(Kind.TAREA, Kind.LLAMADA, Kind.OTRO)
}

/** Áreas que salen en la pestaña Áreas, en el orden del usuario (Gym va en «Esta semana»). */
fun listedAreas(d: AppData): List<Area> = d.areas.filter { it.id != "gym" && d.shows(it.id) }

fun isBuiltInArea(id: String): Boolean = id in setOf("insti", "orbita", "berto", "vaelor", "gym", "personal")

data class AreaSection(val title: String, val kinds: Set<Kind>)

fun sectionsFor(areaId: String): List<AreaSection> = when (areaId) {
    "insti" -> listOf(AreaSection("Exámenes", setOf(Kind.EXAMEN)), AreaSection("Deberes", setOf(Kind.DEBERES, Kind.TRABAJO, Kind.TAREA, Kind.REPASO)))
    "orbita", "vaelor" -> listOf(AreaSection("Llamadas", setOf(Kind.LLAMADA)), AreaSection("Tareas", setOf(Kind.TAREA, Kind.DEBERES, Kind.TRABAJO, Kind.EXAMEN, Kind.OTRO)))
    "berto" -> listOf(AreaSection("Tareas", Kind.entries.toSet()))
    else -> listOf(AreaSection("Tareas", Kind.entries.toSet() - Kind.LLAMADA), AreaSection("Llamadas", setOf(Kind.LLAMADA)))
}

/** Orden natural: fecha, luego hora, sin fecha al final. */
val taskOrder: Comparator<TaskItem> = compareBy<TaskItem>({ it.due == null }, { it.due }, { it.time ?: 9999 })
