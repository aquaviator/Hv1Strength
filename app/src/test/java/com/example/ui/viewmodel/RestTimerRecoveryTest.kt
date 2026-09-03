package com.example.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerRecoveryTest {
    private val now = 1_000_000L

    @Test fun `restores paused sixty second rest after process recreation`() {
        val result = recoverRestTimer(60, 45, now + 60_000L, true, now)!!
        assertEquals(60, result.durationSeconds)
        assertEquals(45, result.remainingSeconds)
        assertTrue(result.paused)
    }

    @Test fun `restores partially elapsed running rest with ceiling rounding`() {
        val result = recoverRestTimer(60, null, now + 44_001L, false, now)!!
        assertEquals(45, result.remainingSeconds)
        assertFalse(result.paused)
    }

    @Test fun `completed expired and malformed rests do not restore`() {
        assertNull(recoverRestTimer(60, null, now, false, now))
        assertNull(recoverRestTimer(60, null, now - 1, false, now))
        assertNull(recoverRestTimer(-1, -5, -10, true, now))
        assertNull(recoverRestTimer(null, null, null, false, now))
    }

    @Test fun `remaining duration is never negative and repeated paused restoration is idempotent`() {
        assertNull(recoverRestTimer(60, -1, now + 60_000L, true, now))
        val first = recoverRestTimer(60, 45, now + 60_000L, true, now)
        val second = recoverRestTimer(60, 45, now + 60_000L, true, now)
        assertEquals(first, second)
    }
}
