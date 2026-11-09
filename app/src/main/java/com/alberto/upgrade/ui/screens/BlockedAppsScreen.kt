package com.alberto.upgrade.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.alberto.upgrade.block.BlockRules
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.LIST_ALLOWED
import com.alberto.upgrade.data.LIST_BLOCKED
import com.alberto.upgrade.data.LIST_WAIT
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.activeRuns
import com.alberto.upgrade.data.appListOf
import com.alberto.upgrade.data.blockSessionActive
import com.alberto.upgrade.data.currentSession
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.minutesOf
import com.alberto.upgrade.data.nextWait
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.ListDivider
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.Segmented
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.UpMotion
import com.alberto.upgrade.ui.motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class InstalledApp(val pkg: String, val label: String, val icon: ImageBitmap?)

/** Apps instaladas que se pueden clasificar (las del sistema que nunca se bloquean no salen). */
@Composable
private fun rememberInstalledApps(): State<List<InstalledApp>?> {
    val ctx = LocalContext.current
    return produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { !BlockRules.isSystemSafe(ctx, it.activityInfo.packageName) }
                .map { ri ->
                    val icon = runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    InstalledApp(ri.activityInfo.packageName, ri.loadLabel(pm).toString(), icon)
                }
                .sortedBy { it.label.lowercase() }
        }
    }
}

/** Cuántas apps hay en cada lista (para la tarjeta de Foco). */
@Composable
fun appListCounts(data: AppData): Triple<Int, Int, Int>? {
    val apps by rememberInstalledApps()
    val list = apps ?: return null
    val lists = list.map { appListOf(data, it.pkg) }
    return Triple(lists.count { it == LIST_BLOCKED }, lists.count { it == LIST_WAIT }, lists.count { it == LIST_ALLOWED })
}

/**
 * Control de apps: cada app en una sola lista. Permitidas (nunca se bloquean), Con espera (5 min de espera
 * y 5 de uso, más espera con cada uso) y Bloqueadas del todo. Se aplica durante cualquier foco o rutina.
 */
