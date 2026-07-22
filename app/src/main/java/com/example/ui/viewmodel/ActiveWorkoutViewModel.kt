package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.VolumeCalculator
import com.example.domain.SetVolumeData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class ActiveWorkoutViewModel(
    private val repository: StrengthRepository,
    private val context: Context,
    private val authViewModel: AuthViewModel
) : ViewModel() {

    private val preferencesRepository = UserPreferencesRepository(repository.dao)
    
    val soundOn = preferencesRepository.userPreferencesFlow
        .map { it.soundOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val vibrationOn = preferencesRepository.userPreferencesFlow
        .map { it.vibrationOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val defaultRestTimerDuration = preferencesRepository.userPreferencesFlow
        .map { it.defaultRestTimerDuration }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 90)

    val isMetric: StateFlow<Boolean> = preferencesRepository.userPreferencesFlow
        .map { it.isMetric }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Active Workout State
    private val _activeWorkoutState = MutableStateFlow<ActiveWorkoutState?>(null)
    val activeWorkoutState: StateFlow<ActiveWorkoutState?> = _activeWorkoutState.asStateFlow()

    val executionQueue: StateFlow<List<WorkoutStep>> = _activeWorkoutState
        .map { state ->
            if (state == null) emptyList()
            else WorkoutExecutionQueue.generateQueue(state.exercises, state.sets, state.exerciseMetadata)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _workoutRecoveryState = MutableStateFlow<WorkoutRecoveryState>(WorkoutRecoveryState.Checking)
    val workoutRecoveryState: StateFlow<WorkoutRecoveryState> = _workoutRecoveryState.asStateFlow()

    private val _isCompletingWorkout = MutableStateFlow(false)
    val isCompletingWorkout = _isCompletingWorkout.asStateFlow()

    private val _navigateToActiveWorkoutEvent = MutableSharedFlow<Unit>(replay = 0)
    val navigateToActiveWorkoutEvent = _navigateToActiveWorkoutEvent.asSharedFlow()

    private val _activeWorkoutEvents = MutableSharedFlow<ActiveWorkoutEvent>()
    val activeWorkoutEvents = _activeWorkoutEvents.asSharedFlow()

    // Rest Timer State
    private val _restTimeRemaining = MutableStateFlow<Int?>(null)
    val restTimeRemaining = _restTimeRemaining.asStateFlow()

    private val _restTimerDuration = MutableStateFlow(90)
    val restTimerDuration = _restTimerDuration.asStateFlow()

    private val _isRestTimerPaused = MutableStateFlow(false)
    val isRestTimerPaused = _isRestTimerPaused.asStateFlow()

    private var restTimerJob: kotlinx.coroutines.Job? = null

    private var lastSavedState: ActiveWorkoutState? = null
    private var backupSaveJob: kotlinx.coroutines.Job? = null
    private val saveMutex = kotlinx.coroutines.sync.Mutex()

    init {
        // 1. Auto-save active workout state changes to Database Backup
        viewModelScope.launch {
            _activeWorkoutState.collect { state ->
                if (state == null) {
                    // Only clear the backup if we are NOT in the Checking or Available recovery states
                    val recState = _workoutRecoveryState.value
                    if (recState !is WorkoutRecoveryState.Checking && recState !is WorkoutRecoveryState.Available) {
                        backupSaveJob?.cancel()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                repository.clearActiveWorkoutBackup()
                            } catch (e: Exception) {
                                android.util.Log.e("ActiveWorkoutVM", "Failed to clear backup on null state", e)
                            }
                        }
                        lastSavedState = null
                    }
                } else {
                    triggerBackupSave(state)
                }
            }
        }

        // 2. Check for active workout backup on startup
        checkForActiveWorkoutBackup()
    }

    private fun isCriticalChange(old: ActiveWorkoutState?, new: ActiveWorkoutState): Boolean {
        if (old == null) return true
        if (old.templateId != new.templateId) return true
        if (old.exercises != new.exercises) return true
        
        val oldSetsList = old.sets.flatMap { it.value }
        val newSetsList = new.sets.flatMap { it.value }
        if (oldSetsList.size != newSetsList.size) return true
        
        // Check if completion states changed
        if (oldSetsList.map { it.isCompleted } != newSetsList.map { it.isCompleted }) return true
        
        return false
    }

    private fun triggerBackupSave(state: ActiveWorkoutState) {
        val isCritical = isCriticalChange(lastSavedState, state)
        backupSaveJob?.cancel()
        if (isCritical) {
            backupSaveJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                performSaveBackup(state)
            }
        } else {
            backupSaveJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.delay(500)
                performSaveBackup(state)
            }
        }
    }

    private suspend fun performSaveBackup(state: ActiveWorkoutState) {
        saveMutex.withLock {
            try {
                val backup = ActiveWorkoutBackup(
                    id = 1,
                    templateId = state.templateId,
                    templateName = state.templateName,
                    startTime = state.startTime,
                    exercisesJson = serializeExercises(state.exercises),
                    setsJson = serializeSets(state.sets),
                    exerciseMetadataJson = serializeMetadata(state.exerciseMetadata, state)
                )
                repository.saveActiveWorkoutBackup(backup)
                lastSavedState = state
            } catch (e: Exception) {
                android.util.Log.e("ActiveWorkoutVM", "Failed to save active workout backup", e)
            }
        }
    }

    fun checkForActiveWorkoutBackup() {
        _workoutRecoveryState.value = WorkoutRecoveryState.Checking
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val backup = repository.getActiveWorkoutBackup()
                if (backup != null) {
                    try {
                        val restoredExercises = deserializeExercises(backup.exercisesJson)
                        val restoredSets = deserializeSets(backup.setsJson)
                        
                        val totalSets = restoredSets.flatMap { it.value }.size
                        val completedSets = restoredSets.flatMap { it.value }.count { it.isCompleted }
                        
                        _workoutRecoveryState.value = WorkoutRecoveryState.Available(
                            workoutName = backup.templateName,
                            startedAt = backup.startTime,
                            completedSets = completedSets,
                            totalSets = totalSets
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("ActiveWorkoutVM", "Malformed active workout backup data", e)
                        _workoutRecoveryState.value = WorkoutRecoveryState.Failed("Malformed backup: ${e.localizedMessage}")
                    }
                } else {
                    _workoutRecoveryState.value = WorkoutRecoveryState.None
                }
            } catch (e: Exception) {
                android.util.Log.e("ActiveWorkoutVM", "Failed to query active workout backup", e)
                _workoutRecoveryState.value = WorkoutRecoveryState.Failed(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun resumeWorkout() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val backup = repository.getActiveWorkoutBackup()
                if (backup != null) {
                    val restoredExercises = deserializeExercises(backup.exercisesJson)
                    val restoredSets = deserializeSets(backup.setsJson)
                    val restoredMetadata = deserializeMetadata(backup.exerciseMetadataJson)
                    
                    val recoveryObj = if (JSONObject(backup.exerciseMetadataJson).has("__global_recovery__")) {
                        JSONObject(backup.exerciseMetadataJson).getJSONObject("__global_recovery__")
                    } else {
                        null
                    }
                    val activeSessionId = recoveryObj?.optString("activeSessionId") ?: java.util.UUID.randomUUID().toString()
                    val currentExerciseId = if (recoveryObj == null || recoveryObj.isNull("currentExerciseId")) null else recoveryObj.getString("currentExerciseId")
                    val workoutNotes = recoveryObj?.optString("workoutNotes") ?: ""
                    val restTimerEndTimestamp = if (recoveryObj == null || recoveryObj.isNull("restTimerEndTimestamp")) null else recoveryObj.getLong("restTimerEndTimestamp")
                    val restTimerDuration = if (recoveryObj == null || recoveryObj.isNull("restTimerDuration")) null else recoveryObj.getInt("restTimerDuration")
                    val isRestTimerPaused = recoveryObj?.optBoolean("isRestTimerPaused", false) ?: false
                    val isMetric = recoveryObj?.optBoolean("isMetric", true) ?: true
                    val stateVersion = recoveryObj?.optInt("stateVersion", 1) ?: 1

                    val state = ActiveWorkoutState(
                        templateId = backup.templateId,
                        templateName = backup.templateName,
                        startTime = backup.startTime,
                        exercises = restoredExercises,
                        sets = restoredSets,
                        exerciseMetadata = restoredMetadata,
                        activeSessionId = activeSessionId,
                        currentExerciseId = currentExerciseId,
                        workoutNotes = workoutNotes,
                        restTimerEndTimestamp = restTimerEndTimestamp,
                        restTimerDuration = restTimerDuration,
                        isRestTimerPaused = isRestTimerPaused,
                        isMetric = isMetric,
                        stateVersion = stateVersion
                    )
                    
                    _activeWorkoutState.value = state
                    lastSavedState = state
                    _workoutRecoveryState.value = WorkoutRecoveryState.None

                    if (restTimerEndTimestamp != null && !isRestTimerPaused) {
                        val remainingMs = restTimerEndTimestamp - System.currentTimeMillis()
                        if (remainingMs > 0) {
                            val remainingSecs = (remainingMs / 1000).toInt()
                            _restTimerDuration.value = restTimerDuration ?: remainingSecs
                            _restTimeRemaining.value = remainingSecs
                            _isRestTimerPaused.value = false
                            runRestTimer(restTimerEndTimestamp)
                        } else {
                            _restTimeRemaining.value = null
                        }
                    } else if (restTimerEndTimestamp != null && isRestTimerPaused) {
                        val remainingSecs = recoveryObj.optInt("restTimerRemainingAtSave", 0)
                        if (remainingSecs > 0) {
                            _restTimerDuration.value = restTimerDuration ?: remainingSecs
                            _restTimeRemaining.value = remainingSecs
                            _isRestTimerPaused.value = true
                        }
                    }

                    _navigateToActiveWorkoutEvent.emit(Unit)
                } else {
                    _workoutRecoveryState.value = WorkoutRecoveryState.None
                }
            } catch (e: Exception) {
                android.util.Log.e("ActiveWorkoutVM", "Failed to resume workout", e)
                _workoutRecoveryState.value = WorkoutRecoveryState.Failed(e.localizedMessage ?: "Failed to restore backup")
            }
        }
    }

    fun discardWorkoutBackup() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.clearActiveWorkoutBackup()
            } catch (e: Exception) {
                android.util.Log.e("ActiveWorkoutVM", "Failed to discard workout backup", e)
            }
            lastSavedState = null
            _activeWorkoutState.value = null
            _workoutRecoveryState.value = WorkoutRecoveryState.None
            skipRestTimer()
        }
    }

    override fun onCleared() {
        super.onCleared()
        val state = _activeWorkoutState.value
        if (state != null) {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                try {
                    val backup = ActiveWorkoutBackup(
                        id = 1,
                        templateId = state.templateId,
                        templateName = state.templateName,
                        startTime = state.startTime,
                        exercisesJson = serializeExercises(state.exercises),
                        setsJson = serializeSets(state.sets),
                        exerciseMetadataJson = serializeMetadata(state.exerciseMetadata, state)
                    )
                    repository.saveActiveWorkoutBackup(backup)
                } catch (e: Exception) {
                    android.util.Log.e("ActiveWorkoutVM", "Failed onCleared backup flush", e)
                }
            }
        }
    }

    // JSON Serialization Helpers
    private fun serializeExercises(exercises: List<Exercise>): String {
        return JSONArray().apply {
            exercises.forEach { ex ->
                put(JSONObject().apply {
                    put("id", ex.id)
                    put("name", ex.name)
                    put("category", ex.category)
                    put("isCustom", ex.isCustom)
                    put("userId", ex.humanUserId)
                })
            }
        }.toString()
    }

    private fun deserializeExercises(json: String): List<Exercise> {
        val list = mutableListOf<Exercise>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(Exercise(
                id = obj.getString("id"),
                name = obj.getString("name"),
                category = obj.getString("category"),
                isCustom = if (obj.has("isCustom")) obj.getBoolean("isCustom") else false,
                humanUserId = if (obj.isNull("userId") || !obj.has("userId")) "" else obj.getString("userId")
            ))
        }
        return list
    }

    private fun serializeSets(sets: Map<String, List<ActiveSet>>): String {
        return JSONObject().apply {
            sets.forEach { (exId, setList) ->
                val arr = JSONArray()
                setList.forEach { set ->
                    arr.put(JSONObject().apply {
                        put("id", set.id)
                        put("setNumber", set.setNumber)
                        put("reps", set.reps)
                        put("weight", set.weight)
                        put("isCompleted", set.isCompleted)
                        put("prevSummary", set.prevSummary)
                        put("rpe", set.rpe ?: JSONObject.NULL)
                        put("actualDuration", set.actualDuration ?: JSONObject.NULL)
                        put("actualDistance", set.actualDistance ?: JSONObject.NULL)
                        put("setType", set.setType)
                        put("targetRepsMin", set.targetRepsMin ?: JSONObject.NULL)
                        put("targetRepsMax", set.targetRepsMax ?: JSONObject.NULL)
                        put("targetWeight", set.targetWeight ?: JSONObject.NULL)
                        put("targetRpe", set.targetRpe ?: JSONObject.NULL)
                        put("targetDuration", set.targetDuration ?: JSONObject.NULL)
                        put("targetDistance", set.targetDistance ?: JSONObject.NULL)
                        put("tempo", set.tempo ?: JSONObject.NULL)
                        put("notes", set.notes ?: JSONObject.NULL)
                    })
                }
                put(exId, arr)
            }
        }.toString()
    }

    private fun deserializeSets(json: String): Map<String, List<ActiveSet>> {
        val map = mutableMapOf<String, List<ActiveSet>>()
        val obj = JSONObject(json)
        obj.keys().forEach { exId ->
            val arr = obj.getJSONArray(exId)
            val list = mutableListOf<ActiveSet>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                list.add(ActiveSet(
                    id = s.optInt("id", 0),
                    setNumber = s.getInt("setNumber"),
                    reps = s.getInt("reps"),
                    weight = s.getDouble("weight").toFloat(),
                    isCompleted = s.getBoolean("isCompleted"),
                    prevSummary = s.optString("prevSummary", ""),
                    rpe = if (s.isNull("rpe")) null else s.getInt("rpe"),
                    actualDuration = if (s.isNull("actualDuration")) null else s.getInt("actualDuration"),
                    actualDistance = if (s.isNull("actualDistance")) null else s.getDouble("actualDistance").toFloat(),
                    setType = s.optString("setType", "WORKING"),
                    targetRepsMin = if (s.isNull("targetRepsMin")) null else s.getInt("targetRepsMin"),
                    targetRepsMax = if (s.isNull("targetRepsMax")) null else s.getInt("targetRepsMax"),
                    targetWeight = if (s.isNull("targetWeight")) null else s.getDouble("targetWeight").toFloat(),
                    targetRpe = if (s.isNull("targetRpe")) null else s.getInt("targetRpe"),
                    targetDuration = if (s.isNull("targetDuration")) null else s.getInt("targetDuration"),
                    targetDistance = if (s.isNull("targetDistance")) null else s.getDouble("targetDistance").toFloat(),
                    tempo = if (s.isNull("tempo")) null else s.getString("tempo"),
                    notes = if (s.isNull("notes")) null else s.getString("notes")
                ))
            }
            map[exId] = list
        }
        return map
    }

    private fun serializeMetadata(meta: Map<String, ActiveExerciseMetadata>, state: ActiveWorkoutState): String {
        return JSONObject().apply {
            meta.forEach { (exId, m) ->
                put(exId, JSONObject().apply {
                    put("restSeconds", m.restSeconds ?: JSONObject.NULL)
                    put("notes", m.notes ?: JSONObject.NULL)
                    put("supersetGroupId", m.supersetGroupId ?: JSONObject.NULL)
                })
            }
            // Save global recovery properties
            put("__global_recovery__", JSONObject().apply {
                put("activeSessionId", state.activeSessionId)
                put("currentExerciseId", state.currentExerciseId ?: JSONObject.NULL)
                put("workoutNotes", state.workoutNotes)
                put("restTimerEndTimestamp", state.restTimerEndTimestamp ?: JSONObject.NULL)
                put("restTimerDuration", state.restTimerDuration ?: JSONObject.NULL)
                put("isRestTimerPaused", state.isRestTimerPaused)
                put("isMetric", state.isMetric)
                put("stateVersion", state.stateVersion)
                _restTimeRemaining.value?.let {
                    put("restTimerRemainingAtSave", it)
                }
            })
        }.toString()
    }

    private fun deserializeMetadata(json: String): Map<String, ActiveExerciseMetadata> {
        val map = mutableMapOf<String, ActiveExerciseMetadata>()
        val obj = JSONObject(json)
        obj.keys().forEach { exId ->
            if (exId != "__global_recovery__") {
                val m = obj.getJSONObject(exId)
                map[exId] = ActiveExerciseMetadata(
                    restSeconds = if (m.isNull("restSeconds")) null else m.getInt("restSeconds"),
                    notes = if (m.isNull("notes")) null else m.getString("notes"),
                    supersetGroupId = if (m.isNull("supersetGroupId")) null else m.getString("supersetGroupId")
                )
            }
        }
        return map
    }

    private fun updateStateTimer(
        endTimestamp: Long?,
        duration: Int?,
        isPaused: Boolean
    ) {
        val current = _activeWorkoutState.value ?: return
        _activeWorkoutState.value = current.copy(
            restTimerEndTimestamp = endTimestamp,
            restTimerDuration = duration,
            isRestTimerPaused = isPaused
        )
    }

    // Rest Timer Logic
    fun startRestTimer(duration: Int) {
        restTimerJob?.cancel()
        val endTimestamp = System.currentTimeMillis() + (duration * 1000L)
        _restTimerDuration.value = duration
        _restTimeRemaining.value = duration
        _isRestTimerPaused.value = false
        
        updateStateTimer(
            endTimestamp = endTimestamp,
            duration = duration,
            isPaused = false
        )
        
        runRestTimer(endTimestamp)
    }

    private fun runRestTimer(targetEndTime: Long) {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (!_isRestTimerPaused.value) {
                    val remainingMs = targetEndTime - System.currentTimeMillis()
                    val remaining = (remainingMs / 1000).toInt()
                    if (remaining > 0) {
                        _restTimeRemaining.value = remaining
                    } else {
                        completeRest(RestCompletionReason.Expired)
                        break
                    }
                }
            }
        }
    }

    fun pauseRestTimer() {
        _isRestTimerPaused.value = true
        val remaining = _restTimeRemaining.value ?: 0
        val current = _activeWorkoutState.value
        if (current != null) {
            _activeWorkoutState.value = current.copy(
                isRestTimerPaused = true,
                restTimerEndTimestamp = System.currentTimeMillis() + (remaining * 1000L),
                restTimerDuration = current.restTimerDuration
            )
        }
    }

    fun resumeRestTimer() {
        val remaining = _restTimeRemaining.value ?: return
        if (remaining > 0) {
            _isRestTimerPaused.value = false
            val endTimestamp = System.currentTimeMillis() + (remaining * 1000L)
            updateStateTimer(
                endTimestamp = endTimestamp,
                duration = _restTimerDuration.value,
                isPaused = false
            )
            runRestTimer(endTimestamp)
        }
    }

    enum class RestCompletionReason { Skipped, Expired }

    fun completeRest(reason: RestCompletionReason = RestCompletionReason.Skipped) {
        restTimerJob?.cancel()
        _restTimeRemaining.value = null
        _isRestTimerPaused.value = false
        updateStateTimer(null, null, false)
        if (reason == RestCompletionReason.Expired) {
            triggerRestFinishedFeedback()
        }
    }

    fun skipRestTimer() {
        completeRest(RestCompletionReason.Skipped)
    }

    fun resetRestTimer() {
        startRestTimer(_restTimerDuration.value)
    }

    fun startRestGuide(seconds: Int) {
        startRestTimer(seconds)
    }

    fun addRestTime(seconds: Int) {
        val remaining = _restTimeRemaining.value ?: return
        val updated = (remaining + seconds).coerceAtMost(600)
        _restTimeRemaining.value = updated
        _restTimerDuration.value = updated
        val endTimestamp = System.currentTimeMillis() + (updated * 1000L)
        updateStateTimer(
            endTimestamp = endTimestamp,
            duration = updated,
            isPaused = _isRestTimerPaused.value
        )
        if (!_isRestTimerPaused.value) {
            runRestTimer(endTimestamp)
        }
    }

    fun reduceRestTime(seconds: Int) {
        val remaining = _restTimeRemaining.value ?: return
        val updated = (remaining - seconds).coerceAtLeast(0)
        _restTimeRemaining.value = updated
        _restTimerDuration.value = updated
        if (updated == 0) {
            completeRest(RestCompletionReason.Skipped)
        } else {
            val endTimestamp = System.currentTimeMillis() + (updated * 1000L)
            updateStateTimer(
                endTimestamp = endTimestamp,
                duration = updated,
                isPaused = _isRestTimerPaused.value
            )
            if (!_isRestTimerPaused.value) {
                runRestTimer(endTimestamp)
            }
        }
    }

    fun skipRestGuide() {
        skipRestTimer()
    }

    fun clearRestGuide() {
        skipRestTimer()
    }

    private fun triggerRestFinishedFeedback() {
        if (soundOn.value) {
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (vibrationOn.value) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (vibrator != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(500)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Active Workout Operations
    fun startCasualWorkout() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _activeWorkoutState.value = ActiveWorkoutState(
                templateId = null,
                templateName = "Log a Workout",
                startTime = startTime,
                exercises = emptyList(),
                sets = emptyMap(),
                exerciseMetadata = emptyMap(),
                workoutSource = "CASUAL"
            )
            _navigateToActiveWorkoutEvent.emit(Unit)
        }
    }

    fun startWorkout(template: WorkoutTemplate?) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val templateName = template?.name ?: "Custom Workout"
            val templateId = template?.id

            val activeExercises = mutableListOf<Exercise>()
            val activeSetsMap = mutableMapOf<String, List<ActiveSet>>()
            val exerciseMetadata = mutableMapOf<String, ActiveExerciseMetadata>()

            if (template != null) {
                val dbExercises = repository.getTemplateExercisesSync(template.id)
                if (dbExercises.isNotEmpty()) {
                    for (te in dbExercises) {
                        val exercise = repository.getExerciseById(te.exerciseId)
                        if (exercise != null) {
                            activeExercises.add(exercise)
                            exerciseMetadata[exercise.id] = ActiveExerciseMetadata(
                                restSeconds = te.restSeconds,
                                notes = te.notes,
                                supersetGroupId = te.supersetGroupId
                            )
                            
                            val prevSets = repository.getPreviousSetsForExercise(te.exerciseId)
                            val tSets = repository.getTemplateSetsSync(te.id)
                            if (tSets.isNotEmpty()) {
                                val activeSets = tSets.mapIndexed { index, ts ->
                                    val ps = prevSets.getOrNull(index)
                                    val prevSum = if (ps != null) "${com.example.core.util.UnitConverter.formatWeight(ps.weight.toDouble(), isMetric.value)} x ${ps.reps}" else ""
                                    ActiveSet(
                                        setNumber = ts.position,
                                        reps = ts.targetRepsMin ?: 10,
                                        weight = ts.targetWeight ?: 0f,
                                        isCompleted = false,
                                        prevSummary = prevSum,
                                        setType = ts.setType,
                                        targetRepsMin = ts.targetRepsMin,
                                        targetRepsMax = ts.targetRepsMax,
                                        targetWeight = ts.targetWeight,
                                        targetRpe = ts.targetRpe,
                                        targetDuration = ts.targetDurationSeconds,
                                        targetDistance = ts.targetDistance,
                                        tempo = ts.tempo,
                                        notes = ts.notes
                                    )
                                }
                                activeSetsMap[exercise.id] = activeSets
                            } else {
                                val activeSets = (1..3).mapIndexed { index, setNum ->
                                    val ps = prevSets.getOrNull(index)
                                    val prevSum = if (ps != null) "${com.example.core.util.UnitConverter.formatWeight(ps.weight.toDouble(), isMetric.value)} x ${ps.reps}" else ""
                                    ActiveSet(
                                        setNumber = setNum,
                                        reps = 10,
                                        weight = 0f,
                                        isCompleted = false,
                                        prevSummary = prevSum
                                    )
                                }
                                activeSetsMap[exercise.id] = activeSets
                            }
                        }
                    }
                } else {
                    val exerciseIds = deserializeExerciseIdsList(template.exerciseIdsJson)
                    for (id in exerciseIds) {
                        val exercise = repository.getExerciseById(id)
                        if (exercise != null) {
                            activeExercises.add(exercise)
                            val prevSets = repository.getPreviousSetsForExercise(id)
                            if (prevSets.isNotEmpty()) {
                                val activeSets = prevSets.map { ps ->
                                    ActiveSet(
                                        setNumber = ps.setNumber,
                                        reps = ps.reps,
                                        weight = ps.weight,
                                        isCompleted = false,
                                        prevSummary = "${com.example.core.util.UnitConverter.formatWeight(ps.weight.toDouble(), isMetric.value)} x ${ps.reps}"
                                    )
                                }
                                activeSetsMap[id] = activeSets
                            } else {
                                activeSetsMap[id] = listOf(
                                    ActiveSet(setNumber = 1, reps = 10, weight = 0f),
                                    ActiveSet(setNumber = 2, reps = 10, weight = 0f),
                                    ActiveSet(setNumber = 3, reps = 10, weight = 0f)
                                )
                            }
                        }
                    }
                }
            }

            _activeWorkoutState.value = ActiveWorkoutState(
                templateId = templateId,
                templateName = templateName,
                startTime = startTime,
                exercises = activeExercises,
                sets = activeSetsMap,
                exerciseMetadata = exerciseMetadata
            )
            _navigateToActiveWorkoutEvent.emit(Unit)
        }
    }

    fun renameActiveWorkout(newName: String) {
        val currentState = _activeWorkoutState.value ?: return
        val finalName = if (newName.isBlank()) {
            val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            "Strength Session - $dateStr"
        } else {
            newName
        }
        _activeWorkoutState.value = currentState.copy(templateName = finalName)
    }

    fun addExerciseToActiveWorkout(exercise: Exercise) {
        viewModelScope.launch {
            val currentState = _activeWorkoutState.value ?: return@launch
            val existingIndex = currentState.exercises.indexOfFirst { it.id == exercise.id }
            if (existingIndex >= 0) {
                // Repeated exercise selected: group as a new set under the existing exercise!
                val currentSets = currentState.sets[exercise.id] ?: emptyList()
                val lastSet = currentSets.lastOrNull()
                val prevSets = repository.getPreviousSetsForExercise(exercise.id)
                val ps = prevSets.getOrNull(currentSets.size)
                val prevSum = if (ps != null) "${com.example.core.util.UnitConverter.formatWeight(ps.weight.toDouble(), isMetric.value)} x ${ps.reps}" else ""

                val newSet = ActiveSet(
                    setNumber = currentSets.size + 1,
                    reps = lastSet?.reps ?: 10,
                    weight = lastSet?.weight ?: 0f,
                    isCompleted = false,
                    prevSummary = prevSum,
                    setType = lastSet?.setType ?: "WORKING",
                    targetRepsMin = lastSet?.targetRepsMin,
                    targetRepsMax = lastSet?.targetRepsMax,
                    targetWeight = lastSet?.targetWeight
                )
                val updatedSetsMap = currentState.sets.toMutableMap()
                updatedSetsMap[exercise.id] = currentSets + newSet
                _activeWorkoutState.value = currentState.copy(sets = updatedSetsMap)
                return@launch
            }

            val updatedExercises = currentState.exercises + exercise
            val prevSets = repository.getPreviousSetsForExercise(exercise.id)
            val initialSets = if (prevSets.isNotEmpty()) {
                prevSets.map { ps ->
                    ActiveSet(
                        setNumber = ps.setNumber,
                        reps = ps.reps,
                        weight = ps.weight,
                        isCompleted = false,
                        prevSummary = "${com.example.core.util.UnitConverter.formatWeight(ps.weight.toDouble(), isMetric.value)} x ${ps.reps}"
                    )
                }
            } else {
                listOf(ActiveSet(setNumber = 1, reps = 10, weight = 0f))
            }

            val updatedSets = currentState.sets.toMutableMap()
            updatedSets[exercise.id] = initialSets

            _activeWorkoutState.value = currentState.copy(
                exercises = updatedExercises,
                sets = updatedSets
            )
        }
    }

    fun removeExerciseFromActiveWorkout(exerciseId: String, onUndoBackup: (Int, Pair<Exercise, List<ActiveSet>>) -> Unit) {
        val currentState = _activeWorkoutState.value ?: return
        val exerciseIndex = currentState.exercises.indexOfFirst { it.id == exerciseId }
        if (exerciseIndex == -1) return
        val exercise = currentState.exercises[exerciseIndex]
        val exerciseSets = currentState.sets[exerciseId] ?: emptyList()

        onUndoBackup(exerciseIndex, Pair(exercise, exerciseSets))

        val updatedExercises = currentState.exercises.filter { it.id != exerciseId }
        val updatedSets = currentState.sets.toMutableMap()
        updatedSets.remove(exerciseId)

        _activeWorkoutState.value = currentState.copy(
            exercises = updatedExercises,
            sets = updatedSets
        )
    }

    fun restoreRemovedExercise(index: Int, backup: Pair<Exercise, List<ActiveSet>>) {
        val currentState = _activeWorkoutState.value ?: return
        val exercise = backup.first
        val sets = backup.second
        
        val updatedExercises = currentState.exercises.toMutableList()
        if (index in 0..updatedExercises.size) {
            updatedExercises.add(index, exercise)
        } else {
            updatedExercises.add(exercise)
        }
        val updatedSets = currentState.sets.toMutableMap()
        updatedSets[exercise.id] = sets
        
        _activeWorkoutState.value = currentState.copy(
            exercises = updatedExercises,
            sets = updatedSets
        )
    }

    fun addSetToExercise(exerciseId: String) {
        val currentState = _activeWorkoutState.value ?: return
        val currentSets = currentState.sets[exerciseId] ?: emptyList()
        val nextNumber = currentSets.size + 1
        
        val lastSet = currentSets.lastOrNull()
        val defaultReps = lastSet?.reps ?: 10
        val defaultWeight = lastSet?.weight ?: 0f
        val defaultType = lastSet?.setType ?: "WORKING"

        val newSet = ActiveSet(
            setNumber = nextNumber,
            reps = defaultReps,
            weight = defaultWeight,
            setType = defaultType,
            targetRepsMin = lastSet?.targetRepsMin,
            targetRepsMax = lastSet?.targetRepsMax,
            targetWeight = lastSet?.targetWeight,
            targetRpe = lastSet?.targetRpe,
            targetDuration = lastSet?.targetDuration,
            targetDistance = lastSet?.targetDistance,
            tempo = lastSet?.tempo,
            notes = lastSet?.notes
        )
        val updatedSets = currentState.sets.toMutableMap()
        updatedSets[exerciseId] = currentSets + newSet

        _activeWorkoutState.value = currentState.copy(sets = updatedSets)
    }

    fun removeSetFromExercise(exerciseId: String, setIndex: Int, onUndoBackup: (String, Pair<Int, ActiveSet>) -> Unit) {
        val currentState = _activeWorkoutState.value ?: return
        val currentSets = currentState.sets[exerciseId] ?: return
        if (setIndex !in currentSets.indices) return

        val set = currentSets[setIndex]
        onUndoBackup(exerciseId, Pair(setIndex, set))

        val updatedSetsList = currentSets.toMutableList()
        updatedSetsList.removeAt(setIndex)

        val renumberedSets = updatedSetsList.mapIndexed { idx, activeSet ->
            activeSet.copy(setNumber = idx + 1)
        }

        val updatedSetsMap = currentState.sets.toMutableMap()
        if (renumberedSets.isEmpty()) {
            updatedSetsMap[exerciseId] = listOf(ActiveSet(setNumber = 1, reps = 10, weight = 0f))
        } else {
            updatedSetsMap[exerciseId] = renumberedSets
        }

        _activeWorkoutState.value = currentState.copy(sets = updatedSetsMap)
    }

    fun restoreRemovedSet(exerciseId: String, index: Int, set: ActiveSet) {
        val currentState = _activeWorkoutState.value ?: return
        val currentSets = currentState.sets[exerciseId] ?: emptyList()
        val updatedSetsList = currentSets.toMutableList()
        if (index in 0..updatedSetsList.size) {
            updatedSetsList.add(index, set)
        } else {
            updatedSetsList.add(set)
        }
        val renumberedSets = updatedSetsList.mapIndexed { idx, s -> s.copy(setNumber = idx + 1) }
        val updatedSetsMap = currentState.sets.toMutableMap()
        updatedSetsMap[exerciseId] = renumberedSets
        _activeWorkoutState.value = currentState.copy(sets = updatedSetsMap)
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
    ) {
        val currentState = _activeWorkoutState.value ?: return
        val currentSets = currentState.sets[exerciseId] ?: return
        if (setIndex !in currentSets.indices) return

        val oldSet = currentSets[setIndex]
        val completionTime = if (isCompleted && oldSet.completedAt == null) System.currentTimeMillis() else if (isCompleted) oldSet.completedAt else null

        val updatedSetsList = currentSets.toMutableList()
        updatedSetsList[setIndex] = updatedSetsList[setIndex].copy(
            reps = reps,
            weight = weight,
            isCompleted = isCompleted,
            rpe = rpe,
            actualDuration = actualDuration,
            actualDistance = actualDistance,
            setType = setType,
            targetRepsMin = targetRepsMin,
            targetRepsMax = targetRepsMax,
            targetWeight = targetWeight,
            targetRpe = targetRpe,
            targetDuration = targetDuration,
            targetDistance = targetDistance,
            tempo = tempo,
            notes = notes,
            completedAt = completionTime
        )

        val updatedSetsMap = currentState.sets.toMutableMap()
        updatedSetsMap[exerciseId] = updatedSetsList

        val updatedState = currentState.copy(sets = updatedSetsMap)
        _activeWorkoutState.value = updatedState

        if (isCompleted && !oldSet.isCompleted) {
            if (currentState.workoutSource != "CASUAL") {
                val groupId = currentState.exerciseMetadata[exerciseId]?.supersetGroupId
                if (!groupId.isNullOrEmpty()) {
                    val groupExercises = currentState.exercises.filter { currentState.exerciseMetadata[it.id]?.supersetGroupId == groupId }
                    val currentInGroupIdx = groupExercises.indexOfFirst { it.id == exerciseId }
                    val isFinalExerciseInRound = currentInGroupIdx == groupExercises.size - 1
                    val restDuration = if (isFinalExerciseInRound) {
                        currentState.exerciseMetadata[exerciseId]?.restSeconds ?: defaultRestTimerDuration.value
                    } else {
                        45
                    }
                    startRestTimer(restDuration)
                } else {
                    val customRest = currentState.exerciseMetadata[exerciseId]?.restSeconds
                    startRestTimer(customRest ?: defaultRestTimerDuration.value)
                }
            }
            evaluateCasualSupersets(updatedState)
        }
    }

    fun confirmCasualSuperset(exIdA: String, exIdB: String) {
        val currentState = _activeWorkoutState.value ?: return
        val newSuperset = CasualSuperset(
            exerciseIds = listOf(exIdA, exIdB),
            isConfirmed = true
        )
        val updatedSupersets = currentState.casualSupersets + newSuperset
        _activeWorkoutState.value = currentState.copy(
            casualSupersets = updatedSupersets,
            pendingSupersetSuggestion = null
        )
    }

    fun dismissCasualSuperset(exIdA: String, exIdB: String) {
        val currentState = _activeWorkoutState.value ?: return
        val key1 = "$exIdA:$exIdB"
        val key2 = "$exIdB:$exIdA"
        val updatedDismissed = currentState.dismissedSupersets + key1 + key2
        _activeWorkoutState.value = currentState.copy(
            dismissedSupersets = updatedDismissed,
            pendingSupersetSuggestion = null
        )
    }

    private fun evaluateCasualSupersets(state: ActiveWorkoutState) {
        if (state.workoutSource != "CASUAL") return
        if (state.pendingSupersetSuggestion != null) return

        data class CompletedSetInfo(val exerciseId: String, val setNumber: Int, val completedAt: Long)
        val completedSets = mutableListOf<CompletedSetInfo>()

        for (ex in state.exercises) {
            val sets = state.sets[ex.id] ?: continue
            for (s in sets) {
                if (s.isCompleted && s.completedAt != null) {
                    completedSets.add(CompletedSetInfo(ex.id, s.setNumber, s.completedAt))
                }
            }
        }

        if (completedSets.size < 4) return
        completedSets.sortBy { it.completedAt }

        val exercisesWithMultipleSets = completedSets.groupBy { it.exerciseId }.filter { it.value.size >= 2 }.keys.toList()
        if (exercisesWithMultipleSets.size < 2) return

        for (i in 0 until exercisesWithMultipleSets.size) {
            for (j in i + 1 until exercisesWithMultipleSets.size) {
                val exIdA = exercisesWithMultipleSets[i]
                val exIdB = exercisesWithMultipleSets[j]

                val pairKey1 = "$exIdA:$exIdB"
                val pairKey2 = "$exIdB:$exIdA"

                val alreadyGrouped = state.casualSupersets.any { cs -> cs.exerciseIds.contains(exIdA) && cs.exerciseIds.contains(exIdB) }
                val alreadyDismissed = state.dismissedSupersets.contains(pairKey1) || state.dismissedSupersets.contains(pairKey2)

                if (alreadyGrouped || alreadyDismissed) continue

                val subList = completedSets.filter { it.exerciseId == exIdA || it.exerciseId == exIdB }
                if (subList.size >= 4) {
                    var isAlternating = true
                    for (idx in 0 until subList.size - 1) {
                        if (subList[idx].exerciseId == subList[idx + 1].exerciseId) {
                            isAlternating = false
                            break
                        }
                    }
                    if (isAlternating) {
                        val startTime = subList.first().completedAt
                        val endTime = subList.last().completedAt
                        val interruptedByThird = completedSets.any {
                            it.exerciseId != exIdA && it.exerciseId != exIdB && it.completedAt in startTime..endTime
                        }

                        if (!interruptedByThird) {
                            val exA = state.exercises.find { it.id == exIdA }
                            val exB = state.exercises.find { it.id == exIdB }
                            if (exA != null && exB != null) {
                                _activeWorkoutState.value = state.copy(
                                    pendingSupersetSuggestion = PendingSupersetSuggestion(
                                        exerciseIdA = exIdA,
                                        exerciseNameA = exA.name,
                                        exerciseIdB = exIdB,
                                        exerciseNameB = exB.name
                                    )
                                )
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    fun finishActiveWorkout() {
        if (_isCompletingWorkout.value) {
            android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Duplicate workout completion request ignored.")
            return
        }
        val currentState = _activeWorkoutState.value
        if (currentState == null) {
            android.util.Log.e("ActiveWorkoutVM", "[COMPLETION] Cannot finish workout: active workout state is null.")
            return
        }
        
        android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Workout completion requested for templateName: ${currentState.templateName}")
        _isCompletingWorkout.value = true

        viewModelScope.launch {
            try {
                // Ensure complete idempotency by querying database before starting insertion
                val existingSession = repository.dao.getSessionByStartTime(currentState.startTime)
                val sessionId = if (existingSession != null) {
                    android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Workout session already exists with startTime: ${currentState.startTime}. Re-using ID: ${existingSession.id}")
                    existingSession.id
                } else {
                    val endTime = System.currentTimeMillis()
                    val session = WorkoutSession(
                        templateId = currentState.templateId,
                        templateName = currentState.templateName,
                        startTime = currentState.startTime,
                        endTime = endTime,
                        userId = authViewModel.activeUserId.value
                    )

                    android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Final set persistence started...")
                    val insertedId = repository.insertSession(session).toInt()

                    val loggedSets = mutableListOf<LoggedSet>()
                    for (exercise in currentState.exercises) {
                        val setsList = currentState.sets[exercise.id] ?: emptyList()
                        for (set in setsList) {
                            val shouldSave = set.isCompleted || (setsList.none { it.isCompleted } && set.weight > 0)
                            if (shouldSave) {
                                loggedSets.add(
                                    LoggedSet(
                                        sessionId = insertedId,
                                        exerciseId = exercise.id,
                                        setNumber = set.setNumber,
                                        reps = set.reps,
                                        weight = set.weight,
                                        isCompleted = true,
                                        rpe = set.rpe,
                                        actualDuration = set.actualDuration,
                                        actualDistance = set.actualDistance,
                                        setType = set.setType,
                                        targetRepsMin = set.targetRepsMin,
                                        targetRepsMax = set.targetRepsMax,
                                        targetWeight = set.targetWeight,
                                        targetRpe = set.targetRpe,
                                        targetDuration = set.targetDuration,
                                        targetDistance = set.targetDistance,
                                        notes = set.notes
                                    )
                                )
                            }
                        }
                    }

                    if (loggedSets.isNotEmpty()) {
                        repository.insertLoggedSets(loggedSets)
                    } else {
                        currentState.exercises.firstOrNull()?.let { firstExercise ->
                            repository.insertLoggedSet(
                                LoggedSet(
                                    sessionId = insertedId,
                                    exerciseId = firstExercise.id,
                                    setNumber = 1,
                                    reps = 10,
                                    weight = 0f,
                                    isCompleted = true
                                )
                            )
                        }
                    }
                    insertedId
                }

                android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Workout persistence succeeded. Assigned Session ID: $sessionId")
                
                clearRestGuide()
                android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Rest guide cleared.")

                // Ensure we explicitly clear database active workout backup now that permanent storage succeeded!
                repository.clearActiveWorkoutBackup()
                lastSavedState = null

                _activeWorkoutState.value = null
                android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Active workout state cleared.")

                _activeWorkoutEvents.emit(ActiveWorkoutEvent.WorkoutCompleted(sessionId.toLong()))
                android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Navigation event emitted with ID: $sessionId")

            } catch (e: Exception) {
                android.util.Log.e("ActiveWorkoutVM", "[COMPLETION] Workout completion failed!", e)
                _activeWorkoutEvents.emit(ActiveWorkoutEvent.ShowError(e.localizedMessage ?: "Unknown database error occurred"))
            } finally {
                _isCompletingWorkout.value = false
            }
        }
    }

    fun cancelActiveWorkout() {
        android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Cancelling active workout.")
        _activeWorkoutState.value = null
    }
}
