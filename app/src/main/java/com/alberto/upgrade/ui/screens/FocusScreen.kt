package com.alberto.upgrade.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.alberto.upgrade.block.BlockRules
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.mostUrgent
import com.alberto.upgrade.data.todayKey
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.UIcon
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

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
    val urgent = mostUrgent(data)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Modo Foco", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 28.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Text(
            "Las apps que elijas se bloquean. Al terminar ganas XP.",
            color = ui.muted, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )

        if (!serviceOn) {
            Spacer(Modifier.height(14.dp))
            ChunkyCard(bg = ui.dangerTint, edge = ui.danger.copy(alpha = 0.5f), onClick = { actions.openOverlay("permissions") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UIcon(Ic.shield, ui.danger, size = 20.dp)
                    Text("El bloqueo está apagado", color = ui.danger, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    "El temporizador funciona, pero no bloqueará apps hasta que actives la accesibilidad. Toca para activarlo.",
                    color = ui.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        val total = (data.focus.minutes * 60_000L).coerceAtLeast(1L)
        val left = if (active) data.focus.endAt - now else minutes * 60_000L
        val progress = if (active) 1f - left / total.toFloat() else 0f
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            val track = ui.hairline
            val arc = if (active) ui.primary else ui.lip
            Canvas(Modifier.fillMaxSize()) {
                val w = 22.dp.toPx()
                val s = Size(size.width - w, size.height - w)
                val o = Offset(w / 2, w / 2)
                drawArc(track, -90f, 360f, false, o, s, style = Stroke(w, cap = StrokeCap.Round))
                if (progress > 0f) drawArc(arc, -90f, 360f * progress, false, o, s, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val sec = (left / 1000).coerceAtLeast(0)
                Text(
                    "%02d:%02d".format(sec / 60, sec % 60),
                    color = ui.ink, fontWeight = FontWeight.Black, fontSize = 52.sp
                )
                if (active) {
                    val endT = Instant.ofEpochMilli(data.focus.endAt).atZone(ZoneId.systemDefault()).toLocalTime()
                    Text("Termina a las ${minStr(endT.hour * 60 + endT.minute)}", color = ui.muted, fontSize = 14.sp)
                } else {
                    Text("+${(minutes * 0.6).toInt()} XP al terminar", color = ui.accentInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!active) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(25, 50, 90).forEach { m -> SelectChip("$m min", minutes == m, { minutes = m }) }
            }
            Spacer(Modifier.height(18.dp))
            ChunkyButton("Empezar", { Repo.startFocus(ctx, minutes) }, Modifier.fillMaxWidth(), icon = Ic.timer)
        } else {
            if (urgent != null) {
                ChunkyCard(Modifier.fillMaxWidth()) {
                    Text("Ahora mismo, solo esto:", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(urgent.title, color = ui.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
            GhostButton("Rendirse", { confirmQuit = true }, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(28.dp))
        val b = data.block
        ChunkyCard(Modifier.fillMaxWidth(), onClick = { actions.openOverlay("apps") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIcon(Ic.lock, ui.ink, size = 20.dp)
                Text("Apps bloqueadas", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                UIcon(Ic.chevronRight, ui.muted, size = 20.dp)
            }
            Text(
                "Elige cuáles se bloquean. Teléfono, alarmas y teclado nunca.",
                color = ui.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        ChunkyCard(Modifier.fillMaxWidth(), onClick = { actions.openOverlay("settings") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIcon(Ic.clock, ui.ink, size = 20.dp)
                Text("Franja de estudio automática", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                UIcon(Ic.chevronRight, ui.muted, size = 20.dp)
            }
            val days = listOf("L", "M", "X", "J", "V", "S", "D").filterIndexed { i, _ -> (i + 1) in b.days }.joinToString(" ")
            Text(
                if (b.scheduleEnabled) "$days · ${minStr(b.from)}–${minStr(b.to)}" else "Desactivada",
                color = ui.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Desbloqueo de emergencia: esperar ${b.waitMinutes} min. Toca para cambiarlo.",
                color = ui.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        val mins = data.focusLog[todayKey()] ?: 0
        Text("Hoy llevas $mins min de foco", color = ui.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("¿Seguro?", fontWeight = FontWeight.Black) },
            text = { Text("Si te rindes ahora no ganas la XP de este bloque. Lo que te cuesta ahora es justo lo que te hace falta.") },
            confirmButton = {
                TextButton({ confirmQuit = false }) { Text("Sigo", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton({ confirmQuit = false; Repo.cancelFocus(ctx) }) { Text("Rendirme", color = ui.danger) }
            }
        )
    }
}
