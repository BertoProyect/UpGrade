package com.alberto.upgrade.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.levelOf
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.reduceMotion
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface Celebration {
    data class Goal(val streak: Int) : Celebration
    data class Level(val level: Int) : Celebration
}

private data class Particle(val x: Float, val delay: Float, val speed: Float, val color: Color, val rot: Float, val w: Float)

@Composable
fun CelebrationOverlay(c: Celebration, data: AppData, onDone: () -> Unit) {
    val ui = LocalUi.current
    val reduce = reduceMotion()
    val scale = remember { Animatable(if (reduce) 1f else 0.3f) }
    val fall = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch { fall.animateTo(1f, tween(2800, easing = LinearEasing)) }
        }
    }
    val colors = listOf(ui.primary, ui.flame, ui.ink, ui.primary, ui.muted)
    val particles = remember {
        val r = Random(42)
        List(60) { Particle(r.nextFloat(), r.nextFloat() * 0.4f, 0.6f + r.nextFloat() * 0.8f, colors[r.nextInt(colors.size)], r.nextFloat() * 360f, 6f + r.nextFloat() * 8f) }
    }
    BackHandler { onDone() }
    Box(
        Modifier.fillMaxSize().background(ui.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        if (!reduce) {
            Canvas(Modifier.fillMaxSize()) {
                val t = fall.value
                particles.forEach { p ->
                    val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    if (local > 0f && local < 1f) {
                        val y = -40f + local * p.speed * size.height * 1.2f
                        val x = p.x * size.width + kotlin.math.sin((local * 6f + p.rot)) * 30f
                        rotate(p.rot + local * 540f, Offset(x, y)) {
                            drawRect(p.color, topLeft = Offset(x, y), size = Size(p.w.dp.toPx(), (p.w * 0.5f).dp.toPx()))
                        }
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            val (icon, title, sub) = when (c) {
                is Celebration.Goal -> Triple(
                    Ic.flameFill,
                    if (c.streak <= 1) "¡Racha iniciada!" else "¡${c.streak} días de racha!",
                    "Meta diaria cumplida. Esto es lo que se siente cumplir contigo mismo."
                )
                is Celebration.Level -> Triple(
                    Ic.trophy,
                    "¡Nivel ${c.level}!",
                    "Siguiente nivel en ${levelOf(data.totalXp).need - levelOf(data.totalXp).into} XP."
                )
            }
            UIcon(icon, if (c is Celebration.Goal) ui.flame else ui.accentInk, size = 140.dp, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
            Spacer(Modifier.height(16.dp))
            Text(title, color = if (c is Celebration.Goal) ui.flame else ui.accentInk, fontWeight = FontWeight.Black, fontSize = 34.sp, textAlign = TextAlign.Center)
            Text(sub, color = ui.text, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.weight(1f))
            ChunkyButton("Continuar", onDone, Modifier.fillMaxWidth())
        }
    }
}
