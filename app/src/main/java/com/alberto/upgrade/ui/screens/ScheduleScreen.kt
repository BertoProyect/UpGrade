package com.alberto.upgrade.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.classesOn
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.nowMin
import com.alberto.upgrade.ui.AppActions
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Eyebrow
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ScreenHeader
import java.time.LocalDate

/** Horario semanal completo. Todo en gris; solo la clase de ahora en lima. */
@Composable
fun ScheduleScreen(data: AppData, actions: AppActions, onClose: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val dow = today.dayOfWeek.value
    val minute = nowMin()
    val days = listOf("L", "M", "X", "J", "V")
    val classes = classesOn(data, today)
    val current = classes.firstOrNull { minute in it.period.start until it.period.end }
    val next = classes.firstOrNull { it.period.start >= (current?.period?.end ?: (minute + 1)) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Horario de clases", onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.width(40.dp))
                days.forEachIndexed { i, n ->
                    Text(
                        n, color = if (i + 1 == dow) ui.ink else ui.muted,
                        fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                    )
                }
            }
            data.periods.forEachIndexed { p, period ->
                if (p > 0 && data.periods[p - 1].end < period.start) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(minStr(data.periods[p - 1].end), color = ui.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                        Box(
                            Modifier.weight(1f).height(28.dp).drawBehind {
                                val w = 2.dp.toPx()
                                drawRoundRect(
                                    ui.hairline, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
                                )
                            },
                            contentAlignment = Alignment.Center
                        ) { Eyebrow("Recreo", fontSize = 11.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(minStr(period.start), color = ui.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp).padding(top = 2.dp))
                    (1..5).forEach { day ->
                        val s = data.timetable.firstOrNull { it.day == day && it.period == p }?.let { data.subject(it.subjectId) }
                        val now = day == dow && minute in period.start until period.end
                        val shape = RoundedCornerShape(12.dp)
                        Box(Modifier.weight(1f).height(51.dp)) {
                            Box(Modifier.fillMaxWidth().height(48.dp).offset(y = 3.dp).background(if (now) ui.primaryLip else ui.hairline, shape))
                            Box(
                                Modifier.fillMaxWidth().height(48.dp).clip(shape)
                                    .background(if (now) ui.primary else ui.card)
                                    .then(if (now) Modifier else Modifier.border(2.dp, ui.hairline, shape)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    s?.name?.take(3) ?: "—",
                                    color = if (now) ui.onPrimary else if (s != null) ui.ink else ui.muted,
                                    fontSize = 13.sp, fontWeight = FontWeight.Black,
                                    maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            ChunkyCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (current != null) ui.primary else ui.strong))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            current?.let { "Ahora: ${it.subject.name}" } ?: next?.let { "Siguiente: ${it.subject.name}" } ?: "Hoy no hay más clases",
                            color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp
                        )
                        val sub = when {
                            current != null -> "Hasta las ${minStr(current.period.end)}" + (next?.let { " · luego ${it.subject.name}" } ?: "")
                            next != null -> "A las ${minStr(next.period.start)}"
                            else -> "Toca el lápiz para cambiar el horario"
                        }
                        Text(sub, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    IconTap(Ic.pencil, { actions.openOverlay("settings") })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
