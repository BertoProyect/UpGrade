package com.alberto.upgrade.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Habit
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.SCHEDULE_DAILY
import com.alberto.upgrade.data.SCHEDULE_DAYS
import com.alberto.upgrade.data.SCHEDULE_WEEKLY
import com.alberto.upgrade.data.activeHabits
import com.alberto.upgrade.data.habitDone
import com.alberto.upgrade.data.habitDueToday
import com.alberto.upgrade.data.habitScheduledOn
import com.alberto.upgrade.data.habitSummary
import com.alberto.upgrade.data.habitVariantOn
import com.alberto.upgrade.data.habitWeekCount
import com.alberto.upgrade.data.habitWeekTarget
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.nextVariant
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.CheckCircle
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.LocalReduceMotion
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.RollingNumber
import com.alberto.upgrade.ui.Segmented
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.Stepper
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.UpMotion
import com.alberto.upgrade.ui.UpTextField
import com.alberto.upgrade.ui.habitIcon
import com.alberto.upgrade.ui.habitIcons
import com.alberto.upgrade.ui.motion
import com.alberto.upgrade.ui.reveal
import java.time.DayOfWeek
import java.time.LocalDate

private val letters = listOf("L", "M", "X", "J", "V", "S", "D")

/**
 * Hábitos: lo que repites (gym, leer, fisio…). Arriba, cuántos de hoy llevas; luego los que tocan hoy
 * con su botón; abajo, el resto de la semana. Tocar uno abre su ficha para cambiar días, veces, XP u opciones.
 */
