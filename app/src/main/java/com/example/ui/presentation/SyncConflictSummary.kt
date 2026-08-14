package com.example.ui.presentation

import com.example.data.Exercise
import com.example.data.WorkoutTemplate

data class SyncConflictSummary(
    val entityType: String,
    val displayLabel: String,
    val entityReference: String,
    val localModifiedAt: Long,
    val onlineModifiedAt: Long?,
    val resolutionAvailable: Boolean = false,
    val status: String = "Needs review"
)

private fun onlineModifiedAt(metadata: String?): Long? = metadata
    ?.split('|')
    ?.firstOrNull { it.startsWith("online=") }
    ?.substringAfter('=')
    ?.toLongOrNull()

fun Exercise.toConflictSummary() = SyncConflictSummary(
    entityType = "Custom exercise",
    displayLabel = name,
    entityReference = globalId,
    localModifiedAt = updatedAt,
    onlineModifiedAt = onlineModifiedAt(conflictState)
)

fun WorkoutTemplate.toConflictSummary() = SyncConflictSummary(
    entityType = "Routine",
    displayLabel = name,
    entityReference = globalId,
    localModifiedAt = updatedAt,
    onlineModifiedAt = onlineModifiedAt(conflictState)
)
