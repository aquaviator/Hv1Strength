package com.example.catalogue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.Exercise
import com.example.data.StrengthDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** DUMP-protected, fixed synthetic V36 fixture. It never contacts Firebase. */
class V36CatalogueAcceptanceController : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_APPLY, ACTION_REPORT)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { if (intent.action == ACTION_APPLY) apply(context) else report(context) }
            catch (error: Throwable) { Log.e(TAG, "result=FAILED_SAFE reason=${error.javaClass.simpleName}") }
            finally { pending.finish() }
        }
    }

    private suspend fun apply(context: Context) {
        val database = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)); val dao = database.strengthDao()
        val bundled = PackagedExerciseLibrarySource.load(context).snapshot
        val rich = bundled.exercises.first { it.id == "calf_raise" }.copy(
            intelligence = ExerciseIntelligence(
                purpose = "Controlled plantar-flexor strength training.", stabilizers = listOf("intrinsic foot muscles"),
                jointActions = listOf("ankle plantar flexion"), movementPlane = "sagittal", kineticChain = "closed chain",
                rangeOfMotionNotes = "Use a comfortable controlled range.", forceVector = "vertical",
                biomechanicalRationale = "The ankle plantar flexors produce the primary lifting action.",
                programmingGuidance = listOf("Use controlled repetitions and progress load gradually."),
                typicalUseCases = listOf("Calf strength and hypertrophy"), cautions = listOf("Avoid painful range."),
                evidence = listOf(EvidenceClaim("Progressive loading can improve plantar-flexor strength.",
                    "Synthetic V36 acceptance citation", "https://doi.org/10.1000/v36-acceptance", "2026-08-17",
                    "acceptance-editor", "editorially reviewed", "Synthetic acceptance citation only",
                    "10.1000/v36-acceptance", "12345678"))))
        val noEvidence = bundled.exercises.first { it.id == "wall_slide" }.copy(
            intelligence = ExerciseIntelligence(purpose = "Isometric lower-body training.", evidence = emptyList()))
        val accepted = bundled.exercises.map { when (it.id) { rich.id -> rich; noEvidence.id -> noEvidence; else -> it } }
        val before = dao.getAllExercisesSync(); require(before.filterNot { it.isCustom }.map { it.id }.toSet().containsAll(bundled.exercises.map { it.id }))
        dao.insertExercises(accepted.map { it.toRoom(System.currentTimeMillis()) })
        if (dao.getExerciseById(CUSTOM_ID) == null) dao.insertExercise(Exercise(CUSTOM_ID, "V36 Acceptance Custom", "Other", true))
        ExerciseCatalogueRuntime.accept(bundled.copy(exercises = accepted))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("beforeGoverned", before.count { !it.isCustom }).putInt("beforeCustom", before.count { it.isCustom }).apply()
        report(context)
    }

    private suspend fun report(context: Context) {
        val dao = StrengthDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).strengthDao(); val exercises = dao.getAllExercisesSync()
        val routines = dao.getAllTemplates().first().size; val sessions = dao.getAllSessions().first(); val sets = dao.getAllLoggedSets().first()
        val commands = dao.getAllCommands(); val pending = commands.count { it.status in setOf("PENDING", "FAILED") }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Log.i(TAG, "result=OK beforeGoverned=${prefs.getInt("beforeGoverned",-1)} beforeCustom=${prefs.getInt("beforeCustom",-1)} governed=${exercises.count { !it.isCustom }} custom=${exercises.count { it.isCustom }} routines=$routines sessions=${sessions.size} sets=${sets.size} pendingFailedCommands=$pending stableIds=${ExerciseCatalogueRuntime.snapshot?.exercises?.size ?: 0}")
    }

    companion object {
        const val ACTION_APPLY = "com.example.debug.V36_APPLY_CATALOGUE_FIXTURE"
        const val ACTION_REPORT = "com.example.debug.V36_REPORT_CATALOGUE_FIXTURE"
        private const val CUSTOM_ID = "v36_acceptance_custom"
        private const val PREFS = "v36_catalogue_acceptance"
        private const val TAG = "V36CatalogueAcceptance"
    }
}
