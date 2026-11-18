package com.alberto.upgrade.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/**
 * Adelanta la petición a la IA mientras hablas: en cada pausa manda lo dicho hasta ahí. Si al parar
 * el texto final es el mismo, la respuesta ya está en camino (o hecha) y se aprovecha; si no, se tira.
 * Con límites para no gastar la cuota gratuita: 4 palabras mínimo, 2,5 s entre envíos y 3 por dictado.
 */
class Speculator<E>(
    private val scope: CoroutineScope,
    private val isDone: (E) -> Boolean,
    private val start: (String) -> Flow<E>
) {
    private var text: String? = null
    private var shared: MutableSharedFlow<E>? = null
    private var job: Job? = null
    private var lastAt = 0L
    private var count = 0

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun offer(t: String) {
        val clean = norm(t)
        if (clean.split(' ').size < 4 || clean == text) return
        val now = System.currentTimeMillis()
        if (now - lastAt < 2_500 || count >= 3) return
        cancel()
        lastAt = now
        count++
        text = clean
        val flow = MutableSharedFlow<E>(replay = 1_000)
        shared = flow
        job = scope.launch { start(t).collect { flow.emit(it) } }
    }

    /** La respuesta adelantada si [final] coincide con lo que se mandó; si no, null (y se cancela). */
    fun take(final: String): Flow<E>? {
        val s = shared
        if (s == null || norm(final) != text) { cancel(); return null }
        shared = null
        text = null
        job = null
        return s.transformWhile { emit(it); !isDone(it) }
    }

    fun cancel() {
        job?.cancel()
        job = null
        shared = null
        text = null
    }

    fun reset() { cancel(); count = 0; lastAt = 0 }
}
