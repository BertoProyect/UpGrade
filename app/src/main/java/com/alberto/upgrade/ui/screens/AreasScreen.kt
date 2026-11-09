package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import com.alberto.upgrade.data.Kind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.sectionsFor
import com.alberto.upgrade.data.taskOrder
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.EmptyNote
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.TaskRow
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.areaIcon
import java.time.LocalDate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.activity.compose.BackHandler
import com.alberto.upgrade.data.Area
import com.alberto.upgrade.data.TaskItem
import com.alberto.upgrade.data.dueLabel
import com.alberto.upgrade.data.isBuiltInArea
import com.alberto.upgrade.data.listedAreas
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UpMotion
import com.alberto.upgrade.ui.areaIcons
import com.alberto.upgrade.ui.motion


/** Áreas: todas como tarjetas; al tocar una se entra en su detalle. Se pueden crear, editar, ordenar y borrar. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AreasScreen(data: AppData, actions: AppActions) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var ordering by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Area?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Area?>(null) }
    val current = openId?.let { data.area(it) }

    BackHandler(enabled = openId != null || ordering) { if (ordering) ordering = false else openId = null }

    AnimatedContent(
        targetState = when { current != null -> "d:" + current.id; ordering -> "order"; else -> "list" },
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
        label = "areas"
    ) { st ->
        when {
            st.startsWith("d:") -> data.area(st.removePrefix("d:"))?.let { a ->
                AreaDetail(data, a, actions, onBack = { openId = null }, onEdit = { editing = a })
            }
            st == "order" -> OrderAreas(data, onDone = { ordering = false }, onDelete = { deleting = it })
            else -> AreaList(data, onOpen = { openId = it }, onOrder = { ordering = true }, onNew = { creating = true })
        }
    }

    if (creating || editing != null) {
        AreaSheet(editing, onDismiss = { creating = false; editing = null }, onDelete = { a -> editing = null; deleting = a })
    }
    deleting?.let { a ->
        val pending = data.tasks.count { it.areaId == a.id && !it.done }
        val ui = LocalUi.current
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { UIcon(areaIcon(a.id), ui.ink, size = 28.dp) },
            title = { Text("¿Borrar «${a.name}»?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    when (pending) {
                        0 -> "No tiene nada pendiente. El XP que ganaste con ella se queda."
                        1 -> "Su tarea pendiente pasa a «Otro». El XP que ganaste con ella se queda."
                        else -> "Sus $pending tareas pendientes pasan a «Otro». El XP que ganaste con ellas se queda."
                    },
                    fontWeight = FontWeight.SemiBold
                )
            },
            confirmButton = { TextButton({ Repo.deleteArea(a.id); if (openId == a.id) openId = null; deleting = null }) { Text("BORRAR", color = ui.danger, fontWeight = FontWeight.Black) } },
            dismissButton = { TextButton({ deleting = null }) { Text("CANCELAR", fontWeight = FontWeight.Black) } }
        )
    }
}

private fun nextOf(data: AppData, areaId: String): TaskItem? =
    data.tasks.filter { it.areaId == areaId && !it.done }.sortedWith(taskOrder).firstOrNull()

private fun nextLabel(t: TaskItem?): String {
    if (t == null) return "Próximo: nada pendiente"
    val d = parseDate(t.due)
    val whenTxt = d?.let { dueLabel(it).lowercase() + (t.time?.let { m -> ", " + minStr(m) } ?: "") }
    return "Próximo: " + t.title + (whenTxt?.let { " · $it" } ?: "")
}

@Composable
private fun AreaList(data: AppData, onOpen: (String) -> Unit, onOrder: () -> Unit, onNew: () -> Unit) {
    val ui = LocalUi.current
    val areas = listedAreas(data)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 150.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Áreas", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onOrder).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UIcon(Ic.arrowUpDown, ui.muted, size = 16.dp)
                Text("ORDENAR", color = ui.muted, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        areas.forEach { a ->
            AreaCard(data, a, Modifier.padding(bottom = 10.dp)) { onOpen(a.id) }
        }
        GhostButton("Nueva área", onNew, Modifier.fillMaxWidth().padding(top = 4.dp), icon = Ic.plus, iconSize = 18.dp)
    }
}

@Composable
private fun AreaCard(data: AppData, a: Area, modifier: Modifier = Modifier, preview: Boolean = false, onClick: (() -> Unit)?) {
    val ui = LocalUi.current
    val pending = if (preview) 0 else data.tasks.count { it.areaId == a.id && !it.done }
    ChunkyCard(modifier.fillMaxWidth(), onClick = onClick, padding = 0.dp, radius = 16.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
                UIcon(if (preview) (com.alberto.upgrade.ui.iconByKey(a.icon) ?: Ic.layers) else areaIcon(a.id), ui.ink, size = 22.dp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(a.name.ifBlank { "Sin nombre" }, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (preview) "Próximo: nada pendiente" else nextLabel(nextOf(data, a.id)),
                    color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text("$pending", color = if (pending > 0) ui.ink else ui.muted, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
            UIcon(Ic.chevronRight, ui.muted, size = 18.dp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun AreaDetail(data: AppData, a: Area, actions: AppActions, onBack: () -> Unit, onEdit: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    var showDone by rememberSaveable(a.id) { mutableStateOf(false) }
    val inArea = data.tasks.filter { it.areaId == a.id }
    val pending = inArea.count { !it.done }
    val sections = sectionsFor(a.id)
    val chev by animateFloatAsState(if (showDone) 180f else 0f, motion(tween(200)), label = "chev")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(Ic.arrowLeft, onBack, tint = ui.ink)
                Box(Modifier.padding(start = 4.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
                    UIcon(areaIcon(a.id), ui.ink, size = 22.dp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(a.name, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (pending == 1) "1 pendiente" else "$pending pendientes", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                IconTap(Ic.pencil, onEdit)
            }
        }
        sections.forEach { sec ->
            val list = inArea.filter { !it.done && it.kind in sec.kinds }.sortedWith(taskOrder)
            if (list.isEmpty() && sections.size > 1 && sec.title == "Llamadas" && !isBuiltInArea(a.id)) return@forEach
            item(key = "h-" + sec.title) {
                SectionTitle(sec.title) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .clickable { actions.openAdd(null, a.id, sec.kinds.firstOrNull { it != Kind.REPASO }) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UIcon(Ic.plus, ui.accentInk, size = 16.dp)
                        Text("AÑADIR", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (list.isEmpty()) {
                item(key = "e-" + sec.title) { EmptyNote("Nada pendiente.") }
            } else {
                items(list, key = { "t-" + sec.title + it.id }) { t ->
                    if (t.kind == Kind.EXAMEN) ExamLine(data, t, today) { actions.openTask(t) }
                    else TaskRow(t, data, { actions.award(Repo.toggleTask(t.id), "¡Bien hecho!") }, { actions.openTask(t) }, today, showKind = sec.kinds.size > 1 && t.kind !in setOf(Kind.DEBERES, Kind.TAREA))
                }
            }
        }

        val done = inArea.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }
        if (done.isNotEmpty()) {
            item(key = "done-h") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp))
                        .clickable { showDone = !showDone }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(Ic.chevronDown, ui.muted, size = 18.dp, modifier = Modifier.rotate(chev))
                    Text(
                        ((if (showDone) "Ocultar hechas" else "Ver hechas") + " (${done.size})").uppercase(),
                        color = ui.muted, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            item(key = "done-l") {
                AnimatedVisibility(showDone, enter = expandVertically(motion(UpMotion.screen())), exit = shrinkVertically(motion(UpMotion.screen())) + fadeOut(tween(150))) {
                    Column { done.forEach { t -> TaskRow(t, data, { Repo.toggleTask(t.id) }, { actions.openTask(t) }, today) } }
                }
            }
        }
    }
}

/** Ordenar: se arrastra por el asa; la tarjeta sube 4 dp y el labio crece a 8 dp mientras se mueve. */
@Composable
private fun OrderAreas(data: AppData, onDone: () -> Unit, onDelete: (Area) -> Unit) {
    val ui = LocalUi.current
    val areas = listedAreas(data)
    val itemH = 76.dp
    val itemPx = with(LocalDensity.current) { itemH.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 150.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ordenar áreas", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp, modifier = Modifier.weight(1f))
            TextAction("Hecho", onDone, color = ui.accentInk, fontSize = 14.sp)
        }
        Text("Arrastra por el asa para cambiar el orden.", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        areas.forEach { a -> androidx.compose.runtime.key(a.id) {
            val isDrag = dragging == a.id
            val pending = data.tasks.count { it.areaId == a.id && !it.done }
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier.fillMaxWidth().height(itemH).padding(bottom = 10.dp)
                    .offset { IntOffset(0, if (isDrag) (dragY - 4.dp.toPx()).toInt() else 0) }
            ) {
                Box(Modifier.fillMaxSize().padding(top = if (isDrag) 0.dp else 4.dp).background(if (isDrag) ui.primaryLip else ui.hairline, shape))
                Row(
                    Modifier.fillMaxWidth().height(itemH - 10.dp - if (isDrag) 8.dp else 4.dp).clip(shape).background(ui.card)
                        .border(2.dp, if (isDrag) ui.accentInk else ui.hairline, shape)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(36.dp).pointerInput(a.id) {
                            detectDragGestures(
                                onDragStart = { dragging = a.id; dragY = 0f },
                                onDragEnd = { dragging = null; dragY = 0f },
                                onDragCancel = { dragging = null; dragY = 0f }
                            ) { change, amount ->
                                change.consume()
                                dragY += amount.y
                                if (dragY > itemPx / 2) { Repo.moveArea(a.id, 1); dragY -= itemPx }
                                else if (dragY < -itemPx / 2) { Repo.moveArea(a.id, -1); dragY += itemPx }
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) { UIcon(Ic.grip, ui.muted, size = 20.dp) }
                    UIcon(areaIcon(a.id), ui.ink, size = 20.dp, modifier = Modifier.padding(start = 4.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(a.name, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (pending == 1) "1 pendiente" else "$pending pendientes", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                    if (!isBuiltInArea(a.id)) IconTap(Ic.trash, { onDelete(a) }, size = 36.dp)
                }
            }
        } }
    }
}

/** Hoja para crear o editar un área: nombre, icono, tipos y vista previa. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AreaSheet(editing: Area?, onDismiss: () -> Unit, onDelete: (Area) -> Unit) {
    val ui = LocalUi.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val builtIn = editing != null && isBuiltInArea(editing.id)
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var icon by remember { mutableStateOf(editing?.icon ?: areaIcons.first().first) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = ui.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(ui.strong)) }
    ) {
        Column(Modifier.padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (editing == null) "Nueva área" else "Editar área", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
                IconTap(Ic.x, onDismiss)
            }
            SheetLabel("Nombre")
            val shape = RoundedCornerShape(12.dp)
            Box(Modifier.fillMaxWidth().clip(shape).background(ui.bg).border(2.dp, ui.hairline, shape).padding(14.dp)) {
                if (name.isEmpty()) Text("Ej: Lectura", color = ui.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                com.alberto.upgrade.ui.UpTextField(
                    name, { name = it.take(30) }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ui.ink),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!builtIn) {
                SheetLabel("Icono")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    areaIcons.take(18).forEach { (key, res) ->
                        val sel = key == icon
                        val s = RoundedCornerShape(12.dp)
                        Box(
                            Modifier.size(46.dp).clip(s).background(if (sel) ui.selTint else ui.bg)
                                .border(2.dp, if (sel) ui.accentInk else ui.hairline, s).clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) { UIcon(res, if (sel) ui.accentInk else ui.ink, size = 22.dp) }
                    }
                }
                SheetLabel("Tipos")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Tarea", "Llamada", "Evento", "Otro").forEach { com.alberto.upgrade.ui.Tag(it) }
                }
                Text(
                    "Las áreas nuevas usan estos cuatro tipos. Todas van en gris: el icono es lo que la distingue.",
                    color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
                )
                SheetLabel("Así se verá")
                AreaCard(Repo.data, Area("preview", name, "gray", "", icon), preview = true, onClick = null)
            }
            Spacer(Modifier.height(20.dp))
            ChunkyButton(
                if (editing == null) "Crear área" else "Guardar",
                {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        Repo.saveArea(
                            editing?.let { e -> e.copy(name = n, icon = if (builtIn) e.icon else icon) }
                                ?: Area("a-" + Repo.newId().take(8), n, "gray", "", icon)
                        )
                    }
                    onDismiss()
                },
                Modifier.fillMaxWidth(), enabled = name.isNotBlank()
            )
            if (editing != null && !builtIn) {
                TextAction("Borrar área", { onDelete(editing) }, Modifier.fillMaxWidth().padding(top = 6.dp), color = ui.danger)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text.uppercase(), color = LocalUi.current.muted, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

/** Examen en Áreas: cuenta atrás en una cajita (roja si faltan ≤ 2 días). */
@Composable
private fun ExamLine(data: com.alberto.upgrade.data.AppData, t: com.alberto.upgrade.data.TaskItem, today: java.time.LocalDate, onOpen: () -> Unit) {
    val ui = LocalUi.current
    val due = com.alberto.upgrade.data.parseDate(t.due)
    val days = due?.let { com.alberto.upgrade.data.daysUntil(it, today) }
    val urgent = days != null && days <= 2
    val subject = data.subject(t.subjectId)
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.size(48.dp).clip(shape).background(if (urgent) ui.dangerTint else ui.surface)
                .border(2.dp, if (urgent) ui.danger else ui.hairline, shape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val c = if (urgent) ui.danger else ui.ink
            Text(if (days == null) "—" else if (days <= 0L) "HOY" else "$days", color = c, fontWeight = FontWeight.Black, fontSize = if (days != null && days <= 0L) 13.sp else 18.sp, lineHeight = 18.sp)
            if (days != null && days > 0) Text(if (days == 1L) "DÍA" else "DÍAS", color = if (urgent) ui.danger else ui.muted, fontWeight = FontWeight.Black, fontSize = 9.sp, lineHeight = 10.sp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                listOfNotNull(subject?.name, t.title.takeIf { subject == null || !it.equals(subject.name, true) }).joinToString(" · "),
                color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                com.alberto.upgrade.data.dueLabel(due, today) + (t.time?.let { " · ${com.alberto.upgrade.data.minStr(it)}" } ?: ""),
                color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
            )
        }
    }
}
