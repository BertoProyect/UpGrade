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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.alberto.upgrade.ui.UpMotion
import com.alberto.upgrade.ui.motion
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
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var confirmQuit by remember { mutableStateOf(false) }

    if (active) {
        FocusRunning(data, now, { t -> actions.award(Repo.toggleTask(t.id), "¡Bien hecho!") }) { confirmQuit = true }
    } else {
        FocusIdle(data, now, minutes, { minutes = it }, serviceOn, actions)
    }

    if (confirmQuit) {
        val done = ((now - data.focus.startedAt) / 60_000L).toInt().coerceIn(0, data.focus.minutes)
        val blocks = done / 30
        val xp = focusXp(data, blocks * 30)
        val inBlock = done % 30
        Dialog(onDismissRequest = { confirmQuit = false }) {
            ChunkyCard(Modifier.fillMaxWidth(), padding = 0.dp, radius = 24.dp) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
                    SerifText("¿Seguro que lo dejas?", fontSize = 28.sp)
                    Text(
                        when {
                            blocks == 0 -> "Llevas $done min. Hacen falta 30 para el primer bloque."
                            inBlock == 0 -> "Llevas $done min: ${blocks} " + (if (blocks == 1) "bloque completo." else "bloques completos.")
                            else -> "Llevas $done min: ${blocks} " + (if (blocks == 1) "bloque completo" else "bloques completos") + ". Los $inBlock min del bloque en curso no cuentan."
                        },
                        color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Te llevas", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        UIcon(Ic.zapFill, if (xp > 0) ui.accentInk else ui.muted, size = 16.dp, modifier = Modifier.padding(start = 8.dp))
                        Text("$xp XP", color = if (xp > 0) ui.accentInk else ui.muted, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(start = 3.dp))
                    }
                    Row(Modifier.padding(top = 8.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        UIcon(Ic.shieldCheck, if (blocks > 0) ui.flame else ui.muted, size = 16.dp)
                        Text(
                            if (blocks > 0) "Cuenta para la racha de foco (pasaste de 30 min)" else "No cuenta para la racha de foco",
                            color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    ChunkyButton("Seguir con el foco", { confirmQuit = false }, Modifier.fillMaxWidth())
                    TextAction(
                        if (xp > 0) "Rendirme · +$xp XP" else "Rendirme",
                        { confirmQuit = false; actions.award(Repo.quitFocus(ctx), "Foco guardado") },
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
        val presets = listOf(30, 60, 90, 120)
        var custom by rememberSaveable { mutableStateOf(minutes !in presets) }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { m ->
                SelectChip("$m min", !custom && minutes == m, { custom = false; setMinutes(m) }, Modifier.weight(1f), center = true)
            }
            SelectChip("Otro", custom, { custom = true }, Modifier.weight(1.15f), icon = Ic.sliders, center = true)
        }
        androidx.compose.animation.AnimatedVisibility(
            custom,
            enter = androidx.compose.animation.expandVertically(motion(UpMotion.screen())) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(motion(UpMotion.screen())) + androidx.compose.animation.fadeOut()
        ) {
            ChunkyCard(Modifier.fillMaxWidth().padding(top = 14.dp), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Personalizar", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    com.alberto.upgrade.ui.IconTap(Ic.x, { custom = false; if (minutes !in presets) setMinutes(60) }, size = 32.dp)
                }
                Text("De 5 a 240 min, de 5 en 5.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                MinuteWheel(minutes, setMinutes)
            }
        }
        val xp = focusXp(data, minutes)
        Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
            BlockRing(250.dp, minutes, 0f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:00".format(minutes), color = ui.ink, fontWeight = FontWeight.Black, fontSize = if (minutes >= 100) 52.sp else 58.sp, letterSpacing = (-2).sp, lineHeight = 60.sp)
                val nb = minutes / 30
                Text(
                    when { nb == 0 -> "menos de un bloque"; nb == 1 -> "1 bloque de 30 min"; else -> "$nb bloques de 30 min" },
                    color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (xp > 0) {
                UIcon(Ic.zapFill, ui.accentInk, size = 16.dp)
                Text("+$xp XP", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
                Text("  ${data.xp.focus30} XP por cada 30 min completos", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            } else {
                UIcon(Ic.info, ui.muted, size = 16.dp)
                Text("No da XP · mínimo 30 min", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (xp > 0) ChunkyButton("Empezar", { Repo.startFocus(ctx, minutes) }, Modifier.fillMaxWidth())
        else com.alberto.upgrade.ui.GhostButton("Empezar $minutes min", { Repo.startFocus(ctx, minutes) }, Modifier.fillMaxWidth())

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
    val urgent = data.focus.taskId?.let { id -> data.tasks.firstOrNull { it.id == id && !it.done } } ?: mostUrgent(data)
    val total = (data.focus.minutes * 60_000L).coerceAtLeast(1L)
    val left = (data.focus.endAt - now).coerceAtLeast(0)
    val sec = left / 1000
    val doneMin = ((now - data.focus.startedAt) / 60_000L).toInt().coerceIn(0, data.focus.minutes)
    val nBlocks = data.focus.minutes / 30
    val current = (doneMin / 30 + 1).coerceAtMost(nBlocks.coerceAtLeast(1))
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Eyebrow(
                "Modo foco · ${data.focus.minutes} min" + if (nBlocks > 1) " · bloque $current de $nBlocks" else "",
                modifier = Modifier.padding(top = 40.dp)
            )
            Box(Modifier.padding(top = 24.dp), contentAlignment = Alignment.Center) {
                BlockRing(270.dp, data.focus.minutes, 1f - left / total.toFloat())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:%02d".format(sec / 60, sec % 60), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 64.sp, letterSpacing = (-2).sp, lineHeight = 64.sp)
                    Text("quedan", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (nBlocks >= 1) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..nBlocks).forEach { b ->
                        val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        val finished = doneMin >= b * 30
                        val running = b == current && !finished
                        val bg by androidx.compose.animation.animateColorAsState(
                            if (finished) ui.selTint else ui.card, motion(androidx.compose.animation.core.tween(220)), label = "blk"
                        )
                        Row(
                            Modifier.weight(1f).clip(shape).background(bg)
                                .border(2.dp, if (finished) ui.accentInk else ui.hairline, shape)
                                .padding(vertical = 9.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                finished -> {
                                    UIcon(Ic.check, ui.accentInk, size = 14.dp)
                                    Text("+${data.xp.focus30} XP", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                                }
                                running -> Text("${doneMin - (b - 1) * 30} de 30 min", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
                                else -> Text("Bloque $b", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
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

/** Anillo dividido en bloques de 30 min (con un pequeño hueco entre ellos); avanza continuo. */
@Composable
private fun BlockRing(size: Dp, minutes: Int, progress: Float) {
    val ui = LocalUi.current
    val track = ui.hairline
    val arc = ui.primary
    val n = (minutes / 30).coerceAtLeast(1)
    Canvas(Modifier.size(size)) {
        val w = 22.dp.toPx()
        val s = Size(this.size.width - w, this.size.height - w)
        val o = Offset(w / 2, w / 2)
        if (n == 1) {
            drawArc(track, -90f, 360f, false, o, s, style = Stroke(w, cap = StrokeCap.Round))
            if (progress > 0f) drawArc(arc, -90f, 360f * progress.coerceAtMost(1f), false, o, s, style = Stroke(w, cap = StrokeCap.Round))
        } else {
            // Hueco angular que compensa los extremos redondeados.
            val r = s.width / 2
            val gapDeg = Math.toDegrees(((w * 1.1f) / r).toDouble()).toFloat()
            val seg = 360f / n
            val done = 360f * progress.coerceIn(0f, 1f)
            for (i in 0 until n) {
                val start = -90f + i * seg + gapDeg / 2
                val sweep = seg - gapDeg
                drawArc(track, start, sweep, false, o, s, style = Stroke(w, cap = StrokeCap.Round))
                val fill = (done - i * seg).coerceIn(0f, seg) / seg * sweep
                if (fill > 0f) drawArc(arc, start, fill, false, o, s, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
    }
}

/** Rueda de minutos (5–240, de 5 en 5) con ajuste al centro. */
@Composable
private fun MinuteWheel(value: Int, onChange: (Int) -> Unit) {
    val ui = LocalUi.current
    val values = remember { (5..240 step 5).toList() }
    val itemH = 44.dp
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(value).coerceAtLeast(0))
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state)
    val center by remember {
        androidx.compose.runtime.derivedStateOf {
            val px = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > px / 2) 1 else 0
        }
    }
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val v = values.getOrNull(center) ?: return@LaunchedEffect
            if (v != value) onChange(v)
        }
    }
    Box(Modifier.fillMaxWidth().height(itemH * 5).padding(top = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(itemH)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(ui.selTint)
                .border(2.dp, ui.accentInk, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        )
        androidx.compose.foundation.lazy.LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = itemH * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(values.size) { i ->
                val sel = i == center
                Row(Modifier.height(itemH), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${values[i]}", color = if (sel) ui.accentInk else ui.muted,
                        fontWeight = FontWeight.Black, fontSize = if (sel) 24.sp else 18.sp
                    )
                    if (sel) Text(" min", color = ui.accentInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
    }
}
