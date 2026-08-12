package com.example.service.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WorkoutExecutionServiceControllerTest {
    @Test
    fun stop_isDeliveredAsServiceCommand_toAvoidPendingForegroundStartRace() {
        val application = ApplicationProvider.getApplicationContext<Application>()

        WorkoutExecutionServiceController.stop(application)

        val intent = shadowOf(application).nextStartedService
        assertEquals(WorkoutExecutionService::class.java.name, intent.component?.className)
        assertEquals(WorkoutExecutionService.ACTION_STOP, intent.action)
    }
}
