package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ActiveExerciseMetadata(
    val restSeconds: Int? = null,
    val notes: String? = null,
    val supersetGroupId: String? = null
)

sealed interface WorkoutRecoveryState {
    data object Checking : WorkoutRecoveryState
    data object None : WorkoutRecoveryState
    data class Available(
        val workoutName: String,
        val startedAt: Long,
        val completedSets: Int,
        val totalSets: Int
    ) : WorkoutRecoveryState
    data class Failed(val reason: String) : WorkoutRecoveryState
}

data class ActiveWorkoutState(
    val templateId: Int? = null,
    val templateName: String,
    val startTime: Long,
    val exercises: List<Exercise> = emptyList(),
    val sets: Map<String, List<ActiveSet>> = emptyMap(),
    val exerciseMetadata: Map<String, ActiveExerciseMetadata> = emptyMap(),
    val activeSessionId: String = java.util.UUID.randomUUID().toString(),
    val currentExerciseId: String? = null,
    val workoutNotes: String = "",
    val restTimerEndTimestamp: Long? = null,
    val restTimerDuration: Int? = null,
    val isRestTimerPaused: Boolean = false,
    val isMetric: Boolean = true,
    val stateVersion: Int = 1
)

data class ActiveSet(
    val id: Int = 0,
    val setNumber: Int,
    val reps: Int = 10,
    val weight: Float = 0f,
    val isCompleted: Boolean = false,
    val prevSummary: String = "",
    val rpe: Int? = null,
    val actualDuration: Int? = null,
    val actualDistance: Float? = null,
    val setType: String = "WORKING",
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetWeight: Float? = null,
    val targetRpe: Int? = null,
    val targetDuration: Int? = null,
    val targetDistance: Float? = null,
    val tempo: String? = null,
    val notes: String? = null
)

sealed interface ActiveWorkoutEvent {
    data class WorkoutCompleted(val workoutId: Long) : ActiveWorkoutEvent
    data class ShowError(val message: String) : ActiveWorkoutEvent
}

data class StreakStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val monthlyConsistencyPct: Float,
    val yearlyConsistencyPct: Float,
    val workoutDates: Set<String>
)

data class EnrichedSession(
    val session: WorkoutSession,
    val sets: List<LoggedSet>,
    val totalVolume: Float,
    val durationMinutes: Long,
    val exerciseNames: List<String>
)

