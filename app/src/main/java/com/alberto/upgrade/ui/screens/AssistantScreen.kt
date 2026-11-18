@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.alberto.upgrade.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.ChatEvent
import com.alberto.upgrade.data.ChatMsg
import com.alberto.upgrade.data.Gemini
import com.alberto.upgrade.data.LiveDictation
import com.alberto.upgrade.data.Op
import com.alberto.upgrade.data.Proposal
import com.alberto.upgrade.data.Recorder
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.SecretStore
import com.alberto.upgrade.data.Speculator
import com.alberto.upgrade.data.Telemetry
import com.alberto.upgrade.data.dayShort
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.nowMin
import com.alberto.upgrade.data.pendingExams
import com.alberto.upgrade.data.subjectLabel
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.ListCard
import com.alberto.upgrade.ui.ListDivider
import com.alberto.upgrade.ui.ListRow
import com.alberto.upgrade.ui.LocalReduceMotion
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.Tag
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.areaIcon
import com.alberto.upgrade.ui.kindIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Un mensaje en pantalla. El de la IA crece mientras llega y puede traer propuestas para la agenda. */
private class UiMsg(val id: String, val role: String, text: String, val image: ImageBitmap? = null, applied: Int = 0, val photo: Boolean = false) {
    var text by mutableStateOf(text)
    var streaming by mutableStateOf(false)
    var note by mutableStateOf<String?>(null)
    val items = mutableStateListOf<Prop>()
    var applied by mutableIntStateOf(applied)
}

private class Prop(val p: Proposal) {
    var keep by mutableStateOf(true)
}

/**
 * La IA general: pregúntale lo que sea (estudios, hábitos, qué hacer ahora…). Conoce todos tus datos.
 * Te escucha mientras hablas (y empieza a pensar en cada pausa), lee fotos hechas con la cámara de la app
 * y propone apuntar en la agenda lo que encuentre; tú confirmas.
 */
