package com.example.planner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.PlannedWorkout
import com.example.data.StrengthDatabase
import com.example.data.TrainingPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class PlannerPersistenceTest {
    private lateinit var db: StrengthDatabase
    private val day = LocalDate.of(2026, 4, 6).toEpochDay()
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), StrengthDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    private fun plan(user: String = "a") = TrainingPlan("series-$user", user, "human_${user.repeat(32)}", 1, "tg", "Routine", day)
    private fun item(user: String = "a", date: Long = day) = PlannedWorkout("series-$user:$date", "series-$user", user, "human_${user.repeat(32)}", 1, "tg", "Routine", date, day)

    @Test fun profileIsolationAndAccountSwitchDoNotRedirectOwnership() = runBlocking {
        db.strengthDao().upsertTrainingPlan(plan("a")); db.strengthDao().insertPlannedWorkouts(listOf(item("a")))
        db.strengthDao().upsertTrainingPlan(plan("b")); db.strengthDao().insertPlannedWorkouts(listOf(item("b")))
        assertEquals(listOf("a"), db.strengthDao().getPlannedWorkoutsForUser("a").first().map { it.userId })
        assertEquals(listOf("b"), db.strengthDao().getPlannedWorkoutsForUser("b").first().map { it.userId })
    }
    @Test fun duplicateReconciliationIsIdempotent() = runBlocking {
        val row = item(); assertEquals(listOf(1L), db.strengthDao().insertPlannedWorkouts(listOf(row)))
        assertEquals(listOf(-1L), db.strengthDao().insertPlannedWorkouts(listOf(row)))
        assertEquals(1, db.strengthDao().getPlannedWorkoutsForUser("a").first().size)
    }
    @Test fun editOneOccurrenceDoesNotRewriteSiblings() = runBlocking {
        val rows = listOf(item(date=day), item(date=day+7)); db.strengthDao().insertPlannedWorkouts(rows)
        db.strengthDao().upsertPlannedWorkout(rows.first().copy(scheduledEpochDay=day+1, detachedFromSeries=true))
        val result = db.strengthDao().getPlannedWorkoutsForUser("a").first()
        assertTrue(result.any { it.id == rows[1].id && it.scheduledEpochDay == day+7 })
        assertTrue(result.any { it.id == rows[0].id && it.originalEpochDay == day && it.detachedFromSeries })
    }
    @Test fun deletingFuturePreservesCompletedHistory() = runBlocking {
        val completed = item(date=day).copy(status="COMPLETED", completedAt=1L)
        db.strengthDao().insertPlannedWorkouts(listOf(completed, item(date=day+7), item(date=day+14)))
        db.strengthDao().deleteFuturePlannedWorkouts("series-a", "a", day)
        assertEquals(listOf(completed.id), db.strengthDao().getPlannedWorkoutsForUser("a").first().map { it.id })
    }
    @Test fun completionLinkIsIdempotentlyRetained() = runBlocking {
        db.strengthDao().insertPlannedWorkouts(listOf(item()))
        db.strengthDao().updatePlannedWorkoutStatus(item().id, "COMPLETED", 123L, 42, 123L)
        val result = db.strengthDao().getPlannedWorkout(item().id)!!
        assertEquals("COMPLETED", result.status); assertEquals(42, result.linkedSessionId); assertEquals(day, result.originalEpochDay)
    }
}
