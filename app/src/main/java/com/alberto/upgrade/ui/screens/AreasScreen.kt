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
    val areas = areaOrder.mapNotNull { id -> data.area(id) }

    val inArea = data.tasks.filter { it.areaId == areaId }
    val sections = sectionsFor(areaId)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)) {
        item {
            Text("Áreas", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 28.sp, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(areas, key = { it.id }) { a ->
                    val pending = data.tasks.count { it.areaId == a.id && !it.done }
                    SelectChip(
                        if (pending > 0) "${a.name} · $pending" else a.name,
                        areaId == a.id,
                        { areaId = a.id; showDone = false },
                        icon = areaIcon(a.id)
                    )
                }
            }
        }

        sections.forEach { sec ->
            val list = inArea.filter { !it.done && it.kind in sec.kinds }.sortedWith(taskOrder)
            item(key = "h-" + sec.title) {
                SectionTitle(sec.title) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .clickable { actions.openAdd(null, areaId, sec.kinds.first()) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UIcon(Ic.plus, ui.accentInk, size = 18.dp)
                        Text("Añadir", color = ui.accentInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (list.isEmpty()) {
                item(key = "e-" + sec.title) { EmptyNote("Nada pendiente.") }
            } else {
                items(list, key = { "t-" + sec.title + it.id }) { t ->
                    TaskRow(t, data, { actions.award(Repo.toggleTask(t.id), "¡Bien hecho!") }, { actions.openTask(t) }, today)
                }
            }
        }

        val done = inArea.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }
        if (done.isNotEmpty()) {
            item(key = "done-h") {
                Text(
                    (if (showDone) "Ocultar hechas" else "Ver hechas") + " (${done.size})",
                    color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(10.dp))
                        .clickable { showDone = !showDone }.padding(8.dp)
                )
            }
            if (showDone) {
                items(done, key = { "d-" + it.id }) { t ->
                    TaskRow(t, data, { Repo.toggleTask(t.id) }, { actions.openTask(t) }, today)
                }
            }
        }
    }
}
