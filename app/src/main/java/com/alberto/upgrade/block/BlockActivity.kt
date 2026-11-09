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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalContext
import com.alberto.upgrade.data.activeRuns
import com.alberto.upgrade.data.currentSession
import com.alberto.upgrade.data.nextWait
import com.alberto.upgrade.data.xpIfDone
import com.alberto.upgrade.ui.iconByKey

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        val pkg = intent.getStringExtra("app") ?: ""
        val blocked = intent.getStringExtra("mode") == "blocked"
        val label = if (pkg.isBlank()) "Esta app" else BlockRules.label(this, pkg)
        // Para «Tus hábitos»: cuántas veces y con qué app intentas saltarte el bloqueo.
        if (savedInstanceState == null && pkg.isNotBlank()) Repo.logBlocked(label)
        setContent {
            UpgradeTheme {
                val data by Repo.state.collectAsStateWithLifecycle()
                BlockContent(
                    data, pkg, label, blocked,
                    onBackToWork = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open", "focus"))
                        finish()
                    },
                    onOpenApp = {
                        packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
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

/**
 * Pantalla de bloqueo. Con espera: «Necesito el móvil» → cuenta atrás → «Abrir · 5 min».
 * Bloqueada del todo: sin botón de desbloqueo hasta que termine el foco o la rutina.
 */
@Composable
private fun BlockContent(
    data: AppData, pkg: String, label: String, blocked: Boolean,
    onBackToWork: () -> Unit, onOpenApp: () -> Unit, onClose: () -> Unit
) {
    val ui = LocalUi.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }

    val focusLeft = data.focus.endAt - now
    val run = activeRuns(data, now).minByOrNull { it.startedAt }
    val routine = run?.let { r -> data.routines.firstOrNull { it.id == r.routineId } }
    val extraOn = focusLeft <= 0 && com.alberto.upgrade.data.inFocusExtra(data, now)
    val until = when {
        focusLeft > 0 -> data.focus.endAt
        extraOn -> com.alberto.upgrade.data.extraEnd(data)
        run != null -> run.endAt
        else -> now
    }
    val untilTxt = minStr(com.alberto.upgrade.data.minutesOf(until))
    val urgent = data.focus.taskId?.let { id -> data.tasks.firstOrNull { it.id == id && !it.done } } ?: mostUrgent(data)

    LaunchedEffect(BlockRules.shouldBlock(data, now)) {
        if (!BlockRules.shouldBlock(data, now)) onClose()
    }

    val w = data.wait
    val session = currentSession(data, now)
    val mine = w.pkg == pkg && w.session == session
    val waiting = mine && now < w.openFrom
    val ready = mine && now >= w.openFrom && now < w.openUntil
    val usesNow = if (w.session == session) w.uses else 0

    Column(Modifier.fillMaxSize().background(ui.bg).safeDrawingPadding().padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f).padding(top = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(14.dp)
                Box(Modifier.size(44.dp).clip(shape).background(ui.surface).border(2.dp, ui.hairline, shape), contentAlignment = Alignment.Center) {
                    Text(label.take(1).uppercase(), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                Text(
                    if (blocked) "$label está bloqueada del todo" else if (waiting) "Podrás abrir $label en" else "$label está en espera",
                    color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp)
                )
            }
            AnimatedContent(waiting, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "lock") { counting ->
                if (counting) {
                    val left = ((w.openFrom - now) / 1000).coerceAtLeast(0)
                    Column {
                        Text("%d:%02d".format(left / 60, left % 60), color = ui.ink, fontWeight = FontWeight.Black, fontSize = 84.sp, letterSpacing = (-3).sp, modifier = Modifier.padding(top = 18.dp))
                        val total = (w.openFrom - w.requestedAt).coerceAtLeast(1)
                        com.alberto.upgrade.ui.ProgressBar(1f - (w.openFrom - now) / total.toFloat(), ui.primary, Modifier.fillMaxWidth().padding(top = 8.dp), height = 10.dp)
                    }
                } else {
                    Text("Ahora no.", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 52.sp, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 22.dp))
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                UIcon(if (focusLeft > 0 || extraOn) Ic.target else (iconByKey(routine?.icon) ?: Ic.calendarClock), ui.muted, size = 18.dp)
                Text(
                    when {
                        focusLeft > 0 -> "Modo Foco · quedan ${focusLeft / 60000 + 1} min"
                        extraOn -> "Modo Foco · tiempo extra · +${com.alberto.upgrade.data.extraMinutes(data, now)} min"
                        routine != null -> "${routine.name} · hasta las $untilTxt"
                        else -> "Bloqueo terminado"
                    },
                    color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (blocked) {
                SerifText("Hasta las $untilTxt. Sin excepciones.", fontSize = 24.sp, modifier = Modifier.padding(top = 18.dp))
            }
            if (urgent != null) {
                Spacer(Modifier.height(24.dp))
                ChunkyCard(Modifier.fillMaxWidth()) {
                    SerifText("Lo que toca:", color = ui.muted, fontSize = 18.sp)
                    Text(urgent.title, color = ui.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val subject = data.subject(urgent.subjectId)
                        val area = data.area(urgent.areaId)
                        if (subject != null) Tag(subject.name, icon = kindIcon(urgent.kind))
                        else if (area != null) Tag(area.name, icon = areaIcon(area.id))
                        val xp = xpIfDone(data, urgent)
                        if (xp > 0) Text("${urgent.kind.label} · +$xp XP", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        ChunkyButton("Volver a lo mío", onBackToWork, Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                blocked -> Text("No hay botón de desbloqueo.", color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                ready -> {
                    GhostButton("Abrir $label · 5 min", onOpenApp, Modifier.fillMaxWidth())
                }
                waiting -> {
                    Text(
                        "La espera sigue aunque salgas; te aviso al terminar." + if (usesNow >= 1) " La próxima será de ${nextWait(usesNow)} min." else "",
                        color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                    )
                    TextAction("Cancelar espera", { Repo.cancelWait() })
                }
                else -> {
                    val mins = nextWait(usesNow)
                    TextAction("Necesito el móvil", { Repo.requestWait(pkg) })
                    Text(
                        "Esperas $mins min y la usas 5 min. No rompe la racha.",
                        color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
