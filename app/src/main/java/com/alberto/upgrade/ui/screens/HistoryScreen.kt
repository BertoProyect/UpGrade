package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.XpEvent
import com.alberto.upgrade.data.dayName
import com.alberto.upgrade.data.goalFor
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.minutesOf
import com.alberto.upgrade.data.monthName
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.data.streakOf
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.UIcon
import java.time.LocalDate

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

/** Historial de la racha de XP: cada día, si cumpliste la meta y qué te dio cada punto. */
@Composable
fun HistoryScreen(data: AppData, onClose: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val streak = streakOf(data, today)
    val byDay = remember(data.xpEvents) { data.xpEvents.groupBy { it.date } }
    val days = remember(data.xpLog, data.xpEvents) {
        val keys = (data.xpLog.filterValues { it > 0 }.keys + byDay.keys + today.toString())
            .mapNotNull { parseDate(it) }.distinct().sortedDescending()
        keys.take(120)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Historial de XP", onClose)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    UIcon(Ic.flameFill, if (streak > 0) ui.flame else ui.muted, size = 28.dp)
                    Text("$streak", color = if (streak > 0) ui.flame else ui.muted, fontWeight = FontWeight.Black, fontSize = 30.sp, modifier = Modifier.padding(start = 6.dp))
                    Text(if (streak == 1) " día de racha" else " días de racha", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    UIcon(Ic.trophy, ui.muted, size = 14.dp)
                    Text("${maxOf(data.bestStreak, streak)}", color = ui.muted, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(start = 3.dp))
                }
                Text("${data.totalXp} XP en total", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            items(days, key = { it.toString() }) { d -> DayBlock(data, d, today, byDay[d.toString()].orEmpty()) }
        }
    }
}

@Composable
private fun DayBlock(data: AppData, d: LocalDate, today: LocalDate, events: List<XpEvent>) {
    val ui = LocalUi.current
    val xp = data.xpLog[d.toString()] ?: 0
    val goal = goalFor(data, d)
    val met = xp >= goal
    val title = when (d) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> dayName(d).replaceFirstChar { it.uppercase() } + " ${d.dayOfMonth}" + if (d.year == today.year && d.month == today.month) "" else " de ${monthName(d)}"
    }
    ChunkyCard(Modifier.fillMaxWidth().padding(top = 12.dp), padding = 14.dp, radius = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UIcon(Ic.flameFill, if (met) ui.flame else ui.strong, size = 18.dp)
            Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text("$xp", color = if (met) ui.accentInk else ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(" / $goal XP", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        ProgressBar(xp / goal.toFloat(), if (met) ui.primary else ui.strong, Modifier.fillMaxWidth().padding(top = 8.dp), height = 8.dp)
        if (events.isEmpty()) {
            Text(
                if (xp > 0) "Sin detalle: el historial empezó con esta versión." else "Nada todavía.",
                color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            Column(Modifier.padding(top = 8.dp)) {
                events.sortedByDescending { it.at }.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
                            UIcon(kindIconFor(e.kind), ui.muted, size = 15.dp)
                        }
                        Text(e.label, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Text(minStr(minutesOf(e.at)), color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        Text(
                            (if (e.amount > 0) "+" else "") + "${e.amount}",
                            color = if (e.amount > 0) ui.accentInk else ui.danger, fontWeight = FontWeight.Black, fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
