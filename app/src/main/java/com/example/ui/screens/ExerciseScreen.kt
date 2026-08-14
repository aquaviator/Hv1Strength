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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var selectedCategories by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedMuscles by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedEquipment by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedCapabilities by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedSource by rememberSaveable { mutableStateOf(ExerciseSource.ALL) }
    var selectedSection by rememberSaveable { mutableStateOf(LibrarySection.ALL) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var draftCategories by remember { mutableStateOf(emptyList<String>()) }
    var draftMuscles by remember { mutableStateOf(emptyList<String>()) }
    var draftEquipment by remember { mutableStateOf(emptyList<String>()) }
    var draftCapabilities by remember { mutableStateOf(emptyList<String>()) }
    var draftSource by remember { mutableStateOf(ExerciseSource.ALL) }
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

            val activeFilterCount = selectedCategories.size + selectedMuscles.size + selectedEquipment.size + selectedCapabilities.size +
                (if (selectedSource == ExerciseSource.ALL) 0 else 1) + (if (selectedSection == LibrarySection.ALL) 0 else 1)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    draftCategories = selectedCategories; draftMuscles = selectedMuscles; draftEquipment = selectedEquipment
                    draftCapabilities = selectedCapabilities; draftSource = selectedSource; showFilters = true
                }, modifier = Modifier.testTag("open_exercise_filters")) {
                    Icon(Icons.Default.FilterList, contentDescription = null); Spacer(Modifier.width(8.dp))
                    Text(if (activeFilterCount == 0) "Filters" else "Filters ($activeFilterCount)")
                }
                if (activeFilterCount > 0) Text("$activeFilterCount active", style = MaterialTheme.typography.labelMedium)
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
            val filteredExercises = remember(searchQuery, selectedCategories, selectedMuscles, selectedEquipment, selectedCapabilities, selectedSource, selectedSection, exercises, catalogue, sessions, allLoggedSets, favoriteExercises) {
                discoverExercises(exercises, catalogueById, favoriteExercises, recentExerciseIds(sessions, allLoggedSets),
                    ExerciseDiscoveryFilters(query = searchQuery, categories = selectedCategories.toSet(), muscles = selectedMuscles.toSet(),
                        equipmentSelections = selectedEquipment.toSet(), capabilities = selectedCapabilities.mapNotNull { value -> MeasurementCapability.entries.find { it.wireName == value } }.toSet(),
                        source = selectedSource, section = selectedSection))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${filteredExercises.size} exercises", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("exercise_result_count"))
                TextButton(onClick = { selectedCategories = emptyList(); selectedMuscles = emptyList(); selectedEquipment = emptyList(); selectedCapabilities = emptyList(); selectedSource = ExerciseSource.ALL; selectedSection = LibrarySection.ALL }) { Text("Clear filters") }
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
                            "Try editing or clearing filters. Your search text will be kept.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { selectedCategories = emptyList(); selectedMuscles = emptyList(); selectedEquipment = emptyList(); selectedCapabilities = emptyList(); selectedSource = ExerciseSource.ALL; selectedSection = LibrarySection.ALL }, modifier = Modifier.testTag("empty_clear_filters")) { Text("Clear filters") }
                        OutlinedButton(onClick = {
                            draftCategories = selectedCategories
                            draftMuscles = selectedMuscles
                            draftEquipment = selectedEquipment
                            draftCapabilities = selectedCapabilities
                            draftSource = selectedSource
                            showFilters = true
                        }, modifier = Modifier.testTag("empty_edit_filters")) { Text("Edit filters") }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredExercises, key = { it.id }) { exercise ->
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

    if (showFilters) {
        val categories = remember(catalogue) { catalogue.exercises.map { it.category }.distinct().sorted() }
        val muscles = remember(catalogue) { catalogue.exercises.flatMap { it.primaryMuscles + it.secondaryMuscles }.distinct().sorted() }
        val equipment = remember(catalogue) { catalogue.exercises.flatMap { it.equipment }.distinct().sorted() }
        ModalBottomSheet(onDismissRequest = { showFilters = false }, modifier = Modifier.testTag("exercise_filter_sheet")) {
            Column(modifier = Modifier.fillMaxHeight(0.7f).padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text("Filter exercises", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Choices within a group match any; different groups combine together.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(modifier = Modifier.weight(1f).testTag("exercise_filter_options"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { FilterOptionGroup("Source", ExerciseSource.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) }, listOf(draftSource.name.lowercase().replaceFirstChar(Char::uppercase))) { value -> draftSource = ExerciseSource.valueOf(value.uppercase()) } }
                    item { FilterOptionGroup("Category", categories, draftCategories) { value -> draftCategories = toggleChoice(draftCategories, value) } }
                    item { FilterOptionGroup("Muscles", muscles, draftMuscles) { value -> draftMuscles = toggleChoice(draftMuscles, value) } }
                    item { FilterOptionGroup("Equipment", equipment, draftEquipment) { value -> draftEquipment = toggleChoice(draftEquipment, value) } }
                    item { FilterOptionGroup("Tracking", MeasurementCapability.entries.map { it.wireName }, draftCapabilities) { value -> draftCapabilities = toggleChoice(draftCapabilities, value) } }
                    item { Spacer(Modifier.height(12.dp)); Text("End of filter options", modifier = Modifier.testTag("exercise_filter_last_option")) }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { draftCategories = emptyList(); draftMuscles = emptyList(); draftEquipment = emptyList(); draftCapabilities = emptyList(); draftSource = ExerciseSource.ALL }, modifier = Modifier.testTag("clear_all_exercise_filters")) { Text("Clear all") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showFilters = false }) { Text("Cancel") }
                    Button(onClick = {
                        selectedCategories = draftCategories; selectedMuscles = draftMuscles; selectedEquipment = draftEquipment
                        selectedCapabilities = draftCapabilities; selectedSource = draftSource; showFilters = false
                    }, modifier = Modifier.testTag("apply_exercise_filters")) { Text("Apply") }
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

    selectedExerciseForHistory?.let { exercise ->
        val logs by remember(exercise.id) { viewModel.getCompletedSetsForExercise(exercise.id) }.collectAsState(initial = emptyList())
        val governed = catalogueById[exercise.id]
        val details = exerciseDetails(exercise, governed)
        val related = remember(governed, catalogue) { governed?.let { relatedExercises(it, catalogue.exercises) } ?: emptyList() }
        Dialog(onDismissRequest = { selectedExerciseForHistory = null }) {
            Surface(Modifier.fillMaxSize().testTag("exercise_detail_screen"), color = MaterialTheme.colorScheme.background) {
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { Text(exercise.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(details.source, color = MaterialTheme.colorScheme.primary) }; IconButton(onClick = { selectedExerciseForHistory = null }) { Icon(Icons.Default.Close, "Close exercise details") } } }
                    item { OutlinedButton(onClick = { viewModel.toggleFavoriteExercise(exercise.id) }, Modifier.fillMaxWidth()) { Icon(if (exercise.id in favoriteExercises) Icons.Default.Star else Icons.Default.StarBorder, null); Spacer(Modifier.width(8.dp)); Text(if (exercise.id in favoriteExercises) "Remove favourite" else "Add favourite") } }
                    item { DetailSection("Overview", listOf("Category" to details.category, "Movement pattern" to details.movementPattern, "Equipment" to details.equipment.joinToString().ifBlank { "Not provided" }, "Primary muscles" to details.primaryMuscles.joinToString().ifBlank { "Not provided" }, "Secondary muscles" to details.secondaryMuscles.joinToString().ifBlank { "None specified" }, "Tracking" to details.measurements.joinToString(), "Laterality" to details.laterality)) }
                    item { DetailTextSection("Setup", details.setup ?: "Setup instructions are not available for this custom exercise.") }
                    item { DetailListSection("Execution", details.steps, true) }
                    item { DetailTextSection("Breathing", details.breathing ?: "Breathing guidance is not available for this custom exercise.") }
                    item { DetailListSection("Technique cues", details.cues) }
                    item { DetailListSection("Common mistakes", details.mistakes) }
                    item { DetailTextSection("Safety", details.safety ?: "No governed safety notes are available. Use a comfortable range and seek qualified guidance when needed.") }
                    item { Text("Previous performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    if (logs.isEmpty()) item { Text("No logged history for this exercise.") }
                    items(logs.groupBy { it.sessionId }.toList(), key = { it.first }) { (sessionId, sets) ->
                        val session = sessions.find { it.id == sessionId }
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)).padding(12.dp)) { Text(session?.templateName ?: "Workout", fontWeight = FontWeight.Bold); Text(sets.joinToString(" · ") { "${com.example.core.util.UnitConverter.formatWeight(it.weight.toDouble(), isMetric)} × ${it.reps}" }) }
                    }
                    item { Text("Related exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    if (related.isEmpty()) item { Text("No governed related exercises are available.") }
                    items(related, key = { "related_${it.id}" }) { item -> OutlinedButton(onClick = { selectedExerciseForHistory = exercises.firstOrNull { it.id == item.id } ?: item.toRoom(0) }, Modifier.fillMaxWidth()) { Text(item.name, Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null) } }
                    item { Text("End of exercise details", modifier = Modifier.testTag("exercise_detail_last_section")); Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // Retained legacy implementation is unreachable while the V32 full-page detail is active.
    if (false && selectedExerciseForHistory != null) {
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

@Composable
private fun DetailTextSection(title: String, text: String) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(text)
}
@Composable
private fun DetailListSection(title: String, values: List<String>, numbered: Boolean = false) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (values.isEmpty()) Text("Not available for this custom exercise.") else values.forEachIndexed { index, value -> Text(if (numbered) "${index + 1}. $value" else "• $value") }
}
@Composable
private fun DetailSection(title: String, values: List<Pair<String, String>>) = Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("exercise_details_metadata")) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); values.forEach { (label, value) -> Text("$label: $value") }
}

private fun toggleChoice(current: List<String>, value: String): List<String> =
    if (value in current) current - value else (current + value).distinct()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterOptionGroup(title: String, options: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.sortedWith(compareByDescending<String> { it in selected }.thenBy { normalize(listOf(it)) }).forEach { option ->
                FilterChip(selected = option in selected, onClick = { onToggle(option) }, label = { Text(option.replace('_', ' ').replaceFirstChar(Char::uppercase)) })
            }
        }
    }
}
