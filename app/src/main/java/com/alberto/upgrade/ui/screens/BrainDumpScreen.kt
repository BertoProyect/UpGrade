@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.alberto.upgrade.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.alberto.upgrade.data.AiEvent
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Gemini
import com.alberto.upgrade.data.Kind
import com.alberto.upgrade.data.Op
import com.alberto.upgrade.data.Proposal
import com.alberto.upgrade.data.Recorder
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.SecretStore
import com.alberto.upgrade.data.dayShort
import com.alberto.upgrade.data.kindsFor
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.Stepper
import com.alberto.upgrade.ui.Tag
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.areaIcon
import com.alberto.upgrade.ui.kindIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Phase { INPUT, RECORDING, WORKING, RESULTS }

/** Una propuesta en pantalla: lo que dijo la IA y lo que el usuario deja. */
private class PItem(val original: Proposal) {
    var p by mutableStateOf(original)
    var keep by mutableStateOf(true)
    var editing by mutableStateOf(false)
    var edited by mutableStateOf(false)
}

/** "Dímelo y lo organizo": voz o texto libre → tareas nuevas y cambios sobre las que ya hay. */
@Composable
fun BrainDumpScreen(data: AppData, actions: AppActions, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { Recorder(ctx.applicationContext) }

    var phase by remember { mutableStateOf(Phase.INPUT) }
    var text by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf<String?>(null) }
    var transcriptEdit by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<PItem>() }
    var note by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var stage by remember { mutableStateOf("Organizando…") }
    var confirmDelete by remember { mutableStateOf(false) }
    val levels = remember { mutableStateListOf<Float>() }
    var elapsed by remember { mutableLongStateOf(0L) }
    val hasKey = remember { SecretStore.hasGeminiKey() }

    LaunchedEffect(Unit) { Gemini.prewarm(Repo.data) }
    DisposableEffect(Unit) { onDispose { recorder.stopQuietly(); job?.cancel() } }

    fun run(input: String?, audio: ByteArray?) {
        job?.cancel()
        items.clear()
        note = null
        if (audio != null) { transcript = null; transcriptEdit = "" }
        stage = if (audio != null) "Escuchándote…" else "Organizando…"
        phase = Phase.WORKING
        job = scope.launch {
            Gemini.organize(Repo.data, input, audio).collect { ev ->
                when (ev) {
                    is AiEvent.Transcript -> { transcript = ev.text; transcriptEdit = ev.text; stage = "Organizando…" }
                    is AiEvent.Item -> items += PItem(ev.p)
                    is AiEvent.Done -> { note = ev.note; phase = Phase.RESULTS }
                }
            }
        }
    }

    fun startRecording() {
        if (!recorder.start()) {
            Toast.makeText(ctx, "No he podido usar el micrófono.", Toast.LENGTH_LONG).show()
            return
        }
        levels.clear()
        elapsed = 0
        phase = Phase.RECORDING
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(ctx, "Sin permiso de micrófono no puedo dictar. Puedes escribirlo.", Toast.LENGTH_LONG).show()
    }

    fun dictate() {
        if (!hasKey) {
            Toast.makeText(ctx, "El dictado usa Gemini: pon tu clave en Ajustes › IA.", Toast.LENGTH_LONG).show()
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stopRecording() {
        val audio = recorder.stop()
        if (audio == null) {
            Toast.makeText(ctx, "No he oído nada. Prueba otra vez.", Toast.LENGTH_SHORT).show()
            phase = Phase.INPUT
        } else run(null, audio)
    }

    if (phase == Phase.RECORDING) {
        LaunchedEffect(Unit) {
            while (recorder.recording) {
                levels += recorder.level()
                if (levels.size > 32) levels.removeAt(0)
                elapsed = System.currentTimeMillis() - recorder.startedAt
                if (elapsed >= Recorder.MAX_MS - 1_000) { stopRecording(); break }
                delay(80)
            }
        }
    }

    fun apply() {
        val chosen = items.filter { it.keep && !it.p.needsChoice }
        val award = Repo.applyProposals(chosen.map { it.p })
        if (chosen.any { it.edited }) {
            val source = transcript?.let { transcriptEdit.ifBlank { it } } ?: text
            Repo.addAiExample(source, Gemini.exampleJson(Repo.data, chosen.map { it.p }))
        }
        actions.award(award, "¡Hecho!")
        val creates = chosen.count { it.p.op == Op.CREAR }
        Toast.makeText(
            ctx,
            if (creates == chosen.size) "Añadido. Lo tienes en Hoy y en el calendario." else "Cambios aplicados.",
            Toast.LENGTH_SHORT
        ).show()
        onClose()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("Dímelo y lo organizo", onClose, close = true)

        when (phase) {
            Phase.INPUT -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                SerifText("Suéltalo todo como te venga. Yo lo separo en tareas, o cambio las que ya tienes.", fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
                if (!hasKey) {
                    Text(
                        "Modo básico, sin IA. Pon tu clave de Gemini en Ajustes para que entienda frases largas y puedas dictar.",
                        color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)
                    )
                }
                InputBox(
                    text, { text = it },
                    "Ej: el jueves tengo examen de Historia, temas 3 y 4. Mañana hay que entregar el writing de inglés. Pasa la llamada de Vaelor al viernes.",
                    Modifier.padding(top = 18.dp), minHeight = 240
                )
                Spacer(Modifier.height(16.dp))
            }

            Phase.RECORDING -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ui.danger))
                    Text("GRABANDO", color = ui.danger, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Waveform(levels)
                val s = elapsed / 1000
                Text("%d:%02d".format(s / 60, s % 60), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 44.sp, modifier = Modifier.padding(top = 24.dp))
                Text("Te escucho. Dilo todo seguido.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Máximo 2 min", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }

            // Esperando la primera respuesta: los puntitos, centrados en la pantalla.
            Phase.WORKING, Phase.RESULTS -> if (phase == Phase.WORKING && items.isEmpty() && transcript == null) Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingDots()
                Text(stage, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(top = 22.dp))
            } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                transcript?.let {
                    Text("Lo que he entendido", color = ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 6.dp))
                    InputBox(transcriptEdit, { transcriptEdit = it }, "", Modifier.padding(top = 6.dp), minHeight = 0)
                    if (phase == Phase.RESULTS && transcriptEdit.isNotBlank() && transcriptEdit != it) {
                        GhostButton(
                            "Reorganizar", { val t = transcriptEdit; transcript = t; run(t, null) },
                            Modifier.padding(top = 10.dp).fillMaxWidth(), height = 42.dp, icon = Ic.repeat, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
                if (phase == Phase.WORKING && items.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingDots()
                        Text(stage, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(top = 22.dp))
                    }
                } else {
                    if (phase == Phase.RESULTS) {
                        val n = items.size
                        SerifText(
                            when (n) {
                                0 -> "No he sacado nada."
                                1 -> "He sacado 1 cosa. Quítala si sobra."
                                else -> "He sacado $n cosas. Quita las que sobren."
                            },
                            fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    note?.let { Text(it, color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(14.dp))
                    items.forEach { item ->
                        key(item) {
                            val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                            AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 4 }) {
                                ProposalCard(data, item)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (phase == Phase.WORKING) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { LoadingDots(small = true) }
                    } else {
                        TextAction("Volver a empezar", { items.clear(); transcript = null; phase = Phase.INPUT }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            when (phase) {
                Phase.INPUT -> {
                    GhostButton("Dictar", { dictate() }, Modifier.fillMaxWidth(), icon = Ic.mic, iconSize = 20.dp)
                    Spacer(Modifier.height(10.dp))
                    ChunkyButton("Organizar", { run(text, null) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
                }
                Phase.RECORDING -> {
                    ChunkyButton("Parar y organizar", { stopRecording() }, Modifier.fillMaxWidth(), icon = Ic.square, iconSize = 18.dp)
                    TextAction("Cancelar", { recorder.stopQuietly(); phase = Phase.INPUT }, Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                Phase.WORKING -> TextAction("Cancelar", { job?.cancel(); phase = Phase.RESULTS }, Modifier.fillMaxWidth())
                Phase.RESULTS -> {
                    val chosen = items.filter { it.keep && !it.p.needsChoice }
                    val allCreate = chosen.all { it.p.op == Op.CREAR }
                    ChunkyButton(
                        if (allCreate) "Añadir ${chosen.size}" else "Aplicar ${chosen.size} " + if (chosen.size == 1) "cambio" else "cambios",
                        { if (chosen.any { it.p.op == Op.BORRAR }) confirmDelete = true else apply() },
                        Modifier.fillMaxWidth(),
                        enabled = chosen.isNotEmpty()
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        val dels = items.filter { it.keep && it.p.op == Op.BORRAR && !it.p.needsChoice }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (dels.size == 1) "¿Borrar esta tarea?" else "¿Borrar ${dels.size} tareas?", fontWeight = FontWeight.Black) },
            text = { Text(dels.joinToString("\n") { "· " + it.p.title }, fontWeight = FontWeight.SemiBold) },
            confirmButton = { TextButton({ confirmDelete = false; apply() }) { Text("Borrar y aplicar", color = ui.danger, fontWeight = FontWeight.Black) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Volver") } }
        )
    }
}

@Composable
private fun InputBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier, minHeight: Int) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.fillMaxWidth().heightIn(min = (minHeight + 32).dp)
            .clip(shape).background(ui.card).border(2.dp, ui.hairline, shape).padding(16.dp)
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, color = ui.muted, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
        }
        com.alberto.upgrade.ui.UpTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = ui.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight.dp)
        )
    }
}

/** Tres puntos lima que botan en secuencia (ciclo ~1,2 s, desfase 150 ms). */
@Composable
fun LoadingDots(small: Boolean = false) {
    val ui = LocalUi.current
    val t = rememberInfiniteTransition(label = "dots")
    val size = if (small) 10.dp else 16.dp
    val jump = if (small) 8f else 14f
    Row(Modifier.height(if (small) 24.dp else 40.dp), horizontalArrangement = Arrangement.spacedBy(if (small) 7.dp else 10.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            val y by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(
                    androidx.compose.animation.core.keyframes<Float> {
                        durationMillis = 1200
                        0f at 0 + i * 150 using FastOutSlowInEasing
                        1f at 200 + i * 150 using FastOutSlowInEasing
                        0f at 420 + i * 150
                        0f at 1200
                    },
                    RepeatMode.Restart
                ),
                label = "dot$i"
            )
            Box(Modifier.padding(bottom = (y * jump).dp).size(size).clip(CircleShape).background(ui.primary))
        }
    }
}

@Composable
private fun Waveform(levels: List<Float>) {
    val ui = LocalUi.current
    Row(Modifier.height(96.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        val padded = List((32 - levels.size).coerceAtLeast(0)) { 0f } + levels
        padded.forEach { l ->
            Box(Modifier.width(5.dp).height((6 + l * 84).dp).clip(RoundedCornerShape(3.dp)).background(if (l > 0.02f) ui.primary else ui.strong))
        }
    }
}

@Composable
private fun ProposalCard(data: AppData, item: PItem) {
    val ui = LocalUi.current
    val p = item.p
    val selected = item.keep && !p.needsChoice
    val target = p.targetId?.let { id -> data.tasks.firstOrNull { it.id == id } }
    val shape = RoundedCornerShape(16.dp)

    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row {
                val boxShape = RoundedCornerShape(9.dp)
                Box(Modifier.size(28.dp, 31.dp).clip(boxShape).clickable { item.keep = !item.keep }) {
                    if (selected) Box(Modifier.size(28.dp).offset(y = 3.dp).background(ui.primaryLip, boxShape))
                    Box(
                        Modifier.size(28.dp).clip(boxShape)
                            .background(if (selected) ui.primary else Color.Transparent)
                            .then(if (selected) Modifier else Modifier.border(2.5.dp, ui.strong, boxShape)),
                        contentAlignment = Alignment.Center
                    ) { if (selected) UIcon(Ic.check, ui.onPrimary, size = 18.dp) }
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    if (p.op != Op.CREAR) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UIcon(opIcon(p.op), if (p.op == Op.BORRAR) ui.danger else ui.accentInk, size = 14.dp)
                            Text(
                                p.op.label.uppercase(), color = if (p.op == Op.BORRAR) ui.danger else ui.accentInk,
                                fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 5.dp)
                            )
                        }
                    }
                    Text(
                        p.title, color = if (item.keep) ui.ink else ui.muted, fontWeight = FontWeight.Black, fontSize = 16.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (p.op == Op.BORRAR) TextDecoration.LineThrough else null
                    )
                    if (target != null && (p.op == Op.MOVER || p.op == Op.EDITAR)) {
                        BeforeAfter(data, target, p)
                    } else if (!p.needsChoice) {
                        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val subject = data.subject(p.subjectId)
                            FieldTag(subject?.name ?: data.area(p.areaId)?.name ?: "Personal", if (subject != null) Ic.graduationCap else areaIcon(p.areaId), "area" in p.doubts || "asignatura" in p.doubts, item.keep)
                            FieldTag(p.kind.label, kindIcon(p.kind), "tipo" in p.doubts, item.keep)
                            FieldTag(shortDue(p.date), if (p.date != null) Ic.calendar else null, "fecha" in p.doubts, item.keep)
                            if (p.time != null || "hora" in p.doubts) FieldTag(p.time?.let { minStr(it) } ?: "¿Hora?", Ic.clock, "hora" in p.doubts, item.keep)
                        }
                    }
                }
                if (p.op == Op.CREAR || p.op == Op.MOVER || p.op == Op.EDITAR) {
                    if (!p.needsChoice) {
                        Box(Modifier.size(34.dp).clip(CircleShape).clickable { item.editing = !item.editing }, contentAlignment = Alignment.Center) {
                            UIcon(if (item.editing) Ic.chevronDown else Ic.pencil, ui.muted, size = 18.dp)
                        }
                    }
                }
            }
            if (p.needsChoice) ChooseTarget(data, item)
            if (item.editing) ProposalEditor(data, item)
        }
    }

    if (selected || p.needsChoice) {
        ChunkyCard(Modifier.fillMaxWidth(), padding = 0.dp, radius = 16.dp) { body() }
    } else {
        Box(
            Modifier.fillMaxWidth().clip(shape)
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

private fun opIcon(op: Op): Int = when (op) {
    Op.CREAR -> Ic.plus
    Op.MOVER -> Ic.calendarClock
    Op.EDITAR -> Ic.pencil
    Op.COMPLETAR -> Ic.circleCheck
    Op.BORRAR -> Ic.trash
}

/** Etiqueta de un campo; si la IA duda, borde discontinuo e icono de interrogación. */
@Composable
private fun FieldTag(text: String, icon: Int?, doubt: Boolean, active: Boolean) {
    val ui = LocalUi.current
    if (!doubt) {
        Tag(text, icon = icon, color = if (active) null else ui.muted)
        return
    }
    Row(
        Modifier.clip(RoundedCornerShape(6.dp))
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    ui.muted, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                )
            }
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIcon(Ic.circleHelp, ui.muted, size = 12.dp, modifier = Modifier.padding(end = 4.dp))
        Text(text, color = ui.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** "Llamada Vaelor · mié 17:00 → vie 17:00", con lo viejo tachado. */
@Composable
private fun BeforeAfter(data: AppData, before: com.alberto.upgrade.data.TaskItem, after: Proposal) {
    val ui = LocalUi.current
    val oldDate = parseDate(before.due)
    val rows = buildList {
        if (before.title != after.title) add("Título" to (before.title to after.title))
        if (oldDate != after.date || before.time != after.time) {
            add("Cuándo" to (whenText(oldDate, before.time) to whenText(after.date, after.time)))
        }
        if (before.areaId != after.areaId) add("Área" to ((data.area(before.areaId)?.name ?: before.areaId) to (data.area(after.areaId)?.name ?: after.areaId)))
        if (before.kind != after.kind) add("Tipo" to (before.kind.label to after.kind.label))
    }
    Column(Modifier.padding(top = 6.dp)) {
        if (rows.isEmpty()) Text("Sin cambios", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        rows.forEach { (_, pair) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(pair.first, color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.LineThrough, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                UIcon(Ic.arrowRight, ui.muted, size = 14.dp, modifier = Modifier.padding(horizontal = 6.dp))
                Text(pair.second, color = ui.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}

private fun whenText(d: LocalDate?, t: Int?): String =
    shortDue(d) + (t?.let { " " + minStr(it) } ?: "")

/** Referencia ambigua: el usuario elige a qué tarea se refería. */
@Composable
private fun ChooseTarget(data: AppData, item: PItem) {
    val ui = LocalUi.current
    Column(Modifier.padding(top = 12.dp)) {
        Text("¿Cuál de estas?", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
        item.p.candidates.mapNotNull { id -> data.tasks.firstOrNull { it.id == id } }.forEach { t ->
            Row(
                Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(2.dp, ui.hairline, RoundedCornerShape(12.dp))
                    .clickable {
                        val p = item.p
                        item.p = when (p.op) {
                            Op.COMPLETAR, Op.BORRAR -> p.copy(
                                targetId = t.id, candidates = emptyList(), title = t.title, kind = t.kind, areaId = t.areaId,
                                subjectId = t.subjectId, date = parseDate(t.due), time = t.time
                            )
                            else -> p.copy(targetId = t.id, candidates = emptyList())
                        }
                        item.keep = true
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(whenText(parseDate(t.due), t.time), color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                UIcon(Ic.chevronRight, ui.muted, size = 16.dp)
            }
        }
    }
}

/** Editor en línea de una propuesta. Lo que se corrige aquí sirve de ejemplo a la IA la próxima vez. */
@Composable
private fun ProposalEditor(data: AppData, item: PItem) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    fun set(np: Proposal) { item.p = np.copy(doubts = emptySet()); item.edited = true }
    val p = item.p
    Column(Modifier.padding(top = 14.dp)) {
        val shape = RoundedCornerShape(12.dp)
        Box(Modifier.fillMaxWidth().clip(shape).background(ui.bg).border(2.dp, ui.hairline, shape).padding(12.dp)) {
            com.alberto.upgrade.ui.UpTextField(
                value = p.title,
                onValueChange = { set(p.copy(title = it.take(120))) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ui.ink),
                modifier = Modifier.fillMaxWidth()
            )
        }
        EditorLabel("Área")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.areas.filter { data.shows(it.id) || it.id == p.areaId }.forEach { a ->
                SelectChip(a.name, p.areaId == a.id, {
                    val kinds = kindsFor(a.id)
                    set(p.copy(areaId = a.id, kind = if (p.kind in kinds || p.kind == Kind.LLAMADA || p.kind == Kind.TAREA) p.kind else kinds.first()))
                }, icon = areaIcon(a.id))
            }
        }
        EditorLabel("Qué es")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (kindsFor(p.areaId) + Kind.TAREA + Kind.LLAMADA).distinct().filter { it != Kind.REPASO }.forEach { k ->
                SelectChip(k.label, p.kind == k, { set(p.copy(kind = k)) }, icon = kindIcon(k))
            }
        }
        if (p.areaId == "insti") {
            EditorLabel("Asignatura")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.subjects.forEach { s -> SelectChip(s.name, p.subjectId == s.id, { set(p.copy(subjectId = s.id)) }) }
            }
        }
        EditorLabel("Para cuándo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("Hoy", p.date == today, { set(p.copy(date = today)) })
            SelectChip("Mañana", p.date == today.plusDays(1), { set(p.copy(date = today.plusDays(1))) })
            SelectChip("Pasado", p.date == today.plusDays(2), { set(p.copy(date = today.plusDays(2))) })
            SelectChip("Sin fecha", p.date == null, { set(p.copy(date = null)) })
        }
        Stepper(
            "Día", shortDue(p.date),
            { set(p.copy(date = (p.date ?: today).minusDays(1).let { if (it.isBefore(today)) today else it })) },
            { set(p.copy(date = (p.date ?: today).plusDays(1))) }
        )
        Stepper(
            "Hora", p.time?.let { minStr(it) } ?: "—",
            { set(p.copy(time = ((p.time ?: 17 * 60) - 15).coerceAtLeast(0))) },
            { set(p.copy(time = ((p.time ?: 17 * 60 - 15) + 15).coerceAtMost(23 * 60 + 45))) }
        )
        if (p.time != null) TextAction("Quitar hora", { set(p.copy(time = null)) })
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(
        text.uppercase(), color = LocalUi.current.muted, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    )
}

private fun shortDue(d: LocalDate?): String {
    if (d == null) return "Sin fecha"
    val today = LocalDate.now()
    return when (d) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> "${dayShort(d).replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}"
    }
}
