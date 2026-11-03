package com.alberto.upgrade.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.notify.AlarmScheduler
import com.alberto.upgrade.ui.ChoiceCard
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.Fonts
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.ListDivider
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.LogoMark
import com.alberto.upgrade.ui.ProgressBar
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.TextAction
import com.alberto.upgrade.ui.UIcon
import com.alberto.upgrade.ui.areaIcon

private data class AreaPick(val id: String, val name: String, val sub: String)

private val picks = listOf(
    AreaPick("insti", "Bachillerato", "Exámenes y deberes"),
    AreaPick("orbita", "Órbita Webs", "Llamadas y tareas"),
    AreaPick("berto", "BertoProject", "Tu marca personal"),
    AreaPick("vaelor", "Vaelor", "Comunidad de emprendedores"),
    AreaPick("gym", "Gym", "Días por semana")
)

private val goals = listOf(
    Triple(30, "Suave", "Unas 3 tareas"),
    Triple(50, "Normal", "Tareas + 25 min de foco"),
    Triple(80, "En serio", "Semana de exámenes"),
    Triple(120, "A tope", "Días sin clase")
)

/** Bienvenida en 3 pasos: hola → áreas → meta diaria. */
@Composable
fun OnboardingScreen(data: AppData, onDone: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var areas by remember { mutableStateOf(picks.map { it.id }.filter { it !in data.hiddenAreas }.toSet()) }
    var goal by remember { mutableIntStateOf(data.dailyGoal) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                Repo.importJson(text)
            }.getOrDefault(false)
            Toast.makeText(ctx, if (ok) "Copia restaurada" else "Ese archivo no es una copia de UpGrade", Toast.LENGTH_LONG).show()
            if (ok) {
                AlarmScheduler.scheduleAll(ctx)
                Repo.update { it.copy(onboarded = true) }
                onDone()
            }
        }
    }

    fun finish() {
        Repo.update { d ->
            d.copy(onboarded = true, dailyGoal = goal, hiddenAreas = picks.map { it.id }.filter { it !in areas })
        }
        onDone()
    }

    BackHandler(enabled = step > 0) { step-- }

    if (step == 0) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Bubble(tailDown = true) {
                    Text(
                        buildAnnotatedString {
                            append("Hola, Alberto. Vamos a que acabes el día ")
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("orgulloso") }
                            append(".")
                        },
                        color = ui.ink, fontFamily = Fonts.Serif, fontSize = 24.sp, lineHeight = 31.sp, textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(36.dp))
                LogoTile(112, 30, 7)
                Spacer(Modifier.height(20.dp))
                Text("UpGrade", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = (-0.5).sp)
            }
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ChunkyButton("Empezar", { step = 1 }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                TextAction("Restaurar copia", { importLauncher.launch(arrayOf("application/json", "*/*")) })
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.arrowLeft, { step-- })
            ProgressBar(if (step == 1) 0.34f else 0.67f, ui.primary, Modifier.weight(1f).padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 26.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoTile(64, 18, 4)
            Spacer(Modifier.width(18.dp))
            Bubble(Modifier.weight(1f), tailDown = false) {
                SerifText(
                    if (step == 1) "¿En qué andas metido? Marca todo lo que lleves." else "¿Cuánto quieres sumar cada día?",
                    fontSize = 21.sp
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step == 1) {
                picks.forEach { p ->
                    val sel = p.id in areas
                    ChoiceCard(sel, { areas = if (sel) areas - p.id else areas + p.id }) { fg ->
                        UIcon(areaIcon(p.id), if (sel) fg else ui.muted, size = 28.dp)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(p.name, color = fg, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text(p.sub, color = if (sel) fg.copy(alpha = 0.85f) else ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        if (sel) UIcon(Ic.circleCheck, fg, size = 24.dp)
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                goals.forEach { (xp, name, sub) ->
                    val sel = goal == xp
                    Box {
                        ChoiceCard(sel, { goal = xp }) { fg ->
                            Row(Modifier.width(64.dp), verticalAlignment = Alignment.Bottom) {
                                Text("$xp", color = fg, fontWeight = FontWeight.Black, fontSize = 24.sp)
                                Text(" XP", color = if (sel) fg else ui.muted, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(name, color = fg, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Text(sub, color = if (sel) fg.copy(alpha = 0.85f) else ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                        if (xp == 50) {
                            Text(
                                "RECOMENDADO",
                                color = ui.onPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp,
                                modifier = Modifier.align(Alignment.TopEnd).offset(y = (-12).dp)
                                    .background(ui.primary, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    "Lo puedes cambiar cuando quieras en Ajustes.",
                    color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        ListDivider()
        ChunkyButton(
            "Continuar",
            { if (step == 1) step = 2 else finish() },
            Modifier.fillMaxWidth().padding(16.dp),
            enabled = step != 1 || areas.isNotEmpty()
        )
    }
}

/** Mascota: el logo en una baldosa lima con labio. */
@Composable
private fun LogoTile(sizeDp: Int, radiusDp: Int, lipDp: Int) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(radiusDp.dp)
    Box(Modifier.size(sizeDp.dp, (sizeDp + lipDp).dp)) {
        Box(Modifier.size(sizeDp.dp).offset(y = lipDp.dp).background(ui.primaryLip, shape))
        Box(Modifier.size(sizeDp.dp).background(ui.primary, shape), contentAlignment = Alignment.Center) {
            LogoMark((sizeDp * 0.56f).dp, color = ui.onPrimary, lip = null)
        }
    }
}

/** Bocadillo con borde y pico (abajo o a la izquierda). */
@Composable
private fun Bubble(modifier: Modifier = Modifier, tailDown: Boolean, content: @Composable () -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(if (tailDown) 20.dp else 18.dp)
    Box(modifier) {
        Box(
            Modifier.widthIn(max = 320.dp).background(ui.card, shape).border(2.dp, ui.hairline, shape)
                .padding(horizontal = if (tailDown) 22.dp else 16.dp, vertical = if (tailDown) 18.dp else 14.dp)
        ) { content() }
        // pico: un cuadrado girado con el borde solo en dos lados se simula con dos capas
        val tail = Modifier.size(16.dp).rotate(45f)
        if (tailDown) {
            Box(Modifier.align(Alignment.BottomCenter).offset(y = 9.dp).then(tail).background(ui.hairline))
            Box(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp).then(tail).background(ui.card))
        } else {
            Box(Modifier.align(Alignment.CenterStart).offset(x = (-9).dp).then(tail).background(ui.hairline))
            Box(Modifier.align(Alignment.CenterStart).offset(x = (-6).dp).then(tail).background(ui.card))
        }
    }
}
