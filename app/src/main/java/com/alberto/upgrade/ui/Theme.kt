package com.alberto.upgrade.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.alberto.upgrade.R

data class Ui(
    val dark: Boolean,
    val bg: Color,
    /** Relleno de etiquetas y chips. */
    val surface: Color,
    val card: Color,
    val ink: Color,
    val text: Color,
    val muted: Color,
    val hairline: Color,
    val lip: Color,
    /** Borde fuerte: círculo de tarea pendiente, interruptor apagado. */
    val strong: Color,
    /** Único color de marca: acciones, progreso y "hecho". */
    val primary: Color,
    val primaryLip: Color,
    val onPrimary: Color,
    /** Versión del color de marca legible como texto sobre el fondo. */
    val accentInk: Color,
    /** Fondo de lo seleccionado (chip, pestaña). */
    val selTint: Color,
    val success: Color,
    val successLip: Color,
    val successTint: Color,
    val successInk: Color,
    /** Solo para la racha. */
    val flame: Color,
    val xp: Color,
    /** Solo para urgente: atrasado, examen cerca. */
    val danger: Color,
    val dangerTint: Color,
    val dangerLip: Color
)

private val Volt = Color(0xFFC4F23A)
private val VoltLip = Color(0xFF86AD12)
private val VoltLight = Color(0xFFB2E62A)
private val VoltLightLip = Color(0xFF6F9A0A)

/** Neutros cálidos: el lima destaca más sobre un negro ligeramente cálido. */
val LightUi = Ui(
    dark = false,
    bg = Color(0xFFFAF9F5),
    surface = Color(0xFFF2EFE8),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF141413),
    text = Color(0xFF3D3D3A),
    muted = Color(0xFF6C6A64),
    hairline = Color(0xFFE6DFD8),
    lip = Color(0xFFE6DFD8),
    strong = Color(0xFFD6D0C4),
    primary = VoltLight,
    primaryLip = VoltLightLip,
    onPrimary = Color(0xFF12160A),
    accentInk = Color(0xFF4C7A00),
    selTint = Color(0x38B2E62A),
    success = VoltLight,
    successLip = VoltLightLip,
    successTint = Color(0xFFEAF5C8),
    successInk = Color(0xFF3D6300),
    flame = Color(0xFFE8630A),
    xp = Color(0xFF4C7A00),
    danger = Color(0xFFD92D2D),
    dangerTint = Color(0xFFFDECEA),
    dangerLip = Color(0xFFF1B8B3)
)

val DarkUi = Ui(
    dark = true,
    bg = Color(0xFF141413),
    surface = Color(0xFF1F1E1B),
    card = Color(0xFF1C1B19),
    ink = Color(0xFFFAF9F5),
    text = Color(0xFFE8E6E1),
    muted = Color(0xFFA09D96),
    hairline = Color(0xFF2E2C28),
    lip = Color(0xFF2E2C28),
    strong = Color(0xFF3A3833),
    primary = Volt,
    primaryLip = VoltLip,
    onPrimary = Color(0xFF12160A),
    accentInk = Color(0xFFC4F23B),
    selTint = Color(0x1FC4F23A),
    success = Volt,
    successLip = VoltLip,
    successTint = Color(0xFF292F18),
    successInk = Color(0xFFC4F23B),
    flame = Color(0xFFFF7A1A),
    xp = Volt,
    danger = Color(0xFFFF5C5C),
    dangerTint = Color(0xFF2A1716),
    dangerLip = Color(0xFF6E2A2A)
)

/** Nunito para todo; Newsreader solo para frases que hacen pensar (nunca botones ni números). */
object Fonts {
    val Sans = FontFamily(
        Font(R.font.nunito_regular, FontWeight.Normal),
        Font(R.font.nunito_semibold, FontWeight.SemiBold),
        Font(R.font.nunito_bold, FontWeight.Bold),
        Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
        Font(R.font.nunito_black, FontWeight.Black)
    )
    val Serif = FontFamily(
        Font(R.font.newsreader_regular, FontWeight.Normal),
        Font(R.font.newsreader_medium, FontWeight.Medium)
    )
}

private fun appTypography(): Typography {
    val base = Typography()
    fun TextStyle.n() = copy(fontFamily = Fonts.Sans)
    return Typography(
        displayLarge = base.displayLarge.n(), displayMedium = base.displayMedium.n(), displaySmall = base.displaySmall.n(),
        headlineLarge = base.headlineLarge.n(), headlineMedium = base.headlineMedium.n(), headlineSmall = base.headlineSmall.n(),
        titleLarge = base.titleLarge.n(), titleMedium = base.titleMedium.n(), titleSmall = base.titleSmall.n(),
        bodyLarge = base.bodyLarge.n(), bodyMedium = base.bodyMedium.n(), bodySmall = base.bodySmall.n(),
        labelLarge = base.labelLarge.n(), labelMedium = base.labelMedium.n(), labelSmall = base.labelSmall.n()
    )
}

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
            primary = ui.accentInk, onPrimary = ui.bg, background = ui.bg, surface = ui.card,
            onBackground = ui.text, onSurface = ui.text, surfaceVariant = ui.surface, outline = ui.hairline,
            surfaceContainerHigh = ui.card, surfaceContainerLow = ui.surface, surfaceContainer = ui.card
        )
    } else {
        lightColorScheme(
            primary = ui.accentInk, onPrimary = ui.bg, background = ui.bg, surface = ui.card,
            onBackground = ui.text, onSurface = ui.text, surfaceVariant = ui.surface, outline = ui.hairline,
            surfaceContainerHigh = ui.card, surfaceContainerLow = ui.surface, surfaceContainer = ui.card
        )
    }
    CompositionLocalProvider(LocalUi provides ui) {
        MaterialTheme(colorScheme = scheme, typography = appTypography(), content = content)
    }
}
