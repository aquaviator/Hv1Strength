package com.example

import com.example.ui.viewmodel.launchAuthentication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationScopeTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun leavingUiCompositionDoesNotCancelViewModelOwnedAuthentication() {
        val dispatcher = StandardTestDispatcher()
        val authenticationScope = TestScope(dispatcher)
        val uiScope = CoroutineScope(dispatcher)
        var firebaseSignInCompleted = false
        var uiWorkCompleted = false

        val uiJob = uiScope.launch {
            delay(1)
            uiWorkCompleted = true
        }

        val authenticationJob = launchAuthentication(authenticationScope) {
            delay(1)
            firebaseSignInCompleted = true
        }

        uiScope.cancel()
        authenticationScope.advanceUntilIdle()

        assertTrue(firebaseSignInCompleted)
        assertFalse(uiWorkCompleted)
        assertTrue(uiJob.isCancelled)
        assertFalse(authenticationJob.isCancelled)
        assertTrue(authenticationJob.isCompleted)
    }
}
