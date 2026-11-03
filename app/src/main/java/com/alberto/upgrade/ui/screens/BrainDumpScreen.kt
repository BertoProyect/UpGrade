package com.alberto.upgrade.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Planner
import com.alberto.upgrade.data.Proposal
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.dueLabel
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.CheckCircle
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.Tag
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.areaIcon
import com.alberto.upgrade.ui.kindIcon
import kotlinx.coroutines.launch

/** "Dímelo y lo organizo": voz o texto libre → tareas con fecha y hora. */
@Composable
fun BrainDumpScreen(data: AppData, actions: AppActions, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var proposals by remember { mutableStateOf<List<Proposal>?>(null) }
    var keep by remember { mutableStateOf(setOf<Int>()) }
    var note by remember { mutableStateOf<String?>(null) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) text = if (text.isBlank()) said else "$text. $said"
        }
    }

    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dime todo lo que tienes que hacer")
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        try {
            speech.launch(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "No encuentro el dictado de Google. Usa el micro del teclado.", Toast.LENGTH_LONG).show()
        }
    }

    fun organize() {
        loading = true
        note = null
        scope.launch {
            val r = Planner.plan(Repo.data, text)
            proposals = r.items
            keep = r.items.indices.toSet()
            note = r.note ?: if (r.items.isEmpty()) "No he sacado nada. Prueba con frases tipo «examen de Lengua el jueves»." else null
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.x, onClose)
            Column(Modifier.padding(start = 8.dp)) {
                Text("Dímelo y lo organizo", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text(
                    if (data.geminiKey.isBlank()) "Modo básico · sin IA" else "Con IA (Gemini)",
                    color = if (data.geminiKey.isBlank()) ui.muted else ui.accentInk, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val list = proposals
            if (list == null) {
                Text(
                    "Suéltalo todo, como te salga. Ejemplos:\n" +
                        "· «El jueves tengo examen de Lengua»\n" +
                        "· «Mañana deberes de Mates pág. 54 y llamada con el cliente de Órbita a las 6»\n" +
                        "· «Hazme la rutina de hoy: estudiar Historia, gym de pierna y editar un vídeo de BertoProject»",
                    color = ui.text, fontSize = 15.sp, lineHeight = 22.sp
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Escribe o pulsa el micro…") },
                    textStyle = TextStyle(fontSize = 17.sp, color = ui.ink, lineHeight = 24.sp),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ui.accentInk, unfocusedBorderColor = ui.hairline,
                        focusedContainerColor = ui.card, unfocusedContainerColor = ui.card
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                )
                Spacer(Modifier.height(12.dp))
                GhostButton("Dictar", { listen() }, Modifier.fillMaxWidth(), height = 48.dp, icon = Ic.mic)
            } else {
                note?.let {
                    Text(it, color = ui.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                list.forEachIndexed { i, p ->
                    ProposalRow(data, p, i in keep) {
                        keep = if (i in keep) keep - i else keep + i
                    }
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("Volver a escribir", { proposals = null }, Modifier.fillMaxWidth(), height = 44.dp, icon = Ic.pencil)
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(Modifier.padding(16.dp)) {
            val list = proposals
            when {
                loading -> Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = ui.accentInk, strokeWidth = 3.dp, modifier = Modifier.height(24.dp))
                    Text("Organizando…", color = ui.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                }
                list == null -> ChunkyButton("Organizar", { organize() }, Modifier.fillMaxWidth(), enabled = text.isNotBlank(), icon = Ic.sparkles)
                else -> ChunkyButton(
                    "Añadir ${keep.size}",
                    {
                        Repo.addTasks(list.filterIndexed { i, _ -> i in keep }.map { it.toTask() })
                        Toast.makeText(ctx, "Añadido. Lo tienes en Hoy y en el calendario.", Toast.LENGTH_SHORT).show()
                        onClose()
                    },
                    Modifier.fillMaxWidth(),
                    enabled = keep.isNotEmpty(),
                    icon = Ic.check
                )
            }
        }
    }
}

@Composable
private fun ProposalRow(data: AppData, p: Proposal, selected: Boolean, onToggle: () -> Unit) {
    val ui = LocalUi.current
    val area = data.area(p.areaId)
    val subject = data.subject(p.subjectId)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) ui.card else ui.surface)
            .border(2.dp, if (selected) ui.hairline else ui.surface, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(selected, onToggle)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                p.title, color = if (selected) ui.ink else ui.muted, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(subject?.name ?: area?.name ?: "Personal", icon = if (subject != null) kindIcon(p.kind) else areaIcon(p.areaId))
                Text(
                    "${p.kind.label} · ${dueLabel(p.date)}" + (p.time?.let { " · ${minStr(it)}" } ?: ""),
                    color = ui.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        UIcon(kindIcon(p.kind), ui.muted, size = 18.dp)
    }
}
