package com.example.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudioPlanReconciliationMigrationTest {
    @Test fun `version 20 plan links migrate in place as healing required`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "studio-plan-reconciliation-migration-${System.nanoTime()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val v20 = factory.create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE studio_plan_link (planVersionId TEXT NOT NULL PRIMARY KEY, humanUserId TEXT NOT NULL, planGlobalId TEXT NOT NULL, sourceRevision INTEGER NOT NULL, planChecksum TEXT NOT NULL, workoutVersionIdsJson TEXT NOT NULL, sourcePayloadJson TEXT NOT NULL, appliedAt INTEGER NOT NULL, acknowledgementId TEXT NOT NULL, acknowledgementState TEXT NOT NULL, conflictState TEXT, isLatest INTEGER NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        v20.writableDatabase.execSQL("INSERT INTO studio_plan_link VALUES ('plan-r3','human_owner','plan',3,'checksum','[]','{}',100,'ack','APPLIED',NULL,1)")
        v20.close()

        val v21 = factory.create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    assertEquals(20, oldVersion)
                    assertEquals(21, newVersion)
                    StrengthDatabase.MIGRATION_20_21.migrate(db)
                }
            }).build())
        v21.writableDatabase.query("SELECT acknowledgementState, planReconciliationVersion FROM studio_plan_link WHERE planVersionId='plan-r3'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("APPLIED", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        v21.close()
        context.deleteDatabase(name)
    }
}
