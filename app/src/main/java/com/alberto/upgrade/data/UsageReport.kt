package com.alberto.upgrade.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/**
 * Informe de uso: un HTML que se abre en cualquier sitio, con las cifras, los recorridos, los mapas de calor
 * de cada pantalla y los datos en bruto al final (para analizarlo con Claude y mejorar la app).
 * Se comparte con el menú del sistema; no se manda a ningún servidor.
 */
object UsageReport {

    /** Mapa de calor de [screen]: la captura atenuada y encima un halo lima por cada zona con toques. */
    fun heatmap(screen: String, width: Int = 270): Bitmap? {
        val grid = Telemetry.data().taps[screen]?.takeIf { it.size == Telemetry.COLS * Telemetry.ROWS } ?: return null
        val max = grid.maxOrNull()?.takeIf { it > 0 } ?: return null
        val snap = Telemetry.loadSnapshot(screen)
        val h = snap?.let { (it.height * width / it.width.toFloat()).toInt() } ?: (width * 915 / 412)
        val out = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(20, 20, 19))
        if (snap != null) {
            c.drawBitmap(Bitmap.createScaledBitmap(snap, width, h, true), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 120 })
        }
        val cw = width / Telemetry.COLS.toFloat()
        val ch = h / Telemetry.ROWS.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (r in 0 until Telemetry.ROWS) for (col in 0 until Telemetry.COLS) {
            val n = grid[r * Telemetry.COLS + col]
            if (n == 0) continue
            val k = (n / max.toFloat()).coerceIn(0f, 1f)
            val cx = (col + 0.5f) * cw
            val cy = (r + 0.5f) * ch
            val rad = maxOf(cw, ch) * (0.9f + 0.9f * k)
            val a = (70 + 170 * k).toInt()
            p.shader = RadialGradient(cx, cy, rad, Color.argb(a, 196, 242, 58), Color.argb(0, 196, 242, 58), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, rad, p)
        }
        return out
    }

    private fun b64(b: Bitmap): String {
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, o)
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Texto corto para pegar en un chat. */
    fun text(d: AppData, today: LocalDate = LocalDate.now()): String = buildString {
        appendLine("Informe de uso de UpGrade · $today")
        appendLine(Telemetry.summary(today).ifBlank { "Aún no hay datos de uso." })
        appendLine()
        appendLine(Habits.analyze(d, today).prompt)
    }

    fun buildHtml(ctx: Context, d: AppData, today: LocalDate = LocalDate.now()): File {
        val t = Telemetry.totals(today, 7)
        // Ventana real: los datos detallados empiezan cuando se activó la medición, no hace 28 días.
        val span = Telemetry.span(today, 28)
        val t28 = Telemetry.totals(today, span)
        val win = if (span == 1) "hoy" else "últimos $span días"
        val since = dateOf(Telemetry.data().since)
        val patterns = Habits.analyze(d, today)
        val raw = Json.encodeToString(TelemetryData.serializer(), Telemetry.data())
        val html = buildString {
            append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<title>Informe de uso · UpGrade · $today</title><style>")
            append("body{margin:0;background:#141413;color:#FAF9F5;font:16px/1.5 system-ui,sans-serif}main{max-width:880px;margin:0 auto;padding:32px 20px 64px}")
            append("h1{font-size:30px;margin:0 0 4px}h2{font-size:20px;margin:36px 0 12px}p.m{color:#A09D96;margin:0}")
            append(".nums{display:flex;gap:12px;flex-wrap:wrap;margin-top:20px}.n{flex:1 1 160px;border:2px solid #2E2C28;border-radius:16px;padding:14px 16px}.n b{display:block;font-size:28px}")
            append("table{width:100%;border-collapse:collapse}td{padding:7px 4px;border-bottom:1px solid #2E2C28;vertical-align:middle}td.r{text-align:right;color:#A09D96;white-space:nowrap}")
            append(".bar{height:10px;border-radius:5px;background:#C4F23A}.heat{display:flex;flex-wrap:wrap;gap:20px}.heat figure{margin:0}.heat img{border-radius:16px;border:2px solid #2E2C28;display:block}")
            append("figcaption{color:#A09D96;font-size:14px;margin-top:6px}pre{white-space:pre-wrap;word-break:break-all;font-size:11px;color:#A09D96}details{margin-top:24px}")
            append("</style></head><body><main>")
            append("<h1>Cómo usas UpGrade</h1><p class=\"m\">Uso detallado del $since al $today ($span días) · patrones de los últimos ${patterns.days} días</p>")
            if (t28.skipped.isNotEmpty()) append("<p class=\"m\">Sin contar los días de estreno de versión (de pruebas): ${t28.skipped.joinToString(", ")}.</p>")
            append("<div class=\"nums\"><div class=\"n\"><b>${t.medianMinutes} min</b>al día en la app (mediana; media ${t.minutesPerDay})</div><div class=\"n\"><b>${"%.1f".format(ES, t.sessionsPerDay)}</b>sesiones al día</div><div class=\"n\"><b>${t.avgSessionSec} s</b>por sesión</div></div>")

            fun bars(title: String, rows: List<Pair<String, Long>>, unit: (Long) -> String) {
                if (rows.isEmpty()) return
                val max = rows.maxOf { it.second }.coerceAtLeast(1)
                append("<h2>$title</h2><table>")
                rows.take(14).forEach { (k, v) ->
                    append("<tr><td style=\"width:40%\">${esc(k)}</td><td><div class=\"bar\" style=\"width:${(v * 100 / max).coerceAtLeast(2)}%\"></div></td><td class=\"r\">${unit(v)}</td></tr>")
                }
                append("</table>")
            }
            val tot = t28.screens.sumOf { it.second }.coerceAtLeast(1)
            bars("Tiempo por pantalla · $win", t28.screens) { "${it * 100 / tot} % · ${it / 60_000} min" }
            bars("Funciones que más usas · $win", t28.events.map { it.first to it.second.toLong() }) { "$it" }
            bars("Toques por pantalla · $win", t28.taps.map { it.first to it.second.toLong() }) { "$it" }
            bars("Recorridos más habituales · desde el $since", t28.flows.map { it.first to it.second.toLong() }) { "$it" }

            // Cuándo abres la app: día de la semana × hora.
            val m = Array(7) { IntArray(24) }
            d.usageDays.forEach { (k, u) -> parseDate(k)?.let { day -> u.openHours.forEachIndexed { h, v -> if (h < 24) m[day.dayOfWeek.value - 1][h] += v } } }
            val mx = m.maxOf { it.maxOrNull() ?: 0 }
            if (mx > 0) {
                append("<h2>Cuándo abres la app</h2><table style=\"table-layout:fixed\"><tr><td></td>")
                for (h in 0 until 24) append("<td class=\"r\" style=\"font-size:10px;text-align:center\">${if (h % 3 == 0) h else ""}</td>")
                append("</tr>")
                listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, dl ->
                    append("<tr><td class=\"r\">$dl</td>")
                    for (h in 0 until 24) {
                        val a = m[i][h] / mx.toFloat()
                        append("<td style=\"padding:1px\"><div style=\"height:16px;border-radius:3px;background:rgba(196,242,58,${"%.2f".format(java.util.Locale.US, 0.06f + 0.94f * a)})\" title=\"${m[i][h]}\"></div></td>")
                    }
                    append("</tr>")
                }
                append("</table>")
            }

            val screens = t28.taps.map { it.first }.take(10)
            if (screens.isNotEmpty()) {
                append("<h2>Mapas de calor</h2><p class=\"m\">Dónde tocas en cada pantalla. Cuanto más lima, más toques.</p><div class=\"heat\" style=\"margin-top:16px\">")
                screens.forEach { s ->
                    heatmap(s)?.let { bmp ->
                        append("<figure><img width=\"270\" src=\"data:image/png;base64,${b64(bmp)}\" alt=\"Mapa de calor de ${esc(s)}\"><figcaption>${esc(s)}" +
                            (Telemetry.hotspots(s)?.let { " · " + esc(it) } ?: "") + "</figcaption></figure>")
                        bmp.recycle()
                    }
                }
                append("</div>")
            }

            append("<h2>Patrones de uso · últimos ${patterns.days} días</h2><pre style=\"font-size:13px;color:#FAF9F5\">${esc(patterns.prompt)}</pre>")
            append("<details><summary>Datos en bruto (JSON)</summary><pre>${esc(raw)}</pre></details>")
            append("</main></body></html>")
        }
        val dir = File(ctx.cacheDir, "informes").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "upgrade-uso-$today.html").apply { writeText(html) }
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Informe de uso de UpGrade")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Enviar informe").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
