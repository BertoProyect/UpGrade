package com.alberto.upgrade.data

import java.time.LocalDate

/**
 * Todo lo que la IA general sabe de Alberto, en texto. Sale de los datos de la app (y de los contadores
 * de uso) cada vez que se pregunta, así nunca está desfasado. Lo que es igual todo el día va primero.
 */
object FullContext {

    fun render(d: AppData, ctx: PromptContext, today: LocalDate = LocalDate.now()): String = buildString {
        // Calendario, áreas, asignaturas, horario, agenda fija y TODO lo pendiente (con referencias t1, t2…).
        append(ctx.render(d))
        appendLine()

        // ---- Racha, XP, meta ----
        val info = streakInfo(d, today)
        val check = dayCheck(d, today)
        val lv = levelOf(d.totalXp)
        appendLine()
        appendLine("Hoy: ${xpOn(d, today)} de ${goalFor(d, today)} XP de meta. Nivel ${lv.level} (${d.totalXp} XP en total).")
        appendLine(
            "Racha total: ${info.days} días (récord ${maxOf(d.bestStreak, info.days)}). Hoy: meta " + yn(check.xp) + ", rutinas " + yn(check.routines) +
                (if (check.missingRoutines.isNotEmpty()) " (faltan: ${check.missingRoutines.joinToString()})" else "") +
                ", hábitos " + yn(check.habits) + (if (check.missingHabits.isNotEmpty()) " (faltan: ${check.missingHabits.joinToString()})" else "") +
                ", foco " + yn(check.focus) + "." + if (info.atRisk) " Ayer falló: si hoy también, la racha se rompe." else ""
        )
        appendLine("Reglas de la racha: cuenta el día si cumple meta de XP + rutinas de ese día + hábitos fijos de ese día + al menos ${focusMinFor(d)} min de foco. Un fallo suelto se perdona; dos seguidos la rompen.")
        appendLine("XP: tarea ${d.xp.task}, examen ${Kind.EXAMEN.xp}, foco ${d.xp.focus30} cada 30 min (bloques de ${d.focusBlock} min, mínimo ${focusMinFor(d)} min), tiempo extra ${d.xp.focusExtra10} cada 10 min. Tope diario de XP por tareas: ${if (d.taskCap > 0) d.taskCap else "sin tope"}. Meta ${d.dailyGoal} L-V, ${d.weekendGoal} S-D.")

        // ---- Exámenes y planes de repaso ----
        val exams = pendingExams(d, today, 60)
        if (exams.isNotEmpty()) {
            appendLine()
            appendLine("Exámenes pendientes:")
            exams.forEach { (ex, n) ->
                val sessions = d.tasks.filter { it.examId == ex.id }.sortedWith(taskOrder)
                val left = if (n == 0L) "hoy" else if (n == 1L) "mañana" else "en $n días"
                append("- «${ex.title}»" + (d.subjectLabel(ex)?.let { " ($it)" } ?: "") + " el ${ex.due}" + (ex.time?.let { " a las " + minStr(it) } ?: "") + ", $left.")
                if (ex.topics.isNotEmpty()) append(" Temas: " + ex.topics.joinToString { it.name + if (it.done) " (dominado)" else "" } + ".")
                if (sessions.isEmpty()) append(" Sin plan de repaso.")
                else append(" Plan: ${sessions.count { it.done }}/${sessions.size} sesiones hechas; próximas: " +
                    sessions.filter { !it.done }.take(4).joinToString { "${it.due} ${it.time?.let { t -> minStr(t) } ?: ""} (${it.minutes ?: 60} min)" } + ".")
                appendLine()
            }
        }

        // ---- Hecho recientemente ----
        val recent = d.tasks.filter { it.done && it.doneAt != null && !dateOf(it.doneAt).isBefore(today.minusDays(7)) }.sortedByDescending { it.doneAt }
        if (recent.isNotEmpty()) {
            appendLine()
            appendLine("Hecho en los últimos 7 días: " + recent.take(30).joinToString("; ") { "${it.title} (${dateOf(it.doneAt!!)} ${minStr(minutesOf(it.doneAt))})" } + ".")
        }

        // ---- Hábitos ----
        val habits = activeHabits(d)
        if (habits.isNotEmpty()) {
            appendLine()
            appendLine("Hábitos (marcas de los últimos 14 días):")
            habits.forEach { h ->
                val marks = (13 downTo 0).map { today.minusDays(it.toLong()) }.filter { habitDone(d, h, it) }
                appendLine(
                    "- ${h.name}: ${habitSummary(h)}" + (if (h.goal.isNotBlank()) ", meta ${h.goal}" else "") + ", +${h.xp} XP. Esta semana ${habitWeekCount(d, h, today)}/${habitWeekTarget(h)}. " +
                        (if (habitDone(d, h, today)) "Hoy hecho" + (habitVariantOn(d, h, today)?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") + ". " else if (habitDueToday(d, h, today)) "Hoy toca y falta. " else "") +
                        "Días: " + (marks.joinToString(", ") { dayShort(it) + " " + it.dayOfMonth }.ifBlank { "ninguno" }) + "."
                )
            }
        }

        // ---- Rutinas ----
        if (d.routines.isNotEmpty()) {
            appendLine()
            appendLine("Rutinas de bloqueo:")
            d.routines.forEach { r ->
                val runs = d.routineLog.filter { it.routineId == r.id && !LocalDate.parse(it.date).isBefore(today.minusDays(7)) }
                appendLine(
                    "- ${r.name}" + (if (!r.enabled) " (apagada)" else "") + ": ${routineSummary(r)}, +${routineXp(d, r)} XP. Última semana: " +
                        "${runs.count { it.status == "done" }} cumplidas, ${runs.count { it.status == "cancelled" }} canceladas." +
                        (runOf(d, r.id, today)?.let { " Hoy: " + mapOf("running" to "en curso", "done" to "cumplida", "cancelled" to "cancelada")[it.status] + "." } ?: "")
                )
            }
        }

        // ---- Foco ----
        appendLine()
        val days14 = (13 downTo 0).map { today.minusDays(it.toLong()) }
        appendLine("Foco por día (últimos 14): " + days14.joinToString(", ") { "${dayShort(it)} ${it.dayOfMonth}: ${d.focusLog[it.key()] ?: 0}" } + " min.")
        if (focusOn(d)) {
            val t = d.focus.taskId?.let { id -> d.tasks.firstOrNull { it.id == id } }
            appendLine("Ahora mismo está en un foco de ${d.focus.minutes} min" + (t?.let { " con «${it.title}»" } ?: "") + ".")
        }

        // ---- XP por día ----
        appendLine("XP por día (últimos 14): " + days14.joinToString(", ") { "${dayShort(it)} ${it.dayOfMonth}: ${xpOn(d, it)}" } + ".")

        // ---- Cerrar el día ----
        val reviews = days14.mapNotNull { day -> d.reviews[day.key()]?.let { day to it } }
        if (reviews.isNotEmpty()) appendLine("Cierres del día: " + reviews.joinToString("; ") { (day, r) -> "${day.dayOfMonth}: ${r.rating}/5" + (r.firstTomorrow.takeIf { it.isNotBlank() }?.let { ", lo primero: $it" } ?: "") } + ".")

        // ---- Patrones y uso de la app ----
        appendLine()
        appendLine("Patrones de las últimas semanas:")
        appendLine(Habits.analyze(d, today).prompt)
        d.insights?.let { r ->
            appendLine()
            appendLine("Último análisis de hábitos (${dateOf(r.at)}): ${r.summary} Recomendaciones: " +
                r.items.joinToString("; ") { it.title + when (it.status) { "trying" -> " (la está probando)"; "dismissed" -> " (descartada)"; else -> "" } } + ".")
        }
    }

    private fun yn(b: Boolean) = if (b) "sí" else "no"
}
