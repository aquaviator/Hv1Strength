package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.example.catalogue.CustomTrackingProfile
import com.example.catalogue.LibrarySection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExerciseLargeTextUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allSectionsRemainVisibleAndCustomCanBeSelectedAtTwoX() {
        var observed = LibrarySection.ALL
        compose.setContent {
            var selected by remember { mutableStateOf(LibrarySection.ALL) }
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    ExerciseSectionSelector(selected, { selected = it; observed = it })
                }
            }
        }

        LibrarySection.entries.forEach {
            compose.onNodeWithTag("exercise_section_${it.name.lowercase()}").assertIsDisplayed()
        }
        compose.onNodeWithTag("exercise_section_custom").performClick().assertIsSelected().assertIsDisplayed()
        compose.runOnIdle { assertEquals(LibrarySection.CUSTOM, observed) }
    }

    @Test fun completeFormAndSaveRemainReachableAtOnePointFiveX() = verifyFormEndAt(1.5f)

    @Test fun completeFormAndSaveRemainReachableAtTwoX() = verifyFormEndAt(2f)

    @Test fun blankSaveRevealsNameValidationError() {
        compose.setContent {
            var invalid by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    CustomExerciseDialog(
                        name = "", category = "Chest", trackingProfile = CustomTrackingProfile.REPS_LOAD,
                        showNameError = invalid, onNameChange = {}, onCategoryChange = {},
                        onTrackingProfileChange = {}, onDismiss = {}, onInvalidName = { invalid = true }, onSave = {}
                    )
                }
            }
        }
        scrollFormTo("save_custom_exercise_button")
        compose.onNodeWithTag("save_custom_exercise_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Enter an exercise name").assertIsDisplayed()
    }

    @Test fun keyboardFocusedFieldDoesNotPreventReachingActions() {
        renderForm(fontScale = 2f)
        compose.onNodeWithTag("custom_exercise_name_input").performClick().performTextInput("Keyboard Reach")
        scrollFormTo("save_custom_exercise_button")
        compose.onNodeWithTag("save_custom_exercise_button").assertIsDisplayed()
        scrollFormTo("custom_exercise_form_end")
        compose.onNodeWithTag("custom_exercise_form_end").assertIsDisplayed()
    }

    @Test fun cancelDoesNotSave() {
        var saves = 0
        var cancels = 0
        renderForm(fontScale = 1.5f, onSave = { saves++ }, onDismiss = { cancels++ })
        scrollFormTo("cancel_custom_exercise_button")
        compose.onNodeWithTag("cancel_custom_exercise_button").performClick()
        compose.runOnIdle { assertEquals(0, saves); assertEquals(1, cancels) }
    }

    @Test fun validSaveInvokesExactlyOnce() {
        var saves = 0
        renderForm(fontScale = 1.5f, initialName = "V35 Accessible", onSave = { saves++ })
        scrollFormTo("save_custom_exercise_button")
        compose.onNodeWithTag("save_custom_exercise_button").performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun enteredFormValueSurvivesRecomposition() {
        val recompositionTrigger = mutableStateOf(0)
        compose.setContent {
            recompositionTrigger.value
            var name by rememberSaveable { mutableStateOf("") }
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                MaterialTheme {
                    CustomExerciseDialog(
                        name = name,
                        category = "Chest",
                        trackingProfile = CustomTrackingProfile.REPS_LOAD,
                        showNameError = false,
                        onNameChange = { name = it },
                        onCategoryChange = {},
                        onTrackingProfileChange = {},
                        onDismiss = {},
                        onInvalidName = {},
                        onSave = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("custom_exercise_name_input").performTextInput("Rotation Safe")
        compose.runOnIdle { recompositionTrigger.value++ }
        compose.onNodeWithTag("custom_exercise_name_input").assertTextContains("Rotation Safe")
    }

    private fun verifyFormEndAt(fontScale: Float) {
        renderForm(fontScale)
        scrollFormTo("custom_tracking_duration_distance")
        compose.onNodeWithTag("custom_tracking_duration_distance").assertIsDisplayed()
        scrollFormTo("save_custom_exercise_button")
        compose.onNodeWithTag("save_custom_exercise_button").assertIsDisplayed()
        scrollFormTo("custom_exercise_form_end")
        compose.onNodeWithTag("custom_exercise_form_end").assertIsDisplayed()
    }

    private fun scrollFormTo(tag: String) {
        compose.onNodeWithTag("custom_exercise_form").performScrollToNode(hasTestTag(tag))
    }

    private fun renderForm(
        fontScale: Float,
        initialName: String = "",
        onSave: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        compose.setContent {
            var name by rememberSaveable { mutableStateOf(initialName) }
            var category by rememberSaveable { mutableStateOf("Chest") }
            var profile by rememberSaveable { mutableStateOf(CustomTrackingProfile.REPS_LOAD) }
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        CustomExerciseDialog(
                            name = name,
                            category = category,
                            trackingProfile = profile,
                            showNameError = false,
                            onNameChange = { name = it },
                            onCategoryChange = { category = it },
                            onTrackingProfileChange = { profile = it },
                            onDismiss = onDismiss,
                            onInvalidName = {},
                            onSave = onSave
                        )
                    }
                }
            }
        }
    }
}
