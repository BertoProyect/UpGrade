package com.alberto.upgrade.ui.screens

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Ic.x, onClose)
            Text("Configura UpGrade", color = ui.ink, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Tu OPPO (ColorOS) cierra apps en segundo plano para ahorrar batería. Sin estos permisos, los avisos llegan tarde y el bloqueo se apaga solo. Hazlo una vez y olvídate.",
                color = ui.text, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp)
            )

            PermCard(
                "1. Notificaciones", notifOk,
                "Plan del día, exámenes, racha y cierre del día.",
                "Permitir"
            ) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }

            PermCard(
                "2. Batería sin restricciones", battOk,
                "Para que ColorOS no mate los avisos ni el bloqueo. Elige «Permitir».",
                "Quitar restricción"
            ) {
                requestIgnoreBattery(ctx)
            }

            PermCard(
                "3. Bloqueo de apps (Accesibilidad)", accOk,
                "Ajustes > Accesibilidad > Apps descargadas > UpGrade · Bloqueo > Activar.\n\nSi aparece en gris o dice «ajuste restringido»: Ajustes > Apps > UpGrade > menú (arriba a la derecha) > «Permitir ajustes restringidos», y vuelve a intentarlo.",
                "Abrir Accesibilidad"
            ) {
                open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            PermCard(
                "4. Inicio automático y segundo plano", null,
                "En Ajustes > Apps > UpGrade: activa «Permitir inicio automático» y «Permitir actividad en segundo plano». Si no ves la opción, está en Batería dentro de la app.",
                "Abrir info de la app"
            ) {
                open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            }

            if (!alarmOk) {
                PermCard(
                    "5. Alarmas exactas", false,
                    "Para que los avisos lleguen a su hora.",
                    "Permitir"
                ) {
                    if (Build.VERSION.SDK_INT >= 31) open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
            }

            PermCard(
                "Widget", null,
                "Mantén pulsado un hueco de la pantalla de inicio > Widgets > UpGrade · Hoy. Desde ahí marcas tareas sin abrir la app.",
                null
            ) {}

            Spacer(Modifier.height(16.dp))
        }
        ChunkyButton("Listo", onClose, Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun PermCard(title: String, ok: Boolean?, body: String, button: String?, onClick: () -> Unit) {
    val ui = LocalUi.current
    ChunkyCard(
        Modifier.padding(vertical = 6.dp),
        bg = if (ok == true) ui.successTint else ui.card,
        edge = if (ok == true) ui.successLip.copy(alpha = 0.6f) else ui.hairline
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = ui.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            when (ok) {
                true -> UIcon(Ic.circleCheck, ui.successInk, size = 22.dp)
                false -> UIcon(Ic.shield, ui.danger, size = 22.dp)
                null -> {}
            }
        }
        Text(body, color = ui.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        if (button != null && ok != true) {
            Spacer(Modifier.height(10.dp))
            ChunkyButton(button, onClick, Modifier.fillMaxWidth(), height = 42.dp, fontSize = 13.sp)
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
