package com.example.measurement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V37MeasurementPersistenceInstrumentedTest {
    @Test fun migration13To14IsAdditiveAndRetainsHistoricalRows() { runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "synthetic-v37-migration.db"
        context.deleteDatabase(name)
        val created = Room.databaseBuilder(context, StrengthDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            created.strengthDao().insertExercise(Exercise("custom_migration", "Migration custom", "Other", true, humanUserId="synthetic-owner"))
        } finally { created.close() }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { raw ->
            raw.execSQL("DROP TABLE metric_sample")
            raw.execSQL("DROP TABLE metric_segment")
            raw.execSQL("DROP TABLE metric_observation")
            raw.execSQL("DROP TABLE metric_prescription")
            raw.version = 13
        }
        val migrated = Room.databaseBuilder(context, StrengthDatabase::class.java, name).addMigrations(StrengthDatabase.MIGRATION_13_14)
            .allowMainThreadQueries().build()
        try {
            assertTrue(migrated.strengthDao().getExerciseById("custom_migration")!!.isCustom)
            migrated.strengthDao().upsertMetricObservations(listOf(MetricObservationEntity("migration-observation", "set", "duration", 30.0, canonicalUnit="second")))
            assertEquals(30.0, migrated.strengthDao().getMetricObservations("set").single().numericValue!!, 0.0)
        } finally { migrated.close() }
        context.deleteDatabase(name)
    } }

    @Test fun prescriptionsObservationsSegmentsAndSamplesRemainRelationalAndPreserveExistingRows() { runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = database.strengthDao()
            dao.insertExercise(Exercise("custom_device_test", "Custom Device Test", "Other", true, humanUserId="synthetic-owner"))
            dao.upsertMetricPrescriptions(listOf(MetricPrescriptionEntity("prescription-1", "template-set-1", "distance", targetValue=5000.0, canonicalUnit="metre")))
            dao.upsertMetricObservations(listOf(MetricObservationEntity("observation-1", "logged-set-1", "manufacturer_resistance",
                numericValue=17.0, canonicalUnit="level", originalValue=17.0, originalUnit="level", source="MACHINE",
                manufacturer="Synthetic", deviceModel="Acceptance")))
            dao.upsertMetricSegments(listOf(MetricSegmentEntity("segment-1", "observation-1", 0, 0, 60_000, 17.0, "level", "work")))
            dao.upsertMetricSamples(listOf(MetricSampleEntity("sample-1", "observation-1", 5_000, 17.0, "level")))

            assertEquals(5000.0, dao.getMetricPrescriptions("template-set-1").single().targetValue!!, 0.0)
            assertEquals(17.0, dao.getMetricObservations("logged-set-1").single().originalValue!!, 0.0)
            assertEquals("work", dao.getMetricSegments("observation-1").single().label)
            assertEquals(5_000, dao.getMetricSamples("observation-1").single().offsetMillis)
            assertTrue(dao.getExerciseById("custom_device_test")!!.isCustom)
        } finally { database.close() }
    } }
}
