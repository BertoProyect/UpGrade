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
import com.alberto.upgrade.ui.areaIcon
import kotlinx.coroutines.delay
import java.time.LocalDate

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
    val dueToday = data.tasks.filter { it.due == today.key() }.sortedBy { it.done }
    val tomorrow = pending.filter { it.due == today.plusDays(1).key() }
    val later = pending.filter {
        val d = parseDate(it.due)
        d != null && d > today.plusDays(1) && d <= today.plusDays(7)
    }.sortedBy { it.due }
    val noDate = pending.filter { it.due == null }
    val exams = pendingExams(data, today, 10)
    val leftToday = overdue.size + dueToday.count { !it.done }

    val toggle: (TaskItem) -> Unit = { t ->
        actions.award(Repo.toggleTask(t.id), if (t.kind == Kind.EXAMEN) "¡Examen hecho!" else "¡Bien hecho!")
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
    ) {
        item { TopStats(data, today) { actions.openOverlay("settings") } }
        item { DayCard(data, today, leftToday, pending.size) }

        if (exams.isNotEmpty()) {
            item { SectionTitle("Exámenes a la vista") }
            items(exams, key = { "ex-" + it.first.id }) { (t, n) ->
                ExamRow(data, t, n, onOpen = { actions.openTask(t) }) {
                    Repo.startFocus(ctx, 25)
                    actions.goTab(2)
                }
            }
        }

        if (events.isNotEmpty()) {
            item { SectionTitle("Agenda") }
            items(events, key = { "ev-" + it.id }) { e ->
                val area = data.area(e.areaId)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(ui.surface).border(1.dp, ui.hairline, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(areaIcon(area?.id), ui.muted, size = 18.dp)
                    Text("${minStr(e.start)}–${minStr(e.end)}", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).width(96.dp))
                    Text(e.title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (overdue.isNotEmpty()) {
            item { SectionTitle("Atrasado") { Text("${overdue.size}", color = ui.danger, fontWeight = FontWeight.Black, fontSize = 18.sp) } }
            items(overdue, key = { "o-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
        }

        item { SectionTitle("Para hoy") }
        if (dueToday.isEmpty()) {
            item { EmptyNote("Nada con fecha de hoy. Adelanta algo de mañana y suma XP.") }
        } else {
            items(dueToday, key = { "t-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
        }

        item { GymCard(data, today, actions) }

        if (tomorrow.isNotEmpty()) {
            item { SectionTitle("Para mañana") }
            items(tomorrow, key = { "m-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
        }
        if (later.isNotEmpty()) {
            item { SectionTitle("Esta semana") }
            items(later, key = { "l-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
        }
        if (noDate.isNotEmpty()) {
            item { SectionTitle("Sin fecha") }
            items(noDate, key = { "n-" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
        }

        if (!data.reviews.containsKey(today.key()) && minute >= 19 * 60) {
            item {
                Column {
                    Spacer(Modifier.height(20.dp))
                    ChunkyCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UIcon(Ic.moon, ui.accentInk, size = 24.dp)
                            Text("Cierra el día", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                        Text(
                            "Dos minutos. Mira lo que has hecho y deja decidido lo primero de mañana.",
                            color = ui.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        ChunkyButton("Cerrar el día", { actions.openOverlay("review") }, Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (classes.isNotEmpty()) {
            item { ScheduleStrip(classes, minute) }
        }
    }
}

@Composable
private fun TopStats(data: AppData, today: LocalDate, onSettings: () -> Unit) {
    val ui = LocalUi.current
    val streak = streakOf(data, today)
    val xp = xpOn(data, today)
    val lv = levelOf(data.totalXp)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Stat(if (streak > 0) Ic.flameFill else Ic.flame, "$streak", if (streak > 0) ui.flame else ui.muted)
        Spacer(Modifier.width(20.dp))
        Stat(Ic.zapFill, "$xp", ui.accentInk)
        Spacer(Modifier.width(20.dp))
        Stat(Ic.award, "Nv ${lv.level}", ui.ink)
        Spacer(Modifier.weight(1f))
        IconTap(Ic.settings, onSettings)
    }
}

@Composable
private fun Stat(icon: Int, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        UIcon(icon, color, size = 22.dp)
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun DayCard(data: AppData, today: LocalDate, leftToday: Int, pendingCount: Int) {
    val ui = LocalUi.current
    val xp = xpOn(data, today)
    val met = xp >= data.dailyGoal
    val (title, sub) = when {
        leftToday > 0 -> "Te quedan $leftToday para hoy" to "Una a una. Marca la primera y arranca."
        pendingCount > 0 -> "Hoy está limpio" to "Tienes $pendingCount pendientes para otros días. Adelanta una."
        else -> "Todo al día" to "Nada pendiente. Disfrútalo, te lo has ganado."
    }
    Column {
        Spacer(Modifier.height(6.dp))
        ChunkyCard {
            Text(
                "${dayName(today).cap()} ${today.dayOfMonth} de ${monthName(today)}",
                color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Text(title, color = ui.ink, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp, modifier = Modifier.padding(top = 2.dp))
            Text(sub, color = ui.text, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIcon(if (met) Ic.circleCheck else Ic.zapFill, ui.accentInk, size = 20.dp)
                Text(
                    if (met) "Meta diaria cumplida" else "Meta diaria",
                    color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                    modifier = Modifier.padding(start = 6.dp).weight(1f)
                )
                Text("${minOf(xp, data.dailyGoal)} / ${data.dailyGoal} XP", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(xp / data.dailyGoal.toFloat(), ui.primary, Modifier.fillMaxWidth(), height = 16.dp)
            Text(
                if (met) "La racha sigue viva. Lo que sumes ahora es extra." else "Cumplirla mantiene tu racha.",
                color = ui.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ExamRow(data: AppData, t: TaskItem, days: Long, onOpen: () -> Unit, onStudy: () -> Unit) {
    val ui = LocalUi.current
    val subject = data.subject(t.subjectId)
    val urgent = days <= 2
    ChunkyCard(
        Modifier.padding(vertical = 4.dp),
        bg = if (urgent) ui.dangerTint else ui.card,
        edge = if (urgent) ui.danger.copy(alpha = 0.6f) else ui.hairline,
        onClick = onOpen,
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (days == 0L) "HOY" else "$days",
                    color = if (urgent) ui.danger else ui.ink, fontWeight = FontWeight.Black, fontSize = if (days == 0L) 22.sp else 30.sp
                )
                if (days > 0) Text(if (days == 1L) "día" else "días", color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(t.title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subject != null) Text(subject.name, color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (days > 0) {
                ChunkyButton("Repasar", onStudy, Modifier.width(112.dp), height = 40.dp, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun GymCard(data: AppData, today: LocalDate, actions: AppActions) {
    val ui = LocalUi.current
    val done = gymThisWeek(data, today)
    val todayType = data.gymLog[today.key()]
    Column {
        Spacer(Modifier.height(20.dp))
        ChunkyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIcon(Ic.activity, ui.ink, size = 26.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Gym · $done/${data.gymWeeklyTarget} esta semana", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text(
                        if (todayType != null) "Hoy: $todayType, hecho" else "Te toca: ${nextGymType(data)} · 1 h + 26 min de camino",
                        color = ui.muted, fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ProgressBar(done / data.gymWeeklyTarget.toFloat(), ui.primary, Modifier.fillMaxWidth(), height = 12.dp)
            Spacer(Modifier.height(12.dp))
            if (todayType == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Torso", { actions.award(Repo.logGym("Torso"), "¡Torso hecho!") }, Modifier.weight(1f), height = 44.dp)
                    GhostButton("Pierna", { actions.award(Repo.logGym("Pierna"), "¡Pierna hecha!") }, Modifier.weight(1f), height = 44.dp)
                }
            } else {
                GhostButton("Deshacer", { Repo.unlogGym() }, Modifier.fillMaxWidth(), height = 40.dp)
            }
        }
    }
}

/** Horario: abajo, discreto. Todo igual menos la clase de ahora. */
@Composable
private fun ScheduleStrip(classes: List<ClassToday>, minute: Int) {
    val ui = LocalUi.current
    Column {
        SectionTitle("Horario de hoy")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            classes.forEach { c ->
                val now = minute in c.period.start until c.period.end
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (now) ui.primary else ui.surface)
                        .border(1.dp, if (now) ui.primary else ui.hairline, RoundedCornerShape(10.dp))
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        minStr(c.period.start),
                        color = if (now) ui.onPrimary else ui.muted,
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Text(
                        c.subject.name.take(4),
                        color = if (now) ui.onPrimary else ui.text,
                        fontSize = 12.sp, fontWeight = if (now) FontWeight.Black else FontWeight.Bold,
                        maxLines = 1, textAlign = TextAlign.Center
                    )
                }
            }
        }
        Box(Modifier.height(4.dp))
    }
}
