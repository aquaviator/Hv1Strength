package com.example.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.max

data class ActiveExerciseMetadata(
    val restSeconds: Int? = null,
    val notes: String? = null,
    val supersetGroupId: String? = null
)

data class ActiveWorkoutState(
    val templateId: Int? = null,
    val templateName: String,
    val startTime: Long,
    val exercises: List<Exercise> = emptyList(),
    // Map of exerciseId to list of sets
    val sets: Map<String, List<ActiveSet>> = emptyMap(),
    // Map of exerciseId to exercise-level template parameters
    val exerciseMetadata: Map<String, ActiveExerciseMetadata> = emptyMap()
)

data class ActiveSet(
    val id: Int = 0,
    val setNumber: Int,
    val reps: Int = 10, // actual reps
    val weight: Float = 0f, // actual weight
    val isCompleted: Boolean = false,
    val prevSummary: String = "",
    val rpe: Int? = null, // actual RPE
    val actualDuration: Int? = null,
    val actualDistance: Float? = null,
    val setType: String = "WORKING", // WARM_UP, WORKING, DROP_SET, FAILURE, BACK_OFF, AMRAP, TIMED, DISTANCE
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetWeight: Float? = null,
    val targetRpe: Int? = null,
    val targetDuration: Int? = null,
    val targetDistance: Float? = null,
    val tempo: String? = null,
    val notes: String? = null
)

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

    // Global Snackbar Host State for undo notifications
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    // Destructive action undo system backing properties
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

    // Undo Methods
    fun undoRemoveSet() {
        val backup = lastDeletedSet ?: return
        lastDeletedSet = null
        val currentState = _activeWorkoutState.value ?: return
        val exerciseId = backup.first
        val index = backup.second.first
        val set = backup.second.second
        
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

    fun undoRemoveExercise() {
        val backup = lastDeletedExercise ?: return
        lastDeletedExercise = null
        val currentState = _activeWorkoutState.value ?: return
        val index = backup.first
        val exercise = backup.second.first
        val sets = backup.second.second
        
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
                        restSeconds = te.restSeconds ?: 90,
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

    private val prefs = context.getSharedPreferences("strength_settings", android.content.Context.MODE_PRIVATE)

    // Auth Support
    val authRepository = AuthRepository(context, repository, viewModelScope)
    val authState: StateFlow<AuthState> = authRepository.authState

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeUserId: StateFlow<String> = authRepository.authState.map { state ->
        when (state) {
            is AuthState.Authenticated -> state.profile.id
            is AuthState.Offline -> "offline"
            else -> "offline"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "offline")

    // Settings StateFlows
    val isMetric = MutableStateFlow(prefs.getBoolean("is_metric", true))
    val theme = MutableStateFlow(prefs.getString("theme", "system") ?: "system")
    val keepScreenAwake = MutableStateFlow(prefs.getBoolean("keep_screen_awake", false))
    val defaultRestTimerDuration = MutableStateFlow(prefs.getInt("default_rest_timer_duration", 90))
    val soundOn = MutableStateFlow(prefs.getBoolean("sound_on", true))
    val vibrationOn = MutableStateFlow(prefs.getBoolean("vibration_on", true))
    val defaultWarmupSets = MutableStateFlow(prefs.getInt("default_warmup_sets", 0))
    val autoCompleteBehavior = MutableStateFlow(prefs.getBoolean("auto_complete_behavior", true))
    val autoScroll = MutableStateFlow(prefs.getBoolean("auto_scroll", true))
    val timerPreferences = MutableStateFlow(prefs.getString("timer_preferences", "standard") ?: "standard")

    // Setters
    fun setMetric(value: Boolean) {
        prefs.edit().putBoolean("is_metric", value).apply()
        isMetric.value = value
    }
    fun setTheme(value: String) {
        prefs.edit().putString("theme", value).apply()
        theme.value = value
    }
    fun setKeepScreenAwake(value: Boolean) {
        prefs.edit().putBoolean("keep_screen_awake", value).apply()
        keepScreenAwake.value = value
    }
    fun setDefaultRestTimerDuration(value: Int) {
        prefs.edit().putInt("default_rest_timer_duration", value).apply()
        defaultRestTimerDuration.value = value
    }
    fun setSoundOn(value: Boolean) {
        prefs.edit().putBoolean("sound_on", value).apply()
        soundOn.value = value
    }
    fun setVibrationOn(value: Boolean) {
        prefs.edit().putBoolean("vibration_on", value).apply()
        vibrationOn.value = value
    }
    fun setDefaultWarmupSets(value: Int) {
        prefs.edit().putInt("default_warmup_sets", value).apply()
        defaultWarmupSets.value = value
    }
    fun setAutoCompleteBehavior(value: Boolean) {
        prefs.edit().putBoolean("auto_complete_behavior", value).apply()
        autoCompleteBehavior.value = value
    }
    fun setAutoScroll(value: Boolean) {
        prefs.edit().putBoolean("auto_scroll", value).apply()
        autoScroll.value = value
    }
    fun setTimerPreferences(value: String) {
        prefs.edit().putString("timer_preferences", value).apply()
        timerPreferences.value = value
    }

    // Favorite Exercises Support
    val favoriteExercises = MutableStateFlow(prefs.getStringSet("favorite_exercises", emptySet()) ?: emptySet())

    fun toggleFavoriteExercise(exerciseId: String) {
        val current = favoriteExercises.value.toMutableSet()
        if (current.contains(exerciseId)) {
            current.remove(exerciseId)
        } else {
            current.add(exerciseId)
        }
        prefs.edit().putStringSet("favorite_exercises", current).apply()
        favoriteExercises.value = current
    }

    // Rest Timer State
    private val _restTimeRemaining = MutableStateFlow<Int?>(null)
    val restTimeRemaining = _restTimeRemaining.asStateFlow()

    private val _restTimerDuration = MutableStateFlow(90)
    val restTimerDuration = _restTimerDuration.asStateFlow()

    private val _isRestTimerPaused = MutableStateFlow(false)
    val isRestTimerPaused = _isRestTimerPaused.asStateFlow()

    private var restTimerJob: kotlinx.coroutines.Job? = null

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
                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
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

    // Body Weight State
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bodyWeights: StateFlow<List<BodyWeight>> = activeUserId.flatMapLatest { userId ->
        repository.getBodyWeightsForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tape Measurements State
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val tapeMeasurements: StateFlow<List<TapeMeasurement>> = activeUserId.flatMapLatest { userId ->
        repository.getTapeMeasurementsForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Exercises State
    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Templates State
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<WorkoutTemplate>> = activeUserId.flatMapLatest { userId ->
        repository.getTemplatesForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Workout Sessions State
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sessions: StateFlow<List<WorkoutSession>> = activeUserId.flatMapLatest { userId ->
        repository.getSessionsForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allLoggedSets: StateFlow<List<LoggedSet>> = activeUserId.flatMapLatest { userId ->
        repository.getLoggedSetsForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History Search & Filters
    val historySearchQuery = MutableStateFlow("")
    val historySelectedSort = MutableStateFlow("Newest") // Newest, Oldest, Highest Volume, Longest Session
    val historyDateRange = MutableStateFlow("All") // All, Last 7 Days, Last 30 Days, Last 90 Days
    val historySelectedExerciseId = MutableStateFlow<String?>(null)
    val historySelectedRoutineName = MutableStateFlow<String?>(null)
    val historyMinDuration = MutableStateFlow<Int?>(null)
    val historyMinVolume = MutableStateFlow<Float?>(null)

    val enrichedSessions: StateFlow<List<EnrichedSession>> = combine(
        sessions,
        allLoggedSets,
        exercises
    ) { sessionsList, setsList, exercisesList ->
        val setsBySession = setsList.groupBy { it.sessionId }
        val exercisesMap = exercisesList.associateBy { it.id }

        sessionsList.map { session ->
            val sessionSets = setsBySession[session.id] ?: emptyList()
            val volume = sessionSets.filter { it.isCompleted }.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
            val durationMs = session.endTime - session.startTime
            val durationMin = max(1L, durationMs / 60000)
            val exerciseNames = sessionSets.mapNotNull { exercisesMap[it.exerciseId]?.name }.distinct()

            EnrichedSession(
                session = session,
                sets = sessionSets,
                totalVolume = volume,
                durationMinutes = durationMin,
                exerciseNames = exerciseNames
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSessions: StateFlow<List<EnrichedSession>> = combine(
        enrichedSessions,
        historySearchQuery,
        historySelectedSort,
        historyDateRange,
        historySelectedExerciseId,
        historySelectedRoutineName,
        historyMinDuration,
        historyMinVolume
    ) { flowsArray ->
        @Suppress("UNCHECKED_CAST")
        val sessionsList = flowsArray[0] as List<EnrichedSession>
        val query = flowsArray[1] as String
        val sort = flowsArray[2] as String
        val dateRange = flowsArray[3] as String
        val exerciseId = flowsArray[4] as String?
        val routineName = flowsArray[5] as String?
        val minDuration = flowsArray[6] as Int?
        val minVolume = flowsArray[7] as Float?

        var result = sessionsList

        if (query.isNotEmpty()) {
            result = result.filter { session ->
                session.session.templateName.contains(query, ignoreCase = true) ||
                        session.exerciseNames.any { it.contains(query, ignoreCase = true) }
            }
        }

        if (dateRange != "All") {
            val now = System.currentTimeMillis()
            val cutoff = when (dateRange) {
                "Last 7 Days" -> now - 7 * 24 * 60 * 60 * 1000L
                "Last 30 Days" -> now - 30 * 24 * 60 * 60 * 1000L
                "Last 90 Days" -> now - 90 * 24 * 60 * 60 * 1000L
                else -> 0L
            }
            result = result.filter { it.session.startTime >= cutoff }
        }

        if (exerciseId != null) {
            result = result.filter { session ->
                session.sets.any { it.exerciseId == exerciseId }
            }
        }

        if (routineName != null) {
            result = result.filter { session ->
                session.session.templateName == routineName
            }
        }

        if (minDuration != null) {
            result = result.filter { it.durationMinutes >= minDuration }
        }

        if (minVolume != null) {
            result = result.filter { it.totalVolume >= minVolume }
        }

        when (sort) {
            "Newest" -> result.sortedByDescending { it.session.startTime }
            "Oldest" -> result.sortedBy { it.session.startTime }
            "Highest Volume" -> result.sortedByDescending { it.totalVolume }
            "Longest Session" -> result.sortedByDescending { it.durationMinutes }
            else -> result.sortedByDescending { it.session.startTime }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streakStats: StateFlow<StreakStats> = sessions.map { sessionList ->
        if (sessionList.isEmpty()) {
            return@map StreakStats(0, 0, 0f, 0f, emptySet())
        }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getDefault()

        val workoutDates = sessionList.map { sdf.format(java.util.Date(it.startTime)) }.toSortedSet()

        if (workoutDates.isEmpty()) {
            return@map StreakStats(0, 0, 0f, 0f, emptySet())
        }

        val todayStr = sdf.format(java.util.Date())
        val cal = java.util.Calendar.getInstance()
        
        var current = 0
        val hasWorkoutToday = workoutDates.contains(todayStr)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)
        val hasWorkoutYesterday = workoutDates.contains(yesterdayStr)

        if (hasWorkoutToday || hasWorkoutYesterday) {
            cal.time = java.util.Date()
            while (true) {
                val dateStr = sdf.format(cal.time)
                if (workoutDates.contains(dateStr)) {
                    current++
                    cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
        }

        var longest = 0
        var currentRun = 0
        if (workoutDates.isNotEmpty()) {
            val list = workoutDates.toList()
            currentRun = 1
            longest = 1
            val parseSdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            for (i in 1 until list.size) {
                val d1 = parseSdf.parse(list[i - 1])!!
                val d2 = parseSdf.parse(list[i])!!
                val diffMs = d2.time - d1.time
                val diffDays = diffMs / (1000 * 60 * 60 * 24)
                if (diffDays <= 1L) {
                    currentRun++
                    if (currentRun > longest) {
                        longest = currentRun
                    }
                } else if (diffDays > 1L) {
                    currentRun = 1
                }
            }
        }

        val currentMonthCalendar = java.util.Calendar.getInstance()
        val currentYear = currentMonthCalendar.get(java.util.Calendar.YEAR)
        val currentMonth = currentMonthCalendar.get(java.util.Calendar.MONTH)

        var daysInMonthWithWorkout = 0
        var daysInYearWithWorkout = 0

        workoutDates.forEach { dateStr ->
            try {
                val date = sdf.parse(dateStr)!!
                val itemCal = java.util.Calendar.getInstance()
                itemCal.time = date
                if (itemCal.get(java.util.Calendar.YEAR) == currentYear) {
                    daysInYearWithWorkout++
                    if (itemCal.get(java.util.Calendar.MONTH) == currentMonth) {
                        daysInMonthWithWorkout++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val totalDaysInCurrentMonthSoFar = currentMonthCalendar.get(java.util.Calendar.DAY_OF_MONTH)
        val totalDaysInYearSoFar = currentMonthCalendar.get(java.util.Calendar.DAY_OF_YEAR)

        val monthlyPct = (daysInMonthWithWorkout.toFloat() / totalDaysInCurrentMonthSoFar.toFloat()) * 100f
        val yearlyPct = (daysInYearWithWorkout.toFloat() / totalDaysInYearSoFar.toFloat()) * 100f

        StreakStats(
            currentStreak = current,
            longestStreak = longest,
            monthlyConsistencyPct = monthlyPct,
            yearlyConsistencyPct = yearlyPct,
            workoutDates = workoutDates
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakStats(0, 0, 0f, 0f, emptySet()))

    // Active Workout State
    private val _activeWorkoutState = MutableStateFlow<ActiveWorkoutState?>(null)
    val activeWorkoutState: StateFlow<ActiveWorkoutState?> = _activeWorkoutState.asStateFlow()

    // Completed sets lookup cache (Exercise ID to completed sets for progress screen)
    fun getCompletedSetsForExercise(exerciseId: String): Flow<List<LoggedSet>> {
        return repository.getCompletedSetsForExercise(exerciseId)
    }

    // Get logged sets for a workout session
    fun getSetsForSession(sessionId: Int): Flow<List<LoggedSet>> {
        return repository.getSetsForSession(sessionId)
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
                            
                            // Load previous performance as reference
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
                    // Backwards compatibility fallback to exerciseIdsJson
                    val exerciseIds = deserializeExerciseIds(template.exerciseIdsJson)
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
        }
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

    fun removeExerciseFromActiveWorkout(exerciseId: String) {
        val currentState = _activeWorkoutState.value ?: return
        val exerciseIndex = currentState.exercises.indexOfFirst { it.id == exerciseId }
        if (exerciseIndex == -1) return
        val exercise = currentState.exercises[exerciseIndex]
        val exerciseSets = currentState.sets[exerciseId] ?: emptyList()

        lastDeletedExercise = Pair(exerciseIndex, Pair(exercise, exerciseSets))

        val updatedExercises = currentState.exercises.filter { it.id != exerciseId }
        val updatedSets = currentState.sets.toMutableMap()
        updatedSets.remove(exerciseId)

        _activeWorkoutState.value = currentState.copy(
            exercises = updatedExercises,
            sets = updatedSets
        )

        showUndoSnackbar("Exercise '${exercise.name}' removed") {
            undoRemoveExercise()
        }
    }

    fun addSetToExercise(exerciseId: String) {
        val currentState = _activeWorkoutState.value ?: return
        val currentSets = currentState.sets[exerciseId] ?: emptyList()
        val nextNumber = currentSets.size + 1
        
        // Copy the reps & weight of the last set if available
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

    fun removeSetFromExercise(exerciseId: String, setIndex: Int) {
        val currentState = _activeWorkoutState.value ?: return
        val currentSets = currentState.sets[exerciseId] ?: return
        if (setIndex !in currentSets.indices) return

        val set = currentSets[setIndex]
        lastDeletedSet = Pair(exerciseId, Pair(setIndex, set))

        val updatedSetsList = currentSets.toMutableList()
        updatedSetsList.removeAt(setIndex)

        // Renumber sets
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

        showUndoSnackbar("Set ${setIndex + 1} removed") {
            undoRemoveSet()
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

        // Automatically start rest timer if newly checked
        if (isCompleted && !oldSet.isCompleted) {
            val customRest = currentState.exerciseMetadata[exerciseId]?.restSeconds
            startRestTimer(customRest ?: defaultRestTimerDuration.value)
        }
    }

    fun finishActiveWorkout() {
        val currentState = _activeWorkoutState.value ?: return
        viewModelScope.launch {
            val endTime = System.currentTimeMillis()
            val session = WorkoutSession(
                templateId = currentState.templateId,
                templateName = currentState.templateName,
                startTime = currentState.startTime,
                endTime = endTime,
                userId = activeUserId.value
            )

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

            _activeWorkoutState.value = null
        }
    }

    fun cancelActiveWorkout() {
        _activeWorkoutState.value = null
    }


    // Weight Operations
    fun logBodyWeight(
        weight: Float,
        bodyFat: Float? = null,
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val fatMass = if (bodyFat != null) weight * (bodyFat / 100f) else null
            val leanMass = if (fatMass != null) weight - fatMass else null
            val heightCm = prefs.getFloat("user_height_cm", 175f)
            val heightM = heightCm / 100f
            val bmi = weight / (heightM * heightM)

            repository.insertBodyWeight(
                BodyWeight(
                    weight = weight,
                    date = date,
                    bodyFat = bodyFat,
                    leanMass = leanMass,
                    fatMass = fatMass,
                    bmi = bmi,
                    userId = activeUserId.value
                )
            )
        }
    }

    fun deleteBodyWeight(id: Int) {
        viewModelScope.launch {
            val weight = repository.getBodyWeightById(id) ?: return@launch
            lastDeletedBodyWeight = weight
            repository.deleteBodyWeight(id)
            showUndoSnackbar("Body weight entry deleted") {
                undoDeleteBodyWeight()
            }
        }
    }


    // Tape Measurement Operations
    fun logTapeMeasurement(
        chest: Float?,
        waist: Float?,
        hips: Float?,
        bicepLeft: Float?,
        bicepRight: Float?,
        thighLeft: Float?,
        thighRight: Float?,
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            repository.insertTapeMeasurement(
                TapeMeasurement(
                    date = date,
                    chest = chest,
                    waist = waist,
                    hips = hips,
                    bicepLeft = bicepLeft,
                    bicepRight = bicepRight,
                    thighLeft = thighLeft,
                    thighRight = thighRight,
                    userId = activeUserId.value
                )
            )
        }
    }

    fun deleteTapeMeasurement(id: Int) {
        viewModelScope.launch {
            val measurement = repository.getTapeMeasurementById(id) ?: return@launch
            lastDeletedMeasurement = measurement
            repository.deleteTapeMeasurement(id)
            showUndoSnackbar("Tape measurement entry deleted") {
                undoDeleteTapeMeasurement()
            }
        }
    }

    fun updateUserProfileBio(dob: String, sex: String, experience: String) {
        viewModelScope.launch {
            val currentProfile = (authState.value as? AuthState.Authenticated)?.profile ?: return@launch
            val updated = currentProfile.copy(
                dateOfBirth = dob,
                sex = sex,
                trainingExperience = experience
            )
            repository.insertUserProfile(updated)
            authRepository.signInWithGoogle(
                idToken = updated.id,
                displayName = updated.displayName,
                email = updated.email,
                photoUrl = updated.photoUrl
            )
        }
    }


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

    // Routine Template Operations
    fun createTemplate(name: String, exerciseIds: List<String>) {
        viewModelScope.launch {
            val json = serializeExerciseIds(exerciseIds)
            val templateId = repository.insertTemplate(
                WorkoutTemplate(
                    name = name,
                    exerciseIdsJson = json,
                    userId = activeUserId.value
                )
            ).toInt()

            exerciseIds.forEachIndexed { idx, exId ->
                val teId = repository.insertTemplateExercise(
                    WorkoutTemplateExercise(
                        templateId = templateId,
                        exerciseId = exId,
                        position = idx + 1,
                        restSeconds = 90
                    )
                ).toInt()

                for (setNum in 1..3) {
                    repository.insertTemplateSet(
                        WorkoutTemplateSet(
                            templateExerciseId = teId,
                            position = setNum,
                            setType = "WORKING",
                            targetRepsMin = 8,
                            targetRepsMax = 10
                        )
                    )
                }
            }
        }
    }

    fun updateTemplate(id: Int, name: String, exerciseIds: List<String>) {
        viewModelScope.launch {
            val json = serializeExerciseIds(exerciseIds)
            val templateId = repository.insertTemplate(
                WorkoutTemplate(
                    id = id,
                    name = name,
                    exerciseIdsJson = json,
                    userId = activeUserId.value
                )
            ).toInt()

            // Fetch existing exercises to preserve sets if the exercise is still there
            val oldExercises = repository.getTemplateExercisesSync(templateId)
            val oldExercisesMap = oldExercises.associateBy { it.exerciseId }
            val oldSetsMap = oldExercises.associate { te ->
                te.exerciseId to repository.getTemplateSetsSync(te.id)
            }

            // Delete old records
            repository.deleteTemplateExercisesForTemplate(templateId)

            exerciseIds.forEachIndexed { idx, exId ->
                val oldTe = oldExercisesMap[exId]
                val customRest = oldTe?.restSeconds ?: 90
                val customNotes = oldTe?.notes
                val supersetId = oldTe?.supersetGroupId

                val teId = repository.insertTemplateExercise(
                    WorkoutTemplateExercise(
                        templateId = templateId,
                        exerciseId = exId,
                        position = idx + 1,
                        restSeconds = customRest,
                        notes = customNotes,
                        supersetGroupId = supersetId
                    )
                ).toInt()

                val oldSets = oldSetsMap[exId]
                if (oldSets != null && oldSets.isNotEmpty()) {
                    oldSets.forEachIndexed { sIdx, os ->
                        repository.insertTemplateSet(
                            WorkoutTemplateSet(
                                templateExerciseId = teId,
                                position = sIdx + 1,
                                setType = os.setType,
                                targetRepsMin = os.targetRepsMin,
                                targetRepsMax = os.targetRepsMax,
                                targetWeight = os.targetWeight,
                                targetRpe = os.targetRpe,
                                targetDurationSeconds = os.targetDurationSeconds,
                                targetDistance = os.targetDistance,
                                tempo = os.tempo,
                                notes = os.notes
                            )
                        )
                    }
                } else {
                    for (setNum in 1..3) {
                        repository.insertTemplateSet(
                            WorkoutTemplateSet(
                                templateExerciseId = teId,
                                position = setNum,
                                setType = "WORKING",
                                targetRepsMin = 8,
                                targetRepsMax = 10
                            )
                        )
                    }
                }
            }
        }
    }

    fun duplicateTemplate(template: WorkoutTemplate) {
        viewModelScope.launch {
            val oldTemplateExercises = repository.getTemplateExercisesSync(template.id)
            
            // Insert duplicate template
            val newTemplateId = repository.insertTemplate(
                WorkoutTemplate(
                    name = "${template.name} (Copy)",
                    exerciseIdsJson = template.exerciseIdsJson,
                    userId = activeUserId.value
                )
            ).toInt()

            // Duplicate associated exercises and sets
            for (te in oldTemplateExercises) {
                val oldSets = repository.getTemplateSetsSync(te.id)
                val newTeId = repository.insertTemplateExercise(
                    WorkoutTemplateExercise(
                        templateId = newTemplateId,
                        exerciseId = te.exerciseId,
                        position = te.position,
                        restSeconds = te.restSeconds,
                        notes = te.notes,
                        supersetGroupId = te.supersetGroupId
                    )
                ).toInt()

                for (ts in oldSets) {
                    repository.insertTemplateSet(
                        WorkoutTemplateSet(
                            templateExerciseId = newTeId,
                            position = ts.position,
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
                    )
                }
            }
        }
    }

    fun deleteTemplate(id: Int) {
        viewModelScope.launch {
            val template = repository.getTemplateSync(id) ?: return@launch
            val details = getTemplateDetails(id)
            lastDeletedTemplate = Pair(template, details)

            // Delete template sets
            val exercises = repository.getTemplateExercisesSync(id)
            for (ex in exercises) {
                repository.deleteTemplateSetsForExercise(ex.id)
            }
            // Delete template exercises
            repository.deleteTemplateExercisesForTemplate(id)
            // Delete template itself
            repository.deleteTemplate(id)

            showUndoSnackbar("Workout template '${template.name}' deleted") {
                undoDeleteTemplate()
            }
        }
    }

    suspend fun getTemplateDetails(templateId: Int): List<TemplateExerciseState> {
        val templateExercises = repository.getTemplateExercisesSync(templateId)
        return templateExercises.map { te ->
            val templateSets = repository.getTemplateSetsSync(te.id)
            TemplateExerciseState(
                id = te.id,
                exerciseId = te.exerciseId,
                restSeconds = te.restSeconds,
                notes = te.notes,
                supersetGroupId = te.supersetGroupId,
                sets = templateSets.map { ts ->
                    TemplateSetState(
                        id = ts.id,
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
            )
        }
    }

    fun saveTemplate(templateId: Int?, name: String, exercises: List<TemplateExerciseState>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            if (name.isBlank() || exercises.isEmpty()) return@launch
            for (ex in exercises) {
                if (ex.sets.isEmpty()) return@launch
            }

            val exerciseIdsJson = serializeExerciseIds(exercises.map { it.exerciseId })
            
            val finalTemplateId = if (templateId != null && templateId != 0) {
                repository.insertTemplate(
                    WorkoutTemplate(
                        id = templateId,
                        name = name,
                        exerciseIdsJson = exerciseIdsJson,
                        userId = activeUserId.value
                    )
                )
                templateId
            } else {
                val newId = repository.insertTemplate(
                    WorkoutTemplate(
                        name = name,
                        exerciseIdsJson = exerciseIdsJson,
                        userId = activeUserId.value
                    )
                )
                newId.toInt()
            }

            // Clear old exercises and sets
            val oldExercises = repository.getTemplateExercisesSync(finalTemplateId)
            for (oldEx in oldExercises) {
                repository.deleteTemplateSetsForExercise(oldEx.id)
            }
            repository.deleteTemplateExercisesForTemplate(finalTemplateId)

            // Insert new hierarchy
            exercises.forEachIndexed { exIndex, exState ->
                val templateExId = repository.insertTemplateExercise(
                    WorkoutTemplateExercise(
                        templateId = finalTemplateId,
                        exerciseId = exState.exerciseId,
                        position = exIndex + 1,
                        restSeconds = exState.restSeconds,
                        notes = exState.notes,
                        supersetGroupId = exState.supersetGroupId
                    )
                ).toInt()

                exState.sets.forEachIndexed { setIndex, setState ->
                    repository.insertTemplateSet(
                        WorkoutTemplateSet(
                            templateExerciseId = templateExId,
                            position = setIndex + 1,
                            setType = setState.setType,
                            targetRepsMin = setState.targetRepsMin,
                            targetRepsMax = setState.targetRepsMax,
                            targetWeight = setState.targetWeight,
                            targetRpe = setState.targetRpe,
                            targetDurationSeconds = setState.targetDurationSeconds,
                            targetDistance = setState.targetDistance,
                            tempo = setState.tempo,
                            notes = setState.notes
                        )
                    )
                }
            }
            onComplete()
        }
    }


    // Exercise Operations
    fun createCustomExercise(name: String, category: String) {
        viewModelScope.launch {
            val id = name.lowercase().replace(" ", "_").replace("[^a-z0-9_]".toRegex(), "")
            repository.insertExercise(
                Exercise(id = "custom_$id", name = name, category = category, isCustom = true)
            )
        }
    }

    fun deleteCustomExercise(id: String) {
        viewModelScope.launch {
            repository.deleteExercise(id)
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId) ?: return@launch
            val sets = repository.getSetsForSessionSync(sessionId)
            lastDeletedSession = Pair(session, sets)

            repository.deleteSession(sessionId)

            showUndoSnackbar("Workout session deleted") {
                undoDeleteSession()
            }
        }
    }


    // Serialization Helpers
    fun serializeExerciseIds(ids: List<String>): String {
        return "[" + ids.joinToString(",") { "\"$it\"" } + "]"
    }

    fun deserializeExerciseIds(json: String): List<String> {
        return json.trim('[', ']')
            .split(',')
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
    }

    // Backup & Restore
    fun exportData(): String = kotlinx.coroutines.runBlocking {
        val root = org.json.JSONObject()
        root.put("version", 3)

        val exercisesArray = org.json.JSONArray()
        exercises.value.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("category", it.category)
            obj.put("isCustom", it.isCustom)
            exercisesArray.put(obj)
        }
        root.put("exercises", exercisesArray)

        val templatesArray = org.json.JSONArray()
        templates.value.forEach { t ->
            val obj = org.json.JSONObject()
            obj.put("id", t.id)
            obj.put("name", t.name)
            obj.put("exerciseIdsJson", t.exerciseIdsJson)

            val exercisesList = repository.getTemplateExercisesSync(t.id)
            val nestedExArray = org.json.JSONArray()
            exercisesList.forEach { te ->
                val teObj = org.json.JSONObject()
                teObj.put("id", te.id)
                teObj.put("exerciseId", te.exerciseId)
                teObj.put("position", te.position)
                teObj.put("restSeconds", te.restSeconds)
                teObj.put("notes", te.notes ?: org.json.JSONObject.NULL)
                teObj.put("supersetGroupId", te.supersetGroupId ?: org.json.JSONObject.NULL)

                val setsList = repository.getTemplateSetsSync(te.id)
                val nestedSetsArray = org.json.JSONArray()
                setsList.forEach { ts ->
                    val tsObj = org.json.JSONObject()
                    tsObj.put("id", ts.id)
                    tsObj.put("position", ts.position)
                    tsObj.put("setType", ts.setType)
                    tsObj.put("targetRepsMin", ts.targetRepsMin ?: org.json.JSONObject.NULL)
                    tsObj.put("targetRepsMax", ts.targetRepsMax ?: org.json.JSONObject.NULL)
                    tsObj.put("targetWeight", ts.targetWeight?.toDouble() ?: org.json.JSONObject.NULL)
                    tsObj.put("targetRpe", ts.targetRpe ?: org.json.JSONObject.NULL)
                    tsObj.put("targetDurationSeconds", ts.targetDurationSeconds ?: org.json.JSONObject.NULL)
                    tsObj.put("targetDistance", ts.targetDistance?.toDouble() ?: org.json.JSONObject.NULL)
                    tsObj.put("tempo", ts.tempo ?: org.json.JSONObject.NULL)
                    tsObj.put("notes", ts.notes ?: org.json.JSONObject.NULL)
                    nestedSetsArray.put(tsObj)
                }
                teObj.put("sets", nestedSetsArray)
                nestedExArray.put(teObj)
            }
            obj.put("exercises", nestedExArray)
            templatesArray.put(obj)
        }
        root.put("workout_templates", templatesArray)

        val sessionsArray = org.json.JSONArray()
        sessions.value.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("templateId", it.templateId)
            obj.put("templateName", it.templateName)
            obj.put("startTime", it.startTime)
            obj.put("endTime", it.endTime)
            sessionsArray.put(obj)
        }
        root.put("workout_sessions", sessionsArray)

        val loggedSetsArray = org.json.JSONArray()
        allLoggedSets.value.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("sessionId", it.sessionId)
            obj.put("exerciseId", it.exerciseId)
            obj.put("setNumber", it.setNumber)
            obj.put("reps", it.reps)
            obj.put("weight", it.weight)
            obj.put("isCompleted", it.isCompleted)
            obj.put("rpe", it.rpe ?: org.json.JSONObject.NULL)
            
            obj.put("actualDuration", it.actualDuration ?: org.json.JSONObject.NULL)
            obj.put("actualDistance", it.actualDistance?.toDouble() ?: org.json.JSONObject.NULL)
            obj.put("setType", it.setType)
            obj.put("targetRepsMin", it.targetRepsMin ?: org.json.JSONObject.NULL)
            obj.put("targetRepsMax", it.targetRepsMax ?: org.json.JSONObject.NULL)
            obj.put("targetWeight", it.targetWeight?.toDouble() ?: org.json.JSONObject.NULL)
            obj.put("targetRpe", it.targetRpe ?: org.json.JSONObject.NULL)
            obj.put("targetDuration", it.targetDuration ?: org.json.JSONObject.NULL)
            obj.put("targetDistance", it.targetDistance?.toDouble() ?: org.json.JSONObject.NULL)
            obj.put("notes", it.notes ?: org.json.JSONObject.NULL)
            loggedSetsArray.put(obj)
        }
        root.put("logged_sets", loggedSetsArray)

        val weightsArray = org.json.JSONArray()
        bodyWeights.value.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("weight", it.weight)
            obj.put("date", it.date)
            obj.put("bodyFat", it.bodyFat ?: org.json.JSONObject.NULL)
            obj.put("leanMass", it.leanMass ?: org.json.JSONObject.NULL)
            obj.put("fatMass", it.fatMass ?: org.json.JSONObject.NULL)
            obj.put("bmi", it.bmi ?: org.json.JSONObject.NULL)
            weightsArray.put(obj)
        }
        root.put("body_weights", weightsArray)

        val tapeArray = org.json.JSONArray()
        tapeMeasurements.value.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("date", it.date)
            obj.put("chest", it.chest ?: org.json.JSONObject.NULL)
            obj.put("waist", it.waist ?: org.json.JSONObject.NULL)
            obj.put("hips", it.hips ?: org.json.JSONObject.NULL)
            obj.put("bicepLeft", it.bicepLeft ?: org.json.JSONObject.NULL)
            obj.put("bicepRight", it.bicepRight ?: org.json.JSONObject.NULL)
            obj.put("thighLeft", it.thighLeft ?: org.json.JSONObject.NULL)
            obj.put("thighRight", it.thighRight ?: org.json.JSONObject.NULL)
            tapeArray.put(obj)
        }
        root.put("tape_measurements", tapeArray)

        val settingsObj = org.json.JSONObject()
        settingsObj.put("is_metric", isMetric.value)
        settingsObj.put("theme", theme.value)
        settingsObj.put("keep_screen_awake", keepScreenAwake.value)
        settingsObj.put("default_rest_timer_duration", defaultRestTimerDuration.value)
        settingsObj.put("sound_on", soundOn.value)
        settingsObj.put("vibration_on", vibrationOn.value)
        settingsObj.put("default_warmup_sets", defaultWarmupSets.value)
        settingsObj.put("auto_complete_behavior", autoCompleteBehavior.value)
        settingsObj.put("auto_scroll", autoScroll.value)
        settingsObj.put("timer_preferences", timerPreferences.value)
        root.put("settings", settingsObj)

        root.toString(2)
    }

    fun exportDataToCsv(): String {
        val sb = java.lang.StringBuilder()
        sb.append("Date,Workout,Exercise,Category,Set Number,Set Type,Weight (kg),Reps,Completed,RPE,Target Weight,Target Reps,Target RPE,Duration (s),Distance,Notes\n")
        
        val sessionsMap = sessions.value.associateBy { it.id }
        val exercisesMap = exercises.value.associateBy { it.id }
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

        allLoggedSets.value.forEach { set ->
            val session = sessionsMap[set.sessionId]
            val exercise = exercisesMap[set.exerciseId]
            val dateStr = if (session != null) sdf.format(java.util.Date(session.startTime)) else "N/A"
            val workoutName = session?.templateName ?: "N/A"
            val exerciseName = exercise?.name ?: set.exerciseId
            val category = exercise?.category ?: "N/A"
            val weight = set.weight
            val reps = set.reps
            val completed = if (set.isCompleted) "Yes" else "No"
            val rpe = set.rpe?.toString() ?: ""
            val targetWeight = set.targetWeight?.toString() ?: ""
            val targetReps = if (set.targetRepsMin != null) "${set.targetRepsMin}-${set.targetRepsMax ?: set.targetRepsMin}" else ""
            val targetRpe = set.targetRpe?.toString() ?: ""
            val duration = set.actualDuration?.toString() ?: ""
            val distance = set.actualDistance?.toString() ?: ""
            val notes = set.notes ?: ""

            sb.append("\"$dateStr\",\"$workoutName\",\"$exerciseName\",\"$category\",${set.setNumber},\"${set.setType}\",$weight,${reps},\"$completed\",\"$rpe\",\"$targetWeight\",\"$targetReps\",\"$targetRpe\",\"$duration\",\"$distance\",\"$notes\"\n")
        }
        return sb.toString()
    }

    data class ImportResult(
    val sessionsImported: Int = 0,
    val templatesImported: Int = 0,
    val exercisesImported: Int = 0,
    val measurementsImported: Int = 0,
    val skippedDuplicates: Int = 0,
    val failedRecords: Int = 0
)

    fun importData(jsonStr: String, onSuccess: (ImportResult) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Validation: Ensure string is a valid JSON Object
                val root = try {
                    org.json.JSONObject(jsonStr)
                } catch (e: Exception) {
                    onError("Invalid JSON structure: ${e.localizedMessage}")
                    return@launch
                }

                var sessionsImported = 0
                var templatesImported = 0
                var exercisesImported = 0
                var measurementsImported = 0
                var skippedDuplicates = 0
                var failedRecords = 0

                val settingsObj = root.optJSONObject("settings")
                if (settingsObj != null) {
                    setMetric(settingsObj.optBoolean("is_metric", true))
                    setTheme(settingsObj.optString("theme", "system"))
                    setKeepScreenAwake(settingsObj.optBoolean("keep_screen_awake", false))
                    setDefaultRestTimerDuration(settingsObj.optInt("default_rest_timer_duration", 90))
                    setSoundOn(settingsObj.optBoolean("sound_on", true))
                    setVibrationOn(settingsObj.optBoolean("vibration_on", true))
                    setDefaultWarmupSets(settingsObj.optInt("default_warmup_sets", 0))
                    setAutoCompleteBehavior(settingsObj.optBoolean("auto_complete_behavior", true))
                    setAutoScroll(settingsObj.optBoolean("auto_scroll", true))
                    setTimerPreferences(settingsObj.optString("timer_preferences", "standard"))
                }

                // Import Exercises
                val exercisesArray = root.optJSONArray("exercises")
                if (exercisesArray != null) {
                    for (i in 0 until exercisesArray.length()) {
                        try {
                            val obj = exercisesArray.getJSONObject(i)
                            val id = obj.getString("id")
                            val name = obj.getString("name")
                            val category = obj.getString("category")
                            val isCustom = obj.optBoolean("isCustom", false)

                            // Validate exercise fields
                            if (id.isBlank() || name.isBlank() || category.isBlank()) {
                                failedRecords++
                                continue
                            }

                            if (repository.getExerciseById(id) == null) {
                                repository.insertExercise(Exercise(id, name, category, isCustom))
                                exercisesImported++
                            } else {
                                skippedDuplicates++
                            }
                        } catch (e: Exception) {
                            failedRecords++
                        }
                    }
                }

                // Import Workout Templates
                val templatesArray = root.optJSONArray("workout_templates")
                if (templatesArray != null) {
                    val existingTemplates = templates.value
                    for (i in 0 until templatesArray.length()) {
                        var insertedTemplateId: Int? = null
                        try {
                            val obj = templatesArray.getJSONObject(i)
                            val name = obj.getString("name")
                            val exerciseIdsJson = obj.getString("exerciseIdsJson")

                            if (name.isBlank()) {
                                failedRecords++
                                continue
                            }

                            // Conflict resolution: Save as Copy if duplicate name exists
                            var targetName = name
                            if (existingTemplates.any { it.name.lowercase() == name.lowercase() }) {
                                targetName = "$name (Imported)"
                            }

                            val templateId = repository.insertTemplate(
                                WorkoutTemplate(
                                    name = targetName,
                                    exerciseIdsJson = exerciseIdsJson,
                                    userId = activeUserId.value
                                )
                            ).toInt()
                            insertedTemplateId = templateId

                            val exercisesNested = obj.optJSONArray("exercises")
                            if (exercisesNested != null) {
                                for (eIdx in 0 until exercisesNested.length()) {
                                    val exObj = exercisesNested.getJSONObject(eIdx)
                                    val exerciseId = exObj.getString("exerciseId")
                                    val position = exObj.getInt("position")
                                    val restSeconds = exObj.optInt("restSeconds", 90)
                                    val notes = if (exObj.isNull("notes")) null else exObj.getString("notes")
                                    val supersetGroupId = if (exObj.isNull("supersetGroupId")) null else exObj.getString("supersetGroupId")

                                    val templateExId = repository.insertTemplateExercise(
                                        WorkoutTemplateExercise(
                                            templateId = templateId,
                                            exerciseId = exerciseId,
                                            position = position,
                                            restSeconds = restSeconds,
                                            notes = notes,
                                            supersetGroupId = supersetGroupId
                                        )
                                    ).toInt()

                                    val setsNested = exObj.optJSONArray("sets")
                                    if (setsNested != null) {
                                        for (sIdx in 0 until setsNested.length()) {
                                            val setObj = setsNested.getJSONObject(sIdx)
                                            repository.insertTemplateSet(
                                                WorkoutTemplateSet(
                                                    templateExerciseId = templateExId,
                                                    position = setObj.getInt("position"),
                                                    setType = setObj.getString("setType"),
                                                    targetRepsMin = if (setObj.isNull("targetRepsMin")) null else setObj.getInt("targetRepsMin"),
                                                    targetRepsMax = if (setObj.isNull("targetRepsMax")) null else setObj.getInt("targetRepsMax"),
                                                    targetWeight = if (setObj.isNull("targetWeight")) null else setObj.getDouble("targetWeight").toFloat(),
                                                    targetRpe = if (setObj.isNull("targetRpe")) null else setObj.getInt("targetRpe"),
                                                    targetDurationSeconds = if (setObj.isNull("targetDurationSeconds")) null else setObj.getInt("targetDurationSeconds"),
                                                    targetDistance = if (setObj.isNull("targetDistance")) null else setObj.getDouble("targetDistance").toFloat(),
                                                    tempo = if (setObj.isNull("tempo")) null else setObj.getString("tempo"),
                                                    notes = if (setObj.isNull("notes")) null else setObj.getString("notes")
                                                )
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Fallback exercise generation
                                val jsonArray = org.json.JSONArray(exerciseIdsJson)
                                for (exIdx in 0 until jsonArray.length()) {
                                    val exerciseId = jsonArray.getString(exIdx)
                                    val position = exIdx + 1
                                    val templateExId = repository.insertTemplateExercise(
                                        WorkoutTemplateExercise(
                                            templateId = templateId,
                                            exerciseId = exerciseId,
                                            position = position,
                                            restSeconds = 90
                                        )
                                    ).toInt()

                                    for (setNum in 1..3) {
                                        repository.insertTemplateSet(
                                            WorkoutTemplateSet(
                                                templateExerciseId = templateExId,
                                                position = setNum,
                                                setType = "WORKING",
                                                targetRepsMin = 8,
                                                targetRepsMax = 10
                                            )
                                        )
                                    }
                                }
                            }
                            templatesImported++
                        } catch (e: Exception) {
                            failedRecords++
                            // Partial import prevention: Rollback this template if it failed midway
                            if (insertedTemplateId != null) {
                                try {
                                    val exercises = repository.getTemplateExercisesSync(insertedTemplateId)
                                    for (ex in exercises) {
                                        repository.deleteTemplateSetsForExercise(ex.id)
                                    }
                                    repository.deleteTemplateExercisesForTemplate(insertedTemplateId)
                                    repository.deleteTemplate(insertedTemplateId)
                                } catch (rollbackEx: Exception) {
                                    rollbackEx.printStackTrace()
                                }
                            }
                        }
                    }
                }

                // Import Body Weights with duplicate prevention
                val weightsArray = root.optJSONArray("body_weights")
                if (weightsArray != null) {
                    val currentWeights = repository.getBodyWeightsForUser(activeUserId.value).firstOrNull() ?: emptyList()
                    for (i in 0 until weightsArray.length()) {
                        try {
                            val obj = weightsArray.getJSONObject(i)
                            val weight = obj.getDouble("weight").toFloat()
                            val date = obj.getLong("date")

                            if (weight <= 0) {
                                failedRecords++
                                continue
                            }

                            // Duplicate prevention
                            if (currentWeights.any { it.date == date }) {
                                skippedDuplicates++
                                continue
                            }

                            val bodyFat = if (obj.isNull("bodyFat")) null else obj.getDouble("bodyFat").toFloat()
                            val leanMass = if (obj.isNull("leanMass")) null else obj.getDouble("leanMass").toFloat()
                            val fatMass = if (obj.isNull("fatMass")) null else obj.getDouble("fatMass").toFloat()
                            val bmi = if (obj.isNull("bmi")) null else obj.getDouble("bmi").toFloat()

                            repository.insertBodyWeight(
                                BodyWeight(
                                    weight = weight,
                                    date = date,
                                    bodyFat = bodyFat,
                                    leanMass = leanMass,
                                    fatMass = fatMass,
                                    bmi = bmi,
                                    userId = activeUserId.value
                                )
                            )
                            measurementsImported++
                        } catch (e: Exception) {
                            failedRecords++
                        }
                    }
                }

                // Import Tape Measurements with duplicate prevention
                val tapeArray = root.optJSONArray("tape_measurements")
                if (tapeArray != null) {
                    val currentTapes = repository.getTapeMeasurementsForUser(activeUserId.value).firstOrNull() ?: emptyList()
                    for (i in 0 until tapeArray.length()) {
                        try {
                            val obj = tapeArray.getJSONObject(i)
                            val date = obj.getLong("date")

                            // Duplicate prevention
                            if (currentTapes.any { it.date == date }) {
                                skippedDuplicates++
                                continue
                            }

                            val chest = if (obj.isNull("chest")) null else obj.getDouble("chest").toFloat()
                            val waist = if (obj.isNull("waist")) null else obj.getDouble("waist").toFloat()
                            val hips = if (obj.isNull("hips")) null else obj.getDouble("hips").toFloat()
                            val bicepLeft = if (obj.isNull("bicepLeft")) null else obj.getDouble("bicepLeft").toFloat()
                            val bicepRight = if (obj.isNull("bicepRight")) null else obj.getDouble("bicepRight").toFloat()
                            val thighLeft = if (obj.isNull("thighLeft")) null else obj.getDouble("thighLeft").toFloat()
                            val thighRight = if (obj.isNull("thighRight")) null else obj.getDouble("thighRight").toFloat()

                            repository.insertTapeMeasurement(
                                TapeMeasurement(
                                    date = date,
                                    chest = chest,
                                    waist = waist,
                                    hips = hips,
                                    bicepLeft = bicepLeft,
                                    bicepRight = bicepRight,
                                    thighLeft = thighLeft,
                                    thighRight = thighRight,
                                    userId = activeUserId.value
                                )
                            )
                            measurementsImported++
                        } catch (e: Exception) {
                            failedRecords++
                        }
                    }
                }

                // Import Workout Sessions with duplicate prevention
                val sessionsArray = root.optJSONArray("workout_sessions")
                val setsArray = root.optJSONArray("logged_sets")
                if (sessionsArray != null && setsArray != null) {
                    val setsList = mutableListOf<org.json.JSONObject>()
                    for (j in 0 until setsArray.length()) {
                        setsList.add(setsArray.getJSONObject(j))
                    }

                    for (i in 0 until sessionsArray.length()) {
                        var insertedSessionId: Int? = null
                        try {
                            val sObj = sessionsArray.getJSONObject(i)
                            val oldSessionId = sObj.getInt("id")
                            val templateId = if (sObj.isNull("templateId")) null else sObj.getInt("templateId")
                            val templateName = sObj.getString("templateName")
                            val realStartTime = sObj.optLong("startTime")
                            val realEndTime = sObj.optLong("endTime")

                            if (templateName.isBlank()) {
                                failedRecords++
                                continue
                            }

                            // Duplicate prevention: same start time and template name
                            if (sessions.value.any { it.startTime == realStartTime && it.templateName == templateName }) {
                                skippedDuplicates++
                                continue
                            }

                            val newSessionId = repository.insertSession(
                                WorkoutSession(
                                    templateId = templateId,
                                    templateName = templateName,
                                    startTime = realStartTime,
                                    endTime = realEndTime,
                                    userId = activeUserId.value
                                )
                            ).toInt()
                            insertedSessionId = newSessionId

                            val matchingSets = setsList.filter { it.getInt("sessionId") == oldSessionId }
                            val setsToInsert = matchingSets.map { setObj ->
                                val rpeVal = if (setObj.isNull("rpe")) null else setObj.getInt("rpe")
                                val actualDuration = if (setObj.has("actualDuration") && !setObj.isNull("actualDuration")) setObj.getInt("actualDuration") else null
                                val actualDistance = if (setObj.has("actualDistance") && !setObj.isNull("actualDistance")) setObj.getDouble("actualDistance").toFloat() else null
                                val setType = if (setObj.has("setType")) setObj.getString("setType") else "WORKING"
                                val targetRepsMin = if (setObj.has("targetRepsMin") && !setObj.isNull("targetRepsMin")) setObj.getInt("targetRepsMin") else null
                                val targetRepsMax = if (setObj.has("targetRepsMax") && !setObj.isNull("targetRepsMax")) setObj.getInt("targetRepsMax") else null
                                val targetWeight = if (setObj.has("targetWeight") && !setObj.isNull("targetWeight")) setObj.getDouble("targetWeight").toFloat() else null
                                val targetRpe = if (setObj.has("targetRpe") && !setObj.isNull("targetRpe")) setObj.getInt("targetRpe") else null
                                val targetDuration = if (setObj.has("targetDuration") && !setObj.isNull("targetDuration")) setObj.getInt("targetDuration") else null
                                val targetDistance = if (setObj.has("targetDistance") && !setObj.isNull("targetDistance")) setObj.getDouble("targetDistance").toFloat() else null
                                val notes = if (setObj.has("notes") && !setObj.isNull("notes")) setObj.getString("notes") else null

                                LoggedSet(
                                    sessionId = newSessionId,
                                    exerciseId = setObj.getString("exerciseId"),
                                    setNumber = setObj.getInt("setNumber"),
                                    reps = setObj.getInt("reps"),
                                    weight = setObj.getDouble("weight").toFloat(),
                                    isCompleted = setObj.optBoolean("isCompleted", true),
                                    rpe = rpeVal,
                                    actualDuration = actualDuration,
                                    actualDistance = actualDistance,
                                    setType = setType,
                                    targetRepsMin = targetRepsMin,
                                    targetRepsMax = targetRepsMax,
                                    targetWeight = targetWeight,
                                    targetRpe = targetRpe,
                                    targetDuration = targetDuration,
                                    targetDistance = targetDistance,
                                    notes = notes
                                )
                            }
                            if (setsToInsert.isNotEmpty()) {
                                repository.insertLoggedSets(setsToInsert)
                            }
                            sessionsImported++
                        } catch (e: Exception) {
                            failedRecords++
                            // Partial import prevention
                            if (insertedSessionId != null) {
                                try {
                                    repository.deleteSession(insertedSessionId)
                                } catch (rollbackEx: Exception) {
                                    rollbackEx.printStackTrace()
                                }
                            }
                        }
                    }
                }

                onSuccess(
                    ImportResult(
                        sessionsImported = sessionsImported,
                        templatesImported = templatesImported,
                        exercisesImported = exercisesImported,
                        measurementsImported = measurementsImported,
                        skippedDuplicates = skippedDuplicates,
                        failedRecords = failedRecords
                    )
                )
            } catch (e: Exception) {
                onError("Failed to parse backup file: ${e.localizedMessage ?: "Unknown format error"}")
            }
        }
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
