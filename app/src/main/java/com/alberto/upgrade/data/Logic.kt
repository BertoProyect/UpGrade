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

/** "Hoy", "Mañana", "en 3 días", "lun 10 nov", "Atrasada 2 d" */
fun dueLabel(due: LocalDate?, today: LocalDate = LocalDate.now()): String {
    if (due == null) return "Sin fecha"
    val diff = ChronoUnit.DAYS.between(today, due)
    return when {
        diff < 0 -> if (diff == -1L) "Ayer" else "Hace ${-diff} días"
        diff == 0L -> "Hoy"
        diff == 1L -> "Mañana"
        diff < 7 -> "${dayShort(due).cap()} (en $diff días)"
        else -> "${dayShort(due)} ${due.dayOfMonth} ${monthShort(due)}"
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

fun xpOn(d: AppData, date: LocalDate): Int = d.xpLog[date.key()] ?: 0
fun goalMet(d: AppData, date: LocalDate): Boolean = xpOn(d, date) >= d.dailyGoal

fun streakOf(d: AppData, today: LocalDate = LocalDate.now()): Int {
    var day = if (goalMet(d, today)) today else today.minusDays(1)
    var n = 0
    while (goalMet(d, day) && n < 3650) {
        n++
        day = day.minusDays(1)
    }
    return n
}

fun gymThisWeek(d: AppData, today: LocalDate = LocalDate.now()): Int {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return d.gymLog.keys.mapNotNull { parseDate(it) }.count { !it.isBefore(monday) && !it.isAfter(today) }
}

fun nextGymType(d: AppData): String {
    val last = d.gymLog.entries.maxByOrNull { it.key }?.value
    return if (last == "Torso") "Pierna" else "Torso"
}

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
