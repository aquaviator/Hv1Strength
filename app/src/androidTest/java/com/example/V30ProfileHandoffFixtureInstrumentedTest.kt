package com.example

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V30ProfileHandoffFixtureInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database by lazy {
        StrengthDatabase.getDatabase(context, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
    }
    private val syntheticOwner = "human_fixtureoffline000000000000000000"

    @Test fun reportAggregates() = runBlocking { report("INITIAL") }

    @Test fun establishEmptyPlaceholder() = runBlocking {
        establishBase()
        report("EMPTY_PLACEHOLDER")
    }

    @Test fun establishMeaningfulConflict() = runBlocking {
        establishBase()
        database.strengthDao().insertTemplate(WorkoutTemplate(
            name = "Fixture Local Routine", exerciseIdsJson = "[]", userId = "offline",
            globalId = "fixture-local-routine", humanUserId = syntheticOwner,
            syncStatus = "LOCAL_ONLY", originDeviceId = "fixture-device"
        ))
        report("MEANINGFUL")
    }

    @Test fun establishAmbiguousConflict() = runBlocking {
        establishBase()
        database.strengthDao().insertUserProfile(UserProfile(
            id = "fixture-second-profile", displayName = "Fixture Profile", authProvider = "offline",
            isOfflineUser = true, humanUserId = "human_fixturesecond0000000000000000000"
        ))
        report("AMBIGUOUS")
    }

    private suspend fun establishBase() {
        val governedExercises = database.strengthDao().getAllExercisesSync()
            .filter { !it.isCustom && it.humanUserId == "global" }
        database.withTransaction {
            database.clearAllTables()
            database.strengthDao().insertExercises(governedExercises)
            database.strengthDao().insertUserProfile(UserProfile(
                id = "offline", displayName = "Offline User", authProvider = "offline",
                isOfflineUser = true, humanUserId = syntheticOwner
            ))
        }
        runCatching { FirebaseAuth.getInstance().signOut() }
        context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private suspend fun report(label: String) {
        val db = database.openHelper.readableDatabase
        fun count(table: String) = db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        val offline = database.strengthDao().getUserProfile("offline")
        val meaningful = offline?.let {
            database.strengthDao().countMeaningfulOwnedRecords(it.id, it.humanUserId)
        } ?: 0
        android.util.Log.i(
            "V30Fixture",
            "state=$label profiles=${count("user_profile")} routines=${count("workout_template")} " +
                "exercises=${count("exercise")} sessions=${count("workout_session")} " +
                "planner=${count("training_plan") + count("planned_workout")} " +
                "active_workout=${count("active_workout_backup") > 0} commands=${count("command_queue")} meaningful=$meaningful"
        )
    }
}
