package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.nowMin
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.LocalUi
import java.time.LocalDate

/** Horario semanal completo. Todo en gris; solo la clase de ahora resaltada. */
@Composable
fun ScheduleScreen(data: AppData, actions: AppActions, onClose: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now().dayOfWeek.value
    val minute = nowMin()
    val days = listOf("L", "M", "X", "J", "V")

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.x, onClose)
            Text("Horario", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.width(48.dp))
                days.forEachIndexed { i, n ->
                    Text(
                        n, color = if (i + 1 == today) ui.accentInk else ui.muted,
                        fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                    )
                }
            }
            data.periods.forEachIndexed { p, period ->
                if (p > 0 && data.periods[p - 1].end < period.start) {
                    Text(
                        "Recreo ${minStr(data.periods[p - 1].end)}–${minStr(period.start)}",
                        color = ui.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(48.dp)) {
                        Text(minStr(period.start), color = ui.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(minStr(period.end), color = ui.muted, fontSize = 11.sp)
                    }
                    (1..5).forEach { day ->
                        val s = data.timetable.firstOrNull { it.day == day && it.period == p }?.let { data.subject(it.subjectId) }
                        val now = day == today && minute in period.start until period.end
                        Box(
                            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (now) ui.primary else ui.surface)
                                .border(1.dp, if (now) ui.primary else ui.hairline, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                s?.name ?: "—",
                                color = if (now) ui.onPrimary else if (s != null) ui.text else ui.muted,
                                fontSize = 11.sp, fontWeight = if (now) FontWeight.Black else FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            GhostButton("Editar horario", { actions.openOverlay("settings") }, Modifier.fillMaxWidth(), height = 44.dp, icon = Ic.pencil)
            Spacer(Modifier.height(24.dp))
        }
    }
}
