package com.alberto.upgrade.ui.screens

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.alberto.upgrade.block.BlockRules
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.goalMet
import com.alberto.upgrade.data.levelOf
import com.alberto.upgrade.data.streakOf
import com.alberto.upgrade.data.xpOn
import com.alberto.upgrade.notify.Notifier
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.ListCard
import com.alberto.upgrade.ui.ListDivider
import com.alberto.upgrade.ui.ListRow
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.UIcon
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

private val es = Locale("es", "ES")
fun fmtXp(n: Int): String = "%,d".format(es, n)

@Composable
fun ProfileScreen(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val lv = levelOf(data.totalXp)
    val streak = streakOf(data, today)

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val pendingPerms = remember(refresh) { pendingPermissions(ctx) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 32.dp)) {
        // La rueda vive aquí, en la misma posición que tenía en Hoy.
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.weight(1f))
            // Cerrar el día, a mano y a cualquier hora, junto a la rueda.
            com.alberto.upgrade.ui.IconTap(Ic.moon, { actions.openOverlay("review") })
            com.alberto.upgrade.ui.IconTap(Ic.settings, { actions.openOverlay("settings") })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp, 97.dp)) {
                Box(Modifier.size(92.dp).offset(y = 5.dp).clip(CircleShape).background(ui.primaryLip))
                Box(Modifier.size(92.dp).clip(CircleShape).background(ui.primary), contentAlignment = Alignment.Center) {
                    Text("${lv.level}", color = ui.onPrimary, fontWeight = FontWeight.Black, fontSize = 44.sp)
                }
            }
            Column(Modifier.padding(start = 18.dp).weight(1f)) {
                Text("Alberto", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = (-0.3).sp)
                Text("Nivel ${lv.level} · ${fmtXp(data.totalXp)} XP en total", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        ProgressBar(lv.into / lv.need.toFloat(), ui.primary, Modifier.fillMaxWidth(), height = 16.dp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("${fmtXp(lv.into)} / ${fmtXp(lv.need)} XP", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${fmtXp(lv.need - lv.into)} para el nivel ${lv.level + 1}", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }

        Spacer(Modifier.height(18.dp))
        StreakGrid(data, today, streak)

        Spacer(Modifier.height(18.dp))
        ListCard {
            ListRow("Progreso", icon = Ic.chart, onClick = { actions.openOverlay("progress") })
            ListDivider()
            ListRow("Horario de clases", icon = Ic.grid, onClick = { actions.openOverlay("horario") })
            ListDivider()
            ListRow(
                "Permisos", icon = Ic.shieldCheck,
                value = if (pendingPerms > 0) "$pendingPerms pendiente" + if (pendingPerms > 1) "s" else "" else null,
                valueColor = ui.danger,
                onClick = { actions.openOverlay("permissions") }
            )
        }
    }
}

/** Dos rachas (XP y foco), con la misma altura; la mejor de cada una, en pequeño en su esquina. */
@Composable
private fun StreakGrid(data: AppData, today: LocalDate, streak: Int) {
    val fs = com.alberto.upgrade.data.focusStreakOf(data, today)
    val bestXp = maxOf(data.bestStreak, streak)
    val bestFs = maxOf(data.bestFocusStreak, fs)
    val left = (com.alberto.upgrade.data.goalFor(data, today) - xpOn(data, today)).coerceAtLeast(0)
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StreakTile("Racha de XP", Ic.flameFill, streak, bestXp, ui.flame, if (left == 0) "Meta de hoy cumplida" else "Te faltan $left XP hoy", Modifier.weight(1f).fillMaxHeight())
        StreakTile("Racha de foco", Ic.shieldCheck, fs, bestFs, ui.flame, "Rutina o 30 min de foco", Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun StreakTile(title: String, icon: Int, value: Int, best: Int, color: androidx.compose.ui.graphics.Color, sub: String, modifier: Modifier) {
    val ui = LocalUi.current
    val record = value > 0 && value >= best
    ChunkyCard(modifier, padding = 14.dp, radius = 16.dp, fillHeight = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.alberto.upgrade.ui.Eyebrow(title, fontSize = 11.sp, modifier = Modifier.weight(1f))
            UIcon(Ic.trophy, if (record) color else ui.muted, size = 13.dp)
            Text("$best", color = if (record) color else ui.muted, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            UIcon(icon, if (value > 0) color else ui.muted, size = 22.dp, modifier = Modifier.padding(bottom = 4.dp))
            Text("$value", color = if (value > 0) color else ui.muted, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 30.sp, modifier = Modifier.padding(start = 6.dp))
            Text("días", color = ui.muted, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(sub, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

/** La semana actual (L–D): llama con check si se cumplió la meta, punteada hoy si aún no. */
@Composable
private fun StreakWeek(data: AppData, today: LocalDate, streak: Int) {
    val ui = LocalUi.current
    val monday = today.with(DayOfWeek.MONDAY)
    val xpToday = xpOn(data, today)
    ChunkyCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UIcon(Ic.flameFill, if (streak > 0) ui.flame else ui.muted, size = 26.dp)
            Text(
                if (streak == 1) "1 día de racha" else "$streak días de racha",
                color = ui.ink, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, n ->
                val d = monday.plusDays(i.toLong())
                val met = !d.isAfter(today) && goalMet(data, d)
                val isToday = d == today
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        met -> Box(Modifier.size(38.dp).clip(CircleShape).background(ui.flame), contentAlignment = Alignment.Center) {
                            UIcon(Ic.check, ui.bg, size = 20.dp)
                        }
                        isToday -> {
                            val c = ui.flame
                            Canvas(Modifier.size(38.dp)) {
                                val w = 2.5.dp.toPx()
                                drawArc(
                                    c, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
                                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                                )
                            }
                        }
                        else -> Box(Modifier.size(38.dp).clip(CircleShape).background(ui.hairline))
                    }
                    Text(
                        n, color = if (isToday) ui.ink else ui.muted, fontWeight = if (isToday) FontWeight.Black else FontWeight.ExtraBold,
                        fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        Text(
            if (xpToday >= com.alberto.upgrade.data.goalFor(data, today)) "Meta de hoy cumplida. La racha sigue viva."
            else "Te faltan ${com.alberto.upgrade.data.goalFor(data, today) - xpToday} XP hoy para mantenerla.",
            color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp)
        )
    }
}

private fun pendingPermissions(ctx: Context): Int {
    var n = 0
    if (!Notifier.canPost(ctx)) n++
    if (ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) != true) n++
    if (!BlockRules.isServiceEnabled(ctx)) n++
    return n
}
