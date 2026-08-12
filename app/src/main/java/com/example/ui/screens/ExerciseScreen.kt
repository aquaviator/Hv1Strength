package com.example.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.Exercise
import com.example.data.LoggedSet
import com.example.data.WorkoutSession
import com.example.data.UserProfile
import com.example.catalogue.*
import com.example.ui.viewmodel.StrengthViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    viewModel: StrengthViewModel,
    onNavigateToProfile: () -> Unit = {}
) {
    val exercises by viewModel.exercises.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val allLoggedSets by viewModel.allLoggedSets.collectAsState()
    val favoriteExercises by viewModel.favoriteExercises.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedEquipment by remember { mutableStateOf("All") }
    var selectedCapability by remember { mutableStateOf<MeasurementCapability?>(null) }
    var showCustomOnly by remember { mutableStateOf<Boolean?>(null) }
    var selectedSection by rememberSaveable { mutableStateOf(LibrarySection.ALL) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val catalogue = remember { ExerciseCatalogueRuntime.snapshot ?: ExerciseCatalogueRuntime.load(context) }
    val catalogueById = remember(catalogue) { catalogue.exercises.associateBy { it.id } }
    val cataloguePrefs = remember { context.getSharedPreferences("strength_catalogue", android.content.Context.MODE_PRIVATE) }
    val lastReconciled = remember(catalogue) { cataloguePrefs.getLong("last_reconciled", 0L) }

    var showCreateExerciseDialog by remember { mutableStateOf(false) }
    var customExerciseName by remember { mutableStateOf("") }
    var customExerciseCategory by remember { mutableStateOf("Chest") }
    var customTrackingProfile by remember { mutableStateOf(com.example.catalogue.CustomTrackingProfile.REPS_LOAD) }

    var selectedExerciseForHistory by remember { mutableStateOf<Exercise?>(null) }

    val userProfile by viewModel.activeUserProfile.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()

    Scaffold(
        topBar = {
            HighDensityHeader(
                title = "Exercises",
                userProfile = userProfile,
                onProfileClick = onNavigateToProfile,
                actions = {
                    IconButton(
                        onClick = {
                            customExerciseName = ""
                            customExerciseCategory = "Chest"
                            showCreateExerciseDialog = true
                        },
                        modifier = Modifier.testTag("create_exercise_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Exercise", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search exercise database...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exercise_search_input"),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                }
            )

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySection.entries.forEach { section ->
                    FilterChip(selected = selectedSection == section, onClick = { selectedSection = section },
                        label = { Text(section.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.testTag("exercise_section_${section.name.lowercase()}"))
                }
            }

            // Muscle Category Pill Tabs
            val categories = remember(catalogue) { listOf("All") + catalogue.exercises.map { it.category }.distinct().sorted() }
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

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "barbell", "dumbbell", "kettlebell", "cable", "machine", "bodyweight", "resistance band").forEach { value ->
                    FilterChip(selected = selectedEquipment == value, onClick = { selectedEquipment = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = showCustomOnly == null, onClick = { showCustomOnly = null }, label = { Text("All") })
                FilterChip(selected = showCustomOnly == false, onClick = { showCustomOnly = false }, label = { Text("Built-in") })
                FilterChip(selected = showCustomOnly == true, onClick = { showCustomOnly = true }, label = { Text("Custom") })
                FilterChip(selected = selectedCapability == MeasurementCapability.DURATION,
                    onClick = { selectedCapability = if (selectedCapability == MeasurementCapability.DURATION) null else MeasurementCapability.DURATION },
                    label = { Text("Timed") })
            }

            Text(
                "Catalogue ${catalogue.metadata.catalogueVersion} · ${catalogue.metadata.exerciseCount} exercises · ${catalogue.metadata.sourceId} · " +
                    (if (catalogue.validation.valid) "Verified" else "Fallback active") +
                    (if (lastReconciled > 0L) " · Updated ${android.text.format.DateFormat.getMediumDateFormat(context).format(java.util.Date(lastReconciled))}" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = if (catalogue.validation.valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("catalogue_diagnostics")
            )

            // Exercise List
            val filteredExercises = remember(searchQuery, selectedCategory, selectedEquipment, selectedCapability, showCustomOnly, selectedSection, exercises, catalogue, sessions, allLoggedSets, favoriteExercises) {
                discoverExercises(exercises, catalogueById, favoriteExercises, recentExerciseIds(sessions, allLoggedSets),
                    ExerciseDiscoveryFilters(query = searchQuery, category = selectedCategory.takeUnless { it == "All" },
                        equipment = selectedEquipment.takeUnless { it == "All" }, capability = selectedCapability,
                        section = if (showCustomOnly == true) LibrarySection.CUSTOM else selectedSection))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${filteredExercises.size} exercises", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("exercise_result_count"))
                TextButton(onClick = { searchQuery = ""; selectedCategory = "All"; selectedEquipment = "All"; selectedCapability = null; showCustomOnly = null; selectedSection = LibrarySection.ALL }) { Text("Clear filters") }
            }

            if (filteredExercises.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = "No exercises",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "No matching exercises.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Create a custom one using the '+' button at the top.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredExercises) { exercise ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedExerciseForHistory = exercise }
                                .testTag("exercise_card_${exercise.id}"),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val isFav = favoriteExercises.contains(exercise.id)
                                    IconButton(
                                        onClick = {
                                            viewModel.toggleFavoriteExercise(exercise.id)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Favorite",
                                            tint = if (isFav) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                exercise.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (exercise.isCustom) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text("Custom", style = MaterialTheme.typography.bodySmall) },
                                                    modifier = Modifier.height(20.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            exercise.category,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "History",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "History Details",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // Create Custom Exercise Dialog
    if (showCreateExerciseDialog) {
        Dialog(onDismissRequest = { showCreateExerciseDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "New Custom Exercise",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )

                    OutlinedTextField(
                        value = customExerciseName,
                        onValueChange = { customExerciseName = it },
                        label = { Text("Exercise Name (e.g. Incline Bench Press)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_exercise_name_input"),
                        singleLine = true
                    )

                    Text(
                        "Select Muscle Group Category",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    val categoriesList = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Abs")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column {
                            // Split list into 2 columns of chips for ergonomics
                            categoriesList.chunked(3).forEach { rowCats ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rowCats.forEach { cat ->
                                        FilterChip(
                                            selected = customExerciseCategory == cat,
                                            onClick = { customExerciseCategory = cat },
                                            label = { Text(cat) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text("Track", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        com.example.catalogue.CustomTrackingProfile.entries.chunked(2).forEach { profiles ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                profiles.forEach { profile ->
                                    FilterChip(selected = customTrackingProfile == profile, onClick = { customTrackingProfile = profile }, label = { Text(profile.label) })
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateExerciseDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (customExerciseName.isNotBlank()) {
                                    viewModel.createCustomExercise(
                                        customExerciseName,
                                        customExerciseCategory,
                                        customTrackingProfile
                                    )
                                    showCreateExerciseDialog = false
                                }
                            },
                            enabled = customExerciseName.isNotBlank(),
                            modifier = Modifier.testTag("save_custom_exercise_button")
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Exercise History / Previous Weights Dialog
    if (selectedExerciseForHistory != null) {
        val exercise = selectedExerciseForHistory!!
        val logsFlow = remember(exercise.id) { viewModel.getCompletedSetsForExercise(exercise.id) }
        val logs by logsFlow.collectAsState(initial = emptyList())

        Dialog(onDismissRequest = { selectedExerciseForHistory = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp),
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
                        Column {
                            Text(
                                exercise.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "Category: ${exercise.category}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { selectedExerciseForHistory = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    val details = exerciseDetails(exercise, catalogueById[exercise.id])
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("exercise_details_metadata")) {
                        Text(details.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Primary: ${details.primaryMuscles.joinToString().ifBlank { "Not specified" }}", style = MaterialTheme.typography.bodySmall)
                        if (details.secondaryMuscles.isNotEmpty()) Text("Secondary: ${details.secondaryMuscles.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        Text("Equipment: ${details.equipment.joinToString().ifBlank { "No equipment specified" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Tracks: ${details.measurements.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        Text("Movement: ${details.laterality.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall)
                        details.replacementId?.let { Text("Replacement available: ${catalogueById[it]?.name ?: it}", style = MaterialTheme.typography.bodySmall) }
                    }
                    OutlinedButton(onClick = { viewModel.toggleFavoriteExercise(exercise.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (favoriteExercises.contains(exercise.id)) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text(if (favoriteExercises.contains(exercise.id)) "Remove favourite" else "Add favourite")
                    }

                    Text(
                        "Previous Weights & Sets Logs",
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
                                "No logged history for this exercise.",
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
                                    val cal = Calendar.getInstance().apply { timeInMillis = sessionDate }
                                    DateFormat.format("MMM d, yyyy", cal).toString()
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
                                            sessionName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            dateStr,
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
                                                        "${com.example.core.util.UnitConverter.formatWeight(set.weight.toDouble(), isMetric)} × ${set.reps}",
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
