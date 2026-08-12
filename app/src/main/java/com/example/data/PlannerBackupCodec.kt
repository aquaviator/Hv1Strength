package com.example.data

import org.json.JSONArray
import org.json.JSONObject

object PlannerBackupCodec {
    const val FORMAT_VERSION = 4
    private val statuses = setOf("PLANNED", "COMPLETED", "SKIPPED")

    data class Payload(val plans: List<TrainingPlan>, val occurrences: List<PlannedWorkout>)

    fun eligibleReminders(items: List<PlannedWorkout>) = items.filter {
        it.deletedAt == null && it.status == "PLANNED" && it.reminderEnabled && it.preferredMinuteOfDay != null
    }

    fun plansJson(plans: List<TrainingPlan>) = JSONArray().apply { plans.forEach { p -> put(JSONObject().apply {
        put("id", p.id); put("humanUserId", p.humanUserId); put("templateId", p.templateId)
        put("templateGlobalId", p.templateGlobalId); put("routineName", p.routineName); put("firstEpochDay", p.firstEpochDay)
        put("preferredMinuteOfDay", p.preferredMinuteOfDay ?: JSONObject.NULL); put("weekdaysMask", p.weekdaysMask)
        put("recurrenceEndEpochDay", p.recurrenceEndEpochDay ?: JSONObject.NULL); put("createdAt", p.createdAt)
        put("updatedAt", p.updatedAt); put("revision", p.revision); put("deletedAt", p.deletedAt ?: JSONObject.NULL)
        put("originDeviceId", p.originDeviceId)
    }) } }

    fun occurrencesJson(items: List<PlannedWorkout>) = JSONArray().apply { items.forEach { o -> put(JSONObject().apply {
        put("id", o.id); put("seriesId", o.seriesId); put("humanUserId", o.humanUserId); put("templateId", o.templateId)
        put("templateGlobalId", o.templateGlobalId); put("routineName", o.routineName); put("scheduledEpochDay", o.scheduledEpochDay)
        put("originalEpochDay", o.originalEpochDay); put("preferredMinuteOfDay", o.preferredMinuteOfDay ?: JSONObject.NULL)
        put("status", o.status); put("completedAt", o.completedAt ?: JSONObject.NULL)
        put("linkedSessionId", o.linkedSessionId ?: JSONObject.NULL); put("reminderEnabled", o.reminderEnabled)
        put("detachedFromSeries", o.detachedFromSeries); put("createdAt", o.createdAt); put("updatedAt", o.updatedAt)
        put("revision", o.revision); put("deletedAt", o.deletedAt ?: JSONObject.NULL); put("originDeviceId", o.originDeviceId)
    }) } }

    fun parse(root: JSONObject, userId: String, humanUserId: String): Payload {
        val version = root.optInt("version", 1)
        require(version in 1..FORMAT_VERSION) { "Unsupported backup format" }
        require(!root.has("training_plans") || root.optJSONArray("training_plans") != null) { "Malformed planner structure" }
        require(!root.has("planned_workouts") || root.optJSONArray("planned_workouts") != null) { "Malformed planner structure" }
        val plansArray = root.optJSONArray("training_plans") ?: JSONArray()
        val occurrencesArray = root.optJSONArray("planned_workouts") ?: JSONArray()
        val sessionIds = root.optJSONArray("workout_sessions")?.let { sessions ->
            (0 until sessions.length()).mapTo(hashSetOf()) { sessions.getJSONObject(it).getInt("id") }
        } ?: emptySet()
        if (plansArray.length() == 0 && occurrencesArray.length() == 0) return Payload(emptyList(), emptyList())

        fun JSONObject.nullableLong(name: String) = if (!has(name) || isNull(name)) null else getLong(name)
        fun JSONObject.minute(name: String): Int? = if (!has(name) || isNull(name)) null else getInt(name).also {
            require(it in 0..1439) { "Malformed preferred time" }
        }
        fun owner(value: String) = require(value == humanUserId) { "Planner backup belongs to a different account" }

        val plans = (0 until plansArray.length()).map { index -> plansArray.getJSONObject(index) }.map { p ->
            owner(p.getString("humanUserId"))
            require(p.getString("templateGlobalId").isNotBlank()) { "Missing routine reference" }
            val first = p.getLong("firstEpochDay")
            val end = p.nullableLong("recurrenceEndEpochDay")
            val mask = p.getInt("weekdaysMask")
            require(mask in 0..127) { "Invalid recurrence weekday set" }
            require(end == null || end >= first) { "Recurrence end precedes start" }
            val revision = p.optLong("revision", 1); require(revision >= 1) { "Malformed revision" }
            TrainingPlan(p.getString("id"), userId, humanUserId, p.getInt("templateId"), p.getString("templateGlobalId"),
                p.getString("routineName"), first, p.minute("preferredMinuteOfDay"), mask, end,
                p.optLong("createdAt", System.currentTimeMillis()), p.optLong("updatedAt", System.currentTimeMillis()),
                p.getString("id"), revision, p.nullableLong("deletedAt"), "PENDING_UPLOAD", null, null,
                p.optString("originDeviceId", "restored-backup"))
        }
        require(plans.map { it.id }.distinct().size == plans.size) { "Duplicate plan identifier" }
        val planIds = plans.mapTo(hashSetOf()) { it.id }

        val occurrences = (0 until occurrencesArray.length()).map { index -> occurrencesArray.getJSONObject(index) }.map { o ->
            owner(o.getString("humanUserId")); require(o.getString("seriesId") in planIds) { "Missing routine plan reference" }
            val status = o.getString("status"); require(status in statuses) { "Unsupported occurrence status" }
            val completedAt = o.nullableLong("completedAt"); val linked = o.nullableLong("linkedSessionId")?.toInt()
            val deleted = o.nullableLong("deletedAt"); val revision = o.optLong("revision", 1)
            require(revision >= 1) { "Malformed revision" }
            require(status != "COMPLETED" || (completedAt != null && linked != null && deleted == null)) {
                "Completed occurrence metadata is inconsistent"
            }
            require(status != "COMPLETED" || linked in sessionIds) { "Completed occurrence link is missing" }
            PlannedWorkout(o.getString("id"), o.getString("seriesId"), userId, humanUserId, o.getInt("templateId"),
                o.getString("templateGlobalId"), o.getString("routineName"), o.getLong("scheduledEpochDay"),
                o.getLong("originalEpochDay"), o.minute("preferredMinuteOfDay"), status, completedAt, linked,
                o.optBoolean("reminderEnabled"), o.optBoolean("detachedFromSeries"),
                o.optLong("createdAt", System.currentTimeMillis()), o.optLong("updatedAt", System.currentTimeMillis()),
                o.getString("id"), revision, deleted, "PENDING_UPLOAD", null, null,
                o.optString("originDeviceId", "restored-backup"))
        }
        require(occurrences.map { it.id }.distinct().size == occurrences.size) { "Duplicate occurrence identifier" }
        require(occurrences.groupBy { it.seriesId to it.scheduledEpochDay }.values.none { it.size > 1 }) {
            "Duplicate occurrence schedule"
        }
        return Payload(plans, occurrences)
    }
}
