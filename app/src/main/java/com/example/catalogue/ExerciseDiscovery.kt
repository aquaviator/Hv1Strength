package com.example.catalogue

import com.example.data.Exercise
import com.example.data.LoggedSet
import com.example.data.WorkoutSession

enum class LibrarySection { ALL, FAVOURITES, RECENT, CUSTOM }

fun favouritePreferenceKey(userId: String): String = "favorite_exercises_${userId.ifBlank { "offline" }}"
fun toggledFavourite(current: Set<String>, exerciseId: String): Set<String> =
    if (exerciseId in current) current - exerciseId else current + exerciseId

data class ExerciseDiscoveryFilters(
    val query: String = "",
    val category: String? = null,
    val muscle: String? = null,
    val equipment: String? = null,
    val capability: MeasurementCapability? = null,
    val section: LibrarySection = LibrarySection.ALL
)

fun recentExerciseIds(
    sessions: List<WorkoutSession>,
    sets: List<LoggedSet>,
    limit: Int = 12
): List<String> {
    val sessionOrder = sessions.sortedWith(compareByDescending<WorkoutSession> { it.endTime }.thenByDescending { it.id })
        .mapIndexed { index, session -> session.id to index }.toMap()
    return sets.asSequence().filter { it.isCompleted && it.sessionId in sessionOrder }
        .sortedWith(compareBy<LoggedSet> { sessionOrder[it.sessionId] ?: Int.MAX_VALUE }.thenByDescending { it.id })
        .map { it.exerciseId }.distinct().take(limit).toList()
}

fun discoverExercises(
    exercises: List<Exercise>,
    catalogueById: Map<String, CatalogueExercise>,
    favourites: Set<String>,
    recentIds: List<String>,
    filters: ExerciseDiscoveryFilters
): List<Exercise> {
    val recentOrder = recentIds.withIndex().associate { it.value to it.index }
    return exercises.asSequence().filter { exercise ->
        val governed = catalogueById[exercise.id]
        val sectionMatch = when (filters.section) {
            LibrarySection.ALL -> governed?.active != false
            LibrarySection.FAVOURITES -> exercise.id in favourites && governed?.active != false
            LibrarySection.RECENT -> exercise.id in recentOrder
            LibrarySection.CUSTOM -> exercise.isCustom
        }
        sectionMatch && exerciseMatches(exercise, filters.query, governed) &&
            (filters.category == null || exercise.category.equals(filters.category, true)) &&
            (filters.muscle == null || governed?.let { filters.muscle in it.primaryMuscles || filters.muscle in it.secondaryMuscles } == true) &&
            (filters.equipment == null || governed?.equipment?.contains(filters.equipment) == true) &&
            (filters.capability == null || governed?.capabilities?.contains(filters.capability) == true)
    }.sortedWith(if (filters.section == LibrarySection.RECENT) compareBy { recentOrder[it.id] ?: Int.MAX_VALUE }
        else compareBy<Exercise> { it.name.lowercase() }.thenBy { it.id }).toList()
}

data class ExerciseDetails(
    val source: String,
    val category: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: List<String>,
    val measurements: List<String>,
    val laterality: String,
    val replacementId: String?
)

fun exerciseDetails(exercise: Exercise, governed: CatalogueExercise?): ExerciseDetails = ExerciseDetails(
    source = if (exercise.isCustom) "Custom exercise" else "Human V1 governed library",
    category = exercise.category,
    primaryMuscles = governed?.primaryMuscles ?: listOf(exercise.category.lowercase()),
    secondaryMuscles = governed?.secondaryMuscles ?: emptyList(),
    equipment = governed?.equipment ?: emptyList(),
    measurements = (governed?.capabilities ?: setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.LOAD, MeasurementCapability.RPE)).map { it.wireName }.sorted(),
    laterality = governed?.laterality ?: "user defined",
    replacementId = governed?.replacementId
)
