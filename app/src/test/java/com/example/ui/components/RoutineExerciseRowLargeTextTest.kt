package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.Exercise
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutineExerciseRowLargeTextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun targetSummaryKeepsReadableWidthAtOnePointFiveFontScale() = verifyAt(1.5f)

    @Test fun targetSummaryKeepsReadableWidthAtTwoFontScale() = verifyAt(2f)

    private fun verifyAt(fontScale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(160.dp)) {
                        ExerciseRow(
                            exercise = Exercise(
                                id = "long_routine_exercise",
                                name = "Dumbbell Romanian Deadlift",
                                category = "Legs"
                            ),
                            setsCount = 1,
                            targetSummary = "8-10 @ 0 kg",
                            onClick = {}
                        )
                    }
                }
            }
        }

        val name = compose.onNodeWithTag("routine_exercise_name", useUnmergedTree = true).assertExists().fetchSemanticsNode()
        val target = compose.onNodeWithTag("routine_exercise_target", useUnmergedTree = true).assertExists().fetchSemanticsNode()
        assertTrue("Exercise name must keep useful horizontal space", name.boundsInRoot.width >= 80f)
        assertTrue("Target summary must not collapse into a vertical character column", target.boundsInRoot.width >= 72f)
        assertTrue("Target summary must stay inside the routine row", target.boundsInRoot.right <= 360f)
    }
}
