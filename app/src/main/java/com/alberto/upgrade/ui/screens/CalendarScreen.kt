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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.compositeOver
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
fun CalendarScreen(data0: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    // Filtro por área: null = todas. Se filtran tareas y eventos; las marcas de racha no.
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val data = remember(data0, filter) {
        val f = filter
        if (f == null) data0
        else {
            val known = data0.areas.map { it.id }.toSet()
            fun match(a: String) = if (f == "otro") a == "otro" || a !in known else a == f
            data0.copy(tasks = data0.tasks.filter { match(it.areaId) }, events = data0.events.filter { match(it.areaId) })
        }
    }
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
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                item { SelectChip("Todas", filter == null, { filter = null }) }
                val opts = com.alberto.upgrade.data.listedAreas(data0).map { it.id to it.name } + ("otro" to "Otro")
                items(opts.size) { i ->
                    val (id, name) = opts[i]
                    SelectChip(name, filter == id, { filter = if (filter == id) null else id }, icon = if (filter == id) Ic.x else areaIcon(id))
                }
            }
        }
        item {
            MonthGrid(data, month, selected, today) { d -> selected = if (selected == d) null else d }
        }
        item {
            // Leyenda: solo lo que de verdad aparece en las celdas.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LegendItem("Examen") {
                    Text("8", style = examGlow(ui), fontWeight = FontWeight.Black, fontSize = 13.sp, lineHeight = 13.sp)
                }
                LegendItem("Tareas") { TaskDots(2, ui.muted, ui.bg) }
            }
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
            if (events.isEmpty() && tasks.isEmpty()) item {
                EmptyNote(filter?.let { f -> "Nada de ${if (f == "otro") "Otro" else data0.area(f)?.name ?: "esta área"} este día." } ?: "Nada apuntado este día.")
            }
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
                    Box(Modifier.weight(1f).height(58.dp).padding(vertical = 1.dp)) {
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
    // Puntos: lo pendiente que no es examen (tareas, deberes, llamadas) y los eventos de ese día.
    val pending = due.count { !it.done && it.kind != Kind.EXAMEN } + data.events.count { it.date == date.key() }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxSize().alpha(if (inMonth) 1f else 0.45f).clip(shape)
            .then(
                when {
                    // La selección abarca la celda entera, así nunca tapa una marca.
                    selected -> Modifier.background(ui.selTint).border(2.dp, ui.accentInk, shape)
                    isToday -> Modifier.border(2.dp, ui.strong, shape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
    ) {
        // Número arriba y, debajo, una fila fija para los puntos de tareas.
        Column(Modifier.fillMaxSize().padding(top = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                // Día con examen: el número brilla.
                style = if (exam) examGlow(ui) else androidx.compose.ui.text.TextStyle.Default,
                color = when {
                    exam -> if (ui.dark) Color.White else ui.ink
                    !inMonth -> ui.muted
                    else -> ui.text
                },
                fontWeight = if (isToday || selected || exam) FontWeight.Black else FontWeight.ExtraBold,
                fontSize = 16.sp,
                lineHeight = 20.sp
            )
            Box(Modifier.padding(top = 8.dp).height(7.dp), contentAlignment = Alignment.Center) {
                if (pending > 0) {
                    // El borde de cada punto es el fondo de la celda, para que se vea el solape.
                    val edge = if (selected) ui.selTint.compositeOver(ui.bg) else ui.bg
                    TaskDots(pending, if (selected) ui.accentInk else ui.muted, edge)
                }
            }
        }
    }
}

/** Halo del número cuando ese día hay examen: blanco en oscuro, tinta suave en claro. El radio va en dp para que se vea igual en cualquier pantalla. */
@Composable
private fun examGlow(ui: com.alberto.upgrade.ui.Ui): androidx.compose.ui.text.TextStyle {
    val blur = with(androidx.compose.ui.platform.LocalDensity.current) { (if (ui.dark) 8.dp else 6.dp).toPx() }
    return androidx.compose.ui.text.TextStyle(
        shadow = androidx.compose.ui.graphics.Shadow(
            color = if (ui.dark) Color.White.copy(alpha = 0.85f) else ui.ink.copy(alpha = 0.35f),
            offset = androidx.compose.ui.geometry.Offset.Zero,
            blurRadius = blur
        )
    )
}

@Composable
private fun LegendItem(label: String, icon: @Composable () -> Unit) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.height(14.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

/** 1, 2 o 3 puntos solapados: del de detrás solo asoma la mitad. */
@Composable
private fun TaskDots(count: Int, color: Color, edge: Color) {
    val n = count.coerceIn(1, 3)
    val d = 7.dp
    Box(Modifier.width(d + d / 2 * (n - 1)).height(d)) {
        // Se dibuja de detrás hacia delante: el primero queda encima.
        for (i in (n - 1) downTo 0) {
            Box(
                Modifier.offset(x = d / 2 * i).size(d).clip(CircleShape).background(edge).padding(1.dp)
                    .clip(CircleShape).background(color)
            )
        }
    }
}
