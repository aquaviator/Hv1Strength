package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Exercise
import com.example.ui.viewmodel.ActiveSet
import com.example.ui.viewmodel.StrengthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: StrengthViewModel,
    onNavigateBack: () -> Unit
) {
    val activeWorkoutState by viewModel.activeWorkoutState.collectAsState()
    val exercisesDb by viewModel.exercises.collectAsState()

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            activeWorkout.templateName,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
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
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize")
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
                            fontWeight = FontWeight.Bold
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

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_exercise_card_${exercise.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Exercise Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        exercise.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        exercise.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.removeExerciseFromActiveWorkout(exercise.id) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove exercise",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Table Headers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "SET",
                                    modifier = Modifier.width(48.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "PREVIOUS",
                                    modifier = Modifier.weight(1.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "KG",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "REPS",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "RPE",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.width(56.dp)) // Check column and delete space
                            }

                            // Sets Rows
                            setsList.forEachIndexed { index, set ->
                                ActiveSetRow(
                                    set = set,
                                    onUpdateSet = { updatedSet ->
                                        viewModel.updateSet(
                                            exerciseId = exercise.id,
                                            setIndex = index,
                                            reps = updatedSet.reps,
                                            weight = updatedSet.weight,
                                            isCompleted = updatedSet.isCompleted,
                                            rpe = updatedSet.rpe,
                                            actualDuration = updatedSet.actualDuration,
                                            actualDistance = updatedSet.actualDistance,
                                            setType = updatedSet.setType,
                                            targetRepsMin = updatedSet.targetRepsMin,
                                            targetRepsMax = updatedSet.targetRepsMax,
                                            targetWeight = updatedSet.targetWeight,
                                            targetRpe = updatedSet.targetRpe,
                                            targetDuration = updatedSet.targetDuration,
                                            targetDistance = updatedSet.targetDistance,
                                            tempo = updatedSet.tempo,
                                            notes = updatedSet.notes
                                        )
                                    },
                                    onDeleteSet = {
                                        viewModel.removeSetFromExercise(exercise.id, index)
                                    }
                                )
                            }

                            // Add Set Button
                            TextButton(
                                onClick = { viewModel.addSetToExercise(exercise.id) },
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .testTag("add_set_button_${exercise.id}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Set")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Set", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Bottom Actions
            item {
                Button(
                    onClick = { showAddExerciseDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("add_exercise_to_active_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Exercise")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Exercise", fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Add Exercise",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search exercises...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }
                    )

                    // Categories TabRow
                    val categories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Abs")
                    ScrollableTabRow(
                        selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent
                    ) {
                        categories.forEach { cat ->
                            Tab(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                text = { Text(cat, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }

                    // Exercises List
                    val filteredExercises = exercisesDb.filter { ex ->
                        val matchesSearch = ex.name.contains(searchQuery, ignoreCase = true)
                        val matchesCat = selectedCategory == "All" || ex.category == selectedCategory
                        matchesSearch && matchesCat
                    }

                    Box(modifier = Modifier.weight(1.5f)) {
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
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(filteredExercises) { _, exercise ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.addExerciseToActiveWorkout(exercise)
                                                showAddExerciseDialog = false
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(exercise.name, fontWeight = FontWeight.Bold)
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
                            Text("Close")
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
                    Text("Resume Workout")
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
                        // Pause / Resume
                        val isRestTimerPaused by viewModel.isRestTimerPaused.collectAsState()
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

                        // Reset
                        IconButton(onClick = { viewModel.resetRestTimer() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        // Skip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSetRow(
    set: ActiveSet,
    onUpdateSet: (ActiveSet) -> Unit,
    onDeleteSet: () -> Unit
) {
    var weightInput by remember(set.weight) {
        mutableStateOf(if (set.weight == 0f) "" else set.weight.toString().removeSuffix(".0"))
    }
    var repsInput by remember(set.reps) {
        mutableStateOf(set.reps.toString())
    }
    var rpeInput by remember(set.rpe) {
        mutableStateOf(set.rpe?.toString() ?: "")
    }

    // Advanced inputs for actual performance
    var durationInput by remember(set.actualDuration) {
        mutableStateOf(set.actualDuration?.toString() ?: "")
    }
    var distanceInput by remember(set.actualDistance) {
        mutableStateOf(set.actualDistance?.toString() ?: "")
    }
    var showAdvancedPanel by remember { mutableStateOf(false) }

    val isChecked = set.isCompleted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set number & badge for set type
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = set.setNumber.toString(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    // Mini tag for special set types
                    if (set.setType != "WORKING") {
                        val typeChar = when (set.setType) {
                            "WARMUP" -> "W"
                            "DROP" -> "D"
                            "FAILURE" -> "F"
                            else -> set.setType.firstOrNull()?.toString() ?: "S"
                        }
                        val typeColor = when (set.setType) {
                            "WARMUP" -> Color(0xFFF57C00) // orange
                            "DROP" -> Color(0xFF7B1FA2) // purple
                            "FAILURE" -> Color(0xFFD32F2F) // red
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        Text(
                            text = typeChar,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                            color = typeColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Previous performance summary or template plan
            Box(
                modifier = Modifier.weight(1.5f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val targetParts = mutableListOf<String>()
                    if (set.targetWeight != null) targetParts.add("${set.targetWeight}kg")
                    if (set.targetRepsMin != null || set.targetRepsMax != null) {
                        val repsStr = when {
                            set.targetRepsMin != null && set.targetRepsMax != null -> "${set.targetRepsMin}-${set.targetRepsMax}"
                            set.targetRepsMin != null -> "${set.targetRepsMin}+"
                            else -> "${set.targetRepsMax}"
                        }
                        targetParts.add("$repsStr reps")
                    }
                    if (set.targetRpe != null) targetParts.add("@RPE ${set.targetRpe}")
                    if (set.targetDuration != null) targetParts.add("${set.targetDuration}s")
                    if (set.targetDistance != null) targetParts.add("${set.targetDistance}m")

                    if (targetParts.isNotEmpty()) {
                        Text(
                            text = "Target:",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = targetParts.joinToString(" "),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = set.prevSummary.ifEmpty { "—" },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Weight textfield
            OutlinedTextField(
                value = weightInput,
                onValueChange = { input ->
                    weightInput = input
                    val weight = input.toFloatOrNull() ?: 0f
                    onUpdateSet(set.copy(weight = weight))
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("weight_input_set_${set.setNumber}"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Reps textfield
            OutlinedTextField(
                value = repsInput,
                onValueChange = { input ->
                    repsInput = input
                    val reps = input.toIntOrNull() ?: 0
                    onUpdateSet(set.copy(reps = reps))
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("reps_input_set_${set.setNumber}"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // RPE textfield
            OutlinedTextField(
                value = rpeInput,
                onValueChange = { input ->
                    if (input.isEmpty() || (input.toIntOrNull() != null && input.toInt() in 1..10)) {
                        rpeInput = input
                        onUpdateSet(set.copy(rpe = input.toIntOrNull()))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("rpe_input_set_${set.setNumber}"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("—", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Action section (Check box + More options button + Delete)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.width(76.dp)
            ) {
                IconButton(
                    onClick = {
                        // Prefill targets on completion if inputs are blank or zero
                        var updatedWeight = set.weight
                        var updatedReps = set.reps
                        if (weightInput.isEmpty() || weightInput == "0") {
                            val prefilledWeight = set.targetWeight ?: 0f
                            updatedWeight = prefilledWeight
                            weightInput = if (prefilledWeight == 0f) "" else prefilledWeight.toString().removeSuffix(".0")
                        }
                        if (repsInput.isEmpty() || repsInput == "0" || repsInput == "10" && set.reps == 10) {
                            val prefilledReps = set.targetRepsMin ?: set.targetRepsMax ?: 10
                            updatedReps = prefilledReps
                            repsInput = prefilledReps.toString()
                        }
                        onUpdateSet(set.copy(
                            isCompleted = !isChecked,
                            weight = updatedWeight,
                            reps = updatedReps
                        ))
                    },
                    modifier = Modifier
                        .size(26.dp)
                        .testTag("check_set_${set.setNumber}")
                ) {
                    Icon(
                        imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Check",
                        tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showAdvancedPanel = !showAdvancedPanel },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (showAdvancedPanel) Icons.Default.KeyboardArrowUp else Icons.Default.MoreVert,
                        contentDescription = "More metrics",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteSet,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete set",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Expanded panel for set notes, tempo, setType, duration, distance
        if (showAdvancedPanel) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Set Type Selection Chip row
                Text(
                    "Type:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    listOf("WARMUP", "WORKING", "DROP", "FAILURE").forEach { type ->
                        val isSelected = set.setType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onUpdateSet(set.copy(setType = type))
                            },
                            label = { Text(type, fontSize = 9.sp) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Inputs for Duration, Distance, Tempo
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { input ->
                        durationInput = input
                        onUpdateSet(set.copy(actualDuration = input.toIntOrNull()))
                    },
                    label = { Text("Duration (s)", fontSize = 9.sp) },
                    modifier = Modifier.weight(1.2f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = distanceInput,
                    onValueChange = { input ->
                        distanceInput = input
                        onUpdateSet(set.copy(actualDistance = input.toFloatOrNull()))
                    },
                    label = { Text("Distance (m)", fontSize = 9.sp) },
                    modifier = Modifier.weight(1.2f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = set.tempo ?: "",
                    onValueChange = { input ->
                        onUpdateSet(set.copy(tempo = input.ifBlank { null }))
                    },
                    label = { Text("Tempo", fontSize = 9.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Notes input
            OutlinedTextField(
                value = set.notes ?: "",
                onValueChange = { input ->
                    onUpdateSet(set.copy(notes = input.ifBlank { null }))
                },
                label = { Text("Set Notes / Performance feel", fontSize = 9.sp) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}
