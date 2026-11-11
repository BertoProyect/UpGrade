package com.alberto.upgrade.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Un hábito: algo que repites (gym, leer, fisio…). Se marca como hecho por día.
 * [schedule]: "daily" todos los días · "days" días concretos ([days]) · "weekly" [perWeek] veces por semana, cuando quieras.
 */
@Serializable
data class Habit(
    val id: String,
    val name: String,
    /** Clave Lucide del icono (ver habitIcons). */
    val icon: String = "circle-check",
    val schedule: String = "daily",
    /** 1 = lunes … 7 = domingo (solo para "days"). */
    val days: List<Int> = (1..7).toList(),
    val perWeek: Int = 3,
    val xp: Int = 10,
    /** Opciones al marcarlo (p. ej. Torso / Pierna). Vacío = un botón «Hecho». */
    val variants: List<String> = emptyList(),
    /** Meta concreta que se enseña debajo del nombre (p. ej. «20 min», «10 páginas»). */
    val goal: String = "",
    val archived: Boolean = false,
    /** Día de alta (yyyy-MM-dd): antes de él no se le exige nada para la racha. */
    val since: String = ""
)

const val SCHEDULE_DAILY = "daily"
const val SCHEDULE_DAYS = "days"
const val SCHEDULE_WEEKLY = "weekly"

fun activeHabits(d: AppData): List<Habit> = d.habits.filter { !it.archived }

fun habitDone(d: AppData, h: Habit, date: LocalDate): Boolean = d.habitLog[h.id]?.containsKey(date.key()) == true

fun habitVariantOn(d: AppData, h: Habit, date: LocalDate): String? = d.habitLog[h.id]?.get(date.key())

private fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Veces hecho esta semana (lunes → [today]). */
fun habitWeekCount(d: AppData, h: Habit, today: LocalDate = LocalDate.now()): Int {
    val m = monday(today)
    return d.habitLog[h.id]?.keys?.mapNotNull { parseDate(it) }?.count { !it.isBefore(m) && !it.isAfter(today) } ?: 0
}

/** Objetivo semanal: 7, los días marcados o las veces elegidas. */
fun habitWeekTarget(h: Habit): Int = when (h.schedule) {
    SCHEDULE_DAILY -> 7
    SCHEDULE_DAYS -> h.days.size.coerceAtLeast(1)
    else -> h.perWeek.coerceIn(1, 7)
}

/** ¿Toca ese día de forma fija? (Los de «N veces por semana» no tocan ningún día concreto.) */
fun habitScheduledOn(h: Habit, date: LocalDate): Boolean = when (h.schedule) {
    SCHEDULE_DAILY -> true
    SCHEDULE_DAYS -> date.dayOfWeek.value in h.days
    else -> false
}

/** ¿Sale hoy en la lista de «toca hoy»? Fijos si tocan; semanales mientras no hayas llegado al objetivo (o si ya lo hiciste hoy). */
fun habitDueToday(d: AppData, h: Habit, today: LocalDate = LocalDate.now()): Boolean =
    habitScheduledOn(h, today) || (h.schedule == SCHEDULE_WEEKLY && (habitWeekCount(d, h, today) < habitWeekTarget(h) || habitDone(d, h, today)))

/** ¿Lo exige la racha ese día? Solo los fijos que tocan y que ya existían. */
fun habitRequiredOn(h: Habit, date: LocalDate): Boolean {
    if (h.archived || !habitScheduledOn(h, date)) return false
    val since = parseDate(h.since) ?: return true
    return !date.isBefore(since)
}

fun habitSummary(h: Habit): String = when (h.schedule) {
    SCHEDULE_DAILY -> "Todos los días"
    SCHEDULE_DAYS -> when {
        h.days.size == 7 -> "Todos los días"
        h.days.sorted() == listOf(1, 2, 3, 4, 5) -> "De lunes a viernes"
        h.days.sorted() == listOf(6, 7) -> "Fines de semana"
        else -> h.days.sorted().joinToString(" ") { listOf("L", "M", "X", "J", "V", "S", "D")[it - 1] }
    }
    else -> if (h.perWeek == 1) "1 vez por semana" else "${h.perWeek} veces por semana"
}

