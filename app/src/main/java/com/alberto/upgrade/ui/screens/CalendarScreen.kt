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
import androidx.compose.ui.draw.alpha
import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.SquareTap
import com.alberto.upgrade.ui.Tag
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
                    monthName(month.atDay(1)).cap() + if (month.year != today.year) " ${month.year}" else "",
                    color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp, modifier = Modifier.weight(1f)
                )
                SquareTap(Ic.chevronLeft, { month = month.minusMonths(1) })
                Box(Modifier.width(10.dp))
                SquareTap(Ic.chevronRight, { month = month.plusMonths(1) })
            }
        }
        item {
            MonthGrid(data, month, selected, today) { d -> selected = if (selected == d) null else d }
        }
        selected?.let { d ->
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("${dayName(d).cap()} ${d.dayOfMonth}", true, { selected = null }, icon = Ic.x)
                    SelectChip("Ver todo", false, { selected = null }, icon = Ic.list)
                }
            }
        }

        val sel = selected
        if (sel != null) {
            val events = eventsOn(data, sel)
            val tasks = tasksDueOn(data, sel).sortedWith(compareBy({ it.done }, { it.time ?: 9999 }))
            item {
                DayHeader(
                    (if (sel == today) "Hoy · " else "") + "${dayName(sel)} ${sel.dayOfMonth} de ${monthName(sel)}",
                    if (sel == today) ui.ink else ui.muted
                ) { AddLink { actions.openAdd(sel, null, null) } }
            }
            if (events.isEmpty() && tasks.isEmpty()) item { EmptyNote("Nada apuntado este día.") }
            items(events, key = { "e" + it.id }) { e -> EventRow(data, e) }
            items(tasks, key = { "t" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) }
        } else {
            val pending = data.tasks.filter { !it.done }
            val overdue = pending.filter { (parseDate(it.due) ?: today) < today }.sortedWith(taskOrder)
            if (overdue.isNotEmpty()) {
                item { DayHeader("Atrasado", ui.danger) }
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
                    DayHeader(
                        when (d) {
                            today -> "Hoy · ${dayName(d)} ${d.dayOfMonth}"
                            today.plusDays(1) -> "Mañana · ${dayName(d)} ${d.dayOfMonth}"
                            else -> if (d.isBefore(today.plusDays(7))) "${dayName(d)} ${d.dayOfMonth}" else "${dayName(d)} ${d.dayOfMonth} de ${monthName(d)}"
                        },
                        if (d == today) ui.ink else ui.muted
                    )
                }
                items(evs, key = { "e" + d + it.id }) { e -> EventRow(data, e) }
                items(ts, key = { "t" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) }
            }
            val weekly = data.events.filter { it.date == null && it.weekdays.isNotEmpty() }
            if (weekly.isNotEmpty()) {
                item { DayHeader("Cada semana", ui.muted) }
                items(weekly, key = { "w" + it.id }) { e -> EventRow(data, e) }
            }
            val noDate = pending.filter { it.due == null }
            if (noDate.isNotEmpty()) {
                item { DayHeader("Sin fecha", ui.muted) }
                items(noDate, key = { "n" + it.id }) { t -> TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) }
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
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(ui.surface).border(1.dp, ui.hairline, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            UIcon(if (e.weekdays.isNotEmpty()) Ic.repeat else Ic.clock, ui.muted, size = 15.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(e.title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (area != null) Tag(area.name, icon = areaIcon(area.id))
                Text(
                    "${minStr(e.start)}–${minStr(e.end)}" + (if (days.isNotEmpty()) " · $days" else ""),
                    color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        IconTap(Ic.trash, { Repo.deleteEvent(e.id) }, size = 32.dp)
    }
}

@Composable
private fun DayHeader(text: String, color: androidx.compose.ui.graphics.Color, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(text, color = color, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun MonthGrid(data: AppData, month: YearMonth, selected: LocalDate?, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val ui = LocalUi.current
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val start = first.minusDays(offset.toLong())
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                Text(it, color = ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            }
        }
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { c ->
                    val date = start.plusDays((r * 7 + c).toLong())
                    Box(Modifier.weight(1f).height(52.dp).padding(vertical = 2.dp)) {
                        DayCell(data, date, date == selected, date == today, YearMonth.from(date) == month) { onSelect(date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(data: AppData, date: LocalDate, selected: Boolean, isToday: Boolean, inMonth: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    val due = tasksDueOn(data, date)
    val exam = due.any { it.kind == Kind.EXAMEN && !it.done }
    val pending = due.count { !it.done }
    val hasEvents = eventsOn(data, date).any { it.date != null }
    val met = date <= LocalDate.now() && goalMet(data, date)
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxSize().alpha(if (inMonth) 1f else 0.45f).clip(shape)
            .background(
                when {
                    selected -> ui.primary
                    exam -> ui.dangerTint
                    else -> Color.Transparent
                }
            )
            .then(if (isToday && !selected) Modifier.border(2.dp, ui.accentInk, shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "${date.dayOfMonth}",
            color = when {
                selected -> ui.onPrimary
                exam -> ui.danger
                !inMonth -> ui.muted
                else -> ui.ink
            },
            fontWeight = if (isToday || selected) FontWeight.Black else FontWeight.ExtraBold,
            fontSize = 15.sp,
            lineHeight = 15.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp).height(10.dp)) {
            if (met) UIcon(Ic.flameFill, if (selected) ui.onPrimary else ui.flame, size = 10.dp)
            else {
                if (pending > 0 && !exam) Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) ui.onPrimary else ui.muted))
                if (hasEvents) Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) ui.onPrimary else ui.text))
            }
        }
    }
}
