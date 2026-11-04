package com.alberto.upgrade.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.goalFor
import com.alberto.upgrade.data.xpOn
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalReduceMotion
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.UIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Una racha que acaba de subir. [kind]: "xp", "focus" o "routine". */
data class StreakUp(val key: String, val kind: String, val name: String, val icon: Int, val value: Int, val xp: Int = 0, val record: Boolean = false)

sealed interface Celebration {
    data class Level(val level: Int) : Celebration
    data class Streaks(val items: List<StreakUp>) : Celebration {
        val milestone: Boolean get() = items.any { it.value == 7 || it.value == 30 }
        /** Solo rutinas y sin hito: tarjeta compacta desde abajo. */
        val compact: Boolean get() = items.all { it.kind == "routine" } && !milestone
    }
}

private data class Particle(val x: Float, val delay: Float, val speed: Float, val color: Color, val rot: Float, val w: Float)

@Composable
fun CelebrationOverlay(c: Celebration, data: AppData, onDone: () -> Unit) {
    if (c is Celebration.Streaks && c.compact) CompactStreaks(c, onDone) else FullCelebration(c, data, onDone)
}

/** Pantalla completa: confeti (lima, su labio y blanco roto, 1,4 s) y un toque lo salta todo. */
@Composable
private fun FullCelebration(c: Celebration, data: AppData, onDone: () -> Unit) {
    val ui = LocalUi.current
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduce) 1f else 0.3f) }
    val fall = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var rolled by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            launch { scale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
            launch { fall.animateTo(1f, tween(1400, easing = LinearOutSlowInEasing)) }
            delay(350)
        }
        rolled = true
    }
    val colors = listOf(ui.primary, ui.primaryLip, Color(0xFFFAF9F5), ui.primary)
    val particles = remember {
        val r = Random(42)
        List(36) { Particle(r.nextFloat(), r.nextFloat() * 0.3f, 0.7f + r.nextFloat() * 0.7f, colors[r.nextInt(colors.size)], r.nextFloat() * 360f, 6f + r.nextFloat() * 8f) }
    }
    BackHandler { onDone() }
    Box(
        Modifier.fillMaxSize().background(ui.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (fall.isRunning || scale.isRunning || !rolled) {
                    scope.launch { fall.snapTo(1f); scale.snapTo(1f); rolled = true }
                }
            }
    ) {
        if (!reduce) {
            Canvas(Modifier.fillMaxSize()) {
                val t = fall.value
                particles.forEach { p ->
                    val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    if (local > 0f && local < 1f) {
                        val y = -40f + local * p.speed * size.height * 1.15f
                        val x = p.x * size.width + kotlin.math.sin(local * 6f + p.rot) * 30f
                        rotate(p.rot + local * 540f, Offset(x, y)) {
                            drawRect(p.color, topLeft = Offset(x, y), size = Size(p.w.dp.toPx(), (p.w * 0.5f).dp.toPx()))
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            val grow = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            when (c) {
                is Celebration.Level -> LevelBody(c, data, grow, rolled)
                is Celebration.Streaks -> if (c.items.size == 1) SingleStreak(c.items.first(), data, grow, rolled) else ManyStreaks(c, grow, rolled)
            }
            Spacer(Modifier.weight(1f))
            ChunkyButton("Continuar", onDone, Modifier.fillMaxWidth())
            Text("Toca en cualquier sitio para saltar", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Subir de nivel: el número en grande, «Nivel» encima y «Subes de nivel» debajo. */
@Composable
private fun LevelBody(c: Celebration.Level, data: AppData, grow: Modifier, rolled: Boolean) {
    val ui = LocalUi.current
    Text("NIVEL", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp)
    Box(grow, contentAlignment = Alignment.Center) {
        RollingNumber(if (rolled) c.level else c.level - 1) { v ->
            Box {
                Text("$v", color = ui.primaryLip, fontWeight = FontWeight.Black, fontSize = 168.sp, lineHeight = 168.sp, letterSpacing = (-6).sp, modifier = Modifier.offset(y = 8.dp))
                Text("$v", color = ui.primary, fontWeight = FontWeight.Black, fontSize = 168.sp, lineHeight = 168.sp, letterSpacing = (-6).sp)
            }
        }
    }
    Text("SUBES DE NIVEL", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 18.dp))
    SerifText(
        "%,d XP sumados a base de terminar cosas.".format(java.util.Locale("es", "ES"), data.totalXp),
        color = ui.muted, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
    )
}

/** Una racha: el icono entra con spring y luego «arde» (oscila suave); el número rueda de N−1 a N. */
@Composable
private fun SingleStreak(s: StreakUp, data: AppData, grow: Modifier, rolled: Boolean) {
    val ui = LocalUi.current
    LivingIcon(s.icon, ui.flame, 150, grow, flicker = s.kind == "xp")
    RollingNumber(if (rolled) s.value else (s.value - 1).coerceAtLeast(0)) { v ->
        Text("$v", color = ui.flame, fontWeight = FontWeight.Black, fontSize = 80.sp, lineHeight = 80.sp, letterSpacing = (-3).sp, modifier = Modifier.padding(top = 4.dp))
    }
    val title = when (s.kind) {
        "xp" -> if (s.value <= 1) "¡Racha iniciada!" else "¡${s.value} días de racha!"
        "focus" -> if (s.value == 1) "1 día de racha de foco" else "${s.value} días de racha de foco"
        else -> s.name + " · " + if (s.value == 1) "1 día" else "${s.value} días"
    }
    Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
    val phrase = when {
        s.value >= 30 && s.value % 30 == 0 -> "Un mes entero. Esto ya no es fuerza de voluntad: es costumbre."
        s.value == 7 -> "Una semana seguida. Sigue así."
        s.kind == "xp" -> "Meta de hoy cumplida: ${xpOn(data, java.time.LocalDate.now())} de ${goalFor(data)} XP."
        s.kind == "focus" -> "Hoy has protegido tu atención."
        s.xp > 0 -> "Rutina cumplida · +${s.xp} XP"
        else -> "Rutina cumplida."
    }
    SerifText(phrase, color = ui.muted, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
    if (s.record && (s.value == 7 || s.value >= 30)) RecordBadge(s.value)
}

/** Varias rachas a la vez: una sola pantalla con todas en fila. */
@Composable
private fun ManyStreaks(c: Celebration.Streaks, grow: Modifier, rolled: Boolean) {
    val ui = LocalUi.current
    Text("¡Rachas arriba!", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 30.sp, textAlign = TextAlign.Center)
    Column(grow.padding(top = 24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        c.items.forEach { s ->
            ChunkyCard(Modifier.fillMaxWidth(), padding = 0.dp, radius = 18.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LivingIcon(s.icon, ui.flame, 34, Modifier, flicker = s.kind == "xp")
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            when (s.kind) { "xp" -> "Racha de XP"; "focus" -> "Racha de foco"; else -> s.name },
                            color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (s.xp > 0) Text("+${s.xp} XP", color = ui.accentInk, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                    RollingNumber(if (rolled) s.value else (s.value - 1).coerceAtLeast(0)) { v ->
                        Text("$v", color = ui.flame, fontWeight = FontWeight.Black, fontSize = 30.sp)
                    }
                    Text(" días", color = ui.muted, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
    c.items.firstOrNull { it.record && (it.value == 7 || it.value >= 30) }?.let { RecordBadge(it.value) }
}

/** Rachas de rutinas sin hito: tarjeta que entra desde abajo y se va sola a los 2,5 s (o al tocarla). */
@Composable
private fun CompactStreaks(c: Celebration.Streaks, onDone: () -> Unit) {
    val ui = LocalUi.current
    var shown by remember { mutableStateOf(false) }
    var rolled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        delay(300); rolled = true
        delay(2200); shown = false
        delay(260); onDone()
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            shown,
            enter = slideInVertically(spring(0.8f, 400f)) { it } + fadeIn(tween(150)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(150))
        ) {
            ChunkyCard(Modifier.fillMaxWidth(), edge = ui.flame, lip = ui.flame.copy(alpha = 0.5f), onClick = onDone, padding = 0.dp, radius = 18.dp) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    c.items.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
                                UIcon(s.icon, ui.ink, size = 20.dp)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(s.name, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (s.xp > 0) "Rutina cumplida · +${s.xp} XP" else "Rutina cumplida", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            UIcon(Ic.shieldCheck, ui.flame, size = 18.dp)
                            RollingNumber(if (rolled) s.value else (s.value - 1).coerceAtLeast(0)) { v ->
                                Text("$v", color = ui.flame, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                            Text(" días", color = ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** El fuego de la racha, vivo: respira y se inclina un poco en bucle. */
@Composable
private fun LivingIcon(icon: Int, color: Color, size: Int, modifier: Modifier, flicker: Boolean) {
    val reduce = LocalReduceMotion.current
    if (reduce) {
        UIcon(icon, color, size = size.dp, modifier = modifier)
        return
    }
    val t = rememberInfiniteTransition(label = "flame")
    val s by t.animateFloat(1f, if (flicker) 1.07f else 1.03f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fs")
    val r by t.animateFloat(if (flicker) -3f else 0f, if (flicker) 3f else 0f, infiniteRepeatable(tween(780, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fr")
    UIcon(
        icon, color, size = size.dp,
        modifier = modifier.graphicsLayer {
            scaleX = s; scaleY = s; rotationZ = r
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        }
    )
}

/** Número que rueda hacia arriba al cambiar. */
@Composable
private fun RollingNumber(value: Int, content: @Composable (Int) -> Unit) {
    AnimatedContent(
        value,
        transitionSpec = {
            (slideInVertically(spring(0.8f, 800f)) { it } + fadeIn(tween(150))) togetherWith
                (slideOutVertically(spring(0.8f, 800f)) { -it } + fadeOut(tween(120)))
        },
        label = "roll"
    ) { v -> content(v) }
}

@Composable
private fun RecordBadge(days: Int) {
    val ui = LocalUi.current
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        UIcon(Ic.trophy, ui.accentInk, size = 18.dp)
        Text("NUEVO RÉCORD · $days DÍAS", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
