@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UIcon
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
import com.alberto.upgrade.data.kindsFor
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
    initialArea: String? = null,
    initialKind: Kind? = null,
    editing: TaskItem?,
    onDismiss: () -> Unit,
    actions: AppActions
) {
    val ui = LocalUi.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()

    var title by remember { mutableStateOf(editing?.title ?: "") }
    var areaId by remember { mutableStateOf(editing?.areaId ?: initialArea ?: "insti") }
    var type by remember { mutableStateOf(editing?.kind?.name ?: initialKind?.name ?: kindsFor(areaId).first().name) }
    var time by remember { mutableStateOf(editing?.time ?: if (initialKind == Kind.LLAMADA) 17 * 60 else null) }
    var subjectId by remember { mutableStateOf(editing?.subjectId) }
    var date by remember {
        mutableStateOf(if (editing != null) parseDate(editing.due) else (initialDate ?: today.plusDays(1)))
    }
    var start by remember { mutableIntStateOf(17 * 60) }
    var end by remember { mutableIntStateOf(18 * 60) }
    var repeatDays by remember { mutableStateOf(listOf<Int>()) }
    var picking by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (editing == null) {
            delay(250)
            runCatching { focus.requestFocus() }
        }
    }

    val isEvent = type == EVENT

    fun save(plan: Boolean = false) {
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
                due = date?.key(),
                time = time
            )
            Repo.saveTask(item)
            if (plan) {
                onDismiss()
                actions.openOverlay("plan:" + item.id)
                return
            }
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = ui.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(ui.strong))
        }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).imePadding().navigationBarsPadding()
        ) {
            if (editing != null) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Editar ${editing.kind.label.lowercase()}", modifier = Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { Repo.deleteTask(editing.id); onDismiss() }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UIcon(Ic.trash, ui.danger, size = 16.dp)
                        Text("ELIMINAR", color = ui.danger, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = if (editing != null) 6.dp else 14.dp)) {
                if (title.isEmpty()) {
                    Text(
                        if (isEvent) "Ej: Formación Vaelor" else if (type == Kind.LLAMADA.name) "Ej: Llamada con cliente" else "¿Qué tienes que hacer?",
                        color = ui.muted, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 24.sp, fontWeight = FontWeight.Black, color = ui.ink),
                    cursorBrush = SolidColor(ui.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 2.dp).focusRequester(focus)
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(ui.hairline))

            Label("Área")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.areas.filter { (data.shows(it.id) && it.id != "personal") || it.id == areaId }.forEach { a ->
                    SelectChip(a.name, areaId == a.id, {
                        areaId = a.id
                        val allowed = kindsFor(a.id)
                        if (type != EVENT && allowed.none { it.name == type }) type = allowed.first().name
                    })
                }
            }

            Label("Qué es")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val kinds = (kindsFor(areaId) + listOfNotNull(editing?.kind)).distinct()
                kinds.forEach { k ->
                    SelectChip(k.label, type == k.name, {
                        type = k.name
                        if (k == Kind.LLAMADA && time == null) time = 17 * 60
                    })
                }
                if (editing == null) SelectChip("Evento", isEvent, { type = EVENT })
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
                        if (custom && d != null) "${dayShort(d).cap()} ${d.dayOfMonth} ${monthShort(d)}" else "Elegir",
                        custom, { picking = true }
                    )
                    if (!isEvent && type != Kind.EXAMEN.name) SelectChip("Sin fecha", date == null, { date = null })
                }
            }

            if (!isEvent) {
                val tm = time
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    UIcon(Ic.clock, ui.muted, size = 20.dp)
                    Text(
                        tm?.let { minStr(it) } ?: "Sin hora",
                        color = if (tm != null) ui.ink else ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                        modifier = Modifier.padding(start = 10.dp).weight(1f).clip(RoundedCornerShape(8.dp))
                            .clickable { if (tm == null) time = 17 * 60; pickingTime = true }.padding(vertical = 6.dp)
                    )
                    TextAction(
                        if (tm == null) "Añadir hora" else "Quitar",
                        { if (tm == null) { time = 17 * 60; pickingTime = true } else time = null },
                        color = ui.accentInk
                    )
                }
            }

            if (isEvent) {
                Spacer(Modifier.height(10.dp))
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

            Spacer(Modifier.height(24.dp))
            val xp = if (isEvent) 0 else com.alberto.upgrade.data.taskXpFor(data, Kind.valueOf(type))
            ChunkyButton(
                when {
                    editing != null -> "Guardar"
                    xp > 0 -> "Añadir · +$xp XP"
                    else -> "Añadir"
                },
                { save() }, Modifier.fillMaxWidth(), enabled = title.isNotBlank()
            )
            if (!isEvent && type == Kind.EXAMEN.name && date != null && date!!.isAfter(today)) {
                Spacer(Modifier.height(10.dp))
                com.alberto.upgrade.ui.GhostButton(
                    if (editing != null) "Guardar y planificar repaso" else "Añadir y planificar repaso",
                    { save(plan = true) }, Modifier.fillMaxWidth(), height = 46.dp, icon = com.alberto.upgrade.ui.Ic.bookOpen, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (pickingTime) {
        val init = time ?: (17 * 60)
        val ts = rememberTimePickerState(initialHour = init / 60, initialMinute = init % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = { TextButton({ time = ts.hour * 60 + ts.minute; pickingTime = false }) { Text("OK", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ pickingTime = false }) { Text("Cancelar") } },
            text = { TimePicker(state = ts) }
        )
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
    Text(text.uppercase(), color = ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 2.dp))
}
