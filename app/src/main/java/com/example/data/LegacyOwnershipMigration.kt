package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class LegacyOwnershipTotals(
    val measurements: Int, val customExercises: Int, val templates: Int,
    val sessions: Int, val loggedSets: Int, val plans: Int, val occurrences: Int, val commands: Int
)

data class LegacyOwnershipPlan(
    val profileId: String,
    val firebaseUid: String,
    val sourceHumanUserId: String,
    val targetHumanUserId: String,
    val targetProfile: UserProfile,
    val bodyWeights: List<BodyWeight>,
    val tape: List<TapeMeasurement>,
    val exercises: List<Exercise>,
    val templates: List<WorkoutTemplate>,
    val templateExercises: List<WorkoutTemplateExercise>,
    val templateSets: List<WorkoutTemplateSet>,
    val sessions: List<WorkoutSession>,
    val loggedSets: List<LoggedSet>,
    val plans: List<TrainingPlan>,
    val occurrences: List<PlannedWorkout>,
    val commands: List<CommandQueueEntity>,
    val replacements: List<CommandQueueEntity>,
    val rewrittenBackup: ActiveWorkoutBackup?,
    val now: Long,
    internal val failAfterParentMigrationForTest: Boolean = false
) {
    val totals = LegacyOwnershipTotals(bodyWeights.size + tape.size, exercises.size, templates.size,
        sessions.size, loggedSets.size, plans.size, occurrences.size, commands.size)

    fun validateGraph() {
        require(profileId == firebaseUid)
        require(targetProfile.id == profileId && targetProfile.firebaseUid == firebaseUid)
        require(targetProfile.humanUserId == targetHumanUserId)
        require(exercises.all { it.isCustom && it.humanUserId == sourceHumanUserId })
        val templateIds = templates.map { it.id }.toSet()
        require(templateExercises.all { it.templateId in templateIds && it.humanUserId == sourceHumanUserId })
        val templateExerciseIds = templateExercises.map { it.id }.toSet()
        require(templateSets.all { it.templateExerciseId in templateExerciseIds && it.humanUserId == sourceHumanUserId })
        val sessionIds = sessions.map { it.id }.toSet()
        require(loggedSets.all { it.sessionId in sessionIds && it.humanUserId == sourceHumanUserId })
        val planIds = plans.map { it.id }.toSet()
        require(occurrences.all { it.seriesId in planIds && (it.linkedSessionId == null || it.linkedSessionId in sessionIds) })
        require(listOf(bodyWeights.map { it.globalId }, tape.map { it.globalId }, exercises.map { it.globalId },
            templates.map { it.globalId }, sessions.map { it.globalId }, loggedSets.map { it.globalId },
            plans.map { it.globalId }, occurrences.map { it.globalId }).all { ids -> ids.filter { it.isNotBlank() }.distinct().size == ids.count { it.isNotBlank() } })
        require(replacements.size == commands.count { it.status in ACTIONABLE_COMMAND_STATES })
    }
}

private val ACTIONABLE_COMMAND_STATES = setOf("PENDING", "PROCESSING", "FAILED")
private val SAFE_COMMANDS = setOf(
    "SettingsUpdated" to "USER_PROFILE",
    "MeasurementLogged" to "BODY_WEIGHT", "MeasurementUpdated" to "BODY_WEIGHT", "MeasurementDeleted" to "BODY_WEIGHT",
    "MeasurementLogged" to "TAPE_MEASUREMENT", "MeasurementUpdated" to "TAPE_MEASUREMENT", "MeasurementDeleted" to "TAPE_MEASUREMENT",
    "ExerciseCreated" to "EXERCISE", "ExerciseUpdated" to "EXERCISE", "ExerciseDeleted" to "EXERCISE",
    "WorkoutTemplateCreated" to "WORKOUT_TEMPLATE", "WorkoutTemplateUpdated" to "WORKOUT_TEMPLATE", "WorkoutTemplateDeleted" to "WORKOUT_TEMPLATE",
    "WorkoutStarted" to "WORKOUT_SESSION", "WorkoutCompleted" to "WORKOUT_SESSION", "WorkoutUpdated" to "WORKOUT_SESSION", "WorkoutDeleted" to "WORKOUT_SESSION",
    "SetCompleted" to "LOGGED_SET",
    "PlannerCreated" to "TRAINING_PLAN", "PlannerSeriesUpdated" to "TRAINING_PLAN",
    "PlannerOccurrenceCreated" to "PLANNED_WORKOUT", "PlannerOccurrenceUpdated" to "PLANNED_WORKOUT",
    "PlannerOccurrenceDeleted" to "PLANNED_WORKOUT", "PlannerOccurrenceCompleted" to "PLANNED_WORKOUT"
)

