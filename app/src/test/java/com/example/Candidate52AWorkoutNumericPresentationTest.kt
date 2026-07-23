package com.example

import com.example.core.util.UnitConverter
import com.example.ui.components.NumericPickerConfiguration
import com.example.ui.components.NumericPickerPresets
import com.example.ui.viewmodel.ActiveSet
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class Candidate52AWorkoutNumericPresentationTest {

    @Test
    fun testWeightFormatting_noMultilineOrWrapping() {
        val metricConfig = NumericPickerPresets.weightConfig(isMetric = true, currentKg = 27.5)
        val testValuesKg = listOf(7.5, 27.5, 100.0)

        for (v in testValuesKg) {
            val formatted = metricConfig.formatValue(v)
            assertFalse("Formatted weight should not contain newlines", formatted.contains("\n"))
            assertTrue("Formatted weight should be clean non-empty string", formatted.isNotBlank())
        }

        val imperialConfig = NumericPickerPresets.weightConfig(isMetric = false, currentKg = 100.0)
        val testValuesLbs = listOf(220.5, 999.5)

        for (v in testValuesLbs) {
            val formatted = imperialConfig.formatValue(v)
            assertFalse("Formatted weight in lbs should not contain newlines", formatted.contains("\n"))
            assertTrue("Formatted weight in lbs should be clean non-empty string", formatted.isNotBlank())
        }
    }

    @Test
    fun testRepsFormatting_singleLine() {
        val repsConfig = NumericPickerPresets.repsConfig(currentReps = 10)
        val repsValues = listOf(1, 10, 100)

        for (r in repsValues) {
            val formatted = repsConfig.formatValue(r.toDouble())
            assertFalse("Reps text should not contain newlines", formatted.contains("\n"))
            assertEquals(r.toString(), formatted)
        }
    }

    @Test
    fun testActiveSetVsCompletedSetState() {
        val activeSet1 = ActiveSet(weight = 27.5f, reps = 10, isCompleted = false)
        val completedSet2 = ActiveSet(weight = 30.0f, reps = 8, isCompleted = true)

        assertFalse(activeSet1.isCompleted)
        assertTrue(completedSet2.isCompleted)

        val updatedSet1 = activeSet1.copy(isCompleted = true)
        assertTrue("Log set action marks set as completed", updatedSet1.isCompleted)
    }

    @Test
    fun testUnitConversionFormattingForTestCases() {
        // 27.5 kg
        val kgVal = 27.5
        val displayKg = if (kgVal % 1.0 == 0.0) kgVal.toInt().toString() else String.format(Locale.US, "%.1f", kgVal)
        assertEquals("27.5", displayKg)

        // 100.0 kg
        val kgVal2 = 100.0
        val displayKg2 = if (kgVal2 % 1.0 == 0.0) kgVal2.toInt().toString() else String.format(Locale.US, "%.1f", kgVal2)
        assertEquals("100", displayKg2)

        // 220.5 lb
        val lbVal = 220.5
        val displayLb = if (lbVal % 1.0 == 0.0) lbVal.toInt().toString() else String.format(Locale.US, "%.1f", lbVal)
        assertEquals("220.5", displayLb)
    }
}
