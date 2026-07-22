package com.example.domain

object ProgressionCalculator {
    fun calculateWeightDifference(lastAvg: Double, prevAvg: Double): Double {
        return lastAvg - prevAvg
    }

    fun formatProgressTrend(diff: Double, isMetric: Boolean = true): String {
        val converted = if (isMetric) diff else com.example.core.util.UnitConverter.kgToLb(diff)
        val unit = if (isMetric) "kg" else "lbs"
        return when {
            converted > 0 -> {
                val formatted = String.format(java.util.Locale.US, "%.1f", converted).removeSuffix(".0")
                "+$formatted $unit"
            }
            converted < 0 -> {
                val formatted = String.format(java.util.Locale.US, "%.1f", converted).removeSuffix(".0")
                "$formatted $unit"
            }
            else -> "Stable"
        }
    }

    fun getProgressTrend(sortedSessions: List<String>, setsBySession: Map<String, List<SetProgressionData>>, isMetric: Boolean = true): String {
        if (sortedSessions.size < 2) {
            return "Baseline Established"
        }
        val lastSessionId = sortedSessions[0]
        val prevSessionId = sortedSessions[1]
        val lastAvg = setsBySession[lastSessionId]?.map { it.weight }?.average() ?: 0.0
        val prevAvg = setsBySession[prevSessionId]?.map { it.weight }?.average() ?: 0.0
        val diff = lastAvg - prevAvg
        return formatProgressTrend(diff, isMetric)
    }
}

interface SetProgressionData {
    val weight: Float
}
