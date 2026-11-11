package com.alberto.upgrade.ui

import androidx.compose.animation.togetherWith

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alberto.upgrade.MainActivity
import com.alberto.upgrade.data.Award
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.TaskItem
import com.alberto.upgrade.ui.screens.AddSheet
import com.alberto.upgrade.ui.screens.AreasScreen
import com.alberto.upgrade.ui.screens.BrainDumpScreen
import com.alberto.upgrade.ui.screens.ScheduleScreen
import com.alberto.upgrade.data.Kind
import com.alberto.upgrade.ui.screens.BlockedAppsScreen
import com.alberto.upgrade.ui.screens.CalendarScreen
import com.alberto.upgrade.ui.screens.Celebration
import com.alberto.upgrade.ui.screens.CelebrationOverlay
import com.alberto.upgrade.ui.screens.FocusScreen
import com.alberto.upgrade.ui.screens.OnboardingScreen
import com.alberto.upgrade.ui.screens.ProgressScreen
import com.alberto.upgrade.ui.screens.PermissionsScreen
import com.alberto.upgrade.ui.screens.ProfileScreen
import com.alberto.upgrade.ui.screens.ReviewScreen
import com.alberto.upgrade.ui.screens.SettingsScreen
import com.alberto.upgrade.ui.screens.TodayScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import java.time.LocalDate

private data class TabDef(val icon: Int, val label: String)

private val tabs = listOf(
    TabDef(Ic.house, "Hoy"),
    TabDef(Ic.calendarDays, "Calendario"),
    TabDef(Ic.layers, "Áreas"),
    TabDef(Ic.target, "Foco"),
    TabDef(Ic.trophy, "Perfil")
)

const val TAB_TODAY = 0
const val TAB_CALENDAR = 1
const val TAB_AREAS = 2
/** Hábitos vive dentro de Foco: ir aquí abre Foco en su panel de Hábitos. */
const val TAB_HABITS = 100
const val TAB_FOCUS = 3
const val TAB_PROFILE = 4

/** Panel que debe abrir Foco la próxima vez que se pinte ("timer" o "routines"). */
val PendingFocusPane = mutableStateOf<String?>(null)

/** Estado compartido de la UI, para que las pantallas premien y naveguen. */
class AppActions(
    val award: (Award?, String) -> Unit,
    val openAdd: (LocalDate?, String?, Kind?) -> Unit,
    val openTask: (TaskItem) -> Unit,
    val openOverlay: (String) -> Unit,
    val goTab: (Int) -> Unit
)

