package com.alberto.upgrade.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AiError
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Gemini
import com.alberto.upgrade.data.HabitFact
import com.alberto.upgrade.data.Habits
import com.alberto.upgrade.data.Insight
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.SecretStore
import com.alberto.upgrade.data.Tone
import com.alberto.upgrade.data.pendingExams
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.ListCard
import com.alberto.upgrade.ui.ListDivider
import com.alberto.upgrade.ui.LocalReduceMotion
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.PendingFocusPane
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.UpMotion
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Un análisis tiene validez una semana; pasado eso se rehace solo al abrir la pantalla. */
private const val STALE_MS = 7L * 24 * 60 * 60 * 1000

/**
 * «Tus hábitos»: patrones calculados en el móvil (siempre) y, con clave de Gemini,
 * un análisis con recomendaciones accionables que puedes probar o descartar.
 */
@Composable
fun InsightsScreen(data: AppData, actions: AppActions, onClose: () -> Unit) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val stats = remember(data.xpLog, data.tasks, data.usageDays, data.focusLog, data.routineLog, data.insights, data.insightHistory, data.coachNotes) {
        Habits.analyze(data, today)
    }
    val hasKey = remember { SecretStore.hasGeminiKey() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPrompt by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }
    val report = data.insights
    if (editNotes) NotesSheet(data.coachNotes) { editNotes = false }

    fun analyze() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                Repo.saveInsights(Gemini.coach(Repo.data, Habits.analyze(Repo.data, LocalDate.now())))
            } catch (e: AiError) {
                error = e.message
            } catch (e: Exception) {
                error = "No se ha podido hacer el análisis. Prueba otra vez."
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (hasKey && report != null && System.currentTimeMillis() - report.at > STALE_MS) analyze()
    }

    fun act(i: Insight) {
        when (i.action) {
            "foco" -> { onClose(); actions.goTab(com.alberto.upgrade.ui.TAB_FOCUS) }
            "rutina" -> { PendingFocusPane.value = "routines"; onClose(); actions.goTab(com.alberto.upgrade.ui.TAB_FOCUS) }
            "apps" -> actions.openOverlay("apps")
            "anadir" -> { onClose(); actions.openAdd(null, null, null) }
            "meta" -> { onClose(); actions.goTab(com.alberto.upgrade.ui.TAB_TODAY); com.alberto.upgrade.ui.PendingSheet.value = "day" }
            "cerrar_dia" -> actions.openOverlay("review")
            "habitos" -> { onClose(); actions.goTab(com.alberto.upgrade.ui.TAB_HABITS) }
            "repaso" -> {
                val exam = pendingExams(Repo.data, LocalDate.now(), 14).firstOrNull { (ex, _) -> Repo.data.tasks.none { it.examId == ex.id } }
                    ?: pendingExams(Repo.data, LocalDate.now(), 14).firstOrNull()
                if (exam != null) actions.openOverlay("plan:" + exam.first.id) else { onClose(); actions.goTab(com.alberto.upgrade.ui.TAB_CALENDAR) }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Cómo usas la app", onClose) {
            if (hasKey && report != null && !loading) IconTap(Ic.rotateCcw, { analyze() }, tint = ui.ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 40.dp)) {
            val reduce = LocalReduceMotion.current
            val phase = when {
                loading -> "loading"
                report != null -> "report"
                else -> "intro"
            }
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(if (reduce) 0 else 220)) togetherWith fadeOut(tween(if (reduce) 0 else 150)) },
                label = "insights"
            ) { ph ->
                Column(Modifier.fillMaxWidth()) {
                    when (ph) {
                        "loading" -> Loading(stats.days)
                        "report" -> report?.let { Report(it.summary, it.at, it.days, it.items, ::act) }
                        else -> Intro(hasKey, stats.days, ::analyze) { actions.openOverlay("settings") }
                    }
                }
            }
            error?.let {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(ui.dangerTint)
                        .border(2.dp, ui.danger, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(Ic.alert, ui.danger, size = 20.dp)
                    Text(it, color = ui.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    TextAction("Reintentar", { analyze() }, color = ui.danger, fontSize = 13.sp)
                }
            }

            CoachNotes(data.coachNotes) { editNotes = true }

            SectionTitle("Lo que dicen tus datos")
            Text(
                "Últimos ${stats.days} ${if (stats.days == 1) "día" else "días"}. Se calcula en el móvil, sin IA.",
                color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp)
            )
            if (stats.facts.isEmpty()) {
                com.alberto.upgrade.ui.EmptyNote("Todavía no hay datos suficientes. Usa la app unos días y vuelve.")
            } else {
                ListCard {
                    stats.facts.forEachIndexed { i, f ->
                        if (i > 0) ListDivider()
                        FactRow(f)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Todo se queda en el móvil. A Gemini solo va este resumen, y solo cuando pides el análisis.",
                color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp
            )
            TextAction(if (showPrompt) "Ocultar el resumen" else "Ver lo que se envía", { showPrompt = !showPrompt }, modifier = Modifier.padding(top = 4.dp))
            AnimatedVisibility(
                showPrompt,
                enter = if (reduce) fadeIn(tween(0)) else expandVertically(UpMotion.state()) + fadeIn(UpMotion.fade()),
                exit = if (reduce) fadeOut(tween(0)) else shrinkVertically(UpMotion.state()) + fadeOut(UpMotion.fade(120))
            ) {
                SelectionContainer {
                    Text(
                        stats.prompt,
                        color = ui.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(ui.surface)
                            .border(2.dp, ui.hairline, RoundedCornerShape(12.dp)).padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Intro(hasKey: Boolean, days: Int, onRun: () -> Unit, onSettings: () -> Unit) {
    val ui = LocalUi.current
    SerifText("¿Dónde se te va el tiempo?", fontSize = 28.sp, modifier = Modifier.padding(top = 8.dp))
    Text(
        "La IA cruza tus tareas, el foco, las rutinas y cómo usas el móvil en los últimos $days días, y te dice qué cambiar esta semana. Con datos, sin frases bonitas.",
        color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
    )
    if (hasKey) {
        ChunkyButton("Analizar mis hábitos", onRun, Modifier.fillMaxWidth(), icon = Ic.sparkles)
    } else {
        ChunkyCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIcon(Ic.key, ui.muted, size = 20.dp)
                Text("Falta la clave de Gemini", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp))
            }
            Text(
                "Los patrones de abajo funcionan sin ella. Para las recomendaciones, pega tu clave gratuita en Ajustes › IA.",
                color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            GhostButton("Ir a Ajustes", onSettings, Modifier.fillMaxWidth(), height = 44.dp, fontSize = 13.sp, icon = Ic.settings)
        }
    }
}

@Composable
private fun Loading(days: Int) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingDots()
        Text(
            "Leyendo tus últimos $days días…",
            color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun Report(summary: String, at: Long, days: Int, items: List<Insight>, onAct: (Insight) -> Unit) {
    val ui = LocalUi.current
    Eyebrow("Análisis · ${ago(at)} · $days días", modifier = Modifier.padding(top = 4.dp))
    if (summary.isNotBlank()) SerifText(summary, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
    val visible = items.filter { it.status != "dismissed" }
    visible.forEachIndexed { i, it ->
        Spacer(Modifier.height(if (i == 0) 12.dp else 10.dp))
        InsightCard(it, first = i == 0, onAct = { onAct(it) })
    }
    if (items.isEmpty()) {
        Text(
            "Nada nuevo que merezca la pena esta semana. Lo que ya descartaste no vuelve a salir.",
            color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp)
        )
    }
    val hidden = items.size - visible.size
    if (hidden > 0) {
        Text(
            if (hidden == 1) "1 recomendación descartada." else "$hidden recomendaciones descartadas.",
            color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)
        )
    }
}

private val actionLabels = mapOf(
    "foco" to ("Empezar foco" to Ic.target),
    "rutina" to ("Ver rutinas" to Ic.calendarClock),
    "apps" to ("Control de apps" to Ic.shieldBan),
    "anadir" to ("Apuntar" to Ic.plus),
    "meta" to ("Meta del día" to Ic.sliders),
    "cerrar_dia" to ("Cerrar el día" to Ic.moon),
    "repaso" to ("Plan de repaso" to Ic.bookOpen),
    "habitos" to ("Ver hábitos" to Ic.listChecks)
)

/** Por qué descartas una recomendación: la IA lo lee la próxima vez y no vuelve con lo mismo. */
private val dismissReasons = listOf("Ya lo sé", "No me encaja", "No me importa", "Datos mal")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InsightCard(i: Insight, first: Boolean, onAct: () -> Unit) {
    val ui = LocalUi.current
    val trying = i.status == "trying"
    var askWhy by remember(i.id) { mutableStateOf(false) }
    ChunkyCard(Modifier.fillMaxWidth(), edge = if (trying) ui.accentInk else ui.hairline, tint = if (trying) ui.selTint else null) {
        if (first || trying) {
            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                UIcon(if (trying) Ic.check else Ic.lightbulb, ui.accentInk, size = 15.dp)
                Eyebrow(if (trying) "Lo estás probando" else "Primero esto", color = ui.accentInk, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Text(i.title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, lineHeight = 22.sp)
        if (i.detail.isNotBlank()) {
            Text(i.detail, color = ui.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (i.evidence.isNotBlank()) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                UIcon(Ic.chart, ui.muted, size = 15.dp, modifier = Modifier.padding(top = 2.dp))
                Text(i.evidence, color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            actionLabels[i.action]?.let { (label, icon) ->
                GhostButton(label, onAct, Modifier.weight(1f, fill = false), height = 40.dp, fontSize = 12.sp, radius = 12.dp, icon = icon, iconSize = 16.dp)
            }
            Spacer(Modifier.weight(1f))
            if (trying) {
                TextAction("Dejarlo", { Repo.setInsightStatus(i.id, "new") }, fontSize = 12.sp)
            } else if (!askWhy) {
                TextAction("Descartar", { askWhy = true }, fontSize = 12.sp)
                TextAction("Lo pruebo", { Repo.setInsightStatus(i.id, "trying") }, color = ui.accentInk, fontSize = 12.sp)
            } else {
                TextAction("Cancelar", { askWhy = false }, fontSize = 12.sp)
            }
        }
        AnimatedVisibility(askWhy) {
            Column(Modifier.padding(top = 10.dp)) {
                Text("¿Por qué?", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dismissReasons.forEach { r ->
                        com.alberto.upgrade.ui.SelectChip(r, false, { Repo.setInsightStatus(i.id, "dismissed", r) })
                    }
                }
            }
        }
    }
}

private val factIcons = mapOf(
    "target" to Ic.target, "calendarDays" to Ic.calendarDays, "hourglass" to Ic.hourglass, "clock" to Ic.clock,
    "alert" to Ic.alert, "fileText" to Ic.fileText, "timer" to Ic.timer, "repeat" to Ic.repeat, "dumbbell" to Ic.dumbbell,
    "shieldBan" to Ic.shieldBan, "phone" to Ic.phone, "moon" to Ic.moon
)

@Composable
private fun FactRow(f: HabitFact) {
    val ui = LocalUi.current
    // Rojo solo para lo urgente (atrasadas); el resto en gris y tinta, como las áreas.
    val urgent = f.icon == "alert" || f.icon == "fileText"
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.Top) {
        UIcon(factIcons[f.icon] ?: Ic.activity, if (urgent) ui.danger else ui.muted, size = 20.dp, modifier = Modifier.padding(top = 1.dp, end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            if (f.note.isNotBlank()) Text(f.note, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            f.value,
            color = if (urgent) ui.danger else if (f.tone == Tone.GOOD) ui.accentInk else ui.ink,
            fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp)
        )
    }
}

private fun ago(at: Long): String {
    val min = (System.currentTimeMillis() - at) / 60_000L
    return when {
        min < 2 -> "ahora"
        min < 60 -> "hace $min min"
        min < 24 * 60 -> "hace ${min / 60} h"
        min < 48 * 60 -> "ayer"
        else -> "hace ${min / (24 * 60)} días"
    }
}

/** Lo que la IA debe saber de ti: una idea por línea. Lo lee en cada análisis y no te propone lo contrario. */
@Composable
private fun CoachNotes(notes: String, onEdit: () -> Unit) {
    val ui = LocalUi.current
    val lines = notes.lines().map { it.trim().removePrefix("-").trim() }.filter { it.isNotBlank() }
    SectionTitle("Lo que la IA sabe de ti") {
        com.alberto.upgrade.ui.PencilTap(onEdit, label = "Editar lo que la IA sabe de ti")
    }
    if (lines.isEmpty()) {
        com.alberto.upgrade.ui.EmptyNote("Nada todavía. Cuéntale por qué fallan las cosas que fallan y dejará de proponerte lo obvio.")
    } else {
        ListCard {
            lines.forEachIndexed { i, l ->
                if (i > 0) ListDivider()
                Text(l, color = ui.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun NotesSheet(initial: String, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    var text by remember { mutableStateOf(initial) }
    com.alberto.upgrade.ui.SectionSheet("Lo que la IA sabe de ti", "Una idea por línea: por qué falla algo, qué no es prioridad, cuándo puedes.", { Repo.setCoachNotes(text); onDismiss() }) {
        com.alberto.upgrade.ui.UpTextField(
            text, { text = it },
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ui.card).border(2.dp, ui.hairline, RoundedCornerShape(14.dp)).padding(14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = ui.ink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.alberto.upgrade.ui.Fonts.Sans),
            minLines = 6
        )
        Spacer(Modifier.height(14.dp))
        ChunkyButton("Guardar", { Repo.setCoachNotes(text); onDismiss() }, Modifier.fillMaxWidth())
    }
}
