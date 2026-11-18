package com.alberto.upgrade.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualización automática: mira la última versión publicada en GitHub (el repo es público), la descarga
 * y la instala con el instalador del sistema. La primera vez Android pide confirmarlo; desde entonces, como
 * UpGrade ya es quien se instala a sí misma, Android 12+ deja actualizar sin preguntar.
 * Para no cerrarte la app en mitad de algo, la instalación se hace al salir de ella (o al momento si la pides).
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/BertoProyect/UpGrade/releases/latest"
    private const val ACTION = "com.alberto.upgrade.UPDATE_STATUS"
    private const val CHECK_EVERY = 20 * 60_000L

    /** Lo que se enseña en Ajustes › General. */
    val status = mutableStateOf("")
    /** Versión descargada y lista para instalar (o null). */
    val ready = mutableStateOf<String?>(null)

    @Volatile private var lastCheck = 0L
    @Volatile private var busy = false
    private val json = Json { ignoreUnknownKeys = true }

    fun current(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()

    /** 1.2.10 > 1.2.9; las de prueba («-prueba.N») nunca cuentan como nuevas. */
    fun newer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    private fun apk(ctx: Context) = File(ctx.cacheDir, "update.apk")

    /** Al abrir la app (como mucho cada 20 min, o siempre con [force]): busca y descarga. */
    fun check(ctx: Context, force: Boolean = false, installNow: Boolean = false) {
        val app = ctx.applicationContext
        if (!force && (!Repo.data.autoUpdate || System.currentTimeMillis() - lastCheck < CHECK_EVERY)) return
        if (busy) return
        busy = true
        lastCheck = System.currentTimeMillis()
        Repo.scope.launch {
            try {
                if (force) status.value = "Buscando…"
                val (tag, url) = withContext(Dispatchers.IO) { latest() } ?: run { if (force) status.value = "No se ha podido comprobar."; return@launch }
                val local = current(app)
                if (!newer(tag, local)) { ready.value = null; if (force) status.value = "Tienes la última ($local)."; return@launch }
                status.value = "Descargando $tag…"
                val ok = withContext(Dispatchers.IO) { download(url, apk(app)) }
                if (!ok) { status.value = "No se ha podido descargar $tag."; return@launch }
                ready.value = tag.removePrefix("v")
                status.value = "Versión ${tag.removePrefix("v")} lista."
                if (installNow || !selfInstalled(app)) install(app)
            } finally { busy = false }
        }
    }

    private fun latest(): Pair<String, String>? = runCatching {
        val c = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        if (c.responseCode != 200) return null
        val o = json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() }).jsonObject
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val asset = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { (it["name"]?.jsonPrimitive?.contentOrNull ?: "").endsWith(".apk") } ?: return null
        tag to (asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null)
    }.getOrNull()

    private fun download(url: String, to: File): Boolean = runCatching {
        var u = URL(url)
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            c = (u.openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 30_000; instanceFollowRedirects = false }
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) { u = URL(u, c.getHeaderField("Location")); continue }
            if (code != 200) return false
            break
        }
        val tmp = File(to.parentFile, to.name + ".part")
        c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        tmp.length() > 1_000_000 && tmp.renameTo(to)
    }.getOrDefault(false)

    /**
     * Instala lo descargado. Sin permiso de «instalar apps» no hace nada (Ajustes lo pide).
     * Si Android necesita confirmación, se abre su ventana (solo la primera vez).
     */
    fun install(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val f = apk(app)
        if (!f.exists() || ready.value == null || !canInstall(app)) return false
        return runCatching {
            val pi = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(app.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                f.inputStream().use { i -> s.openWrite("upgrade.apk", 0, f.length()).use { o -> i.copyTo(o); s.fsync(o) } }
                val intent = Intent(app, UpdateReceiver::class.java).setAction(ACTION)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                s.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
            }
            status.value = "Instalando ${ready.value}…"
            true
        }.getOrElse { status.value = "No se ha podido instalar."; false }
    }

    /** ¿UpGrade se instaló a sí misma la última vez? Entonces Android deja actualizar sin preguntar. */
    fun selfInstalled(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 31 && runCatching {
        ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName == ctx.packageName
    }.getOrDefault(false)

    /**
     * Al salir de la app: si hay versión lista y se puede sin preguntar, se instala (Android cierra UpGrade
     * y la próxima vez que la abras ya está actualizada).
     */
    fun installIfReady(ctx: Context) {
        if (Repo.data.autoUpdate && ready.value != null && selfInstalled(ctx)) install(ctx)
    }

    /** Al volver a la app: si hay versión lista y Android aún va a preguntar (la primera vez), se pide ya, con la app delante. */
    fun installIfNeedsConfirm(ctx: Context) {
        if (Repo.data.autoUpdate && ready.value != null && !selfInstalled(ctx)) install(ctx)
    }
}

/** Respuesta del instalador: si pide confirmación, se abre su ventana. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.status.value = "Actualizada."
            else -> Updater.status.value = "No se ha instalado: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "error")
        }
    }
}
