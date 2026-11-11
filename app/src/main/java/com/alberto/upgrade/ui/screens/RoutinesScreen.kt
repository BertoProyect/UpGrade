@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.alberto.upgrade.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.Routine
import com.alberto.upgrade.data.RoutineRun
import com.alberto.upgrade.data.activeRuns
import com.alberto.upgrade.data.dayName
import com.alberto.upgrade.data.durationLabel
import com.alberto.upgrade.data.effective
import com.alberto.upgrade.data.isTightening
import com.alberto.upgrade.data.minRoutineMinutes
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.minutesOf
import com.alberto.upgrade.data.overlapOn
import com.alberto.upgrade.data.routineSummary
import com.alberto.upgrade.data.routineXp
import com.alberto.upgrade.data.routinesOn
import com.alberto.upgrade.data.runOf
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.Segmented
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.SmallRound
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.UToggle
import com.alberto.upgrade.ui.UpMotion
import com.alberto.upgrade.ui.iconByKey
import com.alberto.upgrade.ui.motion
import kotlinx.coroutines.delay
import java.time.LocalDate

private val routineIcons = listOf("sunrise", "book-open", "moon", "coffee", "dumbbell", "smartphone")

/** Rutinas: franjas que bloquean según el Control de apps, con su racha y su XP. */
@Composable
fun RoutinesPane(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    var editing by remember { mutableStateOf<Routine?>(null) }
    var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val todays = routinesOn(data, today)
    val running = activeRuns(data, now)

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Hoy, ${dayName(today)}", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(if (todays.size == 1) "1 rutina" else "${todays.size} rutinas", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        DayTimeline(data, todays, now)
        val overlaps = buildList {
            for (i in todays.indices) for (j in i + 1 until todays.size) {
                overlapOn(todays[i], todays[j])?.let { add(Triple(todays[i], todays[j], it)) }
            }
        }
        overlaps.forEach { (_, _, o) ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                UIcon(Ic.alert, ui.muted, size = 15.dp)
                Text("${minStr(o.first)}–${minStr(o.second)}: se pisan. Durante el solape cuentan las dos.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }

        running.forEach { run ->
            data.routines.firstOrNull { it.id == run.routineId }?.let { r -> RunningCard(r, run, now) }
        }

        Spacer(Modifier.height(14.dp))
        data.routines.forEach { r ->
            RoutineRow(data, r, now, Modifier.padding(bottom = 10.dp)) { editing = r }
        }
        GhostButton("Nueva rutina", { creating = true }, Modifier.fillMaxWidth().padding(top = 2.dp), icon = Ic.plus, iconSize = 18.dp)
        Spacer(Modifier.height(18.dp))
        ControlAppsCard(data, actions)
    }

    if (creating || editing != null) {
        RoutineSheet(data, editing, now, onDismiss = { creating = false; editing = null }) { msg ->
            if (msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Línea de 0 a 24 h con las rutinas de hoy; la que está en curso, en lima. */
@Composable
private fun DayTimeline(data: AppData, todays: List<Routine>, now: Long) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(ui.surface)) {
        val w = maxWidth
        todays.forEach { r ->
            val run = runOf(data, r.id, today)
            val start = run?.let { minutesOf(it.startedAt) } ?: r.start
            val len = run?.let { ((it.endAt - it.startedAt) / 60_000L).toInt() } ?: r.lengthFrom(r.start)
            val live = run != null && run.status == "running" && now < run.endAt
            val done = run?.status == "done"
            Box(
                Modifier.offset(x = w * (start / 1440f)).width((w * (len.coerceAtMost(1440 - start) / 1440f)).coerceAtLeast(4.dp)).fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (live) ui.primary else if (done) ui.primaryLip.copy(alpha = 0.6f) else ui.strong)
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        listOf("0", "6", "12", "18", "24").forEachIndexed { i, t ->
            Text(t, color = ui.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = if (i < 4) Modifier.weight(1f) else Modifier)
        }
    }
}

@Composable
private fun RunningCard(r: Routine, run: RoutineRun, now: Long) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val elapsed = now - run.startedAt
    val canCancel = elapsed < 5 * 60_000L
    ChunkyCard(Modifier.fillMaxWidth().padding(top = 14.dp), edge = ui.accentInk, lip = ui.primaryLip, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ui.selTint), contentAlignment = Alignment.Center) {
                UIcon(iconByKey(r.icon) ?: Ic.calendarClock, ui.accentInk, size = 22.dp)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("EN CURSO", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp)
                Text(r.name, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
        Text("hasta las ${minStr(minutesOf(run.endAt))}", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 30.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Empezó a las ${minStr(minutesOf(run.startedAt))}" + (if (r.startMode == "unlock") ", al desbloquear" else "") + ". Bloquea según el Control de apps.",
            color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
        )
        AnimatedVisibility(canCancel, enter = fadeIn(), exit = shrinkVertically(motion(UpMotion.screen())) + fadeOut(tween(150))) {
            Column(Modifier.padding(top = 12.dp)) {
                val left = ((5 * 60_000L - elapsed) / 1000).coerceAtLeast(0)
                GhostButton("Cancelar (quedan %d:%02d)".format(left / 60, left % 60), {
                    if (!Repo.cancelRoutine(r.id)) Toast.makeText(ctx, "Ya no se puede cancelar.", Toast.LENGTH_SHORT).show()
                }, Modifier.fillMaxWidth(), height = 44.dp, fontSize = 13.sp)
                Text("Si cancelas, hoy no cuenta: sin XP ni racha.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun RoutineRow(data: AppData, r: Routine, now: Long, modifier: Modifier, onOpen: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val run = runOf(data, r.id, today)
    val live = run != null && run.status == "running" && now >= run.startedAt && now < run.endAt
    val eff = r.effective(today)
    val scheduledToday = eff != null && eff.enabled && today.dayOfWeek.value in eff.days
    val status = when {
        r.deleteFrom != null -> "Se borra mañana"
        r.pending != null -> if (r.pending.enabled) "Cambio desde mañana" else "Se apaga mañana"
        live -> "En curso · hasta las ${minStr(minutesOf(run!!.endAt))}"
        run?.status == "done" -> "Hecha hoy" + if (run.xp > 0) " · +${run.xp} XP" else ""
        run?.status == "cancelled" -> "Cancelada hoy"
        else -> null
    }
    ChunkyCard(modifier.fillMaxWidth(), onClick = onOpen, padding = 0.dp, radius = 16.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
                UIcon(iconByKey(r.icon) ?: Ic.calendarClock, if (r.enabled) ui.ink else ui.muted, size = 22.dp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, color = if (r.enabled) ui.ink else ui.muted, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    // Ya no hay racha por rutina: cuenta para la racha total si toca hoy.
                    if (scheduledToday && run?.status != "done") {
                        Text(" · racha", color = ui.flame, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
                Text(routineSummary(r), color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (status != null) Text(status, color = if (live) ui.accentInk else ui.text, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            if (live) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(ui.surface).padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(Ic.lock, ui.ink, size = 13.dp)
                    Text("EN CURSO", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                }
            } else {
                UToggle(r.pending?.enabled ?: r.enabled) { on ->
                    val immediate = Repo.setRoutineEnabled(r.id, on)
                    if (!immediate) Toast.makeText(ctx, "Apagar afloja la rutina: se aplicará mañana.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/** Hoja para crear o editar una rutina. En curso, todo en modo lectura con candado. */
@Composable
private fun RoutineSheet(data: AppData, editing: Routine?, now: Long, onDismiss: () -> Unit, onMessage: (String?) -> Unit) {
    val ui = LocalUi.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val base = editing?.let { it.pending?.copy(id = it.id) ?: it }
    val run = editing?.let { e -> data.routineLog.lastOrNull { it.routineId == e.id && it.status == "running" && now >= it.startedAt && now < it.endAt } }
    val locked = run != null

    var name by remember { mutableStateOf(base?.name ?: "") }
    var icon by remember { mutableStateOf(base?.icon ?: "book-open") }
    var days by remember { mutableStateOf(base?.days ?: listOf(1, 2, 3, 4, 5)) }
    var startMode by remember { mutableStateOf(base?.startMode ?: "fixed") }
    var start by remember { mutableIntStateOf(base?.start ?: (17 * 60)) }
    var endMode by remember { mutableStateOf(base?.endMode ?: "duration") }
    var end by remember { mutableIntStateOf(base?.end ?: (19 * 60)) }
    var duration by remember { mutableIntStateOf(base?.duration ?: 60) }
    var xp by remember { mutableStateOf(base?.xp) }

    val type = base?.type ?: "custom"
    val draft = Routine(
        id = editing?.id ?: ("r-" + Repo.newId().take(8)), name = name.trim(), icon = icon, type = type, days = days.sorted(),
        startMode = startMode, start = start, endMode = if (startMode == "unlock") "duration" else endMode, end = end,
        duration = duration, enabled = base?.enabled ?: true, xp = if (type == "wake") null else xp
    )
    val minutes = draft.nominal
    val gives = routineXp(data, draft, minutes)
    val loosens = editing != null && !isTightening(editing.copy(name = draft.name, icon = draft.icon, xp = draft.xp, pending = null, pendingFrom = null), draft)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = ui.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(ui.strong)) }
    ) {
        Column(Modifier.padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconTap(Ic.x, onDismiss)
                Text(
                    if (editing == null) "Nueva rutina" else if (locked) name else "Editar rutina",
                    color = ui.ink, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (locked && run != null) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(ui.surface).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UIcon(Ic.lock, ui.ink, size = 16.dp)
                        Text("En curso · termina a las ${minStr(minutesOf(run.endAt))}", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Text("Hasta entonces no se puede editar, desactivar ni cancelar.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Column(Modifier.alpha(if (locked) 0.45f else 1f)) {
                Label2("Nombre", locked)
                val shape = RoundedCornerShape(12.dp)
                Box(Modifier.fillMaxWidth().clip(shape).background(ui.bg).border(2.dp, ui.hairline, shape).padding(14.dp)) {
                    if (name.isEmpty()) Text("Ej: Leer sin móvil", color = ui.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    com.alberto.upgrade.ui.UpTextField(
                        name, { if (!locked) name = it.take(40) }, singleLine = true, enabled = !locked,
                        textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ui.ink),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Label2("Icono", locked)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    routineIcons.forEach { k ->
                        val sel = k == icon
                        val s = RoundedCornerShape(12.dp)
                        Box(
                            Modifier.size(46.dp).clip(s).background(if (sel) ui.selTint else ui.bg)
                                .border(2.dp, if (sel) ui.accentInk else ui.hairline, s).clickable(enabled = !locked) { icon = k },
                            contentAlignment = Alignment.Center
                        ) { UIcon(iconByKey(k) ?: Ic.calendarClock, if (sel) ui.accentInk else ui.ink, size = 22.dp) }
                    }
                }
                Label2("Días", locked)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, n ->
                        val d = i + 1
                        SelectChip(n, d in days, { if (!locked) days = if (d in days) days - d else (days + d).sorted() }, Modifier.weight(1f), center = true)
                    }
                }
                Label2("Empieza", locked)
                Segmented(listOf(Triple("fixed", "A una hora fija", null), Triple("unlock", "Al primer desbloqueo", null)), startMode) {
                    if (!locked) { startMode = it; if (it == "unlock" && start > 12 * 60) start = 5 * 60 }
                }
                if (startMode == "fixed") {
                    TimeRow("Empieza a las", start, locked) { start = it }
                } else {
                    TimeRow("A partir de las", start, locked) { start = it.coerceIn(3 * 60, 12 * 60) }
                    Text("La primera vez que desbloqueas el móvil a partir de esa hora.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                Label2("Termina", locked)
                if (startMode == "fixed") {
                    Segmented(listOf(Triple("fixed", "Hora fija", null), Triple("duration", "Duración", null)), endMode) { if (!locked) endMode = it }
                }
                if (startMode == "fixed" && endMode == "fixed") {
                    TimeRow("Termina a las", end, locked) { end = it }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(durationLabel(duration), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
                        SmallRound("", { if (!locked) duration = (duration - 15).coerceAtLeast(15) }, icon = Ic.minus)
                        Spacer(Modifier.width(10.dp))
                        SmallRound("", { if (!locked) duration = (duration + 15).coerceAtMost(240) }, icon = Ic.plus)
                    }
                    Text("De 15 min a 4 h", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }

                AnimatedVisibility(loosens && !locked, enter = expandVertically(motion(UpMotion.screen())) + fadeIn(tween(200)), exit = shrinkVertically(motion(UpMotion.screen())) + fadeOut(tween(150))) {
                    Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(12.dp)).background(ui.surface).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UIcon(Ic.calendarClock, ui.ink, size = 16.dp)
                            Text("Este cambio se aplicará mañana", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                        Text("Afloja la rutina (menos tiempo, menos días o más tarde). Hoy sigue como estaba.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    UIcon(Ic.zapFill, if (gives > 0) ui.accentInk else ui.muted, size = 16.dp)
                    if (gives > 0) {
                        Text("Da $gives XP", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp).weight(1f))
                        if (type != "wake") {
                            SmallRound("", { if (!locked) xp = ((xp ?: data.xp.routine) - 5).coerceAtLeast(0) }, icon = Ic.minus)
                            Text("${xp ?: data.xp.routine}", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 10.dp))
                            SmallRound("", { if (!locked) xp = ((xp ?: data.xp.routine) + 5).coerceAtMost(100) }, icon = Ic.plus)
                        } else Text("si dura 60 min o más", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    } else {
                        Text("No da XP: mínimo ${minRoutineMinutes(draft)} min", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    UIcon(Ic.shieldBan, ui.muted, size = 16.dp)
                    Text("Bloquea según las listas del Control de apps.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            if (!locked) {
                ChunkyButton(
                    when { editing == null -> "Crear rutina"; loosens -> "Guardar · desde mañana"; else -> "Guardar" },
                    {
                        val immediate = Repo.saveRoutine(draft)
                        onMessage(if (!immediate) "Se aplicará mañana." else null)
                        onDismiss()
                    },
                    Modifier.fillMaxWidth(), enabled = name.isNotBlank() && days.isNotEmpty()
                )
                if (editing != null) {
                    TextAction("Borrar rutina", {
                        Repo.deleteRoutine(editing.id)
                        onMessage(if (editing.enabled) "Se borrará mañana." else null)
                        onDismiss()
                    }, Modifier.fillMaxWidth().padding(top = 6.dp), color = ui.danger)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Label2(text: String, locked: Boolean) {
    val ui = LocalUi.current
    Row(Modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp)
        if (locked) UIcon(Ic.lock, ui.muted, size = 12.dp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun TimeRow(label: String, value: Int, locked: Boolean, onChange: (Int) -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        UIcon(Ic.clock, ui.muted, size = 18.dp)
        Text("$label ${minStr(value)}", color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
        SmallRound("", { if (!locked) onChange(((value - 15) + 1440) % 1440) }, icon = Ic.minus)
        Spacer(Modifier.width(10.dp))
        SmallRound("", { if (!locked) onChange((value + 15) % 1440) }, icon = Ic.plus)
    }
}
