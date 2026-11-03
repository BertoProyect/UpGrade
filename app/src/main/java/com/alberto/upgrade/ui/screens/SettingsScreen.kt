@file:OptIn(ExperimentalLayoutApi::class)

package com.alberto.upgrade.ui.screens

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
import com.alberto.upgrade.ui.Palette
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.Stepper
import com.alberto.upgrade.ui.bg
import com.alberto.upgrade.ui.fg
import java.time.LocalDate

private data class AppEntry(val pkg: String, val label: String)

@Composable
fun SettingsScreen(data: AppData, actions: AppActions, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var editSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showApps by remember { mutableStateOf(false) }

    fun upd(f: (AppData) -> AppData) {
        Repo.update(f)
        AlarmScheduler.scheduleAll(ctx)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportJson().toByteArray()) }
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

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Text("✕", color = ui.muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text("Ajustes", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp)) {

            SectionTitle("Meta diaria")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "Suave", 50 to "Normal", 80 to "Seria", 120 to "Bestia").forEach { (v, n) ->
                    SelectChip("$n · $v XP", data.dailyGoal == v, { upd { it.copy(dailyGoal = v) } })
                }
            }

            SectionTitle("Gym")
            Stepper("Días por semana", "${data.gymWeeklyTarget}",
                { upd { it.copy(gymWeeklyTarget = (it.gymWeeklyTarget - 1).coerceAtLeast(1)) } },
                { upd { it.copy(gymWeeklyTarget = (it.gymWeeklyTarget + 1).coerceAtMost(7)) } })

            SectionTitle("Bloqueo")
            val b = data.block
            SwitchRow("Bloqueo activado", b.enabled) { v -> upd { it.copy(block = it.block.copy(enabled = v)) } }
            SwitchRow("Franja de estudio automática", b.scheduleEnabled) { v -> upd { it.copy(block = it.block.copy(scheduleEnabled = v)) } }
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, n ->
                    val day = i + 1
                    SelectChip(n, day in b.days, {
                        upd { d ->
                            val days = if (day in d.block.days) d.block.days - day else (d.block.days + day).sorted()
                            d.copy(block = d.block.copy(days = days))
                        }
                    })
                }
            }
            Stepper("Desde", minStr(b.from),
                { upd { it.copy(block = it.block.copy(from = (it.block.from - 15).coerceAtLeast(0))) } },
                { upd { it.copy(block = it.block.copy(from = (it.block.from + 15).coerceAtMost(it.block.to - 15))) } })
            Stepper("Hasta", minStr(b.to),
                { upd { it.copy(block = it.block.copy(to = (it.block.to - 15).coerceAtLeast(it.block.from + 15))) } },
                { upd { it.copy(block = it.block.copy(to = (it.block.to + 15).coerceAtMost(24 * 60 - 1))) } })
            Stepper("Espera de emergencia", "${b.waitMinutes} min",
                { upd { it.copy(block = it.block.copy(waitMinutes = (it.block.waitMinutes - 5).coerceAtLeast(5))) } },
                { upd { it.copy(block = it.block.copy(waitMinutes = (it.block.waitMinutes + 5).coerceAtMost(60))) } })
            Stepper("Tiempo desbloqueado", "${b.unlockMinutes} min",
                { upd { it.copy(block = it.block.copy(unlockMinutes = (it.block.unlockMinutes - 5).coerceAtLeast(5))) } },
                { upd { it.copy(block = it.block.copy(unlockMinutes = (it.block.unlockMinutes + 5).coerceAtMost(60))) } })
            Text(
                "Permitidas siempre: teléfono, alarmas, teclado, Moodle y UpGrade. Extra: ${b.allowed.size} app(s).",
                color = ui.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)
            )
            GhostButton("Elegir apps permitidas", { showApps = true }, Modifier.fillMaxWidth(), height = 44.dp)

            SectionTitle("Avisos")
            NotifStepper("Plan del día", data.notif.morning) { v -> upd { it.copy(notif = it.notif.copy(morning = v)) } }
            NotifStepper("Exámenes y deberes", data.notif.afternoon) { v -> upd { it.copy(notif = it.notif.copy(afternoon = v)) } }
            NotifStepper("Racha en peligro", data.notif.streak) { v -> upd { it.copy(notif = it.notif.copy(streak = v)) } }
            NotifStepper("Cierre del día", data.notif.review) { v -> upd { it.copy(notif = it.notif.copy(review = v)) } }

            SectionTitle("Horario de clases")
            Text("Toca una casilla para cambiar la asignatura.", color = ui.muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            TimetableGrid(data) { day, period -> editSlot = day to period }
            Spacer(Modifier.height(10.dp))
            data.periods.forEachIndexed { i, p ->
                Stepper("${i + 1}ª hora (${minStr(p.end)})", minStr(p.start),
                    { upd { d -> d.copy(periods = d.periods.mapIndexed { j, q -> if (j == i) Period(q.start - 5, q.end - 5) else q }) } },
                    { upd { d -> d.copy(periods = d.periods.mapIndexed { j, q -> if (j == i) Period(q.start + 5, q.end + 5) else q }) } })
            }

            SectionTitle("Copia de seguridad")
            Text(
                "Tus datos solo viven en este móvil. Si desinstalas la app, se pierden. Guarda una copia de vez en cuando.",
                color = ui.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
            )
            GhostButton("Exportar copia", { exportLauncher.launch("upgrade-${LocalDate.now()}.json") }, Modifier.fillMaxWidth(), height = 44.dp)
            Spacer(Modifier.height(8.dp))
            GhostButton("Restaurar copia", { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.fillMaxWidth(), height = 44.dp)
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
                        }, colorKey = s.color)
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

    if (showApps) {
        AppPicker(data) { showApps = false }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ui.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = ui.primary))
    }
}

@Composable
private fun NotifStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Stepper(label, minStr(value), { onChange((value - 15).coerceAtLeast(0)) }, { onChange((value + 15).coerceAtMost(23 * 60 + 45)) })
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
                    val tag = Palette.of(s?.color)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(8.dp))
                            .background(if (s != null) tag.bg(ui) else ui.surface)
                            .border(1.dp, ui.hairline, RoundedCornerShape(8.dp))
                            .clickable { onTap(day, p) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            s?.name?.take(5) ?: "—",
                            color = if (s != null) tag.fg(ui) else ui.muted,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPicker(data: AppData, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val apps = remember {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Apps permitidas en el bloqueo", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.height(420.dp).verticalScroll(rememberScrollState())) {
                apps.forEach { a ->
                    val on = a.pkg in data.block.allowed
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            Repo.update { d ->
                                val list = if (a.pkg in d.block.allowed) d.block.allowed - a.pkg else d.block.allowed + a.pkg
                                d.copy(block = d.block.copy(allowed = list))
                            }
                        }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (on) "✅" else "⬜", fontSize = 18.sp)
                        Text(a.label, color = ui.ink, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp).width(220.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Listo", fontWeight = FontWeight.Bold) } }
    )
}
