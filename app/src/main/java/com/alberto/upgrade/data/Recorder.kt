package com.alberto.upgrade.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Graba la voz para la IA: AAC (ADTS), 16 kHz, mono, 24 kbps. Gemini reduce el audio a 16 kbps
 * y a un canal de todas formas, así que grabar con más calidad solo alarga la subida.
 */
class Recorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private val file = File(ctx.cacheDir, "dictado.aac")
    var startedAt = 0L
        private set

    val recording: Boolean get() = rec != null

    fun start(): Boolean {
        stopQuietly()
        file.delete()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(24_000)
            r.setMaxDuration(MAX_MS)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            rec = r
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            false
        }
    }

    /** Nivel de 0 a 1 para dibujar la onda. */
    fun level(): Float {
        val amp = runCatching { rec?.maxAmplitude ?: 0 }.getOrDefault(0)
        return (amp / 12_000f).coerceIn(0f, 1f)
    }

    /** Para y devuelve el audio, o null si es demasiado corto o falló. */
    fun stop(): ByteArray? {
        val r = rec ?: return null
        rec = null
        // Si ya se paró solo (duración máxima) stop() falla, pero el AAC/ADTS se puede leer igual.
        runCatching { r.stop() }
        runCatching { r.release() }
        if (!file.exists()) return null
        val bytes = file.readBytes()
        file.delete()
        return if (bytes.size < 2_000) null else bytes
    }

    fun stopQuietly() {
        val r = rec ?: return
        rec = null
        runCatching { r.stop() }
        runCatching { r.release() }
        file.delete()
    }

    companion object {
        const val MAX_MS = 120_000
    }
}
