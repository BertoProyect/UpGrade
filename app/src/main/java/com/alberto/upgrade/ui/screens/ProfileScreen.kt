package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.cap
import com.alberto.upgrade.data.dayShort
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.levelOf
import com.alberto.upgrade.data.streakOf
import com.alberto.upgrade.data.xpOn
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.UIcon
import java.time.LocalDate


@Composable
fun ProfileScreen(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val lv = levelOf(data.totalXp)
    val streak = streakOf(data, today)
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekXp = week.sumOf { xpOn(data, it) }
    val weekFocus = week.sumOf { data.focusLog[it.key()] ?: 0 }
    val weekDone = data.tasks.count { t -> t.done && t.xpDate != null && week.any { it.key() == t.xpDate } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(ui.primary),
                contentAlignment = Alignment.Center
            ) { Text("${lv.level}", color = ui.onPrimary, fontWeight = FontWeight.Black, fontSize = 30.sp) }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Alberto", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Text("Nivel ${lv.level} · ${data.totalXp} XP en total", color = ui.muted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        ProgressBar(lv.into / lv.need.toFloat(), ui.primary, Modifier.fillMaxWidth(), height = 16.dp)
        Text(
            "Te faltan ${lv.need - lv.into} XP para el nivel ${lv.level + 1}",
            color = ui.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
        )

        SectionTitle("Estadísticas")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Ic.flameFill, ui.flame, "$streak", "días de racha", Modifier.weight(1f))
            StatTile(Ic.crown, ui.ink, "${data.bestStreak}", "mejor racha", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Ic.circleCheck, ui.ink, "$weekDone", "hechas en 7 días", Modifier.weight(1f))
            StatTile(Ic.timer, ui.ink, "$weekFocus", "min de foco (7 d)", Modifier.weight(1f))
        }

        SectionTitle("Últimos 7 días") {
            Text("$weekXp XP", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        ChunkyCard {
            val values = week.map { xpOn(data, it) }
            val max = (values.maxOrNull() ?: 0).coerceAtLeast(data.dailyGoal).toFloat()
            val bar = ui.lip
            val good = ui.primary
            val goalLine = ui.lip
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val n = values.size
                val gap = 10.dp.toPx()
                val bw = (size.width - gap * (n - 1)) / n
                val gy = size.height - size.height * (data.dailyGoal / max)
                drawLine(goalLine, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                values.forEachIndexed { i, v ->
                    val h = (size.height * (v / max)).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(
                        if (v >= data.dailyGoal) good else bar,
                        topLeft = Offset(i * (bw + gap), size.height - h),
                        size = Size(bw, h),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                week.forEach { d ->
                    Text(dayShort(d).cap().take(2), color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                week.forEach { d ->
                    val r = data.reviews[d.key()]
                    Text(r?.let { "${it.rating}★" } ?: "·",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (r != null) ui.text else ui.muted,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Text("Barra de color = meta cumplida · ★ = lo orgulloso que estabas (1–5)", color = ui.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(20.dp))
        ChunkyButton("Cerrar el día", { actions.openOverlay("review") }, Modifier.fillMaxWidth(), icon = Ic.moon)
        Spacer(Modifier.height(10.dp))
        GhostButton("Horario de clases", { actions.openOverlay("horario") }, Modifier.fillMaxWidth(), icon = Ic.calendarClock)
        Spacer(Modifier.height(10.dp))
        GhostButton("Ajustes", { actions.openOverlay("settings") }, Modifier.fillMaxWidth(), icon = Ic.settings)
        Spacer(Modifier.height(10.dp))
        GhostButton("Permisos", { actions.openOverlay("permissions") }, Modifier.fillMaxWidth(), icon = Ic.shield)
    }
}

@Composable
private fun StatTile(icon: Int, tint: androidx.compose.ui.graphics.Color, value: String, label: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    ChunkyCard(modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UIcon(icon, tint, size = 22.dp)
            Text(value, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Text(label, color = ui.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
