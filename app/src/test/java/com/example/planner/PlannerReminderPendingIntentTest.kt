package com.example.planner

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PlannerReminderPendingIntentTest {
    @Test fun occurrencePendingIntentsAreImmutableAndDistinct() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val first = PlannerReminderWorker.contentIntent(context, "occurrence-a")
        val second = PlannerReminderWorker.contentIntent(context, "occurrence-b")

        assertTrue(first.isImmutable)
        assertTrue(second.isImmutable)
        assertNotEquals(first, second)
    }

    @Test fun cancellingOneIssuedNotificationLeavesTheOtherUntouched() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = context.getSystemService(NotificationManager::class.java)
        val firstId = PlannerReminderScheduler.notificationId("occurrence-a")
        val secondId = PlannerReminderScheduler.notificationId("occurrence-b")
        manager.notify(firstId, Notification())
        manager.notify(secondId, Notification())

        PlannerReminderScheduler.cancelIssuedNotification(context, "occurrence-a")

        val remaining = shadowOf(manager).allNotifications
        assertEquals(1, remaining.size)
        assertNull(shadowOf(manager).getNotification(firstId))
        assertNotNull(shadowOf(manager).getNotification(secondId))
    }
}