@Composable
fun HabitsScreen(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val habits = activeHabits(data)
    val due = habits.filter { habitDueToday(data, it, today) }
    val rest = habits.filterNot { habitDueToday(data, it, today) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp)) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Hábitos", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { creating = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(Ic.plus, ui.accentInk, size = 18.dp)
                    Text("NUEVO", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (habits.isEmpty()) {
            item(key = "empty") { EmptyHabits(Modifier.reveal()) { creating = true } }
        } else {
            item(key = "summary") { TodaySummary(data, due, today, Modifier.reveal(0)) }
            if (due.isNotEmpty()) {
                item(key = "due-h") { Header("Toca hoy", "${due.count { habitDone(data, it, today) }} de ${due.size}") }
                items(due, key = { "d-" + it.id }) { h ->
                    HabitCard(data, h, today, actions, Modifier.animateItem().reveal(due.indexOf(h) + 1, once = true)) { editing = h }
                }
            }
            if (rest.isNotEmpty()) {
                item(key = "rest-h") { Header("Resto de la semana", null) }
                items(rest, key = { "r-" + it.id }) { h ->
                    HabitCard(data, h, today, actions, Modifier.animateItem().reveal(due.size + rest.indexOf(h) + 1, once = true)) { editing = h }
                }
            }
            item(key = "foot") {
                Text(
                    "Los hábitos de todos los días y los de días fijos cuentan para la racha esos días. Los de «X veces por semana», no: los haces cuando quieras.",
                    color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }

    if (creating || editing != null) {
        HabitSheet(editing, onDismiss = { creating = false; editing = null })
    }
}

@Composable
private fun Header(title: String, trailing: String?) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

/** Cuántos de hoy llevas, con barra. Todo hecho: frase serif. */
@Composable
private fun TodaySummary(data: AppData, due: List<Habit>, today: LocalDate, modifier: Modifier) {
    val ui = LocalUi.current
    val done = due.count { habitDone(data, it, today) }
    val required = due.filter { habitScheduledOn(it, today) }
    val requiredDone = required.count { habitDone(data, it, today) }
    ChunkyCard(modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                com.alberto.upgrade.ui.Eyebrow("Hoy")
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    RollingNumber(done) { v -> Text("$v", color = if (due.isNotEmpty() && v >= due.size) ui.accentInk else ui.ink, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 36.sp) }
                    Text(" de ${due.size}", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            val all = due.isNotEmpty() && done >= due.size
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (all) ui.selTint else ui.surface)
                    .border(2.dp, if (all) ui.accentInk else ui.hairline, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) { UIcon(if (all) Ic.circleCheck else Ic.listChecks, if (all) ui.accentInk else ui.muted, size = 26.dp) }
        }
        ProgressBar(if (due.isEmpty()) 0f else done / due.size.toFloat(), ui.primary, Modifier.fillMaxWidth().padding(top = 12.dp), height = 12.dp)
        Text(
            when {
                due.isEmpty() -> "Hoy no toca ninguno. Si te apetece, adelanta uno de la semana."
                done >= due.size -> "Todo hecho hoy. Así se construye la racha."
                required.isNotEmpty() && requiredDone < required.size -> "Para la racha te faltan ${required.size - requiredDone}: " + required.filterNot { habitDone(data, it, today) }.joinToString(", ") { it.name } + "."
                else -> "Los de la racha están hechos. Lo demás suma XP."
            },
            color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun EmptyHabits(modifier: Modifier, onNew: () -> Unit) {
    val ui = LocalUi.current
    Column(modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
            UIcon(Ic.listChecks, ui.muted, size = 34.dp)
        }
        SerifText("¿Qué quieres repetir cada semana?", fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Gym, leer, estirar, el fisio… Eliges qué días o cuántas veces, y lo marcas con un toque.",
            color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
        )
        ChunkyButton("Crear hábito", onNew, Modifier.fillMaxWidth().padding(top = 18.dp), icon = Ic.plus, iconSize = 18.dp)
    }
}

/**
 * Ficha de un hábito: icono, nombre, meta y frecuencia; la semana en siete puntos; y el botón.
 * Con opciones (Torso / Pierna) salen como botones y la que toca va primero en lima.
 */
@Composable
private fun HabitCard(data: AppData, h: Habit, today: LocalDate, actions: AppActions, modifier: Modifier, onEdit: () -> Unit) {
    val ui = LocalUi.current
    val done = habitDone(data, h, today)
    val variant = habitVariantOn(data, h, today)
    val count = habitWeekCount(data, h, today)
    val target = habitWeekTarget(h)
    val reduce = LocalReduceMotion.current
    // Al marcarlo, la ficha da un pequeño pulso.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done && !reduce) {
            pulse.animateTo(1.025f, tween(110))
            pulse.animateTo(1f, spring(0.55f, 900f))
        }
    }
    val edge by animateColorAsState(if (done) ui.accentInk else ui.hairline, motion(tween(220)), label = "hedge")
    ChunkyCard(
        modifier.fillMaxWidth().padding(bottom = 10.dp).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
        edge = edge, lip = if (done) ui.primaryLip.copy(alpha = 0.6f) else ui.hairline,
        tint = if (done) ui.selTint else null, onClick = onEdit, padding = 14.dp, radius = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
                UIcon(habitIcon(h.icon), if (done) ui.accentInk else ui.ink, size = 22.dp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(h.name, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(h.goal.takeIf { it.isNotBlank() }, habitSummary(h), if (h.xp > 0) "+${h.xp} XP" else null).joinToString(" · "),
                    color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(count) { v -> Text("$v", color = if (v >= target) ui.accentInk else ui.ink, fontWeight = FontWeight.Black, fontSize = 18.sp) }
                    Text("/$target", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text("semana", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
        WeekDots(data, h, today, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(12.dp))
        val inFade = motion(tween<Float>(200))
        val inScale = motion(tween<Float>(220))
        val outFade = motion(tween<Float>(120))
        androidx.compose.animation.AnimatedContent(
            targetState = done,
            transitionSpec = {
                (fadeIn(inFade) + androidx.compose.animation.scaleIn(inScale, initialScale = 0.94f)) togetherWith fadeOut(outFade)
            },
            label = "hbtn"
        ) { isDone ->
            if (isDone) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UIcon(Ic.circleCheck, ui.accentInk, size = 20.dp)
                    Text(
                        if (!variant.isNullOrBlank()) "$variant, hecho" else "Hecho hoy",
                        color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp).weight(1f)
                    )
                    TextAction("Deshacer", { Repo.unlogHabit(h.id) }, fontSize = 13.sp)
                }
            } else if (h.variants.isNotEmpty()) {
                val next = nextVariant(data, h)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    h.variants.take(3).forEach { v ->
                        if (v == next) ChunkyButton(v, { actions.award(Repo.logHabit(h.id, v), "¡$v hecho!") }, Modifier.weight(1f), height = 44.dp, fontSize = 14.sp, radius = 12.dp, lipH = 3.dp)
                        else GhostButton(v, { actions.award(Repo.logHabit(h.id, v), "¡$v hecho!") }, Modifier.weight(1f), height = 44.dp, fontSize = 14.sp, radius = 12.dp, lipH = 3.dp)
                    }
                }
            } else {
                ChunkyButton("Hecho", { actions.award(Repo.logHabit(h.id), "¡${h.name}, hecho!") }, Modifier.fillMaxWidth(), height = 44.dp, icon = Ic.check, fontSize = 14.sp, radius = 12.dp, lipH = 3.dp, iconSize = 16.dp)
            }
        }
    }
}

/** L–D: lleno si se hizo, aro si es hoy y falta, tenue si ese día no toca. */
@Composable
fun WeekDots(data: AppData, h: Habit, today: LocalDate, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val monday = today.with(DayOfWeek.MONDAY)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        letters.forEachIndexed { i, n ->
            val d = monday.plusDays(i.toLong())
            val hit = data.habitLog[h.id]?.containsKey(d.key()) == true
            val scheduled = h.schedule == SCHEDULE_WEEKLY || habitScheduledOn(h, d)
            val dot by animateColorAsState(if (hit) ui.primary else ui.surface, motion(tween(200)), label = "wdot")
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(dot)
                    .then(if (d == today && !hit) Modifier.border(2.dp, ui.strong, CircleShape) else Modifier)
                    .graphicsLayer { alpha = if (scheduled || hit) 1f else 0.4f },
                contentAlignment = Alignment.Center
            ) {
                if (hit) UIcon(Ic.check, ui.onPrimary, size = 15.dp)
                else Text(n, color = if (d == today) ui.ink else ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}

// ───────────────────────── Ficha de edición ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HabitSheet(editing: Habit?, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var icon by remember { mutableStateOf(editing?.icon ?: "book-open") }
    var schedule by remember { mutableStateOf(editing?.schedule ?: SCHEDULE_DAILY) }
    var days by remember { mutableStateOf(editing?.days?.takeIf { editing.schedule == SCHEDULE_DAYS } ?: listOf(1, 3, 5)) }
    var perWeek by remember { mutableStateOf(editing?.perWeek ?: 3) }
    var xp by remember { mutableStateOf(editing?.xp ?: 10) }
    var goal by remember { mutableStateOf(editing?.goal ?: "") }
    var variants by remember { mutableStateOf(editing?.variants ?: emptyList()) }
    var newVariant by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = ui.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(ui.strong)) }
    ) {
        Column(Modifier.padding(horizontal = 18.dp).imePadding().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (editing == null) "Nuevo hábito" else "Editar hábito", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
                IconTap(Ic.x, onDismiss)
            }

            Label("Nombre")
            Field(name, "Ej: Leer, Estirar, Agua…") { name = it.take(32) }

            Label("Icono")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                habitIcons.forEach { (key, res) ->
                    val sel = key == icon
                    val s = RoundedCornerShape(12.dp)
                    val bg by animateColorAsState(if (sel) ui.selTint else ui.bg, motion(tween(180)), label = "hib")
                    val ed by animateColorAsState(if (sel) ui.accentInk else ui.hairline, motion(tween(180)), label = "hie")
                    Box(
                        Modifier.size(46.dp).clip(s).background(bg).border(2.dp, ed, s).clickable { icon = key },
                        contentAlignment = Alignment.Center
                    ) { UIcon(res, if (sel) ui.accentInk else ui.ink, size = 22.dp) }
                }
            }

            Label("¿Cuándo?")
            Segmented(
                listOf(Triple(SCHEDULE_DAILY, "Diario", null), Triple(SCHEDULE_DAYS, "Días fijos", null), Triple(SCHEDULE_WEEKLY, "Por semana", null)),
                schedule
            ) { schedule = it }
            AnimatedVisibility(schedule == SCHEDULE_DAYS, enter = expandVertically(motion(UpMotion.state())) + fadeIn(), exit = shrinkVertically(motion(UpMotion.state())) + fadeOut()) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    letters.forEachIndexed { i, l ->
                        val d = i + 1
                        val sel = d in days
                        SelectChip(l, sel, { days = if (sel) (days - d).ifEmpty { listOf(d) } else (days + d).sorted() }, Modifier.weight(1f), center = true)
                    }
                }
            }
            AnimatedVisibility(schedule == SCHEDULE_WEEKLY, enter = expandVertically(motion(UpMotion.state())) + fadeIn(), exit = shrinkVertically(motion(UpMotion.state())) + fadeOut()) {
                Stepper("Veces por semana", "$perWeek", { perWeek = (perWeek - 1).coerceAtLeast(1) }, { perWeek = (perWeek + 1).coerceAtMost(7) }, Modifier.padding(top = 8.dp))
            }
            Text(
                when (schedule) {
                    SCHEDULE_DAILY -> "Cuenta para la racha todos los días."
                    SCHEDULE_DAYS -> "Cuenta para la racha los días marcados."
                    else -> "No cuenta para la racha: lo haces cuando quieras, $perWeek " + (if (perWeek == 1) "vez" else "veces") + " por semana."
                },
                color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)
            )

            Label("Meta (opcional)")
            Field(goal, "Ej: 20 min, 10 páginas, 2 litros") { goal = it.take(30) }

            Label("Opciones al marcarlo (opcional)")
            Text("Para elegir qué hiciste: Torso / Pierna, Correr / Bici… Sin opciones, un solo botón «Hecho».", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                variants.forEach { v -> SelectChip(v, true, { variants = variants - v }, icon = Ic.x) }
            }
            if (variants.size < 3) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Field(newVariant, "Añadir opción") { newVariant = it.take(16) } }
                    Spacer(Modifier.width(8.dp))
                    GhostButton("", {
                        val v = newVariant.trim()
                        if (v.isNotEmpty() && v !in variants) variants = variants + v
                        newVariant = ""
                    }, Modifier.size(52.dp, 56.dp), height = 52.dp, icon = Ic.plus, iconSize = 20.dp)
                }
            }

            Label("XP al hacerlo")
            Stepper("XP", "$xp", { xp = (xp - 5).coerceAtLeast(0) }, { xp = (xp + 5).coerceAtMost(50) })

            Spacer(Modifier.height(18.dp))
            ChunkyButton(if (editing == null) "Crear hábito" else "Guardar", {
                val h = (editing ?: Habit(Repo.newId(), "")).copy(
                    name = name.trim(), icon = icon, schedule = schedule, days = if (schedule == SCHEDULE_DAYS) days else (1..7).toList(),
                    perWeek = perWeek, xp = xp, goal = goal.trim(), variants = variants
                )
                Repo.saveHabit(h)
                onDismiss()
            }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
            if (editing != null) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextAction("Subir", { Repo.moveHabit(editing.id, -1) }, fontSize = 13.sp)
                    TextAction("Bajar", { Repo.moveHabit(editing.id, 1) }, fontSize = 13.sp)
                    TextAction("Borrar", { confirmDelete = true }, color = ui.danger, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar «${editing.name}»?", fontWeight = FontWeight.Black) },
            text = { Text("Desaparece de la lista y de la racha. El XP que te dio se queda.") },
            confirmButton = { TextButton({ Repo.deleteHabit(editing.id); confirmDelete = false; onDismiss() }) { Text("Borrar", color = ui.danger, fontWeight = FontWeight.Black) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancelar", color = ui.ink, fontWeight = FontWeight.Bold) } },
            containerColor = ui.card
        )
    }
}

@Composable
private fun Label(text: String) {
    val ui = LocalUi.current
    Text(text, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun Field(value: String, hint: String, onChange: (String) -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(ui.bg).border(2.dp, ui.hairline, shape).padding(14.dp)) {
        if (value.isEmpty()) Text(hint, color = ui.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        UpTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ui.ink),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Tarjeta compacta de hábitos para Hoy: los que tocan hoy, cada uno con su botón. Abre la pestaña Hábitos. */
@Composable
fun HabitsTodayCard(data: AppData, actions: AppActions, openHabits: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val due = activeHabits(data).filter { habitDueToday(data, it, today) }
    Column {
        com.alberto.upgrade.ui.SectionTitle("Hábitos de hoy") {
            TextAction("Ver", openHabits, color = ui.accentInk, fontSize = 13.sp)
        }
        if (due.isEmpty()) {
            com.alberto.upgrade.ui.EmptyNote(if (activeHabits(data).isEmpty()) "Aún no tienes hábitos. Créalos en la pestaña Hábitos." else "Hoy no toca ningún hábito.")
            return@Column
        }
        ChunkyCard(padding = 0.dp) {
            due.forEachIndexed { i, h ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(ui.hairline))
                val done = habitDone(data, h, today)
                val v = habitVariantOn(data, h, today)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
                        UIcon(habitIcon(h.icon), if (done) ui.accentInk else ui.ink, size = 19.dp)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(h.name, color = if (done) ui.muted else ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (done) (v?.takeIf { it.isNotBlank() }?.let { "$it, hecho" } ?: "Hecho") else
                                listOfNotNull(h.goal.takeIf { it.isNotBlank() }, "${habitWeekCount(data, h, today)}/${habitWeekTarget(h)} esta semana").joinToString(" · "),
                            color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!done && h.xp > 0) Text("+${h.xp} XP", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
                    if (h.variants.isNotEmpty() && !done) {
                        val next = nextVariant(data, h) ?: h.variants.first()
                        ChunkyButton(next, { actions.award(Repo.logHabit(h.id, next), "¡$next hecho!") }, Modifier.width(96.dp), height = 38.dp, fontSize = 12.sp, radius = 12.dp, lipH = 3.dp)
                    } else {
                        CheckCircle(done, { if (done) Repo.unlogHabit(h.id) else actions.award(Repo.logHabit(h.id), "¡${h.name}, hecho!") }, size = 32.dp)
                    }
                }
            }
        }
    }
}
