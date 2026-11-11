package com.alberto.upgrade.ui

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Sistema de movimiento de UpGrade: cuatro duraciones y un único amortiguamiento (0.8).
 * Solo se anima lo que provoca el usuario; los bucles solo indican que algo está pasando.
 */
object UpMotion {
    const val PRESS = 90
    const val STATE = 200
    const val SCREEN = 280
    const val CELEBRATION = 1400

    fun <T> press(): FiniteAnimationSpec<T> = tween(PRESS, easing = FastOutLinearInEasing)
    fun <T> release(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 1500f)
    fun <T> state(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 800f)
    fun <T> screen(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 400f)
    fun <T> fade(ms: Int = 180): FiniteAnimationSpec<T> = tween(ms, easing = FastOutSlowInEasing)
}

/** "Quitar animaciones" del sistema, leído una vez y repartido por el árbol. */
val LocalReduceMotion = staticCompositionLocalOf { false }

fun systemReduceMotion(ctx: android.content.Context): Boolean =
    runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        .getOrDefault(false)

/** El spec pedido, o un salto si el sistema pide quitar animaciones. */
@Composable
@ReadOnlyComposable
fun <T> motion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) snap() else spec

/** Curva de salida exponencial (ease-out-quart): arranca rápido y se posa sin rebote. */
val EaseOutQuartCurve = androidx.compose.animation.core.CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

/**
 * Entrada de un elemento: sube 8 dp y aparece con un fundido de 200 ms, sin escalonar
 * ([index] se ignora; se deja para no tocar las llamadas). El contenido siempre está maquetado: si no hay animación, se ve tal cual.
 * Con [once] = true (filas de listas largas) solo se anima la primera vez, no cada vez que vuelve al hacer scroll.
 */
@Composable
fun androidx.compose.ui.Modifier.reveal(index: Int = 0, once: Boolean = false): androidx.compose.ui.Modifier {
    if (LocalReduceMotion.current) return this
    val seenSaved = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val seenLocal = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val seen = if (once) seenSaved else seenLocal
    val a = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (seen.value) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!seen.value) {
            // Sin escalonar: todo lo que entra a la vez aparece a la vez (Alberto lo pidió el 11 nov).
            a.animateTo(1f, tween(200, easing = EaseOutQuartCurve))
            seen.value = true
        }
    }
    val dy = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * dy
    }
}

/** Número que cambia rodando: el nuevo entra desde abajo (sube) o desde arriba (baja). */
@Composable
fun RollingNumber(value: Int, content: @Composable (Int) -> Unit) {
    val reduce = LocalReduceMotion.current
    androidx.compose.animation.AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (reduce) androidx.compose.animation.fadeIn(snap()) togetherWith androidx.compose.animation.fadeOut(snap())
            else {
                val up = targetState > initialState
                (androidx.compose.animation.slideInVertically(spring(0.8f, 800f)) { if (up) it else -it } + androidx.compose.animation.fadeIn(tween(150))) togetherWith
                    (androidx.compose.animation.slideOutVertically(spring(0.8f, 800f)) { if (up) -it else it } + androidx.compose.animation.fadeOut(tween(120)))
            }
        },
        label = "roll"
    ) { v -> content(v) }
}
