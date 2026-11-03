package com.alberto.upgrade.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alberto.upgrade.MainActivity
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.mostUrgent
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.UpgradeTheme
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.UIcon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            UpgradeTheme {
                val data by Repo.state.collectAsStateWithLifecycle()
                BlockContent(
                    data,
                    onBackToWork = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open", "focus"))
                        finish()
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun BlockContent(data: AppData, onBackToWork: () -> Unit, onClose: () -> Unit) {
    val ui = LocalUi.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val focusLeft = data.focus.endAt - now
    val reason = when {
        focusLeft > 0 -> "Modo Foco · quedan ${focusLeft / 60000 + 1} min"
        BlockRules.scheduleActive(data, now) -> "Franja de estudio hasta las ${minStr(data.block.to)}"
        else -> "Bloqueo terminado"
    }
    val unlock = BlockRules.unlockState(data, now)
    val urgent = mostUrgent(data)

    LaunchedEffect(unlock is UnlockState.Active, BlockRules.shouldBlock(data, now)) {
        if (!BlockRules.shouldBlock(data, now)) onClose()
    }

    Column(
        Modifier.fillMaxSize().background(ui.bg).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(ui.primary.copy(alpha = if (ui.dark) 0.14f else 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            UIcon(Ic.lock, ui.accentInk, size = 56.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Ahora no.", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 34.sp)
        Text(reason, color = ui.accentInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
        Text(
            "Esto es lo que te roba la tarde. Lo que hagas ahora decide si esta noche estás orgulloso.",
            color = ui.text, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp)
        )
        if (urgent != null) {
            Spacer(Modifier.height(20.dp))
            ChunkyCard(Modifier.fillMaxWidth()) {
                Text("Lo que toca:", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(urgent.title, color = ui.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        ChunkyButton("Volver a lo mío", onBackToWork, Modifier.fillMaxWidth(), icon = Ic.target)
        Spacer(Modifier.height(10.dp))
        when (unlock) {
            is UnlockState.None -> {
                GhostButton("Necesito el móvil", { Repo.requestUnlock() }, Modifier.fillMaxWidth(), height = 46.dp, icon = Ic.phone)
                Text(
                    "Se desbloquea tras ${data.block.waitMinutes} min de espera, durante ${data.block.unlockMinutes} min.",
                    color = ui.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            is UnlockState.Waiting -> {
                val s = unlock.msLeft / 1000
                Text(
                    "Desbloqueo en %d:%02d. Si de verdad lo necesitas, espera. Si no, vuelve a lo tuyo.".format(s / 60, s % 60),
                    color = ui.muted, fontSize = 14.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                GhostButton("Cancelar desbloqueo", { Repo.cancelUnlock() }, Modifier.fillMaxWidth(), height = 44.dp)
            }
            is UnlockState.Active -> {}
        }
    }
}
