package com.alberto.upgrade.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.alberto.upgrade.ui.ChunkyButton
import com.alberto.upgrade.ui.GhostButton
import com.alberto.upgrade.ui.Ic
import com.alberto.upgrade.ui.LocalReduceMotion
import com.alberto.upgrade.ui.LocalUi
import com.alberto.upgrade.ui.UIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Cámara de UpGrade, dentro de la app (no la del móvil): visor a pantalla completa, disparador lima,
 * flash, cambiar de cámara y elegir de la galería. Tras la foto: «Repetir» o «Usar foto».
 * Entrega un JPEG de 1600 px como mucho (suficiente para leer una pizarra o una agenda).
 */
@Composable
fun CameraCapture(onClose: () -> Unit, onPhoto: (ByteArray) -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    var back by remember { mutableStateOf(true) }
    var flash by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val blink = remember { Animatable(0f) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    BackHandler { if (shot != null) shot = null else onClose() }

    LaunchedEffect(Unit) {
        val f = ProcessCameraProvider.getInstance(ctx)
        f.addListener({ provider = runCatching { f.get() }.getOrNull() ?: run { error = "No se ha podido abrir la cámara."; null } }, ContextCompat.getMainExecutor(ctx))
    }
    LaunchedEffect(provider, back, shot == null) {
        val p = provider ?: return@LaunchedEffect
        if (shot != null) return@LaunchedEffect
        runCatching {
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            p.unbindAll()
            p.bindToLifecycle(owner, if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
        }.onFailure { error = "No se ha podido abrir la cámara." }
    }
    DisposableEffect(Unit) { onDispose { runCatching { provider?.unbindAll() } } }
    capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            shot = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let { toJpeg(it, 0) }
                }.getOrNull()
            }
        }
    }

    fun take() {
        if (busy) return
        busy = true
        if (!reduce) scope.launch { blink.snapTo(0.7f); blink.animateTo(0f, tween(220)) }
        capture.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                scope.launch {
                    val bytes = withContext(Dispatchers.Default) {
                        runCatching { toJpeg(image.toBitmap(), image.imageInfo.rotationDegrees) }.getOrNull().also { image.close() }
                    }
                    busy = false
                    if (bytes != null) shot = bytes else error = "No se ha podido hacer la foto."
                }
            }
            override fun onError(exception: ImageCaptureException) {
                busy = false
                error = "No se ha podido hacer la foto."
            }
        })
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val s = shot
        if (s == null) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = blink.value }.background(Color.White))
        } else {
            val bmp = remember(s) { BitmapFactory.decodeByteArray(s, 0, s.size)?.asImageBitmap() }
            if (bmp != null) Image(bmp, contentDescription = "Foto hecha", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }

        // Arriba: cerrar y flash.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundTap(Ic.x, "Cerrar cámara") { if (shot != null) shot = null else onClose() }
            Text(
                if (shot == null) "Haz una foto y lo apunto" else "¿La uso?", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            if (shot == null) RoundTap(if (flash) Ic.zapFill else Ic.zap, if (flash) "Quitar flash" else "Poner flash", on = flash) { flash = !flash }
        }

        error?.let {
            Text(
                it, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xCC141413)).padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // Abajo: galería · disparador · girar  /  Repetir · Usar foto.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp)) {
            if (shot == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    RoundTap(Ic.image, "Elegir de la galería", big = true) {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    Shutter(busy) { take() }
                    RoundTap(Ic.switchCamera, "Cambiar de cámara", big = true) { back = !back }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton("Repetir", { shot = null }, Modifier.weight(1f), icon = Ic.rotateCcw, iconSize = 18.dp)
                    ChunkyButton("Usar foto", { shot?.let(onPhoto) }, Modifier.weight(1f), icon = Ic.check, iconSize = 18.dp)
                }
            }
        }
    }
}

/** Disparador: aro blanco y centro lima que se encoge al pulsar. */
@Composable
private fun Shutter(busy: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val k by androidx.compose.animation.core.animateFloatAsState(if (pressed || busy) 0.86f else 1f, com.alberto.upgrade.ui.motion(com.alberto.upgrade.ui.UpMotion.state()), label = "shutter")
    Box(
        Modifier.size(84.dp).border(4.dp, Color.White, CircleShape).padding(8.dp)
            .clip(CircleShape).clickable(interactionSource = src, indication = null, onClickLabel = "Hacer foto", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().scale(k).clip(CircleShape).background(ui.primary))
    }
}

@Composable
private fun RoundTap(icon: Int, label: String, big: Boolean = false, on: Boolean = false, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(
        Modifier.size(if (big) 56.dp else 44.dp).clip(CircleShape).background(if (on) ui.primary else Color(0x66000000))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { UIcon(icon, if (on) ui.onPrimary else Color.White, size = if (big) 24.dp else 20.dp) }
}

/** Gira según el sensor, reduce a 1600 px como mucho y comprime a JPEG. */
private fun toJpeg(src: Bitmap, rotation: Int): ByteArray {
    val max = 1600
    val scale = minOf(1f, max / maxOf(src.width, src.height).toFloat())
    val m = Matrix().apply { if (rotation != 0) postRotate(rotation.toFloat()); if (scale < 1f) postScale(scale, scale) }
    val out = if (rotation != 0 || scale < 1f) Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true) else src
    val bytes = ByteArrayOutputStream().use { o -> out.compress(Bitmap.CompressFormat.JPEG, 82, o); o.toByteArray() }
    if (out !== src) out.recycle()
    src.recycle()
    return bytes
}
