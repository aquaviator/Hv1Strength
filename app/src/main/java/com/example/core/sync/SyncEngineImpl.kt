package com.example.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.core.identity.DeviceIdGenerator
import com.example.core.identity.HumanUserIdGenerator
import com.example.core.versioning.VersionedEntity
import com.example.data.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class SyncEngineImpl(
    private val context: Context,
    private val repository: StrengthRepository
) : SyncEngine {

    private val TAG = "SyncEngineImpl"
    private val syncScope = CoroutineScope(Dispatchers.IO)

    private val _connectivity = MutableStateFlow(getInitialConnectivity())
    override val connectivity: StateFlow<ConnectivityState> = _connectivity

    private val _activeSyncing = MutableStateFlow(false)
    override val activeSyncing: StateFlow<Boolean> = _activeSyncing

    private val _pendingUploadsCount = MutableStateFlow(0)
    override val pendingUploadsCount: StateFlow<Int> = _pendingUploadsCount

    private var firestore: FirebaseFirestore? = null

    private data class DeferredChild(
        val entityType: String,
        val doc: DocumentSnapshot,
        val localEntity: VersionedEntity?
    )

    private val pendingDependencies = ConcurrentHashMap<String, CopyOnWriteArrayList<DeferredChild>>()

    init {
        if (com.example.StrengthApplication.isFirebaseConfigured) {
            try {
                firestore = FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore not initialized. Operating in offline/fallback mode.", e)
            }
        } else {
            Log.w(TAG, "Firebase is not configured. Operating in offline/fallback mode.")
        }
        updatePendingCounts()
    }

    private fun getInitialConnectivity(): ConnectivityState {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return ConnectivityState.OFFLINE
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return ConnectivityState.OFFLINE
        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            ConnectivityState.ONLINE
        } else {
            ConnectivityState.OFFLINE
        }
    }

    fun updateConnectivity(state: ConnectivityState) {
        _connectivity.value = state
        if (state == ConnectivityState.OFFLINE) {
            SyncManager.updateStatus("Offline")
        } else {
            SyncManager.updateStatus("Idle")
        }
    }

    private fun updatePendingCounts() {
        _pendingUploadsCount.value = 0
    }

    private fun storePendingDependency(
        parentGlobalId: String,
        entityType: String,
        doc: DocumentSnapshot,
        localEntity: VersionedEntity?
    ) {
        val list = pendingDependencies.getOrPut(parentGlobalId) { CopyOnWriteArrayList() }
        list.add(DeferredChild(entityType, doc, localEntity))
        
        val warning = "Parent missing: $entityType (ID: ${doc.id}) is waiting for parent globalId $parentGlobalId."
        Log.w(TAG, warning)
        SyncManager.addParentWarning(warning)
    }

    private suspend fun resolvePendingDependencies(parentGlobalId: String, dao: StrengthDao) {
        val list = pendingDependencies.remove(parentGlobalId) ?: return
        Log.i(TAG, "Resolving ${list.size} pending dependencies for parent $parentGlobalId")
        
        // Remove warnings matching this parent
        val activeWarnings = SyncManager.parentWarnings.value.filterNot { it.contains("parent globalId $parentGlobalId") }
        SyncManager.clearParentWarnings()
        for (w in activeWarnings) {
            SyncManager.addParentWarning(w)
        }

        for (deferred in list) {
            try {
                Log.i(TAG, "Retrying deferred child: entityType=${deferred.entityType}, globalId=${deferred.doc.id}")
                if (deferred.localEntity == null) {
                    insertRemoteDocument(deferred.entityType, deferred.doc, dao)
                } else {
                    updateLocalDocument(deferred.entityType, deferred.doc, deferred.localEntity, dao)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving pending dependency for child ${deferred.doc.id}", e)
            }
        }
    }

    override suspend fun synchronizeAll(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_activeSyncing.value) return@withContext Result.success(Unit)
        _activeSyncing.value = true
        SyncManager.updateStatus("Uploading")

        try {
            val db = StrengthDatabase.getDatabase(context, syncScope)
            val dao = db.strengthDao()

            // 1. Check internet / firestore connectivity
            if (getInitialConnectivity() == ConnectivityState.OFFLINE || firestore == null) {
                updateConnectivity(ConnectivityState.OFFLINE)
                _activeSyncing.value = false
                return@withContext Result.failure(Exception("Network is offline or Firestore is unavailable"))
            }
            updateConnectivity(ConnectivityState.ONLINE)

            val humanUserId = HumanUserIdGenerator.getOrGenerateOfflineHumanId(context)
            val deviceId = DeviceIdGenerator.getOrGenerateDeviceId(context)

            // 2. Process Command Queue (Upload)
            val now = System.currentTimeMillis()
            val pendingCommands = repository.getPendingCommands(now)
            SyncManager.updateQueueSize(pendingCommands.size)
            SyncManager.updatePendingUploads(pendingCommands.size)

            var successfulUploads = 0

            for (command in pendingCommands) {
                val nextAttempts = command.attempts + 1
                try {
                    repository.updateCommandStatus(
                        id = command.id,
                        status = "PROCESSING",
                        attempts = nextAttempts,
                        lastAttemptAt = System.currentTimeMillis(),
                        nextRetryAt = command.nextRetryAt,
                        errorMessage = command.errorMessage
                    )

                    uploadEntityForCommand(command, humanUserId, deviceId)

                    repository.updateCommandStatus(
                        id = command.id,
                        status = "SUCCEEDED",
                        attempts = nextAttempts,
                        lastAttemptAt = System.currentTimeMillis(),
                        nextRetryAt = null,
                        errorMessage = null
                    )
                    successfulUploads++
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing command ${command.commandId}", e)
                    val errorMsg = e.localizedMessage ?: "Unknown error"
                    SyncManager.updateLastError(errorMsg)

                    if (nextAttempts >= 5) {
                        repository.updateCommandStatus(
                            id = command.id,
                            status = "POISONED",
                            attempts = nextAttempts,
                            lastAttemptAt = System.currentTimeMillis(),
                            nextRetryAt = null,
                            errorMessage = "Poisoned after 5 failed attempts: $errorMsg"
                        )
                    } else {
                        // Exponential backoff: 10s * 2^attempts
                        val backoffMs = 1000L * 10L * Math.pow(2.0, nextAttempts.toDouble()).toLong()
                        val retryAt = System.currentTimeMillis() + backoffMs
                        repository.updateCommandStatus(
                            id = command.id,
                            status = "FAILED",
                            attempts = nextAttempts,
                            lastAttemptAt = System.currentTimeMillis(),
                            nextRetryAt = retryAt,
                            errorMessage = errorMsg
                        )
                    }
                }
            }

            if (successfulUploads > 0) {
                SyncManager.updateLastSuccessfulUpload(System.currentTimeMillis())
            }

            // Update remaining queue size
            val updatedCommands = repository.getPendingCommands(System.currentTimeMillis())
            SyncManager.updateQueueSize(updatedCommands.size)
            SyncManager.updatePendingUploads(updatedCommands.size)

            // 3. Download Remote Changes
            SyncManager.updateStatus("Downloading")
            downloadRemoteChanges(humanUserId, deviceId, dao)

            // 4. Mark successful synchronization
            SyncManager.updateLastSync(System.currentTimeMillis())
            SyncManager.updateStatus("Idle")
            _activeSyncing.value = false
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Sync process failed", e)
            SyncManager.updateLastError(e.localizedMessage)
            SyncManager.updateStatus("Error")
            _activeSyncing.value = false
            Result.failure(e)
        }
    }

    private suspend fun uploadEntityForCommand(
        command: CommandQueueEntity,
        humanUserId: String,
        deviceId: String
    ) {
        val fs = firestore ?: throw Exception("Firestore not available")
        val entityGlobalId = command.entityGlobalId
        val entityType = command.entityType
        val now = System.currentTimeMillis()

        val docData = mutableMapOf<String, Any?>()
        var docRef: com.google.firebase.firestore.DocumentReference? = null
        var afterCommit: (suspend () -> Unit)? = null

        when (entityType) {
            "USER_PROFILE" -> {
                val profile = repository.getUserProfileByGlobalId(entityGlobalId)
                if (profile != null) {
                    docData["globalId"] = profile.globalId
                    docData["humanUserId"] = profile.humanUserId
                    docData["displayName"] = profile.displayName
                    docData["email"] = profile.email
                    docData["photoUrl"] = profile.photoUrl
                    docData["authProvider"] = profile.authProvider
                    docData["preferredUnits"] = profile.preferredUnits
                    docData["heightCm"] = profile.heightCm
                    docData["dateOfBirth"] = profile.dateOfBirth
                    docData["sex"] = profile.sex
                    docData["trainingExperience"] = profile.trainingExperience
                    docData["createdAt"] = profile.createdAt
                    docData["updatedAt"] = profile.updatedAt
                    docData["revision"] = profile.revision
                    docData["deletedAt"] = profile.deletedAt
                    docData["originDeviceId"] = profile.originDeviceId
                    docData["lastSyncedAt"] = now

                    docRef = fs.collection("users").document(humanUserId)
                        .collection("profile").document("main")

                    afterCommit = {
                        repository.markProfileSynced(profile.id, now)
                    }
                }
            }
            "BODY_WEIGHT" -> {
                val weight = repository.getBodyWeightByGlobalId(entityGlobalId)
                if (weight != null) {
                    docData["globalId"] = weight.globalId
                    docData["humanUserId"] = weight.humanUserId
                    docData["weight"] = weight.weight
                    docData["date"] = weight.date
                    docData["bodyFat"] = weight.bodyFat
                    docData["leanMass"] = weight.leanMass
                    docData["fatMass"] = weight.fatMass
                    docData["bmi"] = weight.bmi
                    docData["userId"] = weight.userId
                    docData["createdAt"] = weight.createdAt
                    docData["updatedAt"] = weight.updatedAt
                    docData["revision"] = weight.revision
                    docData["deletedAt"] = weight.deletedAt
                    docData["originDeviceId"] = weight.originDeviceId
                    docData["lastSyncedAt"] = now

                    docRef = fs.collection("users").document(humanUserId)
                        .collection("weight").document(weight.globalId)

                    afterCommit = {
                        repository.markBodyWeightSynced(weight.id, now)
                    }
                }
            }
            "TAPE_MEASUREMENT" -> {
                val tape = repository.getTapeMeasurementByGlobalId(entityGlobalId)
                if (tape != null) {
                    docData["globalId"] = tape.globalId
                    docData["humanUserId"] = tape.humanUserId
                    docData["chest"] = tape.chest
                    docData["waist"] = tape.waist
                    docData["hips"] = tape.hips
                    docData["bicepLeft"] = tape.bicepLeft
                    docData["bicepRight"] = tape.bicepRight
                    docData["thighLeft"] = tape.thighLeft
                    docData["thighRight"] = tape.thighRight
                    docData["userId"] = tape.userId
                    docData["date"] = tape.date
                    docData["createdAt"] = tape.createdAt
                    docData["updatedAt"] = tape.updatedAt
                    docData["revision"] = tape.revision
                    docData["deletedAt"] = tape.deletedAt
                    docData["originDeviceId"] = tape.originDeviceId
                    docData["lastSyncedAt"] = now

                    docRef = fs.collection("users").document(humanUserId)
                        .collection("tape").document(tape.globalId)

                    afterCommit = {
                        repository.markTapeMeasurementSynced(tape.id, now)
                    }
                }
            }
            "CUSTOM_EXERCISE", "EXERCISE" -> {
                val exercise = repository.getExerciseByGlobalId(entityGlobalId)
                if (exercise != null) {
                    docData["globalId"] = exercise.globalId
                    docData["id"] = exercise.id
                    docData["name"] = exercise.name
                    docData["category"] = exercise.category
                    docData["isCustom"] = exercise.isCustom
                    docData["humanUserId"] = exercise.humanUserId
                    docData["createdAt"] = exercise.createdAt
                    docData["updatedAt"] = exercise.updatedAt
                    docData["revision"] = exercise.revision
                    docData["deletedAt"] = exercise.deletedAt
                    docData["originDeviceId"] = exercise.originDeviceId
                    docData["lastSyncedAt"] = now

                    docRef = fs.collection("users").document(humanUserId)
                        .collection("customExercises").document(exercise.globalId)

                    afterCommit = {
                        repository.markExerciseSynced(exercise.id, now)
                    }
                }
            }
            "WORKOUT_TEMPLATE" -> {
                val template = repository.getTemplateByGlobalId(entityGlobalId)
                if (template != null) {
                    docData["globalId"] = template.globalId
                    docData["name"] = template.name
                    docData["exerciseIdsJson"] = template.exerciseIdsJson
                    docData["humanUserId"] = template.humanUserId
                    docData["createdAt"] = template.createdAt
                    docData["updatedAt"] = template.updatedAt
                    docData["revision"] = template.revision
                    docData["deletedAt"] = template.deletedAt
                    docData["originDeviceId"] = template.originDeviceId
                    docData["lastSyncedAt"] = now

                    docRef = fs.collection("users").document(humanUserId)
                        .collection("templates").document(template.globalId)

                    afterCommit = {
                        repository.markTemplateSynced(template.id, now)
                    }
                }
            }
            "WORKOUT_TEMPLATE_EXERCISE" -> {
                val tEx = repository.getTemplateExerciseByGlobalId(entityGlobalId)
                if (tEx != null) {
                    docData["globalId"] = tEx.globalId
                    docData["templateId"] = tEx.templateId
                    docData["templateGlobalId"] = tEx.templateGlobalId
                    docData["exerciseId"] = tEx.exerciseId
                    docData["position"] = tEx.position
                    docData["restSeconds"] = tEx.restSeconds
                    docData["notes"] = tEx.notes
                    docData["supersetGroupId"] = tEx.supersetGroupId
                    docData["humanUserId"] = tEx.humanUserId
                    docData["createdAt"] = tEx.createdAt
                    docData["updatedAt"] = tEx.updatedAt
                    docData["revision"] = tEx.revision
                    docData["deletedAt"] = tEx.deletedAt
                    docData["originDeviceId"] = tEx.originDeviceId
                    docData["lastSyncedAt"] = now

                    docRef = fs.collection("users").document(humanUserId)
                        .collection("templateExercises").document(tEx.globalId)

                    afterCommit = {
                        repository.markTemplateExerciseSynced(tEx.id, now)
                    }
                }
            }
            "WORKOUT_TEMPLATE_SET" -> {
                val tSet = repository.getTemplateSetByGlobalId(entityGlobalId)
                if (tSet != null) {
                    docData["globalId"] = tSet.globalId
                    docData["templateExerciseId"] = tSet.templateExerciseId
                    docData["templateExerciseGlobalId"] = tSet.templateExerciseGlobalId
                    docData["position"] = tSet.position
                    docData["setType"] = tSet.setType
                    docData["targetRepsMin"] = tSet.targetRepsMin
                    docData["targetRepsMax"] = tSet.targetRepsMax
                    docData["targetWeight"] = tSet.targetWeight
                    docData["targetRpe"] = tSet.targetRpe
                    docData["targetDurationSeconds"] = tSet.targetDurationSeconds
                    docData["targetDistance"] = tSet.targetDistance
                    docData["tempo"] = tSet.tempo
                    docData["notes"] = tSet.notes
                    docData["humanUserId"] = tSet.humanUserId
                    docData["createdAt"] = tSet.createdAt
                    docData["updatedAt"] = tSet.updatedAt
                    docData["revision"] = tSet.revision
                    docData["deletedAt"] = tSet.deletedAt
                    docData["originDeviceId"] = tSet.originDeviceId
                    docData["lastSyncedAt"] = now

                    docRef = fs.collection("users").document(humanUserId)
                        .collection("templateSets").document(tSet.globalId)

                    afterCommit = {
                        repository.markTemplateSetSynced(tSet.id, now)
                    }
                }
            }
            "WORKOUT_SESSION" -> {
                val session = repository.getSessionByGlobalId(entityGlobalId)
                if (session != null) {
                    docData["globalId"] = session.globalId
                    docData["templateId"] = session.templateId
                    docData["templateGlobalId"] = session.templateGlobalId
                    docData["templateName"] = session.templateName
                    docData["startTime"] = session.startTime
                    docData["endTime"] = session.endTime
                    docData["humanUserId"] = session.humanUserId
                    docData["createdAt"] = session.createdAt
                    docData["updatedAt"] = session.updatedAt
                    docData["revision"] = session.revision
                    docData["deletedAt"] = session.deletedAt
                    docData["originDeviceId"] = session.originDeviceId
                    docData["lastSyncedAt"] = now

                    docRef = fs.collection("users").document(humanUserId)
                        .collection("sessions").document(session.globalId)

                    afterCommit = {
                        repository.markSessionSynced(session.id, now)
                    }
                }
            }
            "LOGGED_SET" -> {
                val set = repository.getLoggedSetByGlobalId(entityGlobalId)
                if (set != null) {
                    docData["globalId"] = set.globalId
                    docData["sessionId"] = set.sessionId
                    docData["sessionGlobalId"] = set.sessionGlobalId
                    docData["exerciseId"] = set.exerciseId
                    docData["setNumber"] = set.setNumber
                    docData["reps"] = set.reps
                    docData["weight"] = set.weight
                    docData["isCompleted"] = set.isCompleted
                    docData["rpe"] = set.rpe
                    docData["actualDuration"] = set.actualDuration
                    docData["actualDistance"] = set.actualDistance
                    docData["setType"] = set.setType
                    docData["targetRepsMin"] = set.targetRepsMin
                    docData["targetRepsMax"] = set.targetRepsMax
                    docData["targetWeight"] = set.targetWeight
                    docData["targetRpe"] = set.targetRpe
                    docData["targetDuration"] = set.targetDuration
                    docData["targetDistance"] = set.targetDistance
                    docData["notes"] = set.notes
                    docData["humanUserId"] = set.humanUserId
                    docData["createdAt"] = set.createdAt
                    docData["updatedAt"] = set.updatedAt
                    docData["revision"] = set.revision
                    docData["deletedAt"] = set.deletedAt
                    docData["originDeviceId"] = set.originDeviceId
                    docData["lastSyncedAt"] = now

                    docRef = fs.collection("users").document(humanUserId)
                        .collection("loggedSets").document(set.globalId)

                    afterCommit = {
                        repository.markLoggedSetSynced(set.id, now)
                    }
                }
            }
        }

        if (docRef != null && afterCommit != null) {
            // Check if command is already processed (Idempotency check)
            val processedRef = fs.collection("users").document(humanUserId)
                .collection("processedCommands").document(command.commandId)

            val exists = try {
                processedRef.get().await().exists()
            } catch (e: Exception) {
                false
            }

            if (exists) {
                Log.i(TAG, "Command ${command.commandId} already processed on server. Skipping rewrite.")
                afterCommit.invoke()
                return
            }

            // Batched write for atomicity
            val batch = fs.batch()
            batch.set(docRef, docData, SetOptions.merge())
            batch.set(processedRef, mapOf(
                "commandId" to command.commandId,
                "processedAt" to now,
                "humanUserId" to humanUserId
            ))

            batch.commit().await()
            afterCommit.invoke()
        }
    }

    private suspend fun downloadRemoteChanges(
        humanUserId: String,
        deviceId: String,
        dao: StrengthDao
    ) {
        val fs = firestore ?: return
        val now = System.currentTimeMillis()

        val subcollections = listOf(
            "profile" to "USER_PROFILE",
            "weight" to "BODY_WEIGHT",
            "tape" to "TAPE_MEASUREMENT",
            "customExercises" to "CUSTOM_EXERCISE",
            "templates" to "WORKOUT_TEMPLATE",
            "templateExercises" to "WORKOUT_TEMPLATE_EXERCISE",
            "templateSets" to "WORKOUT_TEMPLATE_SET",
            "sessions" to "WORKOUT_SESSION",
            "loggedSets" to "LOGGED_SET"
        )

        var totalDownloaded = 0
        var conflictsDetected = 0

        for ((subColl, entityType) in subcollections) {
            try {
                val snapshot = fs.collection("users").document(humanUserId)
                    .collection(subColl).get().await()

                totalDownloaded += snapshot.size()

                for (doc in snapshot.documents) {
                    val remoteGlobalId = doc.getString("globalId") ?: continue
                    val remoteRevision = doc.getLong("revision") ?: 1L
                    val remoteUpdatedAt = doc.getLong("updatedAt") ?: now
                    val remoteDeletedAt = doc.getLong("deletedAt")
                    val remoteOriginDevice = doc.getString("originDeviceId") ?: ""

                    // Look up local entity
                    val localEntity: VersionedEntity? = when (entityType) {
                        "USER_PROFILE" -> dao.getUserProfileByGlobalId(remoteGlobalId) as VersionedEntity?
                        "BODY_WEIGHT" -> dao.getBodyWeightByGlobalId(remoteGlobalId) as VersionedEntity?
                        "TAPE_MEASUREMENT" -> dao.getTapeMeasurementByGlobalId(remoteGlobalId) as VersionedEntity?
                        "CUSTOM_EXERCISE" -> dao.getExerciseByGlobalId(remoteGlobalId) as VersionedEntity?
                        "WORKOUT_TEMPLATE" -> dao.getTemplateByGlobalId(remoteGlobalId) as VersionedEntity?
                        "WORKOUT_TEMPLATE_EXERCISE" -> dao.getTemplateExerciseByGlobalId(remoteGlobalId) as VersionedEntity?
                        "WORKOUT_TEMPLATE_SET" -> dao.getTemplateSetByGlobalId(remoteGlobalId) as VersionedEntity?
                        "WORKOUT_SESSION" -> dao.getSessionByGlobalId(remoteGlobalId) as VersionedEntity?
                        "LOGGED_SET" -> dao.getLoggedSetByGlobalId(remoteGlobalId) as VersionedEntity?
                        else -> null
                    }

                    if (localEntity == null) {
                        // Insert locally if not deleted
                        if (remoteDeletedAt == null) {
                            insertRemoteDocument(entityType, doc, dao)
                        }
                    } else {
                        // Conflict and Newer Evaluation
                        val isLocalUnsynced = localEntity.syncStatus != "SYNCED"

                        if (remoteRevision > localEntity.revision ||
                            (remoteRevision == localEntity.revision && remoteUpdatedAt > localEntity.updatedAt)) {
                            
                            if (isLocalUnsynced) {
                                conflictsDetected++
                                markLocalConflict(
                                    entityType, 
                                    localEntity, 
                                    "Remote is newer (rev $remoteRevision, updated $remoteUpdatedAt) but local has unsynced changes (rev ${localEntity.revision}, updated ${localEntity.updatedAt})", 
                                    dao
                                )
                            } else {
                                updateLocalDocument(entityType, doc, localEntity, dao)
                            }
                        } else if (remoteRevision < localEntity.revision ||
                                   (remoteRevision == localEntity.revision && remoteUpdatedAt < localEntity.updatedAt)) {
                            // Local is newer, no action required
                        } else if (remoteRevision == localEntity.revision && remoteUpdatedAt == localEntity.updatedAt) {
                            // Check simultaneous device modifications
                            if (remoteOriginDevice != deviceId && remoteOriginDevice.isNotEmpty() && isLocalUnsynced) {
                                conflictsDetected++
                                markLocalConflict(
                                    entityType, 
                                    localEntity, 
                                    "Simultaneous modifications detected on different devices with equal revisions", 
                                    dao
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading subcollection $subColl", e)
            }
        }

        if (totalDownloaded > 0) {
            SyncManager.updateLastSuccessfulDownload(System.currentTimeMillis())
        }

        SyncManager.updatePendingDownloads(0)
        SyncManager.updateConflictCount(conflictsDetected)
    }

    private suspend fun markLocalConflict(
        entityType: String,
        localEntity: VersionedEntity,
        diagnostic: String,
        dao: StrengthDao
    ) {
        Log.w(TAG, "Conflict marked for $entityType: $diagnostic")
        when (entityType) {
            "USER_PROFILE" -> {
                val local = localEntity as UserProfile
                dao.insertUserProfile(local.copy(syncStatus = "CONFLICT", conflictState = diagnostic))
            }
            "BODY_WEIGHT" -> {
                val local = localEntity as BodyWeight
                dao.insertBodyWeight(local.copy(syncStatus = "CONFLICT", conflictState = diagnostic))
            }
            "TAPE_MEASUREMENT" -> {
                val local = localEntity as TapeMeasurement
                dao.insertTapeMeasurement(local.copy(syncStatus = "CONFLICT", conflictState = diagnostic))
            }
            "CUSTOM_EXERCISE" -> {
                val local = localEntity as Exercise
                dao.insertExercise(local.copy(syncStatus = "CONFLICT", conflictState = diagnostic))
            }
            "WORKOUT_TEMPLATE" -> {
                val local = localEntity as WorkoutTemplate
                dao.insertTemplate(local.copy(syncStatus = "CONFLICT", conflictState = diagnostic))
            }
            "WORKOUT_TEMPLATE_EXERCISE" -> {
                val local = localEntity as WorkoutTemplateExercise
                dao.insertTemplateExercise(local.copy(syncStatus = "CONFLICT", conflictState = diagnostic))
            }
            "WORKOUT_TEMPLATE_SET" -> {
                val local = localEntity as WorkoutTemplateSet
                dao.insertTemplateSet(local.copy(syncStatus = "CONFLICT", conflictState = diagnostic))
            }
            "WORKOUT_SESSION" -> {
                val local = localEntity as WorkoutSession
                dao.insertSession(local.copy(syncStatus = "CONFLICT", conflictState = diagnostic))
            }
            "LOGGED_SET" -> {
                val local = localEntity as LoggedSet
                dao.insertLoggedSet(local.copy(syncStatus = "CONFLICT", conflictState = diagnostic))
            }
        }
    }

    private suspend fun insertRemoteDocument(
        entityType: String,
        doc: DocumentSnapshot,
        dao: StrengthDao
    ) {
        val now = System.currentTimeMillis()
        when (entityType) {
            "USER_PROFILE" -> {
                val profile = UserProfile(
                    id = doc.getString("id") ?: "offline",
                    googleUserId = doc.getString("googleUserId"),
                    email = doc.getString("email"),
                    displayName = doc.getString("displayName"),
                    photoUrl = doc.getString("photoUrl"),
                    authProvider = doc.getString("authProvider"),
                    preferredUnits = doc.getString("preferredUnits") ?: "metric",
                    heightCm = doc.getDouble("heightCm")?.toFloat(),
                    dateOfBirth = doc.getString("dateOfBirth"),
                    sex = doc.getString("sex"),
                    trainingExperience = doc.getString("trainingExperience"),
                    globalId = doc.getString("globalId") ?: "",
                    humanUserId = doc.getString("humanUserId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: now,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: 1L,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now,
                    originDeviceId = doc.getString("originDeviceId") ?: ""
                )
                dao.insertUserProfile(profile)
                resolvePendingDependencies(profile.globalId, dao)
            }
            "BODY_WEIGHT" -> {
                val weight = BodyWeight(
                    id = 0,
                    userId = doc.getString("userId"),
                    weight = doc.getDouble("weight")?.toFloat() ?: 0f,
                    date = doc.getLong("date") ?: now,
                    bodyFat = doc.getDouble("bodyFat")?.toFloat(),
                    leanMass = doc.getDouble("leanMass")?.toFloat(),
                    fatMass = doc.getDouble("fatMass")?.toFloat(),
                    bmi = doc.getDouble("bmi")?.toFloat(),
                    globalId = doc.getString("globalId") ?: "",
                    humanUserId = doc.getString("humanUserId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: now,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: 1L,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now,
                    originDeviceId = doc.getString("originDeviceId") ?: ""
                )
                dao.insertBodyWeight(weight)
                resolvePendingDependencies(weight.globalId, dao)
            }
            "TAPE_MEASUREMENT" -> {
                val tape = TapeMeasurement(
                    id = 0,
                    userId = doc.getString("userId"),
                    chest = doc.getDouble("chest")?.toFloat(),
                    bicepLeft = doc.getDouble("bicepLeft")?.toFloat(),
                    bicepRight = doc.getDouble("bicepRight")?.toFloat(),
                    thighLeft = doc.getDouble("thighLeft")?.toFloat(),
                    thighRight = doc.getDouble("thighRight")?.toFloat(),
                    waist = doc.getDouble("waist")?.toFloat(),
                    hips = doc.getDouble("hips")?.toFloat(),
                    date = doc.getLong("date") ?: now,
                    globalId = doc.getString("globalId") ?: "",
                    humanUserId = doc.getString("humanUserId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: now,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: 1L,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now,
                    originDeviceId = doc.getString("originDeviceId") ?: ""
                )
                dao.insertTapeMeasurement(tape)
                resolvePendingDependencies(tape.globalId, dao)
            }
            "CUSTOM_EXERCISE" -> {
                val exercise = Exercise(
                    id = doc.getString("id") ?: "",
                    name = doc.getString("name") ?: "",
                    category = doc.getString("category") ?: "",
                    isCustom = doc.getBoolean("isCustom") ?: true,
                    globalId = doc.getString("globalId") ?: "",
                    humanUserId = doc.getString("humanUserId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: now,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: 1L,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now,
                    originDeviceId = doc.getString("originDeviceId") ?: ""
                )
                dao.insertExercise(exercise)
                resolvePendingDependencies(exercise.globalId, dao)
            }
            "WORKOUT_TEMPLATE" -> {
                val template = WorkoutTemplate(
                    id = 0,
                    name = doc.getString("name") ?: "",
                    exerciseIdsJson = doc.getString("exerciseIdsJson") ?: "[]",
                    userId = doc.getString("userId"),
                    globalId = doc.getString("globalId") ?: "",
                    humanUserId = doc.getString("humanUserId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: now,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: 1L,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now,
                    originDeviceId = doc.getString("originDeviceId") ?: ""
                )
                dao.insertTemplate(template)
                resolvePendingDependencies(template.globalId, dao)
            }
            "WORKOUT_TEMPLATE_EXERCISE" -> {
                val templateGlobalId = doc.getString("templateGlobalId") ?: ""
                val parentTemplate = dao.getTemplateByGlobalId(templateGlobalId)
                if (parentTemplate == null) {
                    storePendingDependency(templateGlobalId, "WORKOUT_TEMPLATE_EXERCISE", doc, null)
                    return
                }

                val tEx = WorkoutTemplateExercise(
                    id = 0,
                    templateId = parentTemplate.id,
                    templateGlobalId = templateGlobalId,
                    exerciseId = doc.getString("exerciseId") ?: "",
                    position = doc.getLong("position")?.toInt() ?: 0,
                    restSeconds = doc.getLong("restSeconds")?.toInt() ?: 90,
                    notes = doc.getString("notes"),
                    supersetGroupId = doc.getString("supersetGroupId"),
                    globalId = doc.getString("globalId") ?: "",
                    humanUserId = doc.getString("humanUserId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: now,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: 1L,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now,
                    originDeviceId = doc.getString("originDeviceId") ?: ""
                )
                dao.insertTemplateExercise(tEx)
                resolvePendingDependencies(tEx.globalId, dao)
            }
            "WORKOUT_TEMPLATE_SET" -> {
                val templateExerciseGlobalId = doc.getString("templateExerciseGlobalId") ?: ""
                val parentExercise = dao.getTemplateExerciseByGlobalId(templateExerciseGlobalId)
                if (parentExercise == null) {
                    storePendingDependency(templateExerciseGlobalId, "WORKOUT_TEMPLATE_SET", doc, null)
                    return
                }

                val tSet = WorkoutTemplateSet(
                    id = 0,
                    templateExerciseId = parentExercise.id,
                    templateExerciseGlobalId = templateExerciseGlobalId,
                    position = doc.getLong("position")?.toInt() ?: 1,
                    setType = doc.getString("setType") ?: "WORKING",
                    targetRepsMin = doc.getLong("targetRepsMin")?.toInt(),
                    targetRepsMax = doc.getLong("targetRepsMax")?.toInt(),
                    targetWeight = doc.getDouble("targetWeight")?.toFloat(),
                    targetRpe = doc.getLong("targetRpe")?.toInt(),
                    targetDurationSeconds = doc.getLong("targetDurationSeconds")?.toInt(),
                    targetDistance = doc.getDouble("targetDistance")?.toFloat(),
                    tempo = doc.getString("tempo"),
                    notes = doc.getString("notes"),
                    globalId = doc.getString("globalId") ?: "",
                    humanUserId = doc.getString("humanUserId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: now,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: 1L,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now,
                    originDeviceId = doc.getString("originDeviceId") ?: ""
                )
                dao.insertTemplateSet(tSet)
                resolvePendingDependencies(tSet.globalId, dao)
            }
            "WORKOUT_SESSION" -> {
                val templateGlobalId = doc.getString("templateGlobalId")
                val resolvedTemplateId = if (!templateGlobalId.isNullOrEmpty()) {
                    val parentTemplate = dao.getTemplateByGlobalId(templateGlobalId)
                    if (parentTemplate == null) {
                        storePendingDependency(templateGlobalId, "WORKOUT_SESSION", doc, null)
                        return
                    }
                    parentTemplate.id
                } else {
                    null
                }

                val session = WorkoutSession(
                    id = 0,
                    templateId = resolvedTemplateId,
                    templateGlobalId = templateGlobalId,
                    templateName = doc.getString("templateName") ?: "Workout",
                    startTime = doc.getLong("startTime") ?: now,
                    endTime = doc.getLong("endTime") ?: now,
                    userId = doc.getString("userId"),
                    globalId = doc.getString("globalId") ?: "",
                    humanUserId = doc.getString("humanUserId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: now,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: 1L,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now,
                    originDeviceId = doc.getString("originDeviceId") ?: ""
                )
                dao.insertSession(session)
                resolvePendingDependencies(session.globalId, dao)
            }
            "LOGGED_SET" -> {
                val sessionGlobalId = doc.getString("sessionGlobalId") ?: ""
                val parentSession = dao.getSessionByGlobalId(sessionGlobalId)
                if (parentSession == null) {
                    storePendingDependency(sessionGlobalId, "LOGGED_SET", doc, null)
                    return
                }

                val set = LoggedSet(
                    id = 0,
                    sessionId = parentSession.id,
                    sessionGlobalId = sessionGlobalId,
                    exerciseId = doc.getString("exerciseId") ?: "",
                    setNumber = doc.getLong("setNumber")?.toInt() ?: 1,
                    reps = doc.getLong("reps")?.toInt() ?: 10,
                    weight = doc.getDouble("weight")?.toFloat() ?: 0f,
                    isCompleted = doc.getBoolean("isCompleted") ?: true,
                    rpe = doc.getLong("rpe")?.toInt(),
                    actualDuration = doc.getLong("actualDuration")?.toInt(),
                    actualDistance = doc.getDouble("actualDistance")?.toFloat(),
                    setType = doc.getString("setType") ?: "WORKING",
                    targetRepsMin = doc.getLong("targetRepsMin")?.toInt(),
                    targetRepsMax = doc.getLong("targetRepsMax")?.toInt(),
                    targetWeight = doc.getDouble("targetWeight")?.toFloat(),
                    targetRpe = doc.getLong("targetRpe")?.toInt(),
                    targetDuration = doc.getLong("targetDuration")?.toInt(),
                    targetDistance = doc.getDouble("targetDistance")?.toFloat(),
                    notes = doc.getString("notes"),
                    globalId = doc.getString("globalId") ?: "",
                    humanUserId = doc.getString("humanUserId") ?: "",
                    createdAt = doc.getLong("createdAt") ?: now,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: 1L,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now,
                    originDeviceId = doc.getString("originDeviceId") ?: ""
                )
                dao.insertLoggedSet(set)
                resolvePendingDependencies(set.globalId, dao)
            }
        }
    }

    private suspend fun updateLocalDocument(
        entityType: String,
        doc: DocumentSnapshot,
        localEntity: VersionedEntity,
        dao: StrengthDao
    ) {
        val now = System.currentTimeMillis()
        when (entityType) {
            "USER_PROFILE" -> {
                val local = localEntity as UserProfile
                val updated = local.copy(
                    displayName = doc.getString("displayName") ?: local.displayName,
                    email = doc.getString("email") ?: local.email,
                    photoUrl = doc.getString("photoUrl") ?: local.photoUrl,
                    preferredUnits = doc.getString("preferredUnits") ?: local.preferredUnits,
                    heightCm = doc.getDouble("heightCm")?.toFloat() ?: local.heightCm,
                    dateOfBirth = doc.getString("dateOfBirth") ?: local.dateOfBirth,
                    sex = doc.getString("sex") ?: local.sex,
                    trainingExperience = doc.getString("trainingExperience") ?: local.trainingExperience,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: local.revision,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now
                )
                dao.insertUserProfile(updated)
                resolvePendingDependencies(updated.globalId, dao)
            }
            "BODY_WEIGHT" -> {
                val local = localEntity as BodyWeight
                val updated = local.copy(
                    weight = doc.getDouble("weight")?.toFloat() ?: local.weight,
                    date = doc.getLong("date") ?: local.date,
                    bodyFat = doc.getDouble("bodyFat")?.toFloat() ?: local.bodyFat,
                    leanMass = doc.getDouble("leanMass")?.toFloat() ?: local.leanMass,
                    fatMass = doc.getDouble("fatMass")?.toFloat() ?: local.fatMass,
                    bmi = doc.getDouble("bmi")?.toFloat() ?: local.bmi,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: local.revision,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now
                )
                dao.insertBodyWeight(updated)
                resolvePendingDependencies(updated.globalId, dao)
            }
            "TAPE_MEASUREMENT" -> {
                val local = localEntity as TapeMeasurement
                val updated = local.copy(
                    chest = doc.getDouble("chest")?.toFloat() ?: local.chest,
                    bicepLeft = doc.getDouble("bicepLeft")?.toFloat() ?: local.bicepLeft,
                    bicepRight = doc.getDouble("bicepRight")?.toFloat() ?: local.bicepRight,
                    thighLeft = doc.getDouble("thighLeft")?.toFloat() ?: local.thighLeft,
                    thighRight = doc.getDouble("thighRight")?.toFloat() ?: local.thighRight,
                    waist = doc.getDouble("waist")?.toFloat() ?: local.waist,
                    hips = doc.getDouble("hips")?.toFloat() ?: local.hips,
                    date = doc.getLong("date") ?: local.date,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: local.revision,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now
                )
                dao.insertTapeMeasurement(updated)
                resolvePendingDependencies(updated.globalId, dao)
            }
            "CUSTOM_EXERCISE" -> {
                val local = localEntity as Exercise
                val updated = local.copy(
                    name = doc.getString("name") ?: local.name,
                    category = doc.getString("category") ?: local.category,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: local.revision,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now
                )
                dao.insertExercise(updated)
                resolvePendingDependencies(updated.globalId, dao)
            }
            "WORKOUT_TEMPLATE" -> {
                val local = localEntity as WorkoutTemplate
                val updated = local.copy(
                    name = doc.getString("name") ?: local.name,
                    exerciseIdsJson = doc.getString("exerciseIdsJson") ?: local.exerciseIdsJson,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: local.revision,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now
                )
                dao.insertTemplate(updated)
                resolvePendingDependencies(updated.globalId, dao)
            }
            "WORKOUT_TEMPLATE_EXERCISE" -> {
                val templateGlobalId = doc.getString("templateGlobalId") ?: ""
                val parentTemplate = dao.getTemplateByGlobalId(templateGlobalId)
                if (parentTemplate == null) {
                    storePendingDependency(templateGlobalId, "WORKOUT_TEMPLATE_EXERCISE", doc, localEntity)
                    return
                }

                val local = localEntity as WorkoutTemplateExercise
                val updated = local.copy(
                    templateId = parentTemplate.id,
                    templateGlobalId = templateGlobalId,
                    position = doc.getLong("position")?.toInt() ?: local.position,
                    restSeconds = doc.getLong("restSeconds")?.toInt() ?: local.restSeconds,
                    notes = doc.getString("notes") ?: local.notes,
                    supersetGroupId = doc.getString("supersetGroupId") ?: local.supersetGroupId,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: local.revision,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now
                )
                dao.insertTemplateExercise(updated)
                resolvePendingDependencies(updated.globalId, dao)
            }
            "WORKOUT_TEMPLATE_SET" -> {
                val templateExerciseGlobalId = doc.getString("templateExerciseGlobalId") ?: ""
                val parentExercise = dao.getTemplateExerciseByGlobalId(templateExerciseGlobalId)
                if (parentExercise == null) {
                    storePendingDependency(templateExerciseGlobalId, "WORKOUT_TEMPLATE_SET", doc, localEntity)
                    return
                }

                val local = localEntity as WorkoutTemplateSet
                val updated = local.copy(
                    templateExerciseId = parentExercise.id,
                    templateExerciseGlobalId = templateExerciseGlobalId,
                    position = doc.getLong("position")?.toInt() ?: local.position,
                    setType = doc.getString("setType") ?: local.setType,
                    targetRepsMin = doc.getLong("targetRepsMin")?.toInt() ?: local.targetRepsMin,
                    targetRepsMax = doc.getLong("targetRepsMax")?.toInt() ?: local.targetRepsMax,
                    targetWeight = doc.getDouble("targetWeight")?.toFloat() ?: local.targetWeight,
                    targetRpe = doc.getLong("targetRpe")?.toInt() ?: local.targetRpe,
                    targetDurationSeconds = doc.getLong("targetDurationSeconds")?.toInt() ?: local.targetDurationSeconds,
                    targetDistance = doc.getDouble("targetDistance")?.toFloat() ?: local.targetDistance,
                    tempo = doc.getString("tempo") ?: local.tempo,
                    notes = doc.getString("notes") ?: local.notes,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: local.revision,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now
                )
                dao.insertTemplateSet(updated)
                resolvePendingDependencies(updated.globalId, dao)
            }
            "WORKOUT_SESSION" -> {
                val templateGlobalId = doc.getString("templateGlobalId")
                val resolvedTemplateId = if (!templateGlobalId.isNullOrEmpty()) {
                    val parentTemplate = dao.getTemplateByGlobalId(templateGlobalId)
                    if (parentTemplate == null) {
                        storePendingDependency(templateGlobalId, "WORKOUT_SESSION", doc, localEntity)
                        return
                    }
                    parentTemplate.id
                } else {
                    null
                }

                val local = localEntity as WorkoutSession
                val updated = local.copy(
                    templateId = resolvedTemplateId,
                    templateGlobalId = templateGlobalId,
                    templateName = doc.getString("templateName") ?: local.templateName,
                    startTime = doc.getLong("startTime") ?: local.startTime,
                    endTime = doc.getLong("endTime") ?: local.endTime,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: local.revision,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now
                )
                dao.insertSession(updated)
                resolvePendingDependencies(updated.globalId, dao)
            }
            "LOGGED_SET" -> {
                val sessionGlobalId = doc.getString("sessionGlobalId") ?: ""
                val parentSession = dao.getSessionByGlobalId(sessionGlobalId)
                if (parentSession == null) {
                    storePendingDependency(sessionGlobalId, "LOGGED_SET", doc, localEntity)
                    return
                }

                val local = localEntity as LoggedSet
                val updated = local.copy(
                    sessionId = parentSession.id,
                    sessionGlobalId = sessionGlobalId,
                    setNumber = doc.getLong("setNumber")?.toInt() ?: local.setNumber,
                    reps = doc.getLong("reps")?.toInt() ?: local.reps,
                    weight = doc.getDouble("weight")?.toFloat() ?: local.weight,
                    isCompleted = doc.getBoolean("isCompleted") ?: local.isCompleted,
                    rpe = doc.getLong("rpe")?.toInt() ?: local.rpe,
                    actualDuration = doc.getLong("actualDuration")?.toInt() ?: local.actualDuration,
                    actualDistance = doc.getDouble("actualDistance")?.toFloat() ?: local.actualDistance,
                    setType = doc.getString("setType") ?: local.setType,
                    targetRepsMin = doc.getLong("targetRepsMin")?.toInt() ?: local.targetRepsMin,
                    targetRepsMax = doc.getLong("targetRepsMax")?.toInt() ?: local.targetRepsMax,
                    targetWeight = doc.getDouble("targetWeight")?.toFloat() ?: local.targetWeight,
                    targetRpe = doc.getLong("targetRpe")?.toInt() ?: local.targetRpe,
                    targetDuration = doc.getLong("targetDuration")?.toInt() ?: local.targetDuration,
                    targetDistance = doc.getDouble("targetDistance")?.toFloat() ?: local.targetDistance,
                    notes = doc.getString("notes") ?: local.notes,
                    updatedAt = doc.getLong("updatedAt") ?: now,
                    deletedAt = doc.getLong("deletedAt"),
                    revision = doc.getLong("revision") ?: local.revision,
                    syncStatus = "SYNCED",
                    lastSyncedAt = now
                )
                dao.insertLoggedSet(updated)
                resolvePendingDependencies(updated.globalId, dao)
            }
        }
    }

    override suspend fun enqueueEntity(entity: VersionedEntity): Result<Unit> {
        updatePendingCounts()
        return Result.success(Unit)
    }

    override suspend fun resolveConflict(
        entityId: String,
        strategy: ConflictResolutionStrategy
    ): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun resetSyncWorkers() {
        updatePendingCounts()
    }
}
