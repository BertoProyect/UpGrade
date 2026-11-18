@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.alberto.upgrade.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.ES
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.Telemetry
import com.alberto.upgrade.data.UsageReport
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.ListCard
import com.alberto.upgrade.ui.ListDivider
import com.alberto.upgrade.ui.ListRow
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.SelectChip
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ajustes › Uso de la app: lo que UpGrade mide de cómo la usas (tiempo por pantalla, funciones, cuándo la abres
 * y dónde tocas), el análisis de la IA y el informe para enviar. Todo vive en el móvil.
 */
@Composable
fun UsageTab(data: AppData, actions: AppActions) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by Telemetry.version.collectAsState()
    val t = remember(version) { Telemetry.totals(days = 7) }
    val t28 = remember(version) { Telemetry.totals(days = 28) }
    var confirmClear by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    Eyebrow("Cómo usas la app", fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp))
    ListCard {
        ListRow("Medir cómo uso la app", subtitle = "Pantallas, toques y funciones. Se queda en este móvil.", chevron = false) {
            UToggle(data.telemetryOn) { v -> Repo.update(false) { it.copy(telemetryOn = v) } }
        }
        ListDivider()
        ListRow("Análisis de la IA", icon = Ic.sparkles, subtitle = "Qué patrones ve en tus datos y qué cambiaría", onClick = { actions.openOverlay("insights") })
    }

    if (t28.screens.isEmpty() && t28.events.isEmpty()) {
        Text(
            "Aún no hay datos. Usa la app un par de días y aquí verás en qué pantallas pasas el tiempo, qué funciones usas y dónde tocas.",
            color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, start = 2.dp)
        )
    } else {
        Eyebrow("Últimos 7 días", fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp))
        ListCard {
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Num("${t.minutesPerDay}", "min al día", Modifier.weight(1f))
                Box(Modifier.width(2.dp).fillMaxHeight().background(ui.hairline))
                Num("%.1f".format(ES, t.sessionsPerDay), "veces al día", Modifier.weight(1f))
                Box(Modifier.width(2.dp).fillMaxHeight().background(ui.hairline))
                Num("${t.avgSessionSec} s", "por visita", Modifier.weight(1f))
            }
        }

        if (t28.screens.isNotEmpty()) {
            Eyebrow("Dónde pasas el tiempo · 28 días", fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp))
            val tot = t28.screens.sumOf { it.second }.coerceAtLeast(1)
            ChunkyCard(padding = 14.dp) {
                t28.screens.take(7).forEach { (name, ms) -> BarRow(name, ms / tot.toFloat(), "${ms * 100 / tot} %") }
            }
        }
        if (t28.events.isNotEmpty()) {
            Eyebrow("Lo que más usas · 28 días", fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp))
            val max = t28.events.first().second.coerceAtLeast(1)
            ChunkyCard(padding = 14.dp) {
                t28.events.take(7).forEach { (name, n) -> BarRow(name, n / max.toFloat(), "$n") }
            }
        }

        val screens = t28.taps.map { it.first }.take(8)
        if (screens.isNotEmpty()) {
            Eyebrow("Mapa de calor", fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp, start = 2.dp))
            Text("Dónde tocas en cada pantalla. Cuanto más lima, más toques.", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
            var sel by remember { mutableStateOf(screens.first()) }
            if (sel !in screens) sel = screens.first()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                screens.forEach { s -> SelectChip(s, s == sel, { sel = s }) }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Heatmap(sel, version) }
            Telemetry.hotspots(sel)?.let {
                Text("Más toques: $it.", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
            }
        }

        OpenHours(data)
    }

    Eyebrow("Informe", fontSize = 12.sp, modifier = Modifier.padding(top = 26.dp, bottom = 6.dp, start = 2.dp))
    Text(
        "Un archivo con todo esto, los mapas de calor y los datos en bruto. Mándamelo y lo uso para afinar la app a tu forma de usarla.",
        color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)
    )
    ChunkyButton(if (sending) "Preparando…" else "Enviar informe", {
        if (!sending) {
            sending = true
            Telemetry.flush()
            scope.launch {
                val f = withContext(Dispatchers.IO) { runCatching { UsageReport.buildHtml(ctx, Repo.data) }.getOrNull() }
                sending = false
                if (f != null) runCatching { UsageReport.share(ctx, f) }.onFailure { Toast.makeText(ctx, "No se ha podido compartir.", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(ctx, "No se ha podido preparar el informe.", Toast.LENGTH_SHORT).show()
            }
        }
    }, Modifier.fillMaxWidth(), icon = Ic.send, iconSize = 18.dp)
    Spacer(Modifier.height(10.dp))
    GhostButton("Copiar resumen", {
        ctx.getSystemService(android.content.ClipboardManager::class.java)
            ?.setPrimaryClip(android.content.ClipData.newPlainText("UpGrade", UsageReport.text(Repo.data)))
        Toast.makeText(ctx, "Resumen copiado", Toast.LENGTH_SHORT).show()
    }, Modifier.fillMaxWidth(), height = 46.dp, fontSize = 13.sp)
    TextAction("Borrar datos de uso", { confirmClear = true }, Modifier.fillMaxWidth().padding(top = 8.dp), color = ui.danger, fontSize = 13.sp)

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar los datos de uso?", fontWeight = FontWeight.Black) },
            text = { Text("Se borran los tiempos, toques, mapas de calor y capturas. Tus tareas, XP y rachas no se tocan.", fontWeight = FontWeight.SemiBold) },
            confirmButton = { androidx.compose.material3.TextButton({ Telemetry.clear(); confirmClear = false }) { Text("Borrar", color = ui.danger, fontWeight = FontWeight.Black) } },
            dismissButton = { androidx.compose.material3.TextButton({ confirmClear = false }) { Text("Volver") } }
        )
    }
}

