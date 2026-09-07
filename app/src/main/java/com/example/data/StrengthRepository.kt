package com.example.data

import com.example.core.identity.GlobalIdGenerator
import com.example.core.identity.HumanUserIdGenerator
import com.example.core.identity.DeviceIdGenerator
import kotlinx.coroutines.flow.Flow

class StrengthRepository(val dao: StrengthDao, private val context: android.content.Context? = null,
    private val deviceIdOverride: String? = null) {

    private fun deviceId(): String = deviceIdOverride ?: DeviceIdGenerator.getOrGenerateDeviceId()

    suspend fun inspectLocalOwnership(profileId: String, humanId: String, authoritativeHumanId: String) =
        LocalOwnershipSummary(
            meaningfulRecordCount = dao.countMeaningfulOwnedRecords(profileId, humanId),
            otherProfileCount = dao.countOtherProfiles(authoritativeHumanId)
        )

    suspend fun adoptEmptyOfflinePlaceholder(profile: UserProfile, offlineHumanId: String) {
        val now = System.currentTimeMillis()
        val finalProfile = profile.copy(
            globalId = profile.globalId.ifEmpty { GlobalIdGenerator.generate("profile") },
            updatedAt = now,
            revision = maxOf(1, profile.revision),
            syncStatus = "SYNCED",
            deletedAt = null,
            originDeviceId = deviceId()
        )
        dao.replaceEmptyOfflinePlaceholder(finalProfile, offlineHumanId)
    }

    fun getPlannedWorkoutsForUser(userId: String): Flow<List<PlannedWorkout>> = dao.getPlannedWorkoutsForUser(userId)
    suspend fun getPlannedWorkout(id: String): PlannedWorkout? = dao.getPlannedWorkout(id)
    suspend fun getTrainingPlan(id: String): TrainingPlan? = dao.getTrainingPlan(id)
    suspend fun getTrainingPlansForUser(userId: String): List<TrainingPlan> = dao.getTrainingPlansForUser(userId)
    suspend fun getAllTrainingPlansForBackup(userId: String): List<TrainingPlan> = dao.getAllTrainingPlansForBackup(userId)
    suspend fun getAllPlannedWorkoutsForBackup(userId: String): List<PlannedWorkout> = dao.getAllPlannedWorkoutsForBackup(userId)

    suspend fun createTrainingPlan(plan: TrainingPlan, occurrences: List<PlannedWorkout>) {
        require(plan.userId.isNotBlank() && plan.humanUserId.isNotBlank())
        val deviceId = deviceId()
        val storedPlan = plan.copy(globalId = plan.id, originDeviceId = deviceId)
        dao.upsertTrainingPlan(storedPlan)
        val storedOccurrences = occurrences.map { it.copy(globalId = it.id, originDeviceId = deviceId) }
        dao.insertPlannedWorkouts(storedOccurrences)
        enqueueCommand("PlannerCreated", "TRAINING_PLAN", storedPlan.globalId, storedPlan.humanUserId, "{}")
        storedOccurrences.forEach { enqueueCommand("PlannerOccurrenceCreated", "PLANNED_WORKOUT", it.globalId, it.humanUserId, "{}") }
    }

    suspend fun updatePlannedWorkout(item: PlannedWorkout, expectedUserId: String) {
        require(item.userId == expectedUserId && dao.ownsPlannedWorkout(item.id, expectedUserId) == 1)
        val existing = dao.getPlannedWorkout(item.id) ?: return
        require(dao.countSeriesDateCollision(item.seriesId, item.scheduledEpochDay, item.id) == 0) {
            "A workout in this recurring series is already scheduled for that date"
        }
        val updated = item.copy(userId = expectedUserId, humanUserId = existing.humanUserId,
            globalId = existing.globalId, revision = existing.revision + 1, syncStatus = "PENDING_UPLOAD",
            originDeviceId = deviceId(), updatedAt = System.currentTimeMillis())
        dao.upsertPlannedWorkout(updated)
        enqueueCommand("PlannerOccurrenceUpdated", "PLANNED_WORKOUT", updated.globalId, updated.humanUserId, "{}")
    }

    suspend fun deletePlannedWorkout(id: String, userId: String) {
        val item = dao.getPlannedWorkout(id) ?: return
        require(item.userId == userId)
        val now = System.currentTimeMillis()
        dao.softDeletePlannedWorkout(id, userId, now)
        if (item.status != "COMPLETED") enqueueCommand("PlannerOccurrenceDeleted", "PLANNED_WORKOUT", item.globalId, item.humanUserId, "{}")
    }
    suspend fun deleteFuturePlannedWorkouts(seriesId: String, userId: String, fromEpochDay: Long) {
        val items = dao.getFuturePlannedWorkouts(seriesId, userId, fromEpochDay)
        val now = System.currentTimeMillis()
        dao.softDeleteFuturePlannedWorkouts(seriesId, userId, fromEpochDay, now)
        items.forEach { enqueueCommand("PlannerOccurrenceDeleted", "PLANNED_WORKOUT", it.globalId, it.humanUserId, "{}") }
    }

    suspend fun completePlannedWorkout(id: String, ownerUserId: String, completedAt: Long, sessionId: Int) {
        if (dao.ownsPlannedWorkout(id, ownerUserId) == 1) {
            dao.updatePlannedWorkoutStatus(id, "COMPLETED", completedAt, sessionId, completedAt)
            dao.getPlannedWorkout(id)?.let { enqueueCommand("PlannerOccurrenceCompleted", "PLANNED_WORKOUT", it.globalId, it.humanUserId, "{}") }
        }
    }

    suspend fun replaceFutureTrainingPlan(
        plan: TrainingPlan,
        fromEpochDay: Long,
        existingFuture: List<PlannedWorkout>,
        replacements: List<PlannedWorkout>
    ) {
        require(existingFuture.all { it.userId == plan.userId && it.humanUserId == plan.humanUserId })
        require(replacements.all { it.userId == plan.userId && it.humanUserId == plan.humanUserId })
        val now = System.currentTimeMillis()
        val storedPlan = (dao.getTrainingPlan(plan.id) ?: plan).let { old -> plan.copy(
            humanUserId = old.humanUserId, globalId = old.globalId, createdAt = old.createdAt,
            revision = old.revision + 1, updatedAt = now, syncStatus = "PENDING_UPLOAD",
            originDeviceId = deviceId()
        ) }
        dao.upsertTrainingPlan(storedPlan)
        enqueueCommand("PlannerSeriesUpdated", "TRAINING_PLAN", storedPlan.globalId, storedPlan.humanUserId, "{}")

        val desiredIds = replacements.mapTo(hashSetOf()) { it.id }
        existingFuture.filter { it.status == "PLANNED" && it.id !in desiredIds }.forEach { obsolete ->
            dao.softDeletePlannedWorkout(obsolete.id, obsolete.userId, now)
            enqueueCommand("PlannerOccurrenceDeleted", "PLANNED_WORKOUT", obsolete.globalId, obsolete.humanUserId, "{}")
        }
        replacements.forEach { replacement ->
            val old = dao.getPlannedWorkout(replacement.id)
            val stored = replacement.copy(
                globalId = old?.globalId ?: replacement.id, createdAt = old?.createdAt ?: now,
                revision = (old?.revision ?: 0) + 1, deletedAt = null, updatedAt = now,
                syncStatus = "PENDING_UPLOAD", originDeviceId = deviceId()
            )
            dao.upsertPlannedWorkout(stored)
            enqueueCommand("PlannerOccurrenceUpdated", "PLANNED_WORKOUT", stored.globalId, stored.humanUserId, "{}")
        }
    }

    private suspend fun resolveHumanUserId(userId: String?, supplied: String? = null): String {
        if (userId.isNullOrBlank() || userId == "offline") {
            return supplied?.takeIf { it.isNotBlank() }
                ?: HumanUserIdGenerator.getOrGenerateOfflineHumanId(context)
        }
        val profile = dao.getUserProfile(userId)
        return profile?.humanUserId?.takeIf { com.example.data.isValidAuthoritativeHumanId(it) }
            ?: supplied?.takeIf { com.example.data.isValidAuthoritativeHumanId(it) }
            ?: throw IllegalStateException("Trusted Human identity is unresolved for authenticated user")
    }

    // Helper for command queue enqueuing
    private suspend fun enqueueCommand(
        commandType: String,
        entityType: String,
        entityGlobalId: String,
        humanUserId: String,
        payloadJson: String
    ) {
        val deviceId = deviceId()
        val randomPart = java.util.UUID.randomUUID().toString().replace("-", "").lowercase().take(12)
        val command = CommandQueueEntity(
            id = 0,
            commandId = "cmd_$randomPart",
            humanUserId = humanUserId,
            commandType = commandType,
            entityType = entityType,
            entityGlobalId = entityGlobalId,
            payloadJson = payloadJson,
            createdAt = System.currentTimeMillis(),
            originDeviceId = deviceId
        )
        dao.enqueueCommand(command)
        context?.let { com.example.core.sync.SyncScheduler.scheduleImmediate(it) }
    }

    // User Profile
    suspend fun getUserProfile(id: String): UserProfile? = dao.getUserProfile(id)

    fun getUserProfileFlow(id: String): Flow<UserProfile?> = dao.getUserProfileFlow(id)

    suspend fun insertUserProfile(profile: UserProfile) {
        val now = System.currentTimeMillis()
        val hUserId = resolveHumanUserId(profile.id, profile.humanUserId)
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val existing = dao.getUserProfile(profile.id)
        require(existing == null || profile.humanUserId.isBlank() || existing.humanUserId.isBlank() ||
            existing.humanUserId == profile.humanUserId) { "Profile ownership mismatch" }
        if (existing != null && profilesSemanticallyEqual(existing, profile.copy(humanUserId = hUserId))) {
            // Preserve local presentation metadata without manufacturing a cloud edit.
            val localOnly = existing.copy(createdAt = profile.createdAt)
            if (localOnly != existing) dao.insertUserProfile(localOnly)
            return
        }
        val finalProfile = if (existing == null && profile.globalId.isEmpty()) {
            profile.copy(
                globalId = GlobalIdGenerator.generate("profile"),
                humanUserId = hUserId,
                createdAt = profile.createdAt,
                updatedAt = now,
                revision = 1,
                syncStatus = "PENDING_UPLOAD",
                deletedAt = null,
                originDeviceId = deviceId
            )
        } else {
            profile.copy(
                globalId = existing?.globalId ?: profile.globalId,
                humanUserId = existing?.humanUserId ?: hUserId,
                createdAt = existing?.createdAt ?: profile.createdAt,
                updatedAt = now,
                revision = (existing?.revision ?: profile.revision) + 1,
                syncStatus = "PENDING_UPLOAD",
                originDeviceId = deviceId
            )
        }
        dao.insertUserProfile(finalProfile)
        enqueueCommand(
            commandType = "SettingsUpdated",
            entityType = "USER_PROFILE",
            entityGlobalId = finalProfile.globalId,
            humanUserId = finalProfile.humanUserId,
            payloadJson = "{\"globalId\":\"${finalProfile.globalId}\"}"
        )
    }

    private fun normalizedOptional(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun profilesSemanticallyEqual(a: UserProfile, b: UserProfile): Boolean =
        a.id == b.id && normalizedOptional(a.email)?.lowercase() == normalizedOptional(b.email)?.lowercase() &&
            normalizedOptional(a.displayName) == normalizedOptional(b.displayName) &&
            normalizedOptional(a.photoUrl) == normalizedOptional(b.photoUrl) &&
            a.preferredUnits.trim().lowercase() == b.preferredUnits.trim().lowercase() &&
            a.heightCm == b.heightCm && normalizedOptional(a.dateOfBirth) == normalizedOptional(b.dateOfBirth) &&
            normalizedOptional(a.sex)?.lowercase() == normalizedOptional(b.sex)?.lowercase() &&
            normalizedOptional(a.trainingExperience)?.lowercase() == normalizedOptional(b.trainingExperience)?.lowercase() &&
            (a.humanUserId.isBlank() || b.humanUserId.isBlank() || a.humanUserId == b.humanUserId) &&
            a.deletedAt == b.deletedAt

    /**
     * Persists identity/profile hydration without turning observation into a user edit.
     * Sign-in and cloud hydration must never echo an unchanged profile back through the
     * command queue. Explicit profile editing continues to use [insertUserProfile].
     */
    suspend fun hydrateUserProfile(profile: UserProfile) {
        val existing = dao.getUserProfile(profile.id)
        require(existing == null || profile.humanUserId.isBlank() || existing.humanUserId.isBlank() ||
            existing.humanUserId == profile.humanUserId) { "Profile ownership mismatch" }
        require(existing == null || profile.firebaseUid.isNullOrBlank() || existing.firebaseUid.isNullOrBlank() ||
            existing.firebaseUid == profile.firebaseUid) { "Profile Firebase identity mismatch" }
        val stored = if (existing == null) {
            profile.copy(
                globalId = profile.globalId.ifBlank { GlobalIdGenerator.generate("profile") },
                humanUserId = resolveHumanUserId(profile.id, profile.humanUserId),
                revision = maxOf(1, profile.revision),
                syncStatus = "SYNCED",
                deletedAt = null,
                originDeviceId = profile.originDeviceId.ifBlank { deviceId() }
            )
        } else if (existing.syncStatus == "PENDING_UPLOAD") {
            // A downloaded/auth record may confirm identity, but cannot silently erase a
            // newer governed local edit which is still waiting to synchronize.
            existing.copy(
                googleUserId = profile.googleUserId ?: existing.googleUserId,
                authProvider = profile.authProvider ?: existing.authProvider,
                firebaseUid = profile.firebaseUid ?: existing.firebaseUid,
                humanUserId = existing.humanUserId.ifBlank { profile.humanUserId }
            )
        } else {
            existing.copy(
                googleUserId = profile.googleUserId ?: existing.googleUserId,
                email = profile.email ?: existing.email,
                displayName = profile.displayName ?: existing.displayName,
                photoUrl = profile.photoUrl ?: existing.photoUrl,
                authProvider = profile.authProvider,
                firebaseUid = profile.firebaseUid ?: existing.firebaseUid,
                humanUserId = existing.humanUserId.ifBlank { profile.humanUserId }
            )
        }
        if (existing != stored) dao.insertUserProfile(stored)
    }

    suspend fun deleteUserProfile(id: String) {
        val existing = dao.getUserProfile(id)
        val now = System.currentTimeMillis()
        dao.softDeleteUserProfile(id, now)
        if (existing != null) {
            enqueueCommand(
                commandType = "SettingsUpdated",
                entityType = "USER_PROFILE",
                entityGlobalId = existing.globalId,
                humanUserId = existing.humanUserId,
                payloadJson = "{\"id\":\"$id\",\"globalId\":\"${existing.globalId}\"}"
            )
        }
    }

    suspend fun permanentDeleteUserProfile(id: String) = dao.deleteUserProfile(id)


    // Body Weight
    val allBodyWeights: Flow<List<BodyWeight>> = dao.getAllBodyWeights()

    fun getBodyWeightsForUser(userId: String): Flow<List<BodyWeight>> = dao.getBodyWeightsForUser(userId)

    suspend fun insertBodyWeight(weight: BodyWeight) {
        val now = System.currentTimeMillis()
        val hUserId = resolveHumanUserId(weight.userId, weight.humanUserId)
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val isNew = weight.id == 0
        val finalWeight = if (isNew) {
            weight.copy(
                globalId = GlobalIdGenerator.generate("measurement"),
                humanUserId = hUserId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                syncStatus = "PENDING_UPLOAD",
                deletedAt = null,
                originDeviceId = deviceId
            )
        } else {
            val existing = dao.getBodyWeightById(weight.id)
            val rev = (existing?.revision ?: 0L) + 1L
            val created = existing?.createdAt ?: now
            weight.copy(
                globalId = if (weight.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("measurement")) else weight.globalId,
                humanUserId = hUserId,
                createdAt = created,
                updatedAt = now,
                revision = rev,
                syncStatus = "PENDING_UPLOAD",
                originDeviceId = deviceId
            )
        }
        dao.insertBodyWeight(finalWeight)
        enqueueCommand(
            commandType = if (isNew) "MeasurementLogged" else "MeasurementUpdated",
            entityType = "BODY_WEIGHT",
            entityGlobalId = finalWeight.globalId,
            humanUserId = finalWeight.humanUserId,
            payloadJson = "{\"globalId\":\"${finalWeight.globalId}\"}"
        )
    }

    suspend fun getBodyWeightById(id: Int): BodyWeight? = dao.getBodyWeightById(id)

    suspend fun deleteBodyWeight(id: Int) {
        val existing = dao.getBodyWeightById(id)
        val now = System.currentTimeMillis()
        dao.softDeleteBodyWeight(id, now)
        if (existing != null) {
            enqueueCommand(
                commandType = "MeasurementDeleted",
                entityType = "BODY_WEIGHT",
                entityGlobalId = existing.globalId,
                humanUserId = existing.humanUserId,
                payloadJson = "{\"id\":$id,\"globalId\":\"${existing.globalId}\"}"
            )
        }
    }


    // Tape Measurements
    val allTapeMeasurements: Flow<List<TapeMeasurement>> = dao.getAllTapeMeasurements()

    fun getTapeMeasurementsForUser(userId: String): Flow<List<TapeMeasurement>> = dao.getTapeMeasurementsForUser(userId)

    suspend fun insertTapeMeasurement(measurement: TapeMeasurement) {
        val now = System.currentTimeMillis()
        val hUserId = resolveHumanUserId(measurement.userId, measurement.humanUserId)
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val isNew = measurement.id == 0
        val finalMeasurement = if (isNew) {
            measurement.copy(
                globalId = GlobalIdGenerator.generate("measurement"),
                humanUserId = hUserId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                syncStatus = "PENDING_UPLOAD",
                deletedAt = null,
                originDeviceId = deviceId
            )
        } else {
            val existing = dao.getTapeMeasurementById(measurement.id)
            val rev = (existing?.revision ?: 0L) + 1L
            val created = existing?.createdAt ?: now
            measurement.copy(
                globalId = if (measurement.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("measurement")) else measurement.globalId,
                humanUserId = hUserId,
                createdAt = created,
                updatedAt = now,
                revision = rev,
                syncStatus = "PENDING_UPLOAD",
                originDeviceId = deviceId
            )
        }
        dao.insertTapeMeasurement(finalMeasurement)
        enqueueCommand(
            commandType = if (isNew) "MeasurementLogged" else "MeasurementUpdated",
            entityType = "TAPE_MEASUREMENT",
            entityGlobalId = finalMeasurement.globalId,
            humanUserId = finalMeasurement.humanUserId,
            payloadJson = "{\"globalId\":\"${finalMeasurement.globalId}\"}"
        )
    }

    suspend fun getTapeMeasurementById(id: Int): TapeMeasurement? = dao.getTapeMeasurementById(id)

    suspend fun deleteTapeMeasurement(id: Int) {
        val existing = dao.getTapeMeasurementById(id)
        val now = System.currentTimeMillis()
        dao.softDeleteTapeMeasurement(id, now)
        if (existing != null) {
            enqueueCommand(
                commandType = "MeasurementDeleted",
                entityType = "TAPE_MEASUREMENT",
                entityGlobalId = existing.globalId,
                humanUserId = existing.humanUserId,
                payloadJson = "{\"id\":$id,\"globalId\":\"${existing.globalId}\"}"
            )
        }
    }


    // Exercises
    val allExercises: Flow<List<Exercise>> = dao.getAllExercises()

    suspend fun getExerciseById(id: String): Exercise? = dao.getExerciseById(id)

    suspend fun insertExercise(exercise: Exercise) {
        val now = System.currentTimeMillis()
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val isNew = exercise.globalId.isEmpty()
        val finalExercise = if (isNew) {
            exercise.copy(
                globalId = if (exercise.isCustom) GlobalIdGenerator.generate("exercise") else exercise.id,
                humanUserId = if (exercise.isCustom) exercise.humanUserId.ifBlank { "human_offlineusr" } else "global",
                createdAt = now,
                updatedAt = now,
                revision = 1,
                syncStatus = if (exercise.isCustom) "PENDING_UPLOAD" else "SYNCED",
                deletedAt = null,
                originDeviceId = deviceId
            )
        } else {
            exercise.copy(
                updatedAt = now,
                revision = exercise.revision + 1,
                syncStatus = if (exercise.isCustom) "PENDING_UPLOAD" else "SYNCED",
                originDeviceId = deviceId
            )
        }
        dao.insertExercise(finalExercise)
        if (finalExercise.isCustom) {
            enqueueCommand(
                commandType = if (isNew) "ExerciseCreated" else "ExerciseUpdated",
                entityType = "EXERCISE",
                entityGlobalId = finalExercise.globalId,
                humanUserId = finalExercise.humanUserId,
                payloadJson = "{\"globalId\":\"${finalExercise.globalId}\"}"
            )
        }
    }

    suspend fun deleteExercise(id: String) {
        val existing = dao.getExerciseById(id)
        val now = System.currentTimeMillis()
        dao.softDeleteExercise(id, now)
        if (existing != null) {
            enqueueCommand(
                commandType = "ExerciseDeleted",
                entityType = "EXERCISE",
                entityGlobalId = existing.globalId,
                humanUserId = existing.humanUserId,
                payloadJson = "{\"id\":\"$id\",\"globalId\":\"${existing.globalId}\"}"
            )
        }
    }


    // Workout Templates
    val allTemplates: Flow<List<WorkoutTemplate>> = dao.getAllTemplates()

    suspend fun getTemplateSync(id: Int): WorkoutTemplate? = dao.getTemplateById(id)

    fun getTemplatesForUser(userId: String): Flow<List<WorkoutTemplate>> = dao.getTemplatesForUser(userId)

    suspend fun insertTemplate(template: WorkoutTemplate): Long {
        val now = System.currentTimeMillis()
        val hUserId = resolveHumanUserId(template.userId, template.humanUserId)
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val isNew = template.id == 0
        val finalTemplate = if (isNew) {
            template.copy(
                globalId = GlobalIdGenerator.generate("template"),
                humanUserId = hUserId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                syncStatus = "PENDING_UPLOAD",
                deletedAt = null,
                originDeviceId = deviceId
            )
        } else {
            val existing = dao.getTemplateById(template.id)
            val rev = (existing?.revision ?: 0L) + 1L
            val created = existing?.createdAt ?: now
            template.copy(
                globalId = if (template.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("template")) else template.globalId,
                humanUserId = hUserId,
                createdAt = created,
                updatedAt = now,
                revision = rev,
                syncStatus = "PENDING_UPLOAD",
                originDeviceId = deviceId
            )
        }
        val insertedId = dao.insertTemplate(finalTemplate)
        enqueueCommand(
            commandType = if (isNew) "WorkoutTemplateCreated" else "WorkoutTemplateUpdated",
            entityType = "WORKOUT_TEMPLATE",
            entityGlobalId = finalTemplate.globalId,
            humanUserId = finalTemplate.humanUserId,
            payloadJson = "{\"globalId\":\"${finalTemplate.globalId}\"}"
        )
        return insertedId
    }

    suspend fun deleteTemplate(id: Int) {
        val existing = dao.getTemplateById(id)
        val now = System.currentTimeMillis()
        dao.softDeleteTemplate(id, now)
        dao.softDeleteTemplateExercisesForTemplate(id, now)
        if (existing != null) {
            enqueueCommand(
                commandType = "WorkoutTemplateDeleted",
                entityType = "WORKOUT_TEMPLATE",
                entityGlobalId = existing.globalId,
                humanUserId = existing.humanUserId,
                payloadJson = "{\"id\":$id,\"globalId\":\"${existing.globalId}\"}"
            )
        }
    }


    // Workout Sessions
    val allSessions: Flow<List<WorkoutSession>> = dao.getAllSessions()

    fun getSessionsForUser(userId: String): Flow<List<WorkoutSession>> = dao.getSessionsForUser(userId)

    suspend fun insertSession(session: WorkoutSession): Long {
        val now = System.currentTimeMillis()
        val hUserId = resolveHumanUserId(session.userId, session.humanUserId)
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val isNew = session.id == 0
        
        // Fetch parent globalId
        val parentTemplate = session.templateId?.let { dao.getTemplateById(it) }
        val templateGId = parentTemplate?.globalId

        val finalSession = if (isNew) {
            session.copy(
                globalId = GlobalIdGenerator.generate("session"),
                humanUserId = hUserId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                syncStatus = "PENDING_UPLOAD",
                deletedAt = null,
                originDeviceId = deviceId,
                templateGlobalId = templateGId
            )
        } else {
            val existing = dao.getSessionById(session.id)
            val rev = (existing?.revision ?: 0L) + 1L
            val created = existing?.createdAt ?: now
            session.copy(
                globalId = if (session.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("session")) else session.globalId,
                humanUserId = hUserId,
                createdAt = created,
                updatedAt = now,
                revision = rev,
                syncStatus = "PENDING_UPLOAD",
                originDeviceId = deviceId,
                templateGlobalId = templateGId
            )
        }
        val insertedId = dao.insertSession(finalSession)
        
        if (isNew) {
            enqueueCommand(
                commandType = "WorkoutStarted",
                entityType = "WORKOUT_SESSION",
                entityGlobalId = finalSession.globalId,
                humanUserId = finalSession.humanUserId,
                payloadJson = "{\"globalId\":\"${finalSession.globalId}\"}"
            )
            enqueueCommand(
                commandType = "WorkoutCompleted",
                entityType = "WORKOUT_SESSION",
                entityGlobalId = finalSession.globalId,
                humanUserId = finalSession.humanUserId,
                payloadJson = "{\"globalId\":\"${finalSession.globalId}\"}"
            )
        } else {
            enqueueCommand(
                commandType = "WorkoutCompleted",
                entityType = "WORKOUT_SESSION",
                entityGlobalId = finalSession.globalId,
                humanUserId = finalSession.humanUserId,
                payloadJson = "{\"globalId\":\"${finalSession.globalId}\"}"
            )
        }
        return insertedId
    }

    suspend fun getSessionById(id: Int): WorkoutSession? = dao.getSessionById(id)

    suspend fun deleteSession(id: Int) {
        val existing = dao.getSessionById(id)
        val now = System.currentTimeMillis()
        dao.softDeleteSession(id, now)
        dao.softDeleteSetsForSession(id, now)
        if (existing != null) {
            enqueueCommand(
                commandType = "WorkoutDeleted",
                entityType = "WORKOUT_SESSION",
                entityGlobalId = existing.globalId,
                humanUserId = existing.humanUserId,
                payloadJson = "{\"id\":$id,\"globalId\":\"${existing.globalId}\"}"
            )
        }
    }


    // Logged Sets
    fun getSetsForSession(sessionId: Int): Flow<List<LoggedSet>> = dao.getSetsForSession(sessionId)

    suspend fun getSetsForSessionSync(sessionId: Int): List<LoggedSet> = dao.getSetsForSessionSync(sessionId)

    suspend fun insertLoggedSet(set: LoggedSet) {
        val now = System.currentTimeMillis()
        val parentSession = dao.getSessionById(set.sessionId)
        val hUserId = parentSession?.humanUserId ?: "human_offlineusr"
        val sessionGId = parentSession?.globalId ?: ""
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val isNew = set.id == 0
        val finalSet = if (isNew) {
            set.copy(
                globalId = GlobalIdGenerator.generate("logged_set"),
                humanUserId = hUserId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                syncStatus = "PENDING_UPLOAD",
                deletedAt = null,
                originDeviceId = deviceId,
                sessionGlobalId = sessionGId
            )
        } else {
            val existing = dao.getLoggedSetById(set.id)
            val rev = (existing?.revision ?: 0L) + 1L
            val created = existing?.createdAt ?: now
            set.copy(
                globalId = if (set.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("logged_set")) else set.globalId,
                humanUserId = hUserId,
                createdAt = created,
                updatedAt = now,
                revision = rev,
                syncStatus = "PENDING_UPLOAD",
                originDeviceId = deviceId,
                sessionGlobalId = sessionGId
            )
        }
        dao.insertLoggedSet(finalSet)
        if (finalSet.isCompleted) {
            enqueueCommand(
                commandType = "SetCompleted",
                entityType = "LOGGED_SET",
                entityGlobalId = finalSet.globalId,
                humanUserId = finalSet.humanUserId,
                payloadJson = "{\"globalId\":\"${finalSet.globalId}\"}"
            )
        }
    }

    suspend fun insertLoggedSets(sets: List<LoggedSet>): List<LoggedSet> {
        val now = System.currentTimeMillis()
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val updatedList = sets.map { set ->
            val parentSession = dao.getSessionById(set.sessionId)
            val hUserId = parentSession?.humanUserId ?: "human_offlineusr"
            val sessionGId = parentSession?.globalId ?: ""
            if (set.id == 0) {
                set.copy(
                    globalId = GlobalIdGenerator.generate("logged_set"),
                    humanUserId = hUserId,
                    createdAt = now,
                    updatedAt = now,
                    revision = 1,
                    syncStatus = "PENDING_UPLOAD",
                    deletedAt = null,
                    originDeviceId = deviceId,
                    sessionGlobalId = sessionGId
                )
            } else {
                val existing = dao.getLoggedSetById(set.id)
                val rev = (existing?.revision ?: 0L) + 1L
                val created = existing?.createdAt ?: now
                set.copy(
                    globalId = if (set.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("logged_set")) else set.globalId,
                    humanUserId = hUserId,
                    createdAt = created,
                    updatedAt = now,
                    revision = rev,
                    syncStatus = "PENDING_UPLOAD",
                    originDeviceId = deviceId,
                    sessionGlobalId = sessionGId
                )
            }
        }
        dao.insertLoggedSets(updatedList)
        for (finalSet in updatedList) {
            if (finalSet.isCompleted) {
                enqueueCommand(
                    commandType = "SetCompleted",
                    entityType = "LOGGED_SET",
                    entityGlobalId = finalSet.globalId,
                    humanUserId = finalSet.humanUserId,
                    payloadJson = "{\"globalId\":\"${finalSet.globalId}\"}"
                )
            }
        }
        return updatedList
    }

    suspend fun saveAdditionalMetrics(set: LoggedSet, profile: com.example.measurement.ExerciseMetricProfile, values: Map<String, Double>) {
        if (values.isEmpty()) return
        val observations = com.example.measurement.MeasurementRepository(dao).saveObservations(
            set.globalId, profile, values.toSortedMap().map { (key, value) ->
                val unit=com.example.measurement.CanonicalMetricDictionary.require(key).canonicalUnit
                com.example.measurement.ObservationInput(com.example.measurement.MetricInput(key, numericValue=value, unitKey=unit))
            }, System.currentTimeMillis(), set.humanUserId, deviceId()
        )
        observations.forEach {
            enqueueCommand("MeasurementRecorded", "MEASUREMENT_RECORD", it.globalId, it.humanUserId, "{}")
        }
    }

    suspend fun getMetricObservation(globalId: String) = dao.getMetricObservation(globalId)
    suspend fun markMetricObservationSynced(globalId: String, timestamp: Long) = dao.markMetricObservationSynced(globalId, timestamp)

    suspend fun deleteSetsForSession(sessionId: Int) {
        dao.softDeleteSetsForSession(sessionId, System.currentTimeMillis())
    }

    fun getCompletedSetsForExercise(exerciseId: String): Flow<List<LoggedSet>> = dao.getCompletedSetsForExercise(exerciseId)

    fun getCompletedSetsForExerciseForUser(exerciseId: String, userId: String): Flow<List<LoggedSet>> = dao.getCompletedSetsForExerciseForUser(exerciseId, userId)

    val allLoggedSets: Flow<List<LoggedSet>> = dao.getAllLoggedSets()

    fun getLoggedSetsForUser(userId: String): Flow<List<LoggedSet>> = dao.getLoggedSetsForUser(userId)

    suspend fun getPreviousSetsForExercise(exerciseId: String): List<LoggedSet> {
        val lastSessionId = dao.getLastSessionIdForExercise(exerciseId) ?: return emptyList()
        return dao.getSetsForExerciseInSession(exerciseId, lastSessionId)
    }

    suspend fun getPreviousSetsForExerciseForUser(exerciseId: String, userId: String): List<LoggedSet> {
        val lastSessionId = dao.getLastSessionIdForExerciseForUser(exerciseId, userId) ?: return emptyList()
        return dao.getSetsForExerciseInSession(exerciseId, lastSessionId)
    }


    // Bulk linking
    suspend fun linkExistingDataToUser(userId: String, authoritativeHumanUserId: String) {
        require(com.example.data.isValidAuthoritativeHumanId(authoritativeHumanUserId))
        val profile = dao.getUserProfile(userId)
        require(profile?.firebaseUid == userId && profile.humanUserId == authoritativeHumanUserId) {
            "Attachment requires the matching trusted account profile"
        }
        val hUserId = authoritativeHumanUserId
        val offlineId = HumanUserIdGenerator.existingOfflineHumanId(context) ?: return
        // The fallback used by old migrations is not installation-specific provenance.
        if (offlineId == "human_offlineusr" || offlineId == hUserId) return
        val localDeviceId = deviceId()
        dao.linkBodyWeightToUser(userId, hUserId, offlineId, localDeviceId)
        dao.linkTapeMeasurementToUser(userId, hUserId, offlineId, localDeviceId)
        dao.linkWorkoutTemplatesToUser(userId, hUserId, offlineId, localDeviceId)
        dao.linkWorkoutSessionsToUser(userId, hUserId, offlineId, localDeviceId)
    }


    // Workout Template Exercises
    fun getTemplateExercises(templateId: Int): Flow<List<WorkoutTemplateExercise>> = dao.getTemplateExercises(templateId)
    
    suspend fun getTemplateExercisesSync(templateId: Int): List<WorkoutTemplateExercise> = dao.getTemplateExercisesSync(templateId)
    
    suspend fun insertTemplateExercise(exercise: WorkoutTemplateExercise): Long {
        val now = System.currentTimeMillis()
        val parentTemplate = dao.getTemplateById(exercise.templateId)
        val hUserId = parentTemplate?.humanUserId ?: "human_offlineusr"
        val templateGId = parentTemplate?.globalId ?: ""
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val finalExercise = if (exercise.id == 0) {
            exercise.copy(
                globalId = GlobalIdGenerator.generate("template_exercise"),
                humanUserId = hUserId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                syncStatus = "PENDING_UPLOAD",
                deletedAt = null,
                originDeviceId = deviceId,
                templateGlobalId = templateGId
            )
        } else {
            val existing = dao.getTemplateExerciseById(exercise.id)
            val rev = (existing?.revision ?: 0L) + 1L
            val created = existing?.createdAt ?: now
            exercise.copy(
                globalId = if (exercise.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("template_exercise")) else exercise.globalId,
                humanUserId = hUserId,
                createdAt = created,
                updatedAt = now,
                revision = rev,
                syncStatus = "PENDING_UPLOAD",
                originDeviceId = deviceId,
                templateGlobalId = templateGId
            )
        }
        return dao.insertTemplateExercise(finalExercise)
    }

    suspend fun insertTemplateExercises(exercises: List<WorkoutTemplateExercise>) {
        val now = System.currentTimeMillis()
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val updatedList = exercises.map { exercise ->
            val parentTemplate = dao.getTemplateById(exercise.templateId)
            val hUserId = parentTemplate?.humanUserId ?: "human_offlineusr"
            val templateGId = parentTemplate?.globalId ?: ""
            if (exercise.id == 0) {
                exercise.copy(
                    globalId = GlobalIdGenerator.generate("template_exercise"),
                    humanUserId = hUserId,
                    createdAt = now,
                    updatedAt = now,
                    revision = 1,
                    syncStatus = "PENDING_UPLOAD",
                    deletedAt = null,
                    originDeviceId = deviceId,
                    templateGlobalId = templateGId
                )
            } else {
                val existing = dao.getTemplateExerciseById(exercise.id)
                val rev = (existing?.revision ?: 0L) + 1L
                val created = existing?.createdAt ?: now
                exercise.copy(
                    globalId = if (exercise.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("template_exercise")) else exercise.globalId,
                    humanUserId = hUserId,
                    createdAt = created,
                    updatedAt = now,
                    revision = rev,
                    syncStatus = "PENDING_UPLOAD",
                    originDeviceId = deviceId,
                    templateGlobalId = templateGId
                )
            }
        }
        dao.insertTemplateExercises(updatedList)
    }

    suspend fun deleteTemplateExercisesForTemplate(templateId: Int) {
        dao.softDeleteTemplateExercisesForTemplate(templateId, System.currentTimeMillis())
    }

    suspend fun deleteTemplateExerciseById(id: Int) {
        val now = System.currentTimeMillis()
        dao.softDeleteTemplateExerciseById(id, now)
        dao.softDeleteTemplateSetsForExercise(id, now)
    }


    // Workout Template Sets
    fun getTemplateSets(templateExerciseId: Int): Flow<List<WorkoutTemplateSet>> = dao.getTemplateSets(templateExerciseId)
    
    suspend fun getTemplateSetsSync(templateExerciseId: Int): List<WorkoutTemplateSet> = dao.getTemplateSetsSync(templateExerciseId)
    
    suspend fun getTemplateSetsForTemplateSync(templateId: Int): List<WorkoutTemplateSet> = dao.getTemplateSetsForTemplateSync(templateId)
    
    suspend fun insertTemplateSet(set: WorkoutTemplateSet) {
        val now = System.currentTimeMillis()
        val parentEx = dao.getTemplateExerciseById(set.templateExerciseId)
        val hUserId = parentEx?.humanUserId ?: "human_offlineusr"
        val exGId = parentEx?.globalId ?: ""
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val finalSet = if (set.id == 0) {
            set.copy(
                globalId = GlobalIdGenerator.generate("template_set"),
                humanUserId = hUserId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                syncStatus = "PENDING_UPLOAD",
                deletedAt = null,
                originDeviceId = deviceId,
                templateExerciseGlobalId = exGId
            )
        } else {
            val existing = dao.getTemplateSetById(set.id)
            val rev = (existing?.revision ?: 0L) + 1L
            val created = existing?.createdAt ?: now
            set.copy(
                globalId = if (set.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("template_set")) else set.globalId,
                humanUserId = hUserId,
                createdAt = created,
                updatedAt = now,
                revision = rev,
                syncStatus = "PENDING_UPLOAD",
                originDeviceId = deviceId,
                templateExerciseGlobalId = exGId
            )
        }
        dao.insertTemplateSet(finalSet)
    }

    suspend fun insertTemplateSets(sets: List<WorkoutTemplateSet>) {
        val now = System.currentTimeMillis()
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val updatedList = sets.map { set ->
            val parentEx = dao.getTemplateExerciseById(set.templateExerciseId)
            val hUserId = parentEx?.humanUserId ?: "human_offlineusr"
            val exGId = parentEx?.globalId ?: ""
            if (set.id == 0) {
                set.copy(
                    globalId = GlobalIdGenerator.generate("template_set"),
                    humanUserId = hUserId,
                    createdAt = now,
                    updatedAt = now,
                    revision = 1,
                    syncStatus = "PENDING_UPLOAD",
                    deletedAt = null,
                    originDeviceId = deviceId,
                    templateExerciseGlobalId = exGId
                )
            } else {
                val existing = dao.getTemplateSetById(set.id)
                val rev = (existing?.revision ?: 0L) + 1L
                val created = existing?.createdAt ?: now
                set.copy(
                    globalId = if (set.globalId.isEmpty()) (existing?.globalId ?: GlobalIdGenerator.generate("template_set")) else set.globalId,
                    humanUserId = hUserId,
                    createdAt = created,
                    updatedAt = now,
                    revision = rev,
                    syncStatus = "PENDING_UPLOAD",
                    originDeviceId = deviceId,
                    templateExerciseGlobalId = exGId
                )
            }
        }
        dao.insertTemplateSets(updatedList)
    }

    suspend fun deleteTemplateSetsForExercise(templateExerciseId: Int) {
        dao.softDeleteTemplateSetsForExercise(templateExerciseId, System.currentTimeMillis())
    }

    suspend fun deleteTemplateSetById(id: Int) {
        dao.softDeleteTemplateSetById(id, System.currentTimeMillis())
    }


    // ==========================================
    // COMMAND QUEUE SUPPORT
    // ==========================================

    suspend fun enqueueCommand(command: CommandQueueEntity) {
        dao.enqueueCommand(command)
    }

    suspend fun getPendingCommands(now: Long): List<CommandQueueEntity> = dao.getPendingCommands(now)

    suspend fun getPendingCommands(): List<CommandQueueEntity> = dao.getPendingCommands(System.currentTimeMillis())

    suspend fun getAllCommands(): List<CommandQueueEntity> = dao.getAllCommands()

    suspend fun reconcileMissingEditableCommands(profileId: String, owner: String): ReconciliationPlan {
        val exercises = dao.legacyCustomExercises(owner).map { ReconciliationCandidate("CUSTOM_EXERCISE", it.globalId, it.humanUserId, it.revision, it.syncStatus, it.deletedAt) }
        val templates = dao.legacyTemplates(profileId, owner).filter { it.humanUserId == owner }
        val templateCandidates = templates.map { ReconciliationCandidate("WORKOUT_TEMPLATE", it.globalId, it.humanUserId, it.revision, it.syncStatus, it.deletedAt) }
        val exerciseChildren = dao.legacyTemplateExercises(owner).map { ReconciliationCandidate("WORKOUT_TEMPLATE_EXERCISE", it.globalId, it.humanUserId, it.revision, it.syncStatus, it.deletedAt, it.templateGlobalId) }
        val childIds = exerciseChildren.map { it.globalId }.toSet()
        val setChildren = dao.legacyTemplateSets(owner).map { ReconciliationCandidate("WORKOUT_TEMPLATE_SET", it.globalId, it.humanUserId, it.revision, it.syncStatus, it.deletedAt, it.templateExerciseGlobalId) }
        val plans = dao.legacyTrainingPlans(profileId, owner).filter { it.humanUserId == owner }
        val planCandidates = plans.map { ReconciliationCandidate("TRAINING_PLAN", it.globalId, it.humanUserId, it.revision, it.syncStatus, it.deletedAt, it.templateGlobalId) }
        val planIds = plans.map { it.globalId }.toSet()
        val occurrences = dao.legacyPlannedWorkouts(profileId, owner).filter { it.humanUserId == owner }
            .map { ReconciliationCandidate("PLANNED_WORKOUT", it.globalId, it.humanUserId, it.revision, if (it.status == "COMPLETED") "COMPLETED" else it.syncStatus, it.deletedAt, it.seriesId) }
        val candidates = exercises + templateCandidates + exerciseChildren + setChildren + planCandidates + occurrences
        val parents = templates.map { it.globalId }.toSet() + childIds + planIds
        val plan = AccountReconciliationPlanner.plan(owner, candidates, dao.legacyCommands(owner), parents, System.currentTimeMillis(), deviceId())
        plan.commands.forEach { dao.enqueueReconciliationCommandIfMissing(it) }
        return plan
    }

    suspend fun retryPermissionDeniedCustomExerciseCommands(humanUserId: String): Int {
        var recovered = 0
        dao.getPoisonedCustomExerciseCommands().forEach { command ->
            val exercise = dao.getExerciseByGlobalId(command.entityGlobalId)
            val eligible = command.humanUserId == humanUserId &&
                command.errorMessage?.contains("PERMISSION_DENIED") == true &&
                exercise?.isCustom == true && exercise.humanUserId == humanUserId &&
                exercise.syncStatus == "PENDING_UPLOAD"
            if (eligible) {
                recovered += dao.requeuePoisonedCustomExerciseCommand(
                    command.commandId, humanUserId, command.entityGlobalId
                )
            }
        }
        return recovered
    }

    suspend fun updateCommandStatus(id: Int, status: String, attempts: Int, lastAttemptAt: Long?, nextRetryAt: Long?, errorMessage: String?) {
        dao.updateCommandStatus(id, status, attempts, lastAttemptAt, nextRetryAt, errorMessage)
    }

    suspend fun markCommandProcessing(id: Int) {
        dao.markCommandProcessing(id, System.currentTimeMillis())
    }

    suspend fun markCommandSucceeded(id: Int) {
        dao.markCommandSucceeded(id)
    }

    suspend fun markCommandFailed(id: Int, error: String) {
        dao.markCommandFailed(id, error)
    }

    suspend fun markCommandPoisoned(id: Int, error: String) {
        dao.markCommandPoisoned(id, error)
    }

    // ==========================================
    // GLOBAL ID SELECTORS FOR SYNC
    // ==========================================

    suspend fun getUserProfileByGlobalId(globalId: String): UserProfile? = dao.getUserProfileByGlobalId(globalId)
    suspend fun getBodyWeightByGlobalId(globalId: String): BodyWeight? = dao.getBodyWeightByGlobalId(globalId)
    suspend fun getTapeMeasurementByGlobalId(globalId: String): TapeMeasurement? = dao.getTapeMeasurementByGlobalId(globalId)
    suspend fun getExerciseByGlobalId(globalId: String): Exercise? = dao.getExerciseByGlobalId(globalId)
    suspend fun getTemplateByGlobalId(globalId: String): WorkoutTemplate? = dao.getTemplateByGlobalId(globalId)
    suspend fun getTemplateExerciseByGlobalId(globalId: String): WorkoutTemplateExercise? = dao.getTemplateExerciseByGlobalId(globalId)
    suspend fun getTemplateSetByGlobalId(globalId: String): WorkoutTemplateSet? = dao.getTemplateSetByGlobalId(globalId)
    suspend fun getSessionByGlobalId(globalId: String): WorkoutSession? = dao.getSessionByGlobalId(globalId)
    suspend fun getLoggedSetByGlobalId(globalId: String): LoggedSet? = dao.getLoggedSetByGlobalId(globalId)
    suspend fun getTrainingPlanByGlobalId(globalId: String): TrainingPlan? = dao.getTrainingPlanByGlobalId(globalId)
    suspend fun getPlannedWorkoutByGlobalId(globalId: String): PlannedWorkout? = dao.getPlannedWorkoutByGlobalId(globalId)

    // Sync helpers
    suspend fun markProfileSynced(id: String, timestamp: Long) = dao.markProfileSynced(id, timestamp)
    suspend fun markBodyWeightSynced(id: Int, timestamp: Long) = dao.markBodyWeightSynced(id, timestamp)
    suspend fun markTapeMeasurementSynced(id: Int, timestamp: Long) = dao.markTapeMeasurementSynced(id, timestamp)
    suspend fun markExerciseSynced(id: String, timestamp: Long) = dao.markExerciseSynced(id, timestamp)
    suspend fun markTemplateSynced(id: Int, timestamp: Long) = dao.markTemplateSynced(id, timestamp)
    suspend fun markTemplateExerciseSynced(id: Int, timestamp: Long) = dao.markTemplateExerciseSynced(id, timestamp)
    suspend fun markTemplateSetSynced(id: Int, timestamp: Long) = dao.markTemplateSetSynced(id, timestamp)
    suspend fun markSessionSynced(id: Int, timestamp: Long) = dao.markSessionSynced(id, timestamp)
    suspend fun markLoggedSetSynced(id: Int, timestamp: Long) = dao.markLoggedSetSynced(id, timestamp)
    suspend fun markTrainingPlanSynced(id: String, timestamp: Long) = dao.markTrainingPlanSynced(id, timestamp)
    suspend fun markPlannedWorkoutSynced(id: String, timestamp: Long) = dao.markPlannedWorkoutSynced(id, timestamp)
    suspend fun reconcileRemoteCompletion(id: String, completedAt: Long?, sessionId: Int?, revision: Long, updatedAt: Long) =
        dao.reconcileRemoteCompletion(id, completedAt, sessionId, revision, updatedAt)
    suspend fun reconcileRemoteTombstone(id: String, deletedAt: Long, revision: Long, updatedAt: Long) =
        dao.reconcileRemoteTombstone(id, deletedAt, revision, updatedAt)
    suspend fun markPlannedWorkoutConflict(id: String, diagnostic: String) = dao.markPlannedWorkoutConflict(id, diagnostic)
    suspend fun markExerciseConflict(id: String, diagnostic: String) = dao.markExerciseConflict(id, diagnostic)
    suspend fun markTemplateConflict(id: Int, diagnostic: String) = dao.markTemplateConflict(id, diagnostic)
    fun getConflictExercisesFlow() = dao.getConflictExercisesFlow()
    fun getConflictTemplatesFlow() = dao.getConflictTemplatesFlow()

    // ==========================================
    // ACTIVE WORKOUT BACKUP SUPPORT
    // ==========================================

    suspend fun saveActiveWorkoutBackup(backup: ActiveWorkoutBackup) {
        dao.insertActiveWorkoutBackup(backup)
    }

    suspend fun getActiveWorkoutBackup(): ActiveWorkoutBackup? {
        return dao.getActiveWorkoutBackup()
    }

    suspend fun clearActiveWorkoutBackup() {
        dao.clearActiveWorkoutBackup()
    }

    suspend fun clearAllLocalData() {
        dao.clearWorkoutSessions()
        dao.clearLoggedSets()
        dao.clearWorkoutTemplates()
        dao.clearWorkoutTemplateExercises()
        dao.clearWorkoutTemplateSets()
        dao.clearBodyWeights()
        dao.clearTapeMeasurements()
    }
}
