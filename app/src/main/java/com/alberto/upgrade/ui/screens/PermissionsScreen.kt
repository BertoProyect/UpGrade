package com.alberto.upgrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.alberto.upgrade.ui.ScreenHeader
import com.alberto.upgrade.ui.SerifText
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.alberto.upgrade.block.BlockRules
import com.alberto.upgrade.notify.AlarmScheduler
import com.alberto.upgrade.notify.Notifier
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.ChunkyCard
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.IconTap
import com.alberto.upgrade.ui.UIcon

@Composable
fun PermissionsScreen(onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        AlarmScheduler.scheduleAll(ctx)
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val notifOk = remember(refresh) { Notifier.canPost(ctx) }
    val accOk = remember(refresh) { BlockRules.isServiceEnabled(ctx) }
    val battOk = remember(refresh) { isIgnoringBattery(ctx) }
    val alarmOk = remember(refresh) {
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Permisos", onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SerifText(
                "Para avisarte y bloquear apps, UpGrade necesita esto. Nada sale de tu móvil.",
                fontSize = 21.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            PermCard(Ic.bell, "Notificaciones", notifOk, "Recordatorios, racha y cierre del día") {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            PermCard(Ic.battery, "Batería sin restricciones", battOk, "Para que los avisos lleguen a su hora. Elige «Permitir».") {
                requestIgnoreBattery(ctx)
            }
            PermCard(
                Ic.accessibility, "Accesibilidad", accOk,
                if (accOk) "Detecta cuándo abres una app bloqueada"
                else "Detecta cuándo abres una app bloqueada. Ajustes > Accesibilidad > Apps descargadas > UpGrade · Bloqueo. Si sale «ajuste restringido»: Ajustes > Apps > UpGrade > ⋮ > Permitir ajustes restringidos."
            ) {
                open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            PermCard(
                Ic.power, "Inicio automático", null,
                "Vuelve a activarse al reiniciar el móvil. En Ajustes > Apps > UpGrade activa «Inicio automático» y «Actividad en segundo plano»."
            ) {
                open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            }
            if (!alarmOk) {
                PermCard(Ic.clock, "Alarmas exactas", false, "Para que los avisos lleguen a su hora") {
                    if (Build.VERSION.SDK_INT >= 31) open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
            }
            Text(
                "Widget: mantén pulsado un hueco de la pantalla de inicio > Widgets > UpGrade · Hoy.",
                color = ui.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, start = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
        ChunkyButton("Listo", onClose, Modifier.fillMaxWidth().padding(16.dp))
    }
}

/** ok = null: no se puede comprobar (inicio automático); se muestra como "Abrir". */
@Composable
private fun PermCard(icon: Int, title: String, ok: Boolean?, body: String, onClick: () -> Unit) {
    val ui = LocalUi.current
    ChunkyCard(
        Modifier.padding(bottom = 10.dp),
        edge = if (ok == false) ui.danger else ui.hairline,
        lip = if (ok == false) ui.dangerLip else ui.hairline,
        padding = 0.dp,
        onClick = if (ok == true) null else onClick
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(ui.surface), contentAlignment = Alignment.Center) {
                UIcon(icon, ui.ink, size = 20.dp)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                Text(title, color = ui.ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(body, color = ui.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
            when (ok) {
                true -> Row(verticalAlignment = Alignment.CenterVertically) {
                    UIcon(Ic.circleCheck, ui.accentInk, size = 16.dp)
                    Text("Listo", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                }
                false -> ChunkyButton("Activar", onClick, Modifier.width(96.dp), height = 34.dp, fontSize = 12.sp, radius = 10.dp, lipH = 3.dp)
                null -> Text("ABRIR", color = ui.accentInk, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp)
            }
        }
    }
}

private fun open(ctx: Context, i: Intent) {
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

private fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

@SuppressLint("BatteryLife")
private fun requestIgnoreBattery(ctx: Context) {
    open(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
}
