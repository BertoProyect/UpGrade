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
import com.alberto.upgrade.ui.reveal
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
    val info = remember(data) { com.alberto.upgrade.data.streakInfo(data, today) }

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val pendingPerms = remember(refresh) { pendingPermissions(ctx) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.weight(1f))
            // Cerrar el día, a mano y a cualquier hora, junto a la rueda.
            com.alberto.upgrade.ui.IconTap(Ic.moon, { actions.openOverlay("review") })
            com.alberto.upgrade.ui.IconTap(Ic.settings, { actions.openOverlay("settings") })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.reveal(0)) {
            Box(Modifier.size(92.dp, 97.dp)) {
                Box(Modifier.size(92.dp).offset(y = 5.dp).clip(CircleShape).background(ui.primaryLip))
                Box(Modifier.size(92.dp).clip(CircleShape).background(ui.primary), contentAlignment = Alignment.Center) {
                    com.alberto.upgrade.ui.RollingNumber(lv.level) { v -> Text("$v", color = ui.onPrimary, fontWeight = FontWeight.Black, fontSize = 44.sp) }
                }
            }
            Column(Modifier.padding(start = 18.dp).weight(1f)) {
                Text("Alberto", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = (-0.3).sp)
                Text("Nivel ${lv.level} · ${fmtXp(data.totalXp)} XP en total", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Column(Modifier.reveal(1)) {
            Spacer(Modifier.height(18.dp))
            ProgressBar(lv.into / lv.need.toFloat(), ui.primary, Modifier.fillMaxWidth(), height = 16.dp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("${fmtXp(lv.into)} / ${fmtXp(lv.need)} XP", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${fmtXp(lv.need - lv.into)} para el nivel ${lv.level + 1}", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        StreakWeek(data, today, info, Modifier.reveal(2)) { actions.openOverlay("history") }

        Spacer(Modifier.height(18.dp))
        ListCard(Modifier.reveal(3)) {
            ListRow("Historial del día", icon = Ic.list, subtitle = "Lo que hiciste cada día, con filtros", onClick = { actions.openOverlay("day") })
            ListDivider()
            ListRow("Progreso", icon = Ic.chart, subtitle = "XP, foco y tareas por semanas", onClick = { actions.openOverlay("progress") })
            ListDivider()
            ListRow("Áreas", icon = Ic.layers, subtitle = "Bachillerato, Órbita Webs, Vaelor…", onClick = { actions.openOverlay("areas") })
        }
        Spacer(Modifier.height(12.dp))
        ListCard(Modifier.reveal(4)) {
            ListRow(
                "Cómo usas la app", icon = Ic.sparkles,
                subtitle = data.insights?.let { r ->
                    val n = r.items.count { it.status == "trying" }
                    if (n > 0) "Probando $n " + (if (n == 1) "recomendación" else "recomendaciones") else "Análisis de la IA y lo que dicen tus datos"
                } ?: "Análisis de la IA y lo que dicen tus datos",
                onClick = { actions.openOverlay("insights") }
            )
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

/**
 * Racha total: la cifra y la semana (L–D). Día completo: llama naranja con check. Fallado pero perdonado:
 * gris con raya. Hoy, si aún falta: aro discontinuo. Toca para ver el historial (donde vive el récord).
 */
@Composable
private fun StreakWeek(data: AppData, today: LocalDate, info: com.alberto.upgrade.data.StreakInfo, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    val monday = today.with(DayOfWeek.MONDAY)
    val streak = info.days
    val check = remember(data) { com.alberto.upgrade.data.dayCheck(data, today) }
    ChunkyCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UIcon(Ic.flameFill, if (streak > 0) ui.flame else ui.muted, size = 30.dp)
            com.alberto.upgrade.ui.RollingNumber(streak) { v ->
                Text("$v", color = if (v > 0) ui.flame else ui.muted, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-1).sp, modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                if (streak == 1) "día de racha" else "días de racha",
                color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            UIcon(Ic.chevronRight, ui.muted, size = 18.dp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, n ->
                val d = monday.plusDays(i.toLong())
                val met = !d.isAfter(today) && com.alberto.upgrade.data.dayComplete(data, d)
                val isToday = d == today
                val past = d.isBefore(today)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        met -> Box(Modifier.size(36.dp).clip(CircleShape).background(ui.flame), contentAlignment = Alignment.Center) {
                            UIcon(Ic.check, ui.bg, size = 19.dp)
                        }
                        isToday -> {
                            val c = ui.flame
                            Canvas(Modifier.size(36.dp)) {
                                val w = 2.5.dp.toPx()
                                drawArc(
                                    c, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
                                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                                )
                            }
                        }
                        past -> Box(Modifier.size(36.dp).clip(CircleShape).background(ui.hairline), contentAlignment = Alignment.Center) {
                            UIcon(Ic.minus, ui.muted, size = 16.dp)
                        }
                        else -> Box(Modifier.size(36.dp).clip(CircleShape).border(2.dp, ui.hairline, CircleShape))
                    }
                    Text(
                        n, color = if (isToday) ui.ink else ui.muted, fontWeight = if (isToday) FontWeight.Black else FontWeight.ExtraBold,
                        fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        val missing = listOfNotNull(
            if (!check.xp) "meta de XP" else null,
            if (!check.routines) "rutinas" else null,
            if (!check.habits) "hábitos" else null,
            if (!check.focus) "un foco" else null
        )
        Text(
            when {
                missing.isEmpty() -> "Hoy está completo. La racha suma."
                info.atRisk -> "Ayer fallaste: hoy no puedes. Falta " + missing.joinToString(", ") + "."
                else -> "Para que hoy cuente falta " + missing.joinToString(", ") + ". Un fallo suelto se perdona; dos seguidos, no."
            },
            color = if (info.atRisk && missing.isNotEmpty()) ui.danger else ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 14.dp)
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
