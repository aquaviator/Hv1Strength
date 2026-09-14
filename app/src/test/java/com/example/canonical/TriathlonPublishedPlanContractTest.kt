package com.example.canonical

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TriathlonPublishedPlanContractTest {
    private fun dataset(): String {
        val start = File(requireNotNull(System.getProperty("user.dir")))
        val file = generateSequence(start) { it.parentFile }
            .map { it.resolve("humanv1-workout-studio/src/fixtures/research/triathlon-research-dataset.json") }
            .firstOrNull { it.isFile } ?: File("missing-triathlon-research-dataset.json")
        assertTrue("Studio research fixture must exist", file.isFile)
        return file.readText()
    }

    @Test fun consumesAllPublishedSchedule12FeaturesWithoutBundlingALibrary() {
        val root = dataset(); assertTrue(root.contains("\"planScheduleSchemaVersion\": \"1.2\"")); assertEquals(4, Regex("\"planId\": \"(?:first|intermediate)-(?:half-)?ironman-\\d+-week\"").findAll(root).count())
        val assignmentIds = Regex("\"assignmentId\": \"([^\"]+)\"").findAll(root).map { it.groupValues[1] }.toList()
        assertEquals(305, assignmentIds.size); assertEquals(305, assignmentIds.toSet().size); assertTrue(root.contains("\"orderWithinDay\": 2")); assertTrue(root.contains("\"assignmentType\": \"RACE\"")); assertTrue(root.contains("\"variableDuration\": true"))
        assertEquals(305, assignmentIds.mapIndexed { index, id -> CanonicalContract.occurrenceId("research-plan-v1", id, 21000L + index) }.toSet().size)
    }

    @Test fun exactAcknowledgementAndReplayRemainIdempotentAndHistorySafe() {
        val checksum = requireNotNull(Regex("\"datasetSha256\": \"([0-9a-f]{64})\"").find(dataset())).groupValues[1]
        assertEquals("a5ae71a890ed90a3dd3f87d3bc0e22c6a8ea52c049e58b1cd30faf5658ebdd74", checksum)
        val acknowledgement = "APPLIED:research-plan-v1:$checksum"
        assertEquals(acknowledgement, "APPLIED:research-plan-v1:$checksum")
        val history = listOf(Occurrence("COMPLETED", "v1"), Occurrence("SKIPPED", "v1"), Occurrence("PLANNED", "v1", true)); assertEquals(history, replaceFutureOnly(history, "v2"))
        assertEquals(setOf(acknowledgement), listOf(acknowledgement, acknowledgement).toSet())
    }
}