class StrengthViewModel(
    private val repository: StrengthRepository,
    private val context: android.content.Context
) : ViewModel() {

    // Screen-level representations for template editing
    data class TemplateExerciseState(
        val id: Int = 0,
        val exerciseId: String,
        val restSeconds: Int = 90,
        val notes: String? = null,
        val supersetGroupId: String? = null,
        val sets: List<TemplateSetState> = emptyList()
    )

    data class TemplateSetState(
        val id: Int = 0,
        val setType: String = "WORKING",
        val targetRepsMin: Int? = 8,
        val targetRepsMax: Int? = 10,
        val targetWeight: Float? = null,
        val targetRpe: Int? = null,
        val targetDurationSeconds: Int? = null,
        val targetDistance: Float? = null,
        val tempo: String? = null,
        val notes: String? = null
    )

    // Shared UI state elements
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    // Sub-ViewModels (focused single-responsibility state holders)
    val authViewModel = AuthViewModel(repository, context)
    val profileViewModel = ProfileViewModel(repository, context, authViewModel)
    val routineViewModel = RoutineViewModel(repository, context, authViewModel)
    val activeWorkoutViewModel = ActiveWorkoutViewModel(repository, context, authViewModel)
    val historyViewModel = HistoryViewModel(repository, context, authViewModel, routineViewModel)

    // Backing undo system properties (held in the orchestrating ViewModel)
    private var lastDeletedSet: Pair<String, Pair<Int, ActiveSet>>? = null
    private var lastDeletedExercise: Pair<Int, Pair<Exercise, List<ActiveSet>>>? = null
    private var lastDeletedTemplate: Pair<WorkoutTemplate, List<TemplateExerciseState>>? = null
    private var lastDeletedSession: Pair<WorkoutSession, List<LoggedSet>>? = null
    private var lastDeletedMeasurement: TapeMeasurement? = null
    private var lastDeletedBodyWeight: BodyWeight? = null

    fun showUndoSnackbar(message: String, onUndo: () -> Unit) {
        viewModelScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                onUndo()
            }
        }
    }

    // Undo Actions
    fun undoRemoveSet() {
        val backup = lastDeletedSet ?: return
        lastDeletedSet = null
        activeWorkoutViewModel.restoreRemovedSet(backup.first, backup.second.first, backup.second.second)
    }

    fun undoRemoveExercise() {
        val backup = lastDeletedExercise ?: return
        lastDeletedExercise = null
        activeWorkoutViewModel.restoreRemovedExercise(backup.first, backup.second)
    }

    fun undoDeleteTemplate() {
        val backup = lastDeletedTemplate ?: return
        lastDeletedTemplate = null
        viewModelScope.launch {
            val oldTemplate = backup.first
            val details = backup.second
            val newTemplateId = repository.insertTemplate(
                WorkoutTemplate(
                    name = oldTemplate.name,
                    exerciseIdsJson = oldTemplate.exerciseIdsJson,
                    userId = oldTemplate.userId
                )
            ).toInt()
            
            for (te in details) {
                val newTeId = repository.insertTemplateExercise(
                    WorkoutTemplateExercise(
                        templateId = newTemplateId,
                        exerciseId = te.exerciseId,
                        position = details.indexOf(te),
                        restSeconds = te.restSeconds,
                        notes = te.notes,
                        supersetGroupId = te.supersetGroupId
                    )
                ).toInt()
                
                val setsToInsert = te.sets.mapIndexed { idx, ts ->
                    WorkoutTemplateSet(
                        templateExerciseId = newTeId,
                        position = idx + 1,
                        setType = ts.setType,
                        targetRepsMin = ts.targetRepsMin,
                        targetRepsMax = ts.targetRepsMax,
                        targetWeight = ts.targetWeight,
                        targetRpe = ts.targetRpe,
                        targetDurationSeconds = ts.targetDurationSeconds,
                        targetDistance = ts.targetDistance,
                        tempo = ts.tempo,
                        notes = ts.notes
                    )
                }
                repository.insertTemplateSets(setsToInsert)
            }
        }
    }

    fun undoDeleteSession() {
        val backup = lastDeletedSession ?: return
        lastDeletedSession = null
        viewModelScope.launch {
            val session = backup.first
            val sets = backup.second
            val newSessionId = repository.insertSession(
                WorkoutSession(
                    templateId = session.templateId,
                    templateName = session.templateName,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    userId = session.userId
                )
            ).toInt()
            
            val setsToInsert = sets.map { s ->
                s.copy(id = 0, sessionId = newSessionId)
            }
            repository.insertLoggedSets(setsToInsert)
        }
    }

    fun undoDeleteTapeMeasurement() {
        val backup = lastDeletedMeasurement ?: return
        lastDeletedMeasurement = null
        viewModelScope.launch {
            repository.insertTapeMeasurement(backup.copy(id = 0))
        }
    }

    fun undoDeleteBodyWeight() {
        val backup = lastDeletedBodyWeight ?: return
        lastDeletedBodyWeight = null
        viewModelScope.launch {
            repository.insertBodyWeight(backup.copy(id = 0))
        }
    }

    // Delegated Authentication properties & functions
    val authRepository get() = authViewModel.authRepository
    val authState = authViewModel.authState
    val activeUserId = authViewModel.activeUserId

    // Delegated User Profile & Settings properties & functions
    val activeUserProfile = profileViewModel.activeUserProfile
    val isMetric = profileViewModel.isMetric
    val theme = profileViewModel.theme
    val keepScreenAwake = profileViewModel.keepScreenAwake
    val defaultRestTimerDuration = profileViewModel.defaultRestTimerDuration
    val soundOn = profileViewModel.soundOn
    val vibrationOn = profileViewModel.vibrationOn
    val defaultWarmupSets = profileViewModel.defaultWarmupSets
    val autoCompleteBehavior = profileViewModel.autoCompleteBehavior
    val autoScroll = profileViewModel.autoScroll
    val timerPreferences = profileViewModel.timerPreferences
    val bodyWeights = profileViewModel.bodyWeights
    val tapeMeasurements = profileViewModel.tapeMeasurements

    fun setMetric(value: Boolean) = profileViewModel.setMetric(value)
    fun setTheme(value: String) = profileViewModel.setTheme(value)
    fun setKeepScreenAwake(value: Boolean) = profileViewModel.setKeepScreenAwake(value)
    fun setDefaultRestTimerDuration(value: Int) = profileViewModel.setDefaultRestTimerDuration(value)
    fun setSoundOn(value: Boolean) = profileViewModel.setSoundOn(value)
    fun setVibrationOn(value: Boolean) = profileViewModel.setVibrationOn(value)
    fun setDefaultWarmupSets(value: Int) = profileViewModel.setDefaultWarmupSets(value)
    fun setAutoCompleteBehavior(value: Boolean) = profileViewModel.setAutoCompleteBehavior(value)
    fun setAutoScroll(value: Boolean) = profileViewModel.setAutoScroll(value)
    fun setTimerPreferences(value: String) = profileViewModel.setTimerPreferences(value)

    fun updateUserProfileBio(dob: String, sex: String, experience: String) =
        profileViewModel.updateUserProfileBio(dob, sex, experience)

    fun logBodyWeight(weight: Float, bodyFat: Float?, timestamp: Long = System.currentTimeMillis()) {
        profileViewModel.logBodyWeight(weight, bodyFat, timestamp) {
            lastDeletedBodyWeight = it
        }
    }

    fun deleteBodyWeight(id: Int) {
        profileViewModel.deleteBodyWeight(id) {
            lastDeletedBodyWeight = it
            showUndoSnackbar("Body weight deleted") {
                undoDeleteBodyWeight()
            }
        }
    }

    fun logTapeMeasurement(
        chest: Float?, waist: Float?, hips: Float?,
        bicepLeft: Float?, bicepRight: Float?,
        thighLeft: Float?, thighRight: Float?,
        timestamp: Long = System.currentTimeMillis()
    ) {
        profileViewModel.logTapeMeasurement(
            chest, waist, hips, bicepLeft, bicepRight, thighLeft, thighRight, timestamp
        ) {
            lastDeletedMeasurement = it
        }
    }

    fun deleteTapeMeasurement(id: Int) {
        profileViewModel.deleteTapeMeasurement(id) {
            lastDeletedMeasurement = it
            showUndoSnackbar("Tape measurement deleted") {
                undoDeleteTapeMeasurement()
            }
        }
    }

    // Delegated Routine Template properties & functions
    val exercises = routineViewModel.exercises
    val templates = routineViewModel.templates
    val favoriteExercises = routineViewModel.favoriteExercises

    fun toggleFavoriteExercise(exerciseId: String) = routineViewModel.toggleFavoriteExercise(exerciseId)
    fun createTemplate(name: String, exerciseIds: List<String>) = routineViewModel.createTemplate(name, exerciseIds)
    fun updateTemplate(id: Int, name: String, exerciseIds: List<String>) = routineViewModel.updateTemplate(id, name, exerciseIds)
    fun duplicateTemplate(template: WorkoutTemplate) = routineViewModel.duplicateTemplate(template)
    
    fun deleteTemplate(id: Int) {
        routineViewModel.deleteTemplate(id) { t, details ->
            lastDeletedTemplate = Pair(t, details)
            showUndoSnackbar("Template '${t.name}' deleted") {
                undoDeleteTemplate()
            }
        }
    }

    suspend fun getTemplateDetails(templateId: Int) = routineViewModel.getTemplateDetails(templateId)
    fun saveTemplate(templateId: Int?, name: String, exercises: List<TemplateExerciseState>, onComplete: () -> Unit = {}) =
        routineViewModel.saveTemplate(templateId, name, exercises, onComplete)

    fun createCustomExercise(name: String, category: String) = routineViewModel.createCustomExercise(name, category)
    fun deleteCustomExercise(id: String) = routineViewModel.deleteCustomExercise(id)

    // Delegated History properties & functions
    val sessions = historyViewModel.sessions
    val allLoggedSets = historyViewModel.allLoggedSets
    val historySearchQuery = historyViewModel.historySearchQuery
    val historySelectedSort = historyViewModel.historySelectedSort
    val historyDateRange = historyViewModel.historyDateRange
    val historySelectedExerciseId = historyViewModel.historySelectedExerciseId
    val historySelectedRoutineName = historyViewModel.historySelectedRoutineName
    val filteredSessions = historyViewModel.filteredSessions
    val streakStats = historyViewModel.streakStats

    fun getCompletedSetsForExercise(exerciseId: String) = historyViewModel.getCompletedSetsForExercise(exerciseId)
    fun getSetsForSession(sessionId: Int) = historyViewModel.getSetsForSession(sessionId)
    fun getEnrichedSession(sessionId: Int) = historyViewModel.getEnrichedSession(sessionId)
    
    fun deleteSession(sessionId: Int) {
        historyViewModel.deleteSession(sessionId) { s, sets ->
            lastDeletedSession = Pair(s, sets)
            showUndoSnackbar("Session deleted") {
                undoDeleteSession()
            }
        }
    }

    // Delegated Active Workout properties & functions
    val activeWorkoutState = activeWorkoutViewModel.activeWorkoutState
    val executionQueue = activeWorkoutViewModel.executionQueue
    val workoutRecoveryState = activeWorkoutViewModel.workoutRecoveryState
    val isCompletingWorkout = activeWorkoutViewModel.isCompletingWorkout
    val navigateToActiveWorkoutEvent = activeWorkoutViewModel.navigateToActiveWorkoutEvent
    val activeWorkoutEvents = activeWorkoutViewModel.activeWorkoutEvents
    val restTimeRemaining = activeWorkoutViewModel.restTimeRemaining
    val restTimerDuration = activeWorkoutViewModel.restTimerDuration
    val isRestTimerPaused = activeWorkoutViewModel.isRestTimerPaused

    fun resumeWorkout() = activeWorkoutViewModel.resumeWorkout()
    fun discardWorkoutBackup() = activeWorkoutViewModel.discardWorkoutBackup()

    fun startWorkout(template: WorkoutTemplate?) = activeWorkoutViewModel.startWorkout(template)
    fun renameActiveWorkout(newName: String) = activeWorkoutViewModel.renameActiveWorkout(newName)
    fun addExerciseToActiveWorkout(exercise: Exercise) = activeWorkoutViewModel.addExerciseToActiveWorkout(exercise)
    
    fun removeExerciseFromActiveWorkout(exerciseId: String) {
        activeWorkoutViewModel.removeExerciseFromActiveWorkout(exerciseId) { index, backup ->
            lastDeletedExercise = Pair(index, backup)
            showUndoSnackbar("Exercise '${backup.first.name}' removed") {
                undoRemoveExercise()
            }
        }
    }

    fun addSetToExercise(exerciseId: String) = activeWorkoutViewModel.addSetToExercise(exerciseId)
    
    fun removeSetFromExercise(exerciseId: String, setIndex: Int) {
        activeWorkoutViewModel.removeSetFromExercise(exerciseId, setIndex) { exId, backup ->
            lastDeletedSet = Pair(exId, backup)
            showUndoSnackbar("Set ${setIndex + 1} removed") {
                undoRemoveSet()
            }
        }
    }

    fun updateSet(
        exerciseId: String,
        setIndex: Int,
        reps: Int,
        weight: Float,
        isCompleted: Boolean,
        rpe: Int? = null,
        actualDuration: Int? = null,
        actualDistance: Float? = null,
        setType: String = "WORKING",
        targetRepsMin: Int? = null,
        targetRepsMax: Int? = null,
        targetWeight: Float? = null,
        targetRpe: Int? = null,
        targetDuration: Int? = null,
        targetDistance: Float? = null,
        tempo: String? = null,
        notes: String? = null
    ) = activeWorkoutViewModel.updateSet(
        exerciseId, setIndex, reps, weight, isCompleted, rpe, actualDuration, actualDistance, setType,
        targetRepsMin, targetRepsMax, targetWeight, targetRpe, targetDuration, targetDistance, tempo, notes
    )

    fun finishActiveWorkout() = activeWorkoutViewModel.finishActiveWorkout()
    fun cancelActiveWorkout() = activeWorkoutViewModel.cancelActiveWorkout()

    // Delegated Rest Timer properties & functions
    fun startRestTimer(duration: Int) = activeWorkoutViewModel.startRestTimer(duration)
    fun pauseRestTimer() = activeWorkoutViewModel.pauseRestTimer()
    fun resumeRestTimer() = activeWorkoutViewModel.resumeRestTimer()
    fun skipRestTimer() = activeWorkoutViewModel.skipRestTimer()
    fun resetRestTimer() = activeWorkoutViewModel.resetRestTimer()
    fun startRestGuide(seconds: Int) = activeWorkoutViewModel.startRestGuide(seconds)
    fun addRestTime(seconds: Int) = activeWorkoutViewModel.addRestTime(seconds)
    fun reduceRestTime(seconds: Int) = activeWorkoutViewModel.reduceRestTime(seconds)
    fun skipRestGuide() = activeWorkoutViewModel.skipRestGuide()
    fun clearRestGuide() = activeWorkoutViewModel.clearRestGuide()

    // Delegated Backup & Restore properties & functions
    suspend fun exportData() = profileViewModel.exportData()
    suspend fun exportDataToCsv() = profileViewModel.exportDataToCsv()
    fun importData(jsonStr: String, onSuccess: (ProfileViewModel.ImportResult) -> Unit, onError: (String) -> Unit) =
        profileViewModel.importData(jsonStr, onSuccess, onError)

    // Serialization Helpers
    fun serializeExerciseIds(ids: List<String>): String {
        return serializeExerciseIdsList(ids)
    }

    fun deserializeExerciseIds(json: String): List<String> {
        return deserializeExerciseIdsList(json)
    }
}

class StrengthViewModelFactory(
    private val repository: StrengthRepository,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StrengthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StrengthViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
