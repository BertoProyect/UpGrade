package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.ClassToday
import com.alberto.upgrade.data.Kind
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.TaskItem
import com.alberto.upgrade.data.cap
import com.alberto.upgrade.data.classesOn
import com.alberto.upgrade.data.dayName
import com.alberto.upgrade.data.eventsOn
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.levelOf
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.monthName
import com.alberto.upgrade.data.nowMin
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.data.pendingExams
import com.alberto.upgrade.data.streakOf
import com.alberto.upgrade.data.xpOn
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.EmptyNote
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.TaskRow
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.kindIcon
import com.alberto.upgrade.ui.Tag
import com.alberto.upgrade.data.dueLabel
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import com.alberto.upgrade.ui.areaIcon
import kotlinx.coroutines.delay
import java.time.LocalDate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import com.alberto.upgrade.data.goalFor
import com.alberto.upgrade.data.taskXpOn
import com.alberto.upgrade.data.dayShort
import com.alberto.upgrade.ui.UpMotion
import com.alberto.upgrade.ui.motion
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import com.alberto.upgrade.ui.RollingNumber
import com.alberto.upgrade.ui.reveal

@Composable
fun TodayScreen(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    val today = remember(tick) { LocalDate.now() }
    val minute = remember(tick) { nowMin() }

    val classes = classesOn(data, today)
    val events = eventsOn(data, today)
    val pending = data.tasks.filter { !it.done }
    val overdue = pending.filter { (parseDate(it.due) ?: today) < today }.sortedBy { it.due }
    val dueToday = data.tasks.filter { it.due == today.key() }
        .sortedWith(compareBy({ it.done }, { it.time ?: 9999 }))
    val tomorrow = pending.filter { it.due == today.plusDays(1).key() && it.kind != Kind.EXAMEN }
    val later = pending.filter {
        val d = parseDate(it.due)
        d != null && d > today.plusDays(1) && d <= today.plusDays(7) && it.kind != Kind.EXAMEN
    }.sortedBy { it.due }
    val noDate = pending.filter { it.due == null }
    val exams = pendingExams(data, today, 30).filter { it.second > 0L }
    val leftToday = overdue.size + dueToday.count { !it.done }
    val night = minute >= data.notif.reviewCard && !data.reviews.containsKey(today.key())

    val toggle: (TaskItem) -> Unit = { t ->
        actions.award(Repo.toggleTask(t.id), if (t.kind == Kind.EXAMEN) "¡Examen hecho!" else "¡Bien hecho!")
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        // 184 dp abajo: al final del scroll nada queda debajo del micro y el +.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 184.dp)
    ) {
        item(key = "top") { TopStats(data, today) { actions.openOverlay("history") } }
        item(key = "day") { DayCard(data, today, minute, leftToday, Modifier.reveal(0), actions) }

        if (night) {
            item(key = "night") {
                Column(Modifier.reveal(1)) {
                    Spacer(Modifier.height(16.dp))
                    ChunkyCard(edge = ui.accentInk, lip = ui.primaryLip, padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UIcon(Ic.moon, ui.accentInk, size = 22.dp)
                            Text("Cierra el día", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                        Text(
                            "Dos minutos: repasas hoy y dejas listo lo primero de mañana.",
                            color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        )
                        ChunkyButton("Cerrar el día", { actions.openOverlay("review") }, Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (exams.isNotEmpty()) {
            item(key = "ex-head") {
                SectionTitle("Exámenes a la vista") {
                    Text("${exams.size}", color = ui.muted, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
            item(key = "ex-grid") {
                Box(Modifier.reveal(2)) {
                    ExamGrid(data, exams, today,
                        onOpen = { actions.openTask(it) },
                        onPlan = { actions.openOverlay("plan:" + it.id) },
                        onStudy = { Repo.startFocus(ctx, Repo.data.focusBlock.coerceAtLeast(25)); actions.goTab(com.alberto.upgrade.ui.TAB_FOCUS) })
                }
            }
        }

        if (events.isNotEmpty()) {
            item(key = "ag-h") { SectionTitle("Agenda") }
            items(events, key = { "ev-" + it.id }) { e -> Box(Modifier.animateItem().reveal(3, once = true)) { AgendaRow(data, e) } }
        }

        if (overdue.isNotEmpty()) {
            item(key = "ov-h") { SectionTitle("Atrasado") { RollingNumber(overdue.size) { n -> Text("$n", color = ui.danger, fontWeight = FontWeight.Black, fontSize = 16.sp) } } }
            items(overdue, key = { "o-" + it.id }) { t -> Box(Modifier.animateItem().reveal(3, once = true)) { TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) } }
        }

        item(key = "today-h") {
            val capReached = data.taskCap > 0 && taskXpOn(data) >= data.taskCap
            SectionTitle("Para hoy") {
                when {
                    capReached -> Text("tope ${data.taskCap}/${data.taskCap}", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    dueToday.isNotEmpty() -> Text("${dueToday.count { it.done }} de ${dueToday.size}", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
        if (dueToday.isEmpty()) {
            item(key = "today-empty") { Box(Modifier.reveal(4)) { EmptyNote("Nada con fecha de hoy. Adelanta algo de mañana y suma XP.") } }
        } else {
            val first = dueToday.take(3)
            val rest = dueToday.drop(3)
            items(first, key = { "t-" + it.id }) { t -> Box(Modifier.animateItem().reveal(4, once = true)) { TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) } }
            if (rest.isNotEmpty()) item(key = "t-more") { MoreTasks(rest, data, today, { toggle(it) }, { actions.openTask(it) }) }
        }

        item(key = "habits") { Box(Modifier.reveal(5)) { HabitsTodayCard(data, actions) { actions.goTab(com.alberto.upgrade.ui.TAB_HABITS) } } }

        if (tomorrow.isNotEmpty()) {
            item(key = "tm-h") { SectionTitle("Para mañana") }
            items(tomorrow, key = { "m-" + it.id }) { t -> Box(Modifier.animateItem().reveal(6, once = true)) { TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) } }
        }
        if (later.isNotEmpty()) {
            item(key = "wk-h") { SectionTitle("Esta semana") }
            items(later, key = { "l-" + it.id }) { t -> Box(Modifier.animateItem().reveal(6, once = true)) { TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) } }
        }
        if (noDate.isNotEmpty()) {
            item(key = "nd-h") { SectionTitle("Sin fecha") }
            items(noDate, key = { "n-" + it.id }) { t -> Box(Modifier.animateItem().reveal(6, once = true)) { TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) } }
        }

        if (classes.isNotEmpty()) {
            item(key = "sched") { ScheduleStrip(classes, minute) { actions.openOverlay("horario") } }
        }
    }
}

@Composable
private fun TopStats(data: AppData, today: LocalDate, onHistory: () -> Unit) {
    val ui = LocalUi.current
    val info = remember(data) { com.alberto.upgrade.data.streakInfo(data, today) }
    val streak = info.days
    val xp = xpOn(data, today)
    val lv = levelOf(data.totalXp)
    // La llama se rellena cuando el día entero cuenta para la racha (meta, rutinas, hábitos y foco).
    val lit = info.todayDone
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onHistory).padding(horizontal = 4.dp, vertical = 2.dp)) {
            StreakFlame(streak, lit)
        }
        Spacer(Modifier.width(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            UIcon(Ic.zapFill, ui.accentInk, size = 22.dp)
            RollingNumber(xp) { v -> Text("$v", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(start = 5.dp)) }
        }
        Spacer(Modifier.width(18.dp))
        Box(Modifier.size(26.dp).clip(CircleShape).background(ui.primary), contentAlignment = Alignment.Center) {
            Text("${lv.level}", color = ui.onPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Text("Nivel ${lv.level}", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
        Spacer(Modifier.weight(1f))
        // Misma altura que tenía la rueda de ajustes (ahora en Perfil), para que nada salte.
        Spacer(Modifier.size(40.dp))
    }
}

/**
 * Llama de la cabecera. En contorno mientras la meta de hoy está pendiente; al cumplirla se rellena,
 * se estira desde la base (×1,3 / ×1,1 en 120 ms), vuelve con spring 0.8 / 1500 y el número rueda.
 */
@Composable
private fun StreakFlame(streak: Int, lit: Boolean) {
    val ui = LocalUi.current
    val reduce = com.alberto.upgrade.ui.LocalReduceMotion.current
    val sx = remember { androidx.compose.animation.core.Animatable(1f) }
    val sy = remember { androidx.compose.animation.core.Animatable(1f) }
    var wasLit by remember { mutableStateOf(lit) }
    LaunchedEffect(lit) {
        if (lit && !wasLit && !reduce) {
            launch {
                sx.animateTo(1.3f, tween(120))
                sx.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 1500f))
            }
            launch {
                sy.animateTo(1.1f, tween(120))
                sy.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 1500f))
            }
        }
        wasLit = lit
    }
    val color = if (streak > 0) ui.flame else ui.muted
    val fadeSpec = motion(tween<Float>(120))
    val slideSpec = motion(spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.8f, stiffness = 800f))
    val numFade = motion(tween<Float>(150))
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.animation.Crossfade(lit, animationSpec = fadeSpec, label = "flame") { on ->
            UIcon(
                if (on) Ic.flameFill else Ic.flame, color, size = 24.dp,
                modifier = Modifier.graphicsLayer {
                    scaleX = sx.value
                    scaleY = sy.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
            )
        }
        androidx.compose.animation.AnimatedContent(
            streak,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically(slideSpec) { it } + androidx.compose.animation.fadeIn(numFade)) togetherWith
                    (androidx.compose.animation.slideOutVertically(slideSpec) { -it } + fadeOut(numFade))
            },
            label = "streakN"
        ) { v ->
            Text("$v", color = color, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun Stat(icon: Int, value: String, color: Color, iconSize: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        UIcon(icon, color, size = iconSize.dp)
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

private fun greeting(minute: Int) = when {
    minute < 14 * 60 -> "Buenos días"
    minute < 21 * 60 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
private fun DayCard(data: AppData, today: LocalDate, minute: Int, leftToday: Int, modifier: Modifier, actions: AppActions) {
    val ui = LocalUi.current
    val xp = xpOn(data, today)
    val goal = goalFor(data, today)
    val info = remember(data) { com.alberto.upgrade.data.streakInfo(data, today) }
    val check = remember(data) { com.alberto.upgrade.data.dayCheck(data, today) }
    val sub = when {
        leftToday == 1 -> "Te queda 1 para hoy"
        leftToday > 1 -> "Te quedan $leftToday para hoy"
        xp >= goal -> "Meta cumplida. Lo que sumes ahora es extra."
        else -> "Hoy está limpio. Adelanta algo y suma XP."
    }
    Column(modifier) {
        Spacer(Modifier.height(8.dp))
        ChunkyCard(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Eyebrow("${dayName(today)}, ${today.dayOfMonth} de ${monthName(today)}")
                SerifText("${greeting(minute)}, Alberto.", fontSize = 30.sp, modifier = Modifier.padding(top = 10.dp))
                Text(sub, color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(xp / goal.toFloat(), ui.primary, Modifier.weight(1f), height = 16.dp)
                    // Sin tope: si pasas de la meta se ve cuánto (83/60).
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RollingNumber(xp) { v -> Text("$v", color = if (v > goal) ui.accentInk else ui.ink, fontWeight = FontWeight.Black, fontSize = 14.sp) }
                        Text("/$goal XP", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
                // Lo que pide la racha de hoy, de un vistazo.
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StreakPill("Meta", check.xp, Modifier.weight(1f)) { actions.openOverlay("history") }
                    StreakPill("Rutinas", check.routines, Modifier.weight(1f)) { com.alberto.upgrade.ui.PendingFocusPane.value = "routines"; actions.goTab(com.alberto.upgrade.ui.TAB_FOCUS) }
                    StreakPill("Hábitos", check.habits, Modifier.weight(1f)) { actions.goTab(com.alberto.upgrade.ui.TAB_HABITS) }
                    StreakPill("Foco", check.focus, Modifier.weight(1f)) { actions.goTab(com.alberto.upgrade.ui.TAB_FOCUS) }
                }
                if (info.atRisk) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        UIcon(Ic.alert, ui.danger, size = 16.dp)
                        Text(
                            "Ayer no completaste el día. Si hoy tampoco, la racha se rompe.",
                            color = ui.danger, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Una condición de la racha de hoy: lima con check si está, gris con aro si falta. Toca para ir a donde se cumple. */
@Composable
private fun StreakPill(label: String, ok: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(10.dp)
    val bg by androidx.compose.animation.animateColorAsState(if (ok) ui.selTint else ui.surface, motion(tween(220)), label = "spbg")
    val edge by androidx.compose.animation.animateColorAsState(if (ok) ui.accentInk else ui.hairline, motion(tween(220)), label = "spe")
    Row(
        modifier.height(32.dp).clip(shape).background(bg).border(2.dp, edge, shape).clickable(onClick = onClick).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.animation.Crossfade(ok, animationSpec = motion(tween(200)), label = "spi") { on ->
            UIcon(if (on) Ic.check else Ic.circleDashed, if (on) ui.accentInk else ui.muted, size = 13.dp)
        }
        Text(label, color = if (ok) ui.accentInk else ui.muted, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 3.dp))
    }
}

@Composable
private fun ExamCard(data: AppData, t: TaskItem, days: Long, onOpen: () -> Unit, onPlan: () -> Unit, onStudy: () -> Unit) {
    val ui = LocalUi.current
    val subject = data.subject(t.subjectId)
    val urgent = days <= 2
    val due = parseDate(t.due)
    ChunkyCard(
        bg = if (urgent) ui.dangerTint else ui.card,
        edge = if (urgent) ui.danger else ui.hairline,
        lip = if (urgent) ui.dangerLip else ui.hairline,
        onClick = onOpen,
        padding = 0.dp
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val c = if (urgent) ui.danger else ui.ink
                Text(if (days == 0L) "HOY" else "$days", color = c, fontWeight = FontWeight.Black, fontSize = if (days == 0L) 26.sp else 44.sp, lineHeight = 44.sp)
                if (days > 0) Text(if (days == 1L) "DÍA" else "DÍAS", color = if (urgent) ui.danger else ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Eyebrow("Examen", color = if (urgent) ui.danger else ui.muted, fontSize = 12.sp)
                Text(
                    listOfNotNull(subject?.name, t.title.takeIf { subject == null || !it.equals(subject.name, true) }).joinToString(" · "),
                    color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    (dueLabel(due, LocalDate.now())) + (t.time?.let { " · ${minStr(it)}" } ?: ""),
                    color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
                if (days > 0) PlanLine(data, t, onPlan, Modifier.padding(top = 4.dp))
            }
            if (days > 0) {
                GhostButton("Repasar", onStudy, Modifier.padding(start = 8.dp).width(116.dp), height = 40.dp, icon = Ic.timer, fontSize = 13.sp, radius = 12.dp, lipH = 3.dp, iconSize = 16.dp)
            }
        }
    }
}

@Composable
private fun AgendaRow(data: AppData, e: com.alberto.upgrade.data.EventItem) {
    val ui = LocalUi.current
    val area = data.area(e.areaId)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(minStr(e.start), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.width(48.dp))
        Box(Modifier.padding(start = 6.dp, end = 14.dp).width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(ui.hairline))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (area != null) Tag(area.name, icon = areaIcon(area.id))
                Text("hasta ${minStr(e.end)}", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** "Ver N más": el resto de tareas de hoy se despliega con un cambio de altura; el chevron gira. */
@Composable
private fun MoreTasks(rest: List<TaskItem>, data: AppData, today: LocalDate, onToggle: (TaskItem) -> Unit, onOpen: (TaskItem) -> Unit) {
    val ui = LocalUi.current
    var open by rememberSaveable { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, motion(tween(200)), label = "chev")
    Column {
        AnimatedVisibility(open, enter = expandVertically(motion(UpMotion.screen())), exit = shrinkVertically(motion(UpMotion.screen())) + fadeOut(tween(150))) {
            Column { rest.forEach { t -> TaskRow(t, data, { onToggle(t) }, { onOpen(t) }, today, showDate = false) } }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            UIcon(Ic.chevronDown, ui.muted, size = 18.dp, modifier = Modifier.rotate(rot))
            Text(
                if (open) "VER MENOS" else "VER ${rest.size} MÁS", color = ui.muted, fontWeight = FontWeight.Black,
                fontSize = 13.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** Rejilla de exámenes: siempre 2 columnas; un día con varios exámenes ocupa una fila deslizable. */
@Composable
private fun ExamGrid(
    data: AppData, exams: List<Pair<TaskItem, Long>>, today: LocalDate,
    onOpen: (TaskItem) -> Unit, onPlan: (TaskItem) -> Unit, onStudy: (TaskItem) -> Unit
) {
    if (exams.size == 1) {
        val (t, n) = exams.first()
        ExamCard(data, t, n, onOpen = { onOpen(t) }, onPlan = { onPlan(t) }) { onStudy(t) }
        return
    }
    val groups = exams.groupBy { it.first.due }.values.toList()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var pending: Pair<TaskItem, Long>? = null
        val rows = mutableListOf<List<Pair<TaskItem, Long>>>()
        val multi = mutableSetOf<Int>()
        groups.forEach { g ->
            if (g.size > 1) {
                pending?.let { rows += listOf(it); pending = null }
                multi += rows.size
                rows += g
            } else {
                val p = pending
                if (p == null) pending = g.first() else { rows += listOf(p, g.first()); pending = null }
            }
        }
        pending?.let { rows += listOf(it) }
        rows.forEachIndexed { i, row ->
            when {
                i in multi -> SameDayRow(data, row, onOpen, onPlan, onStudy)
                // Un examen que se queda solo en su fila ocupa todo el ancho, en versión horizontal.
                row.size == 1 -> {
                    val (t, n) = row.first()
                    ExamCard(data, t, n, onOpen = { onOpen(t) }, onPlan = { onPlan(t) }) { onStudy(t) }
                }
                else -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (t, n) -> ExamTile(data, t, n, Modifier.weight(1f).fillMaxHeight(), showDate = true, { onOpen(t) }, { onPlan(t) }, { onStudy(t) }) }
                }
            }
        }
    }
}

@Composable
private fun SameDayRow(
    data: AppData, row: List<Pair<TaskItem, Long>>,
    onOpen: (TaskItem) -> Unit, onPlan: (TaskItem) -> Unit, onStudy: (TaskItem) -> Unit
) {
    val ui = LocalUi.current
    val (first, days) = row.first()
    val date = parseDate(first.due) ?: return
    val urgent = days <= 2
    val accent = if (urgent) ui.danger else ui.ink
    // Sin tarjeta dentro de tarjeta: cabecera de 28 dp + la rejilla normal de 2 columnas (sin corchete).
    // Todos los exámenes del día se ven a la vez; con 3 o más siguen bajando de dos en dos.
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            com.alberto.upgrade.ui.Eyebrow(
                "${dayName(date)} ${date.dayOfMonth} · ${row.size} exámenes",
                color = if (urgent) ui.danger else ui.muted, fontSize = 12.sp, modifier = Modifier.weight(1f)
            )
            Text(if (days == 0L) "HOY" else "$days", color = accent, fontWeight = FontWeight.Black, fontSize = 22.sp)
            if (days > 0L) {
                Text(if (days == 1L) " DÍA" else " DÍAS", color = accent, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            row.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (t, n) ->
                        ExamTile(data, t, n, Modifier.weight(1f).fillMaxHeight(), showDate = false, { onOpen(t) }, { onPlan(t) }, { onStudy(t) }, showDays = false)
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Examen en tarjeta compacta: dos caben en el ancho de uno. */
@Composable
private fun ExamTile(
    data: AppData, t: TaskItem, days: Long, modifier: Modifier, showDate: Boolean,
    onOpen: () -> Unit, onPlan: () -> Unit, onStudy: () -> Unit, showDays: Boolean = true
) {
    val ui = LocalUi.current
    val subject = data.subject(t.subjectId)
    val urgent = days <= 2
    val due = parseDate(t.due)
    val title = subject?.name ?: t.title
    val detail = t.title.takeIf { subject != null && !it.equals(subject.name, true) }
        ?.removePrefix("Examen de ")?.removePrefix(subject?.name ?: "")?.trim(' ', '(', ')', '·', '-')
    ChunkyCard(
        modifier,
        bg = if (urgent && showDays) ui.dangerTint else ui.card,
        edge = if (urgent && showDays) ui.danger else ui.hairline,
        lip = if (urgent && showDays) ui.dangerLip else ui.hairline,
        onClick = onOpen,
        padding = 14.dp,
        radius = 16.dp,
        fillHeight = true
    ) {
        if (showDays) {
            Row(verticalAlignment = Alignment.Bottom) {
                val c = if (urgent) ui.danger else ui.ink
                Text("$days", color = c, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 34.sp)
                Text(if (days == 1L) "DÍA" else "DÍAS", color = if (urgent) ui.danger else ui.muted, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
        }
        Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (showDays) 6.dp else 0.dp))
        // Sin "Examen" de relleno: todas lo son. Temas si los hay, y la fecha u hora. La altura la iguala la fila.
        val whenTxt = if (showDate) due?.let { "${dayShort(it).replaceFirstChar { c -> c.uppercase() }} ${it.dayOfMonth}" + (t.time?.let { m -> " · " + minStr(m) } ?: "") } else t.time?.let { minStr(it) }
        detail?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        whenTxt?.let {
            Text(it, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.weight(1f))
        GhostButton("Repasar", onStudy, Modifier.fillMaxWidth(), height = 38.dp, icon = Ic.timer, fontSize = 12.sp, radius = 12.dp, lipH = 3.dp, iconSize = 15.dp)
        PlanLine(data, t, onPlan, Modifier.padding(top = 8.dp))
    }
}

/**
 * Plan de un examen en una línea: sin plan, «Plan de repaso ›»; con plan, barra de sesiones hechas
 * y la próxima («Hoy 17:30»). Si hay temas, también cuántos dominas.
 */
@Composable
private fun PlanLine(data: AppData, exam: TaskItem, onPlan: () -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val sessions = data.tasks.filter { it.examId == exam.id }
    val done = sessions.count { it.done }
    val next = sessions.filter { !it.done }.minWithOrNull(compareBy({ it.due }, { it.time ?: 0 }))
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onPlan)) {
        if (sessions.isEmpty()) {
            Text("Plan de repaso ›", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 12.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(done / sessions.size.toFloat(), ui.primary, Modifier.weight(1f), height = 6.dp)
                Text(" $done/${sessions.size}", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            val topics = exam.topics
            Text(
                listOfNotNull(
                    next?.let { n -> "Próxima: " + dueLabel(parseDate(n.due), LocalDate.now()) + (n.time?.let { " " + minStr(it) } ?: "") },
                    if (topics.isNotEmpty()) "${topics.count { it.done }}/${topics.size} temas" else null
                ).joinToString(" · ").ifEmpty { "Plan hecho" } + " ›",
                color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Horario: abajo, discreto. Cuadraditos grises; solo la clase de ahora en lima. */
@Composable
private fun ScheduleStrip(classes: List<ClassToday>, minute: Int, onOpen: () -> Unit) {
    val ui = LocalUi.current
    Column {
        SectionTitle("Horario de hoy") {
            com.alberto.upgrade.ui.TextAction("Ver", onOpen, color = ui.accentInk, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            classes.forEach { c ->
                val now = minute in c.period.start until c.period.end
                val shape = RoundedCornerShape(10.dp)
                Column(
                    Modifier.weight(1f).clip(shape)
                        .background(if (now) ui.primary else ui.card)
                        .border(2.dp, if (now) ui.primary else ui.hairline, shape)
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(minStr(c.period.start), color = if (now) ui.onPrimary else ui.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        c.subject.name.take(3),
                        color = if (now) ui.onPrimary else ui.ink,
                        fontSize = 13.sp, fontWeight = FontWeight.Black,
                        maxLines = 1, textAlign = TextAlign.Center
                    )
                }
            }
        }
        Box(Modifier.height(4.dp))
    }
}
