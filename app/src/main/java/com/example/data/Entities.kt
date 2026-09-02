package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.versioning.VersionedEntity

data class LocalOwnershipSummary(val meaningfulRecordCount: Int, val otherProfileCount: Int)

@Entity(tableName = "catalogue_release_state")
data class CatalogueReleaseState(
    @PrimaryKey val id: Int = 1,
    val bundledVersion: String,
    val acceptedReleaseId: String? = null,
    val acceptedCatalogueVersion: String? = null,
    val acceptedChecksum: String? = null,
    val acceptedSchemaVersion: Int? = null,
    val source: String = "BUNDLED",
    val status: String = "BUNDLED_ACTIVE",
    val previousReleaseId: String? = null,
    val lastCheckAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastFailure: String? = null
)

@Entity(
    tableName = "studio_workout_link",
    indices = [Index("humanUserId"), Index("workoutGlobalId"), Index("localRoutineId")]
)
data class StudioWorkoutLink(
    @PrimaryKey val versionId: String,
    val humanUserId: String,
    val workoutGlobalId: String,
    val sourceRevision: Long,
    val contentChecksum: String,
    val catalogueReleaseId: String,
    val title: String,
    val description: String,
    val discipline: String,
    val sourcePayloadJson: String,
    val localRoutineId: Int,
    val localRoutineRevisionAtApply: Long,
    val appliedAt: Long,
    val applicationId: String = "HUMAN_STRENGTH",
    val acknowledgementId: String,
    val acknowledgementState: String = "PENDING",
    val conflictState: String? = null,
    val tombstoneState: String = "ACTIVE",
    val isLatest: Boolean = true
)

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

val UserProfile.initials: String
    get() {
        val name = displayName?.trim()
        if (!name.isNullOrBlank()) {
            val parts = name.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return if (parts.size >= 2) {
                val first = parts.first().firstOrNull()?.toString() ?: ""
                val last = parts.last().firstOrNull()?.toString() ?: ""
                (first + last).uppercase()
            } else {
                (parts.firstOrNull()?.firstOrNull()?.toString() ?: "").uppercase()
            }
        }
        val mail = email?.trim()
        if (!mail.isNullOrBlank()) {
            val prefix = mail.substringBefore("@")
            return if (prefix.length >= 2) prefix.take(2).uppercase() else prefix.uppercase()
        }
        return "U"
    }

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

/** A target is intentionally separate from an observation: planned values never masquerade as performance. */
@Entity(tableName = "metric_prescription", indices = [Index("templateSetGlobalId"), Index("metricKey")])
data class MetricPrescriptionEntity(
    @PrimaryKey val globalId: String,
    val templateSetGlobalId: String,
    val metricKey: String,
    val minimumValue: Double? = null,
    val targetValue: Double? = null,
    val maximumValue: Double? = null,
    val textValue: String? = null,
    val canonicalUnit: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Queryable scalar result with lossless source-unit and device provenance. */
@Entity(
    tableName = "metric_observation",
    indices = [Index("loggedSetGlobalId"), Index("metricKey"), Index("humanUserId"), Index("syncStatus"),
        Index(value = ["loggedSetGlobalId", "metricKey"], unique = true)]
)
data class MetricObservationEntity(
    @PrimaryKey override val globalId: String,
    val loggedSetGlobalId: String,
    val metricKey: String,
    val numericValue: Double? = null,
    val textValue: String? = null,
    val canonicalUnit: String? = null,
    val originalValue: Double? = null,
    val originalUnit: String? = null,
    val source: String = "USER",
    val manufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceIdentifier: String? = null,
    val protocol: String? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    override val humanUserId: String = "",
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val revision: Long = 1,
    override val deletedAt: Long? = null,
    override val syncStatus: String = "PENDING_UPLOAD",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = ""
) : VersionedEntity

@Entity(tableName = "metric_segment", indices = [Index(value = ["observationGlobalId", "position"], unique = true)])
data class MetricSegmentEntity(
    @PrimaryKey val globalId: String,
    val observationGlobalId: String,
    val position: Int,
    val startOffsetMillis: Long,
    val endOffsetMillis: Long,
    val numericValue: Double? = null,
    val canonicalUnit: String? = null,
    val label: String? = null
)

@Entity(tableName = "metric_sample", indices = [Index(value = ["observationGlobalId", "offsetMillis"], unique = true)])
data class MetricSampleEntity(
    @PrimaryKey val globalId: String,
    val observationGlobalId: String,
    val offsetMillis: Long,
    val numericValue: Double,
    val canonicalUnit: String
)

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

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: String = "default",
    val isMetric: Boolean = true,
    val theme: String = "system",
    val keepScreenAwake: Boolean = false,
    val defaultRestTimerDuration: Int = 90,
    val soundOn: Boolean = true,
    val vibrationOn: Boolean = true,
    val defaultWarmupSets: Int = 0,
    val autoCompleteBehavior: Boolean = true,
    val autoScroll: Boolean = true,
    val timerPreferences: String = "standard"
)

@Entity(tableName = "active_workout_backup")
data class ActiveWorkoutBackup(
    @PrimaryKey val id: Int = 1, // Singleton row id
    val templateId: Int? = null,
    val templateName: String,
    val startTime: Long,
    val exercisesJson: String,
    val setsJson: String,
    val exerciseMetadataJson: String
)

@Entity(tableName = "training_plan")
data class TrainingPlan(
    @PrimaryKey val id: String,
    val userId: String,
    override val humanUserId: String,
    val templateId: Int,
    val templateGlobalId: String,
    val routineName: String,
    val firstEpochDay: Long,
    val preferredMinuteOfDay: Int? = null,
    val weekdaysMask: Int = 0,
    val recurrenceEndEpochDay: Long? = null,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val globalId: String = id,
    override val revision: Long = 1,
    override val deletedAt: Long? = null,
    override val syncStatus: String = "PENDING_UPLOAD",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = ""
) : VersionedEntity

@Entity(
    tableName = "planned_workout",
    indices = [
        Index(value = ["userId", "scheduledEpochDay"]),
        Index(value = ["seriesId", "scheduledEpochDay"], unique = true)
    ]
)
data class PlannedWorkout(
    @PrimaryKey val id: String,
    val seriesId: String,
    val userId: String,
    override val humanUserId: String,
    val templateId: Int,
    val templateGlobalId: String,
    val routineName: String,
    val scheduledEpochDay: Long,
    val originalEpochDay: Long,
    val preferredMinuteOfDay: Int? = null,
    val status: String = "PLANNED",
    val completedAt: Long? = null,
    val linkedSessionId: Int? = null,
    val reminderEnabled: Boolean = false,
    val detachedFromSeries: Boolean = false,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val globalId: String = id,
    override val revision: Long = 1,
    override val deletedAt: Long? = null,
    override val syncStatus: String = "PENDING_UPLOAD",
    override val lastSyncedAt: Long? = null,
    override val conflictState: String? = null,
    override val originDeviceId: String = ""
) : VersionedEntity

@Entity(tableName = "legacy_ownership_migration")
data class LegacyOwnershipMigrationState(
    @PrimaryKey val id: Int = 1,
    val sourceProfileId: String,
    val sourceHumanUserId: String,
    val targetHumanUserId: String,
    val phase: String,
    val updatedAt: Long
)

