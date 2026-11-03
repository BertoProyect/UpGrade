package com.alberto.upgrade.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.alberto.upgrade.block.BlockRules
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.UIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class InstalledApp(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
fun BlockedAppsScreen(data: AppData, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }

    val apps by produceState<List<InstalledApp>?>(null) {
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

    val list = apps
    val shown = list?.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) } ?: emptyList()
    val blockedCount = list?.count { it.pkg !in data.block.allowed } ?: 0

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.x, onClose)
            Column(Modifier.padding(start = 8.dp)) {
                Text("Apps bloqueadas", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text(
                    if (list == null) "Cargando apps…" else "$blockedCount de ${list.size} bloqueadas en Foco y en la franja de estudio",
                    color = ui.muted, fontSize = 13.sp
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar app") },
            leadingIcon = { UIcon(Ic.search, ui.muted, size = 20.dp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ui.accentInk, unfocusedBorderColor = ui.hairline,
                focusedContainerColor = ui.card, unfocusedContainerColor = ui.card
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Bloquear todas", { Repo.setAllBlocked(shown.map { it.pkg }, true) }, Modifier.weight(1f), height = 42.dp, icon = Ic.lock)
            GhostButton("Ninguna", { Repo.setAllBlocked(shown.map { it.pkg }, false) }, Modifier.weight(1f), height = 42.dp)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            items(shown, key = { it.pkg }) { a ->
                val blocked = a.pkg !in data.block.allowed
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { Repo.setBlocked(a.pkg, !blocked) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = a.icon
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
                    } else {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(ui.surface))
                    }
                    Text(
                        a.label,
                        color = ui.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                    Switch(
                        checked = blocked,
                        onCheckedChange = { Repo.setBlocked(a.pkg, it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = ui.primary, checkedThumbColor = ui.onPrimary,
                            checkedBorderColor = ui.primary
                        )
                    )
                }
            }
        }
    }
}
