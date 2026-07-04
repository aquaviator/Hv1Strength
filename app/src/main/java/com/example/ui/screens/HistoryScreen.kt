package com.example.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Exercise
import com.example.data.WorkoutSession
import com.example.ui.viewmodel.EnrichedSession
import com.example.ui.viewmodel.StrengthViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: StrengthViewModel) {
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

    Scaffold(
        topBar = {
            HighDensityHeader(title = "History") {
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
                    placeholder = { Text("Search workouts or exercises...") },
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
                            "No matching workouts",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Try modifying your filter settings, search query, or date range options above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredSessions) { enriched ->
                        HistorySessionCard(
                            enriched = enriched,
                            viewModel = viewModel,
                            exercises = exercises,
                            onDeleteSession = { viewModel.deleteSession(enriched.session.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_session_card_${enriched.session.id}"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        enriched.session.templateName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Duration: ${enriched.durationMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Volume: ${String.format("%.1f", enriched.totalVolume)} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
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
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Exercise Summaries List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                exercisesPerformed.take(if (isExpanded) exercisesPerformed.size else 3).forEach { exercise ->
                    val exerciseSets = enriched.sets.filter { it.exerciseId == exercise.id }
                    val maxWeight = exerciseSets.maxOfOrNull { it.weight } ?: 0f
                    val maxRepsSet = exerciseSets.maxByOrNull { it.weight }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${exerciseSets.size}x ${exercise.name}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (maxWeight > 0) "Best: $maxWeight kg x ${maxRepsSet?.reps ?: 0}" else "Logged",
                            style = MaterialTheme.typography.bodyMedium,
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
                                .padding(start = 16.dp, top = 2.dp, bottom = 6.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            exerciseSets.forEach { set ->
                                val rpeText = if (set.rpe != null) "   (RPE ${set.rpe})" else ""
                                val setTypeLabel = if (set.setType != "WORKING") " [${set.setType}]" else ""
                                
                                val actualPerformance = if (set.actualDuration != null || set.actualDistance != null) {
                                    val durationStr = if (set.actualDuration != null) "${set.actualDuration}s" else ""
                                    val distanceStr = if (set.actualDistance != null) "${set.actualDistance} km" else ""
                                    listOf(durationStr, distanceStr).filter { it.isNotEmpty() }.joinToString(" • ")
                                } else {
                                    "${set.weight} kg   ×   ${set.reps} reps"
                                }

                                val targetList = mutableListOf<String>()
                                if (set.targetWeight != null) targetList.add("${set.targetWeight} kg")
                                if (set.targetRepsMin != null) {
                                    val repsStr = if (set.targetRepsMax != null && set.targetRepsMax != set.targetRepsMin) "${set.targetRepsMin}-${set.targetRepsMax}" else "${set.targetRepsMin}"
                                    targetList.add("$repsStr reps")
                                }
                                if (set.targetRpe != null) targetList.add("RPE ${set.targetRpe}")
                                if (set.targetDuration != null) targetList.add("${set.targetDuration}s")
                                if (set.targetDistance != null) targetList.add("${set.targetDistance} km")
                                
                                val targetText = if (targetList.isNotEmpty()) "   (Target: ${targetList.joinToString(" • ")})" else ""
                                val notesText = if (!set.notes.isNullOrEmpty()) "   • Note: ${set.notes}" else ""

                                Text(
                                    "Set ${set.setNumber}$setTypeLabel:   $actualPerformance$rpeText$targetText$notesText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                if (exercisesPerformed.size > 3 && !isExpanded) {
                    Text(
                        "+ ${exercisesPerformed.size - 3} more exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
