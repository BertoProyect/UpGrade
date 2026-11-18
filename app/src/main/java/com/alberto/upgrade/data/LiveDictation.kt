package com.alberto.upgrade.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Dictado en vivo: el texto aparece mientras hablas (resultados parciales del reconocedor del móvil),
 * sin esperar a que termines. Escucha seguido: cuando el reconocedor cierra una frase, vuelve a escuchar
 * hasta que pulses parar. Cada frase cerrada avisa por [onPhrase], para que la IA pueda ir adelantando trabajo.
 *
 * Todo en el hilo principal (lo exige SpeechRecognizer).
 */
class LiveDictation(private val ctx: Context, private val vocab: List<String> = emptyList()) {
    /** Lo dicho hasta ahora (frases cerradas + la que está en curso). */
    var text by mutableStateOf("")
        private set
    /** Nivel de voz de 0 a 1, para la onda. */
    var level by mutableFloatStateOf(0f)
        private set
    var listening by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Una frase cerrada (pausa al hablar): el texto entero hasta ahora. */
    var onPhrase: ((String) -> Unit)? = null

    private var rec: SpeechRecognizer? = null
    private var committed = ""
    private var partial = ""
    private var active = false
    private var finishing: ((String) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private var softErrors = 0

    companion object {
        fun available(ctx: Context): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false)
    }

    private fun current(): String = listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ").trim()

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        // Pausas largas permitidas: así no corta a media idea.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60_000L)
        if (Build.VERSION.SDK_INT >= 33 && vocab.isNotEmpty()) {
            putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(vocab.take(100)))
        }
    }

    /** Empieza a escuchar. Devuelve false si el móvil no tiene reconocedor de voz. */
    fun start(): Boolean {
        stopQuietly()
        if (!available(ctx)) return false
        committed = ""; partial = ""; text = ""; error = null; softErrors = 0
        active = true
        listening = true
        return listenOnce()
    }

    private fun listenOnce(): Boolean {
        val r = rec ?: runCatching { SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull()?.also { it.setRecognitionListener(listener); rec = it }
            ?: run { listening = false; active = false; return false }
        return runCatching { r.startListening(intent()); true }.getOrElse { listening = false; active = false; false }
    }

    /** Para y entrega el texto final (cuando llega el último resultado, o al segundo y medio como mucho). */
    fun finish(onFinal: (String) -> Unit) {
        if (!active && !listening) { onFinal(current()); return }
        active = false
        finishing = onFinal
        runCatching { rec?.stopListening() }
        main.postDelayed({ deliver() }, 1_500)
    }

    private fun deliver() {
        val f = finishing ?: return
        finishing = null
        listening = false
        level = 0f
        val out = current()
        runCatching { rec?.destroy() }
        rec = null
        f(out)
    }

    fun stopQuietly() {
        active = false
        finishing = null
        listening = false
        level = 0f
        main.removeCallbacksAndMessages(null)
        runCatching { rec?.cancel() }
        runCatching { rec?.destroy() }
        rec = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            // −2 dB (silencio) … 10 dB (voz alta)
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (p.isNotBlank()) { partial = p; text = current() }
        }

        override fun onResults(results: Bundle?) {
            val r = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (r.isNotBlank()) committed = listOf(committed, r).filter { it.isNotBlank() }.joinToString(" ")
            partial = ""
            text = current()
            softErrors = 0
            if (active) {
                if (r.isNotBlank()) onPhrase?.invoke(text)
                listenOnce()
            } else deliver()
        }

        override fun onError(code: Int) {
            val soft = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (active && soft && softErrors < 6) {
                // Silencio o algo que no ha entendido: lo dicho se queda y sigue escuchando.
                softErrors++
                if (partial.isNotBlank()) { committed = current(); partial = "" }
                main.postDelayed({ if (active) listenOnce() }, 120)
                return
            }
            if (!active) { deliver(); return }
            active = false
            error = when (code) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permiso de micrófono."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para el dictado."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono está ocupado."
                else -> if (current().isBlank()) "No te he oído." else null
            }
            listening = false
            level = 0f
            val f = finishing
            finishing = null
            f?.invoke(current())
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
