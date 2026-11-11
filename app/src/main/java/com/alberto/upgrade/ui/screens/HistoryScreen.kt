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
import com.alberto.upgrade.data.dayCheck
import com.alberto.upgrade.data.streakInfo
import com.alberto.upgrade.data.dayComplete
import androidx.compose.foundation.shape.CircleShape
import com.alberto.upgrade.data.goalFor
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.minutesOf
import com.alberto.upgrade.data.monthName
import com.alberto.upgrade.data.parseDate
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.alberto.upgrade.ui.reveal
import java.time.LocalDate
import java.time.ZoneId

private fun kindIconFor(kind: String): Int = when (kind) {
    "task" -> Ic.circleCheck
    "exam" -> Ic.fileText
    "gym" -> Ic.dumbbell
    "fisio" -> Ic.heartPulse
    "habit" -> Ic.listChecks
    "focus" -> Ic.timer
    "routine" -> Ic.calendarClock
    "review" -> Ic.moon
    else -> Ic.zap
}

private fun kindLabel(kind: String): String = when (kind) {
    "task" -> "Tarea"
    "exam" -> "Examen"
    "gym", "fisio", "habit" -> "Hábito"
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
 * Historial de la racha total. El récord solo se ve aquí. Cada día va plegado: arriba, si contó para la racha
 * y qué faltó; al tocarlo se despliega lo que le dio XP.
 */
@Composable
fun HistoryScreen(data: AppData, onClose: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val info = remember(data) { streakInfo(data, today) }
    val streak = info.days
    val best = maxOf(data.bestStreak, streak)
    val byDay = remember(data.xpEvents) { data.xpEvents.groupBy { it.date } }
    val days = remember(data.xpLog, data.xpEvents) {
        val keys = data.xpLog.filterValues { it > 0 }.keys + byDay.keys + today.toString()
        keys.mapNotNull { parseDate(it) }.distinct().sortedDescending().take(120)
    }
    val open = remember { mutableStateListOf<String>() }
    val tint = if (streak > 0) ui.flame else ui.muted

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Racha", onClose)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item(key = "head") {
                Column(Modifier.padding(top = 4.dp, bottom = 6.dp).reveal(0)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UIcon(Ic.flameFill, tint, size = 32.dp)
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
                    // El récord vive aquí, no en el Perfil.
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
                        "Un día cuenta si cumples la meta de XP, tus rutinas, los hábitos de ese día y al menos un bloque de foco. " +
                            "Un día fallado suelto no la rompe; dos seguidos, sí." +
                            (if (info.forgiven > 0) " Llevas ${info.forgiven} " + plural(info.forgiven, "día perdonado", "días perdonados") + " en esta racha." else ""),
                        color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            items(days, key = { it.toString() }) { d ->
                val k = d.toString()
                val expanded = k in open
                val toggle = { if (expanded) open.remove(k) else open.add(k); Unit }
                Box(Modifier.animateItem().reveal(days.indexOf(d).coerceAtMost(6) + 1, once = true)) {
                    XpDay(data, d, today, byDay[k].orEmpty(), expanded, toggle)
                }
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
    val check = remember(data, d) { dayCheck(data, d) }
    FoldDay(dayTitle(d, today), check.complete, Ic.flameFill, "$xp", " / $goal XP", xp / goal.toFloat(), expanded, onToggle) {
        // Qué pidió la racha ese día.
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckChip("Meta", check.xp, Modifier.weight(1f))
            CheckChip("Rutinas", check.routines, Modifier.weight(1f))
            CheckChip("Hábitos", check.habits, Modifier.weight(1f))
            CheckChip("Foco", check.focus, Modifier.weight(1f))
        }
        val missing = check.missingRoutines + check.missingHabits
        if (missing.isNotEmpty()) {
            Text("Faltó: " + missing.joinToString(", "), color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
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
private fun CheckChip(label: String, ok: Boolean, modifier: Modifier) {
    val ui = LocalUi.current
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (ok) ui.selTint else ui.surface).padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        UIcon(if (ok) Ic.check else Ic.x, if (ok) ui.accentInk else ui.muted, size = 12.dp)
        Text(label, color = if (ok) ui.accentInk else ui.muted, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 3.dp))
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

/** [cat]: task, habit, focus, routine u other (para los filtros). */
private data class DayEntry(val at: Long?, val icon: Int, val title: String, val sub: String, val xp: Int?, val cat: String = "other")

private fun catOf(kind: String): String = when (kind) {
    "task", "exam" -> "task"
    "gym", "fisio", "habit" -> "habit"
    "focus" -> "focus"
    "routine" -> "routine"
    else -> "other"
}

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
                if (t.xpDate == key) t.xpAwarded else 0,
                "task"
            )
        }
    }
    val ev = d.xpEvents.filter { it.date == key && it.amount > 0 && it.kind != "task" && it.kind != "exam" }
    ev.forEach { e -> out += DayEntry(e.at, kindIconFor(e.kind), e.label, kindLabel(e.kind), e.amount, catOf(e.kind)) }
    // Respaldo para lo que se hizo antes de que existiera el historial de XP.
    d.gymLog[key]?.let { type -> if (ev.none { it.kind == "gym" || it.kind == "habit" }) out += DayEntry(null, Ic.dumbbell, "Gym · $type", "Hábito", null, "habit") }
    if (key in d.fisioLog && ev.none { it.kind == "fisio" || it.kind == "habit" }) out += DayEntry(null, Ic.heartPulse, "Fisio", "Hábito", null, "habit")
    // Hábitos sin XP (no dejan rastro en el historial de XP).
    d.habits.filter { it.xp <= 0 && d.habitLog[it.id]?.containsKey(key) == true }.forEach { h ->
        out += DayEntry(null, com.alberto.upgrade.ui.habitIcon(h.icon), h.name, "Hábito", 0, "habit")
    }
    val mins = d.focusLog[key] ?: 0
    if (mins > 0 && ev.none { it.kind == "focus" }) out += DayEntry(null, Ic.timer, "Foco · $mins min", "Foco", null, "focus")
    if (ev.none { it.kind == "routine" }) {
        d.routineLog.filter { it.date == key && it.status == "done" }.forEach { r ->
            out += DayEntry(r.startedAt, Ic.calendarClock, d.routines.firstOrNull { it.id == r.routineId }?.name ?: "Rutina", "Rutina", r.xp, "routine")
        }
    }
    return out.sortedWith(compareByDescending<DayEntry> { it.at ?: Long.MIN_VALUE })
}

/**
 * Historial (desde Perfil): Día, Semana o Mes. En Semana y Mes ves de un vistazo qué días fueron completos
 * y saltas a cualquiera con un toque, sin pasar día a día.
 */
@Composable
fun DayLogScreen(data: AppData, onClose: () -> Unit) {
    val today = LocalDate.now()
    var view by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("day") }
    var day by remember { mutableStateOf(today) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Historial", onClose)
        com.alberto.upgrade.ui.Segmented(
            listOf(Triple("day", "Día", null), Triple("week", "Semana", null), Triple("month", "Mes", null)),
            view, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) { view = it }
        androidx.compose.animation.AnimatedContent(
            view, modifier = Modifier.weight(1f), label = "histView",
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }
        ) { v ->
            when (v) {
                "week" -> WeekLog(data, day, { day = it }) { day = it; view = "day" }
                "month" -> MonthLog(data, day, { day = it }) { day = it; view = "day" }
                else -> DayLog(data, day) { day = it }
            }
        }
    }
}