@Composable
fun AssistantScreen(data: AppData, actions: AppActions, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val msgs = remember {
        mutableStateListOf<UiMsg>().apply {
            addAll(Repo.data.chat.map { UiMsg(it.id, it.role, it.text, applied = it.applied, photo = it.photo) })
        }
    }
    var input by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    val photoThumb = remember(photo) { photo?.let { thumb(it) } }
    var camera by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val streaming = msgs.lastOrNull()?.streaming == true
    val hasKey = remember { SecretStore.hasGeminiKey() }

    val canListen = remember { LiveDictation.available(ctx) }
    val live = data.dictation == "live" && canListen
    val dict = remember { LiveDictation(ctx.applicationContext, Gemini.vocabulary(Repo.data)) }
    val recorder = remember { Recorder(ctx.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    val spec = remember { Speculator<ChatEvent>(scope, { it is ChatEvent.Done }) { t -> Gemini.chat(Repo.data, Repo.data.chat, t, null) } }
    dict.onPhrase = { t -> if (photo == null) spec.offer(t) }

    LaunchedEffect(Unit) { Gemini.prewarm(Repo.data); Telemetry.event("IA: abierta") }
    DisposableEffect(Unit) { onDispose { dict.stopQuietly(); recorder.stopQuietly(); spec.cancel() } }

    fun send(text: String, image: ByteArray?, preset: Flow<ChatEvent>? = null) {
        val clean = text.trim()
        if (clean.isEmpty() && image == null) return
        if (streaming) return
        val history = Repo.data.chat
        val user = UiMsg(Repo.newId(), "user", clean, image?.let { thumb(it) }, photo = image != null)
        msgs += user
        Repo.addChat(ChatMsg(user.id, "user", clean, System.currentTimeMillis(), photo = image != null))
        Telemetry.event(if (image != null) "IA: foto enviada" else "IA: pregunta")
        val ai = UiMsg(Repo.newId(), "ai", "")
        ai.streaming = true
        msgs += ai
        input = ""
        photo = null
        job = scope.launch {
            try {
                (preset ?: Gemini.chat(Repo.data, history, clean, image)).collect { ev ->
                    when (ev) {
                        is ChatEvent.Answer -> ai.text = ev.text
                        is ChatEvent.Item -> if (!ev.p.needsChoice) ai.items += Prop(ev.p)
                        is ChatEvent.Done -> {
                            ai.note = ev.note
                            ai.streaming = false
                            Repo.addChat(ChatMsg(ai.id, "ai", ai.text.ifBlank { ev.note.orEmpty() }, System.currentTimeMillis(), proposed = ai.items.size))
                        }
                    }
                }
            } finally {
                if (ai.streaming) {
                    ai.streaming = false
                    if (ai.text.isNotBlank()) Repo.addChat(ChatMsg(ai.id, "ai", ai.text, System.currentTimeMillis()))
                }
            }
        }
    }

    fun stopVoice() {
        if (dict.listening) {
            dict.finish { final ->
                if (final.isBlank()) { Toast.makeText(ctx, dict.error ?: "No te he oído.", Toast.LENGTH_SHORT).show(); return@finish }
                val pre = if (photo == null) spec.take(final) else { spec.cancel(); null }
                send(final, photo, pre)
            }
        } else if (recording) {
            recording = false
            val audio = recorder.stop() ?: run { Toast.makeText(ctx, "No te he oído.", Toast.LENGTH_SHORT).show(); return }
            transcribing = true
            scope.launch {
                val t = runCatching { Gemini.transcribeOnly(Repo.data, audio) }.getOrDefault("")
                transcribing = false
                if (t.isNotBlank()) send(t, photo) else Toast.makeText(ctx, "No te he entendido. Prueba otra vez.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startVoice() {
        if (!hasKey) { Toast.makeText(ctx, "Pon tu clave de Gemini en Ajustes › IA.", Toast.LENGTH_LONG).show(); return }
        spec.reset()
        if (live && dict.start()) { Telemetry.event("IA: dictado en vivo"); return }
        if (recorder.start()) { recording = true; Telemetry.event("IA: dictado preciso") }
        else Toast.makeText(ctx, "No he podido usar el micrófono.", Toast.LENGTH_SHORT).show()
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startVoice() else Toast.makeText(ctx, "Sin permiso de micrófono no puedo escucharte.", Toast.LENGTH_LONG).show()
    }
    val camPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) camera = true else Toast.makeText(ctx, "Sin permiso de cámara no puedo hacer fotos.", Toast.LENGTH_LONG).show()
    }
    fun openCamera() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = true
        else camPermission.launch(Manifest.permission.CAMERA)
    }
    fun mic() {
        if (dict.listening || recording) { stopVoice(); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            com.alberto.upgrade.ui.ScreenHeader("Pregúntame", onClose) {
                if (msgs.isNotEmpty() && !streaming) IconTap(Ic.plus, { msgs.clear(); Repo.clearChat() }, tint = ui.ink)
            }

            if (msgs.isEmpty()) {
                EmptyState(data, hasKey, Modifier.weight(1f), onAsk = { send(it, null) }, onPhoto = { openCamera() }) { actions.openOverlay("settings") }
            } else {
                val list = rememberLazyListState()
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(), state = list, reverseLayout = true,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    items(msgs.asReversed(), key = { it.id }) { m ->
                        if (m.role == "user") UserBubble(m) else AiMessage(data, m) { chosen ->
                            val award = Repo.applyProposals(chosen.map { it.p })
                            m.applied = chosen.size
                            Repo.updateChat(m.id) { it.copy(applied = chosen.size) }
                            actions.award(award, "¡Hecho!")
                            Toast.makeText(ctx, if (chosen.all { it.p.op == Op.CREAR }) "Añadido a tu agenda." else "Cambios aplicados.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            Composer(
                input = if (dict.listening) dict.text else input,
                onInput = { if (!dict.listening) input = it },
                listening = dict.listening || recording,
                level = if (dict.listening) dict.level else if (recording) recorder.level() else 0f,
                transcribing = transcribing,
                streaming = streaming,
                photo = photoThumb,
                onRemovePhoto = { photo = null },
                onCamera = { openCamera() },
                onMic = { mic() },
                onSend = { send(input, photo) },
                onStop = { job?.cancel() }
            )
        }

        if (camera) {
            CameraCapture(onClose = { camera = false }) { bytes ->
                camera = false
                photo = bytes
                Telemetry.event("IA: foto hecha")
                // Sin texto, se manda ya: «apunta lo que veas». Con texto, espera a que lo envíes.
                if (input.isBlank()) send("", bytes)
            }
        }
    }
}

/** Sin conversación: una frase y atajos que salen de tus datos (examen cercano, la hora del día…). */
@Composable
private fun EmptyState(data: AppData, hasKey: Boolean, modifier: Modifier, onAsk: (String) -> Unit, onPhoto: () -> Unit, onSettings: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val exam = remember(data.tasks) { pendingExams(data, today, 14).firstOrNull()?.first }
    val minute = nowMin()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Bottom) {
        SerifText("¿En qué te ayudo?", fontSize = 30.sp)
        Text(
            "Tengo delante tus tareas, exámenes, hábitos, focos y cómo usas la app. Pregunta lo que sea: qué hacer ahora, una duda de clase o cómo vas.",
            color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
        )
        if (!hasKey) {
            ListCard {
                ListRow("Pon tu clave de Gemini", icon = Ic.key, subtitle = "Gratis, en Ajustes › IA. Sin ella no puedo responder.", onClick = onSettings)
            }
            Spacer(Modifier.height(12.dp))
        }
        ListCard {
            val rows = buildList {
                add(Ic.zap to "¿Qué hago ahora?")
                add(Ic.calendarClock to if (minute < 14 * 60) "Organízame el día" else "Organízame lo que queda de hoy")
                exam?.let { add(Ic.graduationCap to "Hazme 5 preguntas para el examen de " + (data.subjectLabel(it) ?: it.title)) }
                add(Ic.trendingUp to "¿Cómo voy esta semana?")
            }
            rows.forEachIndexed { i, (icon, text) ->
                if (i > 0) ListDivider()
                ListRow(text, icon = icon, onClick = { onAsk(text) })
            }
            ListDivider()
            ListRow("Haz una foto y lo apunto", icon = Ic.camera, subtitle = "La agenda, la pizarra, una hoja de deberes…", onClick = onPhoto)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun UserBubble(m: UiMsg) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), horizontalAlignment = Alignment.End) {
        m.image?.let {
            Image(it, contentDescription = "Foto enviada", contentScale = ContentScale.Crop, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, ui.hairline, RoundedCornerShape(16.dp)))
            if (m.text.isNotBlank()) Spacer(Modifier.height(6.dp))
        }
        if (m.image == null && m.photo) {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                UIcon(Ic.camera, ui.muted, size = 14.dp)
                Text("Foto", color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (m.text.isNotBlank()) {
            val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
            Text(
                m.text, color = ui.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 300.dp).clip(shape).background(ui.surface).border(2.dp, ui.hairline, shape).padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AiMessage(data: AppData, m: UiMsg, onApply: (List<Prop>) -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UIcon(Ic.sparkles, ui.accentInk, size = 14.dp)
            Text("UpGrade", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
        }
        if (m.text.isBlank() && m.streaming) {
            Box(Modifier.padding(top = 6.dp)) { LoadingDots(small = true) }
        } else if (m.text.isNotBlank()) {
            RichText(m.text, Modifier.padding(top = 4.dp))
        }
        m.note?.let { Text(it, color = if (m.text.isBlank()) ui.ink else ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
        if (m.items.isNotEmpty()) ProposalBlock(data, m, onApply)
    }
}

/** Lo que la IA propone apuntar: cada línea se puede quitar; un botón lo añade todo de una vez. */
@Composable
private fun ProposalBlock(data: AppData, m: UiMsg, onApply: (List<Prop>) -> Unit) {
    val ui = LocalUi.current
    val done = m.applied > 0
    Column(Modifier.padding(top = 12.dp)) {
        ListCard {
            m.items.forEachIndexed { i, it ->
                if (i > 0) ListDivider()
                ProposalLine(data, it, enabled = !done && !m.streaming)
            }
        }
        if (done) {
            Row(Modifier.padding(top = 10.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                UIcon(Ic.circleCheck, ui.accentInk, size = 18.dp)
                Text(if (m.applied == 1) "Apuntado" else "${m.applied} apuntadas", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
            }
        } else if (!m.streaming) {
            val chosen = m.items.filter { it.keep }
            val allCreate = chosen.all { it.p.op == Op.CREAR }
            ChunkyButton(
                if (allCreate) "Añadir ${chosen.size}" else "Aplicar ${chosen.size} " + if (chosen.size == 1) "cambio" else "cambios",
                { onApply(chosen) }, Modifier.fillMaxWidth().padding(top = 10.dp), height = 46.dp, fontSize = 14.sp, enabled = chosen.isNotEmpty()
            )
        }
    }
}

@Composable
private fun ProposalLine(data: AppData, it: Prop, enabled: Boolean) {
    val ui = LocalUi.current
    val p = it.p
    val on = it.keep
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { it.keep = !it.keep }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = RoundedCornerShape(8.dp)
        Box(
            Modifier.size(24.dp).clip(shape).background(if (on) ui.primary else ui.card).then(if (on) Modifier else Modifier.border(2.dp, ui.strong, shape)),
            contentAlignment = Alignment.Center
        ) { if (on) UIcon(Ic.check, ui.onPrimary, size = 16.dp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            if (p.op != Op.CREAR) Text(p.op.label.uppercase(), color = if (p.op == Op.BORRAR) ui.danger else ui.accentInk, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp)
            Text(
                p.title, color = if (on) ui.ink else ui.muted, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (p.op == Op.BORRAR) TextDecoration.LineThrough else null
            )
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val subject = p.subjectIds.mapNotNull { id -> data.subject(id)?.name }.takeIf { s -> s.isNotEmpty() }?.joinToString(" · ")
                Tag(subject ?: data.area(p.areaId)?.name ?: "Personal", icon = if (subject != null) Ic.graduationCap else areaIcon(p.areaId))
                Tag(p.kind.label, icon = kindIcon(p.kind))
                Tag(p.date?.let { d -> if (d == LocalDate.now()) "Hoy" else if (d == LocalDate.now().plusDays(1)) "Mañana" else dayShort(d).replaceFirstChar { c -> c.uppercase() } + " " + d.dayOfMonth } ?: "Sin fecha", icon = Ic.calendar)
                p.time?.let { t -> Tag(minStr(t), icon = Ic.clock) }
            }
        }
    }
}

/** Texto de la IA con un markdown mínimo: párrafos, listas con «- » y **negrita**. */
@Composable
private fun RichText(text: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val style = TextStyle(fontFamily = Fonts.Sans, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, color = ui.text)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.trim().split("\n").map { it.trimEnd() }.filter { it.isNotBlank() }.forEach { line ->
            val t = line.trimStart()
            val bullet = Regex("^([-•*]|\\d{1,2}[.)])\\s+").find(t)
            if (bullet != null) {
                Row {
                    Text(if (bullet.value.trim().first().isDigit()) bullet.value.trim() else "•", style = style.copy(color = ui.accentInk, fontWeight = FontWeight.Black), modifier = Modifier.widthIn(min = 18.dp))
                    Text(bold(t.substring(bullet.value.length), ui.ink), style = style, modifier = Modifier.padding(start = 4.dp))
                }
            } else Text(bold(t, ui.ink), style = style)
        }
    }
}

private fun bold(s: String, ink: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, p ->
        if (i % 2 == 1 && i < parts.size - 1) withStyle(SpanStyle(fontWeight = FontWeight.Black, color = ink)) { append(p) }
        else append(p)
    }
}

/**
 * Barra de escribir: cámara · texto · micro (o enviar si hay algo escrito). Mientras escucha, el texto
 * aparece según hablas y el micro late con tu voz; pulsarlo otra vez lo manda.
 */
@Composable
private fun Composer(
    input: String, onInput: (String) -> Unit, listening: Boolean, level: Float, transcribing: Boolean, streaming: Boolean,
    photo: ImageBitmap?, onRemovePhoto: () -> Unit, onCamera: () -> Unit, onMic: () -> Unit, onSend: () -> Unit, onStop: () -> Unit
) {
    val ui = LocalUi.current
    val reduce = LocalReduceMotion.current
    Column(Modifier.fillMaxWidth().background(ui.bg).padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)) {
        AnimatedVisibility(photo != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            Box(Modifier.padding(start = 56.dp, bottom = 8.dp)) {
                photo?.let { Image(it, contentDescription = "Foto adjunta", contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) }
                Box(
                    Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(ui.ink).clickable(onClickLabel = "Quitar foto", onClick = onRemovePhoto),
                    contentAlignment = Alignment.Center
                ) { UIcon(Ic.x, ui.bg, size = 14.dp) }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val sq = RoundedCornerShape(14.dp)
            Box(
                Modifier.size(48.dp).clip(sq).border(2.dp, ui.hairline, sq).clickable(onClickLabel = "Hacer una foto", onClick = onCamera),
                contentAlignment = Alignment.Center
            ) { UIcon(Ic.camera, ui.ink, size = 22.dp) }
            val shape = RoundedCornerShape(22.dp)
            Box(
                Modifier.weight(1f).padding(horizontal = 8.dp).heightIn(min = 48.dp).clip(shape).background(ui.card)
                    .border(2.dp, if (listening) ui.accentInk else ui.hairline, shape).padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (input.isEmpty()) {
                    Text(
                        when { transcribing -> "Transcribiendo…"; listening -> "Te escucho…"; else -> "Pregunta o pide algo" },
                        color = if (listening) ui.accentInk else ui.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                com.alberto.upgrade.ui.UpTextField(
                    input, onInput, Modifier.fillMaxWidth().heightIn(max = 132.dp),
                    textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = ui.ink),
                    enabled = !listening,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
            val showSend = !listening && (input.isNotBlank() || photo != null)
            val pulse by animateFloatAsState(if (listening && !reduce) 1f + 0.12f * level else 1f, tween(90), label = "mic")
            when {
                streaming -> ChunkyButton("", onStop, Modifier.size(width = 48.dp, height = 52.dp), height = 48.dp, radius = 14.dp, icon = Ic.square, iconSize = 18.dp,
                    face = ui.card, lip = ui.hairline, content = ui.ink, border = ui.hairline)
                showSend -> ChunkyButton("", onSend, Modifier.size(width = 48.dp, height = 52.dp), height = 48.dp, radius = 14.dp, icon = Ic.arrowUp, iconSize = 22.dp)
                listening -> Box(Modifier.scale(pulse)) {
                    ChunkyButton("", onMic, Modifier.size(width = 48.dp, height = 52.dp), height = 48.dp, radius = 14.dp, icon = Ic.square, iconSize = 18.dp)
                }
                else -> ChunkyButton("", onMic, Modifier.size(width = 48.dp, height = 52.dp), height = 48.dp, radius = 14.dp, icon = Ic.mic, iconSize = 22.dp,
                    face = ui.card, lip = ui.hairline, content = ui.ink, border = ui.hairline, enabled = !transcribing)
            }
        }
    }
}

/** Miniatura de una foto (para el mensaje y la barra), sin cargar la imagen entera. */
private fun thumb(bytes: ByteArray): ImageBitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var s = 1
    while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 360) s *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
}.getOrNull()
