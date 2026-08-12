package com.example.service.workout

import android.app.PendingIntent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WorkoutNotificationFactoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun state(id: String) = WorkoutExecutionSnapshot(id, "Leg day", 1_000L, "Squat", 2, 5, 11_000L, false)

    @Test fun notificationContainsTruthfulWorkoutRestAndProgress() {
        val notification = WorkoutNotificationFactory(context).build(state("one"), 6_000L)
        assertEquals("Leg day", notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("Rest 00:05", notification.extras.getString(android.app.Notification.EXTRA_TEXT))
        assertEquals("2/5 sets complete", notification.extras.getString(android.app.Notification.EXTRA_SUB_TEXT))
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test fun resumePendingIntentIsImmutableSafeAndTargetsCorrectWorkoutFlow() {
        val first = WorkoutNotificationFactory(context).build(state("one"), 6_000L).contentIntent
        val second = WorkoutNotificationFactory(context).build(state("two"), 6_000L).contentIntent
        assertNotEquals(first, second)
        assertTrue(first.isImmutable)
        assertTrue(shadowOf(first).savedIntent.getBooleanExtra(WorkoutNotificationFactory.EXTRA_OPEN_WORKOUT, false))
        assertEquals(com.example.MainActivity::class.java.name, shadowOf(first).savedIntent.component?.className)
    }
}