suspend fun StrengthDao.prepareVerifiedLegacyMigration(
    profileId: String,
    firebaseUid: String,
    targetHumanUserId: String,
    targetProfile: UserProfile,
    now: Long = System.currentTimeMillis()
): LegacyOwnershipPlan {
    val source = requireNotNull(getUserProfile(profileId))
    require(source.firebaseUid != null && source.firebaseUid == firebaseUid && profileId == firebaseUid)
    require(source.humanUserId.isNotBlank() && source.humanUserId != targetHumanUserId)
    require(getUserProfileByHumanUserId(targetHumanUserId)?.id in listOf(null, profileId))
    val sourceHuman = source.humanUserId
    val weights = legacyBodyWeights(profileId, sourceHuman)
    val tape = legacyTapeMeasurements(profileId, sourceHuman)
    val exercises = legacyCustomExercises(sourceHuman)
    val templates = legacyTemplates(profileId, sourceHuman)
    val templateExercises = legacyTemplateExercises(sourceHuman)
    val templateSets = legacyTemplateSets(sourceHuman)
    val sessions = legacySessions(profileId, sourceHuman)
    val sets = legacyLoggedSets(sourceHuman)
    val plans = legacyTrainingPlans(profileId, sourceHuman)
    val occurrences = legacyPlannedWorkouts(profileId, sourceHuman)
    val commands = legacyCommands(sourceHuman)
    val validEntities = mapOf(
        "USER_PROFILE" to setOf(source.globalId), "BODY_WEIGHT" to weights.map { it.globalId }.toSet(),
        "TAPE_MEASUREMENT" to tape.map { it.globalId }.toSet(), "EXERCISE" to exercises.map { it.globalId }.toSet(),
        "WORKOUT_TEMPLATE" to templates.map { it.globalId }.toSet(), "WORKOUT_SESSION" to sessions.map { it.globalId }.toSet(),
        "LOGGED_SET" to sets.map { it.globalId }.toSet(), "TRAINING_PLAN" to plans.map { it.globalId }.toSet(),
        "PLANNED_WORKOUT" to occurrences.map { it.globalId }.toSet()
    )
    val actionable = commands.filter { it.status in ACTIONABLE_COMMAND_STATES }
    val replacements = actionable.map { old ->
        require((old.commandType to old.entityType) in SAFE_COMMANDS)
        require(old.entityGlobalId in (validEntities[old.entityType] ?: emptySet()))
        val payload = JSONObject(old.payloadJson)
        require(!payload.has("humanUserId") && !payload.has("firebaseUid") && !payload.has("email"))
        val hash = MessageDigest.getInstance("SHA-256").digest("${old.commandId}:$targetHumanUserId".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(12)
        old.copy(id = 0, commandId = "cmd_mig_${hash}_${old.commandId.takeLast(12)}", humanUserId = targetHumanUserId,
            attempts = 0, lastAttemptAt = null, status = "PENDING", errorMessage = null, nextRetryAt = null)
    }
    val backup = getActiveWorkoutBackup()?.let { rewriteActiveWorkoutOwnership(it, profileId, sourceHuman, targetHumanUserId) }
    return LegacyOwnershipPlan(profileId, firebaseUid, sourceHuman, targetHumanUserId, targetProfile,
        weights, tape, exercises, templates, templateExercises, templateSets, sessions, sets, plans, occurrences,
        commands, replacements, backup, now).also { it.validateGraph() }
}

internal fun rewriteActiveWorkoutOwnership(
    backup: ActiveWorkoutBackup,
    profileId: String,
    sourceHumanUserId: String,
    targetHumanUserId: String
): ActiveWorkoutBackup {
    val exercises = JSONArray(backup.exercisesJson)
    for (index in 0 until exercises.length()) {
        val item = exercises.getJSONObject(index)
        if (item.optBoolean("isCustom", false)) {
            require(item.optString("userId") == sourceHumanUserId)
            item.put("userId", targetHumanUserId)
        } else require(item.optString("userId") in setOf("", "global", sourceHumanUserId))
    }
    // Parsing is intentional even though sets currently carry no owner: malformed recovery blocks migration.
    val sets = JSONObject(backup.setsJson)
    sets.keys().forEach { key -> require(sets.get(key) is JSONArray) }
    val metadata = JSONObject(backup.exerciseMetadataJson)
    metadata.keys().forEach { key -> require(metadata.get(key) is JSONObject) }
    val recovery = metadata.optJSONObject("__global_recovery__")
    if (recovery != null) {
        require(recovery.optString("workoutOwnerUserId") == profileId)
        require(recovery.optString("workoutOwnerHumanUserId") == sourceHumanUserId)
        recovery.put("workoutOwnerHumanUserId", targetHumanUserId)
    }
    return backup.copy(exercisesJson = exercises.toString(), setsJson = sets.toString(), exerciseMetadataJson = metadata.toString())
}
