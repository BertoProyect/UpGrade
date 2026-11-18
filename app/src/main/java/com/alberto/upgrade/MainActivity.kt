package com.alberto.upgrade

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.notify.AlarmScheduler
import com.alberto.upgrade.notify.Notifier
import com.alberto.upgrade.ui.AppRoot
import com.alberto.upgrade.ui.UpgradeTheme

class MainActivity : ComponentActivity() {
    companion object {
        val pendingOpen = mutableStateOf<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        com.alberto.upgrade.data.Telemetry.init(this)
        Notifier.ensureChannels(this)
        AlarmScheduler.scheduleAll(this)
        com.alberto.upgrade.block.BlockRules.ensureServiceEnabled(this)
        handle(intent)
        setContent { UpgradeTheme { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        Repo.refreshWidget()
        Repo.logOpen()
        com.alberto.upgrade.data.Telemetry.resume()
        com.alberto.upgrade.data.Updater.installIfNeedsConfirm(this)
        com.alberto.upgrade.data.Updater.check(this)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) com.alberto.upgrade.data.Updater.installIfReady(this)
    }

    override fun onPause() {
        com.alberto.upgrade.data.Telemetry.pause()
        super.onPause()
    }

    private fun handle(i: Intent?) {
        i?.getStringExtra("open")?.let { pendingOpen.value = it }
    }
}