/** Resumen de un periodo: XP total, días completos, tareas hechas y minutos de foco. */
@Composable
private fun PeriodStats(data: AppData, days: List<LocalDate>) {
    val ui = LocalUi.current
    val past = days.filter { !it.isAfter(LocalDate.now()) }
    val xp = past.sumOf { data.xpLog[it.toString()] ?: 0 }
    val complete = past.count { dayComplete(data, it) }
    val keys = past.map { it.toString() }.toSet()
    val tasks = data.tasks.count { it.done && it.xpDate in keys }
    val mins = past.sumOf { data.focusLog[it.toString()] ?: 0 }
    ChunkyCard(Modifier.fillMaxWidth().padding(top = 12.dp), padding = 0.dp, radius = 16.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            DayStat("$xp", "XP", Modifier.weight(1f), ui.ink)
            Box(Modifier.width(2.dp).fillMaxHeight().background(ui.hairline))
            DayStat("$complete/${past.size}", "completos", Modifier.weight(1f), if (past.isNotEmpty() && complete == past.size) ui.flame else ui.ink)
            Box(Modifier.width(2.dp).fillMaxHeight().background(ui.hairline))
            DayStat("$tasks", plural(tasks, "hecha", "hechas"), Modifier.weight(1f), ui.ink)
            Box(Modifier.width(2.dp).fillMaxHeight().background(ui.hairline))
            DayStat("$mins", "min foco", Modifier.weight(1f), ui.ink)
        }
    }
}

