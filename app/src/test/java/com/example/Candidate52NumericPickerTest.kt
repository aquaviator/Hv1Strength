package com.example

import com.example.core.util.UnitConverter
import com.example.ui.components.NumericPickerConfiguration
import com.example.ui.components.NumericPickerPresets
import org.junit.Assert.*
import org.junit.Test

class Candidate52NumericPickerTest {

    @Test
    fun testNumericPickerConfiguration_formatting() {
        val configMetric = NumericPickerConfiguration(
            title = "Weight",
            unitLabel = "kg",
            decimalPlaces = 1
        )
        assertEquals("22.5", configMetric.formatValue(22.5))
        assertEquals("20", configMetric.formatValue(20.0))

        val configReps = NumericPickerConfiguration(
            title = "Reps",
            decimalPlaces = 0
        )
        assertEquals("10", configReps.formatValue(10.0))
    }

    @Test
    fun testWeightConfig_metricAndImperial() {
        // Metric test
        val metricConfig = NumericPickerPresets.weightConfig(
            isMetric = true,
            currentKg = 22.5
        )
        assertEquals("kg", metricConfig.unitLabel)
        assertEquals(2.5, metricConfig.step, 0.001)
        assertTrue(metricConfig.quickValues.contains(22.5))

        // Imperial test
        val imperialConfig = NumericPickerPresets.weightConfig(
            isMetric = false,
            currentKg = 22.68 // ~50 lbs
        )
        assertEquals("lbs", imperialConfig.unitLabel)
        assertEquals(5.0, imperialConfig.step, 0.001)
    }

    @Test
    fun testCanonicalUnitConversions() {
        val originalKg = 22.5f
        val displayLbs = UnitConverter.kgToLb(originalKg)
        assertEquals(49.604f, displayLbs, 0.01f)

        val selectedLbs = 50.0f
        val canonicalKg = UnitConverter.lbToKg(selectedLbs)
        assertEquals(22.6796f, canonicalKg, 0.01f)
    }

    @Test
    fun testRestConfig_formatter() {
        val restConfig = NumericPickerPresets.restConfig(90)
        assertEquals("45s", restConfig.formatValue(45.0))
        assertEquals("1m 30s", restConfig.formatValue(90.0))
        assertEquals("2m", restConfig.formatValue(120.0))
    }
}
