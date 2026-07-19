package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.viewmodel.StrengthViewModel.TemplateExerciseState
import com.example.ui.viewmodel.StrengthViewModel.TemplateSetState
import com.example.data.Exercise
import com.example.data.WorkoutTemplate
import com.example.data.UserProfile
import com.example.data.initials
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.example.ui.viewmodel.StrengthViewModel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    viewModel: StrengthViewModel,
    onNavigateToActiveWorkout: () -> Unit
) {
    val templates by viewModel.templates.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val activeWorkout by viewModel.activeWorkoutState.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())

    var showTemplateEditor by remember { mutableStateOf(false) }
    var templateToEdit by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var selectedExerciseForHistory by remember { mutableStateOf<Exercise?>(null) }

    // Navigation trigger when a workout starts
    LaunchedEffect(Unit) {
        viewModel.navigateToActiveWorkoutEvent.collect {
            onNavigateToActiveWorkout()
        }
    }

    val userProfile by viewModel.activeUserProfile.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                HighDensityHeader(
                    title = "Strength",
                    userProfile = userProfile
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Active Session Alert if running
            if (activeWorkout != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToActiveWorkout() }
                            .testTag("active_workout_resume_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Timer",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Workout in Progress",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        activeWorkout?.templateName ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Button(
                                onClick = onNavigateToActiveWorkout,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text("Resume")
                            }
                        }
                    }
                }
            }

            // Quick Start Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_start_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    "Ready to lift?",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Quick Start",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Day 12 of 30",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                        Text(
                            "Start a custom session right away and add exercises as you train.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.startWorkout(null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("start_empty_workout_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Empty Workout", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Routines Title Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "My Routines",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    TextButton(
                        onClick = {
                            templateToEdit = null
                            showTemplateEditor = true
                        },
                        modifier = Modifier.testTag("create_routine_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Routine", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Routines List
            if (templates.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "No templates",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No Routines Built",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Create one to follow your specific workout plans.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                items(templates) { template ->
                    RoutineCard(
                        template = template,
                        exercises = exercises,
                        onStart = { viewModel.startWorkout(template) },
                        onEdit = {
                            templateToEdit = template
                            showTemplateEditor = true
                        },
                        onDuplicate = {
                            viewModel.duplicateTemplate(template)
                        },
                        onDelete = { viewModel.deleteTemplate(template.id) },
                        viewModel = viewModel,
                        onExerciseClick = { exercise ->
                            selectedExerciseForHistory = exercise
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Full screen premium editor overlay with spring transitions
    AnimatedVisibility(
        visible = showTemplateEditor,
        enter = fadeIn() + expandVertically(animationSpec = spring()),
        exit = fadeOut() + shrinkVertically(animationSpec = spring()),
        modifier = Modifier.fillMaxSize()
    ) {
        TemplateEditorDialog(
            template = templateToEdit,
            exercises = exercises,
            viewModel = viewModel,
            onDismiss = { showTemplateEditor = false }
        )
    }

    if (selectedExerciseForHistory != null) {
        val exercise = selectedExerciseForHistory!!
        val logsFlow = remember(exercise.id) { viewModel.getCompletedSetsForExercise(exercise.id) }
        val logs by logsFlow.collectAsState(initial = emptyList())

        Dialog(onDismissRequest = { selectedExerciseForHistory = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Category: ${exercise.category}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { selectedExerciseForHistory = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Text(
                        text = "Previous Weights & Sets Logs",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No logged history for this exercise.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        // Group logs by sessionId
                        val groupedSets = remember(logs) { logs.groupBy { it.sessionId } }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(groupedSets.keys.toList()) { sessionId ->
                                val sessionSets = groupedSets[sessionId] ?: emptyList()
                                val matchingSession = sessions.find { it.id == sessionId }
                                val sessionDate = matchingSession?.startTime ?: System.currentTimeMillis()
                                val sessionName = matchingSession?.templateName ?: "Workout"

                                val dateStr = remember(sessionDate) {
                                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = sessionDate }
                                    android.text.format.DateFormat.format("MMM d, yyyy", cal).toString()
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = sessionName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Display sets
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        sessionSets.forEach { set ->
                                            SuggestionChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        text = "${set.weight}kg × ${set.reps}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
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

@Composable
fun RoutineCard(
    template: WorkoutTemplate,
    exercises: List<Exercise>,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    viewModel: StrengthViewModel,
    onExerciseClick: (Exercise) -> Unit
) {
    val routineExerciseIds = remember(template.exerciseIdsJson) {
        viewModel.deserializeExerciseIds(template.exerciseIdsJson)
    }

    val routineExercises = remember(routineExerciseIds, exercises) {
        routineExerciseIds.mapNotNull { id -> exercises.find { it.id == id } }
    }

    var templateDetails by remember(template.id) { mutableStateOf<List<TemplateExerciseState>>(emptyList()) }
    LaunchedEffect(template.id) {
        templateDetails = viewModel.getTemplateDetails(template.id)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${routineExercises.size} exercises",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Routine",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate Routine",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Routine",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // High-Contrast Dominant Exercise List
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                routineExercises.forEach { exercise ->
                    val templateEx = templateDetails.find { it.exerciseId == exercise.id }
                    val setsCount = templateEx?.sets?.size ?: 1

                    val targetSummary = templateEx?.sets?.firstOrNull()?.let { s ->
                        val rStr = if (s.targetRepsMin != null && s.targetRepsMax != null) "${s.targetRepsMin}-${s.targetRepsMax}" else s.targetRepsMin ?: s.targetRepsMax ?: "?"
                        val wStr = if (s.targetWeight != null) " @ ${s.targetWeight.toString().removeSuffix(".0")} kg" else ""
                        "${rStr}${wStr}"
                    } ?: ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onExerciseClick(exercise) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$setsCount",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = exercise.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (targetSummary.isNotBlank()) {
                                Text(
                                    text = targetSummary,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "View Details",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("start_routine_${template.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start Routine")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Session", fontWeight = FontWeight.Bold)
            }
        }
    }
}

data class ExerciseIntention(
    val goal: String = "Hypertrophy",
    val progression: String = "Straight Sets",
    val targetRepsMin: Int = 8,
    val targetRepsMax: Int = 12,
    val startingWeight: Float? = null,
    val userNotes: String = ""
) {
    fun toSerializedString(): String {
        val parts = mutableListOf<String>()
        parts.add("Goal: $goal")
        parts.add("Progression: $progression")
        parts.add("Reps: $targetRepsMin-$targetRepsMax")
        if (startingWeight != null) parts.add("Start: $startingWeight")
        if (userNotes.isNotEmpty()) parts.add("Notes: $userNotes")
        return parts.joinToString(" | ")
    }

    companion object {
        fun fromSerializedString(str: String?): ExerciseIntention {
            if (str.isNullOrBlank()) return ExerciseIntention()
            val parts = str.split(" | ").associate {
                val subParts = it.split(": ")
                if (subParts.size == 2) subParts[0].trim() to subParts[1].trim() else "" to ""
            }
            val repsParts = parts["Reps"]?.split("-") ?: emptyList()
            return ExerciseIntention(
                goal = parts["Goal"] ?: "Hypertrophy",
                progression = parts["Progression"] ?: "Straight Sets",
                targetRepsMin = repsParts.getOrNull(0)?.toIntOrNull() ?: 8,
                targetRepsMax = repsParts.getOrNull(1)?.toIntOrNull() ?: 12,
                startingWeight = parts["Start"]?.toFloatOrNull(),
                userNotes = parts["Notes"] ?: ""
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorDialog(
    template: WorkoutTemplate?,
    exercises: List<Exercise>,
    viewModel: StrengthViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var routineName by rememberSaveable(template?.id) { mutableStateOf(template?.name ?: "") }
    var routineNameError by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedExercises = remember { mutableStateListOf<TemplateExerciseState>() }
    var selectedType by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(if (template == null) 1 else 3) }
    var activeFocusedExerciseIndex by remember { mutableStateOf<Int?>(null) }
    var pairingDialogIndex by remember { mutableStateOf<Int?>(null) }
    var showRestConfigGroupIndex by remember { mutableStateOf<String?>(null) }
    var showInsights by remember { mutableStateOf(false) }
    val expandedStrategies = remember { mutableStateMapOf<Int, Boolean>() }

    fun updateSupersetGroupRounds(groupId: String, newRounds: Int) {
        selectedExercises.forEachIndexed { sIdx, se ->
            if (se.supersetGroupId == groupId) {
                val currentSets = se.sets
                val updatedSets = if (newRounds > currentSets.size) {
                    val lastSet = currentSets.lastOrNull()
                    val added = List(newRounds - currentSets.size) {
                        TemplateSetState(
                            setType = lastSet?.setType ?: "WORKING",
                            targetRepsMin = lastSet?.targetRepsMin ?: 8,
                            targetRepsMax = lastSet?.targetRepsMax ?: 10,
                            targetWeight = lastSet?.targetWeight
                        )
                    }
                    currentSets + added
                } else {
                    currentSets.take(newRounds)
                }
                selectedExercises[sIdx] = se.copy(sets = updatedSets)
            }
        }
    }
    
    // Search and filters for Step 2 Exercise Browser
    var searchQuery by remember { mutableStateOf("") }
    var selectedMuscleFilter by remember { mutableStateOf("All") }
    var selectedEquipmentFilter by remember { mutableStateOf("All") }
    var selectedDifficultyFilter by remember { mutableStateOf("All") }
    var selectedBrowserTab by remember { mutableStateOf("Suggested") } // Suggested, Recent, Favorites, All
    
    val favoriteExercises by viewModel.favoriteExercises.collectAsState()
    val allLoggedSets by viewModel.allLoggedSets.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Register BackHandler to intercept back gestures when editing/creating a routine
    BackHandler {
        if (currentStep > 1) {
            currentStep -= 1
        } else {
            onDismiss()
        }
    }

    // Load initial template details if editing
    LaunchedEffect(template) {
        if (template != null) {
            val loaded = viewModel.getTemplateDetails(template.id)
            selectedExercises.clear()
            selectedExercises.addAll(loaded)
            selectedType = "Custom"
        } else {
            selectedExercises.clear()
        }
    }

    // Dynamic metrics
    val totalSets = selectedExercises.fold(0) { acc, ex -> acc + ex.sets.size }
    val totalRest = selectedExercises.fold(0) { acc, ex -> acc + (ex.sets.size - 1).coerceAtLeast(0) * ex.restSeconds }
    val totalLiftTime = selectedExercises.fold(0) { acc, ex -> acc + ex.sets.size * 45 }
    val estDurationMin = if (selectedExercises.isEmpty()) 0 else ((totalRest + totalLiftTime) / 60).coerceAtLeast(5 * selectedExercises.size)
    val totalVolume = selectedExercises.fold(0f) { acc, ex ->
        acc + ex.sets.fold(0f) { setAcc, s ->
            setAcc + (s.targetWeight ?: 0f) * (s.targetRepsMax ?: s.targetRepsMin ?: 0).toFloat()
        }
    }

    val musclesCount = selectedExercises.mapNotNull { ex ->
        exercises.find { it.id == ex.exerciseId }?.category
    }.groupBy { it }.mapValues { it.value.size }

    val allReps = selectedExercises.flatMap { it.sets }.mapNotNull { s -> s.targetRepsMax ?: s.targetRepsMin }
    val trainingFocus = when {
        allReps.isEmpty() -> "General Focus"
        allReps.average() < 6 -> "Strength Focus"
        allReps.average() <= 12 -> "Hypertrophy Focus"
        else -> "Endurance Focus"
    }

    val difficulty = when {
        totalSets == 0 -> "Not Rated"
        totalSets <= 6 -> "Easy"
        totalSets <= 15 -> "Moderate"
        else -> "Challenging"
    }

    // Step 1 Options
    data class WorkoutTypeOption(
        val id: String,
        val name: String,
        val muscles: String,
        val duration: String,
        val icon: ImageVector
    )

    val workoutTypes = listOf(
        WorkoutTypeOption("push", "Push", "Chest • Shoulders • Triceps", "45-60 mins", Icons.Default.TrendingUp),
        WorkoutTypeOption("pull", "Pull", "Back • Biceps • Rear Delts", "40-50 mins", Icons.Default.TrendingDown),
        WorkoutTypeOption("legs", "Legs", "Quads • Hamstrings • Calves", "50-60 mins", Icons.Default.AccessibilityNew),
        WorkoutTypeOption("upper", "Upper Body", "Chest • Back • Shoulders • Arms", "45-60 mins", Icons.Default.FitnessCenter),
        WorkoutTypeOption("lower", "Lower Body", "Legs • Calves • Core", "45-60 mins", Icons.Default.DirectionsRun),
        WorkoutTypeOption("full", "Full Body", "All major muscle groups", "50-70 mins", Icons.Default.AccessibilityNew),
        WorkoutTypeOption("core", "Core", "Abs • Obliques • Lower Back", "15-20 mins", Icons.Default.Accessibility),
        WorkoutTypeOption("cardio", "Cardio", "Stamina • Endurance", "20-40 mins", Icons.Default.Timer),
        WorkoutTypeOption("custom", "Custom", "Custom Workout Structure", "Flexible", Icons.Default.Edit)
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // Wizard Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentStep > 1) {
                                currentStep -= 1
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (currentStep == 1) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = when (currentStep) {
                            1 -> "Workout Type"
                            2 -> "Choose Exercises"
                            3 -> "Configure Workout"
                            else -> "Review & Save"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                }

                // Step Progress Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (step in 1..4) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (step <= currentStep) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                // Main wizard body based on step
                when (currentStep) {
                    1 -> {
                        // STEP 1: CHOOSE WORKOUT TYPE
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "What are you training today?",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val chunked = workoutTypes.chunked(2)
                                chunked.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowItems.forEach { option ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            selectedType = option.name
                                                            if (option.id != "custom") {
                                                                routineName = "${option.name} Day"
                                                            } else {
                                                                routineName = "Custom Workout"
                                                            }
                                                            currentStep = 2
                                                        }
                                                        .testTag("workout_type_card_${option.id}"),
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                    ),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(16.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = option.icon,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Text(
                                                                text = option.duration,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            Text(
                                                                text = option.name,
                                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = option.muscles,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (rowItems.size < 2) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // STEP 2: CHOOSE EXERCISES
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Search bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search exercises...") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                modifier = Modifier.fillMaxWidth().testTag("exercise_search_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Horizontally scrollable selection chips for Category filters
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Abs").forEach { cat ->
                                    FilterChip(
                                        selected = selectedMuscleFilter == cat,
                                        onClick = { selectedMuscleFilter = cat },
                                        label = { Text(cat, fontSize = 11.sp) }
                                    )
                                }
                            }

                            // Dynamic Smart Suggestion / Browser tabs
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Suggested", "Recent", "Favorites", "All").forEach { tab ->
                                    val countLabel = when (tab) {
                                        "Suggested" -> {
                                            // exercises matching selectedType
                                            val muscles = when (selectedType) {
                                                "Push" -> listOf("Chest", "Shoulders", "Arms")
                                                "Pull" -> listOf("Back", "Arms")
                                                "Legs" -> listOf("Legs")
                                                "Upper Body" -> listOf("Chest", "Back", "Shoulders", "Arms")
                                                "Lower Body" -> listOf("Legs", "Abs")
                                                "Full Body" -> listOf("Chest", "Back", "Legs", "Shoulders", "Arms")
                                                "Core" -> listOf("Abs")
                                                else -> emptyList()
                                            }
                                            if (muscles.isEmpty()) "All" else exercises.count { muscles.contains(it.category) }.toString()
                                        }
                                        "Recent" -> allLoggedSets.map { it.exerciseId }.distinct().take(10).size.toString()
                                        "Favorites" -> favoriteExercises.size.toString()
                                        else -> exercises.size.toString()
                                    }
                                    FilterChip(
                                        selected = selectedBrowserTab == tab,
                                        onClick = { selectedBrowserTab = tab },
                                        label = { Text("$tab ($countLabel)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }

                            // Filtered List based on Search, category filter and Browser tabs
                            val filteredList = exercises.filter { ex ->
                                val matchesSearch = ex.name.contains(searchQuery, ignoreCase = true)
                                val matchesCategory = selectedMuscleFilter == "All" || ex.category.equals(selectedMuscleFilter, ignoreCase = true)
                                
                                val matchesTab = when (selectedBrowserTab) {
                                    "Suggested" -> {
                                        val suggestedCategories = when (selectedType) {
                                            "Push" -> listOf("Chest", "Shoulders", "Arms")
                                            "Pull" -> listOf("Back", "Arms")
                                            "Legs" -> listOf("Legs")
                                            "Upper Body" -> listOf("Chest", "Back", "Shoulders", "Arms")
                                            "Lower Body" -> listOf("Legs", "Abs")
                                            "Full Body" -> listOf("Chest", "Back", "Legs", "Shoulders", "Arms")
                                            "Core" -> listOf("Abs")
                                            else -> emptyList()
                                        }
                                        suggestedCategories.isEmpty() || suggestedCategories.contains(ex.category)
                                    }
                                    "Recent" -> {
                                        val recentIds = allLoggedSets.map { it.exerciseId }.distinct().take(10)
                                        recentIds.contains(ex.id)
                                    }
                                    "Favorites" -> favoriteExercises.contains(ex.id)
                                    else -> true
                                }
                                matchesSearch && matchesCategory && matchesTab
                            }

                            // Exercise listing
                            Box(modifier = Modifier.weight(1f)) {
                                if (filteredList.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No matching exercises", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(filteredList) { exercise ->
                                            val addedCount = selectedExercises.count { it.exerciseId == exercise.id }
                                            val isFav = favoriteExercises.contains(exercise.id)

                                            // Calculate actual PR / previous performance
                                            val setsForEx = allLoggedSets.filter { it.exerciseId == exercise.id && it.isCompleted }
                                            val prSet = setsForEx.maxByOrNull { it.weight }
                                            val lastSet = setsForEx.lastOrNull()
                                            
                                            val prString = if (prSet != null) "${com.example.core.util.UnitConverter.formatWeight(prSet.weight.toDouble(), isMetric)} × ${prSet.reps}" else "None"
                                            val lastString = if (lastSet != null) "${com.example.core.util.UnitConverter.formatWeight(lastSet.weight.toDouble(), isMetric)} × ${lastSet.reps}" else "None"

                                            // Exercise Card
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        // Tapping adds a new template exercise with default set
                                                        val defaultSet = TemplateSetState(
                                                            id = 0,
                                                            setType = "WORKING",
                                                            targetRepsMin = lastSet?.targetRepsMin ?: 8,
                                                            targetRepsMax = lastSet?.targetRepsMax ?: 10,
                                                            targetWeight = lastSet?.targetWeight ?: 0f
                                                        )
                                                        val newEx = TemplateExerciseState(
                                                            exerciseId = exercise.id,
                                                            restSeconds = 90,
                                                            sets = listOf(defaultSet)
                                                        )
                                                        selectedExercises.add(newEx)
                                                    }
                                                    .testTag("exercise_browser_card_${exercise.id}"),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (addedCount > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (addedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Favorite Toggle
                                                    IconButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            viewModel.toggleFavoriteExercise(exercise.id)
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                            contentDescription = "Favorite",
                                                            tint = if (isFav) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(exercise.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Box(modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                                Text(exercise.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                            }
                                                            val diffLabel = when (exercise.id) {
                                                                "deadlift", "squat", "romanian_deadlift" -> "Advanced"
                                                                "bench_press", "overhead_press", "pull_up" -> "Intermediate"
                                                                else -> "Beginner"
                                                            }
                                                            Text("•  $diffLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                        ) {
                                                            Text("Last: $lastString", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text("PR: $prString", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }

                                                    // Selector Badges
                                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        if (addedCount > 0) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                IconButton(
                                                                    onClick = {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                        val idx = selectedExercises.indexOfLast { it.exerciseId == exercise.id }
                                                                        if (idx >= 0) {
                                                                            selectedExercises.removeAt(idx)
                                                                        }
                                                                    }
                                                                ) {
                                                                    Icon(Icons.Default.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                                                                }
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                                                        .size(24.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text("$addedCount", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        } else {
                                                            Icon(Icons.Default.AddCircleOutline, "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Continue button
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentStep = 3
                                },
                                enabled = selectedExercises.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("exercise_browser_continue_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Continue with ${selectedExercises.size} Exercises ➔", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    3 -> {
                        // STEP 3: CONFIGURE WORKOUT (WORKOUT EDITOR & LIVE INSIGHTS)
                        val isKeyboardVisible = WindowInsets.isImeVisible
                        val listState = rememberLazyListState()

                        // Automatically scroll focused exercise card into view
                        LaunchedEffect(activeFocusedExerciseIndex) {
                            if (isKeyboardVisible && activeFocusedExerciseIndex != null) {
                                activeFocusedExerciseIndex?.let { index ->
                                    if (index in 0 until selectedExercises.size) {
                                        listState.animateScrollToItem(index)
                                    }
                                }
                            }
                        }

                        // Automatically reset focus state when keyboard is closed
                        LaunchedEffect(isKeyboardVisible) {
                            if (!isKeyboardVisible) {
                                activeFocusedExerciseIndex = null
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 1. COMPACT STATUS BANNER (Focus Mode Header - Visible when Keyboard is Active)
                            AnimatedVisibility(
                                visible = isKeyboardVisible,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                imageVector = Icons.Default.FitnessCenter,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "${routineName.ifBlank { "Routine" }} • ${selectedExercises.size} Ex • ${totalSets} Sets",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Text(
                                            text = "${com.example.core.util.UnitConverter.formatWeight(totalVolume.toDouble(), isMetric)} Est.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // 2. DETAILED INSIGHTS & ROUTINE NAME (Context Mode Header - Visible when Keyboard is Inactive)
                            AnimatedVisibility(
                                visible = !isKeyboardVisible,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Routine Name field
                                    OutlinedTextField(
                                        value = routineName,
                                        onValueChange = { 
                                            routineName = it
                                            if (it.isNotBlank()) {
                                                routineNameError = null
                                            }
                                        },
                                        label = { Text("Routine Name (e.g. Push Day)") },
                                        modifier = Modifier.fillMaxWidth().testTag("routine_name_editor_input"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        isError = routineNameError != null,
                                        supportingText = if (routineNameError != null) {
                                            { Text(routineNameError!!, color = MaterialTheme.colorScheme.error) }
                                        } else null
                                    )

                                    // LIVE WORKOUT INSIGHTS ACCORDION
                                    if (!showInsights) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showInsights = true }
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "View Insights (${selectedExercises.size} Exercises, $totalSets Sets) ➔",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = trainingFocus,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    } else {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().clickable { showInsights = false },
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text("Live Workout Insights", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Collapse", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    }
                                                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                                        Text(trainingFocus, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                    }
                                                }

                                                // Metric highlights row
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Exercises: ${selectedExercises.size}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                    Text("Sets: $totalSets", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                    Text("Est: ${estDurationMin}m", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                    Text("Volume: ${com.example.core.util.UnitConverter.formatWeight(totalVolume.toDouble(), isMetric)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                }

                                                // Dynamic Warnings / Recommendations
                                                val backSets = musclesCount["Back"] ?: 0
                                                val chestSets = musclesCount["Chest"] ?: 0
                                                val legSets = musclesCount["Legs"] ?: 0
                                                
                                                val warnings = remember(selectedExercises, musclesCount) {
                                                    mutableListOf<String>().apply {
                                                        if (selectedExercises.isNotEmpty()) {
                                                            if (backSets == 0 && (chestSets > 0 || legSets > 0)) {
                                                                add("⚠️ Insight: Suggest adding a Back pulling exercise to balance the routine.")
                                                            }
                                                            if (legSets == 0 && (chestSets > 0 || backSets > 0)) {
                                                                add("⚠️ Insight: No Lower Body (Legs) exercises included.")
                                                            }
                                                            if (chestSets > 8) {
                                                                add("⚠️ Warning: High Chest volume. Recovery might be slow.")
                                                            }
                                                            if (totalSets > 25) {
                                                                add("⚠️ Note: High total sets volume (>25). Keep intensity high.")
                                                            }
                                                        }
                                                    }
                                                }

                                                if (warnings.isNotEmpty()) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        warnings.take(2).forEach { msg ->
                                                            Text(msg, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                                       // 3. SCROLLABLE SELECTED EXERCISES LIST (Keyboard-aware focus collapse)
                            Box(modifier = Modifier.weight(1f)) {
                                val supersetLabels = remember(selectedExercises.toList()) {
                                    val list = selectedExercises.toList()
                                    val labelsMap = mutableMapOf<Int, String>()
                                    val groupToLetter = mutableMapOf<String, Char>()
                                    var nextLetter = 'A'
                                    val groupCounts = mutableMapOf<String, Int>()

                                    list.forEachIndexed { index, ex ->
                                        val groupId = ex.supersetGroupId
                                        if (!groupId.isNullOrEmpty()) {
                                            val letter = groupToLetter.getOrPut(groupId) {
                                                val l = nextLetter
                                                if (nextLetter < 'Z') nextLetter++
                                                l
                                            }
                                            val count = groupCounts.getOrDefault(groupId, 0) + 1
                                            groupCounts[groupId] = count
                                            labelsMap[index] = "$letter$count"
                                        }
                                    }
                                    labelsMap
                                }

                                LazyColumn(
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(selectedExercises) { exIndex, templateExercise ->
                                        val exerciseObj = exercises.find { it.id == templateExercise.exerciseId }
                                        if (exerciseObj != null) {
                                            val isFocusedEx = activeFocusedExerciseIndex == exIndex
                                            val shouldCollapseEx = isKeyboardVisible && !isFocusedEx

                                            val isFirstInGroup = !templateExercise.supersetGroupId.isNullOrEmpty() &&
                                                    selectedExercises.indexOfFirst { it.supersetGroupId == templateExercise.supersetGroupId } == exIndex

                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (isFirstInGroup) {
                                                    val groupId = templateExercise.supersetGroupId!!
                                                    val groupExs = selectedExercises.filter { it.supersetGroupId == groupId }
                                                    val groupExsObjs = groupExs.mapNotNull { ge -> exercises.find { it.id == ge.exerciseId } }
                                                    val isCircuit = groupExs.size >= 3
                                                    val groupLetter = supersetLabels[exIndex]?.firstOrNull()?.toString() ?: "A"
                                                    
                                                    // Superset / Circuit Group Header Card
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                                                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f))
                                                    ) {
                                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(28.dp)
                                                                            .background(Color(0xFF00E5FF), CircleShape),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Text(
                                                                            text = groupLetter,
                                                                            style = MaterialTheme.typography.bodyMedium,
                                                                            fontWeight = FontWeight.Black,
                                                                            color = MaterialTheme.colorScheme.onPrimary
                                                                        )
                                                                    }
                                                                    Text(
                                                                        text = if (isCircuit) "CIRCUIT GROUP" else "SUPERSET GROUP",
                                                                        style = MaterialTheme.typography.titleSmall,
                                                                        fontWeight = FontWeight.Black,
                                                                        color = Color(0xFF00E5FF)
                                                                    )
                                                                }
                                                                
                                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    IconButton(
                                                                        onClick = {
                                                                            showRestConfigGroupIndex = groupId
                                                                        },
                                                                        modifier = Modifier.size(32.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.Timer, "Group Rest", modifier = Modifier.size(16.dp), tint = Color(0xFF00E5FF))
                                                                    }
                                                                    IconButton(
                                                                        onClick = {
                                                                            // Clear supersetGroupId for all exercises in this group
                                                                            selectedExercises.forEachIndexed { sIdx, se ->
                                                                                if (se.supersetGroupId == groupId) {
                                                                                    selectedExercises[sIdx] = se.copy(supersetGroupId = null)
                                                                                }
                                                                            }
                                                                        },
                                                                        modifier = Modifier.size(32.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.LayersClear, "Ungroup", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                                                    }
                                                                }
                                                            }
                                                            
                                                            // Analysis / Tip Section
                                                            val analysis = remember(groupExsObjs) { analyzeSuperset(groupExsObjs) }
                                                            if (analysis.message.isNotEmpty()) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .background(
                                                                            if (analysis.isGood) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                                                                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f),
                                                                            RoundedCornerShape(8.dp)
                                                                        )
                                                                        .padding(8.dp)
                                                                ) {
                                                                    Text(
                                                                        text = analysis.message,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = if (analysis.isGood) MaterialTheme.colorScheme.onPrimaryContainer
                                                                                else MaterialTheme.colorScheme.onErrorContainer
                                                                    )
                                                                }
                                                            }
                                                            
                                                            // Interactive Group stats summary with Rounds Stepper
                                                            val maxSets = groupExs.maxOfOrNull { it.sets.size } ?: 0
                                                            val commonRest = groupExs.firstOrNull()?.restSeconds ?: 90
                                                            val unevenSets = groupExs.map { it.sets.size }.distinct().size > 1
                                                            
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column {
                                                                    Text("ROUNDS (SETS)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                                    ) {
                                                                        IconButton(
                                                                            onClick = {
                                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                if (maxSets > 1) {
                                                                                    updateSupersetGroupRounds(groupId, maxSets - 1)
                                                                                }
                                                                            },
                                                                            modifier = Modifier
                                                                                .size(32.dp)
                                                                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                                        ) {
                                                                            Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                        
                                                                        Text(
                                                                            text = "$maxSets",
                                                                            style = MaterialTheme.typography.titleMedium,
                                                                            fontWeight = FontWeight.Black,
                                                                            color = MaterialTheme.colorScheme.primary
                                                                        )
                                                                        
                                                                        IconButton(
                                                                            onClick = {
                                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                updateSupersetGroupRounds(groupId, maxSets + 1)
                                                                            },
                                                                            modifier = Modifier
                                                                                .size(32.dp)
                                                                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                                        ) {
                                                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                }
                                                                
                                                                Column(horizontalAlignment = Alignment.End) {
                                                                    Text("POST-ROUND REST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    Text("${commonRest}s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                                                }
                                                                
                                                                if (unevenSets) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                            .align(Alignment.CenterVertically)
                                                                    ) {
                                                                        Text(
                                                                            text = "⚠️ Uneven",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = MaterialTheme.colorScheme.error
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                if (!templateExercise.supersetGroupId.isNullOrEmpty()) {
                                                    SupersetNestedExerciseCard(
                                                        templateExercise = templateExercise,
                                                        exerciseObj = exerciseObj,
                                                        exIndex = exIndex,
                                                        supersetLabels = supersetLabels,
                                                        isMetric = isMetric,
                                                        onUpdate = { updated ->
                                                            selectedExercises[exIndex] = updated
                                                        },
                                                        onDelete = {
                                                            selectedExercises.removeAt(exIndex)
                                                        },
                                                        onMoveUp = {
                                                            if (exIndex > 0) {
                                                                val temp = selectedExercises[exIndex]
                                                                selectedExercises[exIndex] = selectedExercises[exIndex - 1]
                                                                selectedExercises[exIndex - 1] = temp
                                                            }
                                                        },
                                                        onMoveDown = {
                                                            if (exIndex < selectedExercises.size - 1) {
                                                                val temp = selectedExercises[exIndex]
                                                                selectedExercises[exIndex] = selectedExercises[exIndex + 1]
                                                                selectedExercises[exIndex + 1] = temp
                                                            }
                                                        },
                                                        activeFocusedExerciseIndex = activeFocusedExerciseIndex,
                                                        onFocusedChange = { activeFocusedExerciseIndex = it },
                                                        isKeyboardVisible = isKeyboardVisible,
                                                        haptic = haptic,
                                                        expandedStrategies = expandedStrategies
                                                    )
                                                } else {
                                                    if (shouldCollapseEx) {
                                                        // Focus Mode: Inactive items collapse into compact rows
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .alpha(0.45f)
                                                            .clickable {
                                                                activeFocusedExerciseIndex = exIndex
                                                            },
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .drawBehind {
                                                                    if (!templateExercise.supersetGroupId.isNullOrEmpty()) {
                                                                        drawRect(
                                                                            color = Color(0xFF00E5FF),
                                                                            topLeft = Offset.Zero,
                                                                            size = Size(5.dp.toPx(), size.height)
                                                                        )
                                                                    }
                                                                }
                                                                .padding(start = if (!templateExercise.supersetGroupId.isNullOrEmpty()) 5.dp else 0.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                        val label = supersetLabels[exIndex]
                                                                        if (!label.isNullOrEmpty()) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                                                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                            ) {
                                                                                Text(
                                                                                    text = label,
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    fontWeight = FontWeight.Black,
                                                                                    color = Color(0xFF00E5FF),
                                                                                    fontSize = 10.sp
                                                                                )
                                                                            }
                                                                        }
                                                                        Text(exerciseObj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                                    }
                                                                    Text(
                                                                        "${templateExercise.sets.size} Sets • Rest: ${templateExercise.restSeconds}s",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                                Icon(Icons.Default.ExpandMore, contentDescription = "Expand", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    // Focus Mode / Default Mode: Active Card (fully expanded)
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                        border = BorderStroke(
                                                            width = if (isFocusedEx) 2.dp else 1.dp,
                                                            color = if (isFocusedEx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                        )
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .drawBehind {
                                                                    if (!templateExercise.supersetGroupId.isNullOrEmpty()) {
                                                                        drawRect(
                                                                            color = Color(0xFF00E5FF),
                                                                            topLeft = Offset.Zero,
                                                                            size = Size(6.dp.toPx(), size.height)
                                                                        )
                                                                    }
                                                                }
                                                                .padding(start = if (!templateExercise.supersetGroupId.isNullOrEmpty()) 6.dp else 0.dp)
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                                // Exercise Card Title Header
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Column {
                                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                            val label = supersetLabels[exIndex]
                                                                            if (!label.isNullOrEmpty()) {
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                                                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                                ) {
                                                                                    Text(
                                                                                        text = label,
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        fontWeight = FontWeight.Black,
                                                                                        color = Color(0xFF00E5FF)
                                                                                    )
                                                                                }
                                                                            }
                                                                            Text(exerciseObj.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                                        }
                                                                        Text(exerciseObj.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    }

                                                                    // Card reordering / deletion / superset actions
                                                                    Row(
                                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        IconButton(
                                                                            onClick = {
                                                                                if (exIndex > 0) {
                                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                    val temp = selectedExercises[exIndex]
                                                                                    selectedExercises[exIndex] = selectedExercises[exIndex - 1]
                                                                                    selectedExercises[exIndex - 1] = temp
                                                                                }
                                                                            },
                                                                            enabled = exIndex > 0
                                                                        ) {
                                                                            Icon(Icons.Default.ArrowUpward, "Move Up", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                if (exIndex < selectedExercises.size - 1) {
                                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                    val temp = selectedExercises[exIndex]
                                                                                    selectedExercises[exIndex] = selectedExercises[exIndex + 1]
                                                                                    selectedExercises[exIndex + 1] = temp
                                                                                }
                                                                            },
                                                                            enabled = exIndex < selectedExercises.size - 1
                                                                        ) {
                                                                            Icon(Icons.Default.ArrowDownward, "Move Down", modifier = Modifier.size(20.dp))
                                                                        }
                                                                        
                                                                        var showCardMenu by remember { mutableStateOf(false) }
                                                                        Box {
                                                                            IconButton(onClick = { showCardMenu = true }) {
                                                                                Icon(Icons.Default.MoreVert, "More Options", modifier = Modifier.size(20.dp))
                                                                            }
                                                                            DropdownMenu(
                                                                                expanded = showCardMenu,
                                                                                onDismissRequest = { showCardMenu = false }
                                                                            ) {
                                                                                DropdownMenuItem(
                                                                                    text = { Text("Pair / Add to Superset") },
                                                                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                                                    onClick = {
                                                                                        showCardMenu = false
                                                                                        pairingDialogIndex = exIndex
                                                                                    }
                                                                                )
                                                                                if (!templateExercise.supersetGroupId.isNullOrEmpty()) {
                                                                                    DropdownMenuItem(
                                                                                        text = { Text("Remove from Superset") },
                                                                                        leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                                                        onClick = {
                                                                                            showCardMenu = false
                                                                                            selectedExercises[exIndex] = templateExercise.copy(supersetGroupId = null)
                                                                                        }
                                                                                    )
                                                                                    DropdownMenuItem(
                                                                                        text = { Text("Configure Group Rest") },
                                                                                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                                                        onClick = {
                                                                                            showCardMenu = false
                                                                                            showRestConfigGroupIndex = templateExercise.supersetGroupId
                                                                                        }
                                                                                    )
                                                                                }
                                                                                DropdownMenuItem(
                                                                                    text = { Text("Delete Exercise", color = MaterialTheme.colorScheme.error) },
                                                                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                                                                    onClick = {
                                                                                        showCardMenu = false
                                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                        selectedExercises.removeAt(exIndex)
                                                                                    }
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                     val intention = remember(templateExercise.notes) { ExerciseIntention.fromSerializedString(templateExercise.notes) }
                                                                val isStrategyExpanded = expandedStrategies[exIndex] ?: false
                                                                val rotationAngle by animateFloatAsState(targetValue = if (isStrategyExpanded) 90f else 0f)

                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clickable { expandedStrategies[exIndex] = !isStrategyExpanded }
                                                                        .padding(vertical = 4.dp),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = "Exercise Strategy & Rest Guide",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    )
                                                                    Icon(
                                                                        imageVector = Icons.Default.KeyboardArrowRight,
                                                                        contentDescription = if (isStrategyExpanded) "Collapse strategy" else "Expand strategy",
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier
                                                                            .size(16.dp)
                                                                            .rotate(rotationAngle)
                                                                    )
                                                                }

                                                                AnimatedVisibility(
                                                                    visible = isStrategyExpanded,
                                                                    enter = expandVertically() + fadeIn(),
                                                                    exit = shrinkVertically() + fadeOut()
                                                                ) {
                                                                    Column(
                                                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                                    ) {
                                                                        RestDurationControl(
                                                                            restSeconds = templateExercise.restSeconds,
                                                                            onRestSecondsChange = { newRest ->
                                                                                selectedExercises[exIndex] = templateExercise.copy(restSeconds = newRest)
                                                                            }
                                                                        )

                                                                        OutlinedTextField(
                                                                            value = intention.userNotes,
                                                                            onValueChange = {
                                                                                val updated = intention.copy(userNotes = it)
                                                                                selectedExercises[exIndex] = templateExercise.copy(notes = updated.toSerializedString())
                                                                            },
                                                                            label = { Text("Exercise Notes", fontSize = 11.sp) },
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .onFocusChanged { state ->
                                                                                    if (state.isFocused) {
                                                                                        activeFocusedExerciseIndex = exIndex
                                                                                    }
                                                                                },
                                                                            singleLine = true,
                                                                            shape = RoundedCornerShape(12.dp)
                                                                        )

                                                                        // Training Goal Segmented Chips
                                                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                            Text("TRAINING GOAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                                                            Row(
                                                                                modifier = Modifier
                                                                                    .fillMaxWidth()
                                                                                    .horizontalScroll(rememberScrollState()),
                                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                            ) {
                                                                                val goals = listOf(
                                                                                    Triple("Strength", "Strength", Icons.Default.FitnessCenter),
                                                                                    Triple("Hypertrophy", "Muscle", Icons.Default.TrendingUp),
                                                                                    Triple("Endurance", "Stamina", Icons.Default.Timer),
                                                                                    Triple("Technique", "Form", Icons.Default.Psychology),
                                                                                    Triple("Custom", "Custom", Icons.Default.Edit)
                                                                                )
                                                                                goals.forEach { (goalId, label, icon) ->
                                                                                    val isSelected = intention.goal == goalId
                                                                                    FilterChip(
                                                                                        selected = isSelected,
                                                                                        onClick = {
                                                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                            val updated = intention.copy(goal = goalId)
                                                                                            selectedExercises[exIndex] = templateExercise.copy(notes = updated.toSerializedString())
                                                                                        },
                                                                                        label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                                                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                                                        shape = RoundedCornerShape(12.dp),
                                                                                        colors = FilterChipDefaults.filterChipColors(
                                                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                                                        )
                                                                                    )
                                                                                }
                                                                            }
                                                                        }

                                                                        // Progression Style Segmented Chips
                                                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                            Text("PROGRESSION STYLE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                                                            Row(
                                                                                modifier = Modifier
                                                                                    .fillMaxWidth()
                                                                                    .horizontalScroll(rememberScrollState()),
                                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                            ) {
                                                                                val progressions = listOf("Straight Sets", "Ramp Up", "Reverse Pyramid", "Pyramid", "Custom")
                                                                                progressions.forEach { style ->
                                                                                    val isSelected = intention.progression == style
                                                                                    FilterChip(
                                                                                        selected = isSelected,
                                                                                        onClick = {
                                                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                            val updated = intention.copy(progression = style)
                                                                                            selectedExercises[exIndex] = templateExercise.copy(notes = updated.toSerializedString())
                                                                                        },
                                                                                        label = { Text(style, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                                                        shape = RoundedCornerShape(12.dp),
                                                                                        colors = FilterChipDefaults.filterChipColors(
                                                                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                                                        )
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                                                // Interactive Grid/Row of Guided Parameters: Sets, Reps, and Starting Weight
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    // 1. SETS CONFIGURATION
                                                                    Column(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                                                            .padding(6.dp),
                                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                                    ) {
                                                                        Text("SETS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                        Spacer(modifier = Modifier.height(18.dp))
                                                                        Text("${templateExercise.sets.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                                                        Spacer(modifier = Modifier.height(24.dp))
                                                                        Row(
                                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            IconButton(
                                                                                onClick = {
                                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                    val setsList = templateExercise.sets.toMutableList()
                                                                                    if (setsList.size > 1) {
                                                                                        setsList.removeAt(setsList.size - 1)
                                                                                        selectedExercises[exIndex] = templateExercise.copy(sets = setsList)
                                                                                    }
                                                                                },
                                                                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                            ) {
                                                                                Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                                                                            }
                                                                            IconButton(
                                                                                onClick = {
                                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                    val setsList = templateExercise.sets.toMutableList()
                                                                                    val lastSet = setsList.lastOrNull()
                                                                                    val newSet = TemplateSetState(
                                                                                        setType = lastSet?.setType ?: "WORKING",
                                                                                        targetRepsMin = intention.targetRepsMin,
                                                                                        targetRepsMax = intention.targetRepsMax,
                                                                                        targetWeight = intention.startingWeight ?: lastSet?.targetWeight ?: 0f
                                                                                    )
                                                                                    setsList.add(newSet)
                                                                                    selectedExercises[exIndex] = templateExercise.copy(sets = setsList)
                                                                                },
                                                                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                            ) {
                                                                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                                                            }
                                                                        }
                                                                    }

                                                                    // 2. REPS RANGE CONFIGURATION
                                                                    Column(
                                                                        modifier = Modifier
                                                                            .weight(1.3f)
                                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                                                            .padding(6.dp),
                                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                                    ) {
                                                                        Text("REPS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Text("${intention.targetRepsMin} - ${intention.targetRepsMax}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                                                        Spacer(modifier = Modifier.height(6.dp))
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text("Min:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                            Row(
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                                            ) {
                                                                                IconButton(
                                                                                    onClick = {
                                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                        val minReps = (intention.targetRepsMin - 1).coerceAtLeast(1)
                                                                                        val updated = intention.copy(targetRepsMin = minReps)
                                                                                        val setsList = templateExercise.sets.map { it.copy(targetRepsMin = minReps) }
                                                                                        selectedExercises[exIndex] = templateExercise.copy(
                                                                                            notes = updated.toSerializedString(),
                                                                                            sets = setsList
                                                                                        )
                                                                                    },
                                                                                    modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                                ) {
                                                                                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp))
                                                                                }
                                                                                IconButton(
                                                                                    onClick = {
                                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                        val minReps = (intention.targetRepsMin + 1).coerceAtMost(intention.targetRepsMax)
                                                                                        val updated = intention.copy(targetRepsMin = minReps)
                                                                                        val setsList = templateExercise.sets.map { it.copy(targetRepsMin = minReps) }
                                                                                        selectedExercises[exIndex] = templateExercise.copy(
                                                                                            notes = updated.toSerializedString(),
                                                                                            sets = setsList
                                                                                        )
                                                                                    },
                                                                                    modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                                ) {
                                                                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                                                                                }
                                                                            }
                                                                        }
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text("Max:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                            Row(
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                                            ) {
                                                                                IconButton(
                                                                                    onClick = {
                                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                        val maxReps = (intention.targetRepsMax - 1).coerceAtLeast(intention.targetRepsMin)
                                                                                        val updated = intention.copy(targetRepsMax = maxReps)
                                                                                        val setsList = templateExercise.sets.map { it.copy(targetRepsMax = maxReps) }
                                                                                        selectedExercises[exIndex] = templateExercise.copy(
                                                                                            notes = updated.toSerializedString(),
                                                                                            sets = setsList
                                                                                        )
                                                                                    },
                                                                                    modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                                ) {
                                                                                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp))
                                                                                }
                                                                                IconButton(
                                                                                    onClick = {
                                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                        val maxReps = (intention.targetRepsMax + 1).coerceIn(1, 100)
                                                                                        val updated = intention.copy(targetRepsMax = maxReps)
                                                                                        val setsList = templateExercise.sets.map { it.copy(targetRepsMax = maxReps) }
                                                                                        selectedExercises[exIndex] = templateExercise.copy(
                                                                                            notes = updated.toSerializedString(),
                                                                                            sets = setsList
                                                                                        )
                                                                                    },
                                                                                    modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                                ) {
                                                                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                                                                                }
                                                                            }
                                                                        }
                                                                    }

                                                                    // 3. STARTING WEIGHT CONFIGURATION
                                                                    Column(
                                                                        modifier = Modifier
                                                                            .weight(1.2f)
                                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                                                            .padding(6.dp),
                                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                                    ) {
                                                                        Text("START WEIGHT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                        Spacer(modifier = Modifier.height(18.dp))
                                                                        Text(
                                                                            if (intention.startingWeight != null) com.example.core.util.UnitConverter.formatWeight(intention.startingWeight.toDouble(), isMetric) else "--",
                                                                            style = MaterialTheme.typography.headlineSmall,
                                                                            fontWeight = FontWeight.Black,
                                                                            color = MaterialTheme.colorScheme.primary
                                                                        )
                                                                        Spacer(modifier = Modifier.height(24.dp))
                                                                        Row(
                                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            IconButton(
                                                                                onClick = {
                                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                    val currentW = intention.startingWeight ?: 20f
                                                                                    val delta = if (isMetric) 2.5f else (5f * com.example.core.util.UnitConverter.LB_TO_KG).toFloat()
                                                                                    val newW = (currentW - delta).coerceAtLeast(0f)
                                                                                    val updated = intention.copy(startingWeight = newW)
                                                                                    val setsList = templateExercise.sets.map { it.copy(targetWeight = newW) }
                                                                                    selectedExercises[exIndex] = templateExercise.copy(
                                                                                        notes = updated.toSerializedString(),
                                                                                        sets = setsList
                                                                                    )
                                                                                },
                                                                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                            ) {
                                                                                Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                                                                            }
                                                                            IconButton(
                                                                                onClick = {
                                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                                    val currentW = intention.startingWeight ?: 20f
                                                                                    val delta = if (isMetric) 2.5f else (5f * com.example.core.util.UnitConverter.LB_TO_KG).toFloat()
                                                                                    val newW = currentW + delta
                                                                                    val updated = intention.copy(startingWeight = newW)
                                                                                    val setsList = templateExercise.sets.map { it.copy(targetWeight = newW) }
                                                                                    selectedExercises[exIndex] = templateExercise.copy(
                                                                                        notes = updated.toSerializedString(),
                                                                                        sets = setsList
                                                                                    )
                                                                                },
                                                                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                                            ) {
                                                                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                                                            }
                                                                        }
                                                                    }
                                                                }                                                             }

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

                            // 4. FLOATING MACRO BOTTOM NAVIGATION BUTTONS (Automatically fades out in Focus Mode)VIGATION BUTTONS (Automatically fades out in Focus Mode)
                            AnimatedVisibility(
                                visible = !isKeyboardVisible,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { currentStep = 2 },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Add Exercises")
                                    }

                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            currentStep = 4
                                        },
                                        enabled = routineName.isNotBlank() && selectedExercises.isNotEmpty() && selectedExercises.all { it.sets.isNotEmpty() },
                                        modifier = Modifier.weight(1.5f).height(48.dp).testTag("routine_editor_review_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Review Routine ➔", fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // STEP 4: REVIEW & SAVE
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Routine Summary",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )

                            // Main stats grid card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = routineName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Exercises", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${selectedExercises.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Total Sets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$totalSets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Est. Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${estDurationMin}m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Target Volume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(com.example.core.util.UnitConverter.formatWeight(totalVolume.toDouble(), isMetric), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Focus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(trainingFocus, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        Column {
                                            Text("Difficulty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(difficulty, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                }
                            }

                            // Muscle Volume Distribution Chart
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "Target Muscle Distribution",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (totalSets == 0) {
                                        Text("No muscles targeted.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        musclesCount.forEach { (muscle, count) ->
                                            val pct = count.toFloat() / selectedExercises.size
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(muscle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                    Text("${(pct * 100).toInt()}%  ($count sets)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                                }
                                                LinearProgressIndicator(
                                                    progress = { pct.coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Save controls
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val trimmedName = routineName.trim()
                                        if (trimmedName.isEmpty()) {
                                            routineNameError = "Routine name cannot be blank."
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } else if (selectedExercises.isEmpty()) {
                                            Toast.makeText(context, "Please add at least one exercise.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.saveTemplate(
                                                templateId = template?.id,
                                                name = trimmedName,
                                                exercises = selectedExercises.toList()
                                            )
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_reviewed_routine_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Save Routine", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                                }

                                if (template != null) {
                                    OutlinedButton(
                                        onClick = {
                                            val trimmedName = routineName.trim()
                                            if (trimmedName.isEmpty()) {
                                                routineNameError = "Routine name cannot be blank."
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            } else if (selectedExercises.isEmpty()) {
                                                Toast.makeText(context, "Please add at least one exercise.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.saveTemplate(
                                                    templateId = null, // Saves as a new copy
                                                    name = "$trimmedName (Copy)",
                                                    exercises = selectedExercises.toList()
                                                )
                                                onDismiss()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_reviewed_routine_as_copy_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Save As Copy", fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = { currentStep = 3 },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Back to Editor")
                                }
                            }
                        }
                    }
                }
            }

            pairingDialogIndex?.let { pIdx ->
                PairingDialog(
                    exerciseIndex = pIdx,
                    selectedExercises = selectedExercises,
                    exercises = exercises,
                    onGroupSelected = { newGroupId ->
                        selectedExercises[pIdx] = selectedExercises[pIdx].copy(supersetGroupId = newGroupId)
                        pairingDialogIndex = null
                    },
                    onPairWithExercise = { targetIdx ->
                        val targetEx = selectedExercises[targetIdx]
                        val groupId = if (!targetEx.supersetGroupId.isNullOrEmpty()) {
                            targetEx.supersetGroupId
                        } else {
                            val newGroupId = java.util.UUID.randomUUID().toString()
                            selectedExercises[targetIdx] = targetEx.copy(supersetGroupId = newGroupId)
                            newGroupId
                        }
                        selectedExercises[pIdx] = selectedExercises[pIdx].copy(supersetGroupId = groupId)
                        pairingDialogIndex = null
                    },
                    onDismiss = { pairingDialogIndex = null }
                )
            }

            showRestConfigGroupIndex?.let { groupId ->
                GroupRestConfigDialog(
                    groupId = groupId,
                    selectedExercises = selectedExercises,
                    onRestChanged = { newRest ->
                        selectedExercises.forEachIndexed { sIdx, se ->
                            if (se.supersetGroupId == groupId) {
                                selectedExercises[sIdx] = se.copy(restSeconds = newRest)
                            }
                        }
                    },
                    onDismiss = { showRestConfigGroupIndex = null }
                )
            }
        }
    }

@Composable
fun PairingDialog(
    exerciseIndex: Int,
    selectedExercises: List<TemplateExerciseState>,
    exercises: List<Exercise>,
    onGroupSelected: (String) -> Unit,
    onPairWithExercise: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentEx = selectedExercises[exerciseIndex]
    val currentExObj = exercises.find { it.id == currentEx.exerciseId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Link / Superset Grouping",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Group \"${currentExObj?.name ?: "this exercise"}\" into a Superset or Circuit with another exercise in this routine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val otherExercises = selectedExercises.mapIndexed { idx, ex -> idx to ex }
                    .filter { it.first != exerciseIndex }

                if (otherExercises.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Add more exercises to this routine first to create a superset.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "SELECT AN EXERCISE TO PAIR WITH:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(otherExercises) { (idx, otherEx) ->
                            val otherExObj = exercises.find { it.id == otherEx.exerciseId }
                            if (otherExObj != null) {
                                val isInExistingGroup = !otherEx.supersetGroupId.isNullOrEmpty()
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onPairWithExercise(idx)
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${idx + 1}. ${otherExObj.name}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isInExistingGroup) {
                                                Text(
                                                    text = "Part of an active group",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF00E5FF),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = "Link",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun GroupRestConfigDialog(
    groupId: String,
    selectedExercises: List<TemplateExerciseState>,
    onRestChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val groupExs = selectedExercises.filter { it.supersetGroupId == groupId }
    val initialRest = groupExs.firstOrNull()?.restSeconds ?: 90
    var sliderValue by remember { mutableStateOf(initialRest.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Group Post-Round Rest",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Set a synchronized post-round rest period for all exercises in this group.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${sliderValue.toInt()} seconds",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "(${sliderValue.toInt() / 60}m ${sliderValue.toInt() % 60}s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..300f,
                    steps = 59, // 5-second increments
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(30, 60, 90, 120, 180).forEach { secs ->
                        TextButton(
                            onClick = { sliderValue = secs.toFloat() },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("${secs}s", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRestChanged(sliderValue.toInt())
                    onDismiss()
                }
            ) {
                Text("Apply to Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun ExerciseEditorCard(
    index: Int,
    templateExercise: TemplateExerciseState,
    exercise: Exercise,
    isMetric: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (TemplateExerciseState) -> Unit,
    totalExercises: Int
) {
    var isExpanded by remember { mutableStateOf(true) }
    var showSetEditor by remember { mutableStateOf(false) }
    var editingSetIndex by remember { mutableStateOf<Int?>(null) }
    var editingSetState by remember { mutableStateOf<TemplateSetState?>(null) }
    
    var showDeleteConfirmationIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(exercise.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                exercise.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            val setsCount = templateExercise.sets.size
                            Text(
                                text = if (setsCount == 1) "1 Set" else "$setsCount Sets",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (setsCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = index < totalExercises - 1, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Exercise", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isExpanded) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RestDurationControl(
                        restSeconds = templateExercise.restSeconds,
                        onRestSecondsChange = { newRest ->
                            onUpdate(templateExercise.copy(restSeconds = newRest))
                        }
                    )

                    OutlinedTextField(
                        value = templateExercise.notes ?: "",
                        onValueChange = {
                            onUpdate(templateExercise.copy(notes = it.ifBlank { null }))
                        },
                        label = { Text("Exercise Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Prescribed Sets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (templateExercise.sets.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Text("1 set required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (templateExercise.sets.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "No sets configured yet",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Text(
                            "Apply a quick preset shortcut:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val applyPreset: (String) -> Unit = { preset ->
                                val newSets = when (preset) {
                                    "3 x 8-10" -> listOf(
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 10),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 10),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 10)
                                    )
                                    "5 x 5" -> listOf(
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5)
                                    )
                                    "4 x 12" -> listOf(
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 12, targetRepsMax = 12),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 12, targetRepsMax = 12),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 12, targetRepsMax = 12),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 12, targetRepsMax = 12)
                                    )
                                    "Warm-up + working sets" -> listOf(
                                        TemplateSetState(setType = "WARMUP", targetRepsMin = 10, targetRepsMax = 12),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 12),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 12),
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 12)
                                    )
                                    "Timed set" -> listOf(
                                        TemplateSetState(setType = "WORKING", targetDurationSeconds = 60, targetRepsMin = null, targetRepsMax = null)
                                    )
                                    "AMRAP" -> listOf(
                                        TemplateSetState(setType = "WORKING", targetRepsMin = 1, targetRepsMax = null, notes = "AMRAP")
                                    )
                                    else -> emptyList()
                                }
                                onUpdate(templateExercise.copy(sets = newSets))
                            }

                            listOf("3 x 8-10", "5 x 5", "4 x 12", "Warm-up + working sets", "Timed set", "AMRAP").forEach { preset ->
                                AssistChip(
                                    onClick = { applyPreset(preset) },
                                    label = { Text(preset, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        templateExercise.sets.forEachIndexed { sIndex, set ->
                            CompactSetRow(
                                setIndex = sIndex + 1,
                                set = set,
                                isMetric = isMetric,
                                onEdit = {
                                    editingSetIndex = sIndex
                                    editingSetState = set
                                    showSetEditor = true
                                },
                                onDelete = {
                                    val hasData = set.targetWeight != null || set.targetRpe != null || !set.notes.isNullOrBlank() || set.targetDurationSeconds != null || set.targetDistance != null
                                    if (hasData) {
                                        showDeleteConfirmationIndex = sIndex
                                    } else {
                                        val updated = templateExercise.sets.toMutableList()
                                        updated.removeAt(sIndex)
                                        onUpdate(templateExercise.copy(sets = updated))
                                    }
                                },
                                onDuplicate = {
                                    val updated = templateExercise.sets.toMutableList()
                                    updated.add(sIndex + 1, set.copy(id = 0))
                                    onUpdate(templateExercise.copy(sets = updated))
                                },
                                onMoveUp = {
                                    if (sIndex > 0) {
                                        val updated = templateExercise.sets.toMutableList()
                                        val temp = updated[sIndex]
                                        updated[sIndex] = updated[sIndex - 1]
                                        updated[sIndex - 1] = temp
                                        onUpdate(templateExercise.copy(sets = updated))
                                    }
                                },
                                onMoveDown = {
                                    if (sIndex < templateExercise.sets.size - 1) {
                                        val updated = templateExercise.sets.toMutableList()
                                        val temp = updated[sIndex]
                                        updated[sIndex] = updated[sIndex + 1]
                                        updated[sIndex + 1] = temp
                                        onUpdate(templateExercise.copy(sets = updated))
                                    }
                                },
                                isFirst = sIndex == 0,
                                isLast = sIndex == templateExercise.sets.size - 1
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val haptic = LocalHapticFeedback.current
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val lastSet = templateExercise.sets.lastOrNull()
                            val newSet = TemplateSetState(
                                setType = lastSet?.setType ?: "WORKING",
                                targetRepsMin = lastSet?.targetRepsMin ?: 8,
                                targetRepsMax = lastSet?.targetRepsMax ?: 10,
                                targetWeight = lastSet?.targetWeight,
                                targetRpe = lastSet?.targetRpe,
                                targetDurationSeconds = lastSet?.targetDurationSeconds,
                                targetDistance = lastSet?.targetDistance,
                                tempo = lastSet?.tempo,
                                notes = lastSet?.notes
                            )
                            val updated = templateExercise.sets + newSet
                            onUpdate(templateExercise.copy(sets = updated))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_set_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Set", fontWeight = FontWeight.Bold)
                    }

                    if (templateExercise.sets.isNotEmpty()) {
                        var showPresetsDropdown by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { showPresetsDropdown = !showPresetsDropdown }) {
                                Text("Preset Shortcuts", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            DropdownMenu(
                                expanded = showPresetsDropdown,
                                onDismissRequest = { showPresetsDropdown = false }
                            ) {
                                val applyPresetToExisting: (String) -> Unit = { preset ->
                                    val newSets = when (preset) {
                                        "3 x 8-10" -> listOf(
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 10),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 10),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 10)
                                        )
                                        "5 x 5" -> listOf(
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 5, targetRepsMax = 5)
                                        )
                                        "4 x 12" -> listOf(
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 12, targetRepsMax = 12),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 12, targetRepsMax = 12),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 12, targetRepsMax = 12),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 12, targetRepsMax = 12)
                                        )
                                        "Warm-up + working sets" -> listOf(
                                            TemplateSetState(setType = "WARMUP", targetRepsMin = 10, targetRepsMax = 12),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 12),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 12),
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 8, targetRepsMax = 12)
                                        )
                                        "Timed set" -> listOf(
                                            TemplateSetState(setType = "WORKING", targetDurationSeconds = 60, targetRepsMin = null, targetRepsMax = null)
                                        )
                                        "AMRAP" -> listOf(
                                            TemplateSetState(setType = "WORKING", targetRepsMin = 1, targetRepsMax = null, notes = "AMRAP")
                                        )
                                        else -> emptyList()
                                    }
                                    val updated = templateExercise.sets.toMutableList()
                                    updated.addAll(newSets)
                                    onUpdate(templateExercise.copy(sets = updated))
                                    showPresetsDropdown = false
                                }

                                listOf("3 x 8-10", "5 x 5", "4 x 12", "Warm-up + working sets", "Timed set", "AMRAP").forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset) },
                                        onClick = { applyPresetToExisting(preset) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val setsCount = templateExercise.sets.size
                    val summaryText = if (setsCount == 0) {
                        "No sets configured yet"
                    } else {
                        val setPreviews = templateExercise.sets.take(2).mapIndexed { idx, s ->
                            val repsPart = if (s.targetRepsMin != null && s.targetRepsMax != null) "${s.targetRepsMin}-${s.targetRepsMax}" else s.targetRepsMin ?: s.targetRepsMax ?: "?"
                            val weightPart = if (s.targetWeight != null) " @ ${com.example.core.util.UnitConverter.formatWeight(s.targetWeight.toDouble(), isMetric)}" else ""
                            "${repsPart}${weightPart}"
                        }
                        val suffix = if (setsCount > 2) ", ..." else ""
                        "Sets: " + setPreviews.joinToString(", ") + suffix
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (setsCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rest: ${templateExercise.restSeconds}s",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSetEditor && editingSetState != null) {
        TemplateSetEditorDialog(
            set = if (editingSetIndex != null) editingSetState else null,
            isMetric = isMetric,
            onDismiss = {
                showSetEditor = false
                editingSetState = null
                editingSetIndex = null
            },
            onSave = { savedSet ->
                val updated = templateExercise.sets.toMutableList()
                val idx = editingSetIndex
                if (idx != null) {
                    updated[idx] = savedSet
                } else {
                    updated.add(savedSet)
                }
                onUpdate(templateExercise.copy(sets = updated))
                showSetEditor = false
                editingSetState = null
                editingSetIndex = null
            },
            onDelete = if (editingSetIndex != null) {
                {
                    val updated = templateExercise.sets.toMutableList()
                    val idx = editingSetIndex
                    if (idx != null) {
                        updated.removeAt(idx)
                    }
                    onUpdate(templateExercise.copy(sets = updated))
                    showSetEditor = false
                    editingSetState = null
                    editingSetIndex = null
                }
            } else null
        )
    }

    if (showDeleteConfirmationIndex != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationIndex = null },
            title = { Text("Delete Prescribed Set?") },
            text = { Text("This set has configured target data. Are you sure you want to remove it?") },
            confirmButton = {
                Button(
                    onClick = {
                        val idx = showDeleteConfirmationIndex
                        if (idx != null) {
                            val updated = templateExercise.sets.toMutableList()
                            updated.removeAt(idx)
                            onUpdate(templateExercise.copy(sets = updated))
                        }
                        showDeleteConfirmationIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SetTypeBadge(setType: String) {
    val bgColor: Color
    val textColor: Color
    val label: String
    val icon: ImageVector

    when (setType) {
        "WARMUP" -> {
            bgColor = Color(0xFFFFF3E0)
            textColor = Color(0xFFE65100)
            label = "Warm-up"
            icon = Icons.Default.TrendingUp
        }
        "DROP" -> {
            bgColor = Color(0xFFF3E5F5)
            textColor = Color(0xFF4A148C)
            label = "Drop Set"
            icon = Icons.Default.TrendingDown
        }
        "FAILURE" -> {
            bgColor = Color(0xFFFFEBEE)
            textColor = Color(0xFFB71C1C)
            label = "Failure"
            icon = Icons.Default.ErrorOutline
        }
        "AMRAP" -> {
            bgColor = Color(0xFFE8F5E9)
            textColor = Color(0xFF1B5E20)
            label = "AMRAP"
            icon = Icons.Default.DirectionsRun
        }
        "TIMED" -> {
            bgColor = Color(0xFFE0F7FA)
            textColor = Color(0xFF006064)
            label = "Timed"
            icon = Icons.Default.Timer
        }
        "DISTANCE" -> {
            bgColor = Color(0xFFE8EAF6)
            textColor = Color(0xFF1A237E)
            label = "Distance"
            icon = Icons.Default.Map
        }
        else -> {
            bgColor = Color(0xFFE3F2FD)
            textColor = Color(0xFF0D47A1)
            label = "Working"
            icon = Icons.Default.FitnessCenter
        }
    }
    
    Row(
        modifier = Modifier
            .background(bgColor, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
fun CompactSetRow(
    setIndex: Int,
    set: TemplateSetState,
    isMetric: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Set $setIndex",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    SetTypeBadge(set.setType)
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                val details = mutableListOf<String>()
                
                val repsStr = when {
                    set.targetRepsMin != null && set.targetRepsMax != null -> "${set.targetRepsMin}-${set.targetRepsMax} reps"
                    set.targetRepsMin != null -> "${set.targetRepsMin}+ reps"
                    set.targetRepsMax != null -> "${set.targetRepsMax} reps"
                    else -> null
                }
                if (repsStr != null) details.add(repsStr)
                
                if (set.targetWeight != null) details.add(com.example.core.util.UnitConverter.formatWeight(set.targetWeight.toDouble(), isMetric))
                if (set.targetRpe != null) details.add("RPE ${set.targetRpe}")
                if (set.targetDurationSeconds != null) details.add("${set.targetDurationSeconds}s")
                if (set.targetDistance != null) details.add("${set.targetDistance}m")
                if (!set.tempo.isNullOrBlank()) details.add("Tempo: ${set.tempo}")
                if (!set.notes.isNullOrBlank()) details.add("Note: ${set.notes}")
                
                Text(
                    text = if (details.isNotEmpty()) details.joinToString(" • ") else "No specific targets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMoveUp()
                    },
                    enabled = !isFirst,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (!isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMoveDown()
                    },
                    enabled = !isLast,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (!isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDuplicate()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Duplicate Set",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Set",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TemplateSetEditorDialog(
    set: TemplateSetState?,
    isMetric: Boolean,
    onDismiss: () -> Unit,
    onSave: (TemplateSetState) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var setType by remember { mutableStateOf(set?.setType ?: "WORKING") }
    var targetRepsMin by remember { mutableStateOf(set?.targetRepsMin?.toString() ?: "") }
    var targetRepsMax by remember { mutableStateOf(set?.targetRepsMax?.toString() ?: "") }
    var targetWeight by remember {
        mutableStateOf(
            if (set?.targetWeight != null) {
                com.example.core.util.UnitConverter.formatWeightValueOnly(set.targetWeight.toDouble(), isMetric)
            } else {
                ""
            }
        )
    }
    var targetRpe by remember { mutableStateOf(set?.targetRpe?.toString() ?: "") }
    var targetDurationSeconds by remember { mutableStateOf(set?.targetDurationSeconds?.toString() ?: "") }
    var targetDistance by remember { mutableStateOf(set?.targetDistance?.toString() ?: "") }
    var tempo by remember { mutableStateOf(set?.tempo ?: "") }
    var notes by remember { mutableStateOf(set?.notes ?: "") }

    val repsMinVal = targetRepsMin.toIntOrNull()
    val repsMaxVal = targetRepsMax.toIntOrNull()
    val weightVal = targetWeight.toFloatOrNull()
    val rpeVal = targetRpe.toIntOrNull()
    val durationVal = targetDurationSeconds.toIntOrNull()
    val distanceVal = targetDistance.toFloatOrNull()

    val isRepsMinValid = targetRepsMin.isEmpty() || (repsMinVal != null && repsMinVal >= 0)
    val isRepsMaxValid = targetRepsMax.isEmpty() || (repsMaxVal != null && repsMaxVal >= 0)
    val isRepRangeValid = if (repsMinVal != null && repsMaxVal != null) repsMaxVal >= repsMinVal else true
    val isWeightValid = targetWeight.isEmpty() || (weightVal != null && weightVal >= 0f)
    val isRpeValid = targetRpe.isEmpty() || (rpeVal != null && rpeVal in 1..10)
    val isDurationValid = targetDurationSeconds.isEmpty() || (durationVal != null && durationVal > 0)
    val isDistanceValid = targetDistance.isEmpty() || (distanceVal != null && distanceVal > 0f)

    val isFormValid = isRepsMinValid && isRepsMaxValid && isRepRangeValid && isWeightValid && isRpeValid && isDurationValid && isDistanceValid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (set == null) "Add Set" else "Edit Set",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Set Type", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("WARMUP", "WORKING", "DROP", "FAILURE", "AMRAP", "TIMED", "DISTANCE").forEach { type ->
                            val isSelected = setType == type
                            val color = when (type) {
                                "WARMUP" -> Color(0xFFFF9800)
                                "DROP" -> Color(0xFF9C27B0)
                                "FAILURE" -> Color(0xFFE91E63)
                                "AMRAP" -> Color(0xFF4CAF50)
                                "TIMED" -> Color(0xFF00BCD4)
                                "DISTANCE" -> Color(0xFF3F51B5)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { setType = type },
                                label = { Text(type, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color.copy(alpha = 0.15f),
                                    selectedLabelColor = color,
                                    selectedLeadingIconColor = color
                                ),
                                border = if (isSelected) BorderStroke(1.5.dp, color) else FilterChipDefaults.filterChipBorder(enabled = true, selected = false)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = targetRepsMin,
                        onValueChange = { targetRepsMin = it },
                        label = { Text("Min Reps") },
                        modifier = Modifier.weight(1f),
                        isError = !isRepsMinValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetRepsMax,
                        onValueChange = { targetRepsMax = it },
                        label = { Text("Max Reps") },
                        modifier = Modifier.weight(1f),
                        isError = !isRepsMaxValid || !isRepRangeValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                
                if (!isRepRangeValid) {
                    Text(
                        "Max reps must be greater than or equal to min reps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = targetWeight,
                        onValueChange = { targetWeight = it },
                        label = { Text(if (isMetric) "Weight (kg)" else "Weight (lb)") },
                        modifier = Modifier.weight(1f),
                        isError = !isWeightValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetRpe,
                        onValueChange = { targetRpe = it },
                        label = { Text("Target RPE") },
                        modifier = Modifier.weight(1f),
                        isError = !isRpeValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                if (!isRpeValid) {
                    Text("RPE must be between 1 and 10.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (!isWeightValid) {
                    Text("Weight cannot be negative.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                var showAdvanced by remember { mutableStateOf(!targetDurationSeconds.isEmpty() || !targetDistance.isEmpty() || !tempo.isEmpty()) }
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showAdvanced) "Hide Advanced Settings" else "Show Advanced Settings (Duration, Distance, Tempo)")
                }

                if (showAdvanced) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = targetDurationSeconds,
                            onValueChange = { targetDurationSeconds = it },
                            label = { Text("Duration (s)") },
                            modifier = Modifier.weight(1f),
                            isError = !isDurationValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = targetDistance,
                            onValueChange = { targetDistance = it },
                            label = { Text("Distance (m)") },
                            modifier = Modifier.weight(1f),
                            isError = !isDistanceValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    if (!isDurationValid) {
                        Text("Duration must be a positive number.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (!isDistanceValid) {
                        Text("Distance must be a positive number.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    OutlinedTextField(
                        value = tempo,
                        onValueChange = { tempo = it },
                        label = { Text("Tempo (e.g. 3010)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes for this set") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    if (onDelete != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete Set")
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (isFormValid) {
                                val parsedWeight = targetWeight.toFloatOrNull()
                                val weightInKg = if (parsedWeight != null) {
                                    if (isMetric) parsedWeight else com.example.core.util.UnitConverter.lbToKg(parsedWeight.toDouble()).toFloat()
                                } else {
                                    null
                                }
                                val saved = TemplateSetState(
                                    id = set?.id ?: 0,
                                    setType = setType,
                                    targetRepsMin = targetRepsMin.toIntOrNull(),
                                    targetRepsMax = targetRepsMax.toIntOrNull(),
                                    targetWeight = weightInKg,
                                    targetRpe = targetRpe.toIntOrNull(),
                                    targetDurationSeconds = targetDurationSeconds.toIntOrNull(),
                                    targetDistance = targetDistance.toFloatOrNull(),
                                    tempo = tempo.ifBlank { null },
                                    notes = notes.ifBlank { null }
                                )
                                onSave(saved)
                            }
                        },
                        enabled = isFormValid,
                        modifier = Modifier.testTag("save_set_details_button")
                    ) {
                        Text("Save Set")
                    }
                }
            }
        }
    }
}

@Composable
fun HighDensityHeader(
    title: String,
    userProfile: UserProfile?,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    val isNull = userProfile == null
    val displayName = userProfile?.displayName
    val email = userProfile?.email
    val initials = userProfile?.initials ?: "U"
    android.util.Log.d("HighDensityHeader", "Rendered header: title=$title, isNull=$isNull, displayName=$displayName, email=$email, initials=$initials")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.human_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "HUMAN V1",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (actions != null) {
                actions()
            }
            // Dynamic Avatar circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                var imageLoaded by remember { mutableStateOf(false) }

                if (!userProfile?.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = userProfile?.photoUrl,
                        contentDescription = "Profile Photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        onSuccess = { imageLoaded = true },
                        onError = { imageLoaded = false }
                    )
                }

                if (userProfile?.photoUrl.isNullOrBlank() || !imageLoaded) {
                    Text(
                        text = userProfile?.initials ?: "U",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun RestDurationControl(
    restSeconds: Int,
    onRestSecondsChange: (Int) -> Unit
) {
    val presets = listOf(60, 75, 90, 120, 150, 180)
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "REST",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(36.dp)
        )
        
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrement button with 48dp target
            item {
                IconButton(
                    onClick = { onRestSecondsChange((restSeconds - 15).coerceAtLeast(15)) },
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            items(presets) { sec ->
                val isSelected = restSeconds == sec
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .widthIn(min = 52.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onRestSecondsChange(sec) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${sec}s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Custom display if not in presets
            if (restSeconds !in presets) {
                item {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .widthIn(min = 58.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${restSeconds}s",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            // Increment button with 48dp target
            item {
                IconButton(
                    onClick = { onRestSecondsChange((restSeconds + 15).coerceAtMost(600)) },
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

data class SupersetAnalysis(val message: String, val isGood: Boolean)

fun analyzeSuperset(exercises: List<Exercise>): SupersetAnalysis {
    if (exercises.size < 2) return SupersetAnalysis("", true)

    val categories = exercises.map { it.category.lowercase() }
    val names = exercises.map { it.name.lowercase() }

    // Check same rare equipment (setup friction warning)
    val barbellCount = names.count { it.contains("barbell") }
    val cableCount = names.count { it.contains("cable") }
    if (barbellCount >= 2) {
        return SupersetAnalysis("⚠️ Gym Friction: Both exercises require a Barbell. Setup may be slow in busy gyms.", false)
    }
    if (cableCount >= 2) {
        return SupersetAnalysis("⚠️ Gym Friction: Multiple Cable exercises. Consider cable-to-dumbbell transitions to save time.", false)
    }

    // Check agonist/antagonist pairing (excellent synergy)
    val hasChest = categories.any { it.contains("chest") }
    val hasBack = categories.any { it.contains("back") }
    val hasBiceps = categories.any { it.contains("biceps") || it.contains("arms") }
    val hasTriceps = categories.any { it.contains("triceps") || it.contains("arms") }
    val hasQuads = categories.any { it.contains("quads") || it.contains("legs") }
    val hasHamstrings = categories.any { it.contains("hamstrings") || it.contains("legs") }

    if (hasChest && hasBack) {
        return SupersetAnalysis("🔥 Excellent pairing! Chest (Push) & Back (Pull) agonist-antagonist setup maximizes blood flow & recovery.", true)
    }
    if (hasBiceps && hasTriceps) {
        return SupersetAnalysis("🔥 Excellent pairing! Biceps & Triceps antagonist coupling drives massive arm pump and time-efficiency.", true)
    }
    if (hasQuads && hasHamstrings) {
        return SupersetAnalysis("🔥 Smart pairing! Quads & Hamstrings grouping balances leg development with minimal central fatigue.", true)
    }

    // Check same muscle category (high intensity fatigue tip)
    val uniqueCategories = categories.distinct()
    if (uniqueCategories.size == 1) {
        return SupersetAnalysis("💪 High Intensity: Compound-to-isolation drop set detected. Expect high local muscle fatigue.", true)
    }

    return SupersetAnalysis("💡 Smart circuit choice! Alternating different muscle categories allows active recovery between exercises.", true)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SupersetNestedExerciseCard(
    templateExercise: TemplateExerciseState,
    exerciseObj: com.example.data.Exercise,
    exIndex: Int,
    supersetLabels: Map<Int, String>,
    isMetric: Boolean,
    onUpdate: (TemplateExerciseState) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    activeFocusedExerciseIndex: Int?,
    onFocusedChange: (Int?) -> Unit,
    isKeyboardVisible: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    expandedStrategies: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Boolean>
) {
    val isFocusedEx = activeFocusedExerciseIndex == exIndex
    val shouldCollapseEx = isKeyboardVisible && !isFocusedEx
    val label = supersetLabels[exIndex]
    val intention = remember(templateExercise.notes) { ExerciseIntention.fromSerializedString(templateExercise.notes) }

    if (shouldCollapseEx) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.55f)
                .clickable { onFocusedChange(exIndex) },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!label.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF),
                                fontSize = 9.sp
                            )
                        }
                    }
                    Text(exerciseObj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Reps: ${intention.targetRepsMin}-${intention.targetRepsMax}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                width = if (isFocusedEx) 1.5.dp else 1.dp,
                color = if (isFocusedEx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!label.isNullOrEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                            }
                            Text(exerciseObj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(exerciseObj.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ArrowUpward, "Move Up", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ArrowDownward, "Move Down", modifier = Modifier.size(16.dp))
                        }
                        
                        var showCardMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showCardMenu = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.MoreVert, "More Options", modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = showCardMenu,
                                onDismissRequest = { showCardMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remove from Superset") },
                                    leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showCardMenu = false
                                        onUpdate(templateExercise.copy(supersetGroupId = null))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Exercise", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showCardMenu = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }

                val isStrategyExpanded = expandedStrategies[exIndex] ?: false
                val rotationAngle by animateFloatAsState(targetValue = if (isStrategyExpanded) 90f else 0f)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedStrategies[exIndex] = !isStrategyExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exercise Strategy & Rest Guide",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = if (isStrategyExpanded) "Collapse strategy" else "Expand strategy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(rotationAngle)
                    )
                }

                AnimatedVisibility(
                    visible = isStrategyExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = intention.userNotes,
                            onValueChange = {
                                val updated = intention.copy(userNotes = it)
                                onUpdate(templateExercise.copy(notes = updated.toSerializedString()))
                            },
                            label = { Text("Exercise Notes", fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        onFocusedChange(exIndex)
                                    }
                                },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("TRAINING GOAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val goals = listOf(
                                    Triple("Strength", "Strength", Icons.Default.FitnessCenter),
                                    Triple("Hypertrophy", "Muscle", Icons.Default.TrendingUp),
                                    Triple("Endurance", "Stamina", Icons.Default.Timer),
                                    Triple("Technique", "Form", Icons.Default.Psychology),
                                    Triple("Custom", "Custom", Icons.Default.Edit)
                                )
                                goals.forEach { (goalId, label, icon) ->
                                    val isSelected = intention.goal == goalId
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val updated = intention.copy(goal = goalId)
                                            onUpdate(templateExercise.copy(notes = updated.toSerializedString()))
                                        },
                                        label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("PROGRESSION STYLE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val progressions = listOf("Straight Sets", "Ramp Up", "Reverse Pyramid", "Pyramid", "Custom")
                                progressions.forEach { style ->
                                    val isSelected = intention.progression == style
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val updated = intention.copy(progression = style)
                                            onUpdate(templateExercise.copy(notes = updated.toSerializedString()))
                                        },
                                        label = { Text(style, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column 1: REPS TARGET (min and max controls inside a solid grey container)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "REP TARGET RANGE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${intention.targetRepsMin} - ${intention.targetRepsMax}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Min reps adjust row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Min:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val minReps = (intention.targetRepsMin - 1).coerceAtLeast(1)
                                        val updated = intention.copy(targetRepsMin = minReps)
                                        val setsList = templateExercise.sets.map { it.copy(targetRepsMin = minReps) }
                                        onUpdate(templateExercise.copy(
                                            notes = updated.toSerializedString(),
                                            sets = setsList
                                        ))
                                    },
                                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                ) {
                                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                                }
                                Text("${intention.targetRepsMin}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val minReps = (intention.targetRepsMin + 1).coerceAtMost(intention.targetRepsMax)
                                        val updated = intention.copy(targetRepsMin = minReps)
                                        val setsList = templateExercise.sets.map { it.copy(targetRepsMin = minReps) }
                                        onUpdate(templateExercise.copy(
                                            notes = updated.toSerializedString(),
                                            sets = setsList
                                        ))
                                    },
                                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Max reps adjust row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Max:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val maxReps = (intention.targetRepsMax - 1).coerceAtLeast(intention.targetRepsMin)
                                        val updated = intention.copy(targetRepsMax = maxReps)
                                        val setsList = templateExercise.sets.map { it.copy(targetRepsMax = maxReps) }
                                        onUpdate(templateExercise.copy(
                                            notes = updated.toSerializedString(),
                                            sets = setsList
                                        ))
                                    },
                                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                ) {
                                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                                }
                                Text("${intention.targetRepsMax}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val maxReps = (intention.targetRepsMax + 1).coerceIn(1, 100)
                                        val updated = intention.copy(targetRepsMax = maxReps)
                                        val setsList = templateExercise.sets.map { it.copy(targetRepsMax = maxReps) }
                                        onUpdate(templateExercise.copy(
                                            notes = updated.toSerializedString(),
                                            sets = setsList
                                        ))
                                    },
                                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Column 2: START WEIGHT (with increment/decrement buttons inside a solid grey container)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "START WEIGHT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(18.dp)) // Aligns nicely with reps layout height
                        Text(
                            text = if (intention.startingWeight != null) com.example.core.util.UnitConverter.formatWeight(intention.startingWeight.toDouble(), isMetric) else "--",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val currentW = intention.startingWeight ?: 20f
                                    val delta = if (isMetric) 2.5f else (5f * com.example.core.util.UnitConverter.LB_TO_KG).toFloat()
                                    val newW = (currentW - delta).coerceAtLeast(0f)
                                    val updated = intention.copy(startingWeight = newW)
                                    val setsList = templateExercise.sets.map { it.copy(targetWeight = newW) }
                                    onUpdate(templateExercise.copy(
                                        notes = updated.toSerializedString(),
                                        sets = setsList
                                    ))
                                },
                                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, null, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val currentW = intention.startingWeight ?: 20f
                                    val delta = if (isMetric) 2.5f else (5f * com.example.core.util.UnitConverter.LB_TO_KG).toFloat()
                                    val newW = currentW + delta
                                    val updated = intention.copy(startingWeight = newW)
                                    val setsList = templateExercise.sets.map { it.copy(targetWeight = newW) }
                                    onUpdate(templateExercise.copy(
                                        notes = updated.toSerializedString(),
                                        sets = setsList
                                    ))
                                },
                                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}


