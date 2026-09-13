package com.example.core.sync

import com.example.data.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.time.LocalDate

class StudioPlanContractException(val reasonCode: String) : IllegalArgumentException(reasonCode)

object StudioPlanContract {
    @Suppress("UNCHECKED_CAST")
    suspend fun parse(versionId: String, value: Map<String, Any?>, expectedOwner: String, firebaseUid: String,
              dependency: suspend (String) -> StudioWorkoutLink?, appliedAt: Long = System.currentTimeMillis()): StudioPlanImport {
        fun requiredString(map: Map<String, Any?>, key: String) = (map[key] as? String)?.takeIf { it.isNotBlank() }
            ?: throw StudioPlanContractException("MISSING_${key.uppercase()}")
        val owner = requiredString(value, "humanUserId")
        if (owner != expectedOwner) throw StudioPlanContractException("WRONG_OWNER")
        if (requiredString(value, "versionId") != versionId) throw StudioPlanContractException("VERSION_ID_MISMATCH")
        if (value["contentType"] != "plan" || value["publicationState"] != "PUBLISHED" || value["tombstoneState"] != "ACTIVE")
            throw StudioPlanContractException("INVALID_PUBLICATION_STATE")
        if (value["schemaVersion"] != "humanv1.plan/1") throw StudioPlanContractException("UNSUPPORTED_SCHEMA")
        val globalId = requiredString(value, "globalId")
        if (value["sourceDraftId"] != globalId) throw StudioPlanContractException("SOURCE_ID_MISMATCH")
        val revision = (value["revision"] as? Number)?.toLong()?.takeIf { it >= 1 }
            ?: throw StudioPlanContractException("INVALID_REVISION")
        val checksum = requiredString(value, "contentChecksum")
        if (!checksum.matches(Regex("^[0-9a-f]{64}$"))) throw StudioPlanContractException("INVALID_CHECKSUM")
        val payload = value["payload"] as? Map<String, Any?> ?: throw StudioPlanContractException("MISSING_PAYLOAD")
        if (requiredString(payload, "planId") != globalId) throw StudioPlanContractException("PLAN_ID_MISMATCH")
        if (StudioWorkoutContract.sha256(StudioWorkoutContract.canonicalJson(payload)) != checksum) throw StudioPlanContractException("CHECKSUM_MISMATCH")
        val title = requiredString(payload, "title")
        val startDate = runCatching { LocalDate.parse(requiredString(payload, "startDate")) }
            .getOrElse { throw StudioPlanContractException("INVALID_START_DATE") }
        requiredString(payload, "timezone")
        if (payload["destinationApplication"] != "HUMAN_STRENGTH") throw StudioPlanContractException("WRONG_DESTINATION")
        val declaredDependencies = (payload["workoutVersionIds"] as? List<*>)?.map {
            (it as? String)?.takeIf(String::isNotBlank) ?: throw StudioPlanContractException("MALFORMED_DEPENDENCY")
        }?.sorted() ?: throw StudioPlanContractException("MISSING_DEPENDENCIES")
        val weeks = payload["weeks"] as? List<*> ?: throw StudioPlanContractException("MISSING_WEEKS")
        val dependencies = linkedSetOf<String>()
        val occurrences = mutableListOf<PlannedWorkout>()
        weeks.forEach { rawWeek ->
            val week = rawWeek as? Map<String, Any?> ?: throw StudioPlanContractException("MALFORMED_WEEK")
            val weekNumber = (week["weekNumber"] as? Number)?.toInt()?.takeIf { it >= 1 }
                ?: throw StudioPlanContractException("INVALID_WEEK_NUMBER")
            val placements = week["placements"] as? List<*> ?: throw StudioPlanContractException("MISSING_PLACEMENTS")
            placements.forEach { rawPlacement ->
                val placement = rawPlacement as? Map<String, Any?> ?: throw StudioPlanContractException("MALFORMED_PLACEMENT")
                val placementId = requiredString(placement, "placementId")
                val workoutGlobalId = requiredString(placement, "workoutId")
                val workoutVersionId = requiredString(placement, "workoutVersionId")
                val workout = dependency(workoutVersionId) ?: throw StudioPlanContractException("MISSING_WORKOUT_DEPENDENCY")
                if (workout.humanUserId != owner || workout.workoutGlobalId != workoutGlobalId) throw StudioPlanContractException("WORKOUT_DEPENDENCY_MISMATCH")
                dependencies += workoutVersionId
                val day = (placement["dayOfWeek"] as? Number)?.toInt()?.takeIf { it in 1..7 }
                    ?: throw StudioPlanContractException("INVALID_SCHEDULE_DAY")
                val scheduled = (placement["scheduledEpochDay"] as? Number)?.toLong()
                    ?: startDate.plusWeeks((weekNumber - 1).toLong()).plusDays((day - 1).toLong()).toEpochDay()
                occurrences += PlannedWorkout(id = "$globalId:$placementId", seriesId = globalId,
                    userId = firebaseUid, humanUserId = owner, templateId = workout.localRoutineId,
                    templateGlobalId = workout.workoutGlobalId, routineName = title,
                    scheduledEpochDay = scheduled, originalEpochDay = scheduled,
                    preferredMinuteOfDay = (placement["preferredMinuteOfDay"] as? Number)?.toInt(),
                    reminderEnabled = placement["reminderEnabled"] == true, createdAt = appliedAt,
                    updatedAt = appliedAt, globalId = "$globalId:$placementId", revision = revision,
                    syncStatus = "SYNCED", lastSyncedAt = appliedAt, originDeviceId = "studio:HUMAN_STRENGTH")
            }
        }
        if (occurrences.isEmpty()) throw StudioPlanContractException("NO_OCCURRENCES")
        if (dependencies.toList().sorted() != declaredDependencies) throw StudioPlanContractException("DEPENDENCY_SET_MISMATCH")
        val first = occurrences.first()
        val acknowledgementId = "strength_plan_${StudioWorkoutContract.sha256("HUMAN_STRENGTH|$versionId").take(32)}"
        val link = StudioPlanLink(versionId, owner, globalId, revision, checksum,
            JSONArray(dependencies.toList().sorted()).toString(), StudioWorkoutContract.canonicalJson(payload),
            appliedAt, acknowledgementId)
        val plan = TrainingPlan(globalId, firebaseUid, owner, first.templateId, first.templateGlobalId,
            title, startDate.toEpochDay(), first.preferredMinuteOfDay, 0,
            occurrences.maxOf { it.scheduledEpochDay }, appliedAt, appliedAt, globalId, revision,
            null, "SYNCED", appliedAt, null, "studio:HUMAN_STRENGTH")
        return StudioPlanImport(link, plan, occurrences)
    }
}

