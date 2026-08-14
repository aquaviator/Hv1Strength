package com.example.catalogue

import com.example.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GovernedCatalogueApplyPlanTest {
    private fun item(id: String, name: String = id, active: Boolean = true) = CatalogueExercise(
        id, name, emptyList(), "Other", emptyList(), emptyList(), emptyList(), "strength",
        setOf(MeasurementCapability.REPETITIONS), "bilateral", false, active
    )

    @Test fun matchingReleaseIsIdempotent() {
        val old = item("stable", "Stable").toRoom(10).copy(revision = 4)
        val plan = planGovernedCatalogueApply(listOf(item("stable", "Stable")), listOf(old), 20)
        assertEquals(old, plan.upserts.single())
    }

    @Test fun addedExerciseBecomesAnUpsert() {
        assertEquals("added", planGovernedCatalogueApply(listOf(item("added")), emptyList(), 20).upserts.single().id)
    }

    @Test fun stableIdMetadataUpdatePreservesCreationAndAdvancesRevision() {
        val old = item("stable", "Old").toRoom(10).copy(revision = 7)
        val updated = planGovernedCatalogueApply(listOf(item("stable", "New")), listOf(old), 20).upserts.single()
        assertEquals("stable", updated.id); assertEquals(10, updated.createdAt); assertEquals(8, updated.revision)
    }

    @Test fun deprecatedExerciseIsRetainedButNotActiveForDiscovery() {
        val deprecated = item("stable", active = false)
        assertFalse(deprecated.active)
        assertEquals("stable", planGovernedCatalogueApply(listOf(deprecated), emptyList(), 20).upserts.single().id)
    }

    @Test fun removedGovernedRowsAreRetainedForHistory() {
        val old = item("historical").toRoom(10)
        val plan = planGovernedCatalogueApply(emptyList(), listOf(old), 20)
        assertTrue("historical" in plan.retainedHistoricalIds)
        assertTrue(plan.upserts.isEmpty())
    }

    @Test fun customExercisesArePreservedAndNeverOverwrittenOnCollision() {
        val custom = Exercise("custom", "Mine", "Other", true)
        val plan = planGovernedCatalogueApply(listOf(item("custom"), item("global")), listOf(custom), 20)
        assertEquals(setOf("custom"), plan.preservedCustomIds)
        assertEquals(setOf("custom"), plan.customCollisions)
        assertEquals(listOf("global"), plan.upserts.map { it.id })
    }
}
