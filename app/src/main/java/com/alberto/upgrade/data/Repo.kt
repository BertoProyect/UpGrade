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
        SecretStore.init(app)
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
        d = migrateAi(d)
        if (!d.xpV2) d = d.copy(xpV2 = true, dailyGoal = 80, weekendGoal = 80)
        if (!d.routinesV1) d = migrateRoutines(d)
        _state.value = d
        file = f
        write(d)
    }

    /**
     * Rutinas v1: la franja de estudio deja de existir (se crea una rutina si quieres) y entra
     * «Despertar sin dopamina». Las apps que estaban permitidas siguen permitidas; el resto pasa a «con espera».
     */
    private fun migrateRoutines(d0: AppData): AppData {
        val allowed = (d0.block.allowed + BlockRules.defaultAllowed(app)).distinct()
        return d0.copy(
            routinesV1 = true,
            routines = d0.routines.ifEmpty {
                listOf(Routine("wake", "Despertar sin dopamina", "sunrise", "wake", (1..7).toList(), "unlock", 5 * 60, "duration", 19 * 60, 90))
            },
            apps = d0.apps.copy(allowed = allowed),
            block = d0.block.copy(scheduleEnabled = false)
        )
    }

    /** La clave sale del JSON (que entra en copias) a SecretStore; el modelo viejo pasa al rápido. */
    private fun migrateAi(d0: AppData): AppData {
        var d = d0
        if (d.geminiKey.isNotBlank()) {
            if (!SecretStore.hasGeminiKey()) SecretStore.geminiKey = d.geminiKey
            d = d.copy(geminiKey = "")
        }
        if (!d.aiV2) {
            val old = d.aiModel.isBlank() || d.aiModel == "gemini-flash-latest"
            d = d.copy(aiV2 = true, aiModel = if (old) DEFAULT_AI_MODEL else d.aiModel)
        }
        return d
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

    fun update(f: (AppData) -> AppData): AppData = update(true, f)

    /** [widget] = false para cambios que no se ven en el widget (contadores de uso): ahorra redibujarlo. */
    fun update(widget: Boolean, f: (AppData) -> AppData): AppData {
        val n = _state.updateAndGet(f)
        write(n)
        if (widget && ::app.isInitialized) {
            scope.launch { runCatching { TodayWidget().updateAll(app) } }
        }
        return n
    }

    // ---------- Uso (para «Tus hábitos») ----------

    private var lastOpenAt = 0L

    /** Suma un contador del día. Todo se queda en el móvil; a la IA solo va el resumen. */
    fun logUsage(now: Long = System.currentTimeMillis(), f: (DayUsage, Int) -> DayUsage) {
        if (file == null) return
        val day = dateOf(now)
        val hour = minutesOf(now) / 60
        update(false) { d ->
            val u = d.usageDays[day.key()] ?: DayUsage()
            d.copy(usageDays = (d.usageDays + (day.key() to f(u, hour))).prune(day))
        }
    }

    /** UpGrade pasa a primer plano. Dos aperturas a menos de 2 min cuentan como una. */
    fun logOpen(now: Long = System.currentTimeMillis()) {
        if (now - lastOpenAt < 2 * 60_000L) return
        lastOpenAt = now
        logUsage(now) { u, h -> u.copy(opens = u.opens + 1, openHours = u.openHours.bump(h)) }
    }

    fun logBlocked(appLabel: String, now: Long = System.currentTimeMillis()) = logUsage(now) { u, h ->
        u.copy(blocks = u.blocks + 1, blockHours = u.blockHours.bump(h), blockApps = u.blockApps + (appLabel to (u.blockApps[appLabel] ?: 0) + 1))
    }

    // ---------- Análisis de la IA ----------

    fun saveInsights(r: InsightReport) = update(false) { d ->
        val old = d.insights?.items.orEmpty().filter { it.status != "new" }
        val hist = (d.insightHistory + old).reversed().distinctBy { it.title.lowercase() }.reversed().takeLast(12)
        // Lo que estabas probando sigue marcado si la IA lo vuelve a proponer.
        val items = r.items.map { n -> old.firstOrNull { it.title.equals(n.title, ignoreCase = true) && it.status == "trying" }?.let { n.copy(status = "trying", since = it.since) } ?: n }
        d.copy(insights = r.copy(items = items), insightHistory = hist)
    }

    fun setInsightStatus(id: String, status: String) = update(false) { d ->
        val rep = d.insights ?: return@update d
        val items = rep.items.map { if (it.id == id) it.copy(status = status, since = if (status == "trying") System.currentTimeMillis() else it.since) else it }
        d.copy(insights = rep.copy(items = items))
    }

    fun refreshWidget() {
        if (::app.isInitialized) scope.launch { runCatching { TodayWidget().updateAll(app) } }
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---------- XP ----------

    /**
     * Suma (o resta) XP en [dateKey] y lo apunta en el historial con [label] y [kind]
     * (task, exam, gym, fisio, focus, routine, review, other). Al deshacer se borra la entrada que lo dio.
     */
    private fun awardIn(d: AppData, amount: Int, dateKey: String, label: String = "XP", kind: String = "other"): Pair<AppData, Award> {
        val before = d.xpLog[dateKey] ?: 0
        val after = (before + amount).coerceAtLeast(0)
        val log = d.xpLog.toMutableMap().apply { put(dateKey, after) }
        val total = (d.totalXp + amount).coerceAtLeast(0)
        val lvBefore = levelOf(d.totalXp).level
        val lvAfter = levelOf(total).level
        val events = if (amount == 0) d.xpEvents
        else if (amount < 0) {
            val i = d.xpEvents.indexOfLast { it.date == dateKey && it.label == label && it.amount == -amount }
            if (i >= 0) d.xpEvents.toMutableList().apply { removeAt(i) } else d.xpEvents + XpEvent(System.currentTimeMillis(), dateKey, amount, label, kind)
        } else (d.xpEvents + XpEvent(System.currentTimeMillis(), dateKey, amount, label, kind)).takeLast(3000)
        var n = d.copy(xpLog = log, totalXp = total, xpEvents = events)
        val streak = streakOf(n)
        n = n.copy(bestStreak = maxOf(n.bestStreak, streak), bestFocusStreak = maxOf(n.bestFocusStreak, focusStreakOf(n)))
        val dayGoal = goalFor(d, parseDate(dateKey) ?: LocalDate.now())
        val goal = dateKey == todayKey() && before < dayGoal && after >= dayGoal
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
            val base = if (t.done && t.xpAwarded > 0) awardIn(d, -t.xpAwarded, t.xpDate ?: todayKey(), t.title).first else d
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
                // Con el tope de tareas alcanzado, la tarea cuenta como hecha pero sin XP.
                val xp = xpIfDone(d, t)
                val (n, a) = awardIn(d, xp, todayKey(), t.title, if (t.kind == Kind.EXAMEN) "exam" else "task")
                award = a
                n.copy(tasks = n.tasks.map {
                    if (it.id == id) it.copy(done = true, doneAt = System.currentTimeMillis(), xpAwarded = xp, xpDate = todayKey()) else it
                })
            } else {
                val (n, _) = awardIn(d, -t.xpAwarded, t.xpDate ?: todayKey(), t.title)
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
            else { val (n, a) = awardIn(base, d.xp.gym, key, "Gym · $type", "gym"); award = a; n }
        }
        return award!!
    }

    fun unlogGym() {
        update { d ->
            val key = todayKey()
            if (!d.gymLog.containsKey(key)) return@update d
            val base = d.copy(gymLog = d.gymLog - key)
            awardIn(base, -d.xp.gym, key, "Gym · ${d.gymLog[key]}").first
        }
    }

    // ---------- Áreas ----------

    fun saveArea(a: Area) = update { d ->
        val exists = d.areas.any { it.id == a.id }
        d.copy(areas = if (exists) d.areas.map { if (it.id == a.id) a else it } else d.areas + a)
    }

    /** Borra un área propia: sus tareas y eventos pasan a «Otro». El XP ganado se queda. */
    fun deleteArea(id: String) = update { d ->
        if (isBuiltInArea(id)) return@update d
        d.copy(
            areas = d.areas.filterNot { it.id == id },
            tasks = d.tasks.map { if (it.areaId == id) it.copy(areaId = "otro") else it },
            events = d.events.map { if (it.areaId == id) it.copy(areaId = "otro") else it }
        )
    }

    fun moveArea(id: String, delta: Int) = update { d ->
        val list = d.areas.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return@update d
        // Se salta las áreas ocultas (como Gym) para que el movimiento se note en la lista.
        val visible = listedAreas(d).map { it.id }
        val vi = visible.indexOf(id)
        val target = visible.getOrNull(vi + delta) ?: return@update d
        val j = list.indexOfFirst { it.id == target }
        val item = list.removeAt(i)
        list.add(j, item)
        d.copy(areas = list)
    }

    // ---------- Rutinas ----------

    /**
     * Arranca las rutinas de hora fija que tocan, cierra las que han terminado y da su XP.
     * Se llama cada segundo desde la app y cada pocos segundos desde el servicio de bloqueo.
     */
    fun tickRoutines(now: Long = System.currentTimeMillis()): Award? {
        var award: Award? = null
        val today = dateOf(now)
        val min = minutesOf(now)
        val d0 = data
        val needsStart = routinesOn(d0, today).any { r ->
            r.startMode == "fixed" && min >= r.start && min < r.start + r.lengthFrom(r.start) && runOf(d0, r.id, today) == null
        }
        val needsEnd = d0.routineLog.any { it.status == "running" && now >= it.endAt }
        if (!needsStart && !needsEnd) return null
        update { d ->
            var log = d.routineLog
            routinesOn(d, today).filter { r ->
                r.startMode == "fixed" && min >= r.start && min < r.start + r.lengthFrom(r.start) && runOf(d, r.id, today) == null
            }.forEach { r ->
                log = log + RoutineRun(r.id, today.key(), epochAt(today, r.start), epochAt(today, r.start + r.lengthFrom(r.start)))
            }
            var n = d.copy(routineLog = log)
            n.routineLog.filter { it.status == "running" && now >= it.endAt }.forEach { run ->
                val r = n.routines.firstOrNull { it.id == run.routineId }?.effective(LocalDate.parse(run.date))
                val mins = ((run.endAt - run.startedAt) / 60_000L).toInt()
                val xp = if (r != null) routineXp(n, r, mins) else 0
                n = n.copy(routineLog = n.routineLog.map { if (it === run) it.copy(status = "done", xp = xp) else it })
                if (xp > 0) { val (m, a) = awardIn(n, xp, run.date, r?.name ?: "Rutina", "routine"); n = m; award = a }
            }
            val fs = focusStreakOf(n)
            n.copy(routineLog = n.routineLog.takeLast(600), bestFocusStreak = maxOf(n.bestFocusStreak, fs))
        }
        return award
    }

    /** Primer desbloqueo del día: arranca las rutinas «al primer desbloqueo» que toquen. */
    fun onUnlock(now: Long = System.currentTimeMillis()) {
        val today = dateOf(now)
        val min = minutesOf(now)
        logUsage(now) { u, h -> u.copy(unlocks = u.unlocks + 1, unlockHours = u.unlockHours.bump(h), firstUnlock = if (u.firstUnlock < 0) min else u.firstUnlock) }
        val due = routinesOn(data, today).filter { r ->
            r.startMode == "unlock" && min >= r.start && min < 14 * 60 && runOf(data, r.id, today) == null
        }
        if (due.isEmpty()) return
        update { d ->
            d.copy(routineLog = d.routineLog + due.map { r -> RoutineRun(r.id, today.key(), now, now + r.duration * 60_000L) })
        }
    }

    /** Cancelar solo vale en los 5 primeros minutos; cuenta como no hecha (sin XP ni racha). */
    fun cancelRoutine(id: String, now: Long = System.currentTimeMillis()): Boolean {
        val run = data.routineLog.lastOrNull { it.routineId == id && it.status == "running" } ?: return false
        if (now - run.startedAt > 5 * 60_000L) return false
        logUsage(now) { u, _ -> u.copy(routineCancels = u.routineCancels + 1) }
        update { d -> d.copy(routineLog = d.routineLog.map { if (it === run || (it.routineId == id && it.startedAt == run.startedAt)) it.copy(status = "cancelled", endAt = now) else it }) }
        return true
    }

    fun routineRunning(id: String, now: Long = System.currentTimeMillis()): Boolean =
        data.routineLog.any { it.routineId == id && it.status == "running" && now >= it.startedAt && now < it.endAt }

    /**
     * Guarda una rutina. Endurecer (o crear) se aplica ya; aflojar, desde mañana.
     * Devuelve true si se aplicó ya. Con la rutina en curso no se puede tocar.
     */
    fun saveRoutine(r: Routine): Boolean {
        if (routineRunning(r.id)) return false
        val old = data.routines.firstOrNull { it.id == r.id }
        val clean = r.copy(pending = null, pendingFrom = null, deleteFrom = null)
        val immediate = old == null || isTightening(old.copy(name = r.name, icon = r.icon, xp = r.xp), clean)
        val tomorrow = LocalDate.now().plusDays(1).key()
        update { d ->
            val list = if (old == null) d.routines + clean
            else d.routines.map {
                if (it.id != r.id) it
                else if (immediate) clean
                else it.copy(name = r.name, icon = r.icon, xp = r.xp, pending = clean, pendingFrom = tomorrow)
            }
            d.copy(routines = list)
        }
        return immediate
    }

    fun setRoutineEnabled(id: String, on: Boolean): Boolean {
        val r = data.routines.firstOrNull { it.id == id } ?: return false
        return saveRoutine(r.copy(enabled = on))
    }

    /** Borrar afloja: la rutina desaparece desde mañana (si está apagada, ya). */
    fun deleteRoutine(id: String) {
        if (routineRunning(id)) return
        update { d ->
            d.copy(routines = d.routines.mapNotNull {
                when {
                    it.id != id -> it
                    !it.enabled -> null
                    else -> it.copy(deleteFrom = LocalDate.now().plusDays(1).key())
                }
            })
        }
    }

    // ---------- Control de apps ----------

    /**
     * Mueve una app de lista. Más estricta: ya. Más permisiva: desde mañana.
     * Durante un foco o una rutina no se puede tocar. Devuelve true si se aplicó ya.
     */
    fun setAppList(pkg: String, list: String): Boolean {
        val d0 = data
        if (blockSessionActive(d0)) return false
        val current = appListOf(d0, pkg)
        if (current == list) return true
        val loosen = looseness(list) > looseness(current)
        update { d ->
            val a = d.apps
            if (loosen) {
                val from = LocalDate.now().plusDays(1).key()
                d.copy(apps = a.copy(pending = a.pending.filterNot { it.pkg == pkg } + PendingApp(pkg, list, from)))
            } else {
                d.copy(apps = a.copy(
                    allowed = if (list == LIST_ALLOWED) (a.allowed + pkg).distinct() else a.allowed - pkg,
                    blocked = if (list == LIST_BLOCKED) (a.blocked + pkg).distinct() else a.blocked - pkg,
                    pending = a.pending.filterNot { it.pkg == pkg }
                ))
            }
        }
        return !loosen
    }

    fun markAppsKnown(pkgs: List<String>) {
        if (pkgs.all { it in data.apps.known }) return
        update { d -> d.copy(apps = d.apps.copy(known = (d.apps.known + pkgs).distinct())) }
    }

    /** «Necesito el móvil»: espera 5 / 10 / 15 min (según los usos de esta sesión) y luego 5 min abierta. */
    fun requestWait(pkg: String, now: Long = System.currentTimeMillis()) {
        val session = currentSession(data, now) ?: return
        logUsage(now) { u, _ -> u.copy(waits = u.waits + 1) }
        update { d ->
            val w = if (d.wait.session == session) d.wait else WaitState(session = session)
            val mins = nextWait(w.uses)
            d.copy(wait = w.copy(uses = w.uses + 1, pkg = pkg, requestedAt = now, waitMin = mins, openFrom = now + mins * 60_000L, openUntil = now + (mins + 5) * 60_000L))
        }
    }

    fun cancelWait() = update { d ->
        if (d.wait.pkg == null) d
        else d.copy(wait = d.wait.copy(uses = (d.wait.uses - 1).coerceAtLeast(0), pkg = null, requestedAt = 0, openFrom = 0, openUntil = 0))
    }

    // ---------- Celebraciones de racha ----------

    /**
     * Rachas que han subido desde la última vez que se celebraron. La primera vez solo guarda
     * los valores actuales (para no celebrar de golpe todo lo que ya había).
     */
    fun collectStreakUps(): List<com.alberto.upgrade.ui.screens.StreakUp> {
        val d = data
        val today = LocalDate.now()
        val cur = mutableMapOf("xp" to streakOf(d, today), "focus" to focusStreakOf(d, today))
        d.routines.forEach { r -> cur["r:" + r.id] = routineStreak(d, r, today) }
        if (!d.celebratedInit) {
            update { it.copy(celebrated = cur, celebratedInit = true) }
            return emptyList()
        }
        val ups = cur.filter { (k, v) -> v > 0 && v > (d.celebrated[k] ?: 0) }
        if (cur != d.celebrated) update { it.copy(celebrated = cur) }
        return ups.map { (k, v) ->
            when {
                k == "xp" -> com.alberto.upgrade.ui.screens.StreakUp(k, "xp", "Racha de XP", com.alberto.upgrade.ui.Ic.flameFill, v, record = v >= d.bestStreak)
                k == "focus" -> com.alberto.upgrade.ui.screens.StreakUp(k, "focus", "Racha de foco", com.alberto.upgrade.ui.Ic.shieldCheck, v, record = v >= d.bestFocusStreak)
                else -> {
                    val r = d.routines.firstOrNull { "r:" + it.id == k }
                    val xp = r?.let { runOf(d, it.id, today)?.xp } ?: 0
                    com.alberto.upgrade.ui.screens.StreakUp(
                        k, "routine", r?.name ?: "Rutina",
                        com.alberto.upgrade.ui.iconByKey(r?.icon) ?: com.alberto.upgrade.ui.Ic.calendarClock, v, xp
                    )
                }
            }
        }.sortedBy { when (it.kind) { "xp" -> 0; "focus" -> 1; else -> 2 } }
    }

    // ---------- Fisio ----------

    fun logFisio(): Award {
        var award: Award? = null
        update { d ->
            val key = todayKey()
            if (key in d.fisioLog) { award = Award(0, false, null, streakOf(d)); return@update d }
            val base = d.copy(fisioLog = (d.fisioLog + key).takeLast(400))
            val (n, a) = awardIn(base, d.xp.fisio, key, "Ejercicios del fisio", "fisio"); award = a; n
        }
        return award!!
    }

    fun unlogFisio() {
        update { d ->
            val key = todayKey()
            if (key !in d.fisioLog) return@update d
            awardIn(d.copy(fisioLog = d.fisioLog - key), -d.xp.fisio, key, "Ejercicios del fisio").first
        }
    }

    // ---------- Foco ----------

    fun startFocus(ctx: Context, minutes: Int, taskId: String? = null) {
        val now = System.currentTimeMillis()
        val end = now + minutes * 60_000L
        logUsage(now) { u, _ -> u.copy(focusStarts = u.focusStarts + 1) }
        update { it.copy(focus = FocusState(now, end, minutes, false, taskId)) }
        AlarmScheduler.scheduleFocusEnd(ctx, end)
    }

    /** Lanza el foco de una sesión de Repaso; al acabar, la sesión queda hecha. */
    fun startRepaso(ctx: Context, t: TaskItem) {
        if (focusActive()) return
        startFocus(ctx, (t.minutes ?: 60).coerceIn(5, 240), t.id)
    }

    fun cancelFocus(ctx: Context) {
        update { it.copy(focus = FocusState()) }
        AlarmScheduler.cancelFocusEnd(ctx)
        AlarmScheduler.cancelFocusExtraEnd(ctx)
    }

    /** Minutos de foco ya hechos en la sesión actual. */
    fun focusElapsed(now: Long = System.currentTimeMillis()): Int {
        val f = data.focus
        if (f.startedAt == 0L) return 0
        return ((now - f.startedAt) / 60_000L).toInt().coerceIn(0, f.minutes)
    }

    /** Rendirse: se conservan los bloques de 30 min ya completos (si se pasó de 30). */
    fun quitFocus(ctx: Context): Award? {
        var award: Award? = null
        val now = System.currentTimeMillis()
        // Rendirse en los 5 primeros minutos cuenta como cancelar; después, como rendirse a medias.
        if (data.focus.endAt > now) {
            val early = now - data.focus.startedAt < 5 * 60_000L
            logUsage(now) { u, _ -> if (early) u.copy(focusCancels = u.focusCancels + 1) else u.copy(focusQuits = u.focusQuits + 1) }
        }
        update { d ->
            award = null
            val f = d.focus
            if (f.endAt == 0L) return@update d
            val done = ((now - f.startedAt) / 60_000L).toInt().coerceIn(0, f.minutes)
            val blocks = (done / 30) * 30
            if (blocks < 30) return@update d.copy(focus = FocusState())
            val key = todayKey()
            val fl = d.focusLog.toMutableMap().apply { put(key, (get(key) ?: 0) + blocks) }
            val (n, a) = awardIn(d.copy(focusLog = fl), focusXp(d, blocks), key, "Foco · $blocks min", "focus")
            award = a
            n.copy(focus = FocusState())
        }
        AlarmScheduler.cancelFocusEnd(ctx)
        return award
    }

    fun focusActive(now: Long = System.currentTimeMillis()): Boolean = focusOn(data, now)

    /** Cobra los bloques de 10 min de tiempo extra ya completos; a los 60 min el tiempo extra se cierra solo. */
    fun tickFocusExtra(now: Long = System.currentTimeMillis()): Award? {
        val f = data.focus
        if (!f.extra || f.endAt == 0L || now < f.endAt) return null
        val closing = now >= extraEnd(data)
        if (extraMinutes(data, now) / EXTRA_BLOCK_MIN <= f.extraPaid && !closing) return null
        var award: Award? = null
        update { d ->
            award = null
            val g = d.focus
            if (!g.extra) return@update d
            val fresh = (extraMinutes(d, now) / EXTRA_BLOCK_MIN - g.extraPaid).coerceAtLeast(0)
            var n = d
            if (fresh > 0) {
                val key = todayKey()
                val fl = d.focusLog.toMutableMap().apply { put(key, (get(key) ?: 0) + fresh * EXTRA_BLOCK_MIN) }
                val (m, a) = awardIn(d.copy(focusLog = fl), fresh * d.xp.focusExtra10, key, "Tiempo extra · ${fresh * EXTRA_BLOCK_MIN} min", "focus")
                award = a
                n = m
            }
            if (now >= extraEnd(d)) n.copy(focus = FocusState()) else n.copy(focus = g.copy(extraPaid = g.extraPaid + fresh))
        }
        return award
    }

    /** Terminar el tiempo extra: cobra lo completo y apunta los minutos sueltos al foco del día (sin XP). */
    fun finishFocusExtra(ctx: Context, now: Long = System.currentTimeMillis()): Award? {
        val award = tickFocusExtra(now)
        update { d ->
            val g = d.focus
            if (!g.extra) return@update d
            val loose = extraMinutes(d, now) - g.extraPaid * EXTRA_BLOCK_MIN
            val key = todayKey()
            val fl = if (loose > 0) d.focusLog.toMutableMap().apply { put(key, (get(key) ?: 0) + loose) } else d.focusLog
            d.copy(focus = FocusState(), focusLog = fl)
        }
        AlarmScheduler.cancelFocusExtraEnd(ctx)
        return award
    }

    /** Si el foco ha terminado y no se ha cobrado, da la XP. */
    fun claimFocus(now: Long = System.currentTimeMillis()): Award? {
        var award: Award? = null
        update { d ->
            award = null
            val f = d.focus
            if (f.rewarded || f.endAt == 0L || now < f.endAt) return@update d
            val xp = focusXp(d, f.minutes)
            val key = todayKey()
            val fl = d.focusLog.toMutableMap().apply { put(key, (get(key) ?: 0) + f.minutes) }
            val (n, a) = awardIn(d.copy(focusLog = fl), xp, key, "Foco · ${f.minutes} min", "focus")
            award = a
            val tasks = f.taskId?.let { id ->
                n.tasks.map { if (it.id == id && !it.done) it.copy(done = true, doneAt = now, xpAwarded = 0, xpDate = key) else it }
            } ?: n.tasks
            // El reloj sigue: empieza el tiempo extra.
            n.copy(focus = f.copy(rewarded = true, extra = true, extraPaid = 0, taskId = null), tasks = tasks)
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
            else { val (n, a) = awardIn(base, 10, key, "Cerrar el día", "review"); award = a; n }
        }
        return award!!
    }

    // ---------- IA ----------

    fun recordAiTiming(t: AiTiming) = update { d -> d.copy(aiTimings = (d.aiTimings + t).takeLast(10)) }

    fun addAiExample(input: String, output: String) {
        if (input.isBlank()) return
        update { d -> d.copy(aiExamples = (d.aiExamples + AiExample(input.trim().take(400), output.take(1200))).takeLast(5)) }
    }

    /**
     * Aplica lo que el usuario ha confirmado. Devuelve el último premio (si se completó algo).
     * Las propuestas sin tarea elegida ([Proposal.needsChoice]) se ignoran.
     */
    fun applyProposals(items: List<Proposal>): Award? {
        var award: Award? = null
        val creates = items.filter { it.op == Op.CREAR }.map { it.toTask() }
        if (creates.isNotEmpty()) addTasks(creates)
        items.filter { it.op != Op.CREAR && !it.needsChoice }.forEach { p ->
            val id = p.targetId ?: return@forEach
            val t = data.tasks.firstOrNull { it.id == id } ?: return@forEach
            when (p.op) {
                Op.MOVER, Op.EDITAR -> saveTask(p.applyTo(t))
                Op.COMPLETAR -> if (!t.done) toggleTask(id)?.let { award = it }
                Op.BORRAR -> deleteTask(id)
                Op.CREAR -> Unit
            }
        }
        return award
    }

    // ---------- Copia de seguridad ----------

    /** La clave de IA nunca sale en la copia: vive cifrada solo en este móvil. */
    fun exportJson(): String = json.encodeToString(AppData.serializer(), data.copy(geminiKey = ""))

    fun importJson(text: String): Boolean {
        val d = runCatching { json.decodeFromString(AppData.serializer(), text) }.getOrNull() ?: return false
        update { migrateAi(d).copy(seeded = true, permissionsShown = true) }
        return true
    }
}
