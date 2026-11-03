package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import com.alberto.upgrade.data.Kind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.sectionsFor
import com.alberto.upgrade.data.taskOrder
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.EmptyNote
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.TaskRow
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.areaIcon
import java.time.LocalDate

private val areaOrder = listOf("insti", "orbita", "berto", "vaelor")

@Composable
fun AreasScreen(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    var areaId by rememberSaveable { mutableStateOf("insti") }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val areas = areaOrder.filter { data.shows(it) }.mapNotNull { id -> data.area(id) }
    androidx.compose.runtime.LaunchedEffect(areas.map { it.id }) {
        if (areas.isNotEmpty() && areas.none { it.id == areaId }) areaId = areas.first().id
    }

    val inArea = data.tasks.filter { it.areaId == areaId }
    val sections = sectionsFor(areaId)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)) {
        item {
            Text("Áreas", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(areas, key = { it.id }) { a ->
                    val pending = data.tasks.count { it.areaId == a.id && !it.done }
                    SelectChip(
                        a.name,
                        areaId == a.id,
                        { areaId = a.id; showDone = false },
                        badge = if (pending > 0) "$pending" else null
                    )
                }
            }
        }

        sections.forEach { sec ->
            val list = inArea.filter { !it.done && it.kind in sec.kinds }.sortedWith(taskOrder)
            item(key = "h-" + sec.title) {
                SectionTitle(sec.title, Modifier.padding(top = 0.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .clickable { actions.openAdd(null, areaId, sec.kinds.first()) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UIcon(Ic.plus, ui.accentInk, size = 16.dp)
                        Text("Añadir", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (list.isEmpty()) {
                item(key = "e-" + sec.title) { EmptyNote("Nada pendiente.") }
            } else {
                items(list, key = { "t-" + sec.title + it.id }) { t ->
                    if (t.kind == Kind.EXAMEN) ExamLine(data, t, today) { actions.openTask(t) }
                    else TaskRow(t, data, { actions.award(Repo.toggleTask(t.id), "¡Bien hecho!") }, { actions.openTask(t) }, today, showKind = sec.kinds.size > 1 && t.kind !in setOf(Kind.DEBERES, Kind.TAREA))
                }
            }
        }

        val done = inArea.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }
        if (done.isNotEmpty()) {
            item(key = "done-h") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp))
                        .clickable { showDone = !showDone }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(Ic.chevronDown, ui.muted, size = 18.dp, modifier = Modifier.rotate(if (showDone) 180f else 0f))
                    Text(
                        ((if (showDone) "Ocultar hechas" else "Ver hechas") + " (${done.size})").uppercase(),
                        color = ui.muted, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (showDone) {
                items(done, key = { "d-" + it.id }) { t ->
                    TaskRow(t, data, { Repo.toggleTask(t.id) }, { actions.openTask(t) }, today)
                }
            }
        }
    }
}

/** Examen en Áreas: cuenta atrás en una cajita (roja si faltan ≤ 2 días). */
@Composable
private fun ExamLine(data: com.alberto.upgrade.data.AppData, t: com.alberto.upgrade.data.TaskItem, today: java.time.LocalDate, onOpen: () -> Unit) {
    val ui = LocalUi.current
    val due = com.alberto.upgrade.data.parseDate(t.due)
    val days = due?.let { com.alberto.upgrade.data.daysUntil(it, today) }
    val urgent = days != null && days <= 2
    val subject = data.subject(t.subjectId)
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.size(48.dp).clip(shape).background(if (urgent) ui.dangerTint else ui.surface)
                .border(2.dp, if (urgent) ui.danger else ui.hairline, shape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val c = if (urgent) ui.danger else ui.ink
            Text(if (days == null) "—" else if (days <= 0L) "HOY" else "$days", color = c, fontWeight = FontWeight.Black, fontSize = if (days != null && days <= 0L) 13.sp else 18.sp, lineHeight = 18.sp)
            if (days != null && days > 0) Text(if (days == 1L) "DÍA" else "DÍAS", color = if (urgent) ui.danger else ui.muted, fontWeight = FontWeight.Black, fontSize = 9.sp, lineHeight = 10.sp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                listOfNotNull(subject?.name, t.title.takeIf { subject == null || !it.equals(subject.name, true) }).joinToString(" · "),
                color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                com.alberto.upgrade.data.dueLabel(due, today) + (t.time?.let { " · ${com.alberto.upgrade.data.minStr(it)}" } ?: ""),
                color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
            )
        }
    }
}
