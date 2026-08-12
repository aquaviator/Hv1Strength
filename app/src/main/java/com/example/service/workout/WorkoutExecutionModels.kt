package com.example.service.workout

data class WorkoutExecutionSnapshot(
    val sessionId: String,
    val workoutName: String,
    val startedAt: Long,
    val currentExercise: String?,
    val completedSets: Int,
    val totalSets: Int,
    val restEndsAt: Long?,
    val restPaused: Boolean
) {
    fun elapsedSeconds(now: Long) = ((now - startedAt).coerceAtLeast(0L) / 1_000L).toInt()
    fun restRemainingSeconds(now: Long) = if (restPaused || restEndsAt == null) null else
        (((restEndsAt - now).coerceAtLeast(0L) + 999L) / 1_000L).toInt()
}

object WorkoutExecutionParser {
    fun parse(backup: com.example.data.ActiveWorkoutBackup): WorkoutExecutionSnapshot {
        val metadata = org.json.JSONObject(backup.exerciseMetadataJson)
        val recovery = metadata.optJSONObject("__global_recovery__")
        val sessionId = recovery?.optString("activeSessionId")?.takeIf { it.isNotBlank() }
            ?: "legacy-${backup.startTime}"
        val currentId = recovery?.optString("currentExerciseId")?.takeIf { it.isNotBlank() }
        val exercises = org.json.JSONArray(backup.exercisesJson)
        var currentName: String? = null
        for (index in 0 until exercises.length()) {
            val item = exercises.getJSONObject(index)
            if (currentId == null || item.optString("id") == currentId) { currentName = item.optString("name").takeIf { it.isNotBlank() }; if (currentId != null) break }
        }
        val sets = org.json.JSONObject(backup.setsJson)
        var total = 0; var completed = 0
        sets.keys().forEach { key -> val array = sets.getJSONArray(key); total += array.length(); for (i in 0 until array.length()) if (array.getJSONObject(i).optBoolean("isCompleted")) completed++ }
        val restEndsAt = recovery?.takeUnless { it.isNull("restTimerEndTimestamp") }?.optLong("restTimerEndTimestamp")
        return WorkoutExecutionSnapshot(sessionId, backup.templateName, backup.startTime, currentName, completed, total,
            restEndsAt?.takeIf { it > 0L }, recovery?.optBoolean("isRestTimerPaused", false) ?: false)
    }
}

class WorkoutServiceSessionGate {
    var activeSessionId: String? = null
        private set
    fun accept(requestedSessionId: String?, snapshot: WorkoutExecutionSnapshot?): Boolean {
        if (snapshot == null) return false
        if (requestedSessionId != null && snapshot.sessionId != requestedSessionId) return false
        if (activeSessionId != null && activeSessionId != snapshot.sessionId) return false
        activeSessionId = snapshot.sessionId
        return true
    }
    fun clear() { activeSessionId = null }
}
