package com.alberto.upgrade.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Datos de uso detallados de UpGrade: tiempo en cada pantalla, por dónde pasas, qué funciones usas,
 * cuánto duran las sesiones y dónde tocas (mapa de calor). Vive en su propio archivo, solo en el móvil:
 * sale únicamente si envías un informe, y a la IA solo le llega el resumen en texto.
 */
@Serializable
data class TelemetryData(
    val since: Long = 0,
    /** Pantalla → toques acumulados en una cuadrícula de [Telemetry.COLS] × [Telemetry.ROWS]. */
    val taps: Map<String, List<Int>> = emptyMap(),
    /** Día → pantalla → toques. */
    val tapDays: Map<String, Map<String, Int>> = emptyMap(),
    /** Día → pantalla → milisegundos. */
    val screenMs: Map<String, Map<String, Long>> = emptyMap(),
    /** Día → función → veces. */
    val events: Map<String, Map<String, Int>> = emptyMap(),
    /** «De → a» → veces (cómo te mueves por la app). */
    val flows: Map<String, Int> = emptyMap(),
    /** Día → duración de cada sesión (ms), hasta 60 por día. */
    val sessions: Map<String, List<Long>> = emptyMap(),
    /** Pantalla → día de su última captura para el mapa de calor. */
    val snaps: Map<String, String> = emptyMap(),
    /** Última versión de la app que se ha abierto. */
    val lastVersion: String = "",
    /**
     * Días en los que se estrenó una versión nueva. Ese día se prueba todo (Ajustes de arriba abajo, cada pantalla…)
     * y los números de uso se disparan: las cifras de «al día» no los cuentan.
     */
    val updateDays: List<String> = emptyList()
)

object Telemetry {
    const val COLS = 12
    const val ROWS = 26
    private const val KEEP_DAYS = 42L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val io = Executors.newSingleThreadExecutor()
    private var file: File? = null
    private var snapDir: File? = null
    private var d = TelemetryData()
    private var dirty = false
    private var current: String? = null
    private var enteredAt = 0L
    private var sessionStart = 0L

    /** Sube cada vez que cambian los datos, para que la pantalla de uso se redibuje. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun on(): Boolean = Repo.data.telemetryOn

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "uso-detallado.json")
        d = if (f.exists()) runCatching { json.decodeFromString(TelemetryData.serializer(), f.readText()) }.getOrElse { TelemetryData() } else TelemetryData()
        if (d.since == 0L) { d = d.copy(since = System.currentTimeMillis()); dirty = true }
        val ver = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
        if (ver.isNotEmpty() && ver != d.lastVersion) {
            val today = LocalDate.now().key()
            // Primera vez con este registro: solo es estreno si ya había datos de antes de hoy.
            val isUpdate = d.lastVersion.isNotEmpty() || dateOf(d.since).key() != today
            d = d.copy(lastVersion = ver, updateDays = if (isUpdate) (d.updateDays + today).distinct().takeLast(30) else d.updateDays)
            dirty = true
        }
        file = f
        snapDir = File(ctx.filesDir, "capturas").apply { mkdirs() }
    }

    @Synchronized
    fun data(): TelemetryData = d

    // ---------------- Registro ----------------

    /** Entras en [name]: se apunta el tiempo de la pantalla anterior y el paso de una a otra. */
    @Synchronized
    fun screen(name: String, now: Long = System.currentTimeMillis()) {
        if (file == null || name == current) return
        val prev = current
        if (on()) {
            closeScreen(now)
            if (prev != null) d = d.copy(flows = d.flows + ("$prev > $name" to (d.flows["$prev > $name"] ?: 0) + 1))
            dirty = true
        }
        current = name
        enteredAt = now
    }

    fun currentScreen(): String? = current

    private fun closeScreen(now: Long) {
        val s = current ?: return
        if (enteredAt == 0L) return
        val ms = (now - enteredAt).coerceIn(0, 30 * 60_000L)
        if (ms < 300) return
        val key = dateOf(now).key()
        val day = d.screenMs[key].orEmpty()
        d = d.copy(screenMs = d.screenMs + (key to (day + (s to (day[s] ?: 0L) + ms))))
        enteredAt = now
    }

    /** Un toque en ([x], [y]), en fracción del ancho y del alto de la ventana. */
    @Synchronized
    fun tap(x: Float, y: Float) {
        if (file == null || !on()) return
        val s = current ?: return
        val c = (x * COLS).toInt().coerceIn(0, COLS - 1)
        val r = (y * ROWS).toInt().coerceIn(0, ROWS - 1)
        val grid = d.taps[s]?.takeIf { it.size == COLS * ROWS }?.toMutableList() ?: MutableList(COLS * ROWS) { 0 }
        grid[r * COLS + c]++
        val key = LocalDate.now().key()
        val day = d.tapDays[key].orEmpty()
        d = d.copy(taps = d.taps + (s to grid), tapDays = d.tapDays + (key to (day + (s to (day[s] ?: 0) + 1))))
        dirty = true
    }

