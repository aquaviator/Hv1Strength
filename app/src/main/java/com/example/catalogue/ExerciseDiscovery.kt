package com.example.catalogue

import com.example.data.Exercise
import com.example.measurement.CanonicalMetricDictionary
import com.example.measurement.ExerciseMetricProfileResolver
import com.example.data.LoggedSet
import com.example.data.WorkoutSession

enum class LibrarySection { ALL, FAVOURITES, RECENT, CUSTOM }
enum class ExerciseSource { ALL, GOVERNED, CUSTOM }
enum class ExerciseSort { NAME, CATEGORY, RECENT }

fun favouritePreferenceKey(userId: String): String = "favorite_exercises_${userId.ifBlank { "offline" }}"
fun toggledFavourite(current: Set<String>, exerciseId: String): Set<String> =
    if (exerciseId in current) current - exerciseId else current + exerciseId

data class ExerciseDiscoveryFilters(
    val query: String = "",
    val category: String? = null,
    val muscle: String? = null,
    val equipment: String? = null,
    val capability: MeasurementCapability? = null,
    val section: LibrarySection = LibrarySection.ALL,
    val categories: Set<String> = emptySet(),
    val muscles: Set<String> = emptySet(),
    val equipmentSelections: Set<String> = emptySet(),
    val capabilities: Set<MeasurementCapability> = emptySet(),
    val source: ExerciseSource = ExerciseSource.ALL,
    val sort: ExerciseSort = ExerciseSort.NAME
) {
    val activeFilterCount: Int get() = categories.size + muscles.size + equipmentSelections.size + capabilities.size +
        (if (source == ExerciseSource.ALL) 0 else 1) + (if (section == LibrarySection.ALL) 0 else 1)
}

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
    val categories = filters.categories.ifEmpty { filters.category?.let(::setOf) ?: emptySet() }
    val muscles = filters.muscles.ifEmpty { filters.muscle?.let(::setOf) ?: emptySet() }
    val equipment = filters.equipmentSelections.ifEmpty { filters.equipment?.let(::setOf) ?: emptySet() }
    val capabilities = filters.capabilities.ifEmpty { filters.capability?.let(::setOf) ?: emptySet() }
    fun matchesAny(selected: Set<String>, values: Collection<String>) = selected.isEmpty() || selected.any { wanted ->
        values.any { normalize(listOf(it)) == normalize(listOf(wanted)) }
    }
    return exercises.asSequence().filter { exercise ->
        val governed = catalogueById[exercise.id]
        val sectionMatch = when (filters.section) {
            LibrarySection.ALL -> governed?.active != false
            LibrarySection.FAVOURITES -> exercise.id in favourites && governed?.active != false
            LibrarySection.RECENT -> exercise.id in recentOrder
            LibrarySection.CUSTOM -> exercise.isCustom
        }
        val sourceMatch = when (filters.source) {
            ExerciseSource.ALL -> true
            ExerciseSource.GOVERNED -> governed != null && !exercise.isCustom
            ExerciseSource.CUSTOM -> exercise.isCustom
        }
        sectionMatch && sourceMatch && exerciseMatches(exercise, filters.query, governed) &&
            matchesAny(categories, listOf(exercise.category)) &&
            matchesAny(muscles, governed?.let { it.primaryMuscles + it.secondaryMuscles } ?: listOf(exercise.category)) &&
            matchesAny(equipment, governed?.equipment ?: emptyList()) &&
            (capabilities.isEmpty() || governed?.capabilities?.any { it in capabilities } == true)
    }.sortedWith(when {
        filters.section == LibrarySection.RECENT || filters.sort == ExerciseSort.RECENT -> compareBy { recentOrder[it.id] ?: Int.MAX_VALUE }
        filters.sort == ExerciseSort.CATEGORY -> compareBy<Exercise> { normalize(listOf(it.category)) }.thenBy { normalize(listOf(it.name)) }.thenBy { it.id }
        else -> compareBy<Exercise> { normalize(listOf(it.name)) }.thenBy { it.id }
    }).distinctBy { it.id }.toList()
}

data class ExerciseDetails(
    val source: String,
    val category: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: List<String>,
    val measurements: List<String>,
    val measurementLabels: List<String>,
    val laterality: String,
    val replacementId: String?,
    val movementPattern: String,
    val setup: String?,
    val steps: List<String>,
    val breathing: String?,
    val cues: List<String>,
    val mistakes: List<String>,
    val safety: String?,
    val regressionId: String?,
    val progressionId: String?,
    val intelligence: ExerciseIntelligence
)

fun exerciseDetails(exercise: Exercise, governed: CatalogueExercise?): ExerciseDetails = ExerciseDetails(
    source = if (exercise.isCustom) "Custom exercise" else "Human V1 governed library",
    category = exercise.category,
    primaryMuscles = governed?.primaryMuscles ?: listOf(exercise.category.lowercase()),
    secondaryMuscles = governed?.secondaryMuscles ?: emptyList(),
    equipment = governed?.equipment ?: emptyList(),
    measurements = (governed?.capabilities ?: setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.LOAD, MeasurementCapability.RPE)).map { it.wireName }.sorted(),
    measurementLabels = governed?.let { item ->
        ExerciseMetricProfileResolver.resolve(item).let { profile ->
            (profile.recording + profile.derived).map { CanonicalMetricDictionary.require(it).label }.sorted()
        }
    } ?: listOf("Load", "Repetitions", "RPE"),
    laterality = governed?.laterality ?: "user defined",
    replacementId = governed?.replacementId,
    movementPattern = governed?.movementPattern?.ifBlank { "Not specified" } ?: "User defined",
    setup = governed?.setup?.ifBlank { null },
    steps = governed?.steps ?: emptyList(),
    breathing = governed?.breathing?.ifBlank { null },
    cues = governed?.cues ?: emptyList(),
    mistakes = governed?.mistakes ?: emptyList(),
    safety = governed?.safety?.ifBlank { null },
    regressionId = governed?.regressionId,
    progressionId = governed?.progressionId,
    intelligence = governed?.intelligence ?: ExerciseIntelligence()
)

fun relatedExercises(current: CatalogueExercise, all: List<CatalogueExercise>, limit: Int = 4): List<CatalogueExercise> {
    val byId = all.filter { it.active }.associateBy { it.id }
    val explicit = (listOfNotNull(current.regressionId, current.progressionId, current.replacementId) + current.relatedIds)
        .distinct().mapNotNull(byId::get).filter { it.id != current.id }
    val inferred = all.asSequence().filter { it.active && it.id != current.id && it.id !in explicit.map { item -> item.id } }
        .map { candidate ->
            val score = (if (candidate.movementPattern == current.movementPattern) 4 else 0) +
                2 * candidate.primaryMuscles.intersect(current.primaryMuscles.toSet()).size +
                candidate.equipment.intersect(current.equipment.toSet()).size
            candidate to score
        }.filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<CatalogueExercise, Int>> { it.second }
            .thenBy { normalize(listOf(it.first.name)) }.thenBy { it.first.id }).map { it.first }
    return (explicit.asSequence() + inferred).distinctBy { it.id }.take(limit).toList()
}
