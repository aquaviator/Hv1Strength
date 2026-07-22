package com.example.ui.viewmodel

import com.example.data.LoggedSet

data class ExerciseProfile(
    val exerciseId: String,
    val totalSessionsCount: Int,
    val bestSet: String,
    val bestWeight: Float,
    val bestReps: Int,
    val typicalStartingWeight: Float,
    val typicalWorkingWeight: Float,
    val typicalRepRange: String,
    val averageRPE: Float?,
    val totalVolume: Float,
    val estimated1RM: Float,
    val progressTrend: String,
    val recoveryTrend: String,
    val confidenceScore: String
)

data class TrainingRecommendation(
    val startWeight: Float,
    val targetReps: Int,
    val reason: String,
    val confidence: String
)

object ExerciseIntelligence {
    fun getProfile(exerciseId: String, allLoggedSets: List<LoggedSet>, isMetric: Boolean = true): ExerciseProfile? {
        val completedSets = allLoggedSets.filter { it.exerciseId == exerciseId && it.isCompleted }
        if (completedSets.isEmpty()) return null

        val setsBySession = completedSets.groupBy { it.sessionId }
        val sessionsCount = setsBySession.size

        val bestSetByWeight = completedSets.maxByOrNull { it.weight }
        val bestWeight = bestSetByWeight?.weight ?: 0f
        val bestReps = completedSets.filter { it.weight == bestWeight }.maxOfOrNull { it.reps } ?: 0
        
        val bestSetByVolume = completedSets.maxByOrNull { it.weight * it.reps }
        val bestSetString = if (bestSetByVolume != null) "${com.example.core.util.UnitConverter.formatWeight(bestSetByVolume.weight.toDouble(), isMetric)} × ${bestSetByVolume.reps}" else "—"

        val firstSets = completedSets.filter { it.setNumber == 1 }
        val typicalStartingWeight = if (firstSets.isNotEmpty()) firstSets.map { it.weight }.average().toFloat() else completedSets.map { it.weight }.firstOrNull() ?: 0f

        val typicalWorkingWeight = completedSets.map { it.weight }.average().toFloat()

        val reps = completedSets.map { it.reps }
        val minReps = reps.minOrNull() ?: 0
        val maxReps = reps.maxOrNull() ?: 0
        val typicalRepRange = if (minReps == maxReps) "$minReps" else "$minReps-$maxReps"

        val rpes = completedSets.mapNotNull { it.rpe }
        val averageRPE = if (rpes.isNotEmpty()) rpes.average().toFloat() else null

        val totalVolume = completedSets.sumOf { (it.weight * it.reps).toDouble() }.toFloat()

        val max1RM = completedSets.maxOfOrNull { it.weight * (1f + it.reps / 30f) } ?: 0f

        val sortedSessions = setsBySession.keys.sortedDescending()
        val progressTrend = if (sortedSessions.size >= 2) {
            val lastSessionId = sortedSessions[0]
            val prevSessionId = sortedSessions[1]
            val lastAvg = setsBySession[lastSessionId]?.map { it.weight }?.average() ?: 0.0
            val prevAvg = setsBySession[prevSessionId]?.map { it.weight }?.average() ?: 0.0
            val diff = lastAvg - prevAvg
            if (diff > 0) {
                "+${com.example.core.util.UnitConverter.formatWeight(diff, isMetric)}"
            } else if (diff < 0) {
                com.example.core.util.UnitConverter.formatWeight(diff, isMetric)
            } else {
                "Stable"
            }
        } else {
            "Baseline Established"
        }

        val recoveryTrend = when {
            averageRPE == null -> "—"
            averageRPE <= 7f -> "Fast"
            averageRPE <= 8.5f -> "Steady"
            else -> "High Fatigue"
        }

        val confidenceScore = when {
            sessionsCount < 3 -> "Baseline"
            sessionsCount <= 8 -> "Medium"
            else -> "High"
        }

        return ExerciseProfile(
            exerciseId = exerciseId,
            totalSessionsCount = sessionsCount,
            bestSet = bestSetString,
            bestWeight = bestWeight,
            bestReps = bestReps,
            typicalStartingWeight = typicalStartingWeight,
            typicalWorkingWeight = typicalWorkingWeight,
            typicalRepRange = typicalRepRange,
            averageRPE = averageRPE,
            totalVolume = totalVolume,
            estimated1RM = max1RM,
            progressTrend = progressTrend,
            recoveryTrend = recoveryTrend,
            confidenceScore = confidenceScore
        )
    }

    fun getRecommendation(exerciseId: String, allLoggedSets: List<LoggedSet>, fallbackTargetReps: Int = 8, fallbackWeight: Float = 20f): TrainingRecommendation {
        val completedSets = allLoggedSets.filter { it.exerciseId == exerciseId && it.isCompleted }
        if (completedSets.isEmpty()) {
            return TrainingRecommendation(
                startWeight = fallbackWeight,
                targetReps = fallbackTargetReps,
                reason = "No previous history. Use today's session to establish a benchmark baseline.",
                confidence = "Baseline"
            )
        }

        val setsBySession = completedSets.groupBy { it.sessionId }
        val sortedSessionIds = setsBySession.keys.sortedDescending()
        val lastSessionSets = setsBySession[sortedSessionIds.first()] ?: emptyList()

        val lastMaxWeight = lastSessionSets.maxOfOrNull { it.weight } ?: fallbackWeight
        val lastMaxReps = lastSessionSets.maxOfOrNull { it.reps } ?: fallbackTargetReps
        val lastAvgRpe = lastSessionSets.mapNotNull { it.rpe }.average().let { if (it.isNaN()) null else it.toFloat() }

        return when {
            lastAvgRpe == null -> {
                val suggestedWeight = lastMaxWeight + 2.5f
                TrainingRecommendation(
                    startWeight = suggestedWeight,
                    targetReps = lastMaxReps,
                    reason = "You completed your previous session. Let's push for a minor progression.",
                    confidence = "Medium"
                )
            }
            lastAvgRpe <= 7.0f -> {
                val suggestedWeight = lastMaxWeight + 2.5f
                TrainingRecommendation(
                    startWeight = suggestedWeight,
                    targetReps = lastMaxReps,
                    reason = "Completed comfortably. Step up your working weight for progressive overload.",
                    confidence = "High"
                )
            }
            lastAvgRpe >= 9.0f -> {
                TrainingRecommendation(
                    startWeight = lastMaxWeight,
                    targetReps = lastMaxReps,
                    reason = "Previous effort was highly taxing. Solidify this weight with perfect control first.",
                    confidence = "Medium"
                )
            }
            else -> {
                val suggestedWeight = lastMaxWeight + (if (lastMaxReps >= 10) 2.5f else 1.25f)
                TrainingRecommendation(
                    startWeight = suggestedWeight,
                    targetReps = lastMaxReps,
                    reason = "Solid performance last time. Increase slightly to keep driving adaptation.",
                    confidence = "High"
                )
            }
        }
    }
}
