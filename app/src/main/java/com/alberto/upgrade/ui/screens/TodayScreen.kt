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
import com.alberto.upgrade.data.gymThisWeek
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.levelOf
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.monthName
import com.alberto.upgrade.data.nextGymType
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
import com.alberto.upgrade.data.fisioThisWeek
import com.alberto.upgrade.data.goalFor
import com.alberto.upgrade.data.taskXpOn
import com.alberto.upgrade.data.dayShort
import com.alberto.upgrade.ui.UpMotion
import com.alberto.upgrade.ui.motion

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 150.dp)
    ) {
        item { TopStats(data, today) }
        item { DayCard(data, today, minute, leftToday) }

        if (night) {
            item {
                Column {
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
                ExamGrid(data, exams, today,
                    onOpen = { actions.openTask(it) },
                    onPlan = { actions.openOverlay("plan:" + it.id) },
                    onStudy = { Repo.startFocus(ctx, 30); actions.goTab(3) })
            }
        }

        if (events.isNotEmpty()) {
            item { SectionTitle("Agenda") }
            items(events, key = { "ev-" + it.id }) { e -> AgendaRow(data, e) }
        }

        if (overdue.isNotEmpty()) {
            item { SectionTitle("Atrasado") { Text("${overdue.size}", color = ui.danger, fontWeight = FontWeight.Black, fontSize = 16.sp) } }
            items(overdue, key = { "o-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
        }

        item {
            val capReached = data.taskCap > 0 && taskXpOn(data) >= data.taskCap
            SectionTitle("Para hoy") {
                when {
                    capReached -> Text("tope ${data.taskCap}/${data.taskCap}", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    dueToday.isNotEmpty() -> Text("${dueToday.count { it.done }} de ${dueToday.size}", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
        if (dueToday.isEmpty()) {
            item { EmptyNote("Nada con fecha de hoy. Adelanta algo de mañana y suma XP.") }
        } else {
            val first = dueToday.take(3)
            val rest = dueToday.drop(3)
            items(first, key = { "t-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) }
            if (rest.isNotEmpty()) item(key = "t-more") { MoreTasks(rest, data, today, { toggle(it) }, { actions.openTask(it) }) }
        }

        item { WeekCard(data, today, actions) }

        if (tomorrow.isNotEmpty()) {
            item { SectionTitle("Para mañana") }
            items(tomorrow, key = { "m-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) }
        }
        if (later.isNotEmpty()) {
            item { SectionTitle("Esta semana") }
            items(later, key = { "l-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
        }
        if (noDate.isNotEmpty()) {
            item { SectionTitle("Sin fecha") }
            items(noDate, key = { "n-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) }
        }

        if (classes.isNotEmpty()) {
            item { ScheduleStrip(classes, minute) { actions.openOverlay("horario") } }
        }
    }
}

@Composable
private fun TopStats(data: AppData, today: LocalDate) {
    val ui = LocalUi.current
    val streak = streakOf(data, today)
    val xp = xpOn(data, today)
    val lv = levelOf(data.totalXp)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Stat(Ic.flameFill, "$streak", if (streak > 0) ui.flame else ui.muted, 24)
        Spacer(Modifier.width(18.dp))
        Stat(Ic.zapFill, "$xp", ui.accentInk, 22)
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
private fun DayCard(data: AppData, today: LocalDate, minute: Int, leftToday: Int) {
    val ui = LocalUi.current
    val xp = xpOn(data, today)
    val sub = when {
        leftToday == 1 -> "Te queda 1 para hoy"
        leftToday > 1 -> "Te quedan $leftToday para hoy"
        xp >= goalFor(data, today) -> "Meta cumplida. Lo que sumes ahora es extra."
        else -> "Hoy está limpio. Adelanta algo y suma XP."
    }
    Column {
        Spacer(Modifier.height(8.dp))
        ChunkyCard(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Eyebrow("${dayName(today)}, ${today.dayOfMonth} de ${monthName(today)}")
                SerifText("${greeting(minute)}, Alberto.", fontSize = 30.sp, modifier = Modifier.padding(top = 10.dp))
                Text(sub, color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(xp / goalFor(data, today).toFloat(), ui.primary, Modifier.weight(1f), height = 16.dp)
                    Text(
                        "${minOf(xp, goalFor(data, today))}/${goalFor(data, today)} XP",
                        color = ui.ink, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
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
                if (days > 0) {
                    val planned = data.tasks.count { it.examId == t.id && !it.done }
                    Text(
                        if (planned > 0) "Plan: $planned " + (if (planned == 1) "sesión" else "sesiones") + " ›" else "Crear plan de repaso ›",
                        color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onPlan)
                    )
                }
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
            if (i in multi) SameDayRow(data, row, onOpen, onPlan, onStudy)
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (t, n) -> ExamTile(data, t, n, Modifier.weight(1f), showDate = true, { onOpen(t) }, { onPlan(t) }, { onStudy(t) }) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
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
    ChunkyCard(
        bg = if (urgent) ui.dangerTint else ui.card,
        edge = if (urgent) ui.danger else ui.hairline,
        lip = if (urgent) ui.dangerLip else ui.hairline,
        padding = 0.dp
    ) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.alberto.upgrade.ui.Eyebrow(
                "${dayName(date).uppercase()} ${date.dayOfMonth} · ${row.size} EXÁMENES",
                color = if (urgent) ui.danger else ui.muted, fontSize = 12.sp, modifier = Modifier.weight(1f)
            )
            Text("$days", color = if (urgent) ui.danger else ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text(if (days == 1L) " DÍA" else " DÍAS", color = if (urgent) ui.danger else ui.muted, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row.forEach { (t, n) ->
                ExamTile(data, t, n, Modifier.width(176.dp), showDate = false, { onOpen(t) }, { onPlan(t) }, { onStudy(t) }, showDays = false)
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
        radius = 16.dp
    ) {
        if (showDays) {
            Row(verticalAlignment = Alignment.Bottom) {
                val c = if (urgent) ui.danger else ui.ink
                Text("$days", color = c, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 34.sp)
                Text(if (days == 1L) "DÍA" else "DÍAS", color = if (urgent) ui.danger else ui.muted, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
        }
        Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (showDays) 6.dp else 0.dp))
        // Siempre dos líneas, para que las tarjetas de una fila midan lo mismo.
        val whenTxt = if (showDate) due?.let { "${dayShort(it).replaceFirstChar { c -> c.uppercase() }} ${it.dayOfMonth}" + (t.time?.let { m -> " · " + minStr(m) } ?: "") } else t.time?.let { minStr(it) }
        Text(detail?.takeIf { it.isNotBlank() } ?: "Examen", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(whenTxt ?: " ", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        GhostButton("Repasar", onStudy, Modifier.fillMaxWidth(), height = 38.dp, icon = Ic.timer, fontSize = 12.sp, radius = 12.dp, lipH = 3.dp, iconSize = 15.dp)
        val planned = data.tasks.count { it.examId == t.id && !it.done }
        Text(
            if (planned > 0) "Plan: $planned ›" else "Plan de repaso ›",
            color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onPlan)
        )
    }
}

/** "Esta semana": Gym (x/4, Torso o Pierna) y Fisio (x/7, todos los días). */
@Composable
private fun WeekCard(data: AppData, today: LocalDate, actions: AppActions) {
    val ui = LocalUi.current
    val showGym = data.shows("gym")
    Column {
        SectionTitle("Esta semana") {
            Text(
                if (data.xp.gym == data.xp.fisio) "+${data.xp.gym} XP cada uno" else "",
                color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp
            )
        }
        ChunkyCard(padding = 0.dp) {
            if (showGym) {
                val done = gymThisWeek(data, today)
                val todayType = data.gymLog[today.key()]
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    HabitHeader(Ic.dumbbell, "Gym", done, data.gymWeeklyTarget,
                        if (todayType != null) "Hoy: $todayType" else {
                            val left = (data.gymWeeklyTarget - done).coerceAtLeast(0)
                            if (left == 0) "Semana cumplida" else "Faltan $left esta semana"
                        })
                    Spacer(Modifier.height(12.dp))
                    if (todayType == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton("Torso", { actions.award(Repo.logGym("Torso"), "¡Torso hecho!") }, Modifier.weight(1f), height = 44.dp, fontSize = 14.sp, radius = 12.dp, lipH = 3.dp)
                            GhostButton("Pierna", { actions.award(Repo.logGym("Pierna"), "¡Pierna hecha!") }, Modifier.weight(1f), height = 44.dp, fontSize = 14.sp, radius = 12.dp, lipH = 3.dp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UIcon(Ic.circleCheck, ui.accentInk, size = 20.dp)
                            Text("$todayType, hecho", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                            com.alberto.upgrade.ui.TextAction("Deshacer", { Repo.unlogGym() }, fontSize = 13.sp)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(ui.hairline))
            }
            val fDone = fisioThisWeek(data, today)
            val doneToday = today.key() in data.fisioLog
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                HabitHeader(Ic.heartPulse, "Fisio", fDone, data.fisioWeeklyTarget, "Ejercicios del fisio · todos los días")
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val monday = today.with(java.time.DayOfWeek.MONDAY)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, n ->
                            val d = monday.plusDays(i.toLong())
                            val hit = d.key() in data.fisioLog
                            val dot by androidx.compose.animation.animateColorAsState(if (hit) ui.primary else ui.surface, motion(tween(180)), label = "fdot")
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(dot)
                                    .then(if (d == today && !hit) Modifier.border(2.dp, ui.strong, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(n, color = if (hit) ui.onPrimary else ui.muted, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                    }
                    if (doneToday) {
                        com.alberto.upgrade.ui.TextAction("Deshacer", { Repo.unlogFisio() }, fontSize = 13.sp)
                    } else {
                        ChunkyButton("Hecho", { actions.award(Repo.logFisio(), "¡Fisio hecho!") }, Modifier.width(104.dp), height = 40.dp, icon = Ic.check, fontSize = 13.sp, radius = 12.dp, lipH = 3.dp, iconSize = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitHeader(icon: Int, name: String, done: Int, target: Int, sub: String) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
            UIcon(icon, ui.ink, size = 22.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(name, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp)
                androidx.compose.animation.AnimatedContent(done, label = "habit") { v ->
                    Text("  $v/$target", color = if (v >= target) ui.accentInk else ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
            Text(sub, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
