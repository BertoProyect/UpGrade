package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.cap
import com.alberto.upgrade.data.dayName
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.streakOf
import com.alberto.upgrade.data.xpOn
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.UIcon
import java.time.LocalDate

private val letters = listOf("L", "M", "X", "J", "V", "S", "D")

@Composable
fun ProgressScreen(data: AppData, onClose: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekFocus = week.sumOf { data.focusLog[it.key()] ?: 0 }
    val weekDone = data.tasks.count { t -> t.done && t.xpDate != null && week.any { it.key() == t.xpDate } }
    val streak = streakOf(data, today)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Progreso", onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 32.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Ic.flameFill, ui.flame, "$streak", "Racha", Modifier.weight(1f))
                StatTile(Ic.crown, ui.muted, "${maxOf(data.bestStreak, streak)}", "Mejor racha", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Ic.circleCheck, ui.muted, "$weekDone", "Hechas en 7 días", Modifier.weight(1f))
                StatTile(Ic.target, ui.muted, "$weekFocus", "Min de foco", Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            ChunkyCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("XP · últimos 7 días", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text("meta ${data.dailyGoal}", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                val values = week.map { xpOn(data, it) }
                val max = ((values.maxOrNull() ?: 0).coerceAtLeast(data.dailyGoal) * 1.1f)
                val lime = ui.primary
                val limeLip = ui.primaryLip
                val off = ui.strong
                val goalC = ui.muted.copy(alpha = 0.6f)
                val todayEdge = ui.accentInk
                Canvas(Modifier.fillMaxWidth().height(170.dp).padding(top = 14.dp)) {
                    val n = values.size
                    val gap = 10.dp.toPx()
                    val bw = (size.width - gap * (n - 1)) / n
                    val r = 8.dp.toPx()
                    values.forEachIndexed { i, v ->
                        val h = (size.height * (v / max)).coerceAtLeast(10.dp.toPx())
                        val x = i * (bw + gap)
                        val top = size.height - h
                        val met = v >= data.dailyGoal
                        val isToday = i == n - 1
                        if (met) {
                            drawRoundRect(lime, Offset(x, top), Size(bw, h), CornerRadius(r))
                            clipRect(x, size.height - 4.dp.toPx(), x + bw, size.height) {
                                drawRoundRect(limeLip, Offset(x, top), Size(bw, h), CornerRadius(r))
                            }
                        } else {
                            drawRoundRect(off, Offset(x, top), Size(bw, h), CornerRadius(r))
                            if (isToday) {
                                val w = 2.dp.toPx()
                                drawRoundRect(
                                    todayEdge, Offset(x + w / 2, top + w / 2), Size(bw - w, h - w), CornerRadius(r),
                                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                                )
                            }
                        }
                    }
                    val gy = size.height - size.height * (data.dailyGoal / max)
                    drawLine(goalC, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    week.forEachIndexed { i, d ->
                        val isToday = i == week.size - 1
                        Text(
                            if (isToday) "Hoy" else letters[d.dayOfWeek.value - 1],
                            color = if (isToday) ui.ink else ui.muted, fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Black else FontWeight.ExtraBold,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val rated = week.reversed().mapNotNull { d -> data.reviews[d.key()]?.let { d to it.rating } }
            Spacer(Modifier.height(16.dp))
            ChunkyCard(Modifier.fillMaxWidth()) {
                SerifText("¿Cómo de orgulloso acabaste?", fontSize = 22.sp)
                if (rated.isEmpty()) {
                    Text(
                        "Cierra el día por la noche y aquí verás cómo te sentiste cada día.",
                        color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)
                    )
                }
                rated.forEach { (d, rating) ->
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (d == today) "Hoy" else dayName(d).cap(),
                            color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.width(84.dp)
                        )
                        repeat(5) { k ->
                            UIcon(if (k < rating) Ic.starFill else Ic.star, if (k < rating) ui.accentInk else ui.strong, size = 18.dp, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: Int, tint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    ChunkyCard(modifier, padding = 0.dp) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            UIcon(icon, tint, size = 30.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(value, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 26.sp)
                Text(label, color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
