package com.alberto.upgrade.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle

/**
 * Campo de texto de UpGrade. Usa el BasicTextField de TextFieldState, que trae los gestos del sistema:
 * doble toque selecciona la palabra y saca la barra Copiar / Cortar / Pegar; mantener pulsado, igual;
 * las asas de selección se pueden arrastrar.
 *
 * La pantalla sigue trabajando con un String: [value] entra y cada cambio sale por [onValueChange].
 * Si la pantalla transforma el texto (recorta, quita saltos de línea…) el campo se pone al día.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val ui = LocalUi.current
    val state = remember { TextFieldState(value) }
    // Último texto que ha salido del campo: si [value] coincide, el cambio es nuestro y no hay que tocar nada.
    val emitted = remember { arrayOf(value) }
    val onChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { t ->
            if (t != emitted[0]) {
                emitted[0] = t
                onChange(t)
            }
        }
    }
    // Cambio desde fuera (o la pantalla ha transformado lo escrito): se copia al campo.
    SideEffect {
        if (value != emitted[0] && value != state.text.toString()) {
            emitted[0] = value
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }

    BasicTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = minLines.coerceAtLeast(1)),
        cursorBrush = SolidColor(ui.primary)
    )
}
