package com.alberto.upgrade.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Kind
import com.alberto.upgrade.data.XpEvent
import com.alberto.upgrade.data.dayName
import com.alberto.upgrade.data.focusDayMet
import com.alberto.upgrade.data.focusStreakOf
import com.alberto.upgrade.data.goalFor
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.minutesOf
import com.alberto.upgrade.data.monthName
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.data.streakOf
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.EmptyNote
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.SquareTap
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.UpMotion
import com.alberto.upgrade.ui.motion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun kindIconFor(kind: String): Int = when (kind) {
    "task" -> Ic.circleCheck
    "exam" -> Ic.fileText
    "gym" -> Ic.dumbbell
    "fisio" -> Ic.heartPulse
    "focus" -> Ic.timer
    "routine" -> Ic.calendarClock
    "review" -> Ic.moon
    else -> Ic.zap
}

private fun kindLabel(kind: String): String = when (kind) {
    "task" -> "Tarea"
    "exam" -> "Examen"
    "gym", "fisio" -> "Hábito"
    "focus" -> "Foco"
    "routine" -> "Rutina"
    "review" -> "Cerrar el día"
    else -> "XP"
}

private fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Hoy"
    today.minusDays(1) -> "Ayer"
    else -> dayName(d).replaceFirstChar { it.uppercase() } + " ${d.dayOfMonth}" +
        if (d.year == today.year && d.month == today.month) "" else " de ${monthName(d)}"
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

/**
 * Historial de una racha: [mode] "xp" (meta diaria) o "focus" (rutina cumplida o 30 min de foco).
 * El récord solo se ve aquí. Cada día va plegado; al tocarlo se despliega lo que le dio XP.
 */
@Composable
fun HistoryScreen(data: AppData, mode: String = "xp", onClose: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val focus = mode == "focus"
    val streak = if (focus) focusStreakOf(data, today) else streakOf(data, today)
    val best = maxOf(if (focus) data.bestFocusStreak else data.bestStreak, streak)
    val byDay = remember(data.xpEvents) { data.xpEvents.groupBy { it.date } }
    val days = remember(data.xpLog, data.xpEvents, data.focusLog, data.routineLog, mode) {
        val keys = if (focus) {
            data.focusLog.filterValues { it > 0 }.keys + data.routineLog.filter { it.status == "done" }.map { it.date } + today.toString()
        } else {
            data.xpLog.filterValues { it > 0 }.keys + byDay.keys + today.toString()
        }
        keys.mapNotNull { parseDate(it) }.distinct().sortedDescending().take(120)
    }
    val open = remember { mutableStateListOf<String>() }
    val tint = if (streak > 0) ui.flame else ui.muted

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (focus) "Racha de foco" else "Racha de XP", onClose)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                Column(Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UIcon(if (focus) Ic.shieldCheck else Ic.flameFill, tint, size = 32.dp)
                        Row(Modifier.padding(start = 8.dp)) {
                            Text(
                                "$streak", color = tint, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 40.sp,
                                letterSpacing = (-1).sp, modifier = Modifier.alignByBaseline()
                            )
                            Text(
                                plural(streak, "día de racha", "días de racha"), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp,
                                modifier = Modifier.alignByBaseline().padding(start = 8.dp)
                            )
                        }
                    }
                    // El récord vive aquí, no en las miniaturas del Perfil.
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        UIcon(Ic.trophy, ui.muted, size = 16.dp)
                        Text(
                            "Mejor racha: $best " + plural(best, "día", "días"), color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                        if (streak > 0 && streak >= best) {
                            Text(
                                "RÉCORD", color = ui.flame, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.6.sp,
                                modifier = Modifier.padding(start = 8.dp).border(1.5.dp, ui.flame, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        if (focus) "Cuenta un día con una rutina cumplida o 30 min de foco." else "${fmtXp(data.totalXp)} XP en total",
                        color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            items(days, key = { it.toString() }) { d ->
                val k = d.toString()
                val expanded = k in open
                val toggle = { if (expanded) open.remove(k) else open.add(k); Unit }
                if (focus) FocusDay(data, d, today, byDay[k].orEmpty(), expanded, toggle)
                else XpDay(data, d, today, byDay[k].orEmpty(), expanded, toggle)
            }
        }
    }
}

/** Fila de un día: cabecera que se toca y un cuerpo que se despliega con un cambio de altura. */
@Composable
private fun FoldDay(
    title: String,
    met: Boolean,
    icon: Int,
    value: String,
    suffix: String,
    progress: Float?,
    expanded: Boolean,
    onToggle: () -> Unit,
    body: @Composable ColumnScope.() -> Unit
) {
    val ui = LocalUi.current
    val rot by animateFloatAsState(if (expanded) 180f else 0f, motion(tween(200)), label = "daychev")
    ChunkyCard(Modifier.fillMaxWidth().padding(top = 10.dp), padding = 0.dp, radius = 16.dp) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIcon(icon, if (met) ui.flame else ui.strong, size = 18.dp)
                Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Text(value, color = if (met) ui.accentInk else ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(suffix, color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                UIcon(Ic.chevronDown, ui.muted, size = 18.dp, modifier = Modifier.padding(start = 8.dp).rotate(rot))
            }
            if (progress != null) {
                ProgressBar(progress, if (met) ui.primary else ui.strong, Modifier.fillMaxWidth().padding(top = 8.dp), height = 6.dp)
            }
        }
        AnimatedVisibility(
            expanded,
            enter = expandVertically(motion(UpMotion.screen())) + fadeIn(motion(tween(180))),
            exit = shrinkVertically(motion(UpMotion.screen())) + fadeOut(motion(tween(120)))
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(ui.hairline))
                Spacer(Modifier.height(4.dp))
                body()
            }
        }
    }
}

