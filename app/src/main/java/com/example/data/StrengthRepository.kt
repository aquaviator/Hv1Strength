package com.example.data

import com.example.core.identity.GlobalIdGenerator
import com.example.core.identity.HumanUserIdGenerator
import com.example.core.identity.DeviceIdGenerator
import kotlinx.coroutines.flow.Flow

class StrengthRepository(val dao: StrengthDao, private val context: android.content.Context? = null) {

    // Helper for command queue enqueuing
    private suspend fun enqueueCommand(
        commandType: String,
        entityType: String,
        entityGlobalId: String,
        humanUserId: String,
        payloadJson: String
    ) {
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
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
        val hUserId = HumanUserIdGenerator.mapUserIdToHumanUserId(profile.id)
        val deviceId = DeviceIdGenerator.getOrGenerateDeviceId()
        val finalProfile = if (profile.globalId.isEmpty()) {
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
                humanUserId = hUserId,
                updatedAt = now,
                revision = profile.revision + 1,
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
        val hUserId = HumanUserIdGenerator.mapUserIdToHumanUserId(weight.userId)
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
        val hUserId = HumanUserIdGenerator.mapUserIdToHumanUserId(measurement.userId)
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
                humanUserId = if (exercise.isCustom) "human_offlineusr" else "global",
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
        val hUserId = HumanUserIdGenerator.mapUserIdToHumanUserId(template.userId)
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
        val hUserId = HumanUserIdGenerator.mapUserIdToHumanUserId(session.userId)
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

    suspend fun insertLoggedSets(sets: List<LoggedSet>) {
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
    }

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
    suspend fun linkExistingDataToUser(userId: String) {
        val hUserId = HumanUserIdGenerator.mapUserIdToHumanUserId(userId)
        dao.linkBodyWeightToUser(userId, hUserId)
        dao.linkTapeMeasurementToUser(userId, hUserId)
        dao.linkWorkoutTemplatesToUser(userId, hUserId)
        dao.linkWorkoutSessionsToUser(userId, hUserId)
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
}
