package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.Exercise
import com.example.data.StrengthDatabase
import com.example.data.catalogue.CatalogueImportException
import com.example.data.catalogue.CatalogueImportOutcome
import com.example.data.catalogue.PilotCatalogueImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PilotCatalogueImporterTest {
    private lateinit var context: Context
    private lateinit var database: StrengthDatabase
    private lateinit var importer: PilotCatalogueImporter
    private lateinit var fixture: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = PilotCatalogueImporter(database)
        fixture = PilotCatalogueImporter.loadFixture(context)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun fixtureParsesAndVerifiesAllEightCanonicalIds() {
        val parsed = importer.parseAndVerify(fixture)
        assertEquals(1, parsed.runtimeContractVersion)
        assertEquals("pilot_staging", parsed.channel)
        assertEquals(8, parsed.exercises.size)
        assertEquals(
            setOf(
                "bench_press", "plank", "ex_push_up", "ex_goblet_squat",
                "ex_seated_cable_row", "ex_selectorized_chest_press",
                "ex_bulgarian_split_squat", "ex_farmers_carry"
            ),
            parsed.exercises.map { it.canonicalId }.toSet()
        )
    }

    @Test
    fun unsupportedContractWrongChannelAndRecordCountAreRejectedBeforeMutation() = runBlocking {
        val unsupported = changedFixture("runtime_contract_version", 2)
        assertThrows(CatalogueImportException.UnsupportedRuntimeContractVersion::class.java) {
            runBlocking { importer.import(unsupported) }
        }
        val wrongChannel = changedFixture("channel", "production")
        assertThrows(CatalogueImportException.WrongChannel::class.java) {
            runBlocking { importer.import(wrongChannel) }
        }
        val wrongCount = changedFixture("record_count", 7)
        assertThrows(CatalogueImportException.RecordCountMismatch::class.java) {
            runBlocking { importer.import(wrongCount) }
        }
        assertTrue(database.catalogueStagingDao().getExercises().isEmpty())
    }

    @Test
    fun tamperedFixtureIsRejectedBeforeMutation() = runBlocking {
        val tampered = fixture.replace("Barbell Bench Press", "Tampered Bench Press")
        assertThrows(CatalogueImportException.ChecksumMismatch::class.java) {
            runBlocking { importer.import(tampered) }
        }
        assertTrue(database.catalogueStagingDao().getExercises().isEmpty())
    }

    @Test
    fun duplicateAndUnknownRelationshipAreRejected() = runBlocking {
        val duplicateRoot = JSONObject(fixture)
        duplicateRoot.getJSONArray("exercises").getJSONObject(1)
            .put("canonical_id", duplicateRoot.getJSONArray("exercises").getJSONObject(0).getString("canonical_id"))
        val duplicate = resign(duplicateRoot)
        assertThrows(CatalogueImportException.InvalidIdentity::class.java) {
            runBlocking { importer.import(duplicate) }
        }

        val relationshipRoot = JSONObject(fixture)
        relationshipRoot.getJSONArray("exercises").getJSONObject(0)
            .put("relationships", JSONArray().put(
                JSONObject().put("type", "progression").put("target_canonical_id", "missing")
            ))
        val badRelationship = resign(relationshipRoot)
        assertThrows(CatalogueImportException.UnknownRelationship::class.java) {
            runBlocking { importer.import(badRelationship) }
        }
        assertTrue(database.catalogueStagingDao().getExercises().isEmpty())
    }

    @Test
    fun importPersistsEightRecordsMetadataAliasesAndNoInventedRelationships() = runBlocking {
        assertEquals(CatalogueImportOutcome.IMPORTED, importer.import(fixture))
        val dao = database.catalogueStagingDao()
        assertEquals(8, dao.getExercises().size)
        assertEquals(8, dao.getRelease("pilot_staging")?.recordCount)
        assertEquals(
            "4855f1385f96b8b30a4116d3252639522d4a97746717cd20afefb0b757ebc005",
            dao.getRelease("pilot_staging")?.checksum
        )
        assertTrue(dao.getAliases().any {
            it.value == "Bench Press" && it.canonicalId == "bench_press" && it.type == "search"
        })
        assertTrue(dao.getRelationships().isEmpty())
    }

    @Test
    fun identicalImportIsIdempotent() = runBlocking {
        assertEquals(CatalogueImportOutcome.IMPORTED, importer.import(fixture))
        assertEquals(CatalogueImportOutcome.UNCHANGED, importer.import(fixture))
        assertEquals(8, database.catalogueStagingDao().getExercises().size)
    }

    @Test
    fun laterReleaseUpdatesContentUnderStableCanonicalIdentity() = runBlocking {
        importer.import(fixture)
        val root = JSONObject(fixture)
        root.put("catalogue_version", "pilot-1.1-test")
        val exercises = root.getJSONArray("exercises")
        for (index in 0 until exercises.length()) {
            val exercise = exercises.getJSONObject(index)
            if (exercise.getString("canonical_id") == "bench_press") {
                exercise.put("display_name", "Barbell Bench Press Updated")
            }
        }
        assertEquals(CatalogueImportOutcome.IMPORTED, importer.import(resign(root)))
        val stored = database.catalogueStagingDao().getExercises()
        assertEquals(8, stored.size)
        assertEquals("Barbell Bench Press Updated", stored.single { it.canonicalId == "bench_press" }.displayName)
    }

    @Test
    fun failedReplacementLeavesPriorReleaseIntact() = runBlocking {
        importer.import(fixture)
        val tampered = fixture.replace("Barbell Bench Press", "Untrusted")
        assertThrows(CatalogueImportException.ChecksumMismatch::class.java) {
            runBlocking { importer.import(tampered) }
        }
        assertEquals(8, database.catalogueStagingDao().getExercises().size)
        assertEquals("pilot-1.0", database.catalogueStagingDao().getRelease("pilot_staging")?.catalogueVersion)
    }

    @Test
    fun stagingSearchProvesNameAliasAndKeywordLookup() = runBlocking {
        importer.import(fixture)
        val dao = database.catalogueStagingDao()
        assertEquals("bench_press", dao.searchExact("barbell bench press").single().canonicalId)
        assertEquals("bench_press", dao.searchExact("bench press").single().canonicalId)
        assertEquals("ex_goblet_squat", dao.searchExact("front loaded squat").single().canonicalId)
    }

    @Test
    fun productionAndCustomExercisesRemainIsolated() = runBlocking {
        val production = Exercise(id = "seed_test", name = "Seed Test", category = "Chest")
        val custom = Exercise(id = "custom_test", name = "Barbell Bench Press", category = "Chest", isCustom = true)
        database.strengthDao().insertExercise(production)
        database.strengthDao().insertExercise(custom)
        importer.import(fixture)

        val visible = database.strengthDao().getAllExercises().first()
        assertEquals(setOf("seed_test", "custom_test"), visible.map { it.id }.toSet())
        assertTrue(visible.single { it.id == "custom_test" }.isCustom)
        assertEquals(8, database.catalogueStagingDao().getExercises().size)
    }

    @Test
    fun migrationNineToTenPreservesExistingAndCustomExerciseRows() {
        val name = "catalogue-migration-test.db"
        context.deleteDatabase(name)
        val old = helper(name, 9, createDatabase = { db ->
            db.execSQL("CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, isCustom INTEGER NOT NULL)")
            db.execSQL("INSERT INTO exercise VALUES ('seed_test', 'Seed', 'Chest', 0)")
            db.execSQL("INSERT INTO exercise VALUES ('custom_test', 'Custom', 'Chest', 1)")
        })
        old.writableDatabase
        old.close()

        val upgraded = helper(name, 10, upgradeDatabase = { db ->
            StrengthDatabase.MIGRATION_9_10.migrate(db)
        })
        val db = upgraded.writableDatabase
        db.query("SELECT COUNT(*) FROM exercise").use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='catalogue_staging_exercise'").use {
            assertTrue(it.moveToFirst())
        }
        upgraded.close()
        context.deleteDatabase(name)
    }

    private fun changedFixture(key: String, value: Any): String {
        val root = JSONObject(fixture)
        root.put(key, value)
        return resign(root)
    }

    private fun resign(root: JSONObject): String {
        root.remove("checksum")
        root.put("checksum", sha256(canonicalJson(root)))
        return root.toString()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted()
            .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                "${JSONObject.quote(key)}:${canonicalJson(value.get(key))}"
            }
        is JSONArray -> (0 until value.length()).joinToString(
            prefix = "[", postfix = "]", separator = ","
        ) { canonicalJson(value.get(it)) }
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> throw IllegalArgumentException("Unsupported JSON type")
    }

    private fun helper(
        name: String,
        version: Int,
        createDatabase: (SupportSQLiteDatabase) -> Unit = {},
        upgradeDatabase: (SupportSQLiteDatabase) -> Unit = {}
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = createDatabase(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = upgradeDatabase(db)
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(callback)
                .build()
        )
    }
}