@Composable
private fun XpDay(data: AppData, d: LocalDate, today: LocalDate, events: List<XpEvent>, expanded: Boolean, onToggle: () -> Unit) {
    val ui = LocalUi.current
    val xp = data.xpLog[d.toString()] ?: 0
    val goal = goalFor(data, d)
    FoldDay(dayTitle(d, today), xp >= goal, Ic.flameFill, "$xp", " / $goal XP", xp / goal.toFloat(), expanded, onToggle) {
        if (events.isEmpty()) {
            Text(
                if (xp > 0) "Sin detalle: el historial empezó con esta versión." else "Nada todavía.",
                color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            events.sortedByDescending { it.at }.forEach { e ->
                EventRow(kindIconFor(e.kind), e.label, minStr(minutesOf(e.at)), e.amount)
            }
        }
    }
}

@Composable
private fun FocusDay(data: AppData, d: LocalDate, today: LocalDate, events: List<XpEvent>, expanded: Boolean, onToggle: () -> Unit) {
    val ui = LocalUi.current
    val k = d.toString()
    val mins = data.focusLog[k] ?: 0
    val runs = data.routineLog.filter { it.date == k && it.status == "done" }
    val summary = listOfNotNull(
        if (mins > 0) "$mins min" else null,
        if (runs.isNotEmpty()) "${runs.size} " + plural(runs.size, "rutina", "rutinas") else null
    ).joinToString(" · ").ifEmpty { "—" }
    FoldDay(dayTitle(d, today), focusDayMet(data, d), Ic.shieldCheck, summary, "", null, expanded, onToggle) {
        val focusXp = events.filter { it.kind == "focus" }.sumOf { it.amount }
        if (mins == 0 && runs.isEmpty()) {
            Text("Ni foco ni rutinas este día.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (mins > 0) EventRow(Ic.timer, "Foco · $mins min", if (mins >= 30) "cuenta" else "menos de 30 min", focusXp)
        runs.sortedByDescending { it.startedAt }.forEach { r ->
            val name = data.routines.firstOrNull { it.id == r.routineId }?.name ?: "Rutina"
            EventRow(Ic.calendarClock, name, minStr(minutesOf(r.startedAt)), r.xp)
        }
    }
}

@Composable
private fun EventRow(icon: Int, label: String, meta: String, amount: Int) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
            UIcon(icon, ui.muted, size = 15.dp)
        }
        Text(
            label, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp)
        )
        Text(meta, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
        if (amount != 0) {
            Text(
                (if (amount > 0) "+" else "") + "$amount", color = if (amount > 0) ui.accentInk else ui.danger,
                fontWeight = FontWeight.Black, fontSize = 14.sp
            )
        }
    }
}

// ───────────────────────── Historial del día ─────────────────────────

private data class DayEntry(val at: Long?, val icon: Int, val title: String, val sub: String, val xp: Int?)

