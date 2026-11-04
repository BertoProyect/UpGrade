package com.alberto.upgrade.block

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.alberto.upgrade.data.LIST_BLOCKED
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.notify.Notifier

/**
 * Vigila qué app se abre. Durante un foco o una rutina, saca de las apps que no tocan y abre la
 * pantalla de bloqueo. También arranca las rutinas «al primer desbloqueo» y las cierra al acabar.
 */
class BlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg: String? = null
    private var lastKick = 0L
    private var openNotified = 0L

    private val ticker = object : Runnable {
        override fun run() {
            Repo.init(this@BlockerService)
            Repo.tickRoutines()
            lastPkg?.let { check(it) }
            updateOpenNotice()
            // Con una app abierta tras la espera se revisa cada 5 s para cortarla a su hora.
            val fast = Repo.data.wait.openUntil > System.currentTimeMillis()
            handler.postDelayed(this, if (fast) 5_000 else 15_000)
        }
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                Repo.init(context)
                Repo.onUnlock()
                lastPkg?.let { check(it) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Repo.init(this)
        val f = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(unlockReceiver, f, Context.RECEIVER_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(unlockReceiver, f)
        handler.postDelayed(ticker, 3_000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        lastPkg = pkg
        check(pkg)
    }

    private fun check(pkg: String) {
        Repo.init(this)
        if (pkg == packageName) return
        val mode = BlockRules.decide(this, pkg, Repo.data) ?: return
        val now = System.currentTimeMillis()
        if (now - lastKick < 800) return
        lastKick = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        val i = Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("app", pkg)
            .putExtra("mode", if (mode == LIST_BLOCKED) "blocked" else "wait")
        runCatching { startActivity(i) }
    }

    private var readyNotified = 0L

    /** Aviso al acabar la espera y aviso fijo con cuenta atrás mientras la app está abierta. */
    private fun updateOpenNotice() {
        val w = Repo.data.wait
        val now = System.currentTimeMillis()
        if (w.pkg != null && now >= w.openFrom && now < w.openUntil && readyNotified != w.openFrom && lastPkg != w.pkg) {
            readyNotified = w.openFrom
            Notifier.post(this, 121, Notifier.CH_FOCUS, "Ya puedes abrir ${BlockRules.label(this, w.pkg)}", "Tienes 5 min. Luego se vuelve a bloquear.")
        }
        if (w.pkg != null && now >= w.openFrom && now < w.openUntil) {
            if (openNotified != w.openUntil) {
                openNotified = w.openUntil
                Notifier.postCountdown(this, w.openUntil, BlockRules.label(this, w.pkg))
            }
        } else if (openNotified != 0L) {
            openNotified = 0L
            Notifier.cancel(this, Notifier.ID_COUNTDOWN)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        runCatching { unregisterReceiver(unlockReceiver) }
        super.onDestroy()
    }
}
