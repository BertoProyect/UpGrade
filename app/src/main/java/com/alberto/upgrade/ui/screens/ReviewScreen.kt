package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
    val gym = data.gymLog[todayKey()]

    Column(Modifier.fillMaxSize().padding(16.dp).imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.x, onClose)
            ProgressBar((step + 1) / 3f, ui.success, Modifier.weight(1f).padding(start = 8.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
            when (step) {
                0 -> {
                    Text("Hoy has hecho", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 26.sp)
                    Spacer(Modifier.height(16.dp))
                    Summary(Ic.zapFill, "$xp XP", if (xp >= data.dailyGoal) "Meta cumplida" else "Meta: ${data.dailyGoal} XP")
                    Summary(Ic.circleCheck, "${doneToday.size} tareas", doneToday.joinToString(", ") { it.title }.ifEmpty { "Ninguna marcada" })
                    Summary(Ic.timer, "$focus min de foco", if (focus == 0) "Mañana, al menos un bloque de 25" else "Bloques sin móvil")
                    Summary(Ic.activity, if (gym != null) "Gym: $gym" else "Sin gym", if (gym != null) "Hecho" else "Cuenta para tu 4/semana")
                }
                1 -> {
                    Text("¿Orgulloso de hoy?", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 26.sp)
                    Text("Sé honesto. Nadie más lo ve.", color = ui.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    prideLabels.forEachIndexed { i, label ->
                        val sel = rating == i + 1
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (sel) ui.primary.copy(alpha = if (ui.dark) 0.14f else 0.2f) else ui.card)
                                .border(2.dp, if (sel) ui.accentInk else ui.hairline, RoundedCornerShape(14.dp))
                                .clickable { rating = i + 1 }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                repeat(5) { k ->
                                    UIcon(if (k <= i) Ic.starFill else Ic.star, if (k <= i) (if (sel) ui.accentInk else ui.text) else ui.hairline, size = 16.dp)
                                }
                            }
                            Text(label, color = if (sel) ui.accentInk else ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                }
                else -> {
                    Text("¿Qué es lo primero que harás mañana?", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp)
                    Text(
                        "Una sola cosa, concreta. Se apunta para mañana y te evitas decidir con el móvil en la mano.",
                        color = ui.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it },
                        placeholder = { Text("Ej: 25 min de Historia antes del insti") },
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ui.ink),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ui.primary, unfocusedBorderColor = ui.hairline,
                            focusedContainerColor = ui.card, unfocusedContainerColor = ui.card
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        ChunkyButton(
            if (step < 2) "Continuar" else "Cerrar el día",
            {
                if (step < 2) step++
                else {
                    val a = Repo.saveReview(rating.coerceAtLeast(1), first)
                    onClose()
                    actions.award(a, "¡Día cerrado!")
                }
            },
            Modifier.fillMaxWidth(),
            enabled = step != 1 || rating > 0
        )
    }
}

@Composable
private fun Summary(icon: Int, title: String, sub: String) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        UIcon(icon, ui.accentInk, size = 28.dp)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(sub, color = ui.muted, fontSize = 14.sp, maxLines = 2)
        }
    }
}
