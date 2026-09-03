package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountReconciliationPlannerTest {
    private val owner = "human_1234567890abcdef1234567890abcdef"

    @Test fun `synthetic full graph plans one stable backfill and reports orphans conflicts and tombstones`() {
        val candidates = listOf(
            ReconciliationCandidate("CUSTOM_EXERCISE", "exercise_legacy123", owner, 1, "PENDING_UPLOAD"),
            ReconciliationCandidate("WORKOUT_TEMPLATE", "template_local123", owner, 2, "SYNCED"),
            ReconciliationCandidate("WORKOUT_TEMPLATE_EXERCISE", "placement_local1", owner, 2, "SYNCED", parentGlobalId = "template_local123"),
            ReconciliationCandidate("WORKOUT_TEMPLATE_SET", "set_local123456", owner, 2, "SYNCED", parentGlobalId = "placement_local1"),
            ReconciliationCandidate("TRAINING_PLAN", "plan_conflict123", owner, 3, "CONFLICT", deletedAt = 99),
            ReconciliationCandidate("PLANNED_WORKOUT", "occurrence_done1", owner, 3, "COMPLETED", parentGlobalId = "plan_conflict123"),
            ReconciliationCandidate("WORKOUT_TEMPLATE_EXERCISE", "orphan_child123", owner, 1, "PENDING_UPLOAD", parentGlobalId = "missing_parent")
        )
        val first = AccountReconciliationPlanner.plan(owner, candidates, emptyList(), setOf("template_local123", "placement_local1", "plan_conflict123"), 100, "device_test")
        assertEquals(1, first.commands.size)
        assertEquals("exercise_legacy123", first.commands.single().entityGlobalId)
        assertEquals(1, first.classifications["missing parent"])
        assertEquals(1, first.classifications["conflicting equal/divergent revision"])
        assertEquals(1, first.classifications["archived/tombstoned"])
        val second = AccountReconciliationPlanner.plan(owner, candidates, first.commands, setOf("template_local123", "placement_local1", "plan_conflict123"), 200, "device_test")
        assertTrue(second.commands.isEmpty())
        assertEquals(first.receiptHash, AccountReconciliationPlanner.plan(owner, candidates, emptyList(), setOf("template_local123", "placement_local1", "plan_conflict123"), 999, "other_device").receiptHash)
    }

    @Test fun `command id is deterministic and ownership mismatch fails closed`() {
        assertEquals(AccountReconciliationPlanner.deterministicCommandId(owner, "CUSTOM_EXERCISE", "exercise_6cc2f8f0d0a5", 1), AccountReconciliationPlanner.deterministicCommandId(owner, "CUSTOM_EXERCISE", "exercise_6cc2f8f0d0a5", 1))
        val foreign = ReconciliationCandidate("CUSTOM_EXERCISE", "exercise_foreign1", "human_abcdefabcdefabcdefabcdefabcdefab", 1, "PENDING_UPLOAD")
        runCatching { AccountReconciliationPlanner.plan(owner, listOf(foreign), emptyList(), emptySet(), 1, "device") }.onSuccess { error("ownership mismatch accepted") }
    }
}
