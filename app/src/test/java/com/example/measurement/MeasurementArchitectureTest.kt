package com.example.measurement

import com.example.catalogue.CatalogueExercise
import com.example.catalogue.ExerciseIntelligence
import com.example.catalogue.MeasurementCapability
import org.junit.Assert.*
import org.junit.Test

class MeasurementArchitectureTest {
    private fun exercise(id: String, capabilities: Set<MeasurementCapability>) = CatalogueExercise(
        id, id.replace('_',' '), emptyList(), "Cardio", emptyList(), emptyList(), listOf("treadmill"),
        "cardio", capabilities, "bilateral", false, true, intelligence = ExerciseIntelligence()
    )

    @Test fun canonicalUnitsRoundTripWithoutMixingDimensions() {
        val pounds = CanonicalUnitRegistry.require("pound")
        assertEquals(45.359237, pounds.toCanonical(100.0), .000001)
        assertEquals(100.0, pounds.fromCanonical(45.359237), .000001)
        assertTrue(CanonicalUnitRegistry.require("level").manufacturerSpecific)
        assertNotEquals(CanonicalMetricDictionary.require("energy").semantic, CanonicalMetricDictionary.require("mechanical_work").semantic)
        assertNotEquals(CanonicalMetricDictionary.require("external_load").semantic, CanonicalMetricDictionary.require("assistance").semantic)
    }

    @Test fun treadmillNeverShowsOrAcceptsStrengthFields() {
        val profile = ExerciseMetricProfileResolver.resolve(exercise("treadmill_run", setOf(
            MeasurementCapability.DURATION, MeasurementCapability.DISTANCE, MeasurementCapability.PACE, MeasurementCapability.RPE)))
        assertEquals(setOf("duration", "distance"), profile.primary)
        assertTrue("pace" in profile.derived)
        assertTrue("external_load" in profile.unsupported)
        assertTrue("repetitions" in profile.unsupported)
        assertTrue(MetricValidation.validate(profile, MetricInput("external_load", 50.0, unitKey="kilogram")).isNotEmpty())
    }

    @Test fun assistedLoadIsNotOrdinaryResistance() {
        val profile = ExerciseMetricProfileResolver.resolve(exercise("assisted_pull_up", setOf(
            MeasurementCapability.REPETITIONS, MeasurementCapability.BODYWEIGHT, MeasurementCapability.ASSISTED_LOAD, MeasurementCapability.RPE)))
        assertTrue("assistance" in profile.primary)
        assertFalse("external_load" in profile.recording)
    }

    @Test fun manufacturerLevelsRemainLosslessAndUnconverted() {
        val level = CanonicalUnitRegistry.require("level")
        assertEquals(17.0, level.toCanonical(17.0), 0.0)
        assertEquals("manufacturer_scale", level.dimension)
    }

    @Test fun requiredDeviceJourneyFamiliesResolveWithoutIrrelevantStrengthFields() {
        val cardio = listOf("treadmill_run", "stair_climber", "stationary_bike", "air_bike", "rowing_machine", "ski_erg", "elliptical")
        cardio.forEach { id ->
            val profile=ExerciseMetricProfileResolver.resolve(exercise(id,setOf(MeasurementCapability.DURATION,MeasurementCapability.DISTANCE,MeasurementCapability.RPE)))
            assertTrue(id, "duration" in profile.recording)
            assertTrue(id, "distance" in profile.recording)
            if (id == "treadmill_run") assertFalse(id, "repetitions" in profile.recording)
            assertFalse(id, "external_load" in profile.recording)
        }
        val carry=ExerciseMetricProfileResolver.resolve(exercise("farmers_carry",setOf(MeasurementCapability.DURATION,MeasurementCapability.DISTANCE,MeasurementCapability.LOAD,MeasurementCapability.RPE)))
        assertTrue(setOf("duration","distance","external_load").all(carry.recording::contains))
        val hold=ExerciseMetricProfileResolver.resolve(exercise("plank",setOf(MeasurementCapability.DURATION,MeasurementCapability.RPE)))
        assertEquals(setOf("duration"),hold.primary)
        val countAndTime=ExerciseMetricProfileResolver.resolve(exercise("jump_rope",setOf(MeasurementCapability.REPETITIONS,MeasurementCapability.DURATION,MeasurementCapability.RPE)))
        assertTrue(setOf("repetitions","duration").all(countAndTime.recording::contains))
    }

    @Test fun legacyRowingCardioUsesConservativeRowerProfile() {
        val profile = ExerciseMetricProfileResolver.resolve(exercise("rowing_machine_cardio", setOf(
            MeasurementCapability.LOAD, MeasurementCapability.REPETITIONS, MeasurementCapability.RPE)))
        assertEquals(setOf("duration", "distance"), profile.primary)
        assertTrue("power" in profile.optional)
        assertFalse("external_load" in profile.recording)
        assertFalse("repetitions" in profile.recording)
    }
}
