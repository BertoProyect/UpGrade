package com.alberto.upgrade.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Una rutina: una franja en la que se bloquean apps según el Control de apps.
 * Empieza a una hora fija o al primer desbloqueo del día; termina a una hora fija o tras una duración.
 */
@Serializable
data class Routine(
    val id: String,
    val name: String,
    /** Clave del icono (sunrise, book-open, moon, coffee, dumbbell, smartphone). */
    val icon: String = "sunrise",
    /** "wake" = Despertar sin dopamina (su XP es [XpConfig.wake]); "custom" = personalizada. */
    val type: String = "custom",
    /** 1 = lunes … 7 = domingo. */
    val days: List<Int> = (1..7).toList(),
    /** "fixed" o "unlock" (al primer desbloqueo a partir de [start]). */
    val startMode: String = "fixed",
    val start: Int = 17 * 60,
    /** "duration" o "fixed". */
    val endMode: String = "duration",
    val end: Int = 19 * 60,
    val duration: Int = 90,
    val enabled: Boolean = true,
    /** XP propio de una rutina personalizada (null = el de Ajustes). */
    val xp: Int? = null,
    /** Cambio que afloja la rutina: se aplica desde [pendingFrom]. */
    val pending: Routine? = null,
    val pendingFrom: String? = null,
    /** Borrado programado (aflojar): desaparece desde esta fecha. */
    val deleteFrom: String? = null
) {
    /** Minutos que dura cuando empieza a la hora [startMin]. */
    fun lengthFrom(startMin: Int): Int =
        if (endMode == "fixed") (if (end > startMin) end - startMin else 24 * 60 - startMin) else duration

    /** Duración nominal (para el XP y el resumen). */
    val nominal: Int get() = lengthFrom(start)
}

/** Lo que pasó con una rutina un día concreto. */
@Serializable
data class RoutineRun(
    val routineId: String,
    val date: String,
    val startedAt: Long,
    val endAt: Long,
    /** "running", "done" o "cancelled". */
    val status: String = "running",
    val xp: Int = 0
)

/** Listas del Control de apps. Lo que no está en ninguna va con espera. */
@Serializable
data class AppLists(
    val allowed: List<String> = emptyList(),
    val blocked: List<String> = emptyList(),
    /** Movimientos que aflojan (a una lista más permisiva): se aplican desde la fecha. */
    val pending: List<PendingApp> = emptyList(),
    /** Apps que ya se han visto (para marcar las nuevas). */
    val known: List<String> = emptyList()
)

@Serializable
data class PendingApp(val pkg: String, val list: String, val from: String)

/** Estado de la espera de "Necesito el móvil" en la sesión de bloqueo actual. */
@Serializable
data class WaitState(
    /** Sesión a la que pertenecen los usos (foco o rutina). */
    val session: String = "",
    val uses: Int = 0,
    val pkg: String? = null,
    val requestedAt: Long = 0,
    val waitMin: Int = 5,
    /** Cuándo se puede abrir y hasta cuándo. */
    val openFrom: Long = 0,
    val openUntil: Long = 0
)

const val LIST_ALLOWED = "allowed"
const val LIST_WAIT = "wait"
const val LIST_BLOCKED = "blocked"

fun minutesOf(ms: Long): Int {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    return t.hour * 60 + t.minute
}

fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun epochAt(date: LocalDate, minute: Int): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + minute * 60_000L

/** La rutina tal como vale el día [date] (con los cambios programados ya aplicados). */
fun Routine.effective(date: LocalDate): Routine? {
    if (deleteFrom != null && !date.isBefore(LocalDate.parse(deleteFrom))) return null
    val p = pending
    return if (p != null && pendingFrom != null && !date.isBefore(LocalDate.parse(pendingFrom))) p.copy(id = id) else this
}

fun routinesOn(d: AppData, date: LocalDate): List<Routine> =
    d.routines.mapNotNull { it.effective(date) }.filter { it.enabled && date.dayOfWeek.value in it.days }

/** XP que da una rutina al cumplirse (0 si es demasiado corta). */
fun routineXp(d: AppData, r: Routine, minutes: Int = r.nominal): Int = when {
    r.type == "wake" -> if (minutes >= 60) d.xp.wake else 0
    else -> if (minutes >= 30) (r.xp ?: d.xp.routine) else 0
}

fun minRoutineMinutes(r: Routine): Int = if (r.type == "wake") 60 else 30

/** La ejecución de hoy de una rutina, si existe. */
fun runOf(d: AppData, routineId: String, date: LocalDate = LocalDate.now()): RoutineRun? =
    d.routineLog.lastOrNull { it.routineId == routineId && it.date == date.key() }

/** Rutinas en curso ahora mismo. */
fun activeRuns(d: AppData, now: Long = System.currentTimeMillis()): List<RoutineRun> =
    d.routineLog.filter { it.status == "running" && now >= it.startedAt && now < it.endAt }

