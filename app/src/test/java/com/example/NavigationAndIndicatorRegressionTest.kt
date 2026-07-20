package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.performClick
import com.example.ui.components.ExerciseCardHeader
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationAndIndicatorRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testExerciseCardHeader_collapsedState() {
        var clicked = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ExerciseCardHeader(
                    exerciseName = "Bench Press",
                    category = "Chest",
                    labelTag = "A1",
                    isExpanded = false,
                    onToggleExpand = { clicked = true },
                    onDelete = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onPairClick = {},
                    canMoveUp = false,
                    canMoveDown = false
                )
            }
        }

        // Verify expand control exists with correct tag and text
        val expandTag = "expand_collapse_control_Bench_Press"
        composeTestRule.onNodeWithTag(expandTag).assertExists()
        composeTestRule.onNodeWithTag(expandTag).assertTextContains("Expand")
        composeTestRule.onNodeWithTag(expandTag).assertContentDescriptionContains("Expand Bench Press")

        // Click the control and verify it invokes the callback
        composeTestRule.onNodeWithTag(expandTag).performClick()
        assertTrue(clicked)
    }

    @Test
    fun testExerciseCardHeader_expandedState() {
        var clicked = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ExerciseCardHeader(
                    exerciseName = "Squats",
                    category = "Legs",
                    labelTag = "B2",
                    isExpanded = true,
                    onToggleExpand = { clicked = true },
                    onDelete = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onPairClick = {},
                    canMoveUp = false,
                    canMoveDown = false
                )
            }
        }

        // Verify collapse control exists with correct tag and text
        val collapseTag = "expand_collapse_control_Squats"
        composeTestRule.onNodeWithTag(collapseTag).assertExists()
        composeTestRule.onNodeWithTag(collapseTag).assertTextContains("Collapse")
        composeTestRule.onNodeWithTag(collapseTag).assertContentDescriptionContains("Collapse Squats")

        // Click the control and verify it invokes the callback
        composeTestRule.onNodeWithTag(collapseTag).performClick()
        assertTrue(clicked)
    }
}
