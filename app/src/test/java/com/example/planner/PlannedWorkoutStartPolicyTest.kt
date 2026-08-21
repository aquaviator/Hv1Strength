package com.example.ui.viewmodel

import com.example.data.PlannedWorkout
import com.example.data.WorkoutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlannedWorkoutStartPolicyTest {
    @Test
    fun resolvesSyncedRoutineByStableGlobalIdBeforeDeviceLocalId() {
        val wrongLocalMatch = template(id = 7, globalId = "other")
        val stableMatch = template(id = 42, globalId = "routine-global")

        val resolved = PlannedWorkoutStartPolicy.resolveTemplate(
            occurrence(templateId = 7, templateGlobalId = "routine-global"),
            listOf(wrongLocalMatch, stableMatch)
        )

        assertEquals(stableMatch, resolved)
    }

    @Test
    fun fallsBackToLocalIdForLegacyOccurrenceWithoutStableMatch() {
        val localMatch = template(id = 7, globalId = "routine-global")
        assertEquals(
            localMatch,
            PlannedWorkoutStartPolicy.resolveTemplate(
                occurrence(templateId = 7, templateGlobalId = "legacy-missing"),
                listOf(localMatch)
            )
        )
    }

    @Test
    fun unavailableRoutineDoesNotResolve() {
        assertNull(
            PlannedWorkoutStartPolicy.resolveTemplate(
                occurrence(templateId = 7, templateGlobalId = "missing"),
                emptyList()
            )
        )
    }

    private fun template(id: Int, globalId: String) = WorkoutTemplate(
        id = id,
        name = "Routine",
        exerciseIdsJson = "[]",
        userId = "profile",
        globalId = globalId,
        humanUserId = "human"
    )

    private fun occurrence(templateId: Int, templateGlobalId: String) = PlannedWorkout(
        id = "occurrence",
        seriesId = "series",
        userId = "profile",
        humanUserId = "human",
        templateId = templateId,
        templateGlobalId = templateGlobalId,
        routineName = "Routine",
        scheduledEpochDay = 1L,
        originalEpochDay = 1L
    )
}
