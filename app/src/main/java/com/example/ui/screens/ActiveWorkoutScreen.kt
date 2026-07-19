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
import com.example.ui.viewmodel.ActiveWorkoutEvent
import com.example.ui.components.*
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
        // Saving transition state to prevent black screen!
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
    var showCompletionDialog by remember { mutableStateOf(false) }

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
                                if (!isCompletingWorkout) {
                                    viewModel.finishActiveWorkout()
                                }
                            },
                            enabled = !isCompletingWorkout,
                            modifier = Modifier.testTag("finish_workout_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isCompletingWorkout) {
                                Text("Saving workout…", fontWeight = FontWeight.Black)
                            } else {
                                Text("Finish", fontWeight = FontWeight.Black)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            // Independent accordion expansion state that survives recompositions
            var doneExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            var nextExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

            // Authorization: derived authoritative ordered workout structures (Superset round-robin interleaved)
            val flatSets = remember(activeWorkout) {
                val list = mutableListOf<FlatSet>()
                val processedGroups = mutableSetOf<String>()
                
                for (exercise in activeWorkout.exercises) {
                    val groupId = activeWorkout.exerciseMetadata[exercise.id]?.supersetGroupId
                    if (groupId.isNullOrEmpty()) {
                        // Normal sequential exercise
                        val setsList = activeWorkout.sets[exercise.id] ?: emptyList()
                        setsList.forEachIndexed { index, set ->
                            list.add(FlatSet(exercise, index, set))
                        }
                    } else {
                        // Part of a superset group
                        if (groupId !in processedGroups) {
                            processedGroups.add(groupId)
                            val groupExercises = activeWorkout.exercises.filter { activeWorkout.exerciseMetadata[it.id]?.supersetGroupId == groupId }
                            val maxSets = groupExercises.maxOfOrNull { (activeWorkout.sets[it.id] ?: emptyList()).size } ?: 0
                            for (setIdx in 0 until maxSets) {
                                for (groupEx in groupExercises) {
                                    val setsList = activeWorkout.sets[groupEx.id] ?: emptyList()
                                    if (setIdx in setsList.indices) {
                                        list.add(FlatSet(groupEx, setIdx, setsList[setIdx]))
                                    }
                                }
                            }
                        }
                    }
                }
                list
            }

            // The active set is the first valid incomplete set
            val activeFlatSet = flatSets.firstOrNull { !it.set.isCompleted }

            // Active set's input values must be preserved during accordion interactions
            var activeWeight by remember(activeFlatSet?.exercise?.id, activeFlatSet?.setIndex) {
                mutableStateOf(
                    activeFlatSet?.let {
                        if (it.set.weight > 0f) it.set.weight
                        else (it.set.targetWeight ?: 40f)
                    } ?: 40f
                )
            }
            var activeReps by remember(activeFlatSet?.exercise?.id, activeFlatSet?.setIndex) {
                mutableStateOf(
                    activeFlatSet?.let {
                        if (it.set.reps > 0) it.set.reps
                        else (it.set.targetRepsMin ?: 8)
                    } ?: 8
                )
            }
            var activeRpe by remember(activeFlatSet?.exercise?.id, activeFlatSet?.setIndex) {
                mutableStateOf<Int?>(activeFlatSet?.set?.rpe ?: 8)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Workout Header with Minimal Progress Info
                item {
                    val activeIndex = if (activeFlatSet != null) {
                        activeWorkout.exercises.indexOf(activeFlatSet.exercise) + 1
                    } else {
                        activeWorkout.exercises.size
                    }
                    WorkoutHeader(
                        templateName = activeWorkout.templateName,
                        startTime = activeWorkout.startTime,
                        exercisesCount = activeWorkout.exercises.size,
                        activeExerciseIndex = activeIndex,
                        elapsedTime = elapsedTime,
                        onRenameActiveWorkout = { viewModel.renameActiveWorkout(it) }
                    )
                }

                // 2. DONE Accordion (Collapsed by Default)
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
                            if (expanded) nextExpanded = false // Space auto-preservation
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
                                                    text = "${set.weight.toString().removeSuffix(".0")} kg × ${set.reps}$rpeText",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Secondary action to undo completion cleanly
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

                // 3. ACTIVE SET (Unmistakable Centre Card)
                item {
                    if (activeFlatSet == null) {
                        // All sets logged -> Gorgeous workout celebration/finish block
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("workout_complete_card"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Workout Complete",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = "ALL SETS COMPLETED!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Phenomenal effort today. Ready to log your session?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.finishActiveWorkout() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("finish_workout_button_card"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("FINISH WORKOUT", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    } else {
                        // Authoritative workout-set info
                        val currentExercise = activeFlatSet.exercise
                        val setsList = activeWorkout.sets[currentExercise.id] ?: emptyList()

                        // Calculate dynamic exercise recommendation and best effort
                        val activeRecommendation = remember(currentExercise.id, allLoggedSets) {
                            val defaultReps = setsList.firstOrNull()?.targetRepsMin ?: 8
                            val defaultWeight = setsList.firstOrNull()?.targetWeight ?: 40f
                            ExerciseIntelligence.getRecommendation(currentExercise.id, allLoggedSets, defaultReps, defaultWeight)
                        }
                        val activeExerciseProfile = remember(currentExercise.id, allLoggedSets) {
                            ExerciseIntelligence.getProfile(currentExercise.id, allLoggedSets)
                        }

                        val haptic = LocalHapticFeedback.current

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active_set_card"),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Title and position (Superset block layout)
                                val groupId = activeWorkout.exerciseMetadata[currentExercise.id]?.supersetGroupId
                                if (!groupId.isNullOrEmpty()) {
                                    val groupExercises = activeWorkout.exercises.filter { activeWorkout.exerciseMetadata[it.id]?.supersetGroupId == groupId }
                                    val currentInGroupIdx = groupExercises.indexOf(currentExercise)
                                    val roundNum = activeFlatSet.setIndex + 1
                                    val totalRounds = setsList.size

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Layers,
                                                contentDescription = "Superset Block",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "SUPERSET BLOCK",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary,
                                                letterSpacing = 1.sp
                                            )
                                        }

                                        Text(
                                            text = "[ Round $roundNum of $totalRounds ]",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        androidx.compose.material3.HorizontalDivider(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            thickness = 1.dp
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "├── Active Now:",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "A${currentInGroupIdx + 1}. ${currentExercise.name} (Set $roundNum)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        val nextExercise = if (currentInGroupIdx < groupExercises.size - 1) {
                                            groupExercises[currentInGroupIdx + 1]
                                        } else {
                                            null
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "└── Next Up:",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = if (nextExercise != null) {
                                                    "A${currentInGroupIdx + 2}. ${nextExercise.name} (Set $roundNum)"
                                                } else {
                                                    "Unified Rest Countdown"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (nextExercise != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                } else {
                                    Column {
                                        Text(
                                            text = currentExercise.name.uppercase(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "SET ${activeFlatSet.setIndex + 1} OF ${setsList.size}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Recommendation panel
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "HUMAN RECOMMENDS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )

                                    val recReps = activeRecommendation.targetReps
                                    val recWeight = activeRecommendation.startWeight

                                    Text(
                                        text = if (recWeight > 0f) "${recWeight.toString().removeSuffix(".0")} kg × $recReps reps" else "$recReps reps",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (activeExerciseProfile != null && activeExerciseProfile.bestSet.isNotEmpty()) {
                                        Text(
                                            text = "Last session: ${activeExerciseProfile.bestSet}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Actual results entry controls
                                val isBodyweight = (activeRecommendation.startWeight <= 0f && activeWeight <= 0f)

                                if (!isBodyweight) {
                                    // WEIGHT Stepper
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "WEIGHT",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 0.5.sp
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    activeWeight = (activeWeight - 2.5f).coerceAtLeast(0f)
                                                },
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                                            ) {
                                                Text("-2.5", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }

                                            Text(
                                                text = "${activeWeight.toString().removeSuffix(".0")} kg",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.weight(1f)
                                            )

                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    activeWeight = (activeWeight + 2.5f)
                                                },
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                                            ) {
                                                Text("+2.5", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }

                                        // Quick plate chips
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
                                                            activeWeight = (activeWeight + plate).coerceAtLeast(0f)
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
                                }

                                // REPS Stepper
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = if (isBodyweight) "ACTUAL REPS" else "REPS",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 0.5.sp
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    activeReps = (activeReps - 1).coerceAtLeast(0)
                                                },
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Decrease reps")
                                            }

                                            Text(
                                                text = "$activeReps",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.weight(1f)
                                            )

                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    activeReps = (activeReps + 1)
                                                },
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Increase reps")
                                            }
                                        }

                                        // Quick reps presets
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(5, 8, 10, 12, 15).forEach { presetReps ->
                                                val isSelected = activeReps == presetReps
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            activeReps = presetReps
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

                                // RPE chips
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "RPE",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.5.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        listOf(6, 7, 8, 9, 10).forEach { r ->
                                            val isSelected = activeRpe == r
                                            Box(
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .background(
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        activeRpe = if (isSelected) null else r
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$r",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    val rpeDesc = when (activeRpe) {
                                        6 -> "6 - Speed was fast. 4 or more reps left in tank."
                                        7 -> "7 - Moderately easy. 3 reps left in tank."
                                        8 -> "8 - Solid effort. 2 reps left in tank."
                                        9 -> "9 - High effort. Only 1 rep left in tank."
                                        10 -> "10 - Maximum effort. Absolute failure achieved."
                                        else -> "Select RPE to guide future recommendations."
                                    }
                                    Text(
                                        text = rpeDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                // 5. Compact Optional Rest Guide
                                RestTimerCard(
                                    restTimeRemaining = restTimeRemaining,
                                    onReduceRestTime = { viewModel.reduceRestTime(it) },
                                    onSkipRestGuide = { viewModel.skipRestGuide() },
                                    onAddRestTime = { viewModel.addRestTime(it) }
                                )

                                // Massive COMPLETE SET Button
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.updateSet(
                                            exerciseId = currentExercise.id,
                                            setIndex = activeFlatSet.setIndex,
                                            reps = activeReps,
                                            weight = activeWeight,
                                            isCompleted = true,
                                            rpe = activeRpe,
                                            actualDuration = activeFlatSet.set.actualDuration,
                                            actualDistance = activeFlatSet.set.actualDistance,
                                            setType = activeFlatSet.set.setType,
                                            targetRepsMin = activeFlatSet.set.targetRepsMin,
                                            targetRepsMax = activeFlatSet.set.targetRepsMax,
                                            targetWeight = activeFlatSet.set.targetWeight,
                                            targetRpe = activeFlatSet.set.targetRpe,
                                            targetDuration = activeFlatSet.set.targetDuration,
                                            targetDistance = activeFlatSet.set.targetDistance,
                                            tempo = activeFlatSet.set.tempo,
                                            notes = activeFlatSet.set.notes
                                        )
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
                                            "COMPLETE SET",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                // Multi-set details and adjustments (hidden for supersets since volume is controlled by parent block)
                                if (groupId.isNullOrEmpty()) {
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
                                                        viewModel.removeSetFromExercise(currentExercise.id, setsList.size - 1)
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
                                                onClick = { viewModel.addSetToExercise(currentExercise.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase Sets", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.removeExerciseFromActiveWorkout(currentExercise.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove exercise",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. NEXT Accordion (Collapsed by Default)
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
                            // Normal single exercise
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
                            if (expanded) doneExpanded = false // Space auto-preservation
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

                // Add Exercise Button (secondary, cleanly placed out of the active flow)
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
                    Spacer(modifier = Modifier.height(32.dp))
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

        // Workout Completion Celebration Dialog
        if (showCompletionDialog) {
            AlertDialog(
                onDismissRequest = { showCompletionDialog = false },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) },
                title = { Text("Workout Complete!", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) },
                text = { Text("Unbelievable work! You have finished all sets and exercises in this routine. Ready to log your session?") },
                confirmButton = {
                    Button(
                        onClick = {
                            if (!isCompletingWorkout) {
                                viewModel.finishActiveWorkout()
                                showCompletionDialog = false
                            }
                        },
                        enabled = !isCompletingWorkout,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isCompletingWorkout) {
                            Text("Saving workout…", fontWeight = FontWeight.Black)
                        } else {
                            Text("Log Workout", fontWeight = FontWeight.Black)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCompletionDialog = false }) {
                        Text("Review Workout", fontWeight = FontWeight.Bold)
                    }
                }
            )
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

/**
 * Reusable, beautiful Material 3 accordion section for Active Workout screen.
 */
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
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(14.dp)
        ) {
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
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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

/**
 * Authoritative mapping helper representing a single set in chronological workout progression
 */
data class FlatSet(
    val exercise: com.example.data.Exercise,
    val setIndex: Int,
    val set: com.example.ui.viewmodel.ActiveSet
)

sealed class UpcomingItem {
    data class SingleExercise(val exercise: com.example.data.Exercise, val sets: List<com.example.ui.viewmodel.ActiveSet>) : UpcomingItem()
    data class SupersetGroup(val groupId: String, val exercises: List<Pair<com.example.data.Exercise, List<com.example.ui.viewmodel.ActiveSet>>>) : UpcomingItem()
}

