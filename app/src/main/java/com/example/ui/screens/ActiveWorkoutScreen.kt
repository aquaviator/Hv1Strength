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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.data.Exercise
import com.example.ui.viewmodel.ActiveSet
import com.example.ui.viewmodel.StrengthViewModel
import com.example.ui.viewmodel.ExerciseIntelligence
import com.example.ui.viewmodel.ExerciseProfile
import com.example.ui.viewmodel.TrainingRecommendation
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: StrengthViewModel,
    onNavigateBack: () -> Unit
) {
    val activeWorkoutState by viewModel.activeWorkoutState.collectAsState()
    val exercisesDb by viewModel.exercises.collectAsState()
    val allLoggedSets by viewModel.allLoggedSets.collectAsState()

    val activeWorkout = activeWorkoutState ?: return

    var elapsedTime by remember { mutableStateOf("00:00") }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    val restTimeRemaining by viewModel.restTimeRemaining.collectAsState()
    val isRestTimerPaused by viewModel.isRestTimerPaused.collectAsState()

    // Live timer
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                activeWorkout.templateName,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = "Timer",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    elapsedTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.Default.KeyboardArrowDown, 
                                contentDescription = "Minimize",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { showCancelConfirmDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                viewModel.finishActiveWorkout()
                                onNavigateBack()
                            },
                            modifier = Modifier.testTag("finish_workout_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Finish", fontWeight = FontWeight.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            // Active Set tracker to handle expanding / collapsing
            var manualActiveIndexMap = remember { mutableStateMapOf<String, Int>() }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (activeWorkout.exercises.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.FitnessCenter,
                                contentDescription = "No exercises added",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                "Workout is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Add an exercise to start logging your sets.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    itemsIndexed(activeWorkout.exercises) { _, exercise ->
                        val setsList = activeWorkout.sets[exercise.id] ?: emptyList()

                        // Calculate dynamic exercise intelligence
                        val exerciseProfile = remember(exercise.id, allLoggedSets) {
                            ExerciseIntelligence.getProfile(exercise.id, allLoggedSets)
                        }
                        val recommendation = remember(exercise.id, allLoggedSets) {
                            val defaultReps = setsList.firstOrNull()?.targetRepsMin ?: 8
                            val defaultWeight = setsList.firstOrNull()?.targetWeight ?: 40f
                            ExerciseIntelligence.getRecommendation(exercise.id, allLoggedSets, defaultReps, defaultWeight)
                        }

                        // Determine active editable set
                        val activeSetIndex = manualActiveIndexMap[exercise.id] ?: setsList.indexOfFirst { !it.isCompleted }.let { if (it == -1) 0 else it }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active_exercise_card_${exercise.id}"),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Exercise Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            exercise.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            exercise.category,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeExerciseFromActiveWorkout(exercise.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove exercise",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                // Exercise Intelligence / Coaching Context Tab (Progressive Disclosure)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Psychology,
                                            contentDescription = "Intelligence",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            "Human Recommendation",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Text(
                                        text = "${recommendation.startWeight.toString().removeSuffix(".0")} kg × ${recommendation.targetReps} reps",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = recommendation.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (exerciseProfile != null) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("PREVIOUS BEST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                                Text(exerciseProfile.bestSet, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            }
                                            Column {
                                                Text("EST. 1RM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                                Text("${String.format("%.1f", exerciseProfile.estimated1RM).removeSuffix(".0")} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            }
                                            Column {
                                                Text("TREND", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                                Text(exerciseProfile.progressTrend, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }

                                // Interactive Guided Sets Section
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    setsList.forEachIndexed { index, set ->
                                        val isEditable = index == activeSetIndex

                                        if (isEditable) {
                                            // Large, Interactive Guided Set Editor Block
                                            ActiveGuidedSetEditorBlock(
                                                setNumber = index + 1,
                                                set = set,
                                                recommendationWeight = recommendation.startWeight,
                                                recommendationReps = recommendation.targetReps,
                                                onComplete = { actualWeight, actualReps, actualRpe ->
                                                    viewModel.updateSet(
                                                        exerciseId = exercise.id,
                                                        setIndex = index,
                                                        reps = actualReps,
                                                        weight = actualWeight,
                                                        isCompleted = true,
                                                        rpe = actualRpe,
                                                        setType = set.setType,
                                                        notes = set.notes
                                                    )
                                                    // Advance to next uncompleted set automatically
                                                    val nextIndex = setsList.indexOfFirst { !it.isCompleted && it.setNumber != set.setNumber }
                                                    if (nextIndex != -1) {
                                                        manualActiveIndexMap[exercise.id] = nextIndex
                                                    }
                                                },
                                                onDelete = {
                                                    viewModel.removeSetFromExercise(exercise.id, index)
                                                }
                                            )
                                        } else {
                                            // Sleek collapsed card
                                            CollapsedSetCardRow(
                                                setNumber = index + 1,
                                                set = set,
                                                onClick = {
                                                    manualActiveIndexMap[exercise.id] = index
                                                },
                                                onToggleCompletion = {
                                                    viewModel.updateSet(
                                                        exerciseId = exercise.id,
                                                        setIndex = index,
                                                        reps = set.reps,
                                                        weight = set.weight,
                                                        isCompleted = !set.isCompleted,
                                                        rpe = set.rpe,
                                                        setType = set.setType,
                                                        notes = set.notes
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                // Stepper to adjust target sets count easily
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (setsList.isNotEmpty()) {
                                                    viewModel.removeSetFromExercise(exercise.id, setsList.size - 1)
                                                }
                                            },
                                            enabled = setsList.isNotEmpty(),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Reduce Sets", tint = if (setsList.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
                                        }
                                        Text(
                                            text = "${setsList.size} Sets Total",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(
                                            onClick = { viewModel.addSetToExercise(exercise.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase Sets", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    TextButton(
                                        onClick = { viewModel.addSetToExercise(exercise.id) },
                                        modifier = Modifier.testTag("add_set_button_${exercise.id}")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Set", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Set", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Exercise Trigger
                item {
                    Button(
                        onClick = { showAddExerciseDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("add_exercise_to_active_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Exercise")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Exercise", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(64.dp))
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

        // Floating Rest Timer HUD Overlay
        if (restTimeRemaining != null) {
            val remaining = restTimeRemaining!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("floating_rest_timer"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Rest",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = if (remaining > 0) "Rest Timer: $remaining s" else "Rest Finished!",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val durations = listOf(30, 45, 60, 90, 120, 180)
                                    Text(
                                        "Add: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                    durations.forEach { dur ->
                                        Text(
                                            text = "+${dur}s",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable {
                                                    val currentRemaining = restTimeRemaining ?: 0
                                                    viewModel.startRestTimer(currentRemaining + dur)
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isRestTimerPaused) viewModel.resumeRestTimer() else viewModel.pauseRestTimer()
                                }
                            ) {
                                Icon(
                                    imageVector = if (isRestTimerPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isRestTimerPaused) "Resume" else "Pause",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            IconButton(onClick = { viewModel.resetRestTimer() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            TextButton(
                                onClick = { viewModel.skipRestTimer() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Skip", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Redesigned Collapsed Set row showing completed sets cleanly and compactly
 */
@Composable
fun CollapsedSetCardRow(
    setNumber: Int,
    set: ActiveSet,
    onClick: () -> Unit,
    onToggleCompletion: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isChecked = set.isCompleted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleCompletion()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Toggle Complete",
                    tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = "Set $setNumber",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (isChecked) {
                    val rpeStr = if (set.rpe != null) " @ RPE ${set.rpe}" else ""
                    Text(
                        text = "${set.weight.toString().removeSuffix(".0")} kg × ${set.reps} reps$rpeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    val targetList = mutableListOf<String>()
                    if (set.targetWeight != null && set.targetWeight > 0f) targetList.add("${set.targetWeight} kg")
                    if (set.targetRepsMin != null) targetList.add("${set.targetRepsMin} reps")
                    val targetText = if (targetList.isNotEmpty()) "Target: ${targetList.joinToString(" × ")}" else "Tap to edit"
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit Set",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Premium Guided Set Editor Card featuring modern touch-steppers, quick plate chips,
 * segmented RPE pickers, and a massive Complete button. Keyboard-free operations.
 */
@Composable
fun ActiveGuidedSetEditorBlock(
    setNumber: Int,
    set: ActiveSet,
    recommendationWeight: Float,
    recommendationReps: Int,
    onComplete: (actualWeight: Float, actualReps: Int, actualRpe: Int?) -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    // Initialize local values to recommendation or current performance values
    var weightVal by remember(set.weight, recommendationWeight) {
        mutableStateOf(if (set.weight > 0f) set.weight else recommendationWeight)
    }
    var repsVal by remember(set.reps, recommendationReps) {
        mutableStateOf(if (set.reps > 0) set.reps else recommendationReps)
    }
    var rpeVal by remember(set.rpe) {
        mutableStateOf<Int?>(set.rpe ?: 8)
    }

    var manualWeightEdit by remember { mutableStateOf(false) }
    var manualWeightText by remember { mutableStateOf(weightVal.toString().removeSuffix(".0")) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE SET $setNumber",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete Set",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            // ACTUAL WEIGHT SELECTOR (Vertical layout, Touch-steppers, Quick Plate chips)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Actual Weight",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Big Touch Target Minus Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            weightVal = (weightVal - 2.5f).coerceAtLeast(0f)
                            manualWeightText = weightVal.toString().removeSuffix(".0")
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease weight", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                    }

                    // Display weight prominently
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                manualWeightText = weightVal.toString().removeSuffix(".0")
                                manualWeightEdit = !manualWeightEdit
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (manualWeightEdit) {
                            OutlinedTextField(
                                value = manualWeightText,
                                onValueChange = {
                                    manualWeightText = it
                                    val parsed = it.toFloatOrNull()
                                    if (parsed != null && parsed >= 0f) {
                                        weightVal = parsed
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.width(100.dp).height(56.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        manualWeightEdit = false
                                        focusManager.clearFocus()
                                    }
                                ),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${weightVal.toString().removeSuffix(".0")} kg",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Tap to type",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    // Big Touch Target Plus Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            weightVal = (weightVal + 2.5f)
                            manualWeightText = weightVal.toString().removeSuffix(".0")
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase weight", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                    }
                }

                // Quick Plate weight adjustment chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val plates = listOf(-10f, -5f, -2.5f, -1.25f, 1.25f, 2.5f, 5f, 10f)
                    plates.forEach { plate ->
                        val isPositive = plate > 0
                        val textLabel = if (isPositive) "+${plate.toString().removeSuffix(".0")}" else plate.toString().removeSuffix(".0")
                        val containerBg = if (isPositive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        val textColor = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                        Box(
                            modifier = Modifier
                                .background(containerBg, shape = RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    weightVal = (weightVal + plate).coerceAtLeast(0f)
                                    manualWeightText = weightVal.toString().removeSuffix(".0")
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = textLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = textColor
                            )
                        }
                    }
                }
            }

            // ACTUAL REPS SELECTOR (Large steppers & Preset targets)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Actual Reps",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Minus Reps Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            repsVal = (repsVal - 1).coerceAtLeast(0)
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease reps", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                    }

                    // Display Reps prominently
                    Text(
                        text = "$repsVal reps",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    // Plus Reps Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            repsVal = (repsVal + 1)
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase reps", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                    }
                }

                // Quick reps chips presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 8, 10, 12, 15).forEach { presetReps ->
                        val isSelected = repsVal == presetReps
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    repsVal = presetReps
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = presetReps.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ACTUAL RPE SEGMENTED PICKER
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Rating of Perceived Exertion (RPE)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val rpes = listOf(6, 7, 8, 9, 10)
                    rpes.forEach { rVal ->
                        val isSelected = rpeVal == rVal
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = if (isSelected) 0.dp else 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    rpeVal = if (isSelected) null else rVal
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = rVal.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Dynamic helper text for RPE description
                val rpeDescription = when (rpeVal) {
                    6 -> "6 - Speed was fast. 4 or more reps left in tank."
                    7 -> "7 - Moderately easy. 3 reps left in tank."
                    8 -> "8 - Solid effort. 2 reps left in tank."
                    9 -> "9 - High effort. Only 1 rep left in tank."
                    10 -> "10 - Maximum effort. Absolute failure achieved."
                    else -> "Select RPE to guide future recommendations."
                }
                Text(
                    text = rpeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // MASSIVE THUMB-FRIENDLY "COMPLETE SET" BUTTON (56dp target)
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onComplete(weightVal, repsVal, rpeVal)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("submit_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Complete Set", modifier = Modifier.size(20.dp))
                    Text(
                        "Complete Set",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
