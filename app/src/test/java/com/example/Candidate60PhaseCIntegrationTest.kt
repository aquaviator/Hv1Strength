package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.HumanBordersAndElevation
import com.example.ui.theme.HumanShapes
import com.example.ui.theme.HumanSpacing
import com.example.ui.theme.HumanTypographyRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Candidate60PhaseCIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPhaseCDesignTokensAndTouchTargets() {
        // Touch targets must be at least 48dp
        assertTrue("Min touch target must be >= 48dp", HumanSpacing.minTouchTarget.value >= 48f)

        // Spacing scale integrity
        assertEquals(16f, HumanSpacing.pagePadding.value, 0.01f)
        assertEquals(24f, HumanSpacing.sectionGap.value, 0.01f)
        assertEquals(16f, HumanSpacing.cardPadding.value, 0.01f)

        // Shape radius integrity
        assertEquals(16f, HumanShapes.cardRadius.value, 0.01f)
        assertEquals(12f, HumanShapes.buttonRadius.value, 0.01f)

        // Elevation specifications
        assertEquals(0f, HumanBordersAndElevation.flatElevation.value, 0.01f)
        assertEquals(2f, HumanBordersAndElevation.subtleElevation.value, 0.01f)
    }

    @Test
    fun testPhaseCTypographyRoles() {
        assertNotNull("displayBrand role must be configured", HumanTypographyRoles.displayBrand)
        assertNotNull("screenTitle role must be configured", HumanTypographyRoles.screenTitle)
        assertNotNull("sectionTitle role must be configured", HumanTypographyRoles.sectionTitle)
        assertNotNull("numericHero role must be configured", HumanTypographyRoles.numericHero)
        assertNotNull("buttonLabel role must be configured", HumanTypographyRoles.buttonLabel)
    }

    @Test
    fun testPhaseCEmptyStateStringsInResources() {
        val appNameId = context.resources.getIdentifier("app_name", "string", context.packageName)
        assertTrue("app_name resource must exist", appNameId != 0)
        
        val appName = context.resources.getString(appNameId)
        assertEquals("Human v1 - Strength", appName)
    }
}
