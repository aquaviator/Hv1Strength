package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.util.UnitConverter
import com.example.data.Exercise
import com.example.ui.viewmodel.ActiveSet
import com.example.ui.viewmodel.ActiveWorkoutState
import com.example.ui.viewmodel.StrengthViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

object CasualTheme {
    val Background = Color(0xFF111315)          // Deep graphite
    val CardSurface = Color(0xFF1B1E20)         // Warm charcoal
    val CardSurfaceElevated = Color(0xFF24282B) // Elevated charcoal for inputs/pills
    val PrimaryAccent = Color(0xFFC78B45)       // Burnished bronze
    val SecondaryAccent = Color(0xFF4E8D68)     // Muted forest
    val TextPrimary = Color(0xFFF5F2EC)         // Warm off-white
    val TextSecondary = Color(0xFFA8ACA8)       // Stone grey
    val Divider = Color(0x0FFFFFFF)             // Almost invisible rgba(255,255,255,0.06)
    val ErrorRed = Color(0xFFCF6679)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasualWorkoutJournalScreen(
    viewModel: StrengthViewModel,
    activeWorkout: ActiveWorkoutState,
    isCompletingWorkout: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToSummary: (Long) -> Unit
) {
    val exercisesDb by viewModel.exercises.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()

    var elapsedTime by remember { mutableStateOf("00:00") }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showFinishWorkoutDialog by remember { mutableStateOf(false) }

    // Session timer loop
    LaunchedEffect(activeWorkout.startTime) {
        while (true) {
            val totalSeconds = ((System.currentTimeMillis() - activeWorkout.startTime) / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            elapsedTime = if (hours > 0) {
                String.format(Locale.US, "%dh %02dm", hours, minutes)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
            delay(1000)
        }
    }

    // Back handler logic
    BackHandler {
        if (showAddExerciseDialog) {
            showAddExerciseDialog = false
        } else if (showFinishWorkoutDialog) {
            showFinishWorkoutDialog = false
        } else if (activeWorkout.exercises.isNotEmpty()) {
            showCancelConfirmDialog = true
        } else {
            viewModel.cancelActiveWorkout()
            onNavigateBack()
        }
    }

    val startTimeFormatted = remember(activeWorkout.startTime) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(activeWorkout.startTime))
    }

    val totalCompletedSets = remember(activeWorkout.sets) {
        activeWorkout.sets.values.sumOf { sets -> sets.count { it.isCompleted } }
    }

    val totalSetsLogged = remember(activeWorkout.sets) {
        activeWorkout.sets.values.sumOf { it.size }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CasualTheme.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top App Bar & Calm Stats Header
            Surface(
                color = CasualTheme.Background,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (activeWorkout.exercises.isNotEmpty()) {
                                        showCancelConfirmDialog = true
                                    } else {
                                        viewModel.cancelActiveWorkout()
                                        onNavigateBack()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Discard session",
                                    tint = CasualTheme.TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Log a Workout",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = CasualTheme.TextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = CasualTheme.CardSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CasualTheme.SecondaryAccent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = CasualTheme.TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Calm Header Stats Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CasualTheme.CardSurface)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderStatItem(label = "Started", value = startTimeFormatted)
                        HeaderDivider()
                        HeaderStatItem(label = "Duration", value = elapsedTime)
                        HeaderDivider()
                        HeaderStatItem(label = "Exercises", value = "${activeWorkout.exercises.size}")
                        HeaderDivider()
                        HeaderStatItem(label = "Sets", value = "$totalCompletedSets")
                    }
                }
            }

            // Intelligent Superset Banner
            val pendingSuperset = activeWorkout.pendingSupersetSuggestion
            AnimatedVisibility(
                visible = pendingSuperset != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (pendingSuperset != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .testTag("superset_suggestion_banner"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CasualTheme.CardSurfaceElevated),
                        border = BorderStroke(1.dp, CasualTheme.PrimaryAccent.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "POSSIBLE SUPERSET",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = CasualTheme.PrimaryAccent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${pendingSuperset.exerciseNameA} ↔ ${pendingSuperset.exerciseNameB}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = CasualTheme.TextPrimary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.dismissCasualSuperset(pendingSuperset.exerciseIdA, pendingSuperset.exerciseIdB) },
                                    border = BorderStroke(1.dp, CasualTheme.TextSecondary.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("dismiss_superset_button")
                                ) {
                                    Text("Dismiss", style = MaterialTheme.typography.labelMedium, color = CasualTheme.TextSecondary)
                                }
                                Button(
                                    onClick = { viewModel.confirmCasualSuperset(pendingSuperset.exerciseIdA, pendingSuperset.exerciseIdB) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CasualTheme.PrimaryAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("confirm_superset_button")
                                ) {
                                    Text("Group", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }

            // Journal Body Content
            if (activeWorkout.exercises.isEmpty()) {
                // Inviting Minimal Empty State
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("casual_empty_exercises_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = CasualTheme.CardSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = CasualTheme.CardSurfaceElevated,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = CasualTheme.PrimaryAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Ready to train?",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    ),
                                    color = CasualTheme.TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Log your first exercise.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CasualTheme.TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Button(
                                onClick = { showAddExerciseDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("empty_state_add_exercise_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CasualTheme.PrimaryAccent,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Add Exercise",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            } else {
                // Exercise Living Journal Cards List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(
                        items = activeWorkout.exercises,
                        key = { _, ex -> ex.id }
                    ) { _, exercise ->
                        val sets = activeWorkout.sets[exercise.id] ?: emptyList()
                        CasualExerciseJournalCard(
                            exercise = exercise,
                            sets = sets,
                            isMetric = isMetric,
                            onAddSet = { viewModel.addSetToExercise(exercise.id) },
                            onUpdateSet = { setIndex, weight, reps, rpe, completed ->
                                viewModel.updateSet(exercise.id, setIndex, reps, weight, completed, rpe)
                            },
                            onRemoveSet = { setIndex ->
                                viewModel.removeSetFromExercise(exercise.id, setIndex)
                            },
                            onRemoveExercise = {
                                viewModel.removeExerciseFromActiveWorkout(exercise.id)
                            }
                        )
                    }

                    item(key = "bottom_actions") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showAddExerciseDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("add_exercise_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CasualTheme.PrimaryAccent,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Add Exercise",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            OutlinedButton(
                                onClick = { showFinishWorkoutDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("finish_workout_button"),
                                border = BorderStroke(1.dp, CasualTheme.TextSecondary.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CasualTheme.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Finish Workout",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = CasualTheme.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Exercise Dialog / Sheet
        if (showAddExerciseDialog) {
            Dialog(onDismissRequest = { showAddExerciseDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .height(520.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CasualTheme.CardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Add Exercise",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = CasualTheme.TextPrimary
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search exercises...", color = CasualTheme.TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = CasualTheme.TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CasualTheme.PrimaryAccent,
                                unfocusedBorderColor = CasualTheme.Divider,
                                focusedTextColor = CasualTheme.TextPrimary,
                                unfocusedTextColor = CasualTheme.TextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )

                        val categories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Abs")
                        ScrollableTabRow(
                            selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                            edgePadding = 0.dp,
                            containerColor = Color.Transparent,
                            divider = {}
                        ) {
                            categories.forEach { category ->
                                Tab(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    text = {
                                        Text(
                                            category,
                                            fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedCategory == category) CasualTheme.PrimaryAccent else CasualTheme.TextSecondary
                                        )
                                    }
                                )
                            }
                        }

                        val filteredExercises = remember(exercisesDb, searchQuery, selectedCategory) {
                            exercisesDb.filter { ex ->
                                val matchesSearch = ex.name.contains(searchQuery, ignoreCase = true)
                                val matchesCategory = selectedCategory == "All" || ex.category.equals(selectedCategory, ignoreCase = true)
                                matchesSearch && matchesCategory
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(filteredExercises) { _, exercise ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addExerciseToActiveWorkout(exercise)
                                            showAddExerciseDialog = false
                                        },
                                    colors = CardDefaults.cardColors(containerColor = CasualTheme.CardSurfaceElevated),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(exercise.name, fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary)
                                            Text(exercise.category, style = MaterialTheme.typography.bodySmall, color = CasualTheme.TextSecondary)
                                        }
                                        Icon(Icons.Default.Add, contentDescription = "Add", tint = CasualTheme.PrimaryAccent)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddExerciseDialog = false }) {
                                Text("Close", fontWeight = FontWeight.Bold, color = CasualTheme.TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Cancel / Discard Session Dialog
        if (showCancelConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showCancelConfirmDialog = false },
                title = { Text("Discard Session?", fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary) },
                text = { Text("Are you sure you want to discard this workout? All sets logged in this active session will be deleted.", color = CasualTheme.TextSecondary) },
                containerColor = CasualTheme.CardSurface,
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.cancelActiveWorkout()
                            showCancelConfirmDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CasualTheme.ErrorRed)
                    ) {
                        Text("Discard Session", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showCancelConfirmDialog = false },
                        border = BorderStroke(1.dp, CasualTheme.TextSecondary.copy(alpha = 0.5f))
                    ) {
                        Text("Keep Training", fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary)
                    }
                }
            )
        }

        // Finish Workout Sheet / Dialog
        if (showFinishWorkoutDialog) {
            if (activeWorkout.exercises.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { showFinishWorkoutDialog = false },
                    title = { Text("No exercises logged", fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary) },
                    text = { Text("Add an exercise before finishing, or discard this workout.", color = CasualTheme.TextSecondary) },
                    containerColor = CasualTheme.CardSurface,
                    confirmButton = {
                        OutlinedButton(
                            onClick = { showFinishWorkoutDialog = false },
                            border = BorderStroke(1.dp, CasualTheme.TextSecondary.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("keep_training_button")
                        ) {
                            Text("Keep Training", fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary)
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                viewModel.cancelActiveWorkout()
                                showFinishWorkoutDialog = false
                                onNavigateBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CasualTheme.ErrorRed),
                            modifier = Modifier.testTag("discard_workout_button")
                        ) {
                            Text("Discard Workout", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    },
                    modifier = Modifier.testTag("empty_workout_dialog")
                )
            } else {
                AlertDialog(
                    onDismissRequest = { showFinishWorkoutDialog = false },
                    title = {
                        Text("Finish this workout?", fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary)
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Review your training session overview:", color = CasualTheme.TextSecondary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CasualTheme.CardSurfaceElevated)
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Exercises", style = MaterialTheme.typography.labelSmall, color = CasualTheme.TextSecondary)
                                    Text("${activeWorkout.exercises.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Sets", style = MaterialTheme.typography.labelSmall, color = CasualTheme.TextSecondary)
                                    Text("$totalCompletedSets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Duration", style = MaterialTheme.typography.labelSmall, color = CasualTheme.TextSecondary)
                                    Text(elapsedTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary)
                                }
                            }
                        }
                    },
                    containerColor = CasualTheme.CardSurface,
                    confirmButton = {
                        Button(
                            onClick = {
                                if (!isCompletingWorkout) {
                                    viewModel.finishActiveWorkout()
                                    showFinishWorkoutDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CasualTheme.PrimaryAccent, contentColor = Color.White),
                            modifier = Modifier.testTag("confirm_finish_workout_button")
                        ) {
                            Text("Finish Workout", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showFinishWorkoutDialog = false },
                            border = BorderStroke(1.dp, CasualTheme.TextSecondary.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("keep_training_button")
                        ) {
                            Text("Keep Training", fontWeight = FontWeight.Bold, color = CasualTheme.TextPrimary)
                        }
                    },
                    modifier = Modifier.testTag("finish_workout_dialog")
                )
            }
        }
    }
}

@Composable
private fun HeaderStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            ),
            color = CasualTheme.TextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            ),
            color = CasualTheme.TextPrimary
        )
    }
}

@Composable
private fun HeaderDivider() {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .background(CasualTheme.Divider)
    )
}

@Composable
private fun CasualExerciseJournalCard(
    exercise: Exercise,
    sets: List<ActiveSet>,
    isMetric: Boolean,
    onAddSet: () -> Unit,
    onUpdateSet: (setIndex: Int, weight: Float, reps: Int, rpe: Int?, isCompleted: Boolean) -> Unit,
    onRemoveSet: (setIndex: Int) -> Unit,
    onRemoveExercise: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("casual_exercise_card_${exercise.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CasualTheme.CardSurface),
        border = BorderStroke(1.dp, CasualTheme.Divider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = CasualTheme.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${sets.size} Set${if (sets.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CasualTheme.PrimaryAccent
                    )
                }

                IconButton(onClick = onRemoveExercise) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove exercise",
                        tint = CasualTheme.TextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = CasualTheme.Divider)

            // Sets list
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sets.forEachIndexed { setIndex, activeSet ->
                    CasualSetRow(
                        setIndex = setIndex,
                        activeSet = activeSet,
                        isMetric = isMetric,
                        onUpdateSet = { w, r, rpe, c -> onUpdateSet(setIndex, w, r, rpe, c) },
                        onRemoveSet = { onRemoveSet(setIndex) }
                    )
                }
            }

            HorizontalDivider(color = CasualTheme.Divider)

            // Add Set Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = onAddSet,
                    modifier = Modifier.testTag("add_set_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = CasualTheme.PrimaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Set",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CasualTheme.PrimaryAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun CasualSetRow(
    setIndex: Int,
    activeSet: ActiveSet,
    isMetric: Boolean,
    onUpdateSet: (weight: Float, reps: Int, rpe: Int?, isCompleted: Boolean) -> Unit,
    onRemoveSet: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var weightText by remember(activeSet.weight) { mutableStateOf(if (activeSet.weight > 0f) activeSet.weight.toString() else "") }
    var repsText by remember(activeSet.reps) { mutableStateOf(if (activeSet.reps > 0) activeSet.reps.toString() else "10") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CasualTheme.CardSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Set Badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = CasualTheme.CardSurface
        ) {
            Text(
                text = "${setIndex + 1}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = CasualTheme.TextSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Weight Input Field
        OutlinedTextField(
            value = weightText,
            onValueChange = { input ->
                weightText = input
                val parsed = input.toFloatOrNull() ?: 0f
                onUpdateSet(parsed, repsText.toIntOrNull() ?: 10, activeSet.rpe, activeSet.isCompleted)
            },
            placeholder = { Text("0", color = CasualTheme.TextSecondary, fontSize = 14.sp) },
            suffix = { Text(if (isMetric) "kg" else "lbs", color = CasualTheme.TextSecondary, fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CasualTheme.PrimaryAccent,
                unfocusedBorderColor = CasualTheme.Divider,
                focusedTextColor = CasualTheme.TextPrimary,
                unfocusedTextColor = CasualTheme.TextPrimary,
                focusedContainerColor = CasualTheme.CardSurface,
                unfocusedContainerColor = CasualTheme.CardSurface
            ),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Reps Input Field
        OutlinedTextField(
            value = repsText,
            onValueChange = { input ->
                repsText = input
                val parsedReps = input.toIntOrNull() ?: 0
                val parsedWeight = weightText.toFloatOrNull() ?: 0f
                onUpdateSet(parsedWeight, parsedReps, activeSet.rpe, activeSet.isCompleted)
            },
            placeholder = { Text("10", color = CasualTheme.TextSecondary, fontSize = 14.sp) },
            suffix = { Text("reps", color = CasualTheme.TextSecondary, fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CasualTheme.PrimaryAccent,
                unfocusedBorderColor = CasualTheme.Divider,
                focusedTextColor = CasualTheme.TextPrimary,
                unfocusedTextColor = CasualTheme.TextPrimary,
                focusedContainerColor = CasualTheme.CardSurface,
                unfocusedContainerColor = CasualTheme.CardSurface
            ),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Toggle Complete Checkbox Icon
        IconButton(
            onClick = {
                val parsedWeight = weightText.toFloatOrNull() ?: activeSet.weight
                val parsedReps = repsText.toIntOrNull() ?: activeSet.reps
                onUpdateSet(parsedWeight, parsedReps, activeSet.rpe, !activeSet.isCompleted)
            }
        ) {
            Icon(
                imageVector = if (activeSet.isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (activeSet.isCompleted) "Completed" else "Incomplete",
                tint = if (activeSet.isCompleted) CasualTheme.SecondaryAccent else CasualTheme.TextSecondary
            )
        }

        // Remove Set Icon
        IconButton(onClick = onRemoveSet, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete set",
                tint = CasualTheme.TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
