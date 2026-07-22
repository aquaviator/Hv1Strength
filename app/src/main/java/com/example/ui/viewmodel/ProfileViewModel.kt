package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: StrengthRepository,
    private val context: Context,
    private val authViewModel: AuthViewModel
) : ViewModel() {

    // Settings StateFlows using modern database-backed UserPreferencesRepository
    val preferencesRepository = UserPreferencesRepository(repository.dao)

    val userPreferencesFlow = preferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val isMetric = preferencesRepository.userPreferencesFlow
        .map { it.isMetric }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val theme = preferencesRepository.userPreferencesFlow
        .map { it.theme }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    val keepScreenAwake = preferencesRepository.userPreferencesFlow
        .map { it.keepScreenAwake }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val defaultRestTimerDuration = preferencesRepository.userPreferencesFlow
        .map { it.defaultRestTimerDuration }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 90)

    val soundOn = preferencesRepository.userPreferencesFlow
        .map { it.soundOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val vibrationOn = preferencesRepository.userPreferencesFlow
        .map { it.vibrationOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val defaultWarmupSets = preferencesRepository.userPreferencesFlow
        .map { it.defaultWarmupSets }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val autoCompleteBehavior = preferencesRepository.userPreferencesFlow
        .map { it.autoCompleteBehavior }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoScroll = preferencesRepository.userPreferencesFlow
        .map { it.autoScroll }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val timerPreferences = preferencesRepository.userPreferencesFlow
        .map { it.timerPreferences }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "standard")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeUserProfile: StateFlow<UserProfile?> = authViewModel.activeUserId.flatMapLatest { userId ->
        repository.getUserProfileFlow(userId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val sharedPrefs = context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE)
    val simulateTrialExpired = MutableStateFlow(sharedPrefs.getBoolean("simulate_trial_expired", false))

    fun setSimulateTrialExpired(expired: Boolean) {
        sharedPrefs.edit().putBoolean("simulate_trial_expired", expired).apply()
        simulateTrialExpired.value = expired
    }

    val isTrialExpired: StateFlow<Boolean> = activeUserProfile.combine(simulateTrialExpired) { profile, simulate ->
        if (simulate) {
            true
        } else if (profile == null) {
            false
        } else {
            val joinedAt = profile.createdAt
            val thirtyDaysInMillis = 30L * 24L * 60L * 60L * 1000L
            val expirationTime = joinedAt + thirtyDaysInMillis
            System.currentTimeMillis() > expirationTime
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Body Weight State
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bodyWeights: StateFlow<List<BodyWeight>> = authViewModel.activeUserId.flatMapLatest { userId ->
        repository.getBodyWeightsForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tape Measurements State
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val tapeMeasurements: StateFlow<List<TapeMeasurement>> = authViewModel.activeUserId.flatMapLatest { userId ->
        repository.getTapeMeasurementsForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Setters
    fun setMetric(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMetric(value)
        }
    }
    fun setTheme(value: String) {
        viewModelScope.launch {
            preferencesRepository.setTheme(value)
        }
    }
    fun setKeepScreenAwake(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setKeepScreenAwake(value)
        }
    }
    fun setDefaultRestTimerDuration(value: Int) {
        viewModelScope.launch {
            preferencesRepository.setDefaultRestTimerDuration(value)
        }
    }
    fun setSoundOn(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSoundOn(value)
        }
    }
    fun setVibrationOn(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setVibrationOn(value)
        }
    }
    fun setDefaultWarmupSets(value: Int) {
        viewModelScope.launch {
            preferencesRepository.setDefaultWarmupSets(value)
        }
    }
    fun setAutoCompleteBehavior(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoCompleteBehavior(value)
        }
    }
    fun setAutoScroll(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoScroll(value)
        }
    }
    fun setTimerPreferences(value: String) {
        viewModelScope.launch {
            preferencesRepository.setTimerPreferences(value)
        }
    }

    fun updateUserProfileBio(displayName: String? = null, dob: String, sex: String, experience: String) {
        viewModelScope.launch {
            val currentProfile = activeUserProfile.value ?: (authViewModel.authState.value as? AuthState.Authenticated)?.profile ?: return@launch
            val updated = currentProfile.copy(
                displayName = if (!displayName.isNullOrBlank()) displayName else currentProfile.displayName,
                dateOfBirth = dob,
                sex = sex,
                trainingExperience = experience,
                updatedAt = System.currentTimeMillis()
            )
            repository.insertUserProfile(updated)
            authViewModel.authRepository.signInWithGoogle(
                idToken = updated.id,
                displayName = updated.displayName,
                email = updated.email,
                photoUrl = updated.photoUrl
            )
        }
    }

    fun logBodyWeight(weight: Float, bodyFat: Float?, timestamp: Long = System.currentTimeMillis(), onUndoBackup: (BodyWeight) -> Unit) {
        viewModelScope.launch {
            val userId = authViewModel.activeUserId.value
            val item = BodyWeight(
                userId = userId,
                weight = weight,
                bodyFat = bodyFat,
                date = timestamp
            )
            repository.insertBodyWeight(item)
            onUndoBackup(item)
        }
    }

    fun deleteBodyWeight(id: Int, onUndoBackup: (BodyWeight) -> Unit) {
        viewModelScope.launch {
            val item = repository.getBodyWeightById(id)
            if (item != null) {
                repository.deleteBodyWeight(id)
                onUndoBackup(item)
            }
        }
    }

    fun logTapeMeasurement(
        chest: Float?, waist: Float?, hips: Float?,
        bicepLeft: Float?, bicepRight: Float?,
        thighLeft: Float?, thighRight: Float?,
        timestamp: Long = System.currentTimeMillis(),
        onUndoBackup: (TapeMeasurement) -> Unit
    ) {
        viewModelScope.launch {
            val userId = authViewModel.activeUserId.value
            val item = TapeMeasurement(
                userId = userId,
                chest = chest, waist = waist, hips = hips,
                bicepLeft = bicepLeft, bicepRight = bicepRight,
                thighLeft = thighLeft, thighRight = thighRight,
                date = timestamp
            )
            repository.insertTapeMeasurement(item)
            onUndoBackup(item)
        }
    }

    fun deleteTapeMeasurement(id: Int, onUndoBackup: (TapeMeasurement) -> Unit) {
        viewModelScope.launch {
            val item = repository.getTapeMeasurementById(id)
            if (item != null) {
                repository.deleteTapeMeasurement(id)
                onUndoBackup(item)
            }
        }
    }

    // Backup & Restore
    suspend fun exportData(): String {
        val root = org.json.JSONObject()
        root.put("version", 3)
        val uid = authViewModel.activeUserId.value

        val exercisesArray = org.json.JSONArray()
        repository.allExercises.first().forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("category", it.category)
            obj.put("isCustom", it.isCustom)
            exercisesArray.put(obj)
        }
        root.put("exercises", exercisesArray)

        val templatesArray = org.json.JSONArray()
        repository.getTemplatesForUser(uid).first().forEach { t ->
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
                    tsObj.put("targetWeight", ts.targetWeight ?: org.json.JSONObject.NULL)
                    tsObj.put("targetRpe", ts.targetRpe ?: org.json.JSONObject.NULL)
                    tsObj.put("targetDurationSeconds", ts.targetDurationSeconds ?: org.json.JSONObject.NULL)
                    tsObj.put("targetDistance", ts.targetDistance ?: org.json.JSONObject.NULL)
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
        repository.getSessionsForUser(uid).first().forEach {
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
        repository.getLoggedSetsForUser(uid).first().forEach {
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
            obj.put("actualDistance", it.actualDistance ?: org.json.JSONObject.NULL)
            obj.put("setType", it.setType)
            obj.put("targetRepsMin", it.targetRepsMin ?: org.json.JSONObject.NULL)
            obj.put("targetRepsMax", it.targetRepsMax ?: org.json.JSONObject.NULL)
            obj.put("targetWeight", it.targetWeight ?: org.json.JSONObject.NULL)
            obj.put("targetRpe", it.targetRpe ?: org.json.JSONObject.NULL)
            obj.put("targetDuration", it.targetDuration ?: org.json.JSONObject.NULL)
            obj.put("targetDistance", it.targetDistance ?: org.json.JSONObject.NULL)
            obj.put("notes", it.notes ?: org.json.JSONObject.NULL)
            loggedSetsArray.put(obj)
        }
        root.put("logged_sets", loggedSetsArray)

        val weightsArray = org.json.JSONArray()
        repository.getBodyWeightsForUser(uid).first().forEach {
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
        repository.getTapeMeasurementsForUser(uid).first().forEach {
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

        return root.toString(2)
    }

    suspend fun exportDataToCsv(): String {
        val uid = authViewModel.activeUserId.value
        val sb = java.lang.StringBuilder()
        sb.append("Date,Workout,Exercise,Category,Set Number,Set Type,Weight (kg),Reps,Completed,RPE,Target Weight,Target Reps,Target RPE,Duration (s),Distance,Notes\n")

        val sessionsList = repository.getSessionsForUser(uid).first()
        val sessionsMap = sessionsList.associateBy { it.id }

        val exercisesList = repository.allExercises.first()
        val exercisesMap = exercisesList.associateBy { it.id }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

        val allLoggedSetsList = repository.getLoggedSetsForUser(uid).first()
        allLoggedSetsList.forEach { set ->
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
            val notes = set.notes?.replace(",", ";") ?: ""

            sb.append("$dateStr,$workoutName,$exerciseName,$category,${set.setNumber},${set.setType},$weight,$reps,$completed,$rpe,$targetWeight,$targetReps,$targetRpe,$duration,$distance,$notes\n")
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

                val uid = authViewModel.activeUserId.value

                // 1. Restore Custom Exercises
                if (root.has("exercises")) {
                    val arr = root.getJSONArray("exercises")
                    for (i in 0 until arr.length()) {
                        try {
                            val obj = arr.getJSONObject(i)
                            val isCustom = obj.optBoolean("isCustom", false)
                            if (isCustom) {
                                val id = obj.getString("id")
                                val existing = repository.getExerciseById(id)
                                if (existing == null) {
                                    repository.insertExercise(
                                        Exercise(
                                            id = id,
                                            name = obj.getString("name"),
                                            category = obj.getString("category"),
                                            isCustom = true,
                                            humanUserId = uid
                                        )
                                    )
                                    exercisesImported++
                                } else {
                                    skippedDuplicates++
                                }
                            }
                        } catch (e: Exception) {
                            failedRecords++
                        }
                    }
                }

                // 2. Restore Workout Templates
                if (root.has("workout_templates")) {
                    val arr = root.getJSONArray("workout_templates")
                    for (i in 0 until arr.length()) {
                        try {
                            val obj = arr.getJSONObject(i)
                            val name = obj.getString("name")
                            val exerciseIdsJson = obj.optString("exerciseIdsJson", "[]")

                            val tId = repository.insertTemplate(
                                WorkoutTemplate(
                                    name = name,
                                    exerciseIdsJson = exerciseIdsJson,
                                    userId = uid
                                )
                            ).toInt()
                            templatesImported++

                            if (obj.has("exercises")) {
                                val exArr = obj.getJSONArray("exercises")
                                for (j in 0 until exArr.length()) {
                                    val teObj = exArr.getJSONObject(j)
                                    val teId = repository.insertTemplateExercise(
                                        WorkoutTemplateExercise(
                                            templateId = tId,
                                            exerciseId = teObj.getString("exerciseId"),
                                            position = teObj.getInt("position"),
                                            restSeconds = teObj.optInt("restSeconds", 90),
                                            notes = if (teObj.isNull("notes")) null else teObj.getString("notes"),
                                            supersetGroupId = if (teObj.isNull("supersetGroupId")) null else teObj.getString("supersetGroupId")
                                        )
                                    ).toInt()

                                    if (teObj.has("sets")) {
                                        val setsArr = teObj.getJSONArray("sets")
                                        val setsToInsert = mutableListOf<WorkoutTemplateSet>()
                                        for (k in 0 until setsArr.length()) {
                                            val tsObj = setsArr.getJSONObject(k)
                                            setsToInsert.add(
                                                WorkoutTemplateSet(
                                                    templateExerciseId = teId,
                                                    position = tsObj.getInt("position"),
                                                    setType = tsObj.optString("setType", "WORKING"),
                                                    targetRepsMin = if (tsObj.isNull("targetRepsMin")) null else tsObj.getInt("targetRepsMin"),
                                                    targetRepsMax = if (tsObj.isNull("targetRepsMax")) null else tsObj.getInt("targetRepsMax"),
                                                    targetWeight = if (tsObj.isNull("targetWeight")) null else tsObj.getDouble("targetWeight").toFloat(),
                                                    targetRpe = if (tsObj.isNull("targetRpe")) null else tsObj.getInt("targetRpe"),
                                                    targetDurationSeconds = if (tsObj.isNull("targetDurationSeconds")) null else tsObj.getInt("targetDurationSeconds"),
                                                    targetDistance = if (tsObj.isNull("targetDistance")) null else tsObj.getDouble("targetDistance").toFloat(),
                                                    tempo = if (tsObj.isNull("tempo")) null else tsObj.getString("tempo"),
                                                    notes = if (tsObj.isNull("notes")) null else tsObj.getString("notes")
                                                )
                                            )
                                        }
                                        repository.insertTemplateSets(setsToInsert)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            failedRecords++
                        }
                    }
                }

                // 3. Restore Workout Sessions
                val oldToNewSessionIdMap = mutableMapOf<Int, Int>()
                if (root.has("workout_sessions")) {
                    val arr = root.getJSONArray("workout_sessions")
                    for (i in 0 until arr.length()) {
                        try {
                            val obj = arr.getJSONObject(i)
                            val oldId = obj.getInt("id")
                            val templateId = if (obj.isNull("templateId")) null else obj.getInt("templateId")
                            val newId = repository.insertSession(
                                WorkoutSession(
                                    templateId = templateId,
                                    templateName = obj.getString("templateName"),
                                    startTime = obj.getLong("startTime"),
                                    endTime = obj.getLong("endTime"),
                                    userId = uid
                                )
                            ).toInt()
                            oldToNewSessionIdMap[oldId] = newId
                            sessionsImported++
                        } catch (e: Exception) {
                            failedRecords++
                        }
                    }
                }

                // 4. Restore Logged Sets
                if (root.has("logged_sets")) {
                    val arr = root.getJSONArray("logged_sets")
                    val setsToInsert = mutableListOf<LoggedSet>()
                    for (i in 0 until arr.length()) {
                        try {
                            val obj = arr.getJSONObject(i)
                            val oldSessionId = obj.getInt("sessionId")
                            val newSessionId = oldToNewSessionIdMap[oldSessionId] ?: continue

                            setsToInsert.add(
                                LoggedSet(
                                    sessionId = newSessionId,
                                    exerciseId = obj.getString("exerciseId"),
                                    setNumber = obj.getInt("setNumber"),
                                    reps = obj.getInt("reps"),
                                    weight = obj.getDouble("weight").toFloat(),
                                    isCompleted = obj.getBoolean("isCompleted"),
                                    rpe = if (obj.isNull("rpe")) null else obj.getInt("rpe"),
                                    actualDuration = if (obj.isNull("actualDuration")) null else obj.getInt("actualDuration"),
                                    actualDistance = if (obj.isNull("actualDistance")) null else obj.getDouble("actualDistance").toFloat(),
                                    setType = obj.optString("setType", "WORKING"),
                                    targetRepsMin = if (obj.isNull("targetRepsMin")) null else obj.getInt("targetRepsMin"),
                                    targetRepsMax = if (obj.isNull("targetRepsMax")) null else obj.getInt("targetRepsMax"),
                                    targetWeight = if (obj.isNull("targetWeight")) null else obj.getDouble("targetWeight").toFloat(),
                                    targetRpe = if (obj.isNull("targetRpe")) null else obj.getInt("targetRpe"),
                                    targetDuration = if (obj.isNull("targetDuration")) null else obj.getInt("targetDuration"),
                                    targetDistance = if (obj.isNull("targetDistance")) null else obj.getDouble("targetDistance").toFloat(),
                                    notes = if (obj.isNull("notes")) null else obj.getString("notes")
                                )
                            )
                        } catch (e: Exception) {
                            failedRecords++
                        }
                    }
                    if (setsToInsert.isNotEmpty()) {
                        repository.insertLoggedSets(setsToInsert)
                    }
                }

                // 5. Restore Body Weights
                if (root.has("body_weights")) {
                    val arr = root.getJSONArray("body_weights")
                    for (i in 0 until arr.length()) {
                        try {
                            val obj = arr.getJSONObject(i)
                            repository.insertBodyWeight(
                                BodyWeight(
                                    userId = uid,
                                    weight = obj.getDouble("weight").toFloat(),
                                    bodyFat = if (obj.isNull("bodyFat")) null else obj.getDouble("bodyFat").toFloat(),
                                    date = obj.getLong("date")
                                )
                            )
                            measurementsImported++
                        } catch (e: Exception) {
                            failedRecords++
                        }
                    }
                }

                // 6. Restore Tape Measurements
                if (root.has("tape_measurements")) {
                    val arr = root.getJSONArray("tape_measurements")
                    for (i in 0 until arr.length()) {
                        try {
                            val obj = arr.getJSONObject(i)
                            repository.insertTapeMeasurement(
                                TapeMeasurement(
                                    userId = uid,
                                    chest = if (obj.isNull("chest")) null else obj.getDouble("chest").toFloat(),
                                    bicepLeft = if (obj.isNull("bicepLeft")) null else obj.getDouble("bicepLeft").toFloat(),
                                    bicepRight = if (obj.isNull("bicepRight")) null else obj.getDouble("bicepRight").toFloat(),
                                    waist = if (obj.isNull("waist")) null else obj.getDouble("waist").toFloat(),
                                    hips = if (obj.isNull("hips")) null else obj.getDouble("hips").toFloat(),
                                    thighLeft = if (obj.isNull("thighLeft")) null else obj.getDouble("thighLeft").toFloat(),
                                    thighRight = if (obj.isNull("thighRight")) null else obj.getDouble("thighRight").toFloat(),
                                    date = obj.getLong("date")
                                )
                            )
                            measurementsImported++
                        } catch (e: Exception) {
                            failedRecords++
                        }
                    }
                }

                // 7. Restore settings if available
                if (root.has("settings")) {
                    val sObj = root.getJSONObject("settings")
                    setMetric(sObj.optBoolean("is_metric", true))
                    setTheme(sObj.optString("theme", "system"))
                    setKeepScreenAwake(sObj.optBoolean("keep_screen_awake", false))
                    setDefaultRestTimerDuration(sObj.optInt("default_rest_timer_duration", 90))
                    setSoundOn(sObj.optBoolean("sound_on", true))
                    setVibrationOn(sObj.optBoolean("vibration_on", true))
                    setDefaultWarmupSets(sObj.optInt("default_warmup_sets", 0))
                    setAutoCompleteBehavior(sObj.optBoolean("auto_complete_behavior", true))
                    setAutoScroll(sObj.optBoolean("auto_scroll", true))
                    setTimerPreferences(sObj.optString("timer_preferences", "standard"))
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
