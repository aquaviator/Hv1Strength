package com.example.service.workout

import org.junit.Assert.*
import org.junit.Test

class WorkoutOwnershipTest {
    @Test fun logoutDoesNotDestroyOrRewriteOwnership() {
        val started = WorkoutOwnership("account-a", "human_aaaaaaaaaaaa")
        assertEquals(started, started.remainsOwnedBy("offline"))
    }
    @Test fun accountSwitchCannotRedirectWorkoutOwnership() {
        val started = WorkoutOwnership("account-a", "human_aaaaaaaaaaaa")
        assertEquals("account-a", started.remainsOwnedBy("account-b").userId)
        assertFalse(started.isRedirectedTo("account-b"))
    }
    @Test fun cloudBlockedIdentityDoesNotChangeLocalWorkoutAuthority() {
        val started = WorkoutOwnership("account-a", "human_aaaaaaaaaaaa")
        assertEquals("human_aaaaaaaaaaaa", started.remainsOwnedBy("blocked").humanUserId)
    }
}
