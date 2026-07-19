package com.example.domain

object ProgressionCalculator {
    fun calculateWeightDifference(lastAvg: Double, prevAvg: Double): Double {
        return lastAvg - prevAvg
    }

    fun formatProgressTrend(diff: Double): String {
        return when {
            diff > 0 -> {
                val formatted = String.format(java.util.Locale.US, "%.1f", diff).removeSuffix(".0")
                "+$formatted kg"
            }
            diff < 0 -> {
                val formatted = String.format(java.util.Locale.US, "%.1f", diff).removeSuffix(".0")
                "$formatted kg"
            }
            else -> "Stable"
        }
    }

    fun getProgressTrend(sortedSessions: List<String>, setsBySession: Map<String, List<SetProgressionData>>): String {
        if (sortedSessions.size < 2) {
            return "Baseline Established"
        }
        val lastSessionId = sortedSessions[0]
        val prevSessionId = sortedSessions[1]
        val lastAvg = setsBySession[lastSessionId]?.map { it.weight }?.average() ?: 0.0
        val prevAvg = setsBySession[prevSessionId]?.map { it.weight }?.average() ?: 0.0
        val diff = lastAvg - prevAvg
        return formatProgressTrend(diff)
    }
}

interface SetProgressionData {
    val weight: Float
}
