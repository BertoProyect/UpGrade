package com.alberto.upgrade.ui

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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

/** Botón "chunky": cara + labio inferior que se hunde al pulsar. Desactivado no tiene labio. */
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
    radius: Dp = 14.dp,
    lipH: Dp = 4.dp,
    @DrawableRes icon: Int? = null,
    iconSize: Dp? = null
) {
    val ui = LocalUi.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // Baja en 90 ms y vuelve con un spring rápido (0.8 / 1500): el "rebote" está en la vuelta.
    val off by animateDpAsState(
        if (pressed && enabled) lipH else 0.dp,
        motion(if (pressed) UpMotion.press() else UpMotion.release()),
        label = "press"
    )
    val f = if (enabled) face else ui.hairline
    val c = if (enabled) content else ui.muted
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .height(height + lipH)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
    ) {
        if (enabled) {
            Box(
                Modifier.fillMaxWidth().height(height).align(Alignment.BottomCenter)
                    .background(border ?: lip, shape)
            )
        }
        Row(
            Modifier.fillMaxWidth().height(height).offset(y = if (enabled) off else lipH)
                .background(f, shape)
                .then(if (border != null && enabled) Modifier.border(2.dp, border, shape) else Modifier)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                UIcon(icon, c, size = iconSize ?: if (text.isEmpty()) 28.dp else 18.dp)
            }
            if (text.isNotEmpty()) {
                Text(
                    text.uppercase(),
                    color = c,
                    fontWeight = FontWeight.Black,
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

/** Botón secundario: superficie de tarjeta con borde y labio del mismo gris. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    @DrawableRes icon: Int? = null,
    fontSize: TextUnit = 15.sp,
    radius: Dp = 14.dp,
    lipH: Dp = 4.dp,
    content: Color = LocalUi.current.ink,
    iconSize: Dp? = null
) {
    val ui = LocalUi.current
    ChunkyButton(
        text, onClick, modifier, face = ui.card, lip = ui.hairline, content = content, border = ui.hairline,
        height = height, icon = icon, fontSize = fontSize, radius = radius, lipH = lipH, iconSize = iconSize
    )
}

/** Enlace de texto en mayúsculas (Restaurar copia, Rendirse, Necesito el móvil…). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalUi.current.muted, fontSize: TextUnit = 14.sp) {
    Text(
        text.uppercase(),
        color = color,
        fontWeight = FontWeight.Black,
        fontSize = fontSize,
        letterSpacing = 0.6.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

/** Tarjeta con borde de 2 dp y labio inferior. */
@Composable
fun ChunkyCard(
    modifier: Modifier = Modifier,
    bg: Color = LocalUi.current.card,
    edge: Color = LocalUi.current.hairline,
    lip: Color = edge,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    tint: Color? = null,
    radius: Dp = 18.dp,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.background(lip, shape).padding(bottom = 4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .clip(shape)
                .background(bg)
                .then(if (tint != null) Modifier.background(tint) else Modifier)
                .border(2.dp, edge, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(padding),
            content = content
        )
    }
}

/** Opción grande seleccionable (onboarding, meta diaria): lima translúcido cuando está marcada. */
@Composable
fun ChoiceCard(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.(Color) -> Unit) {
    val ui = LocalUi.current
    ChunkyCard(
        modifier.fillMaxWidth(),
        bg = if (selected) ui.bg else ui.card,
        tint = if (selected) ui.selTint else null,
        edge = if (selected) ui.accentInk else ui.hairline,
        lip = if (selected) ui.primaryLip.copy(alpha = 0.6f) else ui.hairline,
        onClick = onClick,
        padding = 0.dp,
        radius = 16.dp
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            content(if (selected) ui.accentInk else ui.ink)
        }
    }
}

@Composable
fun ProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 16.dp, track: Color = LocalUi.current.hairline) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), motion(UpMotion.screen()), label = "bar")
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        if (p > 0f) {
            Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(height)).background(color)) {
                if (height >= 10.dp) {
                    Box(
                        Modifier.align(Alignment.TopCenter).padding(start = 8.dp, end = 8.dp, top = 3.dp)
                            .fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

/** Etiqueta neutra. El color se reserva para lo que importa. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, color: Color? = null) {
    val ui = LocalUi.current
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(ui.surface).border(1.dp, ui.hairline, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) UIcon(icon, ui.muted, size = 12.dp, modifier = Modifier.padding(end = 4.dp))
        Text(text, color = color ?: ui.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    badge: String? = null,
    center: Boolean = false
) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(12.dp)
    val spec = motion(tween<Color>(180))
    val fg by androidx.compose.animation.animateColorAsState(if (selected) ui.accentInk else ui.ink, spec, label = "chipfg")
    val bgc by androidx.compose.animation.animateColorAsState(if (selected) ui.selTint else Color.Transparent, spec, label = "chipbg")
    val edge by androidx.compose.animation.animateColorAsState(if (selected) ui.accentInk else ui.hairline, spec, label = "chipedge")
    Row(
        modifier
            .clip(shape)
            .background(bgc)
            .border(2.dp, edge, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) UIcon(icon, fg, size = 16.dp, modifier = Modifier.padding(end = 6.dp))
        Text(text, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
        if (badge != null) {
            Box(
                Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (selected) ui.primary else ui.surface)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(badge, color = if (selected) ui.onPrimary else ui.muted, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        }
    }
}

/** Interruptor de la marca: 52 × 32, lima encendido, gris con borde apagado. */
@Composable
fun UToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val ui = LocalUi.current
    val x by animateDpAsState(if (checked) 24.dp else 7.dp, motion(UpMotion.state()), label = "tg")
    val track by androidx.compose.animation.animateColorAsState(if (checked) ui.primary else ui.surface, motion(tween(180)), label = "tgbg")
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(52.dp, 32.dp).clip(shape)
            .background(track)
            .then(if (checked) Modifier else Modifier.border(2.dp, ui.strong, shape))
            .clickable { onChange(!checked) }
    ) {
        Box(
            Modifier.offset(x = x, y = if (checked) 4.dp else 7.dp)
                .size(if (checked) 24.dp else 18.dp).clip(CircleShape)
                .background(if (checked) ui.onPrimary else ui.muted)
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val ui = LocalUi.current
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Rótulo pequeño en mayúsculas (fechas, grupos, ajustes). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = LocalUi.current.muted, fontSize: TextUnit = 13.sp) {
    Text(text.uppercase(), color = color, fontWeight = FontWeight.Black, fontSize = fontSize, letterSpacing = 0.8.sp, modifier = modifier)
}

/** Frases que hacen pensar, en Newsreader. */
@Composable
fun SerifText(text: String, modifier: Modifier = Modifier, color: Color = LocalUi.current.ink, fontSize: TextUnit = 26.sp, textAlign: TextAlign? = null) {
    Text(
        text, color = color, fontFamily = Fonts.Serif, fontWeight = FontWeight.Normal, fontSize = fontSize,
        lineHeight = fontSize * 1.18f, letterSpacing = (-0.3).sp, textAlign = textAlign, modifier = modifier
    )
}

/** Cabecera de pantalla superpuesta: flecha o cruz + título. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, close: Boolean = false, trailing: @Composable RowScope.() -> Unit = {}) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTap(if (close) Ic.x else Ic.arrowLeft, onBack, tint = ui.ink)
        Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(start = 4.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** Lista agrupada en tarjeta (menús de Perfil y Ajustes). */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ChunkyCard(modifier.fillMaxWidth(), padding = 0.dp, content = content)
}

@Composable
fun ListDivider() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(LocalUi.current.hairline))
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val ui = LocalUi.current
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) UIcon(icon, ui.muted, size = 20.dp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = ui.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
        }
        if (value != null) Text(value, color = valueColor ?: ui.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        if (trailing != null) trailing()
        else if (chevron && onClick != null) UIcon(Ic.chevronRight, ui.muted, size = 18.dp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun CheckCircle(done: Boolean, onClick: () -> Unit, size: Dp = 28.dp) {
    val ui = LocalUi.current
    val scale by animateFloatAsState(if (done) 1f else 0.4f, motion(tween(180)), label = "chk")
    val fill by androidx.compose.animation.animateColorAsState(if (done) ui.primary else Color.Transparent, motion(tween(180)), label = "chkfill")
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(fill)
            .then(if (done) Modifier else Modifier.border(2.5.dp, ui.strong, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (done) UIcon(Ic.check, ui.onPrimary, size = size * 0.64f, modifier = Modifier.scale(scale))
    }
}

/**
 * Fila de tarea. [showDate] = false cuando el grupo ya dice el día (Para hoy, Hoy · Martes 3);
 * [showKind] = false cuando la sección ya dice el tipo (Áreas). Lo atrasado siempre dice la fecha, en rojo.
 */
@Composable
fun TaskRow(
    t: TaskItem,
    data: AppData,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    today: LocalDate = LocalDate.now(),
    showDate: Boolean = true,
    showKind: Boolean = true,
    showXp: Boolean = true
) {
    val ui = LocalUi.current
    val due = parseDate(t.due)
    val overdue = !t.done && due != null && due.isBefore(today)
    val subject = data.subject(t.subjectId)
    val area = data.area(t.areaId)
    val xpNow = if (t.done) 0 else com.alberto.upgrade.data.xpIfDone(data, t)
    // Tope alcanzado: la tarea sigue contando como hecha, pero sin XP.
    val capped = !t.done && showXp && com.alberto.upgrade.data.countsForCap(t.kind) && xpNow == 0 && data.taskCap > 0
    val parts = buildList {
        if (showKind || overdue) add(t.kind.label)
        if (showDate || overdue) add(dueLabel(due, today))
        t.time?.let { add(minStr(it)) }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(t.done, onToggle)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                t.title,
                color = if (t.done) ui.muted else ui.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    subject != null -> Tag(subject.name, icon = if (t.done) null else kindIcon(t.kind), color = if (t.done) ui.muted else null)
                    area != null -> Tag(area.name, icon = if (t.done) null else areaIcon(area.id), color = if (t.done) ui.muted else null)
                }
                if (capped) {
                    UIcon(Ic.zap, ui.muted, size = 13.dp)
                    Text("Tope de tareas alcanzado", color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (!t.done && parts.isNotEmpty()) {
                    Text(
                        parts.joinToString(" · "),
                        color = if (overdue) ui.danger else ui.muted,
                        fontSize = 13.sp,
                        fontWeight = if (overdue) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (!t.done && t.kind == com.alberto.upgrade.data.Kind.REPASO) {
            val ctx = LocalContext.current
            Row(
                Modifier.padding(start = 8.dp).clip(RoundedCornerShape(12.dp)).background(ui.primary)
                    .clickable {
                        com.alberto.upgrade.data.Repo.startRepaso(ctx, t)
                        com.alberto.upgrade.MainActivity.pendingOpen.value = "focus"
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UIcon(Ic.play, ui.onPrimary, size = 14.dp)
                Text("${t.minutes ?: 60}′", color = ui.onPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
            }
        } else if (!t.done && showXp && !capped && xpNow > 0) {
            Text("+$xpNow", color = ui.muted, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        SmallRound("", onMinus, icon = Ic.minus)
        Text(value, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
        SmallRound("", onPlus, icon = Ic.plus)
    }
}

@Composable
fun SmallRound(text: String, onClick: () -> Unit, @DrawableRes icon: Int? = null) {
    val ui = LocalUi.current
    Box(
        Modifier.size(34.dp).clip(CircleShape).border(2.dp, ui.hairline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) UIcon(icon, ui.ink, size = 16.dp)
        else Text(text, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

/** Botón cuadrado con borde (flechas del calendario). */
@Composable
fun SquareTap(@DrawableRes icon: Int, onClick: () -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(40.dp).clip(shape).border(2.dp, ui.hairline, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { UIcon(icon, ui.ink, size = 20.dp) }
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
    Text(text, color = ui.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
}

/** Logo UpGrade «Hecho y arriba»: check que acaba en flecha, con labio. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier, color: Color = LocalUi.current.primary, lip: Color? = LocalUi.current.primaryLip) {
    val small = size < 32.dp
    Canvas(modifier.size(size)) {
        val k = this.size.width / 120f
        val check = Path().apply { moveTo(26 * k, 64 * k); lineTo(48 * k, 86 * k); lineTo(92 * k, 36 * k) }
        val arrow = Path().apply { moveTo(70 * k, 34 * k); lineTo(93 * k, 34 * k); lineTo(93 * k, 57 * k) }
        val stroke = Stroke(width = (if (small) 18 else 16) * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (lip != null && !small) {
            translate(0f, 6 * k) {
                drawPath(check, lip, style = stroke)
                drawPath(arrow, lip, style = stroke)
            }
        }
        drawPath(check, color, style = stroke)
        drawPath(arrow, color, style = stroke)
    }
}

/** Control segmentado: el indicador se desliza con spring (0.8 / 800) y el color cambia en 180 ms. */
@Composable
fun <K> Segmented(options: List<Triple<K, String, Int?>>, selected: K, modifier: Modifier = Modifier, onSelect: (K) -> Unit) {
    val ui = LocalUi.current
    val outer = RoundedCornerShape(14.dp)
    val inner = RoundedCornerShape(11.dp)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().height(48.dp).clip(outer).background(ui.surface).border(2.dp, ui.hairline, outer).padding(3.dp)
    ) {
        val n = options.size.coerceAtLeast(1)
        val w = maxWidth / n
        val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        val x by animateDpAsState(w * idx, motion(UpMotion.state()), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(inner).background(ui.selTint).border(2.dp, ui.accentInk, inner))
        Row(Modifier.fillMaxSize()) {
            options.forEach { (key, label, icon) ->
                val sel = key == selected
                val c by androidx.compose.animation.animateColorAsState(if (sel) ui.accentInk else ui.muted, motion(tween(180)), label = "segc")
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(inner).clickable { onSelect(key) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) UIcon(icon, c, size = 17.dp, modifier = Modifier.padding(end = 6.dp))
                    Text(label, color = c, fontWeight = if (sel) FontWeight.Black else FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}
