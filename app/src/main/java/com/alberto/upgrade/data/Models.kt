package com.alberto.upgrade.data

import kotlinx.serialization.Serializable

@Serializable
enum class Kind(val label: String, val emoji: String, val xp: Int) {
    DEBERES("Deberes", "📝", 10),
    EXAMEN("Examen", "🎯", 40),
    TRABAJO("Trabajo", "📂", 25),
    TAREA("Tarea", "✅", 10),
    LLAMADA("Llamada", "📞", 10),
    /** Sesión de un plan de repaso: solo da el XP del foco que lanza. */
    REPASO("Repaso", "📖", 0),
    OTRO("Otro", "•", 10)
}

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val kind: Kind = Kind.TAREA,
    val areaId: String = "personal",
    val subjectId: String? = null,
    val due: String? = null,          // yyyy-MM-dd
    val time: Int? = null,            // minutos desde 00:00 (opcional)
    val done: Boolean = false,
    val doneAt: Long? = null,
    val xpAwarded: Int = 0,
    val xpDate: String? = null,
    val notes: String = "",
    val createdAt: Long = 0,
    /** Para tareas de tipo Repaso: el examen al que pertenecen y cuánto dura la sesión. */
    val examId: String? = null,
    val minutes: Int? = null
)

@Serializable
data class EventItem(
    val id: String,
    val title: String,
    val areaId: String = "personal",
    val date: String? = null,             // evento puntual
    val weekdays: List<Int> = emptyList(), // 1 = lunes … 7 = domingo (recurrente)
    val start: Int = 17 * 60,             // minutos desde 00:00
    val end: Int = 18 * 60
)

@Serializable
data class Period(val start: Int, val end: Int)

@Serializable
data class ClassSlot(val day: Int, val period: Int, val subjectId: String)

@Serializable
data class Subject(val id: String, val name: String, val color: String)

@Serializable
data class Area(
    val id: String,
    val name: String,
    val color: String,
    val emoji: String,
    /** Icono Lucide de un área propia (las de serie tienen el suyo fijo). */
    val icon: String? = null
)

@Serializable
data class DayReview(val rating: Int, val note: String = "", val firstTomorrow: String = "")

@Serializable
data class FocusState(
    val startedAt: Long = 0,
    val endAt: Long = 0,
    val minutes: Int = 0,
    val rewarded: Boolean = true,
    /** Tarea de Repaso que se completa al acabar este foco. */
    val taskId: String? = null
)

@Serializable
data class BlockSettings(
    val enabled: Boolean = true,
    val scheduleEnabled: Boolean = true,
    val days: List<Int> = listOf(1, 2, 3, 4, 5),
    val from: Int = 17 * 60,
    val to: Int = 20 * 60,
    val allowed: List<String> = listOf("com.android.chrome"),
    val waitMinutes: Int = 10,
    val unlockMinutes: Int = 15,
    val unlockRequestedAt: Long = 0,
    val allowedSeeded: Boolean = false
)

@Serializable
data class NotifSettings(
    val morning: Int = 7 * 60 + 40,
    val afternoon: Int = 16 * 60 + 30,
    val streak: Int = 20 * 60 + 30,
    val review: Int = 21 * 60 + 45
)

const val DEFAULT_AI_MODEL = "gemini-3.5-flash-lite"

/** XP que da cada cosa. Se edita en Ajustes › XP por tipo. */
@Serializable
data class XpConfig(
    /** Tarea completada (todo menos exámenes y repasos). */
    val task: Int = 5,
    val gym: Int = 20,
    val fisio: Int = 20,
    /** Foco: por cada 30 min completos (mínimo 30). */
    val focus30: Int = 5,
    /** Rutina "Despertar sin dopamina", si dura ≥ 60 min. */
    val wake: Int = 10,
    /** Rutina personalizada, si dura ≥ 30 min. */
    val routine: Int = 15
)

@Serializable
data class AiExample(val input: String, val output: String)

@Serializable
data class AiTiming(
    val at: Long,
    /** Milisegundos hasta la primera propuesta (o -1 si no llegó ninguna). */
    val firstMs: Long,
    val totalMs: Long,
    val mode: String,
    val model: String,
    val ok: Boolean
)

@Serializable
data class AppData(
    val version: Int = 1,
    val seeded: Boolean = false,
    val permissionsShown: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val events: List<EventItem> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val areas: List<Area> = emptyList(),
    val periods: List<Period> = emptyList(),
    val timetable: List<ClassSlot> = emptyList(),
    val xpLog: Map<String, Int> = emptyMap(),
    val totalXp: Int = 0,
    val dailyGoal: Int = 80,
    val bestStreak: Int = 0,
    val reviews: Map<String, DayReview> = emptyMap(),
    val gymLog: Map<String, String> = emptyMap(),
    val gymWeeklyTarget: Int = 4,
    val focusLog: Map<String, Int> = emptyMap(),
    val focus: FocusState = FocusState(),
    val block: BlockSettings = BlockSettings(),
    val notif: NotifSettings = NotifSettings(),
    /** Obsoleto: la clave vive cifrada en SecretStore. Solo se lee para migrar copias antiguas. */
    val geminiKey: String = "",
    val aiModel: String = DEFAULT_AI_MODEL,
    /** Migración a la IA v2 (modelo rápido, clave cifrada). */
    val aiV2: Boolean = false,
    /** Dictado preciso: transcribe primero con el modelo de transcripción y vocabulario propio. */
    val aiPrecise: Boolean = false,
    /** Palabras que el dictado debe entender (nombres, marcas, asignaturas…). */
    val aiVocab: List<String> = emptyList(),
    /** Últimas correcciones del usuario: se usan como ejemplos en el prompt. */
    val aiExamples: List<AiExample> = emptyList(),
    /** Tiempos de las últimas peticiones a la IA. */
    val aiTimings: List<AiTiming> = emptyList(),
    val areasV2: Boolean = false,
    val onboarded: Boolean = false,
    /** Áreas que el usuario desmarcó en la bienvenida ("gym" incluido). */
    val hiddenAreas: List<String> = emptyList(),
    val lastBackupAt: Long = 0,
    /** "light", "dark" o "system". */
    val themeMode: String = "system",
    /** Sistema de XP v2 (meta 80, XP por tipo, tope, fisio). */
    val xpV2: Boolean = false,
    /** Meta de sábado y domingo; [dailyGoal] es la de lunes a viernes. */
    val weekendGoal: Int = 80,
    val xp: XpConfig = XpConfig(),
    /** Tope diario de XP por tareas; 0 = sin tope. */
    val taskCap: Int = 40,
    /** Días (yyyy-MM-dd) con los ejercicios del fisio hechos. */
    val fisioLog: List<String> = emptyList(),
    val fisioWeeklyTarget: Int = 7
) {
    fun subject(id: String?) = subjects.firstOrNull { it.id == id }
    fun area(id: String?) = areas.firstOrNull { it.id == id }
    fun shows(areaId: String) = areaId !in hiddenAreas
}