@Composable
private fun Num(value: String, label: String, modifier: Modifier) {
    val ui = LocalUi.current
    Column(modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(label, color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun BarRow(label: String, frac: Float, value: String) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.46f))
        Box(Modifier.weight(0.4f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(ui.surface)) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0.03f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(ui.primary))
        }
        Text(value, color = ui.muted, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp).width(44.dp))
    }
}

/** La captura de la pantalla, atenuada, con un halo lima donde más tocas. */
@Composable
private fun Heatmap(screen: String, version: Int) {
    val ui = LocalUi.current
    val snap = remember(screen, version) { Telemetry.loadSnapshot(screen)?.asImageBitmap() }
    val grid = remember(screen, version) { Telemetry.data().taps[screen]?.takeIf { it.size == Telemetry.COLS * Telemetry.ROWS } }
    val ratio = snap?.let { it.width / it.height.toFloat() } ?: (412f / 915f)
    val shape = RoundedCornerShape(18.dp)
    val lime = ui.primary
    Box(Modifier.fillMaxWidth(0.72f).aspectRatio(ratio).clip(shape).background(ui.surface).border(2.dp, ui.hairline, shape)) {
        if (snap != null) Image(snap, contentDescription = "Captura de $screen", contentScale = ContentScale.FillBounds, alpha = 0.45f, modifier = Modifier.fillMaxSize())
        if (grid != null) {
            val max = (grid.maxOrNull() ?: 1).coerceAtLeast(1)
            Canvas(Modifier.fillMaxSize()) {
                val cw = size.width / Telemetry.COLS
                val ch = size.height / Telemetry.ROWS
                for (r in 0 until Telemetry.ROWS) for (c in 0 until Telemetry.COLS) {
                    val n = grid[r * Telemetry.COLS + c]
                    if (n == 0) continue
                    val k = n / max.toFloat()
                    val center = Offset((c + 0.5f) * cw, (r + 0.5f) * ch)
                    val rad = maxOf(cw, ch) * (0.9f + 0.9f * k)
                    drawCircle(Brush.radialGradient(listOf(lime.copy(alpha = 0.3f + 0.6f * k), lime.copy(alpha = 0f)), center, rad), rad, center)
                }
            }
        }
    }
}

/** Cuándo abres la app: día de la semana × hora, de los contadores de uso. */
@Composable
private fun OpenHours(data: AppData) {
    val ui = LocalUi.current
    val m = remember(data.usageDays) {
        Array(7) { IntArray(24) }.also { a ->
            data.usageDays.forEach { (k, u) -> parseDate(k)?.let { day -> u.openHours.forEachIndexed { h, v -> if (h < 24) a[day.dayOfWeek.value - 1][h] += v } } }
        }
    }
    val max = m.maxOf { it.maxOrNull() ?: 0 }
    if (max == 0) return
    Eyebrow("Cuándo abres la app", fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp))
    ChunkyCard(padding = 12.dp) {
        listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d, color = ui.muted, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.width(16.dp))
                (0 until 24).forEach { h ->
                    val a = m[i][h] / max.toFloat()
                    Box(Modifier.weight(1f).padding(horizontal = 0.75.dp).height(14.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (m[i][h] == 0) ui.surface else ui.primary.copy(alpha = 0.18f + 0.82f * a)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp)) {
            listOf("0", "6", "12", "18", "24").forEachIndexed { i, t ->
                Text(t, color = ui.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = if (i < 4) Modifier.weight(1f) else Modifier)
            }
        }
    }
}
