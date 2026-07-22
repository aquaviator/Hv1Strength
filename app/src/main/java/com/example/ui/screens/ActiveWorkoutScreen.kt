package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.data.Exercise
import com.example.ui.viewmodel.ActiveSet
import com.example.ui.viewmodel.StrengthViewModel
import com.example.ui.viewmodel.ExerciseIntelligence
import com.example.ui.viewmodel.ActiveWorkoutEvent
import com.example.ui.components.*
import com.example.ui.theme.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: StrengthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSummary: (Long) -> Unit
) {
    val activeWorkoutState by viewModel.activeWorkoutState.collectAsState()
    val isCompletingWorkout by viewModel.isCompletingWorkout.collectAsState()
    val exercisesDb by viewModel.exercises.collectAsState()
    val allLoggedSets by viewModel.allLoggedSets.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(viewModel.activeWorkoutEvents) {
        viewModel.activeWorkoutEvents.collect { event ->
            android.util.Log.d("ActiveWorkoutScreen", "[COMPLETION] Event collected in UI: $event")
            when (event) {
                is ActiveWorkoutEvent.WorkoutCompleted -> {
                    android.util.Log.d("ActiveWorkoutScreen", "[COMPLETION] Navigating to summary with ID: ${event.workoutId}")
                    onNavigateToSummary(event.workoutId)
                }
                is ActiveWorkoutEvent.ShowError -> {
                    android.util.Log.e("ActiveWorkoutScreen", "[COMPLETION] Error event shown in toast: ${event.message}")
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val activeWorkout = activeWorkoutState
    if (activeWorkout == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Saving your epic session...",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        return
    }

    var elapsedTime by remember { mutableStateOf("00:00") }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showFinishWorkoutDialog by remember { mutableStateOf(false) }
    var showCuesDialog by remember { mutableStateOf(false) }

    val restTimeRemaining by viewModel.restTimeRemaining.collectAsState()
    val isRestTimerPaused by viewModel.isRestTimerPaused.collectAsState()
    val restTimerDuration by viewModel.restTimerDuration.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()

    // Accordion expansion state
    var doneExpanded by remember { mutableStateOf(false) }
    var nextExpanded by remember { mutableStateOf(false) }

    // Flat queue representation
    val flatSets by viewModel.executionQueue.collectAsState()

    // Find first incomplete set globally
    val activeFlatSet = flatSets.firstOrNull { !it.set.isCompleted }

    // Navigation and focus override inside the active exercise sets
    val currentExercise = activeFlatSet?.exercise
    val setsList = currentExercise?.let { activeWorkout.sets[it.id] } ?: emptyList()

    var focusedSetIndexOverride by remember(currentExercise?.id) { mutableStateOf<Int?>(null) }
    val currentSetIndex = focusedSetIndexOverride ?: (activeFlatSet?.setIndex ?: 0)

    val currentActiveFlatSet = if (currentExercise != null) {
        flatSets.firstOrNull { it.exercise.id == currentExercise.id && it.setIndex == currentSetIndex } ?: activeFlatSet
    } else {
        activeFlatSet
    }

    // Resolve the priority weight and its label source
    val resolvedWeightAndSource = remember(currentActiveFlatSet?.exercise?.id, currentActiveFlatSet?.setIndex, allLoggedSets) {
        currentActiveFlatSet?.let { flatSet ->
            // 1. Check active session value (entered weight on the set)
            if (flatSet.set.weight > 0f) {
                return@remember flatSet.set.weight to "Entered"
            }
            
            // 2. Routine prescription (target weight defined in the builder)
            if (flatSet.set.targetWeight != null && flatSet.set.targetWeight > 0f) {
                return@remember flatSet.set.targetWeight to "From routine"
            }
            
            // 3. Previous session weight (most recent completed set for this exercise ID)
            val mostRecentCompletedSet = allLoggedSets.filter { it.exerciseId == flatSet.exercise.id && it.isCompleted }.maxByOrNull { it.createdAt }
            if (mostRecentCompletedSet != null && mostRecentCompletedSet.weight > 0f) {
                return@remember mostRecentCompletedSet.weight to "Last session: ${com.example.core.util.UnitConverter.formatWeight(mostRecentCompletedSet.weight.toDouble(), isMetric)}"
            }
            
            // 4. Zero (only if no prescription or history exists)
            return@remember 0f to "Tap to type"
        } ?: (0f to "Tap to type")
    }

    // Active Input States bind directly to the currently selected (active or overridden) set
    var activeWeight by remember(resolvedWeightAndSource) {
        mutableStateOf(resolvedWeightAndSource.first)
    }
    var activeReps by remember(currentActiveFlatSet?.exercise?.id, currentActiveFlatSet?.setIndex, currentActiveFlatSet?.set?.reps) {
        mutableStateOf(
            currentActiveFlatSet?.let {
                if (it.set.reps > 0) it.set.reps
                else (it.set.targetRepsMin ?: 8)
            } ?: 8
        )
    }
    var activeRpe by remember(currentActiveFlatSet?.exercise?.id, currentActiveFlatSet?.setIndex, currentActiveFlatSet?.set?.rpe) {
        mutableStateOf<Int?>(currentActiveFlatSet?.set?.rpe)
    }

    val completedSetsCount = flatSets.count { it.set.isCompleted }
    val totalSetsCount = flatSets.size

    val recSpecs = remember(currentActiveFlatSet?.exercise?.id, allLoggedSets) {
        currentActiveFlatSet?.let { flatSet ->
            val defaultReps = setsList.firstOrNull()?.targetRepsMin ?: 8
            val defaultWeight = setsList.firstOrNull()?.targetWeight ?: 40f
            ExerciseIntelligence.getRecommendation(flatSet.exercise.id, allLoggedSets, defaultReps, defaultWeight)
        } ?: com.example.ui.viewmodel.TrainingRecommendation(40f, 8, "Baseline", "Medium")
    }
    val exProfile = remember(currentActiveFlatSet?.exercise?.id, allLoggedSets, isMetric) {
        currentActiveFlatSet?.let { flatSet ->
            ExerciseIntelligence.getProfile(flatSet.exercise.id, allLoggedSets, isMetric)
        }
    }

    // Live timer task
    LaunchedEffect(activeWorkout.startTime) {
        while (true) {
            val millis = System.currentTimeMillis() - activeWorkout.startTime
            val seconds = millis / 1000
            val hrs = seconds / 3600
            val mins = (seconds % 3600) / 60
            val secs = seconds % 60
            elapsedTime = if (hrs > 0) {
                String.format("%02d:%02d:%02d", hrs, mins, secs)
            } else {
                String.format("%02d:%02d", mins, secs)
            }
            delay(1000)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ZONE 1: Premium Status Header
            WorkoutStatusHeader(
                workoutName = activeWorkout.templateName,
                elapsedTime = elapsedTime,
                completedSets = completedSetsCount,
                totalSets = totalSetsCount,
                onFinishClick = { showFinishWorkoutDialog = true },
                onCancelClick = { showCancelConfirmDialog = true },
                onAddExerciseClick = { showAddExerciseDialog = true },
                onRenameWorkout = { viewModel.renameActiveWorkout(it) }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Pending Superset Suggestion Banner
                val pendingSuperset = activeWorkout.pendingSupersetSuggestion
                if (pendingSuperset != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("superset_suggestion_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MergeType,
                                        contentDescription = "Superset",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Possible Superset Detected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = "You've been alternating sets between ${pendingSuperset.exerciseNameA} and ${pendingSuperset.exerciseNameB}. Group them as a superset?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.confirmCasualSuperset(pendingSuperset.exerciseIdA, pendingSuperset.exerciseIdB)
                                        },
                                        modifier = Modifier.weight(1f).testTag("confirm_superset_button")
                                    ) {
                                        Text("Group as Superset")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.dismissCasualSuperset(pendingSuperset.exerciseIdA, pendingSuperset.exerciseIdB)
                                        },
                                        modifier = Modifier.weight(1f).testTag("dismiss_superset_button")
                                    ) {
                                        Text("Not a Superset")
                                    }
                                }
                            }
                        }
                    }
                }

                // Empty Exercises State for casual mode / empty workout
                if (activeWorkout.exercises.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("casual_empty_exercises_card")
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(28.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = "Add Exercise",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "No Exercises Logged Yet",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "What did you do today? Tap below to select an exercise and start logging your sets.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { showAddExerciseDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("empty_state_add_exercise_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Exercise", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }

                // If rest timer is active -> Rest State Panel Dominates
                if (restTimeRemaining != null && activeFlatSet != null) {
                    item {
                        val nextExName = activeFlatSet.exercise.name
                        val nextSetNum = activeFlatSet.setIndex + 1
                        
                        val nextIsSuperset = activeWorkout.exerciseMetadata[activeFlatSet.exercise.id]?.supersetGroupId != null
                        val nextGroupId = activeWorkout.exerciseMetadata[activeFlatSet.exercise.id]?.supersetGroupId
                        val nextIdentityLabel = if (nextIsSuperset) {
                            val distinctGroups = activeWorkout.exercises.mapNotNull { activeWorkout.exerciseMetadata[it.id]?.supersetGroupId }.distinct()
                            val groupIndex = distinctGroups.indexOf(nextGroupId)
                            val groupLetter = if (groupIndex >= 0) ('A' + groupIndex).toString() else "A"
                            val groupExercises = activeWorkout.exercises.filter { activeWorkout.exerciseMetadata[it.id]?.supersetGroupId == nextGroupId }
                            val exerciseIndexInGroup = groupExercises.indexOf(activeFlatSet.exercise) + 1
                            "SUPERSET $groupLetter • EXERCISE $exerciseIndexInGroup OF ${groupExercises.size}"
                        } else {
                            "STANDARD EXERCISE"
                        }

                        val nextWeightVal = if (activeFlatSet.set.weight > 0f) {
                            activeFlatSet.set.weight
                        } else if (activeFlatSet.set.targetWeight != null && activeFlatSet.set.targetWeight > 0f) {
                            activeFlatSet.set.targetWeight
                        } else {
                            val mostRecentCompletedSet = allLoggedSets.filter { it.exerciseId == activeFlatSet.exercise.id && it.isCompleted }.maxByOrNull { it.createdAt }
                            if (mostRecentCompletedSet != null && mostRecentCompletedSet.weight > 0f) {
                                mostRecentCompletedSet.weight
                            } else {
                                0f
                            }
                        }

                        val nextRepsVal = if (activeFlatSet.set.reps > 0) {
                            activeFlatSet.set.reps
                        } else if (activeFlatSet.set.targetRepsMin != null && activeFlatSet.set.targetRepsMin > 0) {
                            activeFlatSet.set.targetRepsMin
                        } else {
                            8
                        }

                        val nextPrescription = if (nextWeightVal > 0f) {
                            "${com.example.core.util.UnitConverter.formatWeight(nextWeightVal.toDouble(), isMetric)} × $nextRepsVal reps"
                        } else {
                            "$nextRepsVal reps"
                        }

                        RestStatePanel(
                            restTimeRemaining = restTimeRemaining ?: 0,
                            totalRestDuration = restTimerDuration,
                            isPaused = isRestTimerPaused,
                            nextExerciseName = nextExName,
                            nextSetNumber = nextSetNum,
                            nextTargetPrescription = nextPrescription,
                            onAddSecs = { viewModel.addRestTime(it) },
                            onReduceSecs = { viewModel.reduceRestTime(it) },
                            onSkip = { viewModel.skipRestTimer() },
                            onPauseToggle = {
                                if (isRestTimerPaused) viewModel.resumeRestTimer() else viewModel.pauseRestTimer()
                            },
                            nextIsSuperset = nextIsSuperset,
                            nextIdentityLabel = nextIdentityLabel
                        )
                    }
                }

                // If no active sets left -> Workout complete card
                if (activeFlatSet == null && activeWorkout.exercises.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("workout_complete_card")
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = SlateElevatedSurface
                            ),
                            border = BorderStroke(2.dp, HumanPrimaryAccent)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(28.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Workout Complete",
                                    tint = SlateSuccess,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = "ALL SETS COMPLETED!",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                    color = SlateSuccess
                                )
                                Text(
                                    text = "Phenomenal effort today. Ready to log your session?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { showFinishWorkoutDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("finish_workout_button_card"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HumanPrimaryAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("FINISH WORKOUT", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                                }
                            }
                        }
                    }
                } else if (restTimeRemaining == null) {
                    // Main execution panels (when not actively resting)
                    val activeEx = currentActiveFlatSet!!.exercise

                    item {
                        val groupId = activeWorkout.exerciseMetadata[activeEx.id]?.supersetGroupId
                        val isSuperset = !groupId.isNullOrEmpty()
                        
                        // Calculate identityLabel for Supersets
                        val identityLabel = if (isSuperset) {
                            val distinctGroups = activeWorkout.exercises.mapNotNull { activeWorkout.exerciseMetadata[it.id]?.supersetGroupId }.distinct()
                            val groupIndex = distinctGroups.indexOf(groupId)
                            val groupLetter = if (groupIndex >= 0) ('A' + groupIndex).toString() else "A"
                            val groupExercises = activeWorkout.exercises.filter { activeWorkout.exerciseMetadata[it.id]?.supersetGroupId == groupId }
                            val exerciseIndexInGroup = groupExercises.indexOf(activeEx) + 1
                            "SUPERSET $groupLetter • EXERCISE $exerciseIndexInGroup OF ${groupExercises.size}"
                        } else {
                            "STANDARD EXERCISE"
                        }

                        // Calculate previous history completed days ago
                        val mostRecentSet = allLoggedSets.filter { it.exerciseId == activeEx.id && it.isCompleted }.maxByOrNull { it.createdAt }
                        val daysAgoText = if (mostRecentSet != null) {
                            val diffMillis = System.currentTimeMillis() - mostRecentSet.createdAt
                            val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
                            if (days <= 0) "Today" else if (days == 1) "1 day ago" else "$days days ago"
                        } else {
                            null
                        }

                        var isSubmittingSet by remember(currentActiveFlatSet.exercise.id, currentActiveFlatSet.setIndex) { mutableStateOf(false) }

                        val hapticFeedback = LocalHapticFeedback.current
                        LaunchedEffect(activeEx.id) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        AnimatedContent(
                            targetState = activeEx.id,
                            transitionSpec = {
                                (slideInHorizontally(animationSpec = tween(220)) { width -> width / 12 } + fadeIn(animationSpec = tween(220)))
                                    .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { width -> -width / 12 } + fadeOut(animationSpec = tween(220)))
                            },
                            label = "ExerciseTransition"
                        ) { targetExerciseId ->
                            ActiveExerciseCard(
                                exerciseName = activeEx.name,
                                category = activeEx.category,
                                identityLabel = identityLabel,
                                isSuperset = isSuperset,
                                currentExerciseIndex = activeWorkout.exercises.indexOf(activeEx) + 1,
                                totalExercisesCount = activeWorkout.exercises.size,
                                currentSetNumber = currentSetIndex + 1,
                                totalSetsCount = setsList.size,
                                completedSetsCount = completedSetsCount,
                                totalSetsInWorkout = totalSetsCount,
                                sets = setsList,
                                currentSetIndex = currentSetIndex,
                                onSetClick = { index ->
                                    focusedSetIndexOverride = index
                                },
                                weight = activeWeight,
                                onWeightChange = { newWeight ->
                                    activeWeight = newWeight
                                    viewModel.updateSet(
                                        exerciseId = activeEx.id,
                                        setIndex = currentSetIndex,
                                        reps = activeReps,
                                        weight = newWeight,
                                        isCompleted = currentActiveFlatSet.set.isCompleted,
                                        rpe = activeRpe,
                                        actualDuration = currentActiveFlatSet.set.actualDuration,
                                        actualDistance = currentActiveFlatSet.set.actualDistance,
                                        setType = currentActiveFlatSet.set.setType,
                                        targetRepsMin = currentActiveFlatSet.set.targetRepsMin,
                                        targetRepsMax = currentActiveFlatSet.set.targetRepsMax,
                                        targetWeight = currentActiveFlatSet.set.targetWeight,
                                        targetRpe = currentActiveFlatSet.set.targetRpe,
                                        targetDuration = currentActiveFlatSet.set.targetDuration,
                                        targetDistance = currentActiveFlatSet.set.targetDistance,
                                        tempo = currentActiveFlatSet.set.tempo,
                                        notes = currentActiveFlatSet.set.notes
                                    )
                                },
                                reps = activeReps,
                                onRepsChange = { newReps ->
                                    activeReps = newReps
                                    viewModel.updateSet(
                                        exerciseId = activeEx.id,
                                        setIndex = currentSetIndex,
                                        reps = newReps,
                                        weight = activeWeight,
                                        isCompleted = currentActiveFlatSet.set.isCompleted,
                                        rpe = activeRpe,
                                        actualDuration = currentActiveFlatSet.set.actualDuration,
                                        actualDistance = currentActiveFlatSet.set.actualDistance,
                                        setType = currentActiveFlatSet.set.setType,
                                        targetRepsMin = currentActiveFlatSet.set.targetRepsMin,
                                        targetRepsMax = currentActiveFlatSet.set.targetRepsMax,
                                        targetWeight = currentActiveFlatSet.set.targetWeight,
                                        targetRpe = currentActiveFlatSet.set.targetRpe,
                                        targetDuration = currentActiveFlatSet.set.targetDuration,
                                        targetDistance = currentActiveFlatSet.set.targetDistance,
                                        tempo = currentActiveFlatSet.set.tempo,
                                        notes = currentActiveFlatSet.set.notes
                                    )
                                },
                                rpe = activeRpe,
                                onRpeChange = { newRpe ->
                                    activeRpe = newRpe
                                    viewModel.updateSet(
                                        exerciseId = activeEx.id,
                                        setIndex = currentSetIndex,
                                        reps = activeReps,
                                        weight = activeWeight,
                                        isCompleted = currentActiveFlatSet.set.isCompleted,
                                        rpe = newRpe,
                                        actualDuration = currentActiveFlatSet.set.actualDuration,
                                        actualDistance = currentActiveFlatSet.set.actualDistance,
                                        setType = currentActiveFlatSet.set.setType,
                                        targetRepsMin = currentActiveFlatSet.set.targetRepsMin,
                                        targetRepsMax = currentActiveFlatSet.set.targetRepsMax,
                                        targetWeight = currentActiveFlatSet.set.targetWeight,
                                        targetRpe = currentActiveFlatSet.set.targetRpe,
                                        targetDuration = currentActiveFlatSet.set.targetDuration,
                                        targetDistance = currentActiveFlatSet.set.targetDistance,
                                        tempo = currentActiveFlatSet.set.tempo,
                                        notes = currentActiveFlatSet.set.notes
                                    )
                                },
                                prevSummary = exProfile?.bestSet ?: "No prior history",
                                daysAgoText = daysAgoText,
                                coachingCues = currentActiveFlatSet.set.notes,
                                onCuesClick = { showCuesDialog = true },
                                completeSetEnabled = !isSubmittingSet,
                                onCompleteSetClick = {
                                    if (!isSubmittingSet) {
                                        isSubmittingSet = true
                                        viewModel.updateSet(
                                            exerciseId = activeEx.id,
                                            setIndex = currentSetIndex,
                                            reps = activeReps,
                                            weight = activeWeight,
                                            isCompleted = true,
                                            rpe = activeRpe,
                                            actualDuration = currentActiveFlatSet.set.actualDuration,
                                            actualDistance = currentActiveFlatSet.set.actualDistance,
                                            setType = currentActiveFlatSet.set.setType,
                                            targetRepsMin = currentActiveFlatSet.set.targetRepsMin,
                                            targetRepsMax = currentActiveFlatSet.set.targetRepsMax,
                                            targetWeight = currentActiveFlatSet.set.targetWeight,
                                            targetRpe = currentActiveFlatSet.set.targetRpe,
                                            targetDuration = currentActiveFlatSet.set.targetDuration,
                                            targetDistance = currentActiveFlatSet.set.targetDistance,
                                            tempo = currentActiveFlatSet.set.tempo,
                                            notes = currentActiveFlatSet.set.notes
                                        )
                                        focusedSetIndexOverride = null
                                    }
                                },
                                onAddSetClick = {
                                    viewModel.addSetToExercise(activeEx.id)
                                },
                                onRemoveSetClick = {
                                    if (setsList.isNotEmpty()) {
                                        viewModel.removeSetFromExercise(activeEx.id, setsList.size - 1)
                                    }
                                },
                                totalSetsInExercise = setsList.size,
                                onRemoveExerciseClick = {
                                    viewModel.removeExerciseFromActiveWorkout(activeEx.id)
                                },
                                weightSource = resolvedWeightAndSource.second
                            )
                        }
                    }

                    // If Superset Round-Robin is executing -> Round details
                    val groupId = activeWorkout.exerciseMetadata[activeEx.id]?.supersetGroupId
                    if (!groupId.isNullOrEmpty()) {
                        item {
                            val groupExercises = activeWorkout.exercises.filter { activeWorkout.exerciseMetadata[it.id]?.supersetGroupId == groupId }
                            val currentInGroupIdx = groupExercises.indexOf(activeEx)
                            val exercisesInGroup = groupExercises.map { ex ->
                                val sets = activeWorkout.sets[ex.id] ?: emptyList()
                                val isCompleted = sets.getOrNull(currentSetIndex)?.isCompleted ?: false
                                ex.name to isCompleted
                            }

                            SupersetProgressPanel(
                                groupLabel = "SUPERSET BLOCK",
                                groupType = "Round Robin",
                                currentRound = currentSetIndex + 1,
                                totalRounds = setsList.size,
                                exercisesInGroup = exercisesInGroup,
                                activeExerciseIndexInGroup = currentInGroupIdx
                            )
                        }
                    }

                    // ZONE 4: Next Action Preview (Preview of what is next in queue)
                    val upcomingFlatSets = if (activeFlatSet != null) flatSets.dropWhile { it != activeFlatSet }.drop(1) else emptyList()
                    val nextStep = upcomingFlatSets.firstOrNull()
                    if (nextStep != null) {
                        item {
                            val nextTargetPrescription = "${com.example.core.util.UnitConverter.formatWeight((nextStep.set.targetWeight ?: nextStep.set.weight).toDouble(), isMetric)} × ${nextStep.set.targetRepsMin ?: nextStep.set.reps}"
                            NextStepPreview(
                                nextExerciseName = nextStep.exercise.name,
                                nextSetNumber = nextStep.setIndex + 1,
                                nextPrescription = nextTargetPrescription
                            )
                        }
                    }
                }

                // Collapsible DONE Accordion
                item {
                    val completedGroups = flatSets.filter { it.set.isCompleted }.groupBy { it.exercise.id }
                    val completedExercises = activeWorkout.exercises.mapNotNull { exercise ->
                        val sets = completedGroups[exercise.id]
                        if (sets != null) {
                            exercise to sets.map { it.set }
                        } else {
                            null
                        }
                    }
                    val totalCompletedCount = flatSets.count { it.set.isCompleted }

                    val summaryText = if (totalCompletedCount > 0) {
                        completedExercises.joinToString(", ") { (ex, sets) ->
                            "${ex.name} · ${sets.size} set${if (sets.size > 1) "s" else ""}"
                        }
                    } else {
                        "No completed sets yet"
                    }

                    WorkoutAccordionSection(
                        title = "DONE · $totalCompletedCount set${if (totalCompletedCount != 1) "s" else ""} completed",
                        summary = if (doneExpanded) "" else summaryText,
                        expanded = doneExpanded,
                        onExpandedChange = { expanded ->
                            doneExpanded = expanded
                            if (expanded) nextExpanded = false
                        },
                        modifier = Modifier.testTag("done_accordion")
                    ) {
                        if (completedExercises.isEmpty()) {
                            Text(
                                text = "Nothing completed yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            completedExercises.forEach { (exercise, sets) ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = exercise.name.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )
                                    sets.forEachIndexed { sIdx, set ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                val rpeText = if (set.rpe != null) " · RPE ${set.rpe}" else ""
                                                Text(
                                                    text = "✓ Set ${sIdx + 1}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${com.example.core.util.UnitConverter.formatWeight(set.weight.toDouble(), isMetric)} × ${set.reps}$rpeText",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            TextButton(
                                                onClick = {
                                                    viewModel.updateSet(
                                                        exerciseId = exercise.id,
                                                        setIndex = sIdx,
                                                        reps = set.reps,
                                                        weight = set.weight,
                                                        isCompleted = false,
                                                        rpe = set.rpe,
                                                        setType = set.setType,
                                                        notes = set.notes
                                                    )
                                                },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Text("Undo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Collapsible NEXT Accordion
                item {
                    val upcomingFlatSets = if (activeFlatSet != null) flatSets.dropWhile { it != activeFlatSet }.drop(1) else emptyList()
                    val totalRemainingCount = upcomingFlatSets.size

                    val upcomingGroups = upcomingFlatSets.groupBy { it.exercise.id }
                    val upcomingExercisesList = mutableListOf<UpcomingItem>()
                    val processedExerciseIds = mutableSetOf<String>()
                    val processedGroupIds = mutableSetOf<String>()

                    for (exercise in activeWorkout.exercises) {
                        if (exercise.id in processedExerciseIds) continue
                        val setsForExercise = upcomingGroups[exercise.id] ?: emptyList()
                        if (setsForExercise.isEmpty()) continue

                        val groupId = activeWorkout.exerciseMetadata[exercise.id]?.supersetGroupId
                        if (groupId.isNullOrEmpty()) {
                            upcomingExercisesList.add(UpcomingItem.SingleExercise(exercise, setsForExercise.map { it.set }))
                            processedExerciseIds.add(exercise.id)
                        } else {
                            if (groupId !in processedGroupIds) {
                                processedGroupIds.add(groupId)
                                val groupExercises = activeWorkout.exercises.filter { activeWorkout.exerciseMetadata[it.id]?.supersetGroupId == groupId }
                                val groupItems = groupExercises.mapNotNull { ex ->
                                    val sets = upcomingGroups[ex.id] ?: emptyList()
                                    if (sets.isNotEmpty()) ex to sets.map { it.set } else null
                                }
                                if (groupItems.isNotEmpty()) {
                                    upcomingExercisesList.add(UpcomingItem.SupersetGroup(groupId, groupItems))
                                    groupExercises.forEach { processedExerciseIds.add(it.id) }
                                }
                            }
                        }
                    }

                    val summaryText = if (upcomingExercisesList.isNotEmpty()) {
                        upcomingExercisesList.joinToString(", ") { item ->
                            when (item) {
                                is UpcomingItem.SingleExercise -> {
                                    "${item.exercise.name} · ${item.sets.size} set${if (item.sets.size > 1) "s" else ""}"
                                }
                                is UpcomingItem.SupersetGroup -> {
                                    val names = item.exercises.map { it.first.name }.joinToString(" + ")
                                    val maxSets = item.exercises.maxOf { it.second.size }
                                    "Superset [$names] · $maxSets round${if (maxSets > 1) "s" else ""}"
                                }
                            }
                        }
                    } else {
                        "Workout finish"
                    }

                    WorkoutAccordionSection(
                        title = "NEXT · $totalRemainingCount set${if (totalRemainingCount != 1) "s" else ""} remaining",
                        summary = if (nextExpanded) "" else summaryText,
                        expanded = nextExpanded,
                        onExpandedChange = { expanded ->
                            nextExpanded = expanded
                            if (expanded) doneExpanded = false
                        },
                        modifier = Modifier.testTag("next_accordion")
                    ) {
                        if (upcomingExercisesList.isEmpty()) {
                            Text(
                                text = "Workout finish",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                upcomingExercisesList.forEach { item ->
                                    when (item) {
                                        is UpcomingItem.SingleExercise -> {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = item.exercise.name.uppercase(),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )

                                                val setsCount = item.sets.size
                                                val firstSet = item.sets.first()
                                                val repsMin = firstSet.targetRepsMin ?: 5
                                                val repsMax = firstSet.targetRepsMax ?: 8
                                                val repsStr = if (repsMin == repsMax) "$repsMin reps" else "$repsMin–$repsMax reps"
                                                val restSec = activeWorkout.exerciseMetadata[item.exercise.id]?.restSeconds ?: 90
                                                val restStr = if (restSec % 60 == 0) "${restSec / 60} min rest" else "$restSec sec rest"

                                                Text(
                                                    text = "$setsCount set${if (setsCount > 1) "s" else ""} · $repsStr · $restStr",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        is UpcomingItem.SupersetGroup -> {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .border(
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .padding(12.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Layers,
                                                        contentDescription = "Superset Group",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "SUPERSET GROUP",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                item.exercises.forEach { (ex, sets) ->
                                                    val setsCount = sets.size
                                                    val firstSet = sets.firstOrNull()
                                                    val repsMin = firstSet?.targetRepsMin ?: 5
                                                    val repsMax = firstSet?.targetRepsMax ?: 8
                                                    val repsStr = if (repsMin == repsMax) "$repsMin reps" else "$repsMin–$repsMax reps"

                                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                                        Text(
                                                            text = ex.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "$setsCount set${if (setsCount > 1) "s" else ""} remaining · $repsStr",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Exercise Dialog
        if (showAddExerciseDialog) {
            Dialog(onDismissRequest = { showAddExerciseDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .height(500.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Add Exercise",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search exercises...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        val categories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Abs")
                        ScrollableTabRow(
                            selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                            edgePadding = 0.dp,
                            containerColor = Color.Transparent,
                            divider = {}
                        ) {
                            categories.forEach { cat ->
                                Tab(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    text = { Text(cat, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        val filteredExercises = exercisesDb.filter { ex ->
                            val matchesSearch = ex.name.contains(searchQuery, ignoreCase = true)
                            val matchesCat = selectedCategory == "All" || ex.category == selectedCategory
                            matchesSearch && matchesCat
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (filteredExercises.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No exercises found.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    itemsIndexed(filteredExercises) { _, exercise ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    viewModel.addExerciseToActiveWorkout(exercise)
                                                    showAddExerciseDialog = false
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(exercise.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text(
                                                    exercise.category,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Add",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddExerciseDialog = false }) {
                                Text("Close", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Cancel Confirm Dialog
        if (showCancelConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showCancelConfirmDialog = false },
                title = { Text("Cancel Workout?", fontWeight = FontWeight.Black) },
                text = { Text("Are you sure you want to cancel? This will delete all sets logged in this active session.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.cancelActiveWorkout()
                            showCancelConfirmDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Yes, Cancel", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelConfirmDialog = false }) {
                        Text("Resume Workout", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Premium Finish Workout Summary Sheet
        if (showFinishWorkoutDialog) {
            FinishWorkoutSheet(
                workoutName = activeWorkout.templateName,
                durationText = elapsedTime,
                completedSets = completedSetsCount,
                totalSets = totalSetsCount,
                onConfirmFinish = {
                    if (!isCompletingWorkout) {
                        viewModel.finishActiveWorkout()
                        showFinishWorkoutDialog = false
                    }
                },
                onDismiss = { showFinishWorkoutDialog = false }
            )
        }

        // Exercise Coaching Cues Sheet
        if (showCuesDialog && currentActiveFlatSet != null) {
            val ex = currentActiveFlatSet.exercise
            ExerciseNotesSheet(
                exerciseName = ex.name,
                notes = currentActiveFlatSet.set.notes ?: "",
                onNotesSave = { updatedNotes ->
                    viewModel.updateSet(
                        exerciseId = ex.id,
                        setIndex = currentSetIndex,
                        reps = activeReps,
                        weight = activeWeight,
                        isCompleted = currentActiveFlatSet.set.isCompleted,
                        rpe = activeRpe,
                        actualDuration = currentActiveFlatSet.set.actualDuration,
                        actualDistance = currentActiveFlatSet.set.actualDistance,
                        setType = currentActiveFlatSet.set.setType,
                        targetRepsMin = currentActiveFlatSet.set.targetRepsMin,
                        targetRepsMax = currentActiveFlatSet.set.targetRepsMax,
                        targetWeight = currentActiveFlatSet.set.targetWeight,
                        targetRpe = currentActiveFlatSet.set.targetRpe,
                        targetDuration = currentActiveFlatSet.set.targetDuration,
                        targetDistance = currentActiveFlatSet.set.targetDistance,
                        tempo = currentActiveFlatSet.set.tempo,
                        notes = updatedNotes
                    )
                },
                onDismiss = { showCuesDialog = false }
            )
        }
    }
}

@Composable
fun WorkoutAccordionSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (summary.isNotEmpty()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}

data class FlatSet(
    val exercise: Exercise,
    val setIndex: Int,
    val set: ActiveSet
)

sealed class UpcomingItem {
    data class SingleExercise(val exercise: Exercise, val sets: List<ActiveSet>) : UpcomingItem()
    data class SupersetGroup(val groupId: String, val exercises: List<Pair<Exercise, List<ActiveSet>>>) : UpcomingItem()
}
