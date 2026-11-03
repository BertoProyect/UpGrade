package com.alberto.upgrade.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alberto.upgrade.R
import com.alberto.upgrade.data.Kind

/** Iconos Lucide sacados de 21st.dev (Cojeev Animated Icon), convertidos a vectores Android. */
object Ic {
    val house = R.drawable.ic_house
    val calendar = R.drawable.ic_calendar
    val calendarDays = R.drawable.ic_calendar_days
    val target = R.drawable.ic_target
    val trophy = R.drawable.ic_trophy
    val flame = R.drawable.ic_flame
    val flameFill = R.drawable.ic_flame_fill
    val zap = R.drawable.ic_zap
    val zapFill = R.drawable.ic_zap_fill
    val award = R.drawable.ic_award
    val crown = R.drawable.ic_crown
    val settings = R.drawable.ic_settings
    val plus = R.drawable.ic_plus
    val check = R.drawable.ic_check
    val circleCheck = R.drawable.ic_circle_check
    val x = R.drawable.ic_x
    val chevronLeft = R.drawable.ic_chevron_left
    val chevronRight = R.drawable.ic_chevron_right
    val lock = R.drawable.ic_lock
    val star = R.drawable.ic_star
    val starFill = R.drawable.ic_star_fill
    val moon = R.drawable.ic_moon
    val bell = R.drawable.ic_bell
    val shield = R.drawable.ic_shield
    val power = R.drawable.ic_power
    val grid = R.drawable.ic_layout_grid
    val phone = R.drawable.ic_smartphone
    val timer = R.drawable.ic_timer
    val gift = R.drawable.ic_gift
    val repeat = R.drawable.ic_repeat
    val clock = R.drawable.ic_clock
    val trash = R.drawable.ic_trash_2
    val party = R.drawable.ic_party_popper
    val search = R.drawable.ic_search
    val activity = R.drawable.ic_activity
    val bookOpen = R.drawable.ic_book_open
    val pencil = R.drawable.ic_pencil
    val fileText = R.drawable.ic_file_text
    val folder = R.drawable.ic_folder
    val notebook = R.drawable.ic_notebook
    val monitor = R.drawable.ic_monitor
    val sparkles = R.drawable.ic_sparkles
    val users = R.drawable.ic_users
    val user = R.drawable.ic_user
}

@DrawableRes
fun kindIcon(k: Kind): Int = when (k) {
    Kind.DEBERES -> Ic.pencil
    Kind.EXAMEN -> Ic.fileText
    Kind.TRABAJO -> Ic.folder
    Kind.TAREA -> Ic.circleCheck
}

@DrawableRes
fun areaIcon(id: String?): Int = when (id) {
    "insti" -> Ic.bookOpen
    "gym" -> Ic.activity
    "orbita" -> Ic.monitor
    "berto" -> Ic.sparkles
    "vaelor" -> Ic.users
    else -> Ic.user
}

@Composable
fun UIcon(@DrawableRes res: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Icon(painterResource(res), contentDescription = null, modifier = modifier.size(size), tint = tint)
}
