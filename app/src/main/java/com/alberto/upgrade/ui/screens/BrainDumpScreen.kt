@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.alberto.upgrade.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.Tag
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
        ScreenHeader("Dímelo y lo organizo", onClose, close = true)

        val list = proposals
        if (loading) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingDots()
                Text("Organizando…", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(top = 22.dp))
                Text("Separando tareas, fechas y áreas", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
            }
        } else if (list == null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                SerifText("Suéltalo todo como te venga. Yo lo separo en tareas.", fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
                if (data.geminiKey.isBlank()) {
                    Text(
                        "Modo básico, sin IA. Pon tu clave de Gemini en Ajustes para que entienda frases largas.",
                        color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)
                    )
                }
                val shape = RoundedCornerShape(18.dp)
                Box(
                    Modifier.padding(top = 18.dp).fillMaxWidth().heightIn(min = 260.dp)
                        .clip(shape).background(ui.card).border(2.dp, ui.hairline, shape).padding(16.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            "Ej: el jueves tengo examen de Química, temas 3 y 4. Mañana hay que entregar el writing de inglés. Llamar a Lola el viernes a las 6.",
                            color = ui.muted, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = ui.text),
                        cursorBrush = SolidColor(ui.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 228.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                SerifText(
                    when (list.size) {
                        0 -> "No he sacado nada."
                        1 -> "He sacado 1 cosa. Quítala si sobra."
                        else -> "He sacado ${list.size} cosas. Quita las que sobren."
                    },
                    fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp)
                )
                note?.let {
                    Text(it, color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(16.dp))
                list.forEachIndexed { i, p ->
                    ProposalCard(data, p, i in keep) {
                        keep = if (i in keep) keep - i else keep + i
                    }
                    Spacer(Modifier.height(12.dp))
                }
                TextAction("Volver a escribir", { proposals = null }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }
        }

        if (!loading) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                if (list == null) {
                    GhostButton("Dictar", { listen() }, Modifier.fillMaxWidth(), icon = Ic.mic, iconSize = 20.dp)
                    Spacer(Modifier.height(10.dp))
                    ChunkyButton("Organizar", { organize() }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
                } else {
                    ChunkyButton(
                        "Añadir ${keep.size}",
                        {
                            Repo.addTasks(list.filterIndexed { i, _ -> i in keep }.map { it.toTask() })
                            Toast.makeText(ctx, "Añadido. Lo tienes en Hoy y en el calendario.", Toast.LENGTH_SHORT).show()
                            onClose()
                        },
                        Modifier.fillMaxWidth(),
                        enabled = keep.isNotEmpty()
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingDots() {
    val ui = LocalUi.current
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    Row(Modifier.height(40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(
                Modifier.padding(bottom = if (on) 14.dp else 0.dp).size(16.dp).clip(CircleShape)
                    .background(if (on || phase.toInt() > i) ui.primary else ui.strong)
            )
        }
    }
}

@Composable
private fun ProposalCard(data: AppData, p: Proposal, selected: Boolean, onToggle: () -> Unit) {
    val ui = LocalUi.current
    val area = data.area(p.areaId)
    val subject = data.subject(p.subjectId)
    val shape = RoundedCornerShape(16.dp)
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            val boxShape = RoundedCornerShape(9.dp)
            Box(
                Modifier.size(28.dp, 31.dp)
            ) {
                if (selected) Box(Modifier.size(28.dp).offset(y = 3.dp).background(ui.primaryLip, boxShape))
                Box(
                    Modifier.size(28.dp).clip(boxShape)
                        .background(if (selected) ui.primary else Color.Transparent)
                        .then(if (selected) Modifier else Modifier.border(2.5.dp, ui.strong, boxShape)),
                    contentAlignment = Alignment.Center
                ) { if (selected) UIcon(Ic.check, ui.onPrimary, size = 18.dp) }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    p.title, color = if (selected) ui.ink else ui.muted, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(subject?.name ?: area?.name ?: "Personal", icon = if (subject != null) Ic.graduationCap else areaIcon(p.areaId), color = if (selected) null else ui.muted)
                    Tag(p.kind.label, color = if (selected) null else ui.muted)
                    Tag(
                        shortDue(p.date) +
                            (p.time?.let { " · ${minStr(it)}" } ?: ""),
                        icon = if (p.date != null) Ic.calendar else null,
                        color = if (selected) null else ui.muted
                    )
                }
            }
        }
    }
    if (selected) {
        ChunkyCard(Modifier.fillMaxWidth(), onClick = onToggle, padding = 0.dp, radius = 16.dp) { body() }
    } else {
        Box(
            Modifier.fillMaxWidth().clip(shape).clickable(onClick = onToggle)
                .drawBehind {
                    val w = 2.dp.toPx()
                    drawRoundRect(
                        ui.strong,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                    )
                }
        ) { body() }
    }
}

private fun shortDue(d: java.time.LocalDate?): String {
    if (d == null) return "Sin fecha"
    val today = java.time.LocalDate.now()
    return when (d) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> "${com.alberto.upgrade.data.dayShort(d).replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}"
    }
}
