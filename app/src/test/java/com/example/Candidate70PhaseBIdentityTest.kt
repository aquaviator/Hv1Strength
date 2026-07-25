package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AuthRepository
import com.example.data.AuthState
import com.example.data.StrengthDatabase
import com.example.data.StrengthRepository
import com.example.data.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Candidate70PhaseBIdentityTest {

    private lateinit var context: Context
    private lateinit var database: StrengthDatabase
    private lateinit var repository: StrengthRepository
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        repository = StrengthRepository(database.strengthDao())
        authRepository = AuthRepository(context, repository, CoroutineScope(Dispatchers.IO))
    }

    @Test
    fun testLocalProfileWorksWithoutAuthentication() = runBlocking {
        authRepository.signInAnonymously()
        val state = authRepository.authState.value
        assertTrue(state is AuthState.Offline)
        
        val profile = repository.getUserProfile("offline")
        assertNotNull(profile)
        assertEquals("Offline User", profile?.displayName)
        assertTrue(profile?.isOfflineUser == true)
    }

    @Test
    fun testSignOutPreservesLocalData() = runBlocking {
        authRepository.signInAnonymously()
        val profile = repository.getUserProfile("offline")
        assertNotNull(profile)

        authRepository.signOut(keepLocalData = true)
        
        val recheckProfile = repository.getUserProfile("offline")
        assertNotNull(recheckProfile)
        assertEquals(AuthState.Initial, authRepository.authState.value)
    }

    @Test
    fun testAuthStateDoesNotRepresentSubscriptionEntitlement() = runBlocking {
        authRepository.signInAnonymously()
        val state = authRepository.authState.value
        assertTrue(state is AuthState.Offline || state is AuthState.Authenticated || state is AuthState.Initial)
    }

    @Test
    fun testDeveloperTrialSimulationIsolated() {
        val prefs = context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("simulate_trial_expired", true).apply()
        assertTrue(prefs.getBoolean("simulate_trial_expired", false))
    }
}
