package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StrengthDao {

    // User Profile
    @Query("SELECT * FROM user_profile WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getUserProfile(id: String): UserProfile?

    @Query("SELECT * FROM user_profile WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun getUserProfileFlow(id: String): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Query("UPDATE user_profile SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteUserProfile(id: String, deletedTime: Long)

    @Query("DELETE FROM user_profile WHERE id = :id")
    suspend fun deleteUserProfile(id: String)


    // Body Weight
    @Query("SELECT * FROM body_weight WHERE deletedAt IS NULL ORDER BY date DESC")
    fun getAllBodyWeights(): Flow<List<BodyWeight>>

    @Query("SELECT * FROM body_weight WHERE (userId = :userId OR (userId IS NULL AND :userId = 'offline')) AND deletedAt IS NULL ORDER BY date DESC")
    fun getBodyWeightsForUser(userId: String): Flow<List<BodyWeight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyWeight(weight: BodyWeight)

    @Query("SELECT * FROM body_weight WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getBodyWeightById(id: Int): BodyWeight?

    @Query("UPDATE body_weight SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteBodyWeight(id: Int, deletedTime: Long)

    @Query("DELETE FROM body_weight WHERE id = :id")
    suspend fun deleteBodyWeight(id: Int)

    @Query("UPDATE body_weight SET userId = :newUserId, humanUserId = :newHumanUserId, revision = revision + 1, syncStatus = 'PENDING_UPLOAD' WHERE userId IS NULL OR userId = 'offline'")
    suspend fun linkBodyWeightToUser(newUserId: String, newHumanUserId: String)


    // Tape Measurements
    @Query("SELECT * FROM tape_measurement WHERE deletedAt IS NULL ORDER BY date DESC")
    fun getAllTapeMeasurements(): Flow<List<TapeMeasurement>>

    @Query("SELECT * FROM tape_measurement WHERE (userId = :userId OR (userId IS NULL AND :userId = 'offline')) AND deletedAt IS NULL ORDER BY date DESC")
    fun getTapeMeasurementsForUser(userId: String): Flow<List<TapeMeasurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTapeMeasurement(measurement: TapeMeasurement)

    @Query("SELECT * FROM tape_measurement WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getTapeMeasurementById(id: Int): TapeMeasurement?

    @Query("UPDATE tape_measurement SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteTapeMeasurement(id: Int, deletedTime: Long)

    @Query("DELETE FROM tape_measurement WHERE id = :id")
    suspend fun deleteTapeMeasurement(id: Int)

    @Query("UPDATE tape_measurement SET userId = :newUserId, humanUserId = :newHumanUserId, revision = revision + 1, syncStatus = 'PENDING_UPLOAD' WHERE userId IS NULL OR userId = 'offline'")
    suspend fun linkTapeMeasurementToUser(newUserId: String, newHumanUserId: String)


    // Exercises
    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getExerciseById(id: String): Exercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<Exercise>)

    @Query("UPDATE exercise SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteExercise(id: String, deletedTime: Long)

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun deleteExercise(id: String)


    // Workout Templates
    @Query("SELECT * FROM workout_template WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM workout_template WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getTemplateById(id: Int): WorkoutTemplate?

    @Query("SELECT * FROM workout_template WHERE (userId = :userId OR (userId IS NULL AND :userId = 'offline')) AND deletedAt IS NULL ORDER BY name ASC")
    fun getTemplatesForUser(userId: String): Flow<List<WorkoutTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WorkoutTemplate): Long

    @Query("UPDATE workout_template SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteTemplate(id: Int, deletedTime: Long)

    @Query("DELETE FROM workout_template WHERE id = :id")
    suspend fun deleteTemplate(id: Int)

    @Query("UPDATE workout_template SET userId = :newUserId, humanUserId = :newHumanUserId, revision = revision + 1, syncStatus = 'PENDING_UPLOAD' WHERE userId IS NULL OR userId = 'offline'")
    suspend fun linkWorkoutTemplatesToUser(newUserId: String, newHumanUserId: String)


    // Workout Sessions
    @Query("SELECT * FROM workout_session WHERE deletedAt IS NULL ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_session WHERE (userId = :userId OR (userId IS NULL AND :userId = 'offline')) AND deletedAt IS NULL ORDER BY startTime DESC")
    fun getSessionsForUser(userId: String): Flow<List<WorkoutSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSession): Long

    @Query("SELECT * FROM workout_session WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getSessionById(id: Int): WorkoutSession?

    @Query("UPDATE workout_session SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteSession(id: Int, deletedTime: Long)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteSession(id: Int)

    @Query("UPDATE workout_session SET userId = :newUserId, humanUserId = :newHumanUserId, revision = revision + 1, syncStatus = 'PENDING_UPLOAD' WHERE userId IS NULL OR userId = 'offline'")
    suspend fun linkWorkoutSessionsToUser(newUserId: String, newHumanUserId: String)


    // Logged Sets
    @Query("SELECT * FROM logged_set WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY id ASC")
    fun getSetsForSession(sessionId: Int): Flow<List<LoggedSet>>

    @Query("SELECT * FROM logged_set WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY id ASC")
    suspend fun getSetsForSessionSync(sessionId: Int): List<LoggedSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoggedSet(set: LoggedSet)

    @Query("SELECT * FROM logged_set WHERE id = :id LIMIT 1")
    suspend fun getLoggedSetById(id: Int): LoggedSet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoggedSets(sets: List<LoggedSet>)

    @Query("UPDATE logged_set SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE sessionId = :sessionId")
    suspend fun softDeleteSetsForSession(sessionId: Int, deletedTime: Long)

    @Query("DELETE FROM logged_set WHERE sessionId = :sessionId")
    suspend fun deleteSetsForSession(sessionId: Int)

    @Query("SELECT * FROM logged_set WHERE exerciseId = :exerciseId AND isCompleted = 1 AND deletedAt IS NULL ORDER BY id DESC")
    fun getCompletedSetsForExercise(exerciseId: String): Flow<List<LoggedSet>>

    @Query("SELECT ls.* FROM logged_set ls INNER JOIN workout_session ws ON ls.sessionId = ws.id WHERE ls.exerciseId = :exerciseId AND ls.isCompleted = 1 AND (ws.userId = :userId OR (ws.userId IS NULL AND :userId = 'offline')) AND ls.deletedAt IS NULL AND ws.deletedAt IS NULL ORDER BY ls.id DESC")
    fun getCompletedSetsForExerciseForUser(exerciseId: String, userId: String): Flow<List<LoggedSet>>

    @Query("SELECT DISTINCT sessionId FROM logged_set WHERE exerciseId = :exerciseId AND isCompleted = 1 AND deletedAt IS NULL ORDER BY sessionId DESC LIMIT 1")
    suspend fun getLastSessionIdForExercise(exerciseId: String): Int?

    @Query("SELECT DISTINCT ls.sessionId FROM logged_set ls INNER JOIN workout_session ws ON ls.sessionId = ws.id WHERE ls.exerciseId = :exerciseId AND ls.isCompleted = 1 AND (ws.userId = :userId OR (ws.userId IS NULL AND :userId = 'offline')) AND ls.deletedAt IS NULL AND ws.deletedAt IS NULL ORDER BY ls.sessionId DESC LIMIT 1")
    suspend fun getLastSessionIdForExerciseForUser(exerciseId: String, userId: String): Int?

    @Query("SELECT * FROM logged_set WHERE exerciseId = :exerciseId AND sessionId = :sessionId AND isCompleted = 1 AND deletedAt IS NULL ORDER BY id ASC")
    suspend fun getSetsForExerciseInSession(exerciseId: String, sessionId: Int): List<LoggedSet>

    @Query("SELECT * FROM logged_set WHERE deletedAt IS NULL ORDER BY id ASC")
    fun getAllLoggedSets(): Flow<List<LoggedSet>>

    @Query("SELECT ls.* FROM logged_set ls INNER JOIN workout_session ws ON ls.sessionId = ws.id WHERE (ws.userId = :userId OR (ws.userId IS NULL AND :userId = 'offline')) AND ls.deletedAt IS NULL AND ws.deletedAt IS NULL ORDER BY ls.id ASC")
    fun getLoggedSetsForUser(userId: String): Flow<List<LoggedSet>>


    // Workout Template Exercises
    @Query("SELECT * FROM workout_template_exercise WHERE templateId = :templateId AND deletedAt IS NULL ORDER BY position ASC")
    fun getTemplateExercises(templateId: Int): Flow<List<WorkoutTemplateExercise>>

    @Query("SELECT * FROM workout_template_exercise WHERE templateId = :templateId AND deletedAt IS NULL ORDER BY position ASC")
    suspend fun getTemplateExercisesSync(templateId: Int): List<WorkoutTemplateExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateExercise(exercise: WorkoutTemplateExercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateExercises(exercises: List<WorkoutTemplateExercise>)

    @Query("UPDATE workout_template_exercise SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE templateId = :templateId")
    suspend fun softDeleteTemplateExercisesForTemplate(templateId: Int, deletedTime: Long)

    @Query("UPDATE workout_template_exercise SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteTemplateExerciseById(id: Int, deletedTime: Long)

    @Query("DELETE FROM workout_template_exercise WHERE templateId = :templateId")
    suspend fun deleteTemplateExercisesForTemplate(templateId: Int)

    @Query("SELECT * FROM workout_template_exercise WHERE id = :id LIMIT 1")
    suspend fun getTemplateExerciseById(id: Int): WorkoutTemplateExercise?

    @Query("DELETE FROM workout_template_exercise WHERE id = :id")
    suspend fun deleteTemplateExerciseById(id: Int)


    // Workout Template Sets
    @Query("SELECT * FROM workout_template_set WHERE templateExerciseId = :templateExerciseId AND deletedAt IS NULL ORDER BY position ASC")
    fun getTemplateSets(templateExerciseId: Int): Flow<List<WorkoutTemplateSet>>

    @Query("SELECT * FROM workout_template_set WHERE templateExerciseId = :templateExerciseId AND deletedAt IS NULL ORDER BY position ASC")
    suspend fun getTemplateSetsSync(templateExerciseId: Int): List<WorkoutTemplateSet>

    @Query("SELECT s.* FROM workout_template_set s INNER JOIN workout_template_exercise e ON s.templateExerciseId = e.id WHERE e.templateId = :templateId AND s.deletedAt IS NULL AND e.deletedAt IS NULL ORDER BY e.position ASC, s.position ASC")
    suspend fun getTemplateSetsForTemplateSync(templateId: Int): List<WorkoutTemplateSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateSet(set: WorkoutTemplateSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateSets(sets: List<WorkoutTemplateSet>)

    @Query("UPDATE workout_template_set SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE templateExerciseId = :templateExerciseId")
    suspend fun softDeleteTemplateSetsForExercise(templateExerciseId: Int, deletedTime: Long)

    @Query("UPDATE workout_template_set SET deletedAt = :deletedTime, revision = revision + 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteTemplateSetById(id: Int, deletedTime: Long)

    @Query("DELETE FROM workout_template_set WHERE templateExerciseId = :templateExerciseId")
    suspend fun deleteTemplateSetsForExercise(templateExerciseId: Int)

    @Query("SELECT * FROM workout_template_set WHERE id = :id LIMIT 1")
    suspend fun getTemplateSetById(id: Int): WorkoutTemplateSet?

    @Query("DELETE FROM workout_template_set WHERE id = :id")
    suspend fun deleteTemplateSetById(id: Int)


    // ==========================================
    // SYNC UTILITY - PENDING RECORD SELECTORS
    // ==========================================

    @Query("SELECT * FROM user_profile WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadProfiles(): List<UserProfile>

    @Query("SELECT * FROM body_weight WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadBodyWeights(): List<BodyWeight>

    @Query("SELECT * FROM body_weight WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteBodyWeights(): List<BodyWeight>

    @Query("SELECT * FROM tape_measurement WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadTapeMeasurements(): List<TapeMeasurement>

    @Query("SELECT * FROM tape_measurement WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteTapeMeasurements(): List<TapeMeasurement>

    @Query("SELECT * FROM exercise WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadExercises(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteExercises(): List<Exercise>

    @Query("SELECT * FROM workout_template WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadTemplates(): List<WorkoutTemplate>

    @Query("SELECT * FROM workout_template WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteTemplates(): List<WorkoutTemplate>

    @Query("SELECT * FROM workout_template_exercise WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadTemplateExercises(): List<WorkoutTemplateExercise>

    @Query("SELECT * FROM workout_template_exercise WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteTemplateExercises(): List<WorkoutTemplateExercise>

    @Query("SELECT * FROM workout_template_set WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadTemplateSets(): List<WorkoutTemplateSet>

    @Query("SELECT * FROM workout_template_set WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteTemplateSets(): List<WorkoutTemplateSet>

    @Query("SELECT * FROM workout_session WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadSessions(): List<WorkoutSession>

    @Query("SELECT * FROM workout_session WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteSessions(): List<WorkoutSession>

    @Query("SELECT * FROM logged_set WHERE syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadLoggedSets(): List<LoggedSet>

    @Query("SELECT * FROM logged_set WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteLoggedSets(): List<LoggedSet>


    // ==========================================
    // SYNC UTILITY - SYNC STATUS MARKERS
    // ==========================================

    @Query("UPDATE user_profile SET syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markProfileSynced(id: String, timestamp: Long)

    @Query("UPDATE body_weight SET syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markBodyWeightSynced(id: Int, timestamp: Long)

    @Query("UPDATE tape_measurement SET syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markTapeMeasurementSynced(id: Int, timestamp: Long)

    @Query("UPDATE exercise SET syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markExerciseSynced(id: String, timestamp: Long)

    @Query("UPDATE workout_template SET syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markTemplateSynced(id: Int, timestamp: Long)

    @Query("UPDATE workout_template_exercise SET syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markTemplateExerciseSynced(id: Int, timestamp: Long)

    @Query("UPDATE workout_template_set SET syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markTemplateSetSynced(id: Int, timestamp: Long)

    @Query("UPDATE workout_session SET syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markSessionSynced(id: Int, timestamp: Long)

    @Query("UPDATE logged_set SET syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun markLoggedSetSynced(id: Int, timestamp: Long)


    // ==========================================
    // SYNC UTILITY - FAILURE / CONFLICT MARKERS
    // ==========================================

    @Query("UPDATE user_profile SET syncStatus = 'FAILED', conflictState = :error WHERE id = :id")
    suspend fun markProfileFailed(id: String, error: String)

    @Query("UPDATE body_weight SET syncStatus = 'FAILED', conflictState = :error WHERE id = :id")
    suspend fun markBodyWeightFailed(id: Int, error: String)

    @Query("UPDATE tape_measurement SET syncStatus = 'FAILED', conflictState = :error WHERE id = :id")
    suspend fun markTapeMeasurementFailed(id: Int, error: String)

    @Query("UPDATE user_profile SET syncStatus = 'CONFLICT', conflictState = :conflictData WHERE id = :id")
    suspend fun markProfileConflict(id: String, conflictData: String)

    @Query("UPDATE body_weight SET syncStatus = 'CONFLICT', conflictState = :conflictData WHERE id = :id")
    suspend fun markBodyWeightConflict(id: Int, conflictData: String)


    // ==========================================
    // GLOBAL ID SELECTORS FOR SYNC
    // ==========================================

    @Query("SELECT * FROM user_profile WHERE globalId = :globalId LIMIT 1")
    suspend fun getUserProfileByGlobalId(globalId: String): UserProfile?

    @Query("SELECT * FROM body_weight WHERE globalId = :globalId LIMIT 1")
    suspend fun getBodyWeightByGlobalId(globalId: String): BodyWeight?

    @Query("SELECT * FROM tape_measurement WHERE globalId = :globalId LIMIT 1")
    suspend fun getTapeMeasurementByGlobalId(globalId: String): TapeMeasurement?

    @Query("SELECT * FROM exercise WHERE globalId = :globalId LIMIT 1")
    suspend fun getExerciseByGlobalId(globalId: String): Exercise?

    @Query("SELECT * FROM workout_template WHERE globalId = :globalId LIMIT 1")
    suspend fun getTemplateByGlobalId(globalId: String): WorkoutTemplate?

    @Query("SELECT * FROM workout_template_exercise WHERE globalId = :globalId LIMIT 1")
    suspend fun getTemplateExerciseByGlobalId(globalId: String): WorkoutTemplateExercise?

    @Query("SELECT * FROM workout_template_set WHERE globalId = :globalId LIMIT 1")
    suspend fun getTemplateSetByGlobalId(globalId: String): WorkoutTemplateSet?

    @Query("SELECT * FROM workout_session WHERE globalId = :globalId LIMIT 1")
    suspend fun getSessionByGlobalId(globalId: String): WorkoutSession?

    @Query("SELECT * FROM logged_set WHERE globalId = :globalId LIMIT 1")
    suspend fun getLoggedSetByGlobalId(globalId: String): LoggedSet?


    // ==========================================
    // COMMAND QUEUE SUPPORT
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueCommand(command: CommandQueueEntity)

    @Query("SELECT * FROM command_queue WHERE status = 'PENDING' OR (status = 'FAILED' AND attempts < 5 AND (nextRetryAt IS NULL OR nextRetryAt <= :now)) ORDER BY createdAt ASC")
    suspend fun getPendingCommands(now: Long): List<CommandQueueEntity>

    @Query("SELECT * FROM command_queue ORDER BY createdAt DESC")
    suspend fun getAllCommands(): List<CommandQueueEntity>

    @Query("UPDATE command_queue SET status = :status, attempts = :attempts, lastAttemptAt = :lastAttemptAt, nextRetryAt = :nextRetryAt, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateCommandStatus(id: Int, status: String, attempts: Int, lastAttemptAt: Long?, nextRetryAt: Long?, errorMessage: String?)

    @Query("UPDATE command_queue SET status = 'PROCESSING', lastAttemptAt = :timestamp, attempts = attempts + 1 WHERE id = :id")
    suspend fun markCommandProcessing(id: Int, timestamp: Long)

    @Query("UPDATE command_queue SET status = 'SUCCEEDED' WHERE id = :id")
    suspend fun markCommandSucceeded(id: Int)

    @Query("UPDATE command_queue SET status = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markCommandFailed(id: Int, error: String)

    @Query("UPDATE command_queue SET status = 'POISONED', errorMessage = :error WHERE id = :id")
    suspend fun markCommandPoisoned(id: Int, error: String)
}
