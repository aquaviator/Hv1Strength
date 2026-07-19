package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.VolumeCalculator
import com.example.domain.SetVolumeData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    // Active Workout State
    private val _activeWorkoutState = MutableStateFlow<ActiveWorkoutState?>(null)
    val activeWorkoutState: StateFlow<ActiveWorkoutState?> = _activeWorkoutState.asStateFlow()

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

    init {
        // 1. Auto-save active workout state changes to Database Backup
        viewModelScope.launch {
            _activeWorkoutState.collect { state ->
                if (state == null) {
                    repository.clearActiveWorkoutBackup()
                } else {
                    try {
                        val backup = ActiveWorkoutBackup(
                            id = 1,
                            templateId = state.templateId,
                            templateName = state.templateName,
                            startTime = state.startTime,
                            exercisesJson = serializeExercises(state.exercises),
                            setsJson = serializeSets(state.sets),
                            exerciseMetadataJson = serializeMetadata(state.exerciseMetadata)
                        )
                        repository.saveActiveWorkoutBackup(backup)
                    } catch (e: Exception) {
                        android.util.Log.e("ActiveWorkoutVM", "Failed to save active workout backup", e)
                    }
                }
            }
        }

        // 2. Load active workout backup from Database on startup
        viewModelScope.launch {
            try {
                val backup = repository.getActiveWorkoutBackup()
                if (backup != null) {
                    val restoredExercises = deserializeExercises(backup.exercisesJson)
                    val restoredSets = deserializeSets(backup.setsJson)
                    val restoredMetadata = deserializeMetadata(backup.exerciseMetadataJson)
                    _activeWorkoutState.value = ActiveWorkoutState(
                        templateId = backup.templateId,
                        templateName = backup.templateName,
                        startTime = backup.startTime,
                        exercises = restoredExercises,
                        sets = restoredSets,
                        exerciseMetadata = restoredMetadata
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ActiveWorkoutVM", "Failed to restore active workout backup", e)
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
                isCustom = obj.getBoolean("isCustom"),
                humanUserId = if (obj.isNull("userId")) "" else obj.getString("userId")
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

    private fun serializeMetadata(meta: Map<String, ActiveExerciseMetadata>): String {
        return JSONObject().apply {
            meta.forEach { (exId, m) ->
                put(exId, JSONObject().apply {
                    put("restSeconds", m.restSeconds ?: JSONObject.NULL)
                    put("notes", m.notes ?: JSONObject.NULL)
                    put("supersetGroupId", m.supersetGroupId ?: JSONObject.NULL)
                })
            }
        }.toString()
    }

    private fun deserializeMetadata(json: String): Map<String, ActiveExerciseMetadata> {
        val map = mutableMapOf<String, ActiveExerciseMetadata>()
        val obj = JSONObject(json)
        obj.keys().forEach { exId ->
            val m = obj.getJSONObject(exId)
            map[exId] = ActiveExerciseMetadata(
                restSeconds = if (m.isNull("restSeconds")) null else m.getInt("restSeconds"),
                notes = if (m.isNull("notes")) null else m.getString("notes"),
                supersetGroupId = if (m.isNull("supersetGroupId")) null else m.getString("supersetGroupId")
            )
        }
        return map
    }

    // Rest Timer Logic
    fun startRestTimer(duration: Int) {
        restTimerJob?.cancel()
        _restTimerDuration.value = duration
        _restTimeRemaining.value = duration
        _isRestTimerPaused.value = false
        runRestTimer()
    }

    private fun runRestTimer() {
        restTimerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (!_isRestTimerPaused.value) {
                    val remaining = _restTimeRemaining.value ?: break
                    if (remaining > 1) {
                        _restTimeRemaining.value = remaining - 1
                    } else {
                        _restTimeRemaining.value = 0
                        triggerRestFinishedFeedback()
                        break
                    }
                }
            }
        }
    }

    fun pauseRestTimer() {
        _isRestTimerPaused.value = true
    }

    fun resumeRestTimer() {
        if (_restTimeRemaining.value != null && _restTimeRemaining.value!! > 0) {
            _isRestTimerPaused.value = false
        }
    }

    fun skipRestTimer() {
        restTimerJob?.cancel()
        _restTimeRemaining.value = null
        _isRestTimerPaused.value = false
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
    }

    fun reduceRestTime(seconds: Int) {
        val remaining = _restTimeRemaining.value ?: return
        val updated = (remaining - seconds).coerceAtLeast(0)
        _restTimeRemaining.value = updated
        _restTimerDuration.value = updated
        if (updated == 0) {
            skipRestTimer()
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
                                    val prevSum = if (ps != null) "${ps.weight} kg x ${ps.reps}" else ""
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
                                    val prevSum = if (ps != null) "${ps.weight} kg x ${ps.reps}" else ""
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
                                        prevSummary = "${ps.weight} kg x ${ps.reps}"
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
            if (currentState.exercises.any { it.id == exercise.id }) return@launch

            val updatedExercises = currentState.exercises + exercise
            val prevSets = repository.getPreviousSetsForExercise(exercise.id)
            val initialSets = if (prevSets.isNotEmpty()) {
                prevSets.map { ps ->
                    ActiveSet(
                        setNumber = ps.setNumber,
                        reps = ps.reps,
                        weight = ps.weight,
                        isCompleted = false,
                        prevSummary = "${ps.weight} kg x ${ps.reps}"
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
            notes = notes
        )

        val updatedSetsMap = currentState.sets.toMutableMap()
        updatedSetsMap[exerciseId] = updatedSetsList

        _activeWorkoutState.value = currentState.copy(sets = updatedSetsMap)

        if (isCompleted && !oldSet.isCompleted) {
            val groupId = currentState.exerciseMetadata[exerciseId]?.supersetGroupId
            val shouldStartTimer = if (!groupId.isNullOrEmpty()) {
                val supersetExercises = currentState.exercises.filter { currentState.exerciseMetadata[it.id]?.supersetGroupId == groupId }
                supersetExercises.all { ex ->
                    val sets = if (ex.id == exerciseId) updatedSetsList else (currentState.sets[ex.id] ?: emptyList())
                    if (setIndex in sets.indices) {
                        sets[setIndex].isCompleted
                    } else {
                        true
                    }
                }
            } else {
                true
            }

            if (shouldStartTimer) {
                val customRest = currentState.exerciseMetadata[exerciseId]?.restSeconds
                startRestTimer(customRest ?: defaultRestTimerDuration.value)
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
                val endTime = System.currentTimeMillis()
                val session = WorkoutSession(
                    templateId = currentState.templateId,
                    templateName = currentState.templateName,
                    startTime = currentState.startTime,
                    endTime = endTime,
                    userId = authViewModel.activeUserId.value
                )

                android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Final set persistence started...")
                val sessionId = repository.insertSession(session).toInt()

                val loggedSets = mutableListOf<LoggedSet>()
                for (exercise in currentState.exercises) {
                    val setsList = currentState.sets[exercise.id] ?: emptyList()
                    for (set in setsList) {
                        val shouldSave = set.isCompleted || (setsList.none { it.isCompleted } && set.weight > 0)
                        if (shouldSave) {
                            loggedSets.add(
                                LoggedSet(
                                    sessionId = sessionId,
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
                                sessionId = sessionId,
                                exerciseId = firstExercise.id,
                                setNumber = 1,
                                reps = 10,
                                weight = 0f,
                                isCompleted = true
                            )
                        )
                    }
                }

                android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Workout persistence succeeded. Assigned Session ID: $sessionId")
                
                clearRestGuide()
                android.util.Log.d("ActiveWorkoutVM", "[COMPLETION] Rest guide cleared.")

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
