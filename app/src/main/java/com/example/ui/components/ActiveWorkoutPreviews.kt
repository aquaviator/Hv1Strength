package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme

/**
 * 1. Ordinary Single Exercise Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewOrdinarySingleExercise() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                CurrentExerciseHero(
                    exerciseName = "Barbell Bench Press",
                    category = "Chest",
                    groupLabel = null,
                    currentSetNumber = 1,
                    totalSets = 4,
                    targetWeight = 80f,
                    targetReps = 8,
                    prevSummary = "80 kg × 8 reps",
                    coachingCues = "Keep shoulder blades retracted and drive feet into the ground.",
                    onCuesClick = {}
                )
            }
        }
    }
}

/**
 * 2. Active Superset A1 Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewActiveSupersetA1() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CurrentExerciseHero(
                    exerciseName = "Barbell Bench Press",
                    category = "Chest",
                    groupLabel = "A1",
                    currentSetNumber = 1,
                    totalSets = 3,
                    targetWeight = 85f,
                    targetReps = 6,
                    prevSummary = "85 kg × 5 reps",
                    coachingCues = "Retract scapula and drive through feet.",
                    onCuesClick = {}
                )
                SupersetProgressPanel(
                    groupLabel = "SUPERSET A",
                    groupType = "Agonist-Antagonist",
                    currentRound = 1,
                    totalRounds = 3,
                    exercisesInGroup = listOf(
                        "Barbell Bench Press" to false,
                        "Weighted Chin-Up" to false
                    ),
                    activeExerciseIndexInGroup = 0
                )
            }
        }
    }
}

/**
 * 3. Active Superset A2 Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewActiveSupersetA2() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CurrentExerciseHero(
                    exerciseName = "Weighted Chin-Up",
                    category = "Back",
                    groupLabel = "A2",
                    currentSetNumber = 1,
                    totalSets = 3,
                    targetWeight = 15f,
                    targetReps = 6,
                    prevSummary = "10 kg × 6 reps",
                    coachingCues = "Pull with elbows and squeeze the lats.",
                    onCuesClick = {}
                )
                SupersetProgressPanel(
                    groupLabel = "SUPERSET A",
                    groupType = "Agonist-Antagonist",
                    currentRound = 1,
                    totalRounds = 3,
                    exercisesInGroup = listOf(
                        "Barbell Bench Press" to true,
                        "Weighted Chin-Up" to false
                    ),
                    activeExerciseIndexInGroup = 1
                )
            }
        }
    }
}

/**
 * 4. Rest State Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewRestState() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RestStatePanel(
                restTimeRemaining = 85,
                totalRestDuration = 90,
                isPaused = false,
                nextExerciseName = "Barbell Row",
                nextSetNumber = 2,
                nextTargetPrescription = "70 kg × 8 reps",
                onAddSecs = {},
                onReduceSecs = {},
                onSkip = {},
                onPauseToggle = {}
            )
        }
    }
}

/**
 * 5. Paused Rest Timer Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewPausedRestTimer() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RestStatePanel(
                restTimeRemaining = 45,
                totalRestDuration = 90,
                isPaused = true,
                nextExerciseName = "Incline Dumbbell Press",
                nextSetNumber = 3,
                nextTargetPrescription = "32 kg × 10 reps",
                onAddSecs = {},
                onReduceSecs = {},
                onSkip = {},
                onPauseToggle = {}
            )
        }
    }
}

/**
 * 6. Recovered Workout Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewRecoveredWorkout() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WorkoutStatusHeader(
                    workoutName = "Push Day A (Recovered)",
                    elapsedTime = "24:15",
                    completedSets = 6,
                    totalSets = 12,
                    onFinishClick = {},
                    onCancelClick = {},
                    onAddExerciseClick = {},
                    onRenameWorkout = {}
                )
                CurrentExerciseHero(
                    exerciseName = "Dumbbell Shoulder Press",
                    category = "Shoulders",
                    groupLabel = null,
                    currentSetNumber = 3,
                    totalSets = 4,
                    targetWeight = 26f,
                    targetReps = 10,
                    prevSummary = "24 kg × 10 reps",
                    coachingCues = "Keep core tight. Push straight up.",
                    onCuesClick = {}
                )
            }
        }
    }
}

/**
 * 7. Final Set Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewFinalSet() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CurrentExerciseHero(
                    exerciseName = "Barbell Squat",
                    category = "Legs",
                    groupLabel = null,
                    currentSetNumber = 3,
                    totalSets = 3,
                    targetWeight = 120f,
                    targetReps = 5,
                    prevSummary = "115 kg × 5 reps",
                    coachingCues = "Squat to parallel or lower. Drive out of the hole.",
                    onCuesClick = {}
                )
                CompleteSetButton(
                    onClick = {},
                    enabled = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 8. Incomplete Workout Finish Sheet Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewIncompleteWorkoutFinishSheet() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FinishWorkoutSheet(
                workoutName = "High Volume Leg Day",
                durationText = "12:30",
                completedSets = 2,
                totalSets = 15,
                onConfirmFinish = {},
                onDismiss = {}
            )
        }
    }
}

/**
 * 9. Pounds Unit Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewPoundsUnit() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                WeightControl(
                    weight = 135f,
                    isMetric = false,
                    onWeightChange = {}
                )
            }
        }
    }
}

/**
 * 10. Zero Previous Performance Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewZeroPreviousPerformance() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                CurrentExerciseHero(
                    exerciseName = "Cable Face Pull",
                    category = "Shoulders",
                    groupLabel = null,
                    currentSetNumber = 1,
                    totalSets = 3,
                    targetWeight = 20f,
                    targetReps = 12,
                    prevSummary = "No prior history",
                    coachingCues = "Pull to nose and flare elbows.",
                    onCuesClick = {}
                )
            }
        }
    }
}

/**
 * 11. Long Exercise Name Preview
 */
