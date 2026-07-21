package com.example.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Exercise
import com.example.data.LoggedSet
import com.example.ui.viewmodel.EnrichedSession
import com.example.ui.viewmodel.StrengthViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: StrengthViewModel,
    onNavigateToProfile: () -> Unit = {}
) {
    val filteredSessions by viewModel.filteredSessions.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val templates by viewModel.templates.collectAsState()

    // Filter properties states
    val searchQuery by viewModel.historySearchQuery.collectAsState()
    val selectedSort by viewModel.historySelectedSort.collectAsState()
    val dateRange by viewModel.historyDateRange.collectAsState()
    val selectedExerciseId by viewModel.historySelectedExerciseId.collectAsState()
    val selectedRoutineName by viewModel.historySelectedRoutineName.collectAsState()

    var showFilterPanel by remember { mutableStateOf(false) }

    val userProfile by viewModel.activeUserProfile.collectAsState()

    Scaffold(
        topBar = {
            HighDensityHeader(
                title = "Coaching Dashboard",
                userProfile = userProfile,
                onProfileClick = onNavigateToProfile
            ) {
                IconButton(
                    modifier = Modifier.testTag("toggle_filters_button"),
                    onClick = { showFilterPanel = !showFilterPanel }
                ) {
                    Icon(
                        imageVector = if (showFilterPanel) Icons.Default.FilterListOff else Icons.Default.FilterList,
                        contentDescription = "Toggle Filters",
                        tint = if (showFilterPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search and Filter Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search bar Always visible
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.historySearchQuery.value = it },
                    placeholder = { Text("Search workouts, coach notes, or exercises...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input"),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.historySearchQuery.value = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Expandable Filter Section
                AnimatedVisibility(
                    visible = showFilterPanel,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Filters & Sorting",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Sort and Date dropdown row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sort Dropdown
                            var sortExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { sortExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Sort: $selectedSort",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                    listOf("Newest", "Oldest", "Highest Volume", "Longest Session").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                viewModel.historySelectedSort.value = option
                                                sortExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Date Range Dropdown
                            var dateExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { dateExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Date: $dateRange",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = dateExpanded, onDismissRequest = { dateExpanded = false }) {
                                    listOf("All", "Last 7 Days", "Last 30 Days", "Last 90 Days").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                viewModel.historyDateRange.value = option
                                                dateExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Exercise and Routine dropdown row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Exercise Filter
                            var exerciseExpanded by remember { mutableStateOf(false) }
                            val currentExerciseName = exercises.find { it.id == selectedExerciseId }?.name ?: "All Exercises"
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { exerciseExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = currentExerciseName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = exerciseExpanded, onDismissRequest = { exerciseExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("All Exercises") },
                                        onClick = {
                                            viewModel.historySelectedExerciseId.value = null
                                            exerciseExpanded = false
                                        }
                                    )
                                    exercises.forEach { ex ->
                                        DropdownMenuItem(
                                            text = { Text(ex.name) },
                                            onClick = {
                                                viewModel.historySelectedExerciseId.value = ex.id
                                                exerciseExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Routine Filter
                            var routineExpanded by remember { mutableStateOf(false) }
                            val currentRoutineName = selectedRoutineName ?: "All Routines"
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { routineExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = currentRoutineName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = routineExpanded, onDismissRequest = { routineExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("All Routines") },
                                        onClick = {
                                            viewModel.historySelectedRoutineName.value = null
                                            routineExpanded = false
                                        }
                                    )
                                    templates.map { it.name }.distinct().forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                viewModel.historySelectedRoutineName.value = name
                                                routineExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Clear button if any filter is active
                        val isAnyFilterActive = searchQuery.isNotEmpty() || selectedSort != "Newest" || dateRange != "All" || selectedExerciseId != null || selectedRoutineName != null
                        if (isAnyFilterActive) {
                            TextButton(
                                onClick = {
                                    viewModel.historySearchQuery.value = ""
                                    viewModel.historySelectedSort.value = "Newest"
                                    viewModel.historyDateRange.value = "All"
                                    viewModel.historySelectedExerciseId.value = null
                                    viewModel.historySelectedRoutineName.value = null
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Filters", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = "No results found",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            "No matching coaching insights",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "We couldn't find any workouts with those filter constraints. Try resetting filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val allLoggedSetsState by viewModel.allLoggedSets.collectAsState()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(filteredSessions) { enriched ->
                        HistorySessionCard(
                            enriched = enriched,
                            viewModel = viewModel,
                            exercises = exercises,
                            allLoggedSets = allLoggedSetsState,
                            onDeleteSession = { viewModel.deleteSession(enriched.session.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HistorySessionCard(
    enriched: EnrichedSession,
    viewModel: StrengthViewModel,
    exercises: List<Exercise>,
    allLoggedSets: List<LoggedSet>,
    onDeleteSession: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val formattedDate = remember(enriched.session.startTime) {
        val calendar = Calendar.getInstance().apply { timeInMillis = enriched.session.startTime }
        DateFormat.format("EEEE, MMM d, yyyy", calendar).toString()
    }

    val exercisesPerformed = remember(enriched.sets, exercises) {
        enriched.sets.map { it.exerciseId }.distinct().mapNotNull { id -> exercises.find { it.id == id } }
    }

    // Dynamic recovery recommendation based on average session RPE
    val averageSessionRpe = remember(enriched.sets) {
        val completed = enriched.sets.filter { it.isCompleted }
        val rpes = completed.mapNotNull { it.rpe }
        if (rpes.isNotEmpty()) rpes.average().toFloat() else null
    }

    val recoveryTime = remember(averageSessionRpe) {
        when {
            averageSessionRpe == null -> "36 hours (Standard Recovery)"
            averageSessionRpe <= 7.0f -> "24 hours (Light Effort — Fast Recovery)"
            averageSessionRpe <= 8.5f -> "48 hours (Optimal Intensity — Standard Recovery)"
            else -> "72 hours (High Exertion — Deep CNS Recovery Required)"
        }
    }

    // Generate intelligent story comparisons for each exercise
    val deconstructionInsights = remember(enriched.sets, allLoggedSets, exercisesPerformed) {
        exercisesPerformed.map { exercise ->
            val todaySets = enriched.sets.filter { it.exerciseId == exercise.id && it.isCompleted }
            
            // Find the most recent session containing this exercise *before* the current session
            val priorSets = allLoggedSets
                .filter { it.exerciseId == exercise.id && it.isCompleted && it.sessionId != enriched.session.id && it.createdAt < enriched.session.startTime }
                .groupBy { it.sessionId }
            
            val lastSessionId = priorSets.keys.maxOrNull()
            val previousSets = if (lastSessionId != null) priorSets[lastSessionId] ?: emptyList() else emptyList()

            val todayWeightsStr = todaySets.map { it.weight.toString().removeSuffix(".0") }.joinToString(", ")
            val previousWeightsStr = if (previousSets.isNotEmpty()) {
                previousSets.map { it.weight.toString().removeSuffix(".0") }.joinToString(", ")
            } else {
                "None"
            }

            // Compare performance metrics
            val todayMaxWeight = todaySets.maxOfOrNull { it.weight } ?: 0f
            val previousMaxWeight = previousSets.maxOfOrNull { it.weight } ?: 0f
            val todayMaxReps = todaySets.maxOfOrNull { it.reps } ?: 0
            val previousMaxReps = previousSets.maxOfOrNull { it.reps } ?: 0

            val isNewPersonalBest = todayMaxWeight > previousMaxWeight && previousMaxWeight > 0f
            val weightDiff = todayMaxWeight - previousMaxWeight

            val progressStatus = when {
                previousMaxWeight == 0f -> "Benchmark Set"
                isNewPersonalBest -> "New Personal Best!"
                weightDiff > 0 -> "+${weightDiff.toString().removeSuffix(".0")} kg Progress"
                weightDiff < 0 -> "${weightDiff.toString().removeSuffix(".0")} kg Deload"
                todayMaxReps > previousMaxReps -> "+${todayMaxReps - previousMaxReps} Reps Progress"
                else -> "Solid Consistency"
            }

            ExerciseStory(
                exerciseName = exercise.name,
                previousWeights = previousWeightsStr,
                todayWeights = todayWeightsStr,
                progressLabel = progressStatus,
                isImprovement = isNewPersonalBest || weightDiff > 0 || (weightDiff == 0f && todayMaxReps > previousMaxReps)
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_session_card_${enriched.session.id}"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Story of the Workout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        enriched.session.templateName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Timer, contentDescription = "Duration", size = 12.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${enriched.durationMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = "Volume", size = 12.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Volume: ${String.format("%.1f", enriched.totalVolume).removeSuffix(".0")} kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDeleteSession,
                        modifier = Modifier.testTag("delete_history_session_${enriched.session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete from history",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // COACHING INSIGHTS SECTION (Tells the Story of the Workout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = "Coaching", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        "Athlete Insights",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 1. What did I improve? / What went well
                val improvements = deconstructionInsights.filter { it.isImprovement }
                if (improvements.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "WHAT YOU IMPROVED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        improvements.forEach { imp ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text(imp.exerciseName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    imp.progressLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Consistent", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text(
                            "Solid Consolidated Session. Focus on perfect technique and pacing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 2. Recovery & Future Progression Advice
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "COACH SUGGESTIONS & RECOVERY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = "Recovery", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Recovery recommendation: $recoveryTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val suggestedProg = improvements.firstOrNull()?.let {
                        "Next session: Increase ${it.exerciseName} by +2.5 kg to trigger further overload."
                    } ?: "Next session: Focus on increasing target reps by +1 on the final sets."
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = "Progression", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text(
                            text = suggestedProg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Exercise Summaries List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercisesPerformed.take(if (isExpanded) exercisesPerformed.size else 3).forEach { exercise ->
                    val exerciseSets = enriched.sets.filter { it.exerciseId == exercise.id }
                    val maxWeight = exerciseSets.maxOfOrNull { it.weight } ?: 0f
                    val maxRepsSet = exerciseSets.maxByOrNull { it.weight }

                    val insightsForEx = deconstructionInsights.find { it.exerciseName == exercise.name }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${exerciseSets.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                exercise.name,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            if (maxWeight > 0) "${maxWeight.toString().removeSuffix(".0")} kg × ${maxRepsSet?.reps ?: 0}" else "Logged",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Expanded detail of each set
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 32.dp, top = 4.dp, bottom = 8.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (insightsForEx != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Previous weights:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(insightsForEx.previousWeights, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Today's weights:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(insightsForEx.todayWeights, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                            }

                            exerciseSets.forEach { set ->
                                val rpeText = if (set.rpe != null) "  •  RPE ${set.rpe}" else ""
                                val setTypeLabel = if (set.setType != "WORKING") " [${set.setType}]" else ""
                                
                                val actualPerformance = if (set.actualDuration != null || set.actualDistance != null) {
                                    val durationStr = if (set.actualDuration != null) "${set.actualDuration}s" else ""
                                    val distanceStr = if (set.actualDistance != null) "${set.actualDistance} km" else ""
                                    listOf(durationStr, distanceStr).filter { it.isNotEmpty() }.joinToString(" • ")
                                } else {
                                    "${set.weight.toString().removeSuffix(".0")} kg  ×  ${set.reps} reps"
                                }

                                val targetList = mutableListOf<String>()
                                if (set.targetWeight != null && set.targetWeight > 0f) targetList.add("${set.targetWeight.toString().removeSuffix(".0")} kg")
                                if (set.targetRepsMin != null) {
                                    val repsStr = if (set.targetRepsMax != null && set.targetRepsMax != set.targetRepsMin) "${set.targetRepsMin}-${set.targetRepsMax}" else "${set.targetRepsMin}"
                                    targetList.add("$repsStr reps")
                                }
                                val targetText = if (targetList.isNotEmpty()) "  (Target: ${targetList.joinToString(" • ")})" else ""

                                Text(
                                    "Set ${set.setNumber}$setTypeLabel:  $actualPerformance$rpeText$targetText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (exercisesPerformed.size > 3 && !isExpanded) {
                    Text(
                        "+ ${exercisesPerformed.size - 3} more exercises performed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Icon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, size: androidx.compose.ui.unit.Dp, tint: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

data class ExerciseStory(
    val exerciseName: String,
    val previousWeights: String,
    val todayWeights: String,
    val progressLabel: String,
    val isImprovement: Boolean
)