/** Todo lo hecho un día: tareas completadas (den XP o no) y lo que dejó XP sin ser tarea (hábitos, foco, rutinas…). */
private fun dayEntries(d: AppData, day: LocalDate): List<DayEntry> {
    val key = day.toString()
    val zone = ZoneId.systemDefault()
    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val out = mutableListOf<DayEntry>()
    d.tasks.forEach { t ->
        val at = t.doneAt
        if (t.done && at != null && dayOf(at) == day) {
            val where = d.subject(t.subjectId)?.name ?: d.area(t.areaId)?.name
            out += DayEntry(
                at, if (t.kind == Kind.EXAMEN) Ic.fileText else Ic.circleCheck, t.title,
                listOfNotNull(where, t.kind.label).joinToString(" · "),
                if (t.xpDate == key) t.xpAwarded else 0
            )
        }
    }
    val ev = d.xpEvents.filter { it.date == key && it.amount > 0 && it.kind != "task" && it.kind != "exam" }
    ev.forEach { e -> out += DayEntry(e.at, kindIconFor(e.kind), e.label, kindLabel(e.kind), e.amount) }
    // Respaldo para lo que se hizo antes de que existiera el historial de XP.
    d.gymLog[key]?.let { type -> if (ev.none { it.kind == "gym" }) out += DayEntry(null, Ic.dumbbell, "Gym · $type", "Hábito", null) }
    if (key in d.fisioLog && ev.none { it.kind == "fisio" }) out += DayEntry(null, Ic.heartPulse, "Fisio", "Hábito", null)
    val mins = d.focusLog[key] ?: 0
    if (mins > 0 && ev.none { it.kind == "focus" }) out += DayEntry(null, Ic.timer, "Foco · $mins min", "Foco", null)
    if (ev.none { it.kind == "routine" }) {
        d.routineLog.filter { it.date == key && it.status == "done" }.forEach { r ->
            out += DayEntry(r.startedAt, Ic.calendarClock, d.routines.firstOrNull { it.id == r.routineId }?.name ?: "Rutina", "Rutina", r.xp)
        }
    }
    return out.sortedWith(compareByDescending<DayEntry> { it.at ?: Long.MIN_VALUE })
}

/** Historial del día (desde Perfil): qué hiciste un día concreto, de lo último a lo primero. */
@Composable
fun DayLogScreen(data: AppData, onClose: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    var day by remember { mutableStateOf(today) }
    val entries = remember(data, day) { dayEntries(data, day) }
    val xp = data.xpLog[day.toString()] ?: 0
    val goal = goalFor(data, day)
    val done = entries.count { it.icon == Ic.circleCheck || it.icon == Ic.fileText }
    val mins = data.focusLog[day.toString()] ?: 0
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Historial del día", onClose)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SquareTap(Ic.chevronLeft, { day = day.minusDays(1) })
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dayTitle(day, today), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(
                            "${dayName(day).replaceFirstChar { it.uppercase() }} ${day.dayOfMonth} de ${monthName(day)}",
                            color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                    }
                    if (day < today) SquareTap(Ic.chevronRight, { day = day.plusDays(1) }) else Spacer(Modifier.size(40.dp))
                }
            }
            item {
                ChunkyCard(Modifier.fillMaxWidth().padding(top = 14.dp), padding = 0.dp, radius = 16.dp) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        DayStat("$xp/$goal", "XP", Modifier.weight(1f), if (xp >= goal) ui.accentInk else ui.ink)
                        Box(Modifier.width(2.dp).fillMaxHeight().background(ui.hairline))
                        DayStat("$done", plural(done, "hecha", "hechas"), Modifier.weight(1f), ui.ink)
                        Box(Modifier.width(2.dp).fillMaxHeight().background(ui.hairline))
                        DayStat("$mins", "min de foco", Modifier.weight(1f), ui.ink)
                    }
                }
            }
            if (entries.isEmpty()) {
                item { Box(Modifier.padding(top = 12.dp)) { EmptyNote(if (day == today) "Aún no has terminado nada hoy." else "Nada hecho este día.") } }
            } else {
                items(entries) { e -> DayEntryRow(e) }
            }
        }
    }
}

@Composable
private fun DayStat(value: String, label: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    val ui = LocalUi.current
    Column(modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
        Text(label, color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun DayEntryRow(e: DayEntry) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(ui.surface).border(2.dp, ui.hairline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { UIcon(e.icon, ui.ink, size = 18.dp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(e.title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(e.sub, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            e.at?.let { Text(minStr(minutesOf(it)), color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
            val xp = e.xp
            if (xp != null) {
                if (xp > 0) Text("+$xp XP", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp)
                else Text("sin XP", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