@Preview(showBackground = true)
@Composable
fun PreviewLongExerciseName() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                CurrentExerciseHero(
                    exerciseName = "Dumbbell Incline Chest Press on 45-Degree Bench with Pronated Grip",
                    category = "Chest",
                    groupLabel = null,
                    currentSetNumber = 1,
                    totalSets = 4,
                    targetWeight = 34f,
                    targetReps = 10,
                    prevSummary = "32 kg × 10 reps",
                    coachingCues = "Keep elbows at 45 degrees. Squeeze chest at the top.",
                    onCuesClick = {}
                )
            }
        }
    }
}

/**
 * 12. Large Font Scale Preview
 */
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
fun PreviewLargeFontScale() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CurrentExerciseHero(
                    exerciseName = "Barbell Bench Press",
                    category = "Chest",
                    groupLabel = null,
                    currentSetNumber = 2,
                    totalSets = 4,
                    targetWeight = 80f,
                    targetReps = 8,
                    prevSummary = "80 kg × 8 reps",
                    coachingCues = "Retract shoulders.",
                    onCuesClick = {}
                )
                WeightControl(
                    weight = 80f,
                    isMetric = true,
                    onWeightChange = {}
                )
            }
        }
    }
}

/**
 * 13. Small Handset Preview
 */
@Preview(showBackground = true, device = Devices.NEXUS_5)
@Composable
fun PreviewSmallHandset() {
    MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WorkoutStatusHeader(
                    workoutName = "Push A",
                    elapsedTime = "15:45",
                    completedSets = 3,
                    totalSets = 10,
                    onFinishClick = {},
                    onCancelClick = {},
                    onAddExerciseClick = {},
                    onRenameWorkout = {}
                )
                CurrentExerciseHero(
                    exerciseName = "Overhead Barbell Press",
                    category = "Shoulders",
                    groupLabel = null,
                    currentSetNumber = 2,
                    totalSets = 4,
                    targetWeight = 50f,
                    targetReps = 6,
                    prevSummary = "47.5 kg × 6 reps",
                    coachingCues = "Brace core tight.",
                    onCuesClick = {}
                )
            }
        }
    }
}