@Composable
fun BlockedAppsScreen(data: AppData, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(LIST_WAIT) }
    var phoneAsk by remember { mutableStateOf<Pair<String, String>?>(null) }
    val apps by rememberInstalledApps()
    val now = System.currentTimeMillis()
    val locked = blockSessionActive(data, now)
    val dialer = remember { BlockRules.defaultDialer(ctx) }
    val knownAtOpen = remember { data.apps.known.toSet() }

    DisposableEffect(apps) {
        onDispose { apps?.let { list -> Repo.markAppsKnown(list.map { it.pkg }) } }
    }

    fun move(pkg: String, to: String) {
        val immediate = Repo.setAppList(pkg, to)
        if (!immediate) Toast.makeText(ctx, "Pasar a una lista más permisiva se aplica mañana.", Toast.LENGTH_SHORT).show()
    }

    val list = apps
    val byList = list?.groupBy { appListOf(data, it.pkg) }.orEmpty()
    val shown = (byList[tab] ?: emptyList()).filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Control de apps", onClose)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                if (locked) {
                    val end = data.focus.endAt.takeIf { it > now }
                        ?: com.alberto.upgrade.data.extraEnd(data).takeIf { com.alberto.upgrade.data.inFocusExtra(data, now) }
                        ?: activeRuns(data, now).maxOfOrNull { it.endAt } ?: now
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp).clip(RoundedCornerShape(12.dp))
                            .background(ui.surface).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UIcon(Ic.lock, ui.ink, size = 18.dp)
                        Text(
                            "En curso hasta las ${minStr(minutesOf(end))}: no se puede editar hasta entonces.",
                            color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
                Text(
                    "Se aplica durante cualquier foco o rutina. " + when (tab) {
                        LIST_ALLOWED -> "Nunca se bloquean. Las eliges tú, una a una."
                        LIST_WAIT -> "Espera de 5 min y luego 5 min de uso. Cada uso en la misma sesión alarga la espera: 5 → 10 → 15 min. No rompe la racha."
                        else -> "No se abren hasta que termine el foco o la rutina. Sin botón de desbloqueo."
                    },
                    color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Segmented(
                    listOf(
                        Triple(LIST_ALLOWED, "Permitidas ${byList[LIST_ALLOWED]?.size ?: 0}", null),
                        Triple(LIST_WAIT, "Espera ${byList[LIST_WAIT]?.size ?: 0}", null),
                        Triple(LIST_BLOCKED, "Bloq. ${byList[LIST_BLOCKED]?.size ?: 0}", null)
                    ),
                    tab
                ) { tab = it }
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape).background(ui.card)
                        .border(2.dp, ui.hairline, shape).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(Ic.search, ui.muted, size = 20.dp)
                    Box(Modifier.weight(1f).padding(start = 10.dp)) {
                        if (query.isEmpty()) Text("Buscar app", color = ui.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        com.alberto.upgrade.ui.UpTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ui.ink),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (tab == LIST_WAIT) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        UIcon(Ic.download, ui.muted, size = 15.dp)
                        Text("Las apps que instales entran aquí por defecto.", color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
                    Spacer(Modifier.weight(1f))
                    listOf("PERM.", "ESPERA", "BLOQ.").forEach {
                        Text(it, color = ui.muted, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.5.sp, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            if (list == null) {
                item { Text("Cargando apps…", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp)) }
            }
            items(shown, key = { it.pkg }) { a ->
                val current = appListOf(data, a.pkg)
                val pending = data.apps.pending.firstOrNull { it.pkg == a.pkg }
                val w = data.wait
                val sub = when {
                    pending != null -> "Pasa a " + when (pending.list) { LIST_ALLOWED -> "permitidas"; LIST_WAIT -> "con espera"; else -> "bloqueadas" } + " mañana"
                    a.pkg == dialer && current == LIST_ALLOWED -> "Llamadas y emergencias"
                    current == LIST_WAIT && w.pkg == a.pkg && w.session == currentSession(data, now) ->
                        "Usada ${w.uses} " + (if (w.uses == 1) "vez" else "veces") + " · próxima espera ${nextWait(w.uses)} min"
                    else -> null
                }
                Column(Modifier.animateItem()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = a.icon
                        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
                        else Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(ui.surface), contentAlignment = Alignment.Center) {
                            Text(a.label.take(1).uppercase(), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.label, color = ui.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (knownAtOpen.isNotEmpty() && a.pkg !in knownAtOpen) {
                                    Text("NUEVA", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            if (sub != null) Text(sub, color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TriSelector(pending?.list ?: current, enabled = !locked) { to ->
                            if (a.pkg == dialer && current == LIST_ALLOWED && to != LIST_ALLOWED) phoneAsk = a.pkg to to
                            else move(a.pkg, to)
                        }
                    }
                    ListDivider()
                }
            }
        }
    }

    phoneAsk?.let { (pkg, to) ->
        AlertDialog(
            onDismissRequest = { phoneAsk = null },
            title = { Text("¿Sacar Teléfono de permitidas?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Durante un foco o una rutina no podrías llamar. Las llamadas entrantes siguen entrando, pero si alguien de casa te necesita y tienes que devolver la llamada, no podrás.",
                    fontWeight = FontWeight.SemiBold
                )
            },
            confirmButton = { TextButton({ phoneAsk = null }) { Text("MANTENER PERMITIDA", fontWeight = FontWeight.Black, color = ui.accentInk) } },
            dismissButton = { TextButton({ move(pkg, to); phoneAsk = null }) { Text("SACARLA IGUALMENTE", fontWeight = FontWeight.Black, color = ui.danger) } }
        )
    }
}

/** Selector de 3 estados (permitida / espera / bloqueada) con indicador deslizante. */
@Composable
private fun TriSelector(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val ui = LocalUi.current
    val opts = listOf(LIST_ALLOWED to Ic.check, LIST_WAIT to Ic.hourglass, LIST_BLOCKED to Ic.ban)
    val idx = opts.indexOfFirst { it.first == value }.coerceAtLeast(0)
    val x by animateDpAsState(44.dp * idx, motion(UpMotion.state()), label = "tri")
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.width(132.dp).height(36.dp).clip(shape).background(ui.surface)) {
        val ind = if (value == LIST_BLOCKED) ui.danger else ui.accentInk
        Box(Modifier.offset(x = x).width(44.dp).fillMaxHeight().clip(shape).background(ui.selTint).border(2.dp, ind, shape))
        Row(Modifier.fillMaxSize()) {
            opts.forEach { (key, icon) ->
                val sel = key == value
                Box(
                    Modifier.width(44.dp).fillMaxHeight().clip(shape).clickable(enabled = enabled && !sel) { onChange(key) },
                    contentAlignment = Alignment.Center
                ) {
                    UIcon(icon, when {
                        !enabled -> ui.strong
                        sel && key == LIST_BLOCKED -> ui.danger
                        sel -> ui.accentInk
                        else -> ui.muted
                    }, size = 17.dp)
                }
            }
        }
    }
}
