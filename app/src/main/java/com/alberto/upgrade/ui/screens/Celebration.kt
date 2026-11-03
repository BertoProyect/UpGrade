package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.alberto.upgrade.data.xpOn
import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.SerifText
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
    val colors = listOf(ui.primary, ui.flame, ui.ink, ui.primary, ui.flame)
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
            val grow = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            when (c) {
                is Celebration.Goal -> {
                    UIcon(Ic.flameFill, ui.flame, size = 150.dp, modifier = grow)
                    Text("${c.streak}", color = ui.flame, fontWeight = FontWeight.Black, fontSize = 72.sp, lineHeight = 72.sp, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        if (c.streak <= 1) "¡Racha iniciada!" else "¡${c.streak} días de racha!",
                        color = ui.ink, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    SerifText(
                        "Meta de hoy cumplida: ${xpOn(data, java.time.LocalDate.now())} de ${data.dailyGoal} XP.",
                        color = ui.muted, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)
                    )
                }
                is Celebration.Level -> {
                    Box(grow.size(150.dp, 158.dp)) {
                        Box(Modifier.size(150.dp).offset(y = 8.dp).clip(CircleShape).background(ui.primaryLip))
                        Box(Modifier.size(150.dp).clip(CircleShape).background(ui.primary), contentAlignment = Alignment.Center) {
                            UIcon(Ic.trophy, ui.onPrimary, size = 76.dp)
                        }
                    }
                    Eyebrow("Subes de nivel", color = ui.accentInk, fontSize = 12.sp, modifier = Modifier.padding(top = 30.dp))
                    Text("Nivel ${c.level}", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 40.sp, modifier = Modifier.padding(top = 8.dp))
                    SerifText(
                        "%,d XP sumados a base de terminar cosas.".format(java.util.Locale("es", "ES"), data.totalXp),
                        color = ui.muted, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            ChunkyButton("Continuar", onDone, Modifier.fillMaxWidth())
        }
    }
}