class StudioPlanIngestionRepository(private val firestore: FirebaseFirestore, private val dao: StrengthDao) {
    suspend fun synchronize(owner: String, firebaseUid: String) {
        val snapshots = firestore.collection("users").document(owner).collection("publishedPlans").get().await()
        val imports = snapshots.documents.map { document ->
            StudioPlanContract.parse(document.id, document.data ?: emptyMap(), owner, firebaseUid,
                dependency = { version -> dao.getStudioWorkoutLink(version) })
        }.sortedBy { it.link.sourceRevision }
        imports.forEach { value ->
            val result = dao.applyStudioPlanTransaction(value)
            acknowledge(result.link, if (result.conflict) "CONFLICT" else "APPLIED",
                if (result.conflict) "LOCAL_PLAN_EDIT_PRESERVED" else null)
        }
    }

    private suspend fun acknowledge(link: StudioPlanLink, state: String, reasonCode: String?) {
        val ref = firestore.collection("users").document(link.humanUserId)
            .collection("planDeliveryAcks").document(link.acknowledgementId)
        val dependencies = JSONArray(link.workoutVersionIdsJson).let { array -> (0 until array.length()).map { array.getString(it) } }
        firestore.runTransaction { tx ->
            val existing = tx.get(ref)
            if (existing.exists()) {
                val data = existing.data ?: emptyMap()
                require(data["humanUserId"] == link.humanUserId && data["planVersionId"] == link.planVersionId &&
                    data["planChecksum"] == link.planChecksum && data["state"] == state) { "PLAN_ACKNOWLEDGEMENT_CONFLICT" }
            } else tx.set(ref, mapOf("schemaVersion" to 1, "acknowledgementId" to link.acknowledgementId,
                "humanUserId" to link.humanUserId, "planGlobalId" to link.planGlobalId,
                "planVersionId" to link.planVersionId, "planChecksum" to link.planChecksum,
                "applicationId" to "HUMAN_STRENGTH", "sourceRevision" to link.sourceRevision,
                "workoutVersionIds" to dependencies, "state" to state, "reasonCode" to reasonCode,
                "clientAppliedAtMillis" to link.appliedAt, "createdAt" to FieldValue.serverTimestamp()))
        }.await()
        dao.markStudioPlanAcknowledgement(link.planVersionId, state)
    }
}