    /** Usas una función (añadir tarea, empezar foco, preguntar a la IA…). */
    @Synchronized
    fun event(name: String) {
        if (file == null || !on()) return
        val key = LocalDate.now().key()
        val day = d.events[key].orEmpty()
        d = d.copy(events = d.events + (key to (day + (name to (day[name] ?: 0) + 1))))
        dirty = true
    }

    @Synchronized
    fun resume(now: Long = System.currentTimeMillis()) {
        sessionStart = now
        enteredAt = now
    }

    @Synchronized
    fun pause(now: Long = System.currentTimeMillis()) {
        if (file != null && on()) {
            closeScreen(now)
            if (sessionStart > 0) {
                val ms = (now - sessionStart).coerceIn(0, 3 * 60 * 60_000L)
                if (ms >= 1_000) {
                    val key = dateOf(now).key()
                    d = d.copy(sessions = d.sessions + (key to (d.sessions[key].orEmpty() + ms).takeLast(60)))
                }
            }
            dirty = true
        }
        sessionStart = 0
        enteredAt = 0
        flush()
    }

    /** Guarda si hay cambios (se llama al salir de la app y cada medio minuto). */
    @Synchronized
    fun flush() {
        val f = file ?: return
        if (!dirty) return
        dirty = false
        d = prune(d)
        val text = json.encodeToString(TelemetryData.serializer(), d)
        _version.value++
        io.execute { runCatching { File(f.parentFile, f.name + ".tmp").apply { writeText(text) }.renameTo(f) } }
    }

    @Synchronized
    fun clear() {
        d = TelemetryData(since = System.currentTimeMillis())
        snapDir?.listFiles()?.forEach { it.delete() }
        dirty = true
        flush()
    }

    private fun prune(t: TelemetryData): TelemetryData {
        val from = LocalDate.now().minusDays(KEEP_DAYS).key()
        return t.copy(
            tapDays = t.tapDays.filterKeys { it >= from },
            screenMs = t.screenMs.filterKeys { it >= from },
            events = t.events.filterKeys { it >= from },
            sessions = t.sessions.filterKeys { it >= from }
        )
    }

    // ---------------- Capturas para el mapa de calor ----------------

    fun snapFile(screen: String): File? = snapDir?.let { File(it, safe(screen) + ".jpg") }

    @Synchronized
    fun needsSnapshot(screen: String): Boolean =
        file != null && on() && (d.snaps[screen] != LocalDate.now().key() || snapFile(screen)?.exists() != true)

    /** Guarda una miniatura de la pantalla (270 px de ancho) para dibujar encima los toques. */
    fun saveSnapshot(screen: String, full: Bitmap) {
        io.execute {
            runCatching {
                val w = 270
                val h = (full.height * w / full.width.toFloat()).roundToInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(full, w, h, true)
                snapFile(screen)?.outputStream()?.use { small.compress(Bitmap.CompressFormat.JPEG, 72, it) }
                if (small !== full) small.recycle()
                full.recycle()
                synchronized(this) {
                    d = d.copy(snaps = d.snaps + (screen to LocalDate.now().key()))
                    dirty = true
                }
            }
        }
    }

