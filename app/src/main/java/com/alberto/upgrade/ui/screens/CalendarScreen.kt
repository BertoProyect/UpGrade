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
import com.alberto.upgrade.data.TaskItem
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.data.taskOrder
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.EmptyNote
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.SmallRound
import com.alberto.upgrade.ui.TaskRow
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.areaIcon
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    // null = ver todo lo que viene; una fecha = solo ese día
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    val toggle: (TaskItem) -> Unit = { t -> actions.award(Repo.toggleTask(t.id), "¡Bien hecho!") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${monthName(month.atDay(1)).cap()} ${month.year}",
                    color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.weight(1f)
                )
                SmallRound("", { month = month.minusMonths(1) }, icon = Ic.chevronLeft)
                Box(Modifier.width(8.dp))
                SmallRound("", { month = month.plusMonths(1) }, icon = Ic.chevronRight)
            }
        }
        item {
            MonthGrid(data, month, selected, today) { d -> selected = if (selected == d) null else d }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Todo lo que viene", selected == null, { selected = null }, icon = Ic.list)
                selected?.let { d ->
                    SelectChip("${dayName(d).cap()} ${d.dayOfMonth}", true, { selected = null }, icon = Ic.x)
                }
            }
        }

        val sel = selected
        if (sel != null) {
            val events = eventsOn(data, sel)
            val tasks = tasksDueOn(data, sel).sortedWith(compareBy({ it.done }, { it.time ?: 9999 }))
            item {
                SectionTitle("${dayName(sel).cap()} ${sel.dayOfMonth} de ${monthName(sel)}") {
                    AddLink { actions.openAdd(sel, null, null) }
                }
            }
            if (events.isEmpty() && tasks.isEmpty()) item { EmptyNote("Nada apuntado este día.") }
            items(events, key = { "e" + it.id }) { e -> EventRow(data, e) }
            items(tasks, key = { "t" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
        } else {
            val pending = data.tasks.filter { !it.done }
            val overdue = pending.filter { (parseDate(it.due) ?: today) < today }.sortedWith(taskOrder)
            if (overdue.isNotEmpty()) {
                item { SectionTitle("Atrasado") { Text("${overdue.size}", color = ui.danger, fontWeight = FontWeight.Black, fontSize = 18.sp) } }
                items(overdue, key = { "o" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
            }
            val horizon = today.plusDays(60)
            val days = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(horizon) }
                .map { d ->
                    Triple(
                        d,
                        data.events.filter { it.date == d.key() },
                        pending.filter { it.due == d.key() }.sortedWith(taskOrder)
                    )
                }
                .filter { it.second.isNotEmpty() || it.third.isNotEmpty() }
                .toList()
            if (days.isEmpty() && overdue.isEmpty()) {
                item { EmptyNote("No tienes nada por delante. Pulsa + o dicta lo que tengas.") }
            }
            days.forEach { (d, evs, ts) ->
                item(key = "h" + d) {
                    SectionTitle(
                        when (d) {
                            today -> "Hoy"
                            today.plusDays(1) -> "Mañana"
                            else -> "${dayName(d).cap()} ${d.dayOfMonth} de ${monthName(d)}"
                        }
                    )
                }
                items(evs, key = { "e" + d + it.id }) { e -> EventRow(data, e) }
                items(ts, key = { "t" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
            }
            val weekly = data.events.filter { it.date == null && it.weekdays.isNotEmpty() }
            if (weekly.isNotEmpty()) {
                item { SectionTitle("Cada semana") }
                items(weekly, key = { "w" + it.id }) { e -> EventRow(data, e) }
            }
            val noDate = pending.filter { it.due == null }
            if (noDate.isNotEmpty()) {
                item { SectionTitle("Sin fecha") }
                items(noDate, key = { "n" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) }
            }
        }
    }
}

@Composable
private fun AddLink(onClick: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIcon(Ic.plus, ui.accentInk, size = 18.dp)
        Text("Añadir", color = ui.accentInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun EventRow(data: AppData, e: com.alberto.upgrade.data.EventItem) {
    val ui = LocalUi.current
    val area = data.area(e.areaId)
    val days = listOf("L", "M", "X", "J", "V", "S", "D").filterIndexed { i, _ -> (i + 1) in e.weekdays }.joinToString(" ")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(ui.surface).border(1.dp, ui.hairline, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIcon(if (e.weekdays.isNotEmpty()) Ic.repeat else areaIcon(area?.id), ui.muted, size = 18.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(e.title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${minStr(e.start)}–${minStr(e.end)}" + (if (days.isNotEmpty()) " · $days" else "") + (area?.let { " · ${it.name}" } ?: ""),
                color = ui.muted, fontSize = 13.sp
            )
        }
        IconTap(Ic.trash, { Repo.deleteEvent(e.id) }, size = 32.dp)
    }
}

@Composable
private fun MonthGrid(data: AppData, month: YearMonth, selected: LocalDate?, today: LocalDate, onSelect: (LocalDate) -> Unit) {
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
            .border(if (isToday && !selected) 2.dp else 0.dp, if (isToday && !selected) ui.accentInk else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "${date.dayOfMonth}",
            color = when {
                selected -> ui.onPrimary
                exam -> ui.danger
                else -> ui.ink
            },
            fontWeight = if (isToday || selected || exam) FontWeight.Black else FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
            if (met) UIcon(Ic.flameFill, if (selected) ui.onPrimary else ui.flame, size = 9.dp)
            if (pending > 0) Box(Modifier.size(6.dp).clip(CircleShape).background(if (selected) ui.onPrimary else ui.text))
            if (hasEvents) Box(Modifier.size(6.dp).clip(CircleShape).background(if (selected) ui.onPrimary else ui.muted))
        }
        Box(Modifier.height(1.dp))
    }
}