/** La variante que toca ahora: la siguiente a la última usada (Torso → Pierna → Torso…). */
fun nextVariant(d: AppData, h: Habit): String? {
    if (h.variants.isEmpty()) return null
    val last = d.habitLog[h.id]?.entries?.filter { it.value.isNotBlank() }?.maxByOrNull { it.key }?.value
    val i = h.variants.indexOf(last)
    return if (i < 0) h.variants.first() else h.variants[(i + 1) % h.variants.size]
}

// ───────────────────────── Racha total ─────────────────────────

/** Qué cumple un día para la racha total. */
data class DayCheck(
    val xp: Boolean,
    val routines: Boolean,
    val habits: Boolean,
    val focus: Boolean,
    /** Nombres de lo que falta (rutinas y hábitos), para enseñarlo. */
    val missingRoutines: List<String> = emptyList(),
    val missingHabits: List<String> = emptyList()
) {
    val complete: Boolean get() = xp && routines && habits && focus
    /** Cuántas de las cuatro condiciones se cumplen. */
    val score: Int get() = listOf(xp, routines, habits, focus).count { it }
}

/** Foco mínimo de un día: un bloque completo. */
fun focusMinFor(d: AppData): Int = d.focusBlock.coerceIn(5, 120)

fun dayCheck(d: AppData, date: LocalDate): DayCheck {
    val xp = goalMet(d, date)
    val from = parseDate(d.streakRulesFrom)
    // Antes de las reglas nuevas, un día valía con la meta de XP.
    if (from == null || date.isBefore(from)) return DayCheck(xp, true, true, true)
    val routines = routinesOn(d, date)
    val missingR = routines.filter { r -> runOf(d, r.id, date)?.status != "done" }.map { it.name }
    val missingH = activeHabits(d).filter { h -> habitRequiredOn(h, date) && !habitDone(d, h, date) }.map { it.name }
    val focus = (d.focusLog[date.key()] ?: 0) >= focusMinFor(d)
    return DayCheck(xp, missingR.isEmpty(), missingH.isEmpty(), focus, missingR, missingH)
}

fun dayComplete(d: AppData, date: LocalDate): Boolean = dayCheck(d, date).complete

/**
 * Racha total. Un día suma si cumples todo. Fallar un día suelto no la rompe (ese día no suma);
 * fallar dos días seguidos, sí. Hoy, mientras no acabe, nunca cuenta como fallo.
 */
data class StreakInfo(
    val days: Int,
    val todayDone: Boolean,
    /** Ayer fallaste: si hoy fallas también, se rompe. */
    val atRisk: Boolean,
    /** Días fallados que la racha ha perdonado. */
    val forgiven: Int
)

fun streakInfo(d: AppData, today: LocalDate = LocalDate.now()): StreakInfo {
    val todayDone = dayComplete(d, today)
    var n = if (todayDone) 1 else 0
    var forgiven = 0
    var day = today.minusDays(1)
    var prevMissed = false
    var yesterdayMissed = false
    var first = true
    var i = 0
    while (i < 3650) {
        if (dayComplete(d, day)) {
            n++
            prevMissed = false
        } else {
            if (first) yesterdayMissed = true
            if (prevMissed) { forgiven = (forgiven - 1).coerceAtLeast(0); break }
            prevMissed = true
            forgiven++
        }
        first = false
        day = day.minusDays(1)
        i++
    }
    // Si la racha es 0, no hay nada que perdonar ni que perder.
    if (n == 0) return StreakInfo(0, false, false, 0)
    return StreakInfo(n, todayDone, yesterdayMissed && !todayDone, forgiven)
}