/** Cabecera ‹ periodo ›: [next] solo si el periodo siguiente no es futuro. */
@Composable
private fun PeriodNav(title: String, sub: String, onPrev: () -> Unit, onNext: (() -> Unit)?) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SquareTap(Ic.chevronLeft, onPrev)
        androidx.compose.animation.AnimatedContent(title, modifier = Modifier.weight(1f), label = "pnav",
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }) { t ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(sub, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        if (onNext != null) SquareTap(Ic.chevronRight, onNext) else Spacer(Modifier.size(40.dp))
    }
}

/** Semana (L–D): una fila por día con su XP, si fue completo y lo hecho. Toca un día para verlo entero. */
@Composable
private fun WeekLog(data: AppData, anchor: LocalDate, setAnchor: (LocalDate) -> Unit, openDay: (LocalDate) -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val monday = anchor.with(java.time.DayOfWeek.MONDAY)
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val sunday = days.last()
    val thisWeek = !sunday.isBefore(today)
    val title = if (thisWeek) "Esta semana" else if (today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY) == monday) "Semana pasada" else "Semana del ${monday.dayOfMonth}"
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
        item(key = "nav") {
            PeriodNav(
                title, "${monday.dayOfMonth} ${com.alberto.upgrade.data.monthShort(monday)} – ${sunday.dayOfMonth} ${com.alberto.upgrade.data.monthShort(sunday)}",
                { setAnchor(monday.minusWeeks(1)) }, if (thisWeek) null else ({ setAnchor(monday.plusWeeks(1)) })
            )
        }
        item(key = "stats") { PeriodStats(data, days) }
        items(days, key = { it.toString() }) { d ->
            val future = d.isAfter(today)
            val xp = data.xpLog[d.toString()] ?: 0
            val goal = goalFor(data, d)
            val complete = !future && dayComplete(data, d)
            val done = data.tasks.count { it.done && it.xpDate == d.toString() }
            val mins = data.focusLog[d.toString()] ?: 0
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (d == today) ui.selTint else ui.card).border(2.dp, if (d == today) ui.accentInk else ui.hairline, RoundedCornerShape(14.dp))
                    .then(if (future) Modifier else Modifier.clickable { openDay(d) })
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(if (complete) ui.flame else ui.surface), contentAlignment = Alignment.Center) {
                    if (complete) UIcon(Ic.check, ui.bg, size = 18.dp)
                    else Text(listOf("L", "M", "X", "J", "V", "S", "D")[d.dayOfWeek.value - 1], color = ui.muted, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(dayTitle(d, today), color = if (future) ui.muted else ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    if (!future) {
                        ProgressBar(xp / goal.toFloat(), if (xp >= goal) ui.primary else ui.strong, Modifier.fillMaxWidth().padding(top = 5.dp, end = 12.dp), height = 6.dp)
                        Text(
                            listOfNotNull(if (done > 0) "$done " + plural(done, "hecha", "hechas") else null, if (mins > 0) "$mins min foco" else null).joinToString(" · ").ifEmpty { "Sin actividad" },
                            color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                if (!future) {
                    Text("$xp", color = if (xp >= goal) ui.accentInk else ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("/$goal", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    UIcon(Ic.chevronRight, ui.muted, size = 16.dp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** Mes: calendario con cada día pintado (naranja = completo, lima = meta de XP, gris = poco) y su XP. Toca uno para verlo. */
@Composable
private fun MonthLog(data: AppData, anchor: LocalDate, setAnchor: (LocalDate) -> Unit, openDay: (LocalDate) -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val month = java.time.YearMonth.from(anchor)
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val thisMonth = month == java.time.YearMonth.from(today)
    var drag by remember { mutableStateOf(0f) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        modifier = Modifier.pointerInput(month) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (drag > 120f) setAnchor(first.minusMonths(1))
                    else if (drag < -120f && !thisMonth) setAnchor(first.plusMonths(1))
                    drag = 0f
                },
                onDragCancel = { drag = 0f }
            ) { _, dx -> drag += dx }
        }
    ) {
        item(key = "nav") {
            PeriodNav(
                com.alberto.upgrade.data.monthName(first).replaceFirstChar { it.uppercase() } + if (month.year != today.year) " ${month.year}" else "",
                if (thisMonth) "Este mes" else "Desliza o usa las flechas",
                { setAnchor(first.minusMonths(1)) }, if (thisMonth) null else ({ setAnchor(first.plusMonths(1)) })
            )
        }
        item(key = "stats") { PeriodStats(data, days) }
        item(key = "grid") {
            Column(Modifier.padding(top = 12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                        Text(it, color = ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                    }
                }
                val rows = (first.dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(7) { c ->
                            val d = start.plusDays((r * 7 + c).toLong())
                            val inMonth = java.time.YearMonth.from(d) == month
                            val future = d.isAfter(today)
                            val xp = data.xpLog[d.toString()] ?: 0
                            val complete = inMonth && !future && dayComplete(data, d)
                            val met = inMonth && !future && xp >= goalFor(data, d)
                            val shape = RoundedCornerShape(12.dp)
                            Column(
                                Modifier.weight(1f).height(56.dp).padding(vertical = 2.dp).clip(shape)
                                    .background(
                                        when {
                                            !inMonth -> androidx.compose.ui.graphics.Color.Transparent
                                            complete -> ui.flame.copy(alpha = 0.22f)
                                            met -> ui.selTint
                                            else -> ui.card
                                        }
                                    )
                                    .border(2.dp, when { !inMonth -> androidx.compose.ui.graphics.Color.Transparent; d == today -> ui.accentInk; complete -> ui.flame; else -> ui.hairline }, shape)
                                    .then(if (inMonth && !future) Modifier.clickable { openDay(d) } else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (inMonth) {
                                    Text("${d.dayOfMonth}", color = if (future) ui.muted else ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    if (!future) Text(if (xp > 0) "$xp" else "–", color = if (complete) ui.flame else if (met) ui.accentInk else ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "legend") {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(ui.flame, "Día completo")
                LegendDot(ui.accentInk, "Meta de XP")
                LegendDot(ui.strong, "Sin meta")
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

private val dayFilters = listOf("all" to "Todo", "task" to "Tareas", "habit" to "Hábitos", "focus" to "Foco", "routine" to "Rutinas", "other" to "Otros")

/**
 * Historial del día (desde Perfil): ‹ día ›, las tres cifras, qué pidió la racha y la lista de lo hecho.
 * Filtros por tipo (solo los que tienen algo ese día) y deslizar en horizontal para cambiar de día.
 */
@Composable
private fun DayLog(data: AppData, day: LocalDate, setDay: (LocalDate) -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    var filter by remember { mutableStateOf("all") }
    val entries = remember(data, day) { dayEntries(data, day) }
    val shown = if (filter == "all") entries else entries.filter { it.cat == filter }
    val xp = data.xpLog[day.toString()] ?: 0
    val goal = goalFor(data, day)
    val done = entries.count { it.cat == "task" }
    val mins = data.focusLog[day.toString()] ?: 0
    val check = remember(data, day) { dayCheck(data, day) }
    var drag by remember { mutableStateOf(0f) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            modifier = Modifier.pointerInput(day) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag > 120f) setDay(day.minusDays(1))
                        else if (drag < -120f && day < today) setDay(day.plusDays(1))
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f }
                ) { _, dx -> drag += dx }
            }
        ) {
            item(key = "nav") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SquareTap(Ic.chevronLeft, { setDay(day.minusDays(1)) })
                    androidx.compose.animation.AnimatedContent(
                        day, modifier = Modifier.weight(1f), label = "dayT",
                        transitionSpec = {
                            val fwd = targetState > initialState
                            (fadeIn(tween(180)) + androidx.compose.animation.slideInHorizontally(tween(220)) { if (fwd) it / 4 else -it / 4 }) togetherWith fadeOut(tween(120))
                        }
                    ) { dd ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayTitle(dd, today), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text(
                                "${dayName(dd).replaceFirstChar { it.uppercase() }} ${dd.dayOfMonth} de ${monthName(dd)}",
                                color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                        }
                    }
                    if (day < today) SquareTap(Ic.chevronRight, { setDay(day.plusDays(1)) }) else Spacer(Modifier.size(40.dp))
                }
            }
            item(key = "stats") {
                ChunkyCard(Modifier.fillMaxWidth().padding(top = 14.dp), padding = 0.dp, radius = 16.dp) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        DayStat("$xp/$goal", "XP", Modifier.weight(1f), if (xp >= goal) ui.accentInk else ui.ink)
                        Box(Modifier.width(2.dp).fillMaxHeight().background(ui.hairline))
                        DayStat("$done", plural(done, "hecha", "hechas"), Modifier.weight(1f), ui.ink)
                        Box(Modifier.width(2.dp).fillMaxHeight().background(ui.hairline))
                        DayStat("$mins", "min de foco", Modifier.weight(1f), ui.ink)
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(ui.hairline))
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CheckChip("Meta", check.xp, Modifier.weight(1f))
                        CheckChip("Rutinas", check.routines, Modifier.weight(1f))
                        CheckChip("Hábitos", check.habits, Modifier.weight(1f))
                        CheckChip("Foco", check.focus, Modifier.weight(1f))
                    }
                }
            }
            if (entries.isNotEmpty()) {
                item(key = "filters") {
                    val counts = entries.groupingBy { it.cat }.eachCount()
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 14.dp)
                    ) {
                        val opts = dayFilters.filter { (k, _) -> k == "all" || (counts[k] ?: 0) > 0 }
                        items(opts.size) { i ->
                            val (k, label) = opts[i]
                            com.alberto.upgrade.ui.SelectChip(
                                label, filter == k, { filter = k },
                                badge = if (k == "all") "${entries.size}" else "${counts[k] ?: 0}"
                            )
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item(key = "empty") { Box(Modifier.padding(top = 12.dp)) { EmptyNote(if (day == today) "Aún no has terminado nada hoy. Lo que hagas saldrá aquí." else "Nada hecho este día.") } }
            } else if (shown.isEmpty()) {
                item(key = "empty2") { Box(Modifier.padding(top = 12.dp)) { EmptyNote("Nada de este tipo este día.") } }
            } else {
                items(shown, key = { (it.at ?: 0L).toString() + it.title + it.cat }) { e -> Box(Modifier.animateItem().reveal(0)) { DayEntryRow(e) } }
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
