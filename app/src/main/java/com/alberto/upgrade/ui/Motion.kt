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
