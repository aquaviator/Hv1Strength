package com.example.catalogue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Exercise
import com.example.data.StrengthDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GovernedCatalogueFirestoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var app: FirebaseApp
    private lateinit var database: StrengthDatabase

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val project = "demo-hv1-strength-local"
        check(project.startsWith("demo-") && project != "hv1-platform")
        app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setApplicationId("1:123456789:android:v35-catalogue-acceptance")
            .setApiKey("local-emulator-only-key")
            .setProjectId(project)
            .build(), "v35-catalogue-acceptance")!!
        FirebaseFirestore.getInstance(app).apply {
            useEmulator("10.0.2.2", 8080)
            firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        }
        database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
            .allowMainThreadQueries().build()
        val bundled = PackagedExerciseLibrarySource.load(context).snapshot
        database.strengthDao().insertExercises(bundled.exercises.map { it.toRoom(1) })
        database.strengthDao().insertExercises(listOf(
            Exercise("local_custom_v35", "Local Custom V35", "Other", true, "local-custom-v35")
        ))
    }

    @After fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::app.isInitialized) app.delete()
    }

    @Test fun latestDemoReleaseAppliesAtomicallyAndPreservesLocalData() = runBlocking {
        val bundled = PackagedExerciseLibrarySource.load(context).snapshot
        assertEquals(264, bundled.exercises.size)
        val result = GovernedCatalogueCoordinator(
            database,
            FirebaseGovernedCatalogueGateway(FirebaseFirestore.getInstance(app))
        ).synchronize(bundled)

        assertTrue(result.toString(), result.accepted)
        assertEquals(CatalogueSyncStatus.REMOTE_ACCEPTED, result.status)
        assertEquals(266, database.strengthDao().getAllExercisesSync().size)
        assertNotNull(database.strengthDao().getExerciseById("v35_demo_sled_march"))
        assertNotNull(database.strengthDao().getExerciseById("local_custom_v35"))
        assertFalse(ExerciseCatalogueRuntime.snapshot!!.exercises.single { it.id == "squat" }.active)
        assertNotNull(database.strengthDao().getExerciseById("squat"))
        assertEquals("strength-v35-demo-deprecated-2", database.strengthDao().getCatalogueReleaseState()?.acceptedReleaseId)
        assertTrue(database.strengthDao().getAllCommands().isEmpty())
    }
}
