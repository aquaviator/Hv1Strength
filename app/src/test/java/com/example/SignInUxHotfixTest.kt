package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AuthErrorKind
import com.example.data.Exercise
import com.example.data.StrengthDatabase
import com.example.data.WorkoutTemplate
import com.example.ui.presentation.signInDialogCopy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignInUxHotfixTest {
    private lateinit var database: StrengthDatabase

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), StrengthDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun close() { database.close() }

    @Test fun governedExercisesDoNotCountAsMeaningfulUserData() = runBlocking {
        database.strengthDao().insertExercise(Exercise(
            id = "bench_press", name = "Bench Press", category = "Chest", isCustom = false,
            globalId = "bench_press", humanUserId = "human_offlineusr"
        ))
        assertEquals(0, database.strengthDao().countMeaningfulOwnedRecords("offline", "human_offlineusr"))
    }

    @Test fun untouchedDefaultRoutineDoesNotCountButEditedRoutineDoes() = runBlocking {
        val untouched = WorkoutTemplate(
            name = "Legs & Abs", exerciseIdsJson = "[]", userId = null,
            globalId = "template_legs", humanUserId = "human_offlineusr", revision = 1, syncStatus = "LOCAL_ONLY"
        )
        val id = database.strengthDao().insertTemplate(untouched).toInt()
        assertEquals(0, database.strengthDao().countMeaningfulOwnedRecords("offline", "human_offlineusr"))
        database.strengthDao().insertTemplate(untouched.copy(id = id, revision = 2))
        assertEquals(1, database.strengthDao().countMeaningfulOwnedRecords("offline", "human_offlineusr"))
    }

    @Test fun genuineConflictUsesHumanV1WordingAndSafeActions() {
        val copy = signInDialogCopy(AuthErrorKind.DATA_CONFLICT, "ignored")
        assertEquals("Some saved items need your attention", copy.title)
        assertTrue(copy.message.contains("Human V1 online data"))
        assertTrue(copy.message.contains("Nothing has been overwritten"))
        assertEquals(listOf("Review differences", "Use offline for now", "Sign out"), copy.actions)
    }

    @Test fun differentAccountUsesOfflineExportAndSignOutWithoutRetry() {
        val copy = signInDialogCopy(AuthErrorKind.DIFFERENT_ACCOUNT, "ignored")
        assertEquals(listOf("Use this data offline", "Export the data", "Sign out"), copy.actions)
        assertFalse(copy.actions.any { it.contains("retry", true) || it.contains("update", true) })
    }

    @Test fun networkFailureIsNotPresentedAsADataConflict() {
        val copy = signInDialogCopy(AuthErrorKind.NETWORK, "ignored")
        assertEquals("We couldn’t finish signing in", copy.title)
        assertEquals(listOf("Try again", "Use offline", "Sign out"), copy.actions)
        assertFalse(copy.message.contains("different versions", true))
    }

    @Test fun normalUserCopyNeverClaimsGoogleProfileMutation() {
        val all = AuthErrorKind.entries.joinToString(" ") { kind ->
            val copy = signInDialogCopy(kind, "Sign-in could not finish")
            copy.title + " " + copy.message + " " + copy.actions.joinToString()
        }
        assertFalse(all.contains("Google profile", true))
        assertFalse(all.contains("migration", true))
        assertFalse(all.contains("database", true))
        assertFalse(all.contains("command queue", true))
    }
}
