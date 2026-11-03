package com.alberto.upgrade.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.alberto.upgrade.block.BlockRules
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.focusXp
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.mostUrgent
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.ListCard
import com.alberto.upgrade.ui.ListDivider
import com.alberto.upgrade.ui.ListRow
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.TaskRow
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.UToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D")

/** "L–V" si los días son seguidos; si no, "L X V". */
fun daysLabel(days: List<Int>): String {
    val s = days.sorted()
    if (s.isEmpty()) return "Ningún día"
    val consecutive = s.size > 2 && s.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (consecutive) "${dayLetters[s.first() - 1]}–${dayLetters[s.last() - 1]}" else s.joinToString(" ") { dayLetters[it - 1] }
}

@Composable
fun FocusScreen(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }

    var serviceOn by remember { mutableStateOf(BlockRules.isServiceEnabled(ctx)) }
    LifecycleResumeEffect(Unit) {
        serviceOn = BlockRules.isServiceEnabled(ctx)
        onPauseOrDispose { }
    }

    val active = data.focus.endAt > now
    var minutes by remember { mutableIntStateOf(25) }
    var confirmQuit by remember { mutableStateOf(false) }

    if (active) {
        FocusRunning(data, now, { t -> actions.award(Repo.toggleTask(t.id), "¡Bien hecho!") }) { confirmQuit = true }
    } else {
        FocusIdle(data, now, minutes, { minutes = it }, serviceOn, actions)
    }

    if (confirmQuit) {
        val done = ((now - data.focus.startedAt) / 60_000L).coerceAtLeast(0)
        Dialog(onDismissRequest = { confirmQuit = false }) {
            ChunkyCard(Modifier.fillMaxWidth(), padding = 0.dp, radius = 24.dp) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
                    SerifText("¿Seguro que lo dejas?", fontSize = 28.sp)
                    Text(
                        "Llevas $done min. Si paras ahora no sumas los ${focusXp(data.focus.minutes)} XP. La racha no se toca.",
                        color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
                    )
                    ChunkyButton("Seguir con el foco", { confirmQuit = false }, Modifier.fillMaxWidth())
                    TextAction(
                        "Rendirme", { confirmQuit = false; Repo.cancelFocus(ctx) },
                        Modifier.fillMaxWidth().padding(top = 4.dp), color = ui.danger
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusIdle(data: AppData, now: Long, minutes: Int, setMinutes: (Int) -> Unit, serviceOn: Boolean, actions: AppActions) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val blockedCount by produceState<Int?>(null, data.block.allowed) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = ctx.packageManager
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { it.activityInfo.packageName }.distinct()
                    .count { !BlockRules.isSystemSafe(ctx, it) && it !in data.block.allowed }
            }.getOrNull()
        }
    }
    val b = data.block

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        Text("Foco", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 50, 90).forEach { m ->
                SelectChip("$m min", minutes == m, { setMinutes(m) }, Modifier.weight(1f), center = true)
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
            Ring(250.dp, 0f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:00".format(minutes), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 60.sp, letterSpacing = (-2).sp, lineHeight = 60.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    UIcon(Ic.zapFill, ui.accentInk, size = 16.dp)
                    Text("+${focusXp(minutes)} XP", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        ChunkyButton("Empezar", { Repo.startFocus(ctx, minutes) }, Modifier.fillMaxWidth())

        if (!serviceOn) {
            Spacer(Modifier.height(18.dp))
            ChunkyCard(bg = ui.dangerTint, edge = ui.danger, lip = ui.dangerLip, onClick = { actions.openOverlay("permissions") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UIcon(Ic.shield, ui.danger, size = 20.dp)
                    Text("El bloqueo está apagado", color = ui.danger, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    "El temporizador funciona, pero no bloqueará apps hasta que actives la accesibilidad. Toca para activarlo.",
                    color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        ListCard {
            ListRow(
                "Apps bloqueadas",
                icon = Ic.shieldBan,
                subtitle = blockedCount?.let { if (it == 1) "1 app durante el foco" else "$it apps durante el foco" } ?: "Elige cuáles",
                onClick = { actions.openOverlay("apps") }
            )
            ListDivider()
            val activeNow = BlockRules.scheduleActive(data, now)
            ListRow(
                "Franja de estudio automática",
                icon = Ic.calendarClock,
                subtitle = "${daysLabel(b.days)} · ${minStr(b.from)}–${minStr(b.to)}" + if (b.scheduleEnabled && activeNow) " · activa ahora" else "",
                onClick = { actions.openOverlay("settings") }
            ) {
                UToggle(b.scheduleEnabled) { v -> Repo.update { it.copy(block = it.block.copy(scheduleEnabled = v)) } }
            }
        }
        val mins = data.focusLog[com.alberto.upgrade.data.todayKey()] ?: 0
        if (mins > 0) {
            Text(
                "Hoy llevas $mins min de foco", color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun FocusRunning(data: AppData, now: Long, onToggle: (com.alberto.upgrade.data.TaskItem) -> Unit, onQuit: () -> Unit) {
    val ui = LocalUi.current
    val urgent = mostUrgent(data)
    val total = (data.focus.minutes * 60_000L).coerceAtLeast(1L)
    val left = (data.focus.endAt - now).coerceAtLeast(0)
    val sec = left / 1000
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Eyebrow("Modo foco · ${data.focus.minutes} min", modifier = Modifier.padding(top = 48.dp))
            Box(Modifier.padding(top = 28.dp), contentAlignment = Alignment.Center) {
                Ring(270.dp, 1f - left / total.toFloat())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:%02d".format(sec / 60, sec % 60), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 64.sp, letterSpacing = (-2).sp, lineHeight = 64.sp)
                    Text("quedan", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (urgent != null) {
                SerifText("Ahora mismo, solo esto:", fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 40.dp))
                ChunkyCard(Modifier.fillMaxWidth().padding(top = 14.dp), padding = 0.dp) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        TaskRow(urgent, data, { onToggle(urgent) }, {}, showXp = false)
                    }
                }
            }
        }
        TextAction("Rendirse", onQuit, Modifier.padding(bottom = 36.dp))
    }
}

@Composable
private fun Ring(size: Dp, progress: Float) {
    val ui = LocalUi.current
    val track = ui.hairline
    val arc = ui.primary
    Canvas(Modifier.size(size)) {
        val w = 22.dp.toPx()
        val s = Size(this.size.width - w, this.size.height - w)
        val o = Offset(w / 2, w / 2)
        drawArc(track, -90f, 360f, false, o, s, style = Stroke(w, cap = StrokeCap.Round))
        if (progress > 0f) drawArc(arc, -90f, 360f * progress.coerceAtMost(1f), false, o, s, style = Stroke(w, cap = StrokeCap.Round))
    }
}
