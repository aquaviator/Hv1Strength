package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileHydrationIdempotencyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
    private val repository = StrengthRepository(db.strengthDao(), context, "device_test")
    private val owner = "human_1234567890abcdef1234567890abcdef"
    private val profile = UserProfile(
        id = "uid_test", email = "test@example.com", displayName = "Test User",
        authProvider = "google", globalId = "profile_stable123", humanUserId = owner,
        firebaseUid = "uid_test", revision = 4, syncStatus = "SYNCED", originDeviceId = "device_other"
    )

    @After fun close() = db.close()

    @Test fun `startup sign-in hydration and cloud echo create no command`() = runBlocking {
        db.strengthDao().insertUserProfile(profile)
        repository.hydrateUserProfile(profile.copy(updatedAt = 999, revision = 99))
        repository.hydrateUserProfile(profile.copy(updatedAt = 1, revision = 1))
        assertEquals(0, repository.getAllCommands().size)
        assertEquals(4L, repository.getUserProfile(profile.id)?.revision)
    }

    @Test fun `null default and timestamp normalization create no command`() = runBlocking {
        val defaults = profile.copy(photoUrl = null, dateOfBirth = null, updatedAt = 10)
        db.strengthDao().insertUserProfile(defaults)
        repository.hydrateUserProfile(defaults.copy(updatedAt = 9999, revision = 80))
        assertEquals(0, repository.getAllCommands().size)
    }

    @Test fun `real edit creates one command and repeating it creates no duplicate`() = runBlocking {
        db.strengthDao().insertUserProfile(profile)
        val edited = profile.copy(displayName = "Changed")
        repository.insertUserProfile(edited)
        val saved = requireNotNull(repository.getUserProfile(profile.id))
        repository.insertUserProfile(saved.copy(updatedAt = saved.updatedAt + 5000))
        assertEquals(1, repository.getAllCommands().size)
        assertEquals("SettingsUpdated", repository.getAllCommands().single().commandType)
    }

    @Test fun `second device hydration does not echo and history remains readable`() = runBlocking {
        db.strengthDao().insertUserProfile(profile)
        repository.insertUserProfile(profile.copy(displayName = "Historical edit"))
        val historical = repository.getAllCommands().single()
        repository.hydrateUserProfile(requireNotNull(repository.getUserProfile(profile.id)).copy(originDeviceId = "device_two"))
        assertEquals(listOf(historical), repository.getAllCommands())
    }

    @Test fun `timestamp formatting null and default equivalents create no command`() = runBlocking {
        db.strengthDao().insertUserProfile(profile.copy(email = "Test@Example.com", photoUrl = null))
        repository.insertUserProfile(profile.copy(email = " test@example.com ", photoUrl = "",
            preferredUnits = "METRIC", updatedAt = 999, lastLoginAt = 999))
        assertEquals(0, repository.getAllCommands().size)
    }

    @Test fun `hydration preserves a newer pending local edit`() = runBlocking {
        val pending = profile.copy(displayName = "Local edit", revision = 5, syncStatus = "PENDING_UPLOAD")
        db.strengthDao().insertUserProfile(pending)
        repository.hydrateUserProfile(profile.copy(displayName = "Older server value", revision = 4))
        assertEquals("Local edit", repository.getUserProfile(profile.id)?.displayName)
        assertEquals(5L, repository.getUserProfile(profile.id)?.revision)
        assertEquals(0, repository.getAllCommands().size)
    }

    @Test fun `ownership mismatch fails closed without touching history`() = runBlocking {
        db.strengthDao().insertUserProfile(profile)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.hydrateUserProfile(profile.copy(humanUserId = "human_ffffffffffffffffffffffffffffffff")) }
        }
        assertEquals(profile, repository.getUserProfile(profile.id))
        assertEquals(0, repository.getAllCommands().size)
    }
}
