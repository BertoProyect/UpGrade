package com.alberto.upgrade.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
    var skipped by remember { mutableStateOf(false) }
    // La racha de XP sola lleva su propia secuencia (sin confeti) y el botón entra cuando termina.
    val xpOnly = c is Celebration.Streaks && c.items.size == 1 && c.items.first().kind == "xp"
    var ready by remember { mutableStateOf(!xpOnly || reduce) }
    val btn = remember { Animatable(if (!xpOnly || reduce) 1f else 0f) }
    LaunchedEffect(ready) { if (ready) btn.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
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
                skipped = true
                if (fall.isRunning || scale.isRunning || !rolled) {
                    scope.launch { fall.snapTo(1f); scale.snapTo(1f); rolled = true }
                }
            }
    ) {
        if (!reduce && !xpOnly) {
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
                is Celebration.Streaks -> if (c.items.size == 1) SingleStreak(c.items.first(), data, grow, rolled, skipped) { ready = true } else ManyStreaks(c, grow, rolled)
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().graphicsLayer { alpha = btn.value; translationY = (1f - btn.value) * 12.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ChunkyButton("Continuar", onDone, Modifier.fillMaxWidth())
                Text("Toca en cualquier sitio para saltar", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
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
private fun SingleStreak(s: StreakUp, data: AppData, grow: Modifier, rolled: Boolean, skipped: Boolean = false, onReady: () -> Unit = {}) {
    val ui = LocalUi.current
    // La racha de XP tiene su propia secuencia: la llama gris se derrite, se llena de color, flota, se rompe y se enciende.
    if (s.kind == "xp") {
        XpStreakBody(s, data, skipped, onReady)
        return
    }
    LivingIcon(s.icon, ui.flame, 150, grow, flicker = false)
    RollingNumber(if (rolled) s.value else (s.value - 1).coerceAtLeast(0)) { v ->
        Text("$v", color = ui.flame, fontWeight = FontWeight.Black, fontSize = 80.sp, lineHeight = 80.sp, letterSpacing = (-3).sp, modifier = Modifier.padding(top = 4.dp))
    }
    val title = when (s.kind) {
        "focus" -> if (s.value == 1) "1 día de racha de foco" else "${s.value} días de racha de foco"
        else -> s.name + " · " + if (s.value == 1) "1 día" else "${s.value} días"
    }
    Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
    val phrase = when {
        s.value >= 30 && s.value % 30 == 0 -> "Un mes entero. Esto ya no es fuerza de voluntad: es costumbre."
        s.value == 7 -> "Una semana seguida. Sigue así."
        s.kind == "focus" -> "Hoy has protegido tu atención."
        s.xp > 0 -> "Rutina cumplida · +${s.xp} XP"
        else -> "Rutina cumplida."
    }
    SerifText(phrase, color = ui.muted, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
    if (s.record && (s.value == 7 || s.value >= 30)) RecordBadge(s.value)
}

// ───────────────────────── Racha de XP +1 ─────────────────────────
// Toda la secuencia cuelga de un único reloj en ms (0 → STREAK_END). Las formas salen de StreakShapes
// (generadas con gen/morph.py, las mismas que el diseño) y se transforman punto a punto.

private const val STREAK_END = 3200f
private val StdEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val DropEase = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)

private fun c01(x: Float) = x.coerceIn(0f, 1f)

/** Fotogramas clave: (ms, forma). Entre dos claves la forma se interpola punto a punto con FastOutSlowIn. */
private class ShapeKeys(val keys: List<Pair<Float, FloatArray>>) {
    private val buf = FloatArray(keys.first().second.size)
    fun at(t: Float): FloatArray {
        if (t <= keys.first().first) return keys.first().second
        for (i in 0 until keys.size - 1) {
            val (t0, a) = keys[i]
            val (t1, b) = keys[i + 1]
            if (t <= t1) {
                val f = if (t1 > t0) StdEase.transform((t - t0) / (t1 - t0)) else 1f
                for (j in buf.indices) buf[j] = a[j] + (b[j] - a[j]) * f
                return buf
            }
        }
        return keys.last().second
    }
}

private fun numKeys(keys: List<Pair<Float, Float>>, t: Float): Float {
    if (t <= keys.first().first) return keys.first().second
    for (i in 0 until keys.size - 1) {
        val (t0, a) = keys[i]
        val (t1, b) = keys[i + 1]
        if (t <= t1) return a + (b - a) * (if (t1 > t0) StdEase.transform((t - t0) / (t1 - t0)) else 1f)
    }
    return keys.last().second
}

private val OUTER_KEYS = ShapeKeys(
    listOf(
        0f to StreakShapes.OUT_FLAME, 300f to StreakShapes.OUT_FLAME, 400f to StreakShapes.OUT_CAPSULE, 660f to StreakShapes.OUT_CAPSULE,
        700f to StreakShapes.OUT_DROP, 960f to StreakShapes.OUT_DROP, 985f to StreakShapes.OUT_NECK, 1040f to StreakShapes.OUT_NECK,
        1060f to StreakShapes.OUT_SQUASH, 1110f to StreakShapes.OUT_SQUASH2, 1135f to StreakShapes.OUT_TALL, 1190f to StreakShapes.OUT_FLAME
    )
)
private val INNER_KEYS = ShapeKeys(
    listOf(
        0f to StreakShapes.IN_FLAME, 300f to StreakShapes.IN_FLAME, 400f to StreakShapes.IN_CAPA, 500f to StreakShapes.IN_CAPB,
        580f to StreakShapes.IN_CAPC, 650f to StreakShapes.IN_CAPA, 660f to StreakShapes.IN_CAPA, 700f to StreakShapes.IN_DROP,
        960f to StreakShapes.IN_DROP, 985f to StreakShapes.IN_NECK, 1040f to StreakShapes.IN_NECK, 1060f to StreakShapes.IN_SQUASH,
        1110f to StreakShapes.IN_SQUASH, 1135f to StreakShapes.IN_TALL, 1190f to StreakShapes.IN_FLAME
    )
)
private val LIFT_KEYS = listOf(0f to 0f, 660f to 0f, 800f to -30f, 880f to -33f, 960f to -30f, 985f to 0f)

/** Contorno suave que pasa por los puntos medios (cada punto hace de control). */
private fun smoothPath(p: FloatArray, k: Float, ox: Float, oy: Float, path: androidx.compose.ui.graphics.Path) {
    path.reset()
    val n = p.size / 2
    fun x(i: Int) = ox + p[(i % n) * 2] * k
    fun y(i: Int) = oy + p[(i % n) * 2 + 1] * k
    var sx = (x(n - 1) + x(0)) / 2
    var sy = (y(n - 1) + y(0)) / 2
    path.moveTo(sx, sy)
    for (i in 0 until n) {
        val qx = x(i); val qy = y(i)
        val ex = (x(i) + x(i + 1)) / 2; val ey = (y(i) + y(i + 1)) / 2
        // Cuadrática pasada a cúbica.
        path.cubicTo(sx + 2f / 3f * (qx - sx), sy + 2f / 3f * (qy - sy), ex + 2f / 3f * (qx - ex), ey + 2f / 3f * (qy - ey), ex, ey)
        sx = ex; sy = ey
    }
    path.close()
}

/** 0 gris · 0,5 amarillo · 1 naranja. */
private fun heat(t: Float): Float = when {
    t <= 400f -> 0f
    t <= 540f -> 0.5f * StdEase.transform((t - 400f) / 140f)
    t <= 650f -> 0.5f + 0.5f * StdEase.transform((t - 540f) / 110f)
    else -> 1f
}

private fun tri(c: Float, gray: Color, yellow: Color, orange: Color) =
    if (c <= 0.5f) androidx.compose.ui.graphics.lerp(gray, yellow, c * 2f) else androidx.compose.ui.graphics.lerp(yellow, orange, (c - 0.5f) * 2f)

private class Bit(val dx: Float, val dy: Float, val orange: Boolean, val stick: Boolean)

private val SPARKS = listOf(
    Bit(-62f, -46f, true, true), Bit(58f, -52f, false, false), Bit(-74f, 4f, false, false), Bit(72f, -6f, true, true),
    Bit(-30f, -84f, true, true), Bit(34f, -88f, false, true), Bit(-50f, 34f, true, false), Bit(54f, 30f, true, true)
)
private val CONFETTI = listOf(
    Bit(-34f, -46f, false, false), Bit(-14f, -58f, true, true), Bit(10f, -62f, false, true), Bit(30f, -50f, true, false), Bit(42f, -18f, false, true),
    Bit(-44f, -16f, true, true), Bit(-24f, 20f, false, false), Bit(24f, 24f, true, true), Bit(52f, 8f, true, false), Bit(-54f, 4f, false, true)
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBit(b: Bit, x: Float, y: Float, unit: Float, alpha: Float, rot: Float, orange: Color, yellow: Color) {
    val w = (if (b.stick) 6f else 7f) * unit
    val h = (if (b.stick) 10f else 7f) * unit
    rotate(rot, Offset(x, y)) {
        drawRoundRect(
            (if (b.orange) orange else yellow).copy(alpha = alpha), topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit)
        )
    }
}

/**
 * Racha de XP +1 (≈3,2 s; un toque lo salta todo):
 * 1. La llama gris de ayer se derrite en una cápsula (300–400 ms) que se llena de color, gris → amarillo → naranja, con el interior moviéndose (400–650).
 * 2. Salta a una gota que flota sobre su sombra (660–960).
 * 3. Cae y la punta se estira en un cuello con una bolita (960–1040); el cuello se rompe: la bolita sale volando,
 *    el cuerpo se aplasta, saltan chispas y el número cae (el nuevo entra desde arriba).
 * 4. Se levanta alta y se asienta como llama (1110–1330).
 * 5. «días de racha», la semana y el día de hoy: una moneda que sube girando, enseña el check, cae a su sitio y suelta confeti (1350–3200).
 * Con "Quitar animaciones" aparece todo ya en su sitio.
 */
@Composable
private fun XpStreakBody(s: StreakUp, data: AppData, skip: Boolean, onReady: () -> Unit) {
    val ui = LocalUi.current
    val reduce = LocalReduceMotion.current
    val clock = remember { Animatable(if (reduce) STREAK_END else 0f) }
    LaunchedEffect(skip) {
        if (reduce) { onReady(); return@LaunchedEffect }
        if (skip) { clock.snapTo(STREAK_END); onReady(); return@LaunchedEffect }
        launch { delay(2900); onReady() }
        clock.animateTo(STREAK_END, tween(STREAK_END.toInt(), easing = LinearEasing))
    }
    val t = clock.value
    val flick = if (reduce) 1f else {
        val tr = rememberInfiniteTransition(label = "flame")
        val v by tr.animateFloat(1.04f, 0.92f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "inner")
        v
    }
    val dark = ui.dark
    val grayOut = ui.strong
    val grayLip = androidx.compose.ui.graphics.lerp(ui.strong, Color.Black, 0.25f)
    val grayIn = if (dark) Color(0xFF1F1E1B) else Color(0xFFF7F4EE)
    val grayShadow = if (dark) Color(0xFF22211E) else Color(0xFFEBE6DD)
    val yOut = if (dark) Color(0xFFFFC21A) else Color(0xFFF5B800)
    val yLip = Color(0xFFB98A0C)
    val yIn = Color(0xFFFFE7A3)
    val oLip = if (dark) Color(0xFFB54C08) else Color(0xFFA8450A)
    val oIn = if (dark) Color(0xFFFFC58A) else Color(0xFFFFB46B)
    val oShadow = if (dark) Color(0xFF3B2614) else Color(0xFFFDE3C8)
    val outerPath = remember { androidx.compose.ui.graphics.Path() }
    val lipPath = remember { androidx.compose.ui.graphics.Path() }
    val innerPath = remember { androidx.compose.ui.graphics.Path() }

    // Llama: lienzo de 220 × 200 dp; la base (50, 118) del dibujo cae abajo en el centro. El cuello y las chispas se salen por arriba a propósito.
    Canvas(Modifier.size(220.dp, 200.dp)) {
        val k = 1.4.dp.toPx()
        val ox = this.size.width / 2 - 50f * k
        val oy = this.size.height - 10.dp.toPx() - 118f * k
        val hot = heat(t)
        val lift = numKeys(LIFT_KEYS, t)
        // Sombra: se encoge cuando la gota flota.
        val shs = 1f + lift / 150f
        val shW = 72f * k * shs
        drawOval(androidx.compose.ui.graphics.lerp(grayShadow, oShadow, hot), Offset(ox + 50f * k - shW / 2, oy + 114f * k), Size(shW, 14f * k))
        // Chispas al romperse el cuello.
        val q = (t - 1040f) / 560f
        if (q > 0f && q < 1f) {
            val e = 1f - (1f - q) * (1f - q) * (1f - q)
            val a = 1f - c01((q - 0.45f) / 0.55f)
            SPARKS.forEachIndexed { i, b ->
                drawBit(b, ox + (50f + b.dx * e) * k, oy + (70f + b.dy * e - 10f * q * q) * k, k, a, (i * 37f + q * 220f) % 360f, ui.flame, yOut)
            }
        }
        // La bolita del cuello sale volando.
        val kq = (t - 1040f) / 300f
        if (kq > 0f && kq < 1f) {
            val ky = -44f - 60f * (1f - (1f - kq) * (1f - kq))
            drawRoundRect(
                ui.flame.copy(alpha = 1f - c01((kq - 0.35f) / 0.65f)), Offset(ox + 46f * k, oy + (ky - 6f) * k), Size(8f * k, 12f * k),
                androidx.compose.ui.geometry.CornerRadius(4f * k)
            )
        }
        // Asentarse: un pequeño bamboleo desde la base.
        var wx = 1f; var wy = 1f
        if (t in 1190f..1330f) {
            val wq = (t - 1190f) / 140f
            val w = kotlin.math.sin(wq * Math.PI.toFloat() * 2f) * (1f - wq) * 0.05f
            wx = 1f - w; wy = 1f + w
        }
        scale(wx, wy, pivot = Offset(ox + 50f * k, oy + 118f * k)) {
            val outer = OUTER_KEYS.at(t)
            smoothPath(outer, k, ox, oy + (lift + 6f) * k, lipPath)
            smoothPath(outer, k, ox, oy + lift * k, outerPath)
            smoothPath(INNER_KEYS.at(t), k, ox, oy + lift * k, innerPath)
            drawPath(lipPath, tri(hot, grayLip, yLip, oLip))
            drawPath(outerPath, tri(hot, grayOut, yOut, ui.flame))
            val settle = c01((t - 1190f) / 200f)
            scale(1f, 1f + (flick - 1f) * settle, pivot = Offset(ox + 50f * k, oy + 115f * k)) {
                drawPath(innerPath, tri(hot, grayIn, yIn, oIn))
            }
        }
    }

    // Número: el viejo cae por abajo y el nuevo entra desde arriba.
    val n = when {
        t <= 1040f -> 0f
        t >= 1110f -> 1f
        else -> DropEase.transform((t - 1040f) / 70f)
    }
    val old = (s.value - 1).coerceAtLeast(0)
    Box(Modifier.height(96.dp).fillMaxWidth().clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
        val hPx = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
        Text(
            "$old", color = ui.strong, fontWeight = FontWeight.Black, fontSize = 88.sp, lineHeight = 96.sp, letterSpacing = (-3).sp,
            modifier = Modifier.graphicsLayer { translationY = n * hPx }
        )
        Text(
            "${s.value}", color = ui.flame, fontWeight = FontWeight.Black, fontSize = 88.sp, lineHeight = 96.sp, letterSpacing = (-3).sp,
            modifier = Modifier.graphicsLayer { translationY = (n - 1f) * hPx }
        )
    }

    val txt = StdEase.transform(c01((t - 1350f) / 220f))
    Column(
        Modifier.graphicsLayer { alpha = txt; translationY = (1f - txt) * 12.dp.toPx() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (s.value == 1) "día de racha" else "días de racha",
            color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, textAlign = TextAlign.Center
        )
        val phrase = when {
            s.value >= 30 && s.value % 30 == 0 -> "Un mes entero. Esto ya no es fuerza de voluntad: es costumbre."
            s.value == 7 -> "Una semana seguida. Sigue así."
            s.value <= 1 -> "Primer día. Mañana, otro."
            else -> "Meta de hoy cumplida: ${xpOn(data, java.time.LocalDate.now())} de ${goalFor(data)} XP."
        }
        SerifText(phrase, color = ui.muted, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
    StreakWeekRow(data, t)
    if (s.record && (s.value == 7 || s.value >= 30)) {
        Box(Modifier.graphicsLayer { alpha = txt }) { RecordBadge(s.value) }
    }
}

/**
 * La semana (L–D). Entra entera con un fundido (1700 ms). El día de hoy es una moneda: sale de canto,
 * sube girando, se da la vuelta enseñando el check, cae a su hueco con un pequeño aplastón, suelta confeti
 * y los días de al lado rebotan.
 */
@Composable
private fun StreakWeekRow(data: AppData, t: Float) {
    val ui = LocalUi.current
    val day0 = java.time.LocalDate.now()
    val monday = day0.with(java.time.DayOfWeek.MONDAY)
    val todayIdx = day0.dayOfWeek.value - 1
    val todayMet = com.alberto.upgrade.data.goalMet(data, day0)
    val appear = StdEase.transform(c01((t - 1700f) / 160f))
    val yellow = if (ui.dark) Color(0xFFFFC21A) else Color(0xFFF5B800)
    val flameLip = if (ui.dark) Color(0xFFB54C08) else Color(0xFFA8450A)
    val flameLight = if (ui.dark) Color(0xFFFF9A4D) else Color(0xFFF2852F)
    Row(
        Modifier.padding(top = 40.dp).graphicsLayer { alpha = appear; translationY = (1f - appear) * 16.dp.toPx() },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, n ->
            val d = monday.plusDays(i.toLong())
            val isToday = i == todayIdx
            val met = !isToday && !d.isAfter(day0) && com.alberto.upgrade.data.goalMet(data, d)
            val dist = kotlin.math.abs(i - todayIdx)
            val rq = (t - 2560f - (dist - 1) * 40f) / 200f
            val ripple = if (dist in 1..2 && rq > 0f && rq < 1f) kotlin.math.sin(rq * Math.PI.toFloat()) * (if (dist == 1) 4f else 2f) else 0f
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { translationY = ripple.dp.toPx() }) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (met) ui.flame else ui.hairline),
                        contentAlignment = Alignment.Center
                    ) { if (met) UIcon(Ic.check, ui.bg, size = 18.dp) }
                    if (isToday && todayMet && t >= 2000f) {
                        // Confeti al aterrizar (no se recorta: se sale de la celda).
                        val cq = (t - 2560f) / 640f
                        if (cq > 0f && cq < 1f) {
                            Canvas(Modifier.size(34.dp)) {
                                val u = 1.dp.toPx()
                                val e = 1f - Math.pow((1f - cq).toDouble(), 2.4).toFloat()
                                val a = 1f - c01((cq - 0.5f) / 0.5f)
                                CONFETTI.forEachIndexed { j, b ->
                                    drawBit(b, center.x + b.dx * e * u, center.y + (b.dy * e + 46f * cq * cq) * u, u, a, (j * 53f + cq * 300f) % 360f, ui.flame, yellow)
                                }
                            }
                        }
                        Coin(t, ui.flame, flameLip, flameLight, ui.bg)
                    }
                }
                Text(
                    n, color = if (isToday) ui.flame else ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** La moneda del día de hoy: grosor (labio) + cara; de canto cuando el giro está a 90° o 270°. */
@Composable
private fun Coin(t: Float, face: Color, lip: Color, light: Color, onFace: Color) {
    val y: Float; val ang: Float; val sc: Float
    when {
        t <= 2350f -> {
            val q = (t - 2000f) / 350f
            val e = 1f - (1f - q) * (1f - q)
            y = -36f * e; ang = 90f + 270f * c01((t - 2000f) / 330f); sc = 1f + 0.15f * e
        }
        t <= 2400f -> { y = -36f; ang = 360f; sc = 1.15f }
        t <= 2560f -> { val q = (t - 2400f) / 160f; y = -36f * (1f - q * q); ang = 360f; sc = 1.15f - 0.15f * q }
        else -> { y = 0f; ang = 360f; sc = 1f }
    }
    val sy = kotlin.math.abs(kotlin.math.cos(Math.toRadians(ang.toDouble()))).toFloat().coerceAtLeast(0.08f)
    var sqx = 1f; var sqy = 1f
    if (t > 2560f && t < 2700f) {
        val q = (t - 2560f) / 140f
        val s = kotlin.math.sin(q * Math.PI.toFloat()) * (1f - q * 0.4f)
        sqx = 1f + 0.14f * s; sqy = 1f - 0.18f * s
    }
    val showCheck = ang >= 315f
    Box(
        Modifier.size(34.dp).graphicsLayer {
            translationY = y.dp.toPx()
            scaleX = sc * sqx
            scaleY = sc * sqy
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.width / 2
            val thick = 4.dp.toPx() * (1f - sy * 0.6f)
            // Grosor de la moneda.
            drawOval(lip, Offset(0f, center.y - r * sy + thick), Size(this.size.width, 2 * r * sy))
            drawOval(face, Offset(0f, center.y - r * sy), Size(this.size.width, 2 * r * sy))
            if (!showCheck && sy > 0.3f) {
                // Cara sin check: una franja más clara, plana.
                scale(1f, sy, pivot = center) {
                    rotate(-35f, center) {
                        drawRoundRect(light, Offset(center.x - r * 0.75f, center.y - r * 0.22f), Size(r * 1.5f, r * 0.44f), androidx.compose.ui.geometry.CornerRadius(r * 0.22f))
                    }
                }
            }
        }
        if (showCheck) Box(Modifier.fillMaxSize().graphicsLayer { scaleY = sy }, contentAlignment = Alignment.Center) { UIcon(Ic.check, onFace, size = 18.dp) }
    }
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
                            when (s.kind) { "xp" -> "Racha"; "focus" -> "Racha de foco"; else -> s.name },
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
