package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.*
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ActiveWorkoutScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun ordinary_single_exercise_screenshot() {
        composeTestRule.setContent {
            PreviewOrdinarySingleExercise()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/ordinary_single_exercise.png")
    }

    @Test
    fun active_superset_a1_screenshot() {
        composeTestRule.setContent {
            PreviewActiveSupersetA1()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/active_superset_a1.png")
    }

    @Test
    fun active_superset_a2_screenshot() {
        composeTestRule.setContent {
            PreviewActiveSupersetA2()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/active_superset_a2.png")
    }

    @Test
    fun rest_state_screenshot() {
        composeTestRule.setContent {
            PreviewRestState()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/rest_state.png")
    }

    @Test
    fun paused_rest_timer_screenshot() {
        composeTestRule.setContent {
            PreviewPausedRestTimer()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/paused_rest_timer.png")
    }

    @Test
    fun recovered_workout_screenshot() {
        composeTestRule.setContent {
            PreviewRecoveredWorkout()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/recovered_workout.png")
    }

    @Test
    fun final_set_screenshot() {
        composeTestRule.setContent {
            PreviewFinalSet()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/final_set.png")
    }

    @Test
    fun incomplete_workout_finish_sheet_screenshot() {
        composeTestRule.setContent {
            PreviewIncompleteWorkoutFinishSheet()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/incomplete_workout_finish_sheet.png")
    }

    @Test
    fun pounds_unit_screenshot() {
        composeTestRule.setContent {
            PreviewPoundsUnit()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/pounds_unit.png")
    }

    @Test
    fun zero_previous_performance_screenshot() {
        composeTestRule.setContent {
            PreviewZeroPreviousPerformance()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/zero_previous_performance.png")
    }

    @Test
    fun long_exercise_name_screenshot() {
        composeTestRule.setContent {
            PreviewLongExerciseName()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/long_exercise_name.png")
    }

    @Test
    fun large_font_scale_screenshot() {
        composeTestRule.setContent {
            PreviewLargeFontScale()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/large_font_scale.png")
    }

    @Config(qualifiers = "w320dp-h568dp-xhdpi", sdk = [36])
    @Test
    fun small_handset_screenshot() {
        composeTestRule.setContent {
            PreviewSmallHandset()
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/small_handset.png")
    }
}