@Composable
fun AppRoot() {
    val ui = LocalUi.current
    val data by Repo.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // Las pestañas viven en un carrusel: se cambia deslizando o tocando la barra de abajo.
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0) { tabs.size }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val reduceNav = LocalReduceMotion.current
    val tab = pager.currentPage
    val goTo: (Int) -> Unit = { target ->
        val i = if (target == TAB_HABITS) { PendingFocusPane.value = "habits"; TAB_FOCUS } else target
        scope.launch {
            if (reduceNav) pager.scrollToPage(i)
            else pager.animateScrollToPage(i, animationSpec = androidx.compose.animation.core.spring(0.85f, 380f))
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var addDate by remember { mutableStateOf<LocalDate?>(null) }
    var addArea by remember { mutableStateOf<String?>(null) }
    var addKind by remember { mutableStateOf<Kind?>(null) }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    var queue by remember { mutableStateOf(listOf<Celebration>()) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val actions = remember {
        AppActions(
            award = { a, msg ->
                if (a != null && a.xp > 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    toast = "$msg  +${a.xp} XP"
                    // Las rachas las celebra el vigilante de abajo; aquí solo el nivel.
                    if (a.levelUp != null) {
                        if (celebration == null) celebration = Celebration.Level(a.levelUp) else queue = queue + Celebration.Level(a.levelUp)
                    }
                }
            },
            openAdd = { d, a, k -> addDate = d; addArea = a; addKind = k; editing = null; showAdd = true },
            openTask = { t -> editing = t; showAdd = true },
            openOverlay = { overlay = it },
            goTab = { goTo(it) }
        )
    }

    val open by MainActivity.pendingOpen
    LaunchedEffect(open) {
        when (open) {
            "review" -> overlay = "review"
            "focus" -> { overlay = null; goTo(TAB_FOCUS) }
            "add" -> { editing = null; addDate = null; addArea = null; addKind = null; showAdd = true }
            "dump" -> overlay = "dump"
            "permissions" -> overlay = "permissions"
        }
        if (open != null) MainActivity.pendingOpen.value = null
    }
    LaunchedEffect(Unit) {
        if (!Repo.data.onboarded && !Repo.data.permissionsShown) {
            overlay = "onboarding"
        } else if (!Repo.data.permissionsShown) {
            overlay = "permissions"
            Repo.update { it.copy(permissionsShown = true) }
        }
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
            Repo.claimFocus()?.let { actions.award(it, "¡Foco completado! El reloj sigue") }
            Repo.tickFocusExtra()?.let { actions.award(it, "Tiempo extra") }
            Repo.tickRoutines()?.let { actions.award(it, "¡Rutina cumplida!") }
        }
    }
    // Vigilante de rachas: cada subida (XP, foco y cada rutina) se celebra una vez, también si pasó con la app cerrada.
    LaunchedEffect(data.xpLog, data.focusLog, data.routineLog, data.routines, data.habitLog) {
        val ups = Repo.collectStreakUps()
        if (ups.isNotEmpty()) {
            val c = Celebration.Streaks(ups)
            if (celebration == null) celebration = c else queue = queue + c
        }
    }
    LaunchedEffect(celebration) {
        if (celebration == null && queue.isNotEmpty()) {
            delay(250)
            celebration = queue.first()
            queue = queue.drop(1)
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1900)
            toast = null
        }
    }

    BackHandler(enabled = overlay != null && overlay != "onboarding") { overlay = null }
    BackHandler(enabled = overlay == null && tab != 0) { goTo(TAB_TODAY) }

    val reduce0 = LocalReduceMotion.current
    Box(Modifier.fillMaxSize().background(ui.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            val reduce = LocalReduceMotion.current
            val px24 = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.roundToPx() }
            // Contenido, botones flotantes y aviso en la misma caja: cuando aparece la barra de foco
            // debajo, la caja encoge y todo sube con ella, sin solaparse.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    key = { it }
                ) { t ->
                    Box(Modifier.fillMaxSize()) {
                        when (t) {
                            TAB_TODAY -> TodayScreen(data, actions)
                            TAB_CALENDAR -> CalendarScreen(data, actions)
                            TAB_AREAS -> AreasScreen(data, actions)
                            TAB_FOCUS -> FocusScreen(data, actions)
                            else -> ProfileScreen(data, actions)
                        }
                    }
                }

                // Micro y + en Hoy, Calendario y Áreas; entran y salen creciendo desde la esquina.
                androidx.compose.animation.AnimatedVisibility(
                    visible = pager.targetPage <= TAB_AREAS,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp),
                    enter = if (reduce) fadeIn(androidx.compose.animation.core.tween(120)) else androidx.compose.animation.scaleIn(UpMotion.state(), initialScale = 0.6f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)) + fadeIn(UpMotion.fade()),
                    exit = if (reduce) fadeOut(androidx.compose.animation.core.tween(120)) else androidx.compose.animation.scaleOut(UpMotion.state(), targetScale = 0.6f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)) + fadeOut(UpMotion.fade(120))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GhostButton("", { overlay = "dump" }, Modifier.size(width = 52.dp, height = 56.dp), height = 52.dp, icon = Ic.mic, iconSize = 24.dp)
                        Box(Modifier.height(12.dp))
                        ChunkyButton("", { actions.openAdd(null, null, null) }, Modifier.size(width = 64.dp, height = 68.dp), height = 64.dp, radius = 16.dp, icon = Ic.plus, iconSize = 32.dp)
                    }
                }

                // Cualificado: dentro de Box-en-Column, el AnimatedVisibility suelto cogería el de ColumnScope.
                androidx.compose.animation.AnimatedVisibility(
                    visible = toast != null,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(ui.successTint).border(2.dp, ui.accentInk, RoundedCornerShape(16.dp))
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UIcon(Ic.circleCheck, ui.successInk, size = 24.dp)
                        Text(
                            toast ?: "",
                            color = ui.successInk,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
            // En foco puedes moverte por la app; esta barra te recuerda el tiempo y te lleva de vuelta.
            // Entra y sale plegándose, así los botones de encima suben y bajan con ella.
            AnimatedVisibility(
                visible = com.alberto.upgrade.data.focusOn(data, nowMs) && tab != TAB_FOCUS,
                enter = if (reduce) fadeIn(androidx.compose.animation.core.tween(150)) else androidx.compose.animation.expandVertically(UpMotion.state()) + fadeIn(UpMotion.fade()),
                exit = if (reduce) fadeOut(androidx.compose.animation.core.tween(150)) else androidx.compose.animation.shrinkVertically(UpMotion.state()) + fadeOut(UpMotion.fade(120))
            ) {
                val extraOn = data.focus.endAt <= nowMs
                val left = ((data.focus.endAt - nowMs) / 1000).let { if (extraOn) -it else it }.coerceAtLeast(0)
                Row(
                    Modifier.fillMaxWidth().background(ui.selTint).clickable { goTo(TAB_FOCUS) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIcon(Ic.target, ui.accentInk, size = 16.dp)
                    Text((if (extraOn) "Tiempo extra · +%d:%02d" else "Modo foco · quedan %d:%02d").format(left / 60, left % 60), color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text("VOLVER", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp)
                }
            }
            BottomBar(pager) { goTo(it) }
        }

        AnimatedVisibility(
            overlay != null,
            enter = if (reduce0) fadeIn(androidx.compose.animation.core.tween(150)) else slideInVertically(UpMotion.screen()) { it / 6 } + fadeIn(UpMotion.fade(220)),
            exit = if (reduce0) fadeOut(androidx.compose.animation.core.tween(120)) else slideOutVertically(UpMotion.screen()) { it / 8 } + fadeOut(UpMotion.fade(160))
        ) {
            Box(Modifier.fillMaxSize().background(ui.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (overlay) {
                    "onboarding" -> OnboardingScreen(data) {
                        Repo.update { it.copy(permissionsShown = true) }
                        overlay = "permissions"
                    }
                    "progress" -> ProgressScreen(data) { overlay = null }
                    "review" -> ReviewScreen(data, actions) { overlay = null }
                    "settings" -> SettingsScreen(data, actions) { overlay = null }
                    "permissions" -> PermissionsScreen { overlay = null }
                    "apps" -> BlockedAppsScreen(data) { overlay = null }
                    "dump" -> BrainDumpScreen(data, actions) { overlay = null }
                    "horario" -> ScheduleScreen(data, actions) { overlay = null }
                    "history", "history:focus" -> com.alberto.upgrade.ui.screens.HistoryScreen(data) { overlay = null }
                    "areas" -> AreasScreen(data, actions) { overlay = null }
                    "day" -> com.alberto.upgrade.ui.screens.DayLogScreen(data) { overlay = null }
                    "insights" -> com.alberto.upgrade.ui.screens.InsightsScreen(data, actions) { overlay = null }
                    else -> overlay?.takeIf { it.startsWith("plan:") }?.let { o ->
                        com.alberto.upgrade.ui.screens.StudyPlanScreen(data, o.removePrefix("plan:")) { overlay = null }
                    }
                }
            }
        }

        if (showAdd) {
            AddSheet(
                data = data,
                initialDate = addDate,
                initialArea = addArea,
                initialKind = addKind,
                editing = editing,
                onDismiss = { showAdd = false; editing = null },
                actions = actions
            )
        }

        celebration?.let { c ->
            CelebrationOverlay(c, data) { celebration = null }
        }
    }
}

/**
 * Barra inferior: el pill lima sigue al dedo mientras deslizas entre pestañas (posición del carrusel)
 * y se asienta con su spring; icono y etiqueta cambian de color en 180 ms y el icono elegido da un saltito.
 */
@Composable
private fun BottomBar(pager: androidx.compose.foundation.pager.PagerState, onSelect: (Int) -> Unit) {
    val ui = LocalUi.current
    val selected = pager.currentPage
    val reduce = LocalReduceMotion.current
    Column(Modifier.fillMaxWidth().background(ui.bg)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(ui.hairline))
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp).height(54.dp)
        ) {
            val gap = 4.dp
            val tabW = (maxWidth - gap * (tabs.size - 1)) / tabs.size
            val pos = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (tabs.size - 1).toFloat())
            val shape = RoundedCornerShape(14.dp)
            val labelSize = with(androidx.compose.ui.platform.LocalDensity.current) { (if (tabW < 72.dp) 10 else 11).dp.toSp() }
            Box(
                Modifier.offset(x = (tabW + gap) * pos).size(tabW, 54.dp).clip(shape)
                    .background(ui.selTint).border(2.dp, ui.accentInk, shape)
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == selected
                    val c by androidx.compose.animation.animateColorAsState(
                        if (sel) ui.accentInk else ui.muted, motion(androidx.compose.animation.core.tween(180)), label = "tabc"
                    )
                    val bump = remember { androidx.compose.animation.core.Animatable(1f) }
                    LaunchedEffect(sel) {
                        if (sel && !reduce) {
                            bump.animateTo(1.12f, androidx.compose.animation.core.tween(110, easing = EaseOutQuartCurve))
                            bump.animateTo(1f, androidx.compose.animation.core.spring(0.6f, 900f))
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxSize().clip(shape)
                            .clickable { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        UIcon(t.icon, c, size = 22.dp, modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value })
                        // Tamaño fijo en dp: con la letra del sistema agrandada, «Calendario» en negrita
                        // era más ancho que la pestaña y tocaba el borde de la píldora.
                        Text(
                            t.label,
                            fontSize = labelSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = c,
                            maxLines = 1,
                            softWrap = false,
                            letterSpacing = (-0.1).sp,
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
