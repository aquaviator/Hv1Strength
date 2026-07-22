package com.example.core.util

import kotlin.math.roundToInt

object UnitConverter {
    const val KG_TO_LB = 2.2046226218
    const val LB_TO_KG = 0.45359237

    fun kgToLb(kg: Double): Double = kg * KG_TO_LB
    fun lbToKg(lb: Double): Double = lb * LB_TO_KG

    fun kgToLb(kg: Float): Float = (kg * KG_TO_LB).toFloat()
    fun lbToKg(lb: Float): Float = (lb * LB_TO_KG).toFloat()

    fun formatWeight(weight: Float, isMetric: Boolean): String = formatWeight(weight.toDouble(), isMetric)
    fun formatWeightValueOnly(weight: Float, isMetric: Boolean): String = formatWeightValueOnly(weight.toDouble(), isMetric)

    /**
     * Formats weight with a clean decimal policy.
     * If the value is a whole number (e.g. 100.0), it removes the decimal part.
     * Otherwise, it keeps up to 2 decimal places, removing trailing zeros.
     */
    fun formatWeight(weight: Double, isMetric: Boolean): String {
        val converted = if (isMetric) weight else kgToLb(weight)
        val suffix = if (isMetric) "kg" else "lb"
        
        val rounded = (converted * 100).roundToInt() / 100.0
        val str = if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            val formatted = String.format(java.util.Locale.US, "%.2f", rounded)
            if (formatted.endsWith(".00")) {
                formatted.substringBefore(".")
            } else if (formatted.endsWith("0")) {
                formatted.substring(0, formatted.length - 1)
            } else {
                formatted
            }
        }
        return "$str $suffix"
    }

    /**
     * Same as above but without suffix for text fields / input editing.
     */
    fun formatWeightValueOnly(weight: Double, isMetric: Boolean): String {
        val converted = if (isMetric) weight else kgToLb(weight)
        val rounded = (converted * 100).roundToInt() / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            val formatted = String.format(java.util.Locale.US, "%.2f", rounded)
            if (formatted.endsWith(".00")) {
                formatted.substringBefore(".")
            } else if (formatted.endsWith("0")) {
                formatted.substring(0, formatted.length - 1)
            } else {
                formatted
            }
        }
    }
}
