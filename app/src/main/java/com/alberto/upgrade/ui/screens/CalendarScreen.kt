package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Kind
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.cap
import com.alberto.upgrade.data.classesOn
import com.alberto.upgrade.data.dayName
import com.alberto.upgrade.data.eventsOn
import com.alberto.upgrade.data.goalMet
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.monthName
import com.alberto.upgrade.data.tasksDueOn
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.EmptyNote
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.Palette
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.SmallRound
import com.alberto.upgrade.ui.TaskRow
import com.alberto.upgrade.ui.bg
import com.alberto.upgrade.ui.fg
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }

    val classes = classesOn(data, selected)
    val events = eventsOn(data, selected)
    val tasks = tasksDueOn(data, selected).sortedWith(compareBy({ it.done }, { it.kind != Kind.EXAMEN }))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${monthName(month.atDay(1)).cap()} ${month.year}",
                    color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.weight(1f)
                )
                SmallRound("‹") { month = month.minusMonths(1) }
                Box(Modifier.width(8.dp))
                SmallRound("›") { month = month.plusMonths(1) }
            }
        }
        item { MonthGrid(data, month, selected, today) { selected = it } }

        item {
            SectionTitle("${dayName(selected).cap()} ${selected.dayOfMonth}") {
                Text(
                    "+ Añadir",
                    color = ui.primary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { actions.openAdd(selected) }.padding(8.dp)
                )
            }
        }

        if (classes.isEmpty() && events.isEmpty() && tasks.isEmpty()) {
            item { EmptyNote("Día libre. Nada apuntado.") }
        }

        items(events, key = { "e" + it.id }) { e ->
            val area = data.area(e.areaId)
            val tag = Palette.of(area?.color)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(tag.bg(ui)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${minStr(e.start)}–${minStr(e.end)}", color = tag.fg(ui), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(100.dp))
                Text(
                    "${area?.emoji ?: ""} ${e.title}" + if (e.weekdays.isNotEmpty()) " · semanal" else "",
                    color = tag.fg(ui), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text("✕", color = tag.fg(ui), fontWeight = FontWeight.Black, modifier = Modifier.clip(CircleShape).clickable { Repo.deleteEvent(e.id) }.padding(6.dp))
            }
        }

        items(tasks, key = { "t" + it.id }) { t ->
            TaskRow(t, data, { actions.award(Repo.toggleTask(t.id), "¡Bien hecho!") }, { actions.openTask(t) }, today)
        }

        if (classes.isNotEmpty()) {
            item { SectionTitle("Clases") }
            items(classes, key = { "c" + it.index }) { c ->
                val tag = Palette.of(c.subject.color)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${minStr(c.period.start)}–${minStr(c.period.end)}", color = ui.muted, fontSize = 14.sp, modifier = Modifier.width(110.dp))
                    Text(
                        c.subject.name,
                        color = tag.fg(ui), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(tag.bg(ui)).padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(data: AppData, month: YearMonth, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val ui = LocalUi.current
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val cells = offset + days
    val rows = (cells + 6) / 7
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                Text(it, color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val n = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(0.9f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..days) {
                            val date = month.atDay(n)
                            DayCell(data, date, date == selected, date == today) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(data: AppData, date: LocalDate, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    val due = tasksDueOn(data, date)
    val exam = due.any { it.kind == Kind.EXAMEN && !it.done }
    val pending = due.count { !it.done }
    val hasEvents = eventsOn(data, date).any { it.date != null }
    val met = date <= LocalDate.now() && goalMet(data, date)
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxSize().clip(shape)
            .background(
                when {
                    selected -> ui.primary
                    exam -> ui.dangerTint
                    else -> Color.Transparent
                }
            )
            .border(if (isToday && !selected) 2.dp else 0.dp, if (isToday && !selected) ui.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "${date.dayOfMonth}",
            color = when {
                selected -> Color.White
                exam -> ui.danger
                else -> ui.ink
            },
            fontWeight = if (isToday || selected || exam) FontWeight.Black else FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
            if (met) Text("🔥", fontSize = 9.sp)
            if (pending > 0) Box(Modifier.size(6.dp).clip(CircleShape).background(if (selected) Color.White else ui.primary))
            if (hasEvents) Box(Modifier.size(6.dp).clip(CircleShape).background(if (selected) Color.White else Palette.of("green").strong))
        }
        Box(Modifier.height(1.dp))
    }
}
