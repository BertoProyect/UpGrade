package com.alberto.upgrade.block

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.LIST_ALLOWED
import com.alberto.upgrade.data.LIST_BLOCKED
import com.alberto.upgrade.data.LIST_WAIT
import com.alberto.upgrade.data.appListOf
import com.alberto.upgrade.data.blockSessionActive
import com.alberto.upgrade.data.currentSession
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

sealed interface UnlockState {
    data object None : UnlockState
    data class Waiting(val msLeft: Long) : UnlockState
    data class Active(val msLeft: Long) : UnlockState
}

object BlockRules {
    /**
     * Nunca se bloquean (ni salen en el Control de apps): sistema, llamadas entrantes, alarmas,
     * teclado e instalador. El marcador (Teléfono) sí sale: lo eliges tú, con aviso si lo sacas.
     */
    private val alwaysContains = listOf(
        "systemui", "launcher", "incallui", "telecom",
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
        decide(ctx, pkg, d) == null

    /** Nombre visible de una app. */
    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun defaultDialer(ctx: Context): String? =
        runCatching { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()

    /** Primera vez: Teléfono, Mensajes, Chrome, Moodle y la calculadora permitidas; el resto, con espera. */
    fun defaultAllowed(ctx: Context): List<String> {
        val out = mutableListOf("com.android.chrome")
        defaultDialer(ctx)?.let { out += it }
        runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(ctx)?.let { out += it } }
        runCatching {
            ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }
                .filter { p -> val l = p.lowercase(); l.contains("moodle") || l.contains("calculator") }
                .forEach { out += it }
        }
        return out.distinct()
    }

    /**
     * Qué hacer al abrir [pkg]: null = dejarla; [LIST_WAIT] = pantalla con espera; [LIST_BLOCKED] = bloqueada del todo.
     * Solo bloquea durante un foco o una rutina en curso.
     */
    fun decide(ctx: Context, pkg: String, d: AppData, now: Long = System.currentTimeMillis()): String? {
        if (!d.block.enabled) return null
        if (!blockSessionActive(d, now)) return null
        if (isSystemSafe(ctx, pkg)) return null
        return when (appListOf(d, pkg)) {
            LIST_ALLOWED -> null
            LIST_BLOCKED -> LIST_BLOCKED
            else -> if (waitOpen(d, pkg, now)) null else LIST_WAIT
        }
    }

    /** ¿Está abierta la ventana de 5 min de esta app en la sesión actual? */
    fun waitOpen(d: AppData, pkg: String, now: Long = System.currentTimeMillis()): Boolean {
        val w = d.wait
        return w.pkg == pkg && w.session == currentSession(d, now) && now >= w.openFrom && now < w.openUntil
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

    fun shouldBlock(d: AppData, now: Long = System.currentTimeMillis()): Boolean =
        d.block.enabled && blockSessionActive(d, now)

    const val GRANT_CMD = "adb shell pm grant com.alberto.upgrade android.permission.WRITE_SECURE_SETTINGS"

    fun canWriteSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Android (sobre todo ColorOS) apaga la accesibilidad de la app al actualizarla. Con el permiso
     * WRITE_SECURE_SETTINGS (concedido una vez por ADB) la volvemos a encender sola. Devuelve si quedó activa.
     */
    fun ensureServiceEnabled(ctx: Context): Boolean {
        if (isServiceEnabled(ctx)) return true
        if (!canWriteSecure(ctx)) return false
        return runCatching {
            val me = ComponentName(ctx, BlockerService::class.java).flattenToString()
            val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val list = cur.split(':').filter { it.isNotBlank() && it != me } + me
            Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
            Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }

    fun isServiceEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, BlockerService::class.java)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it)?.let { c -> c.packageName == me.packageName && c.className == me.className } ?: false
        }
    }
}
