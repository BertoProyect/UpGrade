package com.alberto.upgrade.block

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.alberto.upgrade.data.AppData
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

sealed interface UnlockState {
    data object None : UnlockState
    data class Waiting(val msLeft: Long) : UnlockState
    data class Active(val msLeft: Long) : UnlockState
}

object BlockRules {
    /** Nunca se bloquean: sistema, teléfono, alarmas, teclado, instalador. */
    private val alwaysContains = listOf(
        "systemui", "launcher", "incallui", "dialer", "telecom", "contacts",
        "deskclock", "alarmclock", "inputmethod", "keyboard", "packageinstaller",
        "permissioncontroller", "emergency"
    )
    private val alwaysExact = setOf("android", "com.android.phone")

    @Volatile private var cache: Set<String>? = null

    private fun systemSafe(ctx: Context): Set<String> {
        cache?.let { return it }
        val s = mutableSetOf<String>()
        val pm = ctx.packageManager
        runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .forEach { s += it.activityInfo.packageName }
        }
        runCatching {
            ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { s += it }
        }
        runCatching {
            ctx.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { s += it.packageName }
        }
        cache = s
        return s
    }

    /** Apps del sistema que nunca se bloquean (no salen en la lista). */
    fun isSystemSafe(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName) return true
        if (pkg in alwaysExact) return true
        val low = pkg.lowercase()
        if (alwaysContains.any { low.contains(it) }) return true
        return pkg in systemSafe(ctx)
    }

    fun isAllowed(ctx: Context, pkg: String, d: AppData): Boolean =
        pkg in d.block.allowed || isSystemSafe(ctx, pkg)

    /** Primera vez: Chrome y Moodle permitidas, el resto bloqueadas. */
    fun defaultAllowed(ctx: Context): List<String> {
        val out = mutableListOf("com.android.chrome")
        runCatching {
            ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }
                .filter { it.lowercase().contains("moodle") }
                .forEach { out += it }
        }
        return out.distinct()
    }

    fun unlockState(d: AppData, now: Long = System.currentTimeMillis()): UnlockState {
        val req = d.block.unlockRequestedAt
        if (req == 0L) return UnlockState.None
        val startUnlock = req + d.block.waitMinutes * 60_000L
        val endUnlock = startUnlock + d.block.unlockMinutes * 60_000L
        return when {
            now < startUnlock -> UnlockState.Waiting(startUnlock - now)
            now < endUnlock -> UnlockState.Active(endUnlock - now)
            else -> UnlockState.None
        }
    }

    fun scheduleActive(d: AppData, now: Long = System.currentTimeMillis()): Boolean {
        if (!d.block.scheduleEnabled) return false
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
        val m = t.hour * 60 + t.minute
        return t.dayOfWeek.value in d.block.days && m >= d.block.from && m < d.block.to
    }

    fun shouldBlock(d: AppData, now: Long = System.currentTimeMillis()): Boolean {
        if (!d.block.enabled) return false
        if (unlockState(d, now) is UnlockState.Active) return false
        return d.focus.endAt > now || scheduleActive(d, now)
    }

    fun isServiceEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, BlockerService::class.java)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it)?.let { c -> c.packageName == me.packageName && c.className == me.className } ?: false
        }
    }
}