    fun loadSnapshot(screen: String): Bitmap? = snapFile(screen)?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }

    private fun safe(s: String) = s.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

    // ---------------- Resúmenes ----------------

    data class Totals(
        val days: Int,
        val minutesPerDay: Int,
        /** Mediana de minutos al día: un día de pruebas de 34 min no la mueve. */
        val medianMinutes: Int,
        /** Días de estreno de versión que se han dejado fuera. */
        val skipped: List<String>,
        val sessionsPerDay: Float,
        val avgSessionSec: Int,
        val screens: List<Pair<String, Long>>,
        val events: List<Pair<String, Int>>,
        val flows: List<Pair<String, Int>>,
        val taps: List<Pair<String, Int>>
    )

    /** Días con datos de uso detallado (desde que se empezó a medir), como mucho [cap]. */
    @Synchronized
    fun span(today: LocalDate = LocalDate.now(), cap: Int = 28): Int {
        if (d.since == 0L) return 0
        val n = java.time.temporal.ChronoUnit.DAYS.between(dateOf(d.since), today).toInt() + 1
        return n.coerceIn(1, cap)
    }

    /** Cifras de los últimos [days] días, sin los días de estreno de versión (salvo que [withUpdates]). */
    @Synchronized
    fun totals(today: LocalDate = LocalDate.now(), days: Int = 7, withUpdates: Boolean = false): Totals {
        val all = (0 until days).map { today.minusDays(it.toLong()).key() }.toSet()
        val skipped = if (withUpdates) emptyList() else d.updateDays.filter { it in all }.sorted()
        val keys = all - skipped.toSet()
        val active = keys.count { k -> d.sessions[k].orEmpty().isNotEmpty() || d.screenMs[k].orEmpty().isNotEmpty() }.coerceAtLeast(1)
        val screens = mutableMapOf<String, Long>()
        d.screenMs.filterKeys { it in keys }.values.forEach { m -> m.forEach { (k, v) -> screens[k] = (screens[k] ?: 0L) + v } }
        val evs = mutableMapOf<String, Int>()
        d.events.filterKeys { it in keys }.values.forEach { m -> m.forEach { (k, v) -> evs[k] = (evs[k] ?: 0) + v } }
        val taps = mutableMapOf<String, Int>()
        d.tapDays.filterKeys { it in keys }.values.forEach { m -> m.forEach { (k, v) -> taps[k] = (taps[k] ?: 0) + v } }
        val sess = d.sessions.filterKeys { it in keys }.values.flatten()
        val perDay = keys.mapNotNull { k -> d.sessions[k]?.takeIf { it.isNotEmpty() }?.sum() }.sorted()
        val median = if (perDay.isEmpty()) 0L else if (perDay.size % 2 == 1) perDay[perDay.size / 2] else (perDay[perDay.size / 2 - 1] + perDay[perDay.size / 2]) / 2
        return Totals(
            days = active,
            minutesPerDay = (sess.sum() / 60_000L / active).toInt(),
            medianMinutes = (median / 60_000L).toInt(),
            skipped = skipped,
            sessionsPerDay = sess.size / active.toFloat(),
            avgSessionSec = if (sess.isEmpty()) 0 else (sess.average() / 1000).roundToInt(),
            screens = screens.entries.sortedByDescending { it.value }.map { it.key to it.value },
            events = evs.entries.sortedByDescending { it.value }.map { it.key to it.value },
            // Los recorridos no se guardan por día: son de todo el periodo medido.
            flows = d.flows.entries.sortedByDescending { it.value }.take(12).map { it.key to it.value },
            taps = taps.entries.sortedByDescending { it.value }.map { it.key to it.value }
        )
    }

    /** Dónde se concentran los toques de una pantalla, en palabras (arriba, centro, abajo…). */
    @Synchronized
    fun hotspots(screen: String): String? {
        val g = d.taps[screen]?.takeIf { it.size == COLS * ROWS } ?: return null
        val total = g.sum().takeIf { it > 0 } ?: return null
        val bands = listOf("arriba" to (0 until 6), "parte alta" to (6 until 12), "centro" to (12 until 18), "parte baja" to (18 until 23), "barra de abajo" to (23 until ROWS))
        return bands.map { (n, rows) -> n to rows.sumOf { r -> (0 until COLS).sumOf { c -> g[r * COLS + c] } } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(3)
            .joinToString(", ") { "${it.first} ${it.second * 100 / total} %" }
    }

    /** Resumen en texto para la IA. */
    fun summary(today: LocalDate = LocalDate.now()): String {
        if (file == null) return ""
        val t = totals(today, 7)
        if (t.screens.isEmpty() && t.events.isEmpty()) return ""
        return buildString {
            appendLine(
                "Últimos 7 días (${t.days} con uso" + (if (t.skipped.isNotEmpty()) ", sin contar ${t.skipped.size} de estreno de versión, que son de pruebas" else "") + "): " +
                    "${t.medianMinutes} min al día en UpGrade (mediana; media ${t.minutesPerDay}), ${"%.1f".format(ES, t.sessionsPerDay)} sesiones al día de ${t.avgSessionSec} s de media."
            )
            val tot = t.screens.sumOf { it.second }.coerceAtLeast(1)
            appendLine("Tiempo por pantalla: " + t.screens.take(10).joinToString(", ") { "${it.first} ${it.second * 100 / tot} %" } + ".")
            if (t.events.isNotEmpty()) appendLine("Funciones usadas: " + t.events.take(15).joinToString(", ") { "${it.first} ${it.second}" } + ".")
            if (t.flows.isNotEmpty()) appendLine("Recorridos más habituales: " + t.flows.take(8).joinToString("; ") { "${it.first} (${it.second})" } + ".")
            t.taps.take(4).forEach { (s, n) -> hotspots(s)?.let { appendLine("Toques en $s ($n): $it.") } }
        }.trim()
    }
}
