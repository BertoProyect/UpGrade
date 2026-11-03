@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.EventItem
import com.alberto.upgrade.data.Kind
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.TaskItem
import com.alberto.upgrade.data.cap
import com.alberto.upgrade.data.dayShort
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.monthShort
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.Stepper
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.areaIcon
import com.alberto.upgrade.ui.kindIcon
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val EVENT = "EVENTO"

@Composable
fun AddSheet(
    data: AppData,
    initialDate: LocalDate?,
    editing: TaskItem?,
    onDismiss: () -> Unit,
    actions: AppActions
) {
    val ui = LocalUi.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()

    var title by remember { mutableStateOf(editing?.title ?: "") }
    var type by remember { mutableStateOf(editing?.kind?.name ?: Kind.DEBERES.name) }
    var areaId by remember { mutableStateOf(editing?.areaId ?: "insti") }
    var subjectId by remember { mutableStateOf(editing?.subjectId) }
    var date by remember {
        mutableStateOf(if (editing != null) parseDate(editing.due) else (initialDate ?: today.plusDays(1)))
    }
    var start by remember { mutableIntStateOf(17 * 60) }
    var end by remember { mutableIntStateOf(18 * 60) }
    var repeatDays by remember { mutableStateOf(listOf<Int>()) }
    var picking by remember { mutableStateOf(false) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (editing == null) {
            delay(250)
            runCatching { focus.requestFocus() }
        }
    }

    val isEvent = type == EVENT

    fun save() {
        val t = title.trim()
        if (t.isEmpty()) return
        if (isEvent) {
            Repo.saveEvent(
                EventItem(
                    Repo.newId(), t, areaId,
                    date = if (repeatDays.isEmpty()) (date ?: today).key() else null,
                    weekdays = repeatDays, start = start, end = maxOf(end, start + 15)
                )
            )
        } else {
            val kind = Kind.valueOf(type)
            val item = (editing ?: TaskItem(Repo.newId(), t, createdAt = System.currentTimeMillis())).copy(
                title = t,
                kind = kind,
                areaId = areaId,
                subjectId = if (areaId == "insti") subjectId else null,
                due = date?.key()
            )
            Repo.saveTask(item)
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = ui.bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).imePadding().navigationBarsPadding()
        ) {
            Text(
                if (editing != null) "Editar" else "Añadir",
                color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(if (isEvent) "Ej: Llamada Vaelor" else "Ej: Ejercicios pág. 54") },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ui.ink),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ui.primary, unfocusedBorderColor = ui.hairline,
                    focusedContainerColor = ui.card, unfocusedContainerColor = ui.card
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )

            Label("Qué es")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kind.entries.forEach { k ->
                    SelectChip(k.label, type == k.name, { type = k.name }, icon = kindIcon(k))
                }
                if (editing == null) SelectChip("Evento", isEvent, { type = EVENT }, icon = Ic.clock)
            }

            Label("Área")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.areas.forEach { a ->
                    SelectChip(a.name, areaId == a.id, { areaId = a.id }, icon = areaIcon(a.id))
                }
            }

            if (areaId == "insti" && !isEvent) {
                Label("Asignatura")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.subjects.forEach { s ->
                        SelectChip(s.name, subjectId == s.id, { subjectId = if (subjectId == s.id) null else s.id })
                    }
                }
            }

            Label(if (isEvent) "Cuándo" else "Para cuándo")
            if (!(isEvent && repeatDays.isNotEmpty())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Hoy", date == today, { date = today })
                    SelectChip("Mañana", date == today.plusDays(1), { date = today.plusDays(1) })
                    SelectChip("Pasado", date == today.plusDays(2), { date = today.plusDays(2) })
                    val custom = date != null && date != today && date != today.plusDays(1) && date != today.plusDays(2)
                    val d = date
                    SelectChip(
                        if (custom && d != null) "${dayShort(d).cap()} ${d.dayOfMonth} ${monthShort(d)}" else "Elegir…",
                        custom, { picking = true }, icon = Ic.calendar
                    )
                    if (!isEvent && type != Kind.EXAMEN.name) SelectChip("Sin fecha", date == null, { date = null })
                }
            }

            if (isEvent) {
                Spacer(Modifier.height(8.dp))
                Stepper("Empieza", minStr(start), { start = (start - 15).coerceAtLeast(0) }, { start = (start + 15).coerceAtMost(23 * 60 + 45); if (end <= start) end = start + 60 })
                Stepper("Termina", minStr(end), { end = (end - 15).coerceAtLeast(start + 15) }, { end = (end + 15).coerceAtMost(23 * 60 + 59) })
                Label("Repetir cada semana")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, n ->
                        val day = i + 1
                        SelectChip(n, day in repeatDays, {
                            repeatDays = if (day in repeatDays) repeatDays - day else (repeatDays + day).sorted()
                        })
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            ChunkyButton(if (editing != null) "Guardar" else "Añadir", { save() }, Modifier.fillMaxWidth(), enabled = title.isNotBlank())
            if (editing != null) {
                Spacer(Modifier.height(10.dp))
                GhostButton("Borrar", { Repo.deleteTask(editing.id); onDismiss() }, Modifier.fillMaxWidth(), icon = Ic.trash)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (picking) {
        val init = (date ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = init)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    picking = false
                }) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun Label(text: String) {
    val ui = LocalUi.current
    Text(text, color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}
