package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.catalogue.ExerciseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExerciseFilterSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longContentScrollsToFinalOptionWhileFooterRemainsReachable() {
        render()
        compose.onNodeWithTag("exercise_filter_options").performScrollToNode(hasTestTag("exercise_filter_last_option"))
        compose.onNodeWithTag("exercise_filter_last_option").assertIsDisplayed()
        compose.onNodeWithTag("clear_all_exercise_filters").assertIsDisplayed()
        compose.onNodeWithTag("apply_exercise_filters").assertIsDisplayed()
    }

    @Test fun applyAndClearInvokeOnlyTheirExplicitActions() {
        var applied = 0; var cleared = 0
        render(onApply = { applied++ }, onClear = { cleared++ })
        compose.onNodeWithText("Chest").performClick()
        compose.runOnIdle { assertEquals(0, applied) }
        compose.onNodeWithTag("apply_exercise_filters").performClick()
        compose.runOnIdle { assertEquals(1, applied); assertEquals(0, cleared) }
    }

    @Test fun cancelDismissesWithoutApplyingDraftSelection() {
        var applied = 0; var dismissed = 0
        render(onApply = { applied++ }, onDismiss = { dismissed++ })
        compose.onNodeWithText("Chest").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, applied); assertTrue(dismissed >= 1) }
    }

    private fun render(onApply: () -> Unit = {}, onClear: () -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                ExerciseFilterSheet(
                    categories = listOf("Abs", "Arms", "Back", "Cardio", "Chest", "Core", "Full Body", "Legs", "Mobility", "Shoulders"),
                    muscles = (1..32).map { "Muscle $it" }, equipment = (1..28).map { "Equipment $it" },
                    selectedCategories = emptyList(), selectedMuscles = emptyList(), selectedEquipment = emptyList(),
                    selectedCapabilities = emptyList(), selectedSource = ExerciseSource.ALL,
                    onCategory = {}, onMuscle = {}, onEquipment = {}, onCapability = {}, onSource = {},
                    onClear = onClear, onDismiss = onDismiss, onApply = onApply
                )
            }
        }
    }
}
