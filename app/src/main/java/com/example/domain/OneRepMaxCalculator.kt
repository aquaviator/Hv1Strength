package com.example.domain

object OneRepMaxCalculator {
    /**
     * Estimates 1RM using Epley's formula: 1RM = w * (1 + r / 30)
     */
    fun estimateEpley(weight: Float, reps: Int): Float {
        if (reps <= 0 || weight < 0f) return 0f
        if (reps == 1) return weight
        return weight * (1f + reps / 30f)
    }

    /**
     * Estimates 1RM using Brzycki's formula: 1RM = w / (1.0278 - 0.0278 * r)
     */
    fun estimateBrzycki(weight: Float, reps: Int): Float {
        if (reps <= 0 || weight < 0f) return 0f
        if (reps == 1) return weight
        val denominator = 1.0278f - 0.0278f * reps
        if (denominator <= 0f) return 0f
        return weight / denominator
    }

    /**
     * Calculates the maximum 1RM among completed sets using Epley's formula.
     */
    fun calculateMax1RM(sets: List<Set1RMData>): Float {
        return sets.filter { it.isCompleted }
            .maxOfOrNull { estimateEpley(it.weight, it.reps) } ?: 0f
    }
}

interface Set1RMData {
    val weight: Float
    val reps: Int
    val isCompleted: Boolean
}
