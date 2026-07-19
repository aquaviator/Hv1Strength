package com.example.domain

object VolumeCalculator {
    /**
     * Calculates volume for a single set.
     */
    fun calculateVolume(weight: Float, reps: Int): Float {
        if (weight < 0f || reps < 0) return 0f
        return weight * reps
    }

    /**
     * Calculates total volume for a list of sets.
     */
    fun calculateTotalVolume(sets: List<SetVolumeData>): Float {
        return sets.filter { it.isCompleted }
            .sumOf { (it.weight * it.reps).toDouble() }
            .toFloat()
    }
}

interface SetVolumeData {
    val weight: Float
    val reps: Int
    val isCompleted: Boolean
}
