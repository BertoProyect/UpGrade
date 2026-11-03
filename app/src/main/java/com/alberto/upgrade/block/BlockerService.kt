package com.alberto.upgrade.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.alberto.upgrade.data.Repo

class BlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg: String? = null
    private var lastKick = 0L

    private val ticker = object : Runnable {
        override fun run() {
            lastPkg?.let { check(it) }
            handler.postDelayed(this, 20_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Repo.init(this)
        handler.postDelayed(ticker, 20_000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        lastPkg = pkg
        check(pkg)
    }

    private fun check(pkg: String) {
        Repo.init(this)
        val d = Repo.data
        if (!BlockRules.shouldBlock(d)) return
        if (BlockRules.isAllowed(this, pkg, d)) return
        val now = System.currentTimeMillis()
        if (now - lastKick < 800) return
        lastKick = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        val i = Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("app", pkg)
        runCatching { startActivity(i) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
