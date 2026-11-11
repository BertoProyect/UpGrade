package com.alberto.upgrade.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AiError
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Gemini
import com.alberto.upgrade.data.Kind
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.SecretStore
import com.alberto.upgrade.data.StudySlot
import com.alberto.upgrade.data.TaskItem
import com.alberto.upgrade.data.dayName
import com.alberto.upgrade.data.dueLabel
import com.alberto.upgrade.data.eventsOn
import com.alberto.upgrade.data.classesOn
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.nowMin
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.ui.CheckCircle
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.Stepper
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UIcon
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.alberto.upgrade.data.Topic
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.UpTextField
import com.alberto.upgrade.ui.reveal

private class SlotUi(slot: StudySlot, mins: Int, topic: String?) {
    var date by mutableStateOf(slot.date)
    var time by mutableIntStateOf(slot.time)
    var minutes by mutableIntStateOf(mins)
    var topicId by mutableStateOf(topic)
    var keep by mutableStateOf(true)
}

/**
 * Plan de repaso de un examen. Si aún no tiene sesiones, se crean (cuántas y de cuánto). Si ya tiene,
 * se ve el progreso: temas que dominas, sesiones hechas, la próxima; cada sesión con su duración y su tema.
 */
@Composable
fun StudyPlanScreen(data: AppData, examId: String, onClose: () -> Unit) {
    val ui = LocalUi.current
    val exam = data.tasks.firstOrNull { it.id == examId }
    val examDate = parseDate(exam?.due)
    val sessions = data.tasks.filter { it.examId == examId }.sortedWith(compareBy({ it.due }, { it.time ?: 0 }))
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (adding) "Añadir sesiones" else "Plan de repaso", { if (adding && sessions.isNotEmpty()) adding = false else onClose() }, close = !adding || sessions.isEmpty())
        if (exam == null || examDate == null) {
            Column(Modifier.padding(16.dp)) {
                SerifText("Este examen no tiene fecha.", fontSize = 24.sp)
                Text(
                    "Ponle una fecha para poder repartir el repaso hasta ese día.",
                    color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            androidx.compose.animation.AnimatedContent(
                targetState = adding || sessions.isEmpty(),
                modifier = Modifier.weight(1f),
                transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
                label = "plan"
            ) { creating ->
                if (creating) PlanCreator(data, exam, examDate, onDone = { adding = false; if (Repo.data.tasks.none { it.examId == examId }) onClose() })
                else PlanOverview(data, exam, examDate, sessions, onAdd = { adding = true })
            }
        }
    }
}

// ───────────────────────── Vista del plan ─────────────────────────

