package com.alberto.upgrade.ui

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
import com.alberto.upgrade.ui.screens.BlockedAppsScreen
import com.alberto.upgrade.ui.screens.CalendarScreen
import com.alberto.upgrade.ui.screens.Celebration
import com.alberto.upgrade.ui.screens.CelebrationOverlay
import com.alberto.upgrade.ui.screens.FocusScreen
import com.alberto.upgrade.ui.screens.PermissionsScreen
import com.alberto.upgrade.ui.screens.ProfileScreen
import com.alberto.upgrade.ui.screens.ReviewScreen
import com.alberto.upgrade.ui.screens.SettingsScreen
import com.alberto.upgrade.ui.screens.TodayScreen
import kotlinx.coroutines.delay
import java.time.LocalDate

private data class TabDef(val icon: Int, val label: String)

private val tabs = listOf(
    TabDef(Ic.house, "Hoy"),
    TabDef(Ic.calendarDays, "Calendario"),
    TabDef(Ic.target, "Foco"),
    TabDef(Ic.trophy, "Perfil")
)

/** Estado compartido de la UI, para que las pantallas premien y naveguen. */
class AppActions(
    val award: (Award?, String) -> Unit,
    val openAdd: (LocalDate?) -> Unit,
    val openTask: (TaskItem) -> Unit,
    val openOverlay: (String) -> Unit,
    val goTab: (Int) -> Unit
)

@Composable
fun AppRoot() {
    val ui = LocalUi.current
    val data by Repo.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var addDate by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var celebration by remember { mutableStateOf<Celebration?>(null) }

    val actions = remember {
        AppActions(
            award = { a, msg ->
                if (a != null && a.xp > 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    toast = "$msg  +${a.xp} XP"
                    when {
                        a.goalReached -> celebration = Celebration.Goal(a.streak)
                        a.levelUp != null -> celebration = Celebration.Level(a.levelUp)
                    }
                }
            },
            openAdd = { d -> addDate = d; editing = null; showAdd = true },
            openTask = { t -> editing = t; showAdd = true },
            openOverlay = { overlay = it },
            goTab = { tab = it }
        )
    }

    val open by MainActivity.pendingOpen
    LaunchedEffect(open) {
        when (open) {
            "review" -> overlay = "review"
            "focus" -> tab = 2
            "add" -> { editing = null; addDate = null; showAdd = true }
            "permissions" -> overlay = "permissions"
        }
        if (open != null) MainActivity.pendingOpen.value = null
    }
    LaunchedEffect(Unit) {
        if (!Repo.data.permissionsShown) {
            overlay = "permissions"
            Repo.update { it.copy(permissionsShown = true) }
        }
        while (true) {
            delay(1000)
            Repo.claimFocus()?.let { actions.award(it, "¡Foco completado!") }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1900)
            toast = null
        }
    }

    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = overlay == null && tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize().background(ui.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TodayScreen(data, actions)
                    1 -> CalendarScreen(data, actions)
                    2 -> FocusScreen(data, actions)
                    else -> ProfileScreen(data, actions)
                }
            }
            BottomBar(tab) { tab = it }
        }

        if (tab == 0 || tab == 1) {
            Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 88.dp)
            ) {
                ChunkyButton("", { actions.openAdd(null) }, Modifier.size(width = 64.dp, height = 68.dp), height = 64.dp, icon = Ic.plus)
            }
        }

        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(ui.successTint).border(2.dp, ui.success, RoundedCornerShape(16.dp))
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

        AnimatedVisibility(overlay != null, enter = slideInVertically { it / 6 } + fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(ui.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (overlay) {
                    "review" -> ReviewScreen(data, actions) { overlay = null }
                    "settings" -> SettingsScreen(data, actions) { overlay = null }
                    "permissions" -> PermissionsScreen { overlay = null }
                    "apps" -> BlockedAppsScreen(data) { overlay = null }
                }
            }
        }

        if (showAdd) {
            AddSheet(
                data = data,
                initialDate = addDate,
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

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().background(ui.bg)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(ui.hairline))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .then(
                            if (sel) Modifier.background(ui.primary.copy(alpha = if (ui.dark) 0.12f else 0.18f))
                                .border(2.dp, ui.accentInk.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            else Modifier
                        )
                        .clickable { onSelect(i) }
                        .padding(top = 8.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UIcon(t.icon, if (sel) ui.accentInk else ui.muted, size = 24.dp)
                    Text(
                        t.label,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (sel) ui.accentInk else ui.muted
                    )
                }
            }
        }
    }
}
