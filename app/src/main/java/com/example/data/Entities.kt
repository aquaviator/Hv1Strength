package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.versioning.VersionedEntity

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: String, // "offline" or Google User ID
    val googleUserId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val authProvider: String? = null, // "google" or "offline"
    override val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val isOfflineUser: Boolean = false,
    val preferredUnits: String = "metric", // "metric" or "imperial"
    val heightCm: Float? = null,
    val dateOfBirth: String? = null,
    val sex: String? = null,
    val trainingExperience: String? = null, // "Beginner", "Intermediate", "Advanced"

    // Sync metadata fields
    override val globalId: String = "",
    override val humanUserId: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deletedAt: Long? = null,
    override val revision: Long = 1,
    override val syncStatus: String = "LOCAL_ONLY",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    val firebaseUid: String? = null, // Firebase Auth mapping
    override val originDeviceId: String = ""
) : VersionedEntity

@Entity(tableName = "body_weight")
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val weight: Float,
    val date: Long,
    val bodyFat: Float? = null,
    val leanMass: Float? = null,
    val fatMass: Float? = null,
    val bmi: Float? = null,
    val userId: String? = null,

    // Sync metadata fields
    override val globalId: String = "",
    override val humanUserId: String = "",
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deletedAt: Long? = null,
    override val revision: Long = 1,
    override val syncStatus: String = "LOCAL_ONLY",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = ""
) : VersionedEntity

@Entity(tableName = "tape_measurement")
data class TapeMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val chest: Float? = null,
    val waist: Float? = null,
    val hips: Float? = null,
    val bicepLeft: Float? = null,
    val bicepRight: Float? = null,
    val thighLeft: Float? = null,
    val thighRight: Float? = null,
    val userId: String? = null,

    // Sync metadata fields
    override val globalId: String = "",
    override val humanUserId: String = "",
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deletedAt: Long? = null,
    override val revision: Long = 1,
    override val syncStatus: String = "LOCAL_ONLY",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = ""
) : VersionedEntity

@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // Chest, Back, Legs, Shoulders, Arms, Abs
    val isCustom: Boolean = false,

    // Sync metadata fields
    override val globalId: String = "",
    override val humanUserId: String = "",
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deletedAt: Long? = null,
    override val revision: Long = 1,
    override val syncStatus: String = "LOCAL_ONLY",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = ""
) : VersionedEntity

@Entity(tableName = "workout_template")
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val exerciseIdsJson: String, // JSON array of Exercise IDs (e.g., ["bench_press", "squat"])
    val userId: String? = null,

    // Sync metadata fields
    override val globalId: String = "",
    override val humanUserId: String = "",
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deletedAt: Long? = null,
    override val revision: Long = 1,
    override val syncStatus: String = "LOCAL_ONLY",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = ""
) : VersionedEntity

@Entity(tableName = "workout_template_exercise")
data class WorkoutTemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val templateId: Int,
    val exerciseId: String,
    val position: Int,
    val restSeconds: Int,
    val notes: String? = null,
    val supersetGroupId: String? = null,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),

    // Sync metadata fields
    override val globalId: String = "",
    override val humanUserId: String = "",
    override val deletedAt: Long? = null,
    override val revision: Long = 1,
    override val syncStatus: String = "LOCAL_ONLY",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = "",
    
    // Global reference fields
    val templateGlobalId: String = ""
) : VersionedEntity

@Entity(tableName = "workout_template_set")
data class WorkoutTemplateSet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val templateExerciseId: Int,
    val position: Int,
    val setType: String, // WARM_UP, WORKING, DROP_SET, FAILURE, BACK_OFF, AMRAP, TIMED, DISTANCE
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetWeight: Float? = null,
    val targetRpe: Int? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistance: Float? = null,
    val tempo: String? = null,
    val notes: String? = null,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),

    // Sync metadata fields
    override val globalId: String = "",
    override val humanUserId: String = "",
    override val deletedAt: Long? = null,
    override val revision: Long = 1,
    override val syncStatus: String = "LOCAL_ONLY",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = "",
    
    // Global reference fields
    val templateExerciseGlobalId: String = ""
) : VersionedEntity

@Entity(tableName = "workout_session")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val templateId: Int? = null,
    val templateName: String, // e.g. "Push Day" or "Custom Session"
    val startTime: Long,
    val endTime: Long,
    val userId: String? = null,

    // Sync metadata fields
    override val globalId: String = "",
    override val humanUserId: String = "",
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deletedAt: Long? = null,
    override val revision: Long = 1,
    override val syncStatus: String = "LOCAL_ONLY",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = "",
    
    // Global reference fields
    val templateGlobalId: String? = null
) : VersionedEntity

@Entity(tableName = "logged_set")
data class LoggedSet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val exerciseId: String,
    val setNumber: Int,
    val reps: Int,
    val weight: Float,
    val isCompleted: Boolean = false,
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
    val notes: String? = null,

    // Sync metadata fields
    override val globalId: String = "",
    override val humanUserId: String = "",
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deletedAt: Long? = null,
    override val revision: Long = 1,
    override val syncStatus: String = "LOCAL_ONLY",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = "",
    
    // Global reference fields
    val sessionGlobalId: String = ""
) : VersionedEntity

@Entity(tableName = "command_queue")
data class CommandQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val commandId: String,
    val humanUserId: String,
    val commandType: String,
    val entityType: String,
    val entityGlobalId: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val status: String = "PENDING", // PENDING, PROCESSING, SUCCEEDED, FAILED, POISONED
    val errorMessage: String? = null,
    val originDeviceId: String = "",
    val nextRetryAt: Long? = null
)
