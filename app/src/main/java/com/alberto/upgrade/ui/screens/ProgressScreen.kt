package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.cap
import com.alberto.upgrade.data.dayName
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.streakOf
import com.alberto.upgrade.data.xpOn
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.SerifText
import com.alberto.upgrade.ui.UIcon
import java.time.LocalDate
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.remember
import com.alberto.upgrade.ui.reveal
import kotlinx.coroutines.launch

private val letters = listOf("L", "M", "X", "J", "V", "S", "D")

@Composable
fun ProgressScreen(data: AppData, onClose: () -> Unit) {
    val ui = LocalUi.current
    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekFocus = week.sumOf { data.focusLog[it.key()] ?: 0 }
    val weekDone = data.tasks.count { t -> t.done && t.xpDate != null && week.any { it.key() == t.xpDate } }
    val streak = streakOf(data, today)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Progreso", onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 32.dp)) {
            // Las cuatro cifras en dos filas de altura igual: si una etiqueta ocupa dos líneas, su pareja crece con ella.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).reveal(0), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Ic.flameFill, ui.flame, "$streak", "Racha", Modifier.weight(1f).fillMaxHeight())
                StatTile(Ic.crown, ui.muted, "${maxOf(data.bestStreak, streak)}", "Mejor racha", Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).reveal(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Ic.circleCheck, ui.muted, "$weekDone", "Hechas en 7 días", Modifier.weight(1f).fillMaxHeight())
                StatTile(Ic.target, ui.muted, "$weekFocus", "Min de foco en 7 días", Modifier.weight(1f).fillMaxHeight())
            }

            Spacer(Modifier.height(16.dp))
            XpChart(data, today, Modifier.reveal(2))

            val rated = week.reversed().mapNotNull { d -> data.reviews[d.key()]?.let { d to it.rating } }
            Spacer(Modifier.height(16.dp))
            ChunkyCard(Modifier.fillMaxWidth()) {
                SerifText("¿Cómo de orgulloso acabaste?", fontSize = 22.sp)
                if (rated.isEmpty()) {
                    Text(
                        "Cierra el día por la noche y aquí verás cómo te sentiste cada día.",
                        color = ui.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)
                    )
                }
                rated.forEach { (d, rating) ->
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (d == today) "Hoy" else dayName(d).cap(),
                            color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.width(84.dp)
                        )
                        repeat(5) { k ->
                            UIcon(if (k < rating) Ic.starFill else Ic.star, if (k < rating) ui.accentInk else ui.strong, size = 18.dp, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: Int, tint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    ChunkyCard(modifier, padding = 0.dp, fillHeight = true) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            UIcon(icon, tint, size = 30.dp)
            Column(Modifier.padding(start = 12.dp)) {
                val n = value.toIntOrNull()
                if (n != null) com.alberto.upgrade.ui.RollingNumber(n) { v -> Text("$v", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 26.sp) }
                else Text(value, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 26.sp)
                Text(label, color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2)
            }
        }
    }
}

/**
 * XP por semanas: se desliza en horizontal hacia semanas anteriores. Las barras crecen desde abajo
 * cada vez que entra una semana; lima si se cumplió la meta, gris si no; hoy, con borde discontinuo.
 */
@Composable
private fun XpChart(data: AppData, today: LocalDate, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val pages = 26
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = pages - 1) { pages }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val back = pages - 1 - pager.currentPage
    val end = today.minusDays(back * 7L)
    val start = end.minusDays(6)
    ChunkyCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("XP por día", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp)
                androidx.compose.animation.AnimatedContent(back, label = "wk") { b ->
                    val e = today.minusDays(b * 7L); val st = e.minusDays(6)
                    Text(
                        if (b == 0) "Últimos 7 días" else "${st.dayOfMonth} ${com.alberto.upgrade.data.monthShort(st)} – ${e.dayOfMonth} ${com.alberto.upgrade.data.monthShort(e)}",
                        color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                }
            }
            val total = (0..6).sumOf { xpOn(data, start.plusDays(it.toLong())) }
            com.alberto.upgrade.ui.RollingNumber(total) { v -> Text("$v", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 17.sp) }
            Text(" XP", color = ui.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        androidx.compose.foundation.pager.HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { page ->
            val b = pages - 1 - page
            val e = today.minusDays(b * 7L)
            val days = (6 downTo 0).map { e.minusDays(it.toLong()) }
            WeekBars(data, days, today)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            com.alberto.upgrade.ui.TextAction("‹ Anterior", { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, fontSize = 12.sp)
            Text("Desliza para ver otras semanas", color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            if (back > 0) com.alberto.upgrade.ui.TextAction("Hoy ›", { scope.launch { pager.animateScrollToPage(pages - 1) } }, color = ui.accentInk, fontSize = 12.sp)
            else Spacer(Modifier.width(64.dp))
        }
    }
}

@Composable
private fun WeekBars(data: AppData, days: List<LocalDate>, today: LocalDate) {
    val ui = LocalUi.current
    val values = days.map { xpOn(data, it) }
    val goals = days.map { com.alberto.upgrade.data.goalFor(data, it) }
    val max = ((values.maxOrNull() ?: 0).coerceAtLeast(goals.maxOrNull() ?: 1) * 1.1f).coerceAtLeast(1f)
    val lime = ui.primary
    val limeLip = ui.primaryLip
    val off = ui.strong
    val goalC = ui.muted.copy(alpha = 0.6f)
    val todayEdge = ui.accentInk
    val reduce = com.alberto.upgrade.ui.LocalReduceMotion.current
    val grow = remember { androidx.compose.animation.core.Animatable(if (reduce) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.tween(520, easing = com.alberto.upgrade.ui.EaseOutQuartCurve)) }
    Column {
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(top = 10.dp)) {
            val n = values.size
            val gap = 10.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            val r = 8.dp.toPx()
            values.forEachIndexed { i, v ->
                val h = ((size.height * (v / max)) * grow.value).coerceAtLeast(10.dp.toPx())
                val x = i * (bw + gap)
                val top = size.height - h
                val met = v >= goals[i]
                val isToday = days[i] == today
                if (met) {
                    drawRoundRect(lime, Offset(x, top), Size(bw, h), CornerRadius(r))
                    clipRect(x, size.height - 4.dp.toPx(), x + bw, size.height) {
                        drawRoundRect(limeLip, Offset(x, top), Size(bw, h), CornerRadius(r))
                    }
                } else {
                    drawRoundRect(off, Offset(x, top), Size(bw, h), CornerRadius(r))
                    if (isToday) {
                        val w = 2.dp.toPx()
                        drawRoundRect(
                            todayEdge, Offset(x + w / 2, top + w / 2), Size(bw - w, h - w), CornerRadius(r),
                            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                        )
                    }
                }
            }
            val gy = size.height - size.height * (goals.last() / max)
            drawLine(goalC, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEach { d ->
                val isToday = d == today
                Text(
                    if (isToday) "Hoy" else letters[d.dayOfWeek.value - 1],
                    color = if (isToday) ui.ink else ui.muted, fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.Black else FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
