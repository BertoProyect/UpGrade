package com.alberto.upgrade.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.alberto.upgrade.block.BlockRules
import com.alberto.upgrade.notify.AlarmScheduler
import com.alberto.upgrade.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors

data class Award(val xp: Int, val goalReached: Boolean, val levelUp: Int?, val streak: Int)

object Repo {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var file: File? = null
    private lateinit var app: Context
    private val io = Executors.newSingleThreadExecutor()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state
    val data: AppData get() = _state.value

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        app = ctx.applicationContext
        val f = File(app.filesDir, "upgrade.json")
        var d = if (f.exists()) {
            runCatching { json.decodeFromString(AppData.serializer(), f.readText()) }.getOrElse { AppData() }
        } else AppData()
        if (!d.seeded) d = Seed.apply(d)
        if (!d.block.allowedSeeded) {
            val allowed = (d.block.allowed + BlockRules.defaultAllowed(app)).distinct()
            d = d.copy(block = d.block.copy(allowed = allowed, allowedSeeded = true))
        }
        if (!d.areasV2) {
            d = d.copy(
                areasV2 = true,
                areas = d.areas.map {
                    when (it.id) {
                        "insti" -> it.copy(name = "Bachillerato")
                        "orbita" -> it.copy(name = "Órbita Webs")
                        else -> it
                    }
                }
            )
        }
        _state.value = d
        file = f
        write(d)
    }

    private fun write(d: AppData) {
        val f = file ?: return
        val text = json.encodeToString(AppData.serializer(), d)
        io.execute {
            runCatching {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(f)) {
                    f.writeText(text)
                    tmp.delete()
                }
            }
        }
    }

    fun update(f: (AppData) -> AppData): AppData {
        val n = _state.updateAndGet(f)
        write(n)
        if (::app.isInitialized) {
            scope.launch { runCatching { TodayWidget().updateAll(app) } }
        }
        return n
    }

    fun refreshWidget() {
        if (::app.isInitialized) scope.launch { runCatching { TodayWidget().updateAll(app) } }
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---------- XP ----------

    private fun awardIn(d: AppData, amount: Int, dateKey: String): Pair<AppData, Award> {
        val before = d.xpLog[dateKey] ?: 0
        val after = (before + amount).coerceAtLeast(0)
        val log = d.xpLog.toMutableMap().apply { put(dateKey, after) }
        val total = (d.totalXp + amount).coerceAtLeast(0)
        val lvBefore = levelOf(d.totalXp).level
        val lvAfter = levelOf(total).level
        var n = d.copy(xpLog = log, totalXp = total)
        val streak = streakOf(n)
        n = n.copy(bestStreak = maxOf(n.bestStreak, streak))
        val goal = dateKey == todayKey() && before < d.dailyGoal && after >= d.dailyGoal
        return n to Award(amount, goal, if (lvAfter > lvBefore) lvAfter else null, streak)
    }

    fun addXp(amount: Int): Award {
        var award: Award? = null
        update { d -> val (n, a) = awardIn(d, amount, todayKey()); award = a; n }
        return award!!
    }

    // ---------- Tareas ----------

    fun saveTask(t: TaskItem) {
        update { d ->
            val exists = d.tasks.any { it.id == t.id }
            d.copy(tasks = if (exists) d.tasks.map { if (it.id == t.id) t else it } else d.tasks + t)
        }
    }

    fun addTasks(items: List<TaskItem>) {
        if (items.isEmpty()) return
        update { d -> d.copy(tasks = d.tasks + items) }
    }

    fun deleteTask(id: String) {
        update { d ->
            val t = d.tasks.firstOrNull { it.id == id } ?: return@update d
            val base = if (t.done && t.xpAwarded > 0) awardIn(d, -t.xpAwarded, t.xpDate ?: todayKey()).first else d
            base.copy(tasks = base.tasks.filterNot { it.id == id })
        }
    }

    /** Devuelve el premio si la tarea pasa a hecha; null si se desmarca. */
    fun toggleTask(id: String): Award? {
        var award: Award? = null
        update { d ->
            award = null
            val t = d.tasks.firstOrNull { it.id == id } ?: return@update d
            if (!t.done) {
                val xp = t.kind.xp
                val (n, a) = awardIn(d, xp, todayKey())
                award = a
                n.copy(tasks = n.tasks.map {
                    if (it.id == id) it.copy(done = true, doneAt = System.currentTimeMillis(), xpAwarded = xp, xpDate = todayKey()) else it
                })
            } else {
                val (n, _) = awardIn(d, -t.xpAwarded, t.xpDate ?: todayKey())
                n.copy(tasks = n.tasks.map {
                    if (it.id == id) it.copy(done = false, doneAt = null, xpAwarded = 0, xpDate = null) else it
                })
            }
        }
        return award
    }

    // ---------- Eventos ----------

    fun saveEvent(e: EventItem) {
        update { d ->
            val exists = d.events.any { it.id == e.id }
            d.copy(events = if (exists) d.events.map { if (it.id == e.id) e else it } else d.events + e)
        }
    }

    fun deleteEvent(id: String) = update { d -> d.copy(events = d.events.filterNot { it.id == id }) }

    // ---------- Gym ----------

    fun logGym(type: String): Award {
        var award: Award? = null
        update { d ->
            val key = todayKey()
            val already = d.gymLog.containsKey(key)
            val log = d.gymLog.toMutableMap().apply { put(key, type) }
            val base = d.copy(gymLog = log)
            if (already) { award = Award(0, false, null, streakOf(base)); base }
            else { val (n, a) = awardIn(base, 20, key); award = a; n }
        }
        return award!!
    }

    fun unlogGym() {
        update { d ->
            val key = todayKey()
            if (!d.gymLog.containsKey(key)) return@update d
            val base = d.copy(gymLog = d.gymLog - key)
            awardIn(base, -20, key).first
        }
    }

    // ---------- Foco ----------

    fun startFocus(ctx: Context, minutes: Int) {
        val now = System.currentTimeMillis()
        val end = now + minutes * 60_000L
        update { it.copy(focus = FocusState(now, end, minutes, false)) }
        AlarmScheduler.scheduleFocusEnd(ctx, end)
    }

    fun cancelFocus(ctx: Context) {
        update { it.copy(focus = FocusState()) }
        AlarmScheduler.cancelFocusEnd(ctx)
    }

    fun focusActive(now: Long = System.currentTimeMillis()): Boolean = data.focus.endAt > now

    /** Si el foco ha terminado y no se ha cobrado, da la XP. */
    fun claimFocus(now: Long = System.currentTimeMillis()): Award? {
        var award: Award? = null
        update { d ->
            award = null
            val f = d.focus
            if (f.rewarded || f.endAt == 0L || now < f.endAt) return@update d
            val xp = (f.minutes * 0.6).toInt().coerceAtLeast(5)
            val key = todayKey()
            val fl = d.focusLog.toMutableMap().apply { put(key, (get(key) ?: 0) + f.minutes) }
            val (n, a) = awardIn(d.copy(focusLog = fl), xp, key)
            award = a
            n.copy(focus = FocusState())
        }
        return award
    }

    // ---------- Bloqueo ----------

    fun requestUnlock() = update { it.copy(block = it.block.copy(unlockRequestedAt = System.currentTimeMillis())) }
    fun cancelUnlock() = update { it.copy(block = it.block.copy(unlockRequestedAt = 0)) }

    fun setBlocked(pkg: String, blocked: Boolean) = update { d ->
        val list = if (blocked) d.block.allowed - pkg else (d.block.allowed + pkg).distinct()
        d.copy(block = d.block.copy(allowed = list))
    }

    fun setAllBlocked(pkgs: List<String>, blocked: Boolean) = update { d ->
        val list = if (blocked) d.block.allowed - pkgs.toSet() else (d.block.allowed + pkgs).distinct()
        d.copy(block = d.block.copy(allowed = list))
    }

    // ---------- Cierre del día ----------

    fun saveReview(rating: Int, firstTomorrow: String): Award {
        var award: Award? = null
        update { d ->
            val key = todayKey()
            val had = d.reviews.containsKey(key)
            val reviews = d.reviews.toMutableMap().apply { put(key, DayReview(rating, "", firstTomorrow)) }
            var base = d.copy(reviews = reviews)
            if (firstTomorrow.isNotBlank()) {
                base = base.copy(
                    tasks = base.tasks + TaskItem(
                        newId(), firstTomorrow.trim(), Kind.TAREA, "personal", null,
                        LocalDate.now().plusDays(1).key(), createdAt = System.currentTimeMillis()
                    )
                )
            }
            if (had) { award = Award(0, false, null, streakOf(base)); base }
            else { val (n, a) = awardIn(base, 10, key); award = a; n }
        }
        return award!!
    }

    // ---------- Copia de seguridad ----------

    fun exportJson(): String = json.encodeToString(AppData.serializer(), data)

    fun importJson(text: String): Boolean {
        val d = runCatching { json.decodeFromString(AppData.serializer(), text) }.getOrNull() ?: return false
        update { d.copy(seeded = true, permissionsShown = true) }
        return true
    }
}