/** ¿Hay alguna sesión de bloqueo (foco o rutina) activa? */
fun blockSessionActive(d: AppData, now: Long = System.currentTimeMillis()): Boolean =
    d.focus.endAt > now || activeRuns(d, now).isNotEmpty()

/** Identificador de la sesión de bloqueo actual (para contar los usos de la espera). */
fun currentSession(d: AppData, now: Long = System.currentTimeMillis()): String? = when {
    d.focus.endAt > now -> "f:" + d.focus.startedAt
    else -> activeRuns(d, now).minByOrNull { it.startedAt }?.let { "r:" + it.routineId + ":" + it.startedAt }
}

/** Lista de una app, con los cambios programados ya aplicados. */
fun appListOf(d: AppData, pkg: String, today: LocalDate = LocalDate.now()): String {
    val p = d.apps.pending.lastOrNull { it.pkg == pkg && !today.isBefore(LocalDate.parse(it.from)) }
    if (p != null) return p.list
    return when (pkg) {
        in d.apps.allowed -> LIST_ALLOWED
        in d.apps.blocked -> LIST_BLOCKED
        else -> LIST_WAIT
    }
}

/** Cuánto más permisiva es una lista (0 = bloqueada, 2 = permitida). */
fun looseness(list: String): Int = when (list) { LIST_BLOCKED -> 0; LIST_WAIT -> 1; else -> 2 }

/** Espera para el siguiente uso en esta sesión: 5 → 10 → 15 min. */
fun nextWait(uses: Int): Int = when (uses) { 0 -> 5; 1 -> 10; else -> 15 }

/** ¿[n] es más estricta o igual que [o]? (más días, misma hora de inicio, igual o más larga, activa). */
fun isTightening(o: Routine, n: Routine): Boolean {
    if (o.enabled && !n.enabled) return false
    if (!n.days.containsAll(o.days)) return false
    if (n.startMode != o.startMode) return false
    if (n.start > o.start && n.startMode == "fixed") return false
    if (n.startMode == "unlock" && n.start != o.start) return false
    val oEnd = o.start + o.lengthFrom(o.start)
    val nEnd = n.start + n.lengthFrom(n.start)
    if (n.endMode == "fixed" && o.endMode == "fixed") return nEnd >= oEnd
    return n.lengthFrom(n.start) >= o.lengthFrom(o.start) && nEnd >= oEnd
}

/** Racha de una rutina: días programados seguidos cumplidos. Hoy, si aún no ha tocado o está en curso, no rompe. */
fun routineStreak(d: AppData, r: Routine, today: LocalDate = LocalDate.now()): Int {
    var day = today
    var n = 0
    repeat(400) {
        val eff = r.effective(day)
        val scheduled = eff != null && eff.enabled && day.dayOfWeek.value in eff.days
        if (scheduled) {
            if (runOf(d, r.id, day)?.status == "done") n++
            else if (day != today) return n
        }
        day = day.minusDays(1)
    }
    return n
}

/** Un día cuenta para la racha de foco si se cumplió una rutina o se hicieron ≥ 30 min de foco. */
fun focusDayMet(d: AppData, date: LocalDate): Boolean =
    (d.focusLog[date.key()] ?: 0) >= 30 || d.routineLog.any { it.date == date.key() && it.status == "done" }

fun focusStreakOf(d: AppData, today: LocalDate = LocalDate.now()): Int {
    var day = if (focusDayMet(d, today)) today else today.minusDays(1)
    var n = 0
    while (focusDayMet(d, day) && n < 3650) {
        n++
        day = day.minusDays(1)
    }
    return n
}

/** ¿Se solapan dos rutinas un día? Devuelve el tramo común (min desde 00:00) o null. */
fun overlapOn(a: Routine, b: Routine): Pair<Int, Int>? {
    if (a.days.intersect(b.days.toSet()).isEmpty()) return null
    if (a.startMode == "unlock" || b.startMode == "unlock") return null
    val a1 = a.start; val a2 = a.start + a.lengthFrom(a.start)
    val b1 = b.start; val b2 = b.start + b.lengthFrom(b.start)
    val s = maxOf(a1, b1); val e = minOf(a2, b2)
    return if (s < e) s to e else null
}

fun routineSummary(r: Routine): String {
    val days = when {
        r.days.size == 7 -> "Todos los días"
        r.days.sorted() == listOf(1, 2, 3, 4, 5) -> "L–V"
        r.days.sorted() == listOf(6, 7) -> "Fin de semana"
        else -> r.days.sorted().joinToString(" ") { listOf("L", "M", "X", "J", "V", "S", "D")[it - 1] }
    }
    val time = if (r.startMode == "unlock") "al primer desbloqueo · " + durationLabel(r.duration)
    else minStr(r.start) + "–" + minStr((r.start + r.lengthFrom(r.start)).coerceAtMost(24 * 60 - 1))
    return "$days · $time"
}

fun durationLabel(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}
