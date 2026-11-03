package com.alberto.upgrade.data

object Seed {
    val subjects = listOf(
        Subject("mates", "Mates", "blue"),
        Subject("lengua", "Lengua", "red"),
        Subject("ingles", "Inglés", "pink"),
        Subject("historia", "Historia", "brown"),
        Subject("filosofia", "Filosofía", "purple"),
        Subject("economia", "Economía", "green"),
        Subject("cultura", "Cultura", "yellow"),
        Subject("tic", "TIC", "teal"),
        Subject("ef", "E.F", "orange"),
        Subject("religion", "Religión", "gray")
    )

    val areas = listOf(
        Area("insti", "Bachillerato", "blue", "📚"),
        Area("gym", "Gym", "orange", "💪"),
        Area("orbita", "Órbita Webs", "purple", "🪐"),
        Area("berto", "BertoProject", "yellow", "🚀"),
        Area("vaelor", "Vaelor", "green", "🤝"),
        Area("personal", "Personal", "gray", "✨")
    )

    val periods = listOf(
        Period(8 * 60 + 30, 9 * 60 + 30),
        Period(9 * 60 + 30, 10 * 60 + 30),
        Period(10 * 60 + 30, 11 * 60 + 30),
        Period(12 * 60, 13 * 60),
        Period(13 * 60, 14 * 60),
        Period(14 * 60, 15 * 60)
    )

    private val week = mapOf(
        1 to listOf("ingles", "historia", "mates", "economia", "cultura", "lengua"),
        2 to listOf("ef", "tic", "economia", "lengua", "historia", "ingles"),
        3 to listOf("religion", "filosofia", "ef", "tic", "mates", "ingles"),
        4 to listOf("filosofia", "economia", "ingles", "mates", "historia", "lengua"),
        5 to listOf("filosofia", "economia", "historia", "mates", "cultura", "lengua")
    )

    fun apply(d: AppData): AppData {
        val slots = week.flatMap { (day, list) ->
            list.mapIndexed { i, s -> ClassSlot(day, i, s) }
        }
        val now = System.currentTimeMillis()
        val tasks = listOf(
            TaskItem("seed-1", "2ª redacción de Lengua", Kind.TRABAJO, "insti", "lengua", "2026-11-03", createdAt = now),
            TaskItem("seed-2", "Examen de Lengua", Kind.EXAMEN, "insti", "lengua", "2026-11-04", createdAt = now),
            TaskItem("seed-3", "Examen de Historia", Kind.EXAMEN, "insti", "historia", "2026-11-10", createdAt = now)
        )
        return d.copy(
            seeded = true,
            subjects = subjects,
            areas = areas,
            periods = periods,
            timetable = slots,
            tasks = d.tasks + tasks
        )
    }
}
