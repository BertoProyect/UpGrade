@file:OptIn(ExperimentalLayoutApi::class)

package com.alberto.upgrade.ui.screens

import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.ListCard
import com.alberto.upgrade.ui.ListDivider
import com.alberto.upgrade.ui.ListRow
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.UToggle
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.ClassSlot
import com.alberto.upgrade.data.Period
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.notify.AlarmScheduler
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.Stepper
import java.time.LocalDate


@Composable
fun SettingsScreen(data: AppData, actions: AppActions, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var editSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun upd(f: (AppData) -> AppData) {
        Repo.update(f)
        AlarmScheduler.scheduleAll(ctx)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportJson().toByteArray()) }
                Repo.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
                Toast.makeText(ctx, "Copia guardada", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                Repo.importJson(text)
            }.getOrDefault(false)
            Toast.makeText(ctx, if (ok) "Copia restaurada" else "Ese archivo no es una copia de UpGrade", Toast.LENGTH_LONG).show()
            if (ok) AlarmScheduler.scheduleAll(ctx)
        }
    }

    var open by remember { mutableStateOf<String?>(null) }
    fun toggle(k: String) { open = if (open == k) null else k }
    val b = data.block

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ajustes", onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp)) {

            Group("Meta diaria")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 50, 80, 120).forEach { v ->
                    SelectChip("$v", data.dailyGoal == v, { upd { it.copy(dailyGoal = v) } }, Modifier.weight(1f), center = true)
                }
            }
            if (data.shows("gym")) {
                Stepper(
                    "Gym por semana", "${data.gymWeeklyTarget}",
                    { upd { it.copy(gymWeeklyTarget = (it.gymWeeklyTarget - 1).coerceAtLeast(1)) } },
                    { upd { it.copy(gymWeeklyTarget = (it.gymWeeklyTarget + 1).coerceAtMost(7)) } },
                    Modifier.padding(top = 12.dp)
                )
            }

            Group("Bloqueo")
            ListCard {
                ListRow("Bloqueo activado", subtitle = "En el modo Foco y en la franja de estudio", chevron = false) {
                    UToggle(b.enabled) { v -> upd { it.copy(block = it.block.copy(enabled = v)) } }
                }
                ListDivider()
                ListRow("Franja de estudio", subtitle = "${daysLabel(b.days)} · ${minStr(b.from)}–${minStr(b.to)}", onClick = { toggle("franja") }, chevron = false) {
                    UToggle(b.scheduleEnabled) { v -> upd { it.copy(block = it.block.copy(scheduleEnabled = v)) } }
                }
                if (open == "franja") Inline {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, n ->
                            val day = i + 1
                            SelectChip(n, day in b.days, {
                                upd { d ->
                                    val days = if (day in d.block.days) d.block.days - day else (d.block.days + day).sorted()
                                    d.copy(block = d.block.copy(days = days))
                                }
                            }, Modifier.weight(1f), center = true)
                        }
                    }
                    Stepper("Desde", minStr(b.from),
                        { upd { it.copy(block = it.block.copy(from = (it.block.from - 15).coerceAtLeast(0))) } },
                        { upd { it.copy(block = it.block.copy(from = (it.block.from + 15).coerceAtMost(it.block.to - 15))) } })
                    Stepper("Hasta", minStr(b.to),
                        { upd { it.copy(block = it.block.copy(to = (it.block.to - 15).coerceAtLeast(it.block.from + 15))) } },
                        { upd { it.copy(block = it.block.copy(to = (it.block.to + 15).coerceAtMost(24 * 60 - 1))) } })
                }
                ListDivider()
                ListRow("Apps bloqueadas", onClick = { actions.openOverlay("apps") })
                ListDivider()
                ListRow("Espera para desbloquear", value = "${b.waitMinutes} min", onClick = { toggle("espera") })
                if (open == "espera") Inline {
                    Stepper("Espera", "${b.waitMinutes} min",
                        { upd { it.copy(block = it.block.copy(waitMinutes = (it.block.waitMinutes - 5).coerceAtLeast(5))) } },
                        { upd { it.copy(block = it.block.copy(waitMinutes = (it.block.waitMinutes + 5).coerceAtMost(60))) } })
                    Stepper("Desbloqueado", "${b.unlockMinutes} min",
                        { upd { it.copy(block = it.block.copy(unlockMinutes = (it.block.unlockMinutes - 5).coerceAtLeast(5))) } },
                        { upd { it.copy(block = it.block.copy(unlockMinutes = (it.block.unlockMinutes + 5).coerceAtMost(60))) } })
                    Text("Teléfono, alarmas y teclado nunca se bloquean.", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Group("Avisos")
            ListCard {
                NotifRow("Plan del día", "morning", data.notif.morning, open, { toggle(it) }) { v -> upd { it.copy(notif = it.notif.copy(morning = v)) } }
                ListDivider()
                NotifRow("Exámenes y deberes", "afternoon", data.notif.afternoon, open, { toggle(it) }) { v -> upd { it.copy(notif = it.notif.copy(afternoon = v)) } }
                ListDivider()
                NotifRow("Racha en peligro", "streak", data.notif.streak, open, { toggle(it) }) { v -> upd { it.copy(notif = it.notif.copy(streak = v)) } }
                ListDivider()
                NotifRow("Cierra el día", "review", data.notif.review, open, { toggle(it) }) { v -> upd { it.copy(notif = it.notif.copy(review = v)) } }
            }

            Group("Datos")
            ListCard {
                ListRow(
                    "Clave de IA", icon = Ic.key,
                    value = if (data.geminiKey.isBlank()) "Sin poner" else "•••• " + data.geminiKey.takeLast(4),
                    onClick = { toggle("ia") }, chevron = false
                )
                if (open == "ia") Inline {
                    Text(
                        "Para «Dímelo y lo organizo». Pega tu clave gratuita de Gemini (aistudio.google.com → Get API key). Se guarda solo en este móvil.",
                        color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    var keyText by remember { mutableStateOf(data.geminiKey) }
                    Field(keyText, "Clave de Gemini", secret = true) { keyText = it.trim(); upd { d -> d.copy(geminiKey = keyText) } }
                    var modelText by remember { mutableStateOf(data.aiModel) }
                    Spacer(Modifier.height(8.dp))
                    Field(modelText, "Modelo") { modelText = it.trim(); upd { d -> d.copy(aiModel = modelText) } }
                }
                ListDivider()
                ListRow("Editar horario", icon = Ic.pencil, onClick = { toggle("horario") }, chevron = false)
                if (open == "horario") Inline {
                    Text("Toca una casilla para cambiar la asignatura.", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    TimetableGrid(data) { day, period -> editSlot = day to period }
                    Spacer(Modifier.height(10.dp))
                    data.periods.forEachIndexed { i, p ->
                        Stepper("${i + 1}ª hora (hasta ${minStr(p.end)})", minStr(p.start),
                            { upd { d -> d.copy(periods = d.periods.mapIndexed { j, q -> if (j == i) Period(q.start - 5, q.end - 5) else q }) } },
                            { upd { d -> d.copy(periods = d.periods.mapIndexed { j, q -> if (j == i) Period(q.start + 5, q.end + 5) else q }) } })
                    }
                }
                ListDivider()
                val last = data.lastBackupAt
                ListRow(
                    "Copia de seguridad", icon = Ic.backup,
                    subtitle = if (last == 0L) "Nunca. Tus datos solo viven en este móvil." else "Última: " + backupLabel(last),
                    onClick = { toggle("copia") }, chevron = false
                )
                if (open == "copia") Inline {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("Exportar", { exportLauncher.launch("upgrade-${LocalDate.now()}.json") }, Modifier.weight(1f), height = 44.dp, fontSize = 13.sp)
                        GhostButton("Restaurar", { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f), height = 44.dp, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    editSlot?.let { (day, period) ->
        AlertDialog(
            onDismissRequest = { editSlot = null },
            title = { Text("${listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")[day - 1]} · ${period + 1}ª hora", fontWeight = FontWeight.Black) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.subjects.forEach { s ->
                        SelectChip(s.name, data.timetable.any { it.day == day && it.period == period && it.subjectId == s.id }, {
                            upd { d ->
                                d.copy(timetable = d.timetable.filterNot { it.day == day && it.period == period } + ClassSlot(day, period, s.id))
                            }
                            editSlot = null
                        })
                    }
                    SelectChip("— Libre", false, {
                        upd { d -> d.copy(timetable = d.timetable.filterNot { it.day == day && it.period == period }) }
                        editSlot = null
                    })
                }
            },
            confirmButton = { TextButton({ editSlot = null }) { Text("Cerrar") } }
        )
    }

}

@Composable
private fun Group(text: String) {
    Eyebrow(text, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp))
}

/** Editor que se despliega debajo de una fila. */
@Composable
private fun Inline(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), content = content)
}

