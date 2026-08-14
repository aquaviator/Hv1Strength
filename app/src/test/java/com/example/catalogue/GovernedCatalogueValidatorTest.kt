package com.example.catalogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GovernedCatalogueValidatorTest {
    private val raw = mapOf<String, Any?>(
        "exerciseId" to "bench_press", "schemaVersion" to 1L, "displayName" to "Bench Press"
    )
    private val exercise = CatalogueExercise(
        "bench_press", "Bench Press", emptyList(), "Chest", listOf("chest"), emptyList(), listOf("barbell"),
        "strength", setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.LOAD), "bilateral", false, true
    )

    private fun payload(
        documents: List<Map<String, Any?>> = listOf(raw), exercises: List<CatalogueExercise> = listOf(exercise),
        checksum: String = GovernedCatalogueValidator.checksum(documents), count: Int = exercises.size,
        status: String = "published", minimumVersion: Int = 1
    ) = GovernedCataloguePayload(
        GovernedReleaseManifest("release-1", 1, "2026.08.33", count, checksum, status, "production", minimumVersion, null),
        documents, exercises
    )

    @Test fun acceptsCompletePublishedPayload() {
        val result = GovernedCatalogueValidator.validate(payload())
        assertTrue(result.accepted)
        assertEquals(CatalogueSyncStatus.REMOTE_ACCEPTED, result.status)
    }

    @Test fun rejectsDraftRelease() {
        assertEquals(CatalogueSyncStatus.INVALID_MANIFEST, GovernedCatalogueValidator.validate(payload(status = "draft")).status)
    }

    @Test fun rejectsCountMismatch() {
        assertEquals(CatalogueSyncStatus.COUNT_MISMATCH, GovernedCatalogueValidator.validate(payload(count = 2)).status)
    }

    @Test fun rejectsChecksumMismatchWithoutPartialAcceptance() {
        val result = GovernedCatalogueValidator.validate(payload(checksum = "0".repeat(64)))
        assertFalse(result.accepted)
        assertEquals(CatalogueSyncStatus.CHECKSUM_MISMATCH, result.status)
    }

    @Test fun rejectsBrokenReferences() {
        val broken = exercise.copy(relatedIds = listOf("missing"))
        assertEquals(CatalogueSyncStatus.INVALID_REFERENCE, GovernedCatalogueValidator.validate(payload(exercises = listOf(broken))).status)
    }

    @Test fun rejectsIncompatibleMinimumVersion() {
        assertEquals(CatalogueSyncStatus.VERSION_INCOMPATIBLE, GovernedCatalogueValidator.validate(payload(minimumVersion = Int.MAX_VALUE)).status)
    }
}
