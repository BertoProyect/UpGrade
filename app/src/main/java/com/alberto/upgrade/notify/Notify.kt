package com.alberto.upgrade.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alberto.upgrade.MainActivity
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Kind
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.classesOn
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.pendingExams
import com.alberto.upgrade.data.streakOf
import com.alberto.upgrade.data.xpOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class AlarmType(val code: Int) { MORNING(1), AFTERNOON(2), STREAK(3), REVIEW(4), BLOCK_START(5), CALL(6), FOCUS_END(10) }

object Notifier {
    const val CH_PLAN = "plan"
    const val CH_ALERT = "avisos"
    const val CH_FOCUS = "foco"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PLAN, "Plan del día", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Exámenes y racha", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_FOCUS, "Modo Foco", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(ctx: Context, id: Int, channel: String, title: String, text: String, open: String? = null) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (open != null) putExtra("open", open)
        }
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(com.alberto.upgrade.R.drawable.ic_logo)
            .setColor(0xFF86AD12.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

object AlarmScheduler {
    private fun pi(ctx: Context, type: AlarmType): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).putExtra("type", type.name)
        return PendingIntent.getBroadcast(ctx, type.code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun setAt(ctx: Context, type: AlarmType, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val p = pi(ctx, type)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    private fun nextAt(minuteOfDay: Int, days: List<Int>? = null): Long {
        val now = LocalDateTime.now()
        var date = LocalDate.now()
        repeat(8) {
            val t = date.atStartOfDay().plusMinutes(minuteOfDay.toLong())
            val okDay = days == null || date.dayOfWeek.value in days
            if (okDay && t.isAfter(now.plusSeconds(20))) {
                return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            date = date.plusDays(1)
        }
        return System.currentTimeMillis() + 24 * 3600_000L
    }

    fun scheduleAll(ctx: Context) {
        Repo.init(ctx)
        val d = Repo.data
        setAt(ctx, AlarmType.MORNING, nextAt(d.notif.morning))
        setAt(ctx, AlarmType.AFTERNOON, nextAt(d.notif.afternoon))
        setAt(ctx, AlarmType.STREAK, nextAt(d.notif.streak))
        setAt(ctx, AlarmType.REVIEW, nextAt(d.notif.review))
        if (d.block.enabled && d.block.scheduleEnabled && d.block.days.isNotEmpty()) {
            setAt(ctx, AlarmType.BLOCK_START, nextAt(d.block.from, d.block.days))
        }
        if (d.focus.endAt > System.currentTimeMillis()) scheduleFocusEnd(ctx, d.focus.endAt)
        nextTimed(d)?.let { (_, at) -> setAt(ctx, AlarmType.CALL, at - 10 * 60_000L) }
    }

    /** Próxima tarea o llamada con hora (aviso 10 min antes). */
    fun nextTimed(d: AppData): Pair<com.alberto.upgrade.data.TaskItem, Long>? {
        val now = System.currentTimeMillis()
        return d.tasks.filter { !it.done && it.time != null && it.due != null }
            .mapNotNull { t ->
                val date = com.alberto.upgrade.data.parseDate(t.due) ?: return@mapNotNull null
                val at = date.atStartOfDay().plusMinutes((t.time ?: 0).toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (at - 10 * 60_000L > now + 20_000) t to at else null
            }
            .minByOrNull { it.second }
    }

    fun scheduleFocusEnd(ctx: Context, at: Long) = setAt(ctx, AlarmType.FOCUS_END, at)

    fun cancelFocusEnd(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pi(ctx, AlarmType.FOCUS_END))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        val type = runCatching { AlarmType.valueOf(intent.getStringExtra("type") ?: "") }.getOrNull() ?: return
        val d = Repo.data
        val today = LocalDate.now()
        when (type) {
            AlarmType.MORNING -> morning(context, d, today)
            AlarmType.AFTERNOON -> afternoon(context, d, today)
            AlarmType.STREAK -> {
                val xp = xpOn(d, today)
                if (xp < com.alberto.upgrade.data.goalFor(d)) {
                    val s = streakOf(d)
                    val title = if (s > 0) "Tu racha de $s días está en peligro" else "Hoy aún no has cumplido tu meta"
                    Notifier.post(context, 103, Notifier.CH_ALERT, title, "Te faltan ${com.alberto.upgrade.data.goalFor(d) - xp} XP. Unas tareas o 30 min de foco y listo.")
                }
            }
            AlarmType.REVIEW -> {
                if (!d.reviews.containsKey(today.key())) {
                    Notifier.post(context, 104, Notifier.CH_PLAN, "Cierra el día", "2 minutos. ¿Orgulloso de hoy? Deja decidido lo primero de mañana.", "review")
                }
            }
            AlarmType.BLOCK_START -> {
                if (d.block.enabled && d.block.scheduleEnabled) {
                    Notifier.post(context, 105, Notifier.CH_FOCUS, "Empieza tu franja de estudio", "Hasta las ${minStr(d.block.to)} las apps elegidas están bloqueadas. Ponte con lo primero.", "focus")
                }
            }
            AlarmType.CALL -> {
                d.tasks.filter { !it.done && it.time != null && it.due == today.key() }
                    .filter { t -> val m = (t.time ?: 0) - com.alberto.upgrade.data.nowMin(); m in 0..15 }
                    .forEach { t ->
                        val what = if (t.kind == Kind.LLAMADA) "Llamada" else "Toca"
                        Notifier.post(context, 200 + (t.id.hashCode() and 0xFF), Notifier.CH_ALERT, "$what a las ${minStr(t.time ?: 0)}", t.title)
                    }
            }
            AlarmType.FOCUS_END -> {
                val a = Repo.claimFocus()
                if (a != null) Notifier.post(context, 110, Notifier.CH_FOCUS, "Foco completado", "+${a.xp} XP. Buen bloque. Descansa 5 minutos y sigue.", "focus")
            }
        }
        AlarmScheduler.scheduleAll(context)
        Repo.refreshWidget()
    }

    private fun morning(ctx: Context, d: AppData, today: LocalDate) {
        val classes = classesOn(d, today)
        val due = d.tasks.count { !it.done && it.due == today.key() }
        val overdue = d.tasks.count { !it.done && it.due != null && it.due < today.key() }
        val exams = pendingExams(d, today, 3)
        val parts = mutableListOf<String>()
        if (classes.isNotEmpty()) parts += "${classes.size} clases desde las ${minStr(classes.first().period.start)}"
        if (due > 0) parts += "$due para hoy"
        if (overdue > 0) parts += "$overdue atrasadas"
        exams.firstOrNull()?.let { (t, n) -> parts += if (n == 0L) "HOY ${t.title}" else "${t.title} en $n días" }
        if (parts.isEmpty()) parts += "Nada urgente. Adelanta algo y suma XP."
        Notifier.post(ctx, 101, Notifier.CH_PLAN, "Tu día", parts.joinToString(" · "))
    }

    private fun afternoon(ctx: Context, d: AppData, today: LocalDate) {
        val tomorrow = today.plusDays(1).key()
        val homework = d.tasks.filter { !it.done && it.due == tomorrow && it.kind != Kind.EXAMEN }
        val exam = pendingExams(d, today, 3).firstOrNull { it.second >= 1 }
        if (exam != null) {
            val (t, n) = exam
            val title = if (n == 1L) "${t.title} MAÑANA" else "${t.title} en $n días"
            Notifier.post(ctx, 102, Notifier.CH_ALERT, title, "Hoy toca repaso. Abre UpGrade y pon 25 min de foco.", "focus")
        } else if (homework.isNotEmpty()) {
            Notifier.post(ctx, 102, Notifier.CH_ALERT, "${homework.size} cosa(s) para mañana", homework.joinToString(", ") { it.title })
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        Notifier.ensureChannels(context)
        AlarmScheduler.scheduleAll(context)
    }
}
