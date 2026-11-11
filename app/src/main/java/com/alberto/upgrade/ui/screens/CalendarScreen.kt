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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import com.alberto.upgrade.ui.reveal
import kotlinx.coroutines.launch

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
    // Meses en carrusel: se cambia deslizando la rejilla. 1200 = el mes actual.
    val base = remember { YearMonth.from(today) }
    val monthPager = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1200) { 2400 }
    val month = base.plusMonths((monthPager.currentPage - 1200).toLong())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val reduce = com.alberto.upgrade.ui.LocalReduceMotion.current
    var filterOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // null = ver todo lo que viene; una fecha = solo ese día
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    val toggle: (TaskItem) -> Unit = { t -> actions.award(Repo.toggleTask(t.id), "¡Bien hecho!") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // El nombre del mes rueda hacia el lado al que deslizas.
                androidx.compose.animation.AnimatedContent(
                    targetState = month,
                    transitionSpec = {
                        val fwd = targetState > initialState
                        if (reduce) androidx.compose.animation.fadeIn(androidx.compose.animation.core.snap()) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.snap())
                        else (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.spring(0.8f, 600f)) { if (fwd) it / 3 else -it / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180))) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(160)) { if (fwd) -it / 3 else it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "month"
                ) { m ->
                    Text(
                        monthName(m.atDay(1)).cap() + if (m.year != today.year) " ${m.year}" else "",
                        color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp
                    )
                }
                // Volver al mes actual, solo si te has movido.
                androidx.compose.animation.AnimatedVisibility(
                    month != base,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f)
                ) {
                    SelectChip("Hoy", false, {
                        scope.launch { if (reduce) monthPager.scrollToPage(1200) else monthPager.animateScrollToPage(1200) }
                        selected = null
                    }, icon = Ic.calendarDays)
                }
                Box(Modifier.width(8.dp))
                // Pestañita que pliega y despliega el filtro de áreas.
                val active = filter != null
                val rot by androidx.compose.animation.core.animateFloatAsState(if (filterOpen) 180f else 0f, com.alberto.upgrade.ui.motion(androidx.compose.animation.core.tween(200)), label = "fchev")
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.height(36.dp).clip(shape)
                        .background(if (active || filterOpen) ui.selTint else Color.Transparent)
                        .border(2.dp, if (active || filterOpen) ui.accentInk else ui.hairline, shape)
                        .clickable { filterOpen = !filterOpen }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(Ic.filter, if (active || filterOpen) ui.accentInk else ui.ink, size = 16.dp)
                    if (active) Text(
                        filter?.let { f -> if (f == "otro") "Otro" else data0.area(f)?.name ?: "" } ?: "",
                        color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp).widthIn(max = 90.dp), overflow = TextOverflow.Ellipsis
                    )
                    UIcon(Ic.chevronDown, if (active || filterOpen) ui.accentInk else ui.muted, size = 14.dp, modifier = Modifier.padding(start = 4.dp).rotate(rot))
                }
            }
        }
        item(key = "filter") {
            androidx.compose.animation.AnimatedVisibility(
                filterOpen,
                enter = androidx.compose.animation.expandVertically(com.alberto.upgrade.ui.motion(com.alberto.upgrade.ui.UpMotion.state())) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically(com.alberto.upgrade.ui.motion(com.alberto.upgrade.ui.UpMotion.state())) + androidx.compose.animation.fadeOut()
            ) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    item { SelectChip("Todas", filter == null, { filter = null }) }
                    val opts = com.alberto.upgrade.data.listedAreas(data0).map { it.id to it.name } + ("otro" to "Otro")
                    items(opts.size) { i ->
                        val (id, name) = opts[i]
                        SelectChip(name, filter == id, { filter = if (filter == id) null else id }, icon = if (filter == id) Ic.x else areaIcon(id))
                    }
                }
            }
        }
        item(key = "grid") {
            // Deslizar la rejilla cambia de mes. Va siempre con 6 filas para que la altura no salte.
            androidx.compose.foundation.pager.HorizontalPager(
                state = monthPager,
                modifier = Modifier.fillMaxWidth(),
                beyondViewportPageCount = 1,
                verticalAlignment = Alignment.Top
            ) { page ->
                val m = base.plusMonths((page - 1200).toLong())
                MonthGrid(data, m, selected, today) { d -> selected = if (selected == d) null else d }
            }
        }
        item {
            // Leyenda: solo lo que de verdad aparece en las celdas.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LegendItem("Examen") {
                    Text("8", style = examGlow(ui), color = ui.danger, fontWeight = FontWeight.Black, fontSize = 13.sp, lineHeight = 13.sp)
                }
                LegendItem("Tareas") { TaskDots(2, ui.muted, ui.bg) }
            }
        }
        selected?.let { d ->
            item(key = "selchips") {
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
            item(key = "selhead") {
                DayHeader(
                    (if (sel == today) "Hoy · " else "") + "${dayName(sel)} ${sel.dayOfMonth} de ${monthName(sel)}",
                    if (sel == today) ui.ink else ui.muted
                ) { AddLink { actions.openAdd(sel, null, null) } }
            }
            if (events.isEmpty() && tasks.isEmpty()) item(key = "selempty") {
                EmptyNote(filter?.let { f -> "Nada de ${if (f == "otro") "Otro" else data0.area(f)?.name ?: "esta área"} este día." } ?: "Nada apuntado este día.")
            }
            items(events, key = { "e" + it.id }) { e -> Box(Modifier.animateItem().reveal(0)) { EventRow(data, e) } }
            items(tasks, key = { "t" + it.id }) { t -> Box(Modifier.animateItem().reveal(0)) { TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) } }
        } else {
            val pending = data.tasks.filter { !it.done }
            val overdue = pending.filter { (parseDate(it.due) ?: today) < today }.sortedWith(taskOrder)
            if (overdue.isNotEmpty()) {
                item(key = "ovh") { DayHeader("Atrasado", ui.danger) }
                items(overdue, key = { "o" + it.id }) { t -> Box(Modifier.animateItem().reveal(0, once = true)) { TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today) } }
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
                item(key = "allempty") { EmptyNote(filter?.let { "Nada pendiente en esta área. Quita el filtro para verlo todo." } ?: "No tienes nada por delante. Pulsa + o dicta lo que tengas.") }
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
                items(evs, key = { "e" + d + it.id }) { e -> Box(Modifier.animateItem().reveal(0, once = true)) { EventRow(data, e) } }
                items(ts, key = { "t" + it.id }) { t -> Box(Modifier.animateItem().reveal(0, once = true)) { TaskRow(t, data, { toggle(t) }, { actions.openTask(t) }, today, showDate = false) } }
            }
            val weekly = data.events.filter { it.date == null && it.weekdays.isNotEmpty() }
            if (weekly.isNotEmpty()) {
                item(key = "wkh") { DayHeader("Cada semana", ui.muted) }
                items(weekly, key = { "w" + it.id }) { e -> EventRow(data, e) }
            }
            val noDate = pending.filter { it.due == null }
            if (noDate.isNotEmpty()) {
                item(key = "ndh") { DayHeader("Sin fecha", ui.muted) }
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
    // Siempre 6 filas: así todos los meses miden lo mismo y el carrusel no da saltos.
    val rows = 6
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
    val spec = com.alberto.upgrade.ui.motion(androidx.compose.animation.core.tween<Color>(180))
    val bg by androidx.compose.animation.animateColorAsState(if (selected) ui.selTint else Color.Transparent, spec, label = "cellbg")
    val edge by androidx.compose.animation.animateColorAsState(
        when { selected -> ui.accentInk; isToday -> ui.strong; else -> Color.Transparent }, spec, label = "celledge"
    )
    val press by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, com.alberto.upgrade.ui.motion(com.alberto.upgrade.ui.UpMotion.state()), label = "cellsel")
    Box(
        Modifier.fillMaxSize().alpha(if (inMonth) 1f else 0.45f).clip(shape)
            // La selección abarca la celda entera, así nunca tapa una marca.
            .background(bg).border(2.dp, edge, shape)
            .clickable(onClick = onClick)
    ) {
        // Número arriba y, debajo, una fila fija para los puntos de tareas.
        Column(Modifier.fillMaxSize().padding(top = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                // Día con examen: el número, en rojo, brilla.
                style = if (exam) examGlow(ui) else androidx.compose.ui.text.TextStyle.Default,
                color = when {
                    exam -> ui.danger
                    !inMonth -> ui.muted
                    else -> ui.text
                },
                modifier = Modifier.graphicsLayer { val k = 1f + 0.08f * press; scaleX = k; scaleY = k },
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

/**
 * Halo del número cuando ese día hay examen: rojo, para que destaque como lo urgente que es.
 * El radio va en dp para que se vea igual en cualquier pantalla, y respira despacio (salvo si quitas animaciones).
 */
@Composable
private fun examGlow(ui: com.alberto.upgrade.ui.Ui): androidx.compose.ui.text.TextStyle {
    val reduce = com.alberto.upgrade.ui.LocalReduceMotion.current
    val k = if (reduce) 1f else {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow")
        val v by t.animateFloat(
            0.75f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "glowk"
        )
        v
    }
    val blur = with(androidx.compose.ui.platform.LocalDensity.current) { ((if (ui.dark) 10.dp else 7.dp) * k).toPx() }
    return androidx.compose.ui.text.TextStyle(
        shadow = androidx.compose.ui.graphics.Shadow(
            color = ui.danger.copy(alpha = (if (ui.dark) 0.95f else 0.6f) * k),
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
