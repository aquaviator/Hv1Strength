package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.viewmodel.StrengthViewModel.TemplateExerciseState
import com.example.ui.viewmodel.StrengthViewModel.TemplateSetState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RoutineViewModel(
    private val repository: StrengthRepository,
    private val context: Context,
    private val authViewModel: AuthViewModel
) : ViewModel() {

    private val prefs = context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE)

    // Exercises State
    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Templates State
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<WorkoutTemplate>> = authViewModel.activeUserId.flatMapLatest { userId ->
        repository.getTemplatesForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun createTemplate(name: String, exerciseIds: List<String>) {
        viewModelScope.launch {
            val userId = authViewModel.activeUserId.value
            val json = serializeExerciseIds(exerciseIds)
            repository.insertTemplate(WorkoutTemplate(name = name, exerciseIdsJson = json, userId = userId))
        }
    }

    fun updateTemplate(id: Int, name: String, exerciseIds: List<String>) {
        viewModelScope.launch {
            val userId = authViewModel.activeUserId.value
            val json = serializeExerciseIds(exerciseIds)
            repository.insertTemplate(WorkoutTemplate(id = id, name = name, exerciseIdsJson = json, userId = userId))
        }
    }

    fun duplicateTemplate(template: WorkoutTemplate) {
        viewModelScope.launch {
            val userId = authViewModel.activeUserId.value
            val exercisesDetails = repository.getTemplateExercisesSync(template.id)
            val newTemplateId = repository.insertTemplate(
                WorkoutTemplate(
                    name = "${template.name} (Copy)",
                    exerciseIdsJson = template.exerciseIdsJson,
                    userId = userId
                )
            ).toInt()
            
            for (te in exercisesDetails) {
                val newTeId = repository.insertTemplateExercise(
                    WorkoutTemplateExercise(
                        templateId = newTemplateId,
                        exerciseId = te.exerciseId,
                        position = te.position,
                        restSeconds = te.restSeconds ?: 90,
                        notes = te.notes,
                        supersetGroupId = te.supersetGroupId
                    )
                ).toInt()
                
                val sets = repository.getTemplateSetsSync(te.id)
                val setsToInsert = sets.map { ts ->
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
                }
                repository.insertTemplateSets(setsToInsert)
            }
        }
    }

    fun deleteTemplate(id: Int, onUndoBackup: (WorkoutTemplate, List<TemplateExerciseState>) -> Unit) {
        viewModelScope.launch {
            val template = repository.getTemplateSync(id)
            if (template != null) {
                val details = getTemplateDetails(id)
                repository.deleteTemplate(id)
                onUndoBackup(template, details)
            }
        }
    }

    suspend fun getTemplateDetails(templateId: Int): List<TemplateExerciseState> {
        val list = mutableListOf<TemplateExerciseState>()
        val dbExercises = repository.getTemplateExercisesSync(templateId)
        for (te in dbExercises) {
            val dbSets = repository.getTemplateSetsSync(te.id)
            list.add(
                TemplateExerciseState(
                    id = te.id,
                    exerciseId = te.exerciseId,
                    restSeconds = te.restSeconds ?: 90,
                    notes = te.notes,
                    supersetGroupId = te.supersetGroupId,
                    sets = dbSets.map { ts ->
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
            )
        }
        return list
    }

    fun saveTemplate(templateId: Int?, name: String, exercises: List<TemplateExerciseState>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val userId = authViewModel.activeUserId.value
            val exerciseIds = exercises.map { it.exerciseId }
            val json = serializeExerciseIds(exerciseIds)
            
            val tId = if (templateId != null && templateId != 0) {
                val existing = repository.getTemplateSync(templateId)
                if (existing != null) {
                    repository.insertTemplate(existing.copy(name = name, exerciseIdsJson = json))
                    templateId
                } else {
                    repository.insertTemplate(WorkoutTemplate(name = name, exerciseIdsJson = json, userId = userId)).toInt()
                }
            } else {
                repository.insertTemplate(WorkoutTemplate(name = name, exerciseIdsJson = json, userId = userId)).toInt()
            }
            
            val oldExercises = repository.getTemplateExercisesSync(tId)
            oldExercises.forEach {
                repository.deleteTemplateExerciseById(it.id)
            }
            
            exercises.forEachIndexed { index, state ->
                val teId = repository.insertTemplateExercise(
                    WorkoutTemplateExercise(
                        templateId = tId,
                        exerciseId = state.exerciseId,
                        position = index,
                        restSeconds = state.restSeconds,
                        notes = state.notes,
                        supersetGroupId = state.supersetGroupId
                    )
                ).toInt()
                
                val setsToInsert = state.sets.mapIndexed { idx, ss ->
                    WorkoutTemplateSet(
                        templateExerciseId = teId,
                        position = idx + 1,
                        setType = ss.setType,
                        targetRepsMin = ss.targetRepsMin,
                        targetRepsMax = ss.targetRepsMax,
                        targetWeight = ss.targetWeight,
                        targetRpe = ss.targetRpe,
                        targetDurationSeconds = ss.targetDurationSeconds,
                        targetDistance = ss.targetDistance,
                        tempo = ss.tempo,
                        notes = ss.notes
                    )
                }
                repository.insertTemplateSets(setsToInsert)
            }
            onComplete()
        }
    }

    fun createCustomExercise(name: String, category: String) {
        viewModelScope.launch {
            val customId = "custom_${java.util.UUID.randomUUID()}"
            val exercise = Exercise(
                id = customId,
                name = name,
                category = category,
                isCustom = true,
                humanUserId = authViewModel.activeUserId.value
            )
            repository.insertExercise(exercise)
        }
    }

    fun deleteCustomExercise(id: String) {
        viewModelScope.launch {
            val exercise = repository.getExerciseById(id)
            if (exercise != null && exercise.isCustom) {
                repository.deleteExercise(exercise.id)
            }
        }
    }

    fun serializeExerciseIds(ids: List<String>): String {
        return serializeExerciseIdsList(ids)
    }

    fun deserializeExerciseIds(json: String): List<String> {
        return deserializeExerciseIdsList(json)
    }
}
