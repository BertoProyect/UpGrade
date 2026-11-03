package com.alberto.upgrade.ui

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.TaskItem
import com.alberto.upgrade.data.dueLabel
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.parseDate
import java.time.LocalDate

@Composable
fun reduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/** Botón "3D": cara + labio inferior que se hunde al pulsar. */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    face: Color = LocalUi.current.primary,
    lip: Color = LocalUi.current.primaryLip,
    content: Color = LocalUi.current.onPrimary,
    border: Color? = null,
    height: Dp = 52.dp,
    enabled: Boolean = true,
    fontSize: TextUnit = 15.sp,
    @DrawableRes icon: Int? = null
) {
    val ui = LocalUi.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val lipH = 4.dp
    val off by animateDpAsState(if (pressed && enabled) lipH else 0.dp, tween(70), label = "press")
    val f = if (enabled) face else ui.hairline
    val l = if (enabled) lip else ui.lip
    val c = if (enabled) content else ui.muted
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(height + lipH)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().height(height).align(Alignment.BottomCenter)
                .background(border ?: l, shape)
        )
        Row(
            Modifier.fillMaxWidth().height(height).offset(y = off)
                .background(f, shape)
                .then(if (border != null) Modifier.border(2.dp, border, shape) else Modifier)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                UIcon(icon, c, size = if (text.isEmpty()) 28.dp else 20.dp)
            }
            if (text.isNotEmpty()) {
                Text(
                    text.uppercase(),
                    color = c,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = if (icon != null) Modifier.padding(start = 8.dp) else Modifier
                )
            }
        }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp, @DrawableRes icon: Int? = null) {
    val ui = LocalUi.current
    ChunkyButton(text, onClick, modifier, face = ui.card, lip = ui.hairline, content = ui.text, border = ui.hairline, height = height, icon = icon)
}

/** Tarjeta con borde y "labio" inferior. */
@Composable
fun ChunkyCard(
    modifier: Modifier = Modifier,
    bg: Color = LocalUi.current.card,
    edge: Color = LocalUi.current.hairline,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val outer = RoundedCornerShape(18.dp)
    val inner = RoundedCornerShape(16.dp)
    Box(
        modifier
            .background(edge, outer)
            .padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 4.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(inner)
                .background(bg)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(padding),
            content = content
        )
    }
}

@Composable
fun ProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 16.dp, track: Color = LocalUi.current.hairline) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        if (p > 0f) {
            Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(height)).background(color)) {
                Box(
                    Modifier.align(Alignment.TopCenter).padding(start = 8.dp, end = 8.dp, top = 3.dp)
                        .fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                )
            }
        }
    }
}

/** Etiqueta neutra. El color se reserva para lo que importa. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val ui = LocalUi.current
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(ui.surface).border(1.dp, ui.hairline, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) UIcon(icon, ui.muted, size = 13.dp, modifier = Modifier.padding(end = 4.dp))
        Text(text, color = ui.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null
) {
    val ui = LocalUi.current
    val bg = if (selected) ui.primary.copy(alpha = if (ui.dark) 0.16f else 0.22f) else ui.card
    val edge = if (selected) ui.accentInk else ui.hairline
    val fg = if (selected) ui.accentInk else ui.text
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(2.dp, edge, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) UIcon(icon, fg, size = 16.dp, modifier = Modifier.padding(end = 6.dp))
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val ui = LocalUi.current
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun CheckCircle(done: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    val scale by animateFloatAsState(if (done) 1f else 0.4f, tween(180), label = "chk")
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(if (done) ui.primary else ui.card)
            .border(2.5.dp, if (done) ui.primary else ui.lip, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (done) UIcon(Ic.check, ui.onPrimary, size = 18.dp, modifier = Modifier.scale(scale))
    }
}

@Composable
fun TaskRow(
    t: TaskItem,
    data: AppData,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    today: LocalDate = LocalDate.now()
) {
    val ui = LocalUi.current
    val due = parseDate(t.due)
    val overdue = !t.done && due != null && due.isBefore(today)
    val subject = data.subject(t.subjectId)
    val area = data.area(t.areaId)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(t.done, onToggle)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                t.title,
                color = if (t.done) ui.muted else ui.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (subject != null) Tag(subject.name, icon = kindIcon(t.kind))
                else if (area != null) Tag(area.name, icon = areaIcon(area.id))
                Text(
                    "${t.kind.label} · ${dueLabel(due, today)}" + (t.time?.let { " · ${minStr(it)}" } ?: ""),
                    color = if (overdue) ui.danger else ui.muted,
                    fontSize = 13.sp,
                    fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!t.done) {
            Text("+${t.kind.xp}", color = ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ui.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        SmallRound("–", onMinus)
        Text(value, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(70.dp))
        SmallRound("+", onPlus)
    }
}

@Composable
fun SmallRound(text: String, onClick: () -> Unit, @DrawableRes icon: Int? = null) {
    val ui = LocalUi.current
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(ui.surface).border(2.dp, ui.hairline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) UIcon(icon, ui.ink, size = 18.dp)
        else Text(text, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
fun IconTap(@DrawableRes icon: Int, onClick: () -> Unit, tint: Color = LocalUi.current.muted, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        UIcon(icon, tint, size = 22.dp)
    }
}

@Composable
fun EmptyNote(text: String) {
    val ui = LocalUi.current
    Text(text, color = ui.muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
}
