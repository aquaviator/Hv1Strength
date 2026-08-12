package com.example.catalogue

import androidx.test.core.app.ApplicationProvider
import com.example.data.Exercise
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExerciseCapabilityResolverTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun custom(id: String) = Exercise("custom_$id", "Custom $id", "Other", true)

    @Test fun everyCustomProfileMapsToAUsableDistinctPresentation() {
        CustomTrackingProfile.entries.forEach { profile ->
            val exercise = custom(profile.name.lowercase())
            ExerciseCapabilityResolver.persistCustom(context, exercise.id, profile)
            val resolved = ExerciseCapabilityResolver.resolve(context, exercise)
            assertEquals(profile.capabilities, resolved.values)
            assertTrue(resolved.repetitions || resolved.duration || resolved.distance)
        }
    }

    @Test fun weightedAndAssistedBodyweightUseClearWeightMeaning() {
        val weighted = custom("weighted"); val assisted = custom("assisted")
        ExerciseCapabilityResolver.persistCustom(context, weighted.id, CustomTrackingProfile.WEIGHTED_BODYWEIGHT)
        ExerciseCapabilityResolver.persistCustom(context, assisted.id, CustomTrackingProfile.ASSISTED_BODYWEIGHT)
        assertEquals("Added weight", ExerciseCapabilityResolver.resolve(context, weighted).weightLabel)
        assertEquals("Assistance", ExerciseCapabilityResolver.resolve(context, assisted).weightLabel)
    }

    @Test fun legacyInferencePreservesEveryStoredMeasurementDimension() {
        val resolved = ExerciseCapabilityResolver.resolve(null, custom("legacy"), LegacyMeasurements(8, 20f, 60, 1.5f, 8, "3010"))
        assertTrue(resolved.inferred)
        assertTrue(resolved.repetitions && resolved.load && resolved.duration && resolved.distance && resolved.rpe && resolved.tempo)
    }

    @Test fun validationAppliesOnlyToVisibleSupportedMeasurements() {
        val repsLoad = ResolvedExerciseCapabilities(setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.LOAD, MeasurementCapability.RPE, MeasurementCapability.TEMPO))
        val errors = ExerciseCapabilityResolver.validate(repsLoad, LegacyMeasurements(-1, -2f, -5, -1f, 11, "bad"))
        assertEquals(4, errors.size)
        assertFalse(errors.any { it.contains("Duration") || it.contains("Distance") })
    }

    @Test fun governedDurationDistanceAndFallbackResolve() {
        ExerciseCatalogueRuntime.load(context)
        val carry = Exercise("farmers_carry", "Farmer's Carry", "Full Body")
        val resolved = ExerciseCapabilityResolver.resolve(context, carry)
        assertTrue(resolved.duration && resolved.distance && resolved.load)
        val fallback = ExerciseCatalogueRuntime.fallback("test").exercises.single { it.id == "plank" }
        assertTrue(fallback.capabilities.contains(MeasurementCapability.DURATION))
    }
}
