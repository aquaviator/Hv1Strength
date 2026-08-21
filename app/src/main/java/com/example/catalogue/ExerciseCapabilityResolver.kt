package com.example.catalogue

import android.content.Context
import com.example.data.Exercise
import com.example.measurement.ExerciseMetricProfile
import com.example.measurement.ExerciseMetricProfileResolver

enum class CustomTrackingProfile(val label: String, val capabilities: Set<MeasurementCapability>) {
    REPS("Reps", setOf(MeasurementCapability.REPETITIONS)),
    REPS_LOAD("Reps + weight", setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.LOAD)),
    BODYWEIGHT("Bodyweight", setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.BODYWEIGHT)),
    WEIGHTED_BODYWEIGHT("Weighted bodyweight", setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.BODYWEIGHT, MeasurementCapability.WEIGHTED_BODYWEIGHT)),
    ASSISTED_BODYWEIGHT("Assisted bodyweight", setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.BODYWEIGHT, MeasurementCapability.ASSISTED_LOAD)),
    DURATION("Duration", setOf(MeasurementCapability.DURATION)),
    DISTANCE("Distance", setOf(MeasurementCapability.DISTANCE)),
    DURATION_DISTANCE("Duration + distance", setOf(MeasurementCapability.DURATION, MeasurementCapability.DISTANCE))
}

data class LegacyMeasurements(
    val reps: Int? = null, val load: Float? = null, val durationSeconds: Int? = null,
    val distance: Float? = null, val rpe: Int? = null, val tempo: String? = null
)

data class ResolvedExerciseCapabilities(val values: Set<MeasurementCapability>, val inferred: Boolean = false,
    val metricProfile: ExerciseMetricProfile? = null) {
    private val recording get() = metricProfile?.recording.orEmpty()
    val repetitions get() = "repetitions" in recording || metricProfile == null && MeasurementCapability.REPETITIONS in values
    val load get() = "external_load" in recording || "assistance" in recording || metricProfile == null && (MeasurementCapability.LOAD in values || weightedBodyweight || assistedBodyweight)
    val duration get() = "duration" in recording || metricProfile == null && MeasurementCapability.DURATION in values
    val distance get() = "distance" in recording || metricProfile == null && MeasurementCapability.DISTANCE in values
    val rpe get() = "rpe" in recording || metricProfile == null && MeasurementCapability.RPE in values
    val tempo get() = "tempo" in recording || metricProfile == null && MeasurementCapability.TEMPO in values
    val bodyweight get() = MeasurementCapability.BODYWEIGHT in values
    val weightedBodyweight get() = MeasurementCapability.WEIGHTED_BODYWEIGHT in values
    val assistedBodyweight get() = MeasurementCapability.ASSISTED_LOAD in values
    val weightLabel get() = when { assistedBodyweight -> "Assistance"; weightedBodyweight -> "Added weight"; else -> "Weight" }
}

object ExerciseCapabilityResolver {
    private const val PREFS = "custom_exercise_capabilities"

    fun persistCustom(context: Context, exerciseId: String, profile: CustomTrackingProfile) {
        require(exerciseId.startsWith("custom_"))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(exerciseId, profile.name).apply()
    }

    fun resolve(context: Context?, exercise: Exercise, legacy: LegacyMeasurements = LegacyMeasurements()): ResolvedExerciseCapabilities {
        // Active workouts can render before asynchronous catalogue reconciliation starts. Load the
        // packaged governed catalogue here so seeded legacy set values never define the exercise UI.
        val catalogue = (context?.let(ExerciseCatalogueRuntime::current) ?: ExerciseCatalogueRuntime.snapshot)
            ?.exercises?.firstOrNull { it.id == exercise.id || it.id == exercise.globalId }
        if (catalogue != null) return ResolvedExerciseCapabilities(catalogue.capabilities, metricProfile = ExerciseMetricProfileResolver.resolve(catalogue))
        if (exercise.isCustom && context != null) {
            val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(exercise.id, null)
            CustomTrackingProfile.entries.firstOrNull { it.name == stored }?.let { return ResolvedExerciseCapabilities(it.capabilities) }
        }
        val inferred = buildSet {
            if (legacy.durationSeconds != null) add(MeasurementCapability.DURATION)
            if (legacy.distance != null) add(MeasurementCapability.DISTANCE)
            if (legacy.reps != null || isEmpty()) add(MeasurementCapability.REPETITIONS)
            if (legacy.load != null) add(MeasurementCapability.LOAD)
            if (legacy.rpe != null) add(MeasurementCapability.RPE)
            if (!legacy.tempo.isNullOrBlank()) add(MeasurementCapability.TEMPO)
        }
        return ResolvedExerciseCapabilities(inferred, true)
    }

    fun validate(capabilities: ResolvedExerciseCapabilities, values: LegacyMeasurements): List<String> = buildList {
        if (capabilities.repetitions && values.reps != null && values.reps < 0) add("Repetitions cannot be negative")
        if (capabilities.load && values.load != null && values.load < 0f) add("Weight cannot be negative")
        if (capabilities.duration && values.durationSeconds != null && values.durationSeconds <= 0) add("Duration must be greater than zero")
        if (capabilities.distance && values.distance != null && values.distance < 0f) add("Distance cannot be negative")
        if (capabilities.rpe && values.rpe != null && values.rpe !in 1..10) add("RPE must be between 1 and 10")
        if (capabilities.tempo && !values.tempo.isNullOrBlank() && !values.tempo.matches(Regex("[0-9Xx]{3,4}"))) add("Tempo must contain 3 or 4 digits/X characters")
    }
}
