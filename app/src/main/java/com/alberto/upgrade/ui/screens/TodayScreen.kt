package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
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
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.Palette
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.TaskRow
import com.alberto.upgrade.ui.bg
import com.alberto.upgrade.ui.fg
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun TodayScreen(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    // Reloj que refresca la vista cada 30 s (clase actual, etc.)
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

    val toggle: (TaskItem) -> Unit = { t ->
        val a = Repo.toggleTask(t.id)
        actions.award(a, if (t.kind.name == "EXAMEN") "¡Examen hecho!" else "¡Bien hecho!")
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
    ) {
        item { TopStats(data, today) { actions.openOverlay("settings") } }

        item {
            HeroCard(data, today, minute, classes, pending.size, overdue.size + dueToday.count { !it.done })
        }

        item { DailyQuest(data, today) }

        if (exams.isNotEmpty()) {
            item { SectionTitle("Exámenes a la vista") }
            items(exams, key = { "ex-" + it.first.id }) { (t, n) ->
                ExamRow(data, t, n, onOpen = { actions.openTask(t) }) {
                    Repo.startFocus(ctx, 25)
                    actions.goTab(2)
                }
            }
        }

        if (classes.isNotEmpty()) {
            item { SectionTitle("Clases de hoy") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(classes, key = { "c" + it.index }) { c ->
                        val now = minute in c.period.start until c.period.end
                        val past = minute >= c.period.end
                        val tag = Palette.of(c.subject.color)
                        Column(
                            Modifier.width(96.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (past) ui.surface else tag.bg(ui))
                                .border(if (now) 3.dp else 0.dp, if (now) ui.primary else Color.Transparent, RoundedCornerShape(14.dp))
                                .padding(10.dp)
                        ) {
                            Text(minStr(c.period.start), color = if (past) ui.muted else tag.fg(ui), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                c.subject.name,
                                color = if (past) ui.muted else tag.fg(ui),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (now) Text("AHORA", color = ui.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            item { SectionTitle("Agenda") }
            items(events, key = { "ev-" + it.id }) { e ->
                val area = data.area(e.areaId)
                val tag = Palette.of(area?.color)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(tag.bg(ui)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${minStr(e.start)}–${minStr(e.end)}", color = tag.fg(ui), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(100.dp))
                    Text("${area?.emoji ?: ""} ${e.title}", color = tag.fg(ui), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                ChunkyCard(bg = if (ui.dark) Color(0xFF26223F) else Color(0xFFE6E0F5), edge = ui.primary.copy(alpha = 0.5f)) {
                    Text("🌙 Cierra el día", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text(
                        "Dos minutos. Mira lo que has hecho y deja decidido lo primero de mañana.",
                        color = ui.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    ChunkyButton("Cerrar el día", { actions.openOverlay("review") }, Modifier.fillMaxWidth())
                }
                }
            }
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
        Stat("🔥", "$streak", if (streak > 0) ui.flame else ui.muted)
        Spacer(Modifier.width(18.dp))
        Stat("⚡", "$xp", ui.xp)
        Spacer(Modifier.width(18.dp))
        Stat("🏅", "Nv ${lv.level}", ui.primary)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) { Text("⚙️", fontSize = 22.sp) }
    }
}

@Composable
private fun Stat(emoji: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp)
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun HeroCard(
    data: AppData,
    today: LocalDate,
    minute: Int,
    classes: List<com.alberto.upgrade.data.ClassToday>,
    pendingCount: Int,
    todayLeft: Int
) {
    val ui = LocalUi.current
    val current = classes.firstOrNull { minute in it.period.start until it.period.end }
    val next = classes.firstOrNull { it.period.start > minute }
    val dateLine = "${dayName(today).cap()} ${today.dayOfMonth} de ${monthName(today)}"
    val (title, sub) = when {
        current != null -> "Ahora: ${current.subject.name}" to
            ("Hasta las ${minStr(current.period.end)}" + (next?.let { " · después ${it.subject.name}" } ?: ""))
        next != null && minute < (classes.firstOrNull()?.period?.start ?: 0) ->
            "Empiezas con ${next.subject.name}" to "A las ${minStr(next.period.start)} · ${classes.size} clases hoy"
        next != null -> "Recreo" to "Vuelves a las ${minStr(next.period.start)} con ${next.subject.name}"
        todayLeft > 0 -> "Te quedan $todayLeft para hoy" to "Una a una. Marca la primera y arranca."
        pendingCount > 0 -> "Hoy está limpio" to "Tienes $pendingCount pendientes para otros días. Adelanta una."
        else -> "Todo al día" to "Nada pendiente. Disfrútalo, te lo has ganado."
    }
    Box(
        Modifier.fillMaxWidth().padding(top = 8.dp)
            .background(ui.primaryLip, RoundedCornerShape(18.dp))
            .padding(bottom = 5.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ui.primary).padding(18.dp)
        ) {
            Text(dateLine, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp, modifier = Modifier.padding(top = 4.dp))
            Text(sub, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DailyQuest(data: AppData, today: LocalDate) {
    val ui = LocalUi.current
    val xp = xpOn(data, today)
    val met = xp >= data.dailyGoal
    Column {
    Spacer(Modifier.height(14.dp))
    ChunkyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡", fontSize = 30.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (met) "Meta diaria cumplida" else "Gana ${data.dailyGoal} XP hoy",
                    color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp
                )
                Spacer(Modifier.height(8.dp))
                Box(contentAlignment = Alignment.Center) {
                    ProgressBar(xp / data.dailyGoal.toFloat(), if (met) ui.success else ui.xp, Modifier.fillMaxWidth(), height = 18.dp)
                    Text(
                        "${minOf(xp, data.dailyGoal)} / ${data.dailyGoal}",
                        color = if (xp > data.dailyGoal / 2) Color.White else ui.text,
                        fontSize = 12.sp, fontWeight = FontWeight.Black
                    )
                }
            }
            Text(if (met) "🎁" else "🔒", fontSize = 26.sp, modifier = Modifier.padding(start = 12.dp))
        }
        Text(
            if (met) "La racha sigue viva. Lo que sumes ahora es extra." else "Cumplirla mantiene tu racha 🔥",
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
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (days == 0L) "HOY" else "$days",
                    color = if (urgent) ui.danger else ui.ink, fontWeight = FontWeight.Black, fontSize = if (days == 0L) 22.sp else 30.sp
                )
                if (days > 0) Text(if (days == 1L) "día" else "días", color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(t.title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subject != null) Text(subject.name, color = Palette.of(subject.color).fg(ui), fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
    val gym = Palette.of("orange")
    Column {
    Spacer(Modifier.height(20.dp))
    ChunkyCard(bg = gym.bg(ui), edge = gym.strong.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💪", fontSize = 28.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Gym · $done/${data.gymWeeklyTarget} esta semana", color = gym.fg(ui), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(
                    if (todayType != null) "Hoy: $todayType ✓" else "Te toca: ${nextGymType(data)} · 1 h + 26 min de camino",
                    color = gym.fg(ui), fontSize = 14.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(done / data.gymWeeklyTarget.toFloat(), gym.strong, Modifier.fillMaxWidth(), height = 12.dp, track = ui.card)
        Spacer(Modifier.height(12.dp))
        if (todayType == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkyButton("Torso", { actions.award(Repo.logGym("Torso"), "¡Torso hecho!") }, Modifier.weight(1f), face = gym.strong, lip = Color(0xFF9A3F00), height = 44.dp)
                ChunkyButton("Pierna", { actions.award(Repo.logGym("Pierna"), "¡Pierna hecha!") }, Modifier.weight(1f), face = gym.strong, lip = Color(0xFF9A3F00), height = 44.dp)
            }
        } else {
            GhostButton("Deshacer", { Repo.unlogGym() }, Modifier.fillMaxWidth(), height = 40.dp)
        }
    }
    }
}
