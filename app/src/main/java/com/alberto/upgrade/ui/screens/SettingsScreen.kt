@file:OptIn(ExperimentalLayoutApi::class)

package com.alberto.upgrade.ui.screens

import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.ListCard
import com.alberto.upgrade.ui.ListDivider
import com.alberto.upgrade.ui.ListRow
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.UToggle
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import com.alberto.upgrade.data.DEFAULT_AI_MODEL
import com.alberto.upgrade.data.Gemini
import com.alberto.upgrade.data.KeyCheck
import com.alberto.upgrade.data.SecretStore
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.alberto.upgrade.data.ClassSlot
import com.alberto.upgrade.data.Period
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.notify.AlarmScheduler
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.SectionTitle
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.Stepper
import java.time.LocalDate


private val settingsTabs = listOf("general" to "General", "avisos" to "Avisos", "ia" to "IA", "datos" to "Datos", "uso" to "Uso de la app")

/**
 * Ajustes en pestañas que se deslizan en horizontal: General · Avisos · IA · Datos · Uso de la app.
 * Lo que pertenece a una sección (meta, foco, rutinas, hábitos) ya no está aquí: se edita en su sección,
 * con el lápiz de la esquina. General tiene atajos a cada uno.
 */
@Composable
fun SettingsScreen(data: AppData, actions: AppActions, initialTab: String = "general", onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var editSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scope = rememberCoroutineScope()
    val pager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = settingsTabs.indexOfFirst { it.first == initialTab }.coerceAtLeast(0)
    ) { settingsTabs.size }
    androidx.compose.runtime.LaunchedEffect(pager.currentPage) {
        com.alberto.upgrade.data.Telemetry.screen("Ajustes · " + settingsTabs[pager.currentPage].second)
    }

    fun upd(f: (AppData) -> AppData) {
        Repo.update(f)
        AlarmScheduler.scheduleAll(ctx)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportJson().toByteArray()) }
                Repo.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
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

    var open by remember { mutableStateOf<String?>(null) }
    fun toggle(k: String) { open = if (open == k) null else k }

    /** Cierra Ajustes y abre el sitio donde se edita algo (con su hoja de ajustes, si la tiene). */
    fun goTo(tab: Int, sheet: String? = null, pane: String? = null) {
        onClose()
        pane?.let { com.alberto.upgrade.ui.PendingFocusPane.value = it }
        actions.goTab(tab)
        sheet?.let { com.alberto.upgrade.ui.PendingSheet.value = it }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ajustes", onClose)
        SettingsTabs(pager.currentPage + pager.currentPageOffsetFraction) { i -> scope.launch { pager.animateScrollToPage(i) } }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { settingsTabs[it].first }) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp)) {
                when (settingsTabs[page].first) {
                    "general" -> {
                        Group("Tema")
                        com.alberto.upgrade.ui.Segmented(
                            listOf(Triple("light", "Claro", Ic.sun), Triple("dark", "Oscuro", Ic.moon), Triple("system", "Sistema", Ic.phone)),
                            data.themeMode
                        ) { m -> upd { it.copy(themeMode = m) } }

                        // Sin accesos repetidos: cada sección se ajusta con su lápiz (meta en Hoy, XP y bloqueo en Foco,
                        // rutinas y hábitos en su ficha) y Permisos vive en Perfil.
                        Group("Actualizaciones")
                        UpdatesCard(data)

                        val version = remember {
                            runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
                        }
                        Text(
                            "UpGrade $version", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                        )
                    }

                    "avisos" -> {
                        Group("Notificaciones")
                        ListCard {
                            NotifRow("Plan del día", "morning", data.notif.morning, open, { toggle(it) }) { v -> upd { it.copy(notif = it.notif.copy(morning = v)) } }
                            ListDivider()
                            NotifRow("Exámenes y deberes", "afternoon", data.notif.afternoon, open, { toggle(it) }) { v -> upd { it.copy(notif = it.notif.copy(afternoon = v)) } }
                            ListDivider()
                            NotifRow("Racha en peligro", "streak", data.notif.streak, open, { toggle(it) }) { v -> upd { it.copy(notif = it.notif.copy(streak = v)) } }
                            ListDivider()
                            NotifRow("Cierra el día", "review", data.notif.review, open, { toggle(it) }) { v -> upd { it.copy(notif = it.notif.copy(review = v)) } }
                        }
                        Group("En Hoy")
                        ListCard {
                            NotifRow("Tarjeta «Cierra el día» desde", "reviewCard", data.notif.reviewCard, open, { toggle(it) }) { v -> upd { it.copy(notif = it.notif.copy(reviewCard = v)) } }
                        }
                    }

                    "ia" -> {
                        Group("Gemini")
                        AiSettings(data, open) { toggle(it) }
                        Group("Dictado")
                        com.alberto.upgrade.ui.Segmented(
                            listOf(Triple("live", "Mientras hablas", Ic.mic), Triple("precise", "Preciso", Ic.sparkles)),
                            data.dictation
                        ) { m -> upd { it.copy(dictation = m) } }
                        Text(
                            if (data.dictation == "live") "El texto sale según hablas y la IA empieza a trabajar en cada pausa: la respuesta llega casi al soltar el botón."
                            else "Gemini transcribe al parar, con tu vocabulario. Entiende mejor los nombres raros, pero tarda unos segundos más.",
                            color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, start = 2.dp)
                        )
                    }

                    "datos" -> {
                        Group("Horario")
                        ListCard {
                            ListRow("Editar horario", icon = Ic.pencil, onClick = { toggle("horario") }, chevron = false)
                            if (open == "horario") Inline {
                                Text("Toca una casilla para cambiar la asignatura.", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                TimetableGrid(data) { day, period -> editSlot = day to period }
                                Spacer(Modifier.height(10.dp))
                                data.periods.forEachIndexed { i, p ->
                                    Stepper("${i + 1}ª hora (hasta ${minStr(p.end)})", minStr(p.start),
                                        { upd { d -> d.copy(periods = d.periods.mapIndexed { j, q -> if (j == i) Period(q.start - 5, q.end - 5) else q }) } },
                                        { upd { d -> d.copy(periods = d.periods.mapIndexed { j, q -> if (j == i) Period(q.start + 5, q.end + 5) else q }) } })
                                }
                            }
                        }
                        Group("Copia de seguridad")
                        ListCard {
                            val last = data.lastBackupAt
                            ListRow(
                                "Copia de seguridad", icon = Ic.backup,
                                subtitle = if (last == 0L) "Nunca. Tus datos solo viven en este móvil." else "Última: " + backupLabel(last),
                                onClick = { toggle("copia") }, chevron = false
                            )
                            if (open == "copia") Inline {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GhostButton("Exportar", { exportLauncher.launch("upgrade-${LocalDate.now()}.json") }, Modifier.weight(1f), height = 44.dp, fontSize = 13.sp)
                                    GhostButton("Restaurar", { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f), height = 44.dp, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    else -> UsageTab(data, actions)
                }
            }
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
                        })
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
}

/**
 * Pestañas de Ajustes en una fila que se desliza. La píldora lima sigue al dedo al pasar de una a otra
 * y la fila se mueve sola para que la elegida quede a la vista.
 */
@Composable
private fun SettingsTabs(position: Float, onSelect: (Int) -> Unit) {
    val ui = LocalUi.current
    val scroll = rememberScrollState()
    val selected = kotlin.math.round(position).toInt().coerceIn(0, settingsTabs.size - 1)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsets = remember { IntArray(settingsTabs.size) }
    androidx.compose.runtime.LaunchedEffect(selected) {
        val target = (offsets[selected] - with(density) { 24.dp.roundToPx() }).coerceAtLeast(0)
        scroll.animateScrollTo(target)
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        settingsTabs.forEachIndexed { i, (_, label) ->
            val sel = i == selected
            val shape = RoundedCornerShape(12.dp)
            val bg by androidx.compose.animation.animateColorAsState(if (sel) ui.selTint else ui.card, com.alberto.upgrade.ui.motion(androidx.compose.animation.core.tween(180)), label = "stbg")
            val edge by androidx.compose.animation.animateColorAsState(if (sel) ui.accentInk else ui.hairline, com.alberto.upgrade.ui.motion(androidx.compose.animation.core.tween(180)), label = "stedge")
            val fg by androidx.compose.animation.animateColorAsState(if (sel) ui.accentInk else ui.ink, com.alberto.upgrade.ui.motion(androidx.compose.animation.core.tween(180)), label = "stfg")
            Text(
                label, color = fg, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1,
                modifier = Modifier
                    .onGloballyPositionedX { offsets[i] = it }
                    .clip(shape).background(bg).border(2.dp, edge, shape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

private fun Modifier.onGloballyPositionedX(onX: (Int) -> Unit): Modifier =
    this.then(androidx.compose.ui.Modifier.onGloballyPositioned { c -> onX(c.positionInParent().x.toInt()) })

/** Atajo a donde se edita algo, con el lápiz a la derecha. */
@Composable
private fun ShortcutRow(icon: Int, title: String, where: String, onClick: () -> Unit) {
    val ui = LocalUi.current
    ListRow(title, icon = icon, subtitle = where, onClick = onClick, chevron = false) {
        com.alberto.upgrade.ui.UIcon(Ic.pencil, ui.muted, size = 16.dp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Clave cifrada, dictado, vocabulario propio y ajustes avanzados de la IA. */
@Composable
private fun AiSettings(data: AppData, open: String?, toggle: (String) -> Unit) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var savedKey by remember { mutableStateOf(SecretStore.geminiKey) }
    var check by remember { mutableStateOf<String?>(null) }
    fun upd(f: (AppData) -> AppData) { Repo.update(f) }

    ListCard {
        ListRow(
            "Clave de Gemini", icon = Ic.key,
            value = if (savedKey.isBlank()) "Sin poner" else "•••• " + savedKey.takeLast(4),
            onClick = { toggle("ia") }, chevron = false
        )
        if (open == "ia") Inline {
            Text(
                "Para «Dímelo y lo organizo» y el dictado. Pega tu clave gratuita de Gemini (aistudio.google.com → Get API key). Se guarda cifrada solo en este móvil y no entra en las copias de seguridad.",
                color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
            )
            var keyText by remember { mutableStateOf(savedKey) }
            Field(keyText, "Clave de Gemini", secret = true) { keyText = it.trim(); check = null }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Guardar", {
                    SecretStore.geminiKey = keyText
                    savedKey = keyText
                    if (keyText.isBlank()) check = null
                    else {
                        check = "Comprobando…"
                        scope.launch { check = Gemini.checkKey(keyText, Gemini.model(Repo.data)).label }
                    }
                }, Modifier.weight(1f), height = 44.dp, fontSize = 13.sp)
                GhostButton("Comprobar", {
                    check = "Comprobando…"
                    scope.launch { check = Gemini.checkKey(keyText, Gemini.model(Repo.data)).label }
                }, Modifier.weight(1f), height = 44.dp, fontSize = 13.sp)
            }
            check?.let {
                Text(
                    it, color = if (it == KeyCheck.VALID.label) ui.accentInk else ui.ink,
                    fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        ListDivider()
        ListRow(
            "Palabras que debe entender", icon = Ic.bookOpen,
            value = if (data.aiVocab.isEmpty()) "Ninguna" else "${data.aiVocab.size}",
            onClick = { toggle("vocab") }, chevron = false
        )
        if (open == "vocab") Inline {
            Text(
                "Nombres de clientes, profes, marcas… El dictado los reconoce mejor. Ya incluye tus áreas y asignaturas.",
                color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.aiVocab.forEach { w ->
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).border(2.dp, ui.hairline, RoundedCornerShape(10.dp))
                            .clickable { upd { d -> d.copy(aiVocab = d.aiVocab - w) } }
                            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(w, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        com.alberto.upgrade.ui.UIcon(Ic.x, ui.muted, size = 14.dp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            var word by remember { mutableStateOf("") }
            Field(word, "Añadir palabra") { word = it }
            Spacer(Modifier.height(8.dp))
            GhostButton("Añadir", {
                val w = word.trim()
                if (w.isNotEmpty()) upd { d -> d.copy(aiVocab = (d.aiVocab + w).distinct().take(100)) }
                word = ""
            }, Modifier.fillMaxWidth(), height = 44.dp, fontSize = 13.sp)
        }
        ListDivider()
        ListRow("Avanzado", icon = Ic.settings, onClick = { toggle("ia-adv") }, chevron = false)
        if (open == "ia-adv") Inline {
            var modelText by remember { mutableStateOf(data.aiModel) }
            Text("Modelo", color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 6.dp))
            Field(modelText, DEFAULT_AI_MODEL) { modelText = it.trim(); upd { d -> d.copy(aiModel = modelText.ifBlank { DEFAULT_AI_MODEL }) } }
            if (data.aiModel != DEFAULT_AI_MODEL) {
                com.alberto.upgrade.ui.TextAction("Volver al recomendado", { modelText = DEFAULT_AI_MODEL; upd { d -> d.copy(aiModel = DEFAULT_AI_MODEL) } })
            }
            Text("Últimas peticiones", color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            if (data.aiTimings.isEmpty()) {
                Text("Todavía ninguna.", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            data.aiTimings.asReversed().forEach { t ->
                val first = if (t.firstMs >= 0) "%.1f s".format(t.firstMs / 1000f) else "—"
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(backupLabel(t.at) + " · " + t.mode, color = ui.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        if (t.ok) "1ª $first · total %.1f s".format(t.totalMs / 1000f) else "falló",
                        color = if (t.ok) ui.muted else ui.danger, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            if (data.aiExamples.isNotEmpty()) {
                Text("Ha aprendido de ${data.aiExamples.size} correcciones tuyas.", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                com.alberto.upgrade.ui.TextAction("Olvidar correcciones", { upd { d -> d.copy(aiExamples = emptyList()) } })
            }
        }
    }
}

@Composable
private fun Group(text: String) {
    Eyebrow(text, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp))
}

/** Editor que se despliega debajo de una fila. */
@Composable
private fun Inline(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), content = content)
}

@Composable
private fun NotifRow(label: String, key: String, value: Int, open: String?, toggle: (String) -> Unit, onChange: (Int) -> Unit) {
    ListRow(label, value = minStr(value), onClick = { toggle(key) }, chevron = false)
    if (open == key) Inline {
        Stepper("Hora", minStr(value), { onChange((value - 15).coerceAtLeast(0)) }, { onChange((value + 15).coerceAtMost(23 * 60 + 45)) })
    }
}

@Composable
private fun Field(value: String, placeholder: String, secret: Boolean = false, onChange: (String) -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(ui.bg).border(2.dp, ui.hairline, shape).padding(horizontal = 12.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(placeholder, color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        val style = androidx.compose.ui.text.TextStyle(fontFamily = com.alberto.upgrade.ui.Fonts.Sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ui.ink)
        if (secret) {
            // La clave va oculta: aquí no tiene sentido seleccionar palabras.
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                textStyle = style,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(ui.primary),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            com.alberto.upgrade.ui.UpTextField(value, onChange, Modifier.fillMaxWidth(), textStyle = style, singleLine = true)
        }
    }
}

private fun backupLabel(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    val d = t.toLocalDate()
    val today = LocalDate.now()
    val day = when (d) {
        today -> "hoy"
        today.minusDays(1) -> "ayer"
        else -> "${d.dayOfMonth}/${d.monthValue}"
    }
    return "$day, ${minStr(t.hour * 60 + t.minute)}"
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
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(10.dp))
                            .background(ui.bg)
                            .border(2.dp, ui.hairline, RoundedCornerShape(10.dp))
                            .clickable { onTap(day, p) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            s?.name?.take(3) ?: "—",
                            color = if (s != null) ui.ink else ui.muted,
                            fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}


/** Actualización automática: interruptor, permiso de instalar y «Buscar ahora». */
@Composable
private fun UpdatesCard(data: AppData) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val status by com.alberto.upgrade.data.Updater.status
    var allowed by remember { mutableStateOf(com.alberto.upgrade.data.Updater.canInstall(ctx)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        allowed = com.alberto.upgrade.data.Updater.canInstall(ctx)
        onPauseOrDispose { }
    }
    ListCard {
        ListRow("Actualizar sola", icon = Ic.download, subtitle = "Descarga cada versión nueva y se instala al salir de la app", chevron = false) {
            UToggle(data.autoUpdate) { v -> Repo.update(false) { it.copy(autoUpdate = v) } }
        }
        if (!allowed) {
            ListDivider()
            ListRow(
                "Permitir que UpGrade se instale", icon = Ic.shieldCheck, valueColor = ui.danger, value = "Falta",
                subtitle = "Una sola vez: activa «Permitir de esta fuente».",
                onClick = {
                    runCatching {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + ctx.packageName)))
                    }
                }
            )
        }
        ListDivider()
        ListRow(
            "Buscar ahora", icon = Ic.refresh,
            subtitle = status.ifBlank { "Versión instalada: " + com.alberto.upgrade.data.Updater.current(ctx) },
            onClick = { com.alberto.upgrade.data.Updater.check(ctx, force = true, installNow = true) }
        )
    }
    Text(
        "La primera vez Android te pedirá confirmar la instalación. Desde ahí se actualiza sin preguntar.",
        color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, start = 2.dp)
    )
}
