package com.alberto.upgrade.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Ui(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val card: Color,
    val ink: Color,
    val text: Color,
    val muted: Color,
    val hairline: Color,
    val lip: Color,
    val primary: Color,
    val primaryLip: Color,
    val onPrimary: Color,
    val success: Color,
    val successLip: Color,
    val successTint: Color,
    val successInk: Color,
    val flame: Color,
    val xp: Color,
    val danger: Color,
    val dangerTint: Color
)

val LightUi = Ui(
    dark = false,
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F5F4),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF1A1A1A),
    text = Color(0xFF37352F),
    muted = Color(0xFF6F6D66),
    hairline = Color(0xFFE5E3DF),
    lip = Color(0xFFD3D0CA),
    primary = Color(0xFF5645D4),
    primaryLip = Color(0xFF3A2A99),
    onPrimary = Color.White,
    success = Color(0xFF1AAE39),
    successLip = Color(0xFF12812A),
    successTint = Color(0xFFD9F3E1),
    successInk = Color(0xFF0F5A22),
    flame = Color(0xFFDD5B00),
    xp = Color(0xFFE0A100),
    danger = Color(0xFFE03131),
    dangerTint = Color(0xFFFDE2E0)
)

val DarkUi = Ui(
    dark = true,
    bg = Color(0xFF191919),
    surface = Color(0xFF202020),
    card = Color(0xFF252525),
    ink = Color(0xFFFFFFFF),
    text = Color(0xFFE6E6E4),
    muted = Color(0xFFA4A097),
    hairline = Color(0xFF353535),
    lip = Color(0xFF0E0E0E),
    primary = Color(0xFF7B6CF0),
    primaryLip = Color(0xFF4534B3),
    onPrimary = Color.White,
    success = Color(0xFF2FC250),
    successLip = Color(0xFF168A31),
    successTint = Color(0xFF1D3A26),
    successInk = Color(0xFFA6E3B5),
    flame = Color(0xFFFF8A3D),
    xp = Color(0xFFF5C542),
    danger = Color(0xFFFF6B6B),
    dangerTint = Color(0xFF4A2523)
)

data class TagColor(val bgL: Color, val fgL: Color, val bgD: Color, val fgD: Color, val strong: Color)

object Palette {
    val tags = mapOf(
        "gray" to TagColor(Color(0xFFF0EEEC), Color(0xFF5D5B54), Color(0xFF373737), Color(0xFFD3D3D1), Color(0xFF9B9A97)),
        "brown" to TagColor(Color(0xFFF3E9E2), Color(0xFF64473A), Color(0xFF4A3A2F), Color(0xFFE6CBB8), Color(0xFF9F6B53)),
        "orange" to TagColor(Color(0xFFFFE8D4), Color(0xFF793400), Color(0xFF5C3B23), Color(0xFFFFC79C), Color(0xFFDD5B00)),
        "yellow" to TagColor(Color(0xFFFEF3C7), Color(0xFF6B4E00), Color(0xFF564328), Color(0xFFFFE08A), Color(0xFFD9A400)),
        "green" to TagColor(Color(0xFFD9F3E1), Color(0xFF1C5E2E), Color(0xFF243D30), Color(0xFFA6E3B5), Color(0xFF1AAE39)),
        "teal" to TagColor(Color(0xFFD5F1EF), Color(0xFF1B5C59), Color(0xFF1F3F3D), Color(0xFF9FE0DA), Color(0xFF2A9D99)),
        "blue" to TagColor(Color(0xFFDCECFA), Color(0xFF0B4A85), Color(0xFF143A4E), Color(0xFFA9D3FF), Color(0xFF0075DE)),
        "purple" to TagColor(Color(0xFFE6E0F5), Color(0xFF391C57), Color(0xFF3C2D49), Color(0xFFD6B6F6), Color(0xFF7B3FF2)),
        "pink" to TagColor(Color(0xFFFDE0EC), Color(0xFF8A1E5A), Color(0xFF4E2C3C), Color(0xFFF9B7D7), Color(0xFFE255A1)),
        "red" to TagColor(Color(0xFFFDE2E0), Color(0xFF8E1F1A), Color(0xFF522E2A), Color(0xFFFFB3AE), Color(0xFFE03131))
    )
    val keys = tags.keys.toList()
    fun of(key: String?): TagColor = tags[key] ?: tags.getValue("gray")
}

fun TagColor.bg(ui: Ui) = if (ui.dark) bgD else bgL
fun TagColor.fg(ui: Ui) = if (ui.dark) fgD else fgL

val LocalUi = staticCompositionLocalOf { LightUi }

@Composable
fun UpgradeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ui = if (dark) DarkUi else LightUi
    val scheme = if (dark) {
        darkColorScheme(
            primary = ui.primary, onPrimary = ui.onPrimary, background = ui.bg, surface = ui.card,
            onBackground = ui.text, onSurface = ui.text, surfaceVariant = ui.surface, outline = ui.hairline,
            surfaceContainerHigh = ui.card, surfaceContainerLow = ui.surface, surfaceContainer = ui.card
        )
    } else {
        lightColorScheme(
            primary = ui.primary, onPrimary = ui.onPrimary, background = ui.bg, surface = ui.card,
            onBackground = ui.text, onSurface = ui.text, surfaceVariant = ui.surface, outline = ui.hairline,
            surfaceContainerHigh = ui.card, surfaceContainerLow = ui.surface, surfaceContainer = ui.card
        )
    }
    CompositionLocalProvider(LocalUi provides ui) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