@Composable
private fun NotifRow(label: String, key: String, value: Int, open: String?, toggle: (String) -> Unit, onChange: (Int) -> Unit) {
    ListRow(label, value = minStr(value), onClick = { toggle(key) }, chevron = false)
    if (open == key) Inline {
        Stepper("Hora", minStr(value), { onChange((value - 15).coerceAtLeast(0)) }, { onChange((value + 15).coerceAtMost(23 * 60 + 45)) })
    }
}

@Composable
private fun Field(value: String, placeholder: String, secret: Boolean = false, onChange: (String) -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(ui.bg).border(2.dp, ui.hairline, shape).padding(horizontal = 12.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(placeholder, color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.alberto.upgrade.ui.Fonts.Sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ui.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(ui.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun backupLabel(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    val d = t.toLocalDate()
    val today = LocalDate.now()
    val day = when (d) {
        today -> "hoy"
        today.minusDays(1) -> "ayer"
        else -> "${d.dayOfMonth}/${d.monthValue}"
    }
    return "$day, ${minStr(t.hour * 60 + t.minute)}"
}

@Composable
private fun TimetableGrid(data: AppData, onTap: (Int, Int) -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("L", "M", "X", "J", "V").forEachIndexed { i, n ->
            val day = i + 1
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(n, color = ui.muted, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                data.periods.indices.forEach { p ->
                    val s = data.timetable.firstOrNull { it.day == day && it.period == p }?.let { data.subject(it.subjectId) }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(10.dp))
                            .background(ui.bg)
                            .border(2.dp, ui.hairline, RoundedCornerShape(10.dp))
                            .clickable { onTap(day, p) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            s?.name?.take(3) ?: "—",
                            color = if (s != null) ui.ink else ui.muted,
                            fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

