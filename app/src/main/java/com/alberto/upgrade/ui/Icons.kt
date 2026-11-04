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

/** Iconos Lucide (trazo 2), convertidos a vectores Android. */
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
    val mic = R.drawable.ic_mic
    val phoneCall = R.drawable.ic_phone
    val briefcase = R.drawable.ic_briefcase
    val layers = R.drawable.ic_layers
    val send = R.drawable.ic_send
    val list = R.drawable.ic_list
    val sun = R.drawable.ic_sun
    val inbox = R.drawable.ic_inbox
    val calendarClock = R.drawable.ic_calendar_clock
    val arrowLeft = R.drawable.ic_arrow_left
    val notebookPen = R.drawable.ic_notebook_pen
    val globe = R.drawable.ic_globe
    val penTool = R.drawable.ic_pen_tool
    val graduationCap = R.drawable.ic_graduation_cap
    val dumbbell = R.drawable.ic_dumbbell
    val shieldCheck = R.drawable.ic_shield_check
    val shieldBan = R.drawable.ic_shield_ban
    val minus = R.drawable.ic_minus
    val key = R.drawable.ic_key_round
    val backup = R.drawable.ic_hard_drive_download
    val chevronDown = R.drawable.ic_chevron_down
    val chart = R.drawable.ic_chart_column
    val battery = R.drawable.ic_battery_charging
    val accessibility = R.drawable.ic_accessibility
    val logo = R.drawable.ic_logo
    val play = R.drawable.ic_play
    val square = R.drawable.ic_square
    val circleHelp = R.drawable.ic_circle_help
    val arrowRight = R.drawable.ic_arrow_right
    val heartPulse = R.drawable.ic_heart_pulse
    val sliders = R.drawable.ic_sliders_horizontal
    val info = R.drawable.ic_info
    val sunrise = R.drawable.ic_sunrise
    val arrowUpDown = R.drawable.ic_arrow_up_down
    val grip = R.drawable.ic_grip_vertical
    val rotateCcw = R.drawable.ic_rotate_ccw
    val circleDashed = R.drawable.ic_circle_dashed
    val hourglass = R.drawable.ic_hourglass
    val ban = R.drawable.ic_ban
    val download = R.drawable.ic_download
    val alert = R.drawable.ic_triangle_alert
}

@DrawableRes
fun kindIcon(k: Kind): Int = when (k) {
    Kind.DEBERES -> Ic.notebookPen
    Kind.EXAMEN -> Ic.fileText
    Kind.TRABAJO -> Ic.folder
    Kind.TAREA -> Ic.circleCheck
    Kind.LLAMADA -> Ic.phoneCall
    Kind.REPASO -> Ic.bookOpen
    Kind.OTRO -> Ic.circleDashed
}

/** Iconos que se pueden elegir para un área propia (clave Lucide → recurso). */
val areaIcons: List<Pair<String, Int>> = listOf(
    "book-open" to R.drawable.ic_book_open, "briefcase" to R.drawable.ic_briefcase, "music" to R.drawable.ic_music,
    "plane" to R.drawable.ic_plane, "heart" to R.drawable.ic_heart, "coffee" to R.drawable.ic_coffee,
    "code" to R.drawable.ic_code, "camera" to R.drawable.ic_camera, "palette" to R.drawable.ic_palette,
    "wallet" to R.drawable.ic_wallet, "car" to R.drawable.ic_car, "house" to R.drawable.ic_house,
    "leaf" to R.drawable.ic_leaf, "gamepad-2" to R.drawable.ic_gamepad, "star" to R.drawable.ic_star,
    "flask-conical" to R.drawable.ic_flask, "languages" to R.drawable.ic_languages, "shopping-cart" to R.drawable.ic_cart,
    "dumbbell" to R.drawable.ic_dumbbell, "sunrise" to R.drawable.ic_sunrise, "moon" to R.drawable.ic_moon,
    "smartphone" to R.drawable.ic_smartphone, "sun" to R.drawable.ic_sun, "users" to R.drawable.ic_users, "globe" to R.drawable.ic_globe,
    "pen-tool" to R.drawable.ic_pen_tool, "graduation-cap" to R.drawable.ic_graduation_cap, "user" to R.drawable.ic_user
)

@DrawableRes
fun iconByKey(key: String?): Int? = areaIcons.firstOrNull { it.first == key }?.second

@DrawableRes
fun areaIcon(id: String?): Int = when (id) {
    "insti" -> Ic.graduationCap
    "orbita" -> Ic.globe
    "gym" -> Ic.dumbbell
    "berto" -> Ic.penTool
    "vaelor" -> Ic.users
    "personal" -> Ic.user
    "otro", null -> Ic.circleDashed
    else -> iconByKey(com.alberto.upgrade.data.Repo.data.area(id)?.icon) ?: Ic.layers
}

@Composable
fun UIcon(@DrawableRes res: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Icon(painterResource(res), contentDescription = null, modifier = modifier.size(size), tint = tint)
}
