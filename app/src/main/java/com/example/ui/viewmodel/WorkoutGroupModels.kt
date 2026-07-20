package com.example.ui.viewmodel

import com.example.data.Exercise
import java.util.UUID

enum class WorkoutGroupType {
    SINGLE,
    SUPERSET,
    TRI_SET,
    GIANT_SET,
    CIRCUIT
}

data class GroupedTemplateExercise(
    val id: Int = 0,
    val exerciseId: String,
    val restSeconds: Int = 90,
    val notes: String? = null,
    val supersetGroupId: String? = null,
    val sets: List<StrengthViewModel.TemplateSetState> = emptyList()
)

data class WorkoutExerciseGroup(
    val id: String,
    val type: WorkoutGroupType,
    val position: Int,
    val rounds: Int,
    val restBetweenExercisesSeconds: Int?,
    val restAfterRoundSeconds: Int?,
    val exercises: List<GroupedTemplateExercise>
)

data class WorkoutStep(
    val id: String,
    val exercise: Exercise,
    val setIndex: Int,
    val set: ActiveSet,
    val groupId: String?,
    val groupType: WorkoutGroupType,
    val roundNumber: Int,
    val positionInGroup: Int
)

object WorkoutExecutionQueue {
    fun generateQueue(
        exercises: List<Exercise>,
        sets: Map<String, List<ActiveSet>>,
        exerciseMetadata: Map<String, ActiveExerciseMetadata>
    ): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        val processedGroups = mutableSetOf<String>()
        
        exercises.forEach { exercise ->
            val groupId = exerciseMetadata[exercise.id]?.supersetGroupId
            if (groupId.isNullOrBlank()) {
                // Single exercise group
                val setsList = sets[exercise.id] ?: emptyList()
                setsList.forEachIndexed { setIdx, activeSet ->
                    steps.add(
                        WorkoutStep(
                            id = "single_${exercise.id}_$setIdx",
                            exercise = exercise,
                            setIndex = setIdx,
                            set = activeSet,
                            groupId = null,
                            groupType = WorkoutGroupType.SINGLE,
                            roundNumber = setIdx + 1,
                            positionInGroup = 0
                        )
                    )
                }
            } else {
                // Grouped exercise (Superset, Tri-set, etc.)
                if (groupId !in processedGroups) {
                    processedGroups.add(groupId)
                    
                    // Filter and order exercises in this group
                    val groupExercises = exercises.filter { exerciseMetadata[it.id]?.supersetGroupId == groupId }
                    val groupType = when (groupExercises.size) {
                        2 -> WorkoutGroupType.SUPERSET
                        3 -> WorkoutGroupType.TRI_SET
                        4 -> WorkoutGroupType.GIANT_SET
                        else -> WorkoutGroupType.CIRCUIT
                    }
                    
                    val maxSets = groupExercises.maxOfOrNull { (sets[it.id] ?: emptyList()).size } ?: 0
                    for (setIdx in 0 until maxSets) {
                        groupExercises.forEachIndexed { groupExIdx, groupEx ->
                            val setsList = sets[groupEx.id] ?: emptyList()
                            if (setIdx in setsList.indices) {
                                steps.add(
                                    WorkoutStep(
                                        id = "group_${groupId}_${groupEx.id}_$setIdx",
                                        exercise = groupEx,
                                        setIndex = setIdx,
                                        set = setsList[setIdx],
                                        groupId = groupId,
                                        groupType = groupType,
                                        roundNumber = setIdx + 1,
                                        positionInGroup = groupExIdx
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return steps
    }

    fun mapToExerciseGroups(exercises: List<StrengthViewModel.TemplateExerciseState>): List<WorkoutExerciseGroup> {
        val groups = mutableListOf<WorkoutExerciseGroup>()
        val processedGroupIds = mutableSetOf<String>()
        
        exercises.forEach { ex ->
            val groupId = ex.supersetGroupId
            if (groupId.isNullOrBlank()) {
                // SINGLE group
                groups.add(
                    WorkoutExerciseGroup(
                        id = UUID.randomUUID().toString(),
                        type = WorkoutGroupType.SINGLE,
                        position = groups.size,
                        rounds = ex.sets.size,
                        restBetweenExercisesSeconds = null,
                        restAfterRoundSeconds = ex.restSeconds,
                        exercises = listOf(
                            GroupedTemplateExercise(
                                id = ex.id,
                                exerciseId = ex.exerciseId,
                                restSeconds = ex.restSeconds,
                                notes = ex.notes,
                                supersetGroupId = null,
                                sets = ex.sets
                            )
                        )
                    )
                )
            } else {
                // Grouped exercise
                if (groupId !in processedGroupIds) {
                    processedGroupIds.add(groupId)
                    val groupExs = exercises.filter { it.supersetGroupId == groupId }
                    val maxRounds = groupExs.maxOfOrNull { it.sets.size } ?: 0
                    val groupType = when (groupExs.size) {
                        2 -> WorkoutGroupType.SUPERSET
                        3 -> WorkoutGroupType.TRI_SET
                        4 -> WorkoutGroupType.GIANT_SET
                        else -> WorkoutGroupType.CIRCUIT
                    }
                    groups.add(
                        WorkoutExerciseGroup(
                            id = groupId,
                            type = groupType,
                            position = groups.size,
                            rounds = maxRounds,
                            restBetweenExercisesSeconds = groupExs.firstOrNull()?.restSeconds,
                            restAfterRoundSeconds = groupExs.firstOrNull()?.restSeconds,
                            exercises = groupExs.map { ge ->
                                GroupedTemplateExercise(
                                    id = ge.id,
                                    exerciseId = ge.exerciseId,
                                    restSeconds = ge.restSeconds,
                                    notes = ge.notes,
                                    supersetGroupId = groupId,
                                    sets = ge.sets
                                )
                            }
                        )
                    )
                }
            }
        }
        return groups
    }
}
