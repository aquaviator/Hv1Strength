package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.StrengthDatabase
import com.example.data.UserPreferencesRepository
import com.example.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Candidate60PhaseBBrandFoundationTest {

    private lateinit var context: Context
    private lateinit var database: StrengthDatabase
    private lateinit var preferencesRepository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesRepository = UserPreferencesRepository(database.strengthDao())
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun testCentralTokenAvailability() {
        // Verify Electric Royal Blue derived from master SVG artwork #0066FF
        assertEquals(Color(0xFF0066FF), HumanElectricBlue)

        // Verify Dark Graphite Background derived from master logo #0A0D10
        assertEquals(Color(0xFF0A0D10), HumanDarkBackground)

        // Verify Spacing grid constants
        assertEquals(16.dp, HumanSpacing.pagePadding)
        assertEquals(24.dp, HumanSpacing.sectionGap)
        assertEquals(48.dp, HumanSpacing.minTouchTarget)

        // Verify Shape tokens
        assertNotNull(HumanShapes.card)
        assertNotNull(HumanShapes.button)

        // Verify Typography roles
        assertNotNull(HumanTypographyRoles.displayBrand)
        assertNotNull(HumanTypographyRoles.numericHero)
        assertNotNull(HumanTypographyRoles.exerciseTitle)
    }

    @Test
    fun testThemeMappingAndColorScheme() {
        // Dark theme color scheme checks
        assertEquals(HumanElectricBlue, HumanDarkColorScheme.primary)
        assertEquals(HumanDarkBackground, HumanDarkColorScheme.background)
        assertEquals(HumanDarkOnBackground, HumanDarkColorScheme.onBackground)

        // Light theme color scheme checks
        assertEquals(HumanElectricBlue, HumanLightColorScheme.primary)
        assertEquals(HumanLightBackground, HumanLightColorScheme.background)
        assertEquals(HumanLightOnBackground, HumanLightColorScheme.onBackground)
    }

    @Test
    fun testThemePreferencePersistence() = runBlocking {
        // Initial setting
        preferencesRepository.setTheme("dark")
        val savedDark = preferencesRepository.userPreferencesFlow.first().theme
        assertEquals("dark", savedDark)

        preferencesRepository.setTheme("light")
        val savedLight = preferencesRepository.userPreferencesFlow.first().theme
        assertEquals("light", savedLight)

        preferencesRepository.setTheme("system")
        val savedSystem = preferencesRepository.userPreferencesFlow.first().theme
        assertEquals("system", savedSystem)
    }

    @Test
    fun testBrandResourcesExist() {
        // Verify vector & launcher resources exist in Android application context
        val logoId = context.resources.getIdentifier("human_logo", "drawable", context.packageName)
        assertTrue("human_logo resource must exist", logoId != 0)

        val launcherId = context.resources.getIdentifier("human_launcher", "drawable", context.packageName)
        assertTrue("human_launcher resource must exist", launcherId != 0)

        val splashIconId = context.resources.getIdentifier("ic_splash_icon", "drawable", context.packageName)
        assertTrue("ic_splash_icon resource must exist", splashIconId != 0)

        val monochromeId = context.resources.getIdentifier("ic_launcher_foreground_monochrome", "drawable", context.packageName)
        assertTrue("ic_launcher_foreground_monochrome resource must exist", monochromeId != 0)

        val themeId = context.resources.getIdentifier("Theme.HumanStrength", "style", context.packageName)
        assertTrue("Theme.HumanStrength style must exist", themeId != 0)
    }

    @Test
    fun testColorContrastSmokeCheck() {
        // Helper to compute contrast ratio
        fun getContrastRatio(fg: Color, bg: Color): Double {
            return ColorUtils.calculateContrast(fg.toArgb(), bg.toArgb())
        }

        // Dark theme text contrast
        val darkTextContrast = getContrastRatio(HumanDarkColorScheme.onBackground, HumanDarkColorScheme.background)
        assertTrue("Dark theme text contrast should be >= 4.5:1 (was $darkTextContrast)", darkTextContrast >= 4.5)

        // Light theme text contrast
        val lightTextContrast = getContrastRatio(HumanLightColorScheme.onBackground, HumanLightColorScheme.background)
        assertTrue("Light theme text contrast should be >= 4.5:1 (was $lightTextContrast)", lightTextContrast >= 4.5)

        // Primary button contrast
        val darkPrimaryContrast = getContrastRatio(HumanDarkColorScheme.onPrimary, HumanDarkColorScheme.primary)
        assertTrue("Primary button text contrast should be >= 3.0:1 (was $darkPrimaryContrast)", darkPrimaryContrast >= 3.0)
    }
}
