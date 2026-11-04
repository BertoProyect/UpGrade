package com.alberto.upgrade.block

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.Tag
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.areaIcon
import com.alberto.upgrade.ui.kindIcon
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

    Column(Modifier.fillMaxSize().background(ui.bg).safeDrawingPadding().padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f).padding(top = 48.dp)) {
            val shape = RoundedCornerShape(22.dp)
            Box(Modifier.size(64.dp, 68.dp)) {
                Box(Modifier.size(64.dp).offset(y = 4.dp).background(ui.hairline, shape))
                Box(Modifier.size(64.dp).clip(shape).background(ui.card).border(2.dp, ui.hairline, shape), contentAlignment = Alignment.Center) {
                    UIcon(Ic.lock, ui.ink, size = 30.dp)
                }
            }
            Text("Ahora no.", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 48.sp, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 26.dp))
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                UIcon(if (focusLeft > 0) Ic.target else Ic.calendarClock, ui.muted, size = 18.dp)
                Text(reason, color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            }
            if (urgent != null) {
                Spacer(Modifier.height(28.dp))
                ChunkyCard(Modifier.fillMaxWidth()) {
                    SerifText("Lo que toca:", color = ui.muted, fontSize = 18.sp)
                    Text(urgent.title, color = ui.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val subject = data.subject(urgent.subjectId)
                        val area = data.area(urgent.areaId)
                        if (subject != null) Tag(subject.name, icon = kindIcon(urgent.kind))
                        else if (area != null) Tag(area.name, icon = areaIcon(area.id))
                        Text("+${com.alberto.upgrade.data.xpIfDone(Repo.data, urgent)} XP", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        ChunkyButton("Volver a lo mío", onBackToWork, Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (unlock) {
                is UnlockState.None -> {
                    TextAction("Necesito el móvil", { Repo.requestUnlock() })
                    Text(
                        "Se desbloquea tras ${data.block.waitMinutes} min de espera, durante ${data.block.unlockMinutes} min.",
                        color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                    )
                }
                is UnlockState.Waiting -> {
                    val s = unlock.msLeft / 1000
                    Text(
                        "Desbloqueo en %d:%02d. Si de verdad lo necesitas, espera. Si no, vuelve a lo tuyo.".format(s / 60, s % 60),
                        color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextAction("Cancelar desbloqueo", { Repo.cancelUnlock() })
                }
                is UnlockState.Active -> {}
            }
        }
    }
}
