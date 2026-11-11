package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.SerifText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.todayKey
import com.alberto.upgrade.data.xpOn
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.UIcon
import java.time.LocalDate

private val prideLabels = listOf("Nada orgulloso", "Poco", "Normal", "Bastante", "Muy orgulloso")

@Composable
fun ReviewScreen(data: AppData, actions: AppActions, onClose: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    var step by remember { mutableIntStateOf(0) }
    var rating by remember { mutableIntStateOf(data.reviews[todayKey()]?.rating ?: 0) }
    var first by remember { mutableStateOf("") }

    val doneToday = data.tasks.filter { it.done && it.xpDate == todayKey() }
    val xp = xpOn(data, today)
    val focus = data.focusLog[todayKey()] ?: 0
    val gym = data.habitLog["gym"]?.get(todayKey())

    val pendingToday = data.tasks.filter { !it.done && it.due == todayKey() }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.x, onClose)
            ProgressBar((step + 1) / 3f, ui.primary, Modifier.weight(1f).padding(start = 8.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 24.dp)) {
            when (step) {
                0 -> {
                    SerifText("Así ha ido hoy", fontSize = 30.sp)
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("${doneToday.size}", "hechas", ui.ink, Modifier.weight(1f))
                        StatBox("$xp", "XP", ui.accentInk, Modifier.weight(1f))
                        StatBox("$focus", "min de foco", ui.ink, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    doneToday.forEach { DoneLine(it.title, true) }
                    if (gym != null) DoneLine("Gym · $gym", true)
                    pendingToday.forEach { DoneLine("${it.title} · pendiente", false) }
                    if (doneToday.isEmpty() && gym == null && pendingToday.isEmpty()) {
                        Text("Hoy no has marcado nada. Mañana es otra oportunidad.", color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                1 -> {
                    Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        SerifText("¿Orgulloso de hoy?", fontSize = 30.sp, textAlign = TextAlign.Center)
                        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(5) { k ->
                                val on = k < rating
                                Box(Modifier.clip(CircleShape).clickable { rating = k + 1 }.padding(2.dp)) {
                                    UIcon(if (on) Ic.starFill else Ic.star, if (on) ui.primary else ui.strong, size = 42.dp)
                                }
                            }
                        }
                        Text(
                            if (rating > 0) prideLabels[rating - 1] else "Toca una estrella",
                            color = if (rating > 0) ui.ink else ui.muted, fontWeight = FontWeight.Black, fontSize = 16.sp,
                            modifier = Modifier.padding(top = 18.dp)
                        )
                    }
                }
                else -> {
                    SerifText("¿Qué es lo primero que harás mañana?", fontSize = 30.sp)
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier.padding(top = 22.dp).fillMaxWidth().clip(shape).background(ui.card)
                            .border(2.dp, if (first.isNotEmpty()) ui.accentInk else ui.hairline, shape).padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        if (first.isEmpty()) Text("Ej: repasar química antes de clase", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        com.alberto.upgrade.ui.UpTextField(
                            value = first,
                            onValueChange = { first = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Fonts.Sans, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ui.ink),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    com.alberto.upgrade.ui.GhostButton(
                        "Dictar el plan de mañana",
                        {
                            val a = Repo.saveReview(rating.coerceAtLeast(1), first)
                            onClose()
                            actions.award(a, "¡Día cerrado!")
                            actions.openOverlay("dump")
                        },
                        Modifier.fillMaxWidth(), height = 48.dp, icon = Ic.mic, fontSize = 14.sp
                    )
                }
            }
        }

        com.alberto.upgrade.ui.ListDivider()
        ChunkyButton(
            if (step < 2) "Continuar" else "Terminar",
            {
                if (step < 2) step++
                else {
                    val a = Repo.saveReview(rating.coerceAtLeast(1), first)
                    onClose()
                    actions.award(a, "¡Día cerrado!")
                }
            },
            Modifier.fillMaxWidth().padding(16.dp),
            enabled = step != 1 || rating > 0
        )
    }
}

@Composable
private fun StatBox(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    com.alberto.upgrade.ui.ChunkyCard(modifier, padding = 14.dp) {
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(label, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun DoneLine(text: String, done: Boolean) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (done) ui.primary else androidx.compose.ui.graphics.Color.Transparent)
                .then(if (done) Modifier else Modifier.border(2.5.dp, ui.strong, CircleShape)),
            contentAlignment = Alignment.Center
        ) { if (done) UIcon(Ic.check, ui.onPrimary, size = 15.dp) }
        Text(text, color = if (done) ui.ink else ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
    }
}