@Composable
private fun PlanOverview(data: AppData, exam: TaskItem, examDate: LocalDate, sessions: List<TaskItem>, onAdd: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val topics = exam.topics
    val done = sessions.count { it.done }
    val minsDone = sessions.filter { it.done }.sumOf { it.minutes ?: 0 }
    val minsTotal = sessions.sumOf { it.minutes ?: 0 }
    val subject = data.subject(exam.subjectId)?.name
    val left = com.alberto.upgrade.data.daysUntil(examDate, today)
    var newTopic by remember { mutableStateOf("") }

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item(key = "title") {
            Column(Modifier.reveal(0)) {
                SerifText(subject ?: exam.title, fontSize = 26.sp, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Examen ${dueLabel(examDate, today).lowercase()}" + (exam.time?.let { " a las " + minStr(it) } ?: "") +
                        " · " + when { left <= 0L -> "es hoy"; left == 1L -> "queda 1 día"; else -> "quedan $left días" },
                    color = if (left <= 2) ui.danger else ui.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        item(key = "progress") {
            ChunkyCard(Modifier.fillMaxWidth().padding(top = 16.dp).reveal(1)) {
                ProgressRow("Sesiones", done, sessions.size, "$minsDone de $minsTotal min")
                if (topics.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ProgressRow("Temas que dominas", topics.count { it.done }, topics.size, null)
                }
                val next = sessions.firstOrNull { !it.done }
                Text(
                    when {
                        next == null -> "Todas las sesiones hechas. Si aún flojeas en algún tema, añade otra."
                        else -> "Próxima: " + dueLabel(parseDate(next.due), today) + (next.time?.let { " a las " + minStr(it) } ?: "") + " · ${next.minutes ?: 60} min" +
                            (topics.firstOrNull { it.id == next.topicId }?.let { " · ${it.name}" } ?: "")
                    },
                    color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        // ── Temas ──
        item(key = "topics-h") { PlanHeader("Temas", if (topics.isEmpty()) null else "${topics.count { it.done }}/${topics.size}") }
        if (topics.isEmpty()) {
            item(key = "topics-empty") {
                Text(
                    "Apunta los temas que entran. Cada sesión repasa uno y aquí marcas los que ya dominas.",
                    color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        items(topics, key = { "tp-" + it.id }) { tp ->
            val count = sessions.count { it.topicId == tp.id }
            val doneCount = sessions.count { it.topicId == tp.id && it.done }
            Row(Modifier.fillMaxWidth().animateItem().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(tp.done, { Repo.toggleTopic(exam.id, tp.id) })
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        tp.name, color = if (tp.done) ui.muted else ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                        textDecoration = if (tp.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (count == 0) "Sin sesión asignada" else "$doneCount de $count " + if (count == 1) "sesión" else "sesiones",
                        color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                    )
                }
                MiniTap(Ic.x) {
                    Repo.setTopics(exam.id, topics.filterNot { it.id == tp.id })
                    sessions.filter { it.topicId == tp.id }.forEach { Repo.saveTask(it.copy(topicId = null)) }
                }
            }
        }
        item(key = "topics-add") {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(12.dp)
                Box(Modifier.weight(1f).clip(shape).background(ui.bg).border(2.dp, ui.hairline, shape).padding(14.dp)) {
                    if (newTopic.isEmpty()) Text("Ej: Tema 3 · La Restauración", color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    UpTextField(
                        newTopic, { newTopic = it.take(60).replace("\n", " ") }, singleLine = true,
                        textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ui.ink),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                GhostButton("", {
                    val n = newTopic.trim()
                    if (n.isNotEmpty()) {
                        Repo.setTopics(exam.id, topics + Topic(Repo.newId(), n))
                        newTopic = ""
                    }
                }, Modifier.size(52.dp, 56.dp), height = 52.dp, icon = Ic.plus, iconSize = 20.dp)
            }
        }

        // ── Sesiones ──
        item(key = "ses-h") { PlanHeader("Sesiones", "$done/${sessions.size}") }
        items(sessions, key = { "s-" + it.id }) { t ->
            SessionCard(data, exam, t, today, Modifier.animateItem().reveal(0, once = true)) {
                Repo.startRepaso(ctx, t)
                com.alberto.upgrade.MainActivity.pendingOpen.value = "focus"
            }
        }
        item(key = "add") {
            GhostButton("Añadir sesiones", onAdd, Modifier.fillMaxWidth().padding(top = 8.dp), icon = Ic.plus, iconSize = 18.dp)
            Text(
                "Cada sesión es un foco: al terminarlo queda hecha y te da su XP. Las ves en Hoy y en el calendario.",
                color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun PlanHeader(title: String, trailing: String?) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 19.sp, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun ProgressRow(label: String, done: Int, total: Int, extra: String?) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        com.alberto.upgrade.ui.RollingNumber(done) { v -> Text("$v", color = if (total > 0 && v >= total) ui.accentInk else ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp) }
        Text("/$total", color = ui.muted, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
    ProgressBar(if (total == 0) 0f else done / total.toFloat(), ui.primary, Modifier.fillMaxWidth().padding(top = 6.dp), height = 10.dp)
    if (extra != null) Text(extra, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

/** Una sesión ya creada: cuándo, cuánto (se puede cambiar) y qué tema (toca para cambiarlo). */
@Composable
private fun SessionCard(data: AppData, exam: TaskItem, t: TaskItem, today: LocalDate, modifier: Modifier, onStart: () -> Unit) {
    val ui = LocalUi.current
    val due = parseDate(t.due)
    val overdue = !t.done && due != null && due.isBefore(today)
    val topics = exam.topics
    val topic = topics.firstOrNull { it.id == t.topicId }
    val mins = t.minutes ?: 60
    var confirm by remember { mutableStateOf(false) }
    ChunkyCard(
        modifier.fillMaxWidth().padding(bottom = 10.dp), padding = 0.dp, radius = 16.dp,
        edge = if (t.done) ui.accentInk else if (overdue) ui.danger else ui.hairline,
        tint = if (t.done) ui.selTint else null
    ) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            UIcon(if (t.done) Ic.circleCheck else Ic.bookOpen, if (t.done) ui.accentInk else if (overdue) ui.danger else ui.muted, size = 20.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    (due?.let { "${dayName(it).replaceFirstChar { c -> c.uppercase() }} ${it.dayOfMonth}" } ?: "Sin fecha") + (t.time?.let { " · " + minStr(it) } ?: ""),
                    color = if (overdue) ui.danger else ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp
                )
                Text(
                    if (t.done) "Hecha" else if (overdue) "Atrasada: muévela o hazla ya" else dueLabel(due, today),
                    color = if (overdue) ui.danger else ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                )
            }
            if (!t.done) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(ui.primary).clickable(onClick = onStart).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(Ic.play, ui.onPrimary, size = 14.dp)
                    Text("EMPEZAR", color = ui.onPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
                }
            }
            MiniTap(Ic.trash) { confirm = true }
        }
        Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!t.done) {
                MiniTap(Ic.minus) { Repo.saveTask(t.copy(minutes = (mins - 15).coerceAtLeast(15))) }
            }
            com.alberto.upgrade.ui.RollingNumber(mins) { v -> Text("$v min", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            if (!t.done) {
                MiniTap(Ic.plus) { Repo.saveTask(t.copy(minutes = (mins + 15).coerceAtMost(180))) }
            }
            Spacer(Modifier.weight(1f))
            if (topics.isNotEmpty()) {
                // Toca para pasar al siguiente tema (o a ninguno).
                val order = listOf<String?>(null) + topics.map { it.id }
                com.alberto.upgrade.ui.SelectChip(
                    topic?.name ?: "Sin tema", topic != null,
                    { Repo.saveTask(t.copy(topicId = order[(order.indexOf(t.topicId) + 1) % order.size])) },
                    Modifier.widthIn(max = 180.dp), icon = Ic.bookOpen
                )
            }
        }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Quitar esta sesión?", fontWeight = FontWeight.Black) },
            text = { Text(if (t.done) "Ya la hiciste: el XP del foco se queda." else "Desaparece del plan, de Hoy y del calendario.") },
            confirmButton = { androidx.compose.material3.TextButton({ Repo.deleteTask(t.id); confirm = false }) { Text("Quitar", color = ui.danger, fontWeight = FontWeight.Black) } },
            dismissButton = { androidx.compose.material3.TextButton({ confirm = false }) { Text("Cancelar", color = ui.ink, fontWeight = FontWeight.Bold) } },
            containerColor = ui.card
        )
    }
}

// ───────────────────────── Crear sesiones ─────────────────────────

@Composable
private fun PlanCreator(data: AppData, exam: TaskItem, examDate: LocalDate, onDone: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val days = Gemini.studyDays(today, examDate)
    var sessions by remember { mutableIntStateOf(4) }
    var minutes by remember { mutableIntStateOf(60) }
    val slots = remember { mutableStateListOf<SlotUi>() }
    var generated by remember { mutableStateOf(false) }
    val topics = exam.topics.filter { !it.done }.ifEmpty { exam.topics }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val subject = data.subject(exam.subjectId)?.name
            SerifText(subject ?: exam.title, fontSize = 26.sp, modifier = Modifier.padding(top = 6.dp))
            Text(
                "Examen ${dueLabel(examDate, today).lowercase()}" + (exam.time?.let { " a las " + minStr(it) } ?: "") +
                    " · ${days.size} " + if (days.size == 1) "día para repasar" else "días para repasar",
                color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)
            )

            if (days.isEmpty()) {
                SerifText("Ya no quedan días antes del examen.", fontSize = 22.sp, modifier = Modifier.padding(top = 20.dp))
                Text("Haz un foco ahora con lo que más te cueste: cada minuto cuenta.", color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }

            if (days.isNotEmpty() && !generated) {
                Spacer(Modifier.height(18.dp))
                Stepper("Sesiones", "$sessions", { sessions = (sessions - 1).coerceAtLeast(1) }, { sessions = (sessions + 1).coerceAtMost(minOf(14, days.size * 2)) })
                Text("Duración de cada sesión", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 45, 60, 90).forEach { m ->
                        SelectChip("$m′", minutes == m, { minutes = m }, Modifier.weight(1f), center = true)
                    }
                }
                Stepper("Otra duración", "$minutes min", { minutes = (minutes - 5).coerceAtLeast(15) }, { minutes = (minutes + 5).coerceAtMost(180) }, Modifier.padding(top = 6.dp))
                Text(
                    "Luego puedes cambiar la duración de cada sesión por separado." +
                        if (topics.isNotEmpty()) " Los temas que aún no dominas se reparten entre las sesiones." else " Si apuntas los temas, se reparten entre las sesiones.",
                    color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (generated) {
                Spacer(Modifier.height(12.dp))
                slots.forEachIndexed { i, s ->
                    ChunkyCard(Modifier.fillMaxWidth().padding(bottom = 10.dp).reveal(i), padding = 0.dp, radius = 16.dp) {
                        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckCircle(s.keep, { s.keep = !s.keep })
                            Text(
                                "${dayName(s.date).replaceFirstChar { it.uppercase() }} ${s.date.dayOfMonth} · ${minStr(s.time)}",
                                color = if (s.keep) ui.ink else ui.muted, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp)
                            )
                            MiniTap(Ic.chevronLeft) { val n = s.date.minusDays(1); if (n in days) s.date = n }
                            MiniTap(Ic.chevronRight) { val n = s.date.plusDays(1); if (n in days) s.date = n }
                        }
                        Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Hora", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                            MiniTap(Ic.minus) { s.time = (s.time - 15).coerceAtLeast(7 * 60) }
                            MiniTap(Ic.plus) { s.time = (s.time + 15).coerceAtMost(22 * 60) }
                            Spacer(Modifier.width(6.dp))
                            MiniTap(Ic.minus) { s.minutes = (s.minutes - 15).coerceAtLeast(15) }
                            Text("${s.minutes} min", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            MiniTap(Ic.plus) { s.minutes = (s.minutes + 15).coerceAtMost(180) }
                            Spacer(Modifier.weight(1f))
                            topics.firstOrNull { it.id == s.topicId }?.let { tp ->
                                Text(tp.name, color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
                            }
                        }
                    }
                }
                TextAction("Cambiar número o duración", { generated = false; slots.clear() }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            if (days.isNotEmpty() && !generated) {
                ChunkyButton("Organizar", {
                    // Reparto en el móvil, al momento: después de clase, sin pisar lo que ya tienes.
                    val plan = localPlan(Repo.data, days, sessions, minutes)
                    slots.clear()
                    plan.forEachIndexed { i, sl -> slots += SlotUi(sl, minutes, topics.getOrNull(i % topics.size.coerceAtLeast(1))?.id) }
                    generated = true
                }, Modifier.fillMaxWidth())
            } else if (generated) {
                val n = slots.count { it.keep }
                ChunkyButton("Añadir plan · $n " + if (n == 1) "sesión" else "sesiones", {
                    val title = "Repasar " + (data.subject(exam.subjectId)?.name ?: exam.title)
                    val tasks = slots.filter { it.keep }.sortedWith(compareBy({ it.date }, { it.time })).map { s ->
                        TaskItem(
                            id = Repo.newId(),
                            title = title + (topics.firstOrNull { it.id == s.topicId }?.let { " · ${it.name}" } ?: ""),
                            kind = Kind.REPASO,
                            areaId = exam.areaId,
                            subjectId = exam.subjectId,
                            due = s.date.key(),
                            time = s.time,
                            createdAt = System.currentTimeMillis(),
                            examId = exam.id,
                            minutes = s.minutes,
                            topicId = s.topicId
                        )
                    }
                    Repo.addTasks(tasks)
                    Toast.makeText(ctx, "Plan añadido. Lo tienes en Hoy y en el calendario.", Toast.LENGTH_SHORT).show()
                    onDone()
                }, Modifier.fillMaxWidth(), enabled = n > 0)
            }
        }
    }
}

@Composable
private fun MiniTap(icon: Int, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        UIcon(icon, ui.muted, size = 18.dp)
    }
}

/** Reparto sin IA: días espaciados hasta el examen, después de clase o por la mañana el fin de semana. */
internal fun localPlan(d: AppData, days: List<LocalDate>, sessions: Int, minutes: Int): List<StudySlot> {
    if (days.isEmpty()) return emptyList()
    val today = LocalDate.now()
    val out = mutableListOf<StudySlot>()
    // Más sesiones cerca del examen: se recorren los días desde el final.
    val order = days.reversed()
    var round = 0
    while (out.size < sessions && round < 2) {
        val step = if (round == 0) maxOf(1, order.size / sessions.coerceAtLeast(1)) else 1
        var i = 0
        while (i < order.size && out.size < sessions) {
            val day = order[i]
            val taken = out.filter { it.date == day }
            if (taken.size <= round) {
                val start = baseTime(d, day) + taken.size * (minutes + 30)
                val time = freeFrom(d, day, start, minutes)
                val okToday = day != today || time >= nowMin() + 30
                if (time + minutes <= 22 * 60 && okToday) out += StudySlot(day, time)
            }
            i += step
        }
        round++
    }
    return out.sortedWith(compareBy({ it.date }, { it.time }))
}

private fun baseTime(d: AppData, day: LocalDate): Int {
    val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
    if (weekend) return 10 * 60 + 30
    val b = d.block
    if (b.enabled && b.scheduleEnabled && day.dayOfWeek.value in b.days) return b.from
    val classEnd = classesOn(d, day).lastOrNull()?.period?.end ?: (14 * 60)
    return maxOf(17 * 60, classEnd + 120)
}

/** Empuja la hora hasta que no choque con eventos ni tareas con hora. */
private fun freeFrom(d: AppData, day: LocalDate, start: Int, minutes: Int): Int {
    val busy = eventsOn(d, day).map { it.start to it.end } +
        d.tasks.filter { !it.done && it.due == day.key() }.mapNotNull { t -> t.time?.let { tm -> tm to tm + (t.minutes ?: 30) } }
    var t = start
    repeat(12) {
        val clash = busy.firstOrNull { (s, e) -> t < e && t + minutes > s } ?: return t
        t = clash.second + 15
    }
    return t
}
