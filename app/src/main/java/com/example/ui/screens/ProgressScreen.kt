package com.example.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.viewmodel.StrengthViewModel
import kotlinx.coroutines.flow.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: StrengthViewModel,
    onNavigateToProfile: () -> Unit = {}
) {
    val bodyWeights by viewModel.bodyWeights.collectAsState()
    val tapeMeasurements by viewModel.tapeMeasurements.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val sessions by viewModel.sessions.collectAsState()

    var activeTab by remember { mutableStateOf("Weight") }

    // Weight Logging Fields
    var weightInput by remember { mutableStateOf("") }

    // Tape Logging Fields
    var chestInput by remember { mutableStateOf("") }
    var waistInput by remember { mutableStateOf("") }
    var hipsInput by remember { mutableStateOf("") }
    var bicepLeftInput by remember { mutableStateOf("") }
    var bicepRightInput by remember { mutableStateOf("") }
    var thighLeftInput by remember { mutableStateOf("") }
    var thighRightInput by remember { mutableStateOf("") }

    // Measurement Site Selection for Charting
    var selectedTapeSite by remember { mutableStateOf("Waist") }

    // Exercise Selection for Strength Charting
    var selectedExerciseForChart by remember { mutableStateOf<Exercise?>(null) }
    var showExerciseSelectionDialog by remember { mutableStateOf(false) }

    // Set default exercise for chart on launch
    LaunchedEffect(exercises) {
        if (selectedExerciseForChart == null && exercises.isNotEmpty()) {
            selectedExerciseForChart = exercises.find { it.id == "bench_press" } ?: exercises.first()
        }
    }

    val userProfile by viewModel.activeUserProfile.collectAsState()

    Scaffold(
        topBar = {
            HighDensityHeader(
                title = "Progress",
                userProfile = userProfile,
                onProfileClick = onNavigateToProfile
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
            val streakStats by viewModel.streakStats.collectAsState()

            // Streak & Calendar Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "STREAK ENGINE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                "🔥 ${streakStats.currentStreak} Days",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Current Streak",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column {
                                Text(
                                    "🏆 ${streakStats.longestStreak}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Longest Streak",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column {
                                Text(
                                    "${String.format("%.1f", streakStats.monthlyConsistencyPct)}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "This Month",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Calendar Grid Column
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val calendar = Calendar.getInstance()
                        val year = calendar.get(Calendar.YEAR)
                        val month = calendar.get(Calendar.MONTH)
                        val monthMaxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""

                        Text(
                            "$monthName $year ACTIVITY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        val paddingCalendar = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                        val firstDayOfWeek = paddingCalendar.get(Calendar.DAY_OF_WEEK)
                        val paddingOffset = (firstDayOfWeek - 1) % 7

                        val totalCells = paddingOffset + monthMaxDays
                        val rowsCount = (totalCells + 6) / 7

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Days headers
                            val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                dayHeaders.forEach { header ->
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.width(18.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            for (r in 0 until rowsCount) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    for (c in 0 until 7) {
                                        val cellIndex = r * 7 + c
                                        val dayOfMonth = cellIndex - paddingOffset + 1
                                        if (dayOfMonth in 1..monthMaxDays) {
                                            val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                            val isWorkedOut = streakStats.workoutDates.contains(dateStr)

                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (isWorkedOut) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isWorkedOut) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dayOfMonth.toString(),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                    color = if (isWorkedOut) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (isWorkedOut) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Main Selector Row (Weight, Tape, Strength)
            TabRow(
                selectedTabIndex = when (activeTab) {
                    "Weight" -> 0
                    "Tape" -> 1
                    else -> 2
                },
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = activeTab == "Weight",
                    onClick = { activeTab = "Weight" },
                    text = { Text("Weight", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == "Tape",
                    onClick = { activeTab = "Tape" },
                    text = { Text("Tape Measure", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == "Strength",
                    onClick = { activeTab = "Strength" },
                    text = { Text("Strength", fontWeight = FontWeight.Bold) }
                )
            }

            when (activeTab) {
                "Weight" -> {
                    WeightProgressTab(
                        bodyWeights = bodyWeights,
                        weightInput = weightInput,
                        onWeightInputChange = { weightInput = it },
                        onLogWeight = { w, bf ->
                            viewModel.logBodyWeight(w, bf)
                        },
                        onDeleteWeight = { id -> viewModel.deleteBodyWeight(id) }
                    )
                }
                "Tape" -> {
                    TapeProgressTab(
                        measurements = tapeMeasurements,
                        selectedSite = selectedTapeSite,
                        onSelectSite = { selectedTapeSite = it },
                        chestInput = chestInput,
                        onChestChange = { chestInput = it },
                        waistInput = waistInput,
                        onWaistChange = { waistInput = it },
                        hipsInput = hipsInput,
                        onHipsChange = { hipsInput = it },
                        bicepLeftInput = bicepLeftInput,
                        onBicepLeftChange = { bicepLeftInput = it },
                        bicepRightInput = bicepRightInput,
                        onBicepRightChange = { bicepRightInput = it },
                        thighLeftInput = thighLeftInput,
                        onThighLeftChange = { thighLeftInput = it },
                        thighRightInput = thighRightInput,
                        onThighRightChange = { thighRightInput = it },
                        onLogMeasurements = {
                            viewModel.logTapeMeasurement(
                                chest = chestInput.toFloatOrNull(),
                                waist = waistInput.toFloatOrNull(),
                                hips = hipsInput.toFloatOrNull(),
                                bicepLeft = bicepLeftInput.toFloatOrNull(),
                                bicepRight = bicepRightInput.toFloatOrNull(),
                                thighLeft = thighLeftInput.toFloatOrNull(),
                                thighRight = thighRightInput.toFloatOrNull()
                            )
                            chestInput = ""
                            waistInput = ""
                            hipsInput = ""
                            bicepLeftInput = ""
                            bicepRightInput = ""
                            thighLeftInput = ""
                            thighRightInput = ""
                        },
                        onDeleteMeasurement = { id -> viewModel.deleteTapeMeasurement(id) }
                    )
                }
                "Strength" -> {
                    if (selectedExerciseForChart != null) {
                        val completedSetsFlow = remember(selectedExerciseForChart!!.id) {
                            viewModel.getCompletedSetsForExercise(selectedExerciseForChart!!.id)
                        }
                        val completedSets by completedSetsFlow.collectAsState(initial = emptyList())

                        StrengthProgressTab(
                            selectedExercise = selectedExerciseForChart!!,
                            completedSets = completedSets,
                            sessions = sessions,
                            onSelectExerciseClick = { showExerciseSelectionDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Exercise Selection Dialog for Strength progress
    if (showExerciseSelectionDialog) {
        Dialog(onDismissRequest = { showExerciseSelectionDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Select Exercise to Track",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )

                    Divider()

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(exercises) { exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedExerciseForChart = exercise
                                        showExerciseSelectionDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(exercise.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        exercise.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showExerciseSelectionDialog = false }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeightProgressTab(
    bodyWeights: List<BodyWeight>,
    weightInput: String,
    onWeightInputChange: (String) -> Unit,
    onLogWeight: (weight: Float, bodyFat: Float?) -> Unit,
    onDeleteWeight: (Int) -> Unit
) {
    var bodyFatInput by remember { mutableStateOf("") }
    var selectedMetricToPlot by remember { mutableStateOf("Weight") } // "Weight", "Body Fat", "BMI"

    val sortedWeights = remember(bodyWeights) {
        bodyWeights.sortedBy { it.date }
    }

    val chartData = remember(sortedWeights, selectedMetricToPlot) {
        sortedWeights.mapNotNull {
            val value = when (selectedMetricToPlot) {
                "Weight" -> it.weight
                "Body Fat" -> it.bodyFat
                "BMI" -> it.bmi?.toFloat()
                else -> null
            }
            if (value != null && value > 0f) Pair(it.date, value) else null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Entry Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Record Body Composition",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = onWeightInputChange,
                            label = { Text("Weight") },
                            suffix = { Text("kg") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("weight_progress_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = bodyFatInput,
                            onValueChange = { bodyFatInput = it },
                            label = { Text("Body Fat") },
                            suffix = { Text("%") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("body_fat_progress_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    Button(
                        onClick = {
                            val w = weightInput.toFloatOrNull()
                            val bf = bodyFatInput.toFloatOrNull()
                            if (w != null) {
                                onLogWeight(w, bf)
                                onWeightInputChange("")
                                bodyFatInput = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_weight_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Record Entry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Line Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        Text(
                            "Progression Trend",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Selector Row for plot metric
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Weight", "Body Fat", "BMI").forEach { metric ->
                                val isSelected = selectedMetricToPlot == metric
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { selectedMetricToPlot = metric }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = metric,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (chartData.size < 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Add 2 or more logs with $selectedMetricToPlot to populate trend.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        StrengthLineChart(
                            dataPoints = chartData,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                    }
                }
            }
        }

        // History Entries
        item {
            Text(
                "Composition Logs",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (bodyWeights.isEmpty()) {
            item {
                Text(
                    "No composition metrics recorded yet.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(bodyWeights) { entry ->
                val dateStr = remember(entry.date) {
                    val cal = Calendar.getInstance().apply { timeInMillis = entry.date }
                    DateFormat.format("MMM d, yyyy", cal).toString()
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Weight: ${entry.weight} kg",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    dateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteWeight(entry.id) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete entry",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Composition Details Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (entry.bodyFat != null) {
                                Column {
                                    Text("Body Fat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${entry.bodyFat}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                entry.leanMass?.let { lm ->
                                    Column {
                                        Text("Lean Mass", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${String.format("%.1f", lm)} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                entry.fatMass?.let { fm ->
                                    Column {
                                        Text("Fat Mass", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${String.format("%.1f", fm)} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            entry.bmi?.let { bmiVal ->
                                Column {
                                    Text("BMI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format("%.1f", bmiVal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TapeProgressTab(
    measurements: List<TapeMeasurement>,
    selectedSite: String,
    onSelectSite: (String) -> Unit,
    chestInput: String,
    onChestChange: (String) -> Unit,
    waistInput: String,
    onWaistChange: (String) -> Unit,
    hipsInput: String,
    onHipsChange: (String) -> Unit,
    bicepLeftInput: String,
    onBicepLeftChange: (String) -> Unit,
    bicepRightInput: String,
    onBicepRightChange: (String) -> Unit,
    thighLeftInput: String,
    onThighLeftChange: (String) -> Unit,
    thighRightInput: String,
    onThighRightChange: (String) -> Unit,
    onLogMeasurements: () -> Unit,
    onDeleteMeasurement: (Int) -> Unit
) {
    val sitesList = listOf("Chest", "Waist", "Hips", "Bicep Left", "Bicep Right", "Thigh Left", "ThighRight")

    // Map selected site to the appropriate value of tape entry for chart
    val chartData = remember(measurements, selectedSite) {
        measurements.sortedBy { it.date }.mapNotNull { entry ->
            val value = when (selectedSite) {
                "Chest" -> entry.chest
                "Waist" -> entry.waist
                "Hips" -> entry.hips
                "Bicep Left" -> entry.bicepLeft
                "Bicep Right" -> entry.bicepRight
                "Thigh Left" -> entry.thighLeft
                else -> entry.thighRight
            }
            if (value != null) Pair(entry.date, value) else null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form to record measurements
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Log Tape Measurements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Grid of Inputs
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = chestInput,
                                onValueChange = onChestChange,
                                placeholder = { Text("Chest") },
                                suffix = { Text("cm") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = waistInput,
                                onValueChange = onWaistChange,
                                placeholder = { Text("Waist") },
                                suffix = { Text("cm") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = hipsInput,
                                onValueChange = onHipsChange,
                                placeholder = { Text("Hips") },
                                suffix = { Text("cm") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = bicepLeftInput,
                                onValueChange = onBicepLeftChange,
                                placeholder = { Text("L Bicep") },
                                suffix = { Text("cm") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = bicepRightInput,
                                onValueChange = onBicepRightChange,
                                placeholder = { Text("R Bicep") },
                                suffix = { Text("cm") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = thighLeftInput,
                                onValueChange = onThighLeftChange,
                                placeholder = { Text("L Thigh") },
                                suffix = { Text("cm") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = thighRightInput,
                                onValueChange = onThighRightChange,
                                placeholder = { Text("R Thigh") },
                                suffix = { Text("cm") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }
                    }

                    val hasValue = chestInput.isNotEmpty() || waistInput.isNotEmpty() || hipsInput.isNotEmpty() ||
                            bicepLeftInput.isNotEmpty() || bicepRightInput.isNotEmpty() ||
                            thighLeftInput.isNotEmpty() || thighRightInput.isNotEmpty()

                    Button(
                        onClick = onLogMeasurements,
                        enabled = hasValue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_measurements_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Log Measurements", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Progression Trend Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Tape Trend: $selectedSite",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Horizontal Selection of sites for charting
                    ScrollableTabRow(
                        selectedTabIndex = sitesList.indexOf(selectedSite).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        sitesList.forEach { site ->
                            Tab(
                                selected = selectedSite == site,
                                onClick = { onSelectSite(site) },
                                text = { Text(site, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    if (chartData.size < 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Add 2+ entries containing $selectedSite to view trend.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        StrengthLineChart(
                            dataPoints = chartData,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }
        }

        // Historical Tape logs
        item {
            Text(
                "History Logs",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (measurements.isEmpty()) {
            item {
                Text(
                    "No tape measurements recorded.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(measurements) { entry ->
                val dateStr = remember(entry.date) {
                    val cal = Calendar.getInstance().apply { timeInMillis = entry.date }
                    DateFormat.format("MMM d, yyyy", cal).toString()
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dateStr,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { onDeleteMeasurement(entry.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Render active measurements in a summary format
                        val summaryText = remember(entry) {
                            val list = mutableListOf<String>()
                            if (entry.chest != null) list.add("Chest: ${entry.chest}cm")
                            if (entry.waist != null) list.add("Waist: ${entry.waist}cm")
                            if (entry.hips != null) list.add("Hips: ${entry.hips}cm")
                            if (entry.bicepLeft != null) list.add("L Bicep: ${entry.bicepLeft}cm")
                            if (entry.bicepRight != null) list.add("R Bicep: ${entry.bicepRight}cm")
                            if (entry.thighLeft != null) list.add("L Thigh: ${entry.thighLeft}cm")
                            if (entry.thighRight != null) list.add("R Thigh: ${entry.thighRight}cm")
                            list.joinToString("   •   ")
                        }

                        Text(
                            summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
fun StrengthProgressTab(
    selectedExercise: Exercise,
    completedSets: List<LoggedSet>,
    sessions: List<com.example.data.WorkoutSession>,
    onSelectExerciseClick: () -> Unit
) {
    // Generate progression points: max weight lifted per session over time
    val strengthDataPoints = remember(completedSets, sessions) {
        val groupedBySession = completedSets.groupBy { it.sessionId }
        groupedBySession.mapNotNull { (sessionId, setsList) ->
            val sessionDate = sessions.find { it.id == sessionId }?.startTime ?: return@mapNotNull null
            val maxWeight = setsList.maxOfOrNull { it.weight } ?: 0f
            if (maxWeight > 0f) Pair(sessionDate, maxWeight) else null
        }.sortedBy { it.first }
    }

    val personalRecord = remember(completedSets) {
        completedSets.maxOfOrNull { it.weight } ?: 0f
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Exercise Selector Box
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectExerciseClick() }
                    .testTag("select_exercise_progress_button"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Selected Exercise Strength",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            selectedExercise.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Button(
                        onClick = onSelectExerciseClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Change", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // PR summary card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "PERSONAL RECORD",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (personalRecord > 0) "$personalRecord kg" else "—",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "LOGGED LIFTS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${completedSets.size} sets",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Progression Line Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "${selectedExercise.name} Lift Progression",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (strengthDataPoints.size < 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Add completed sessions of this exercise to view trend.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        StrengthLineChart(
                            dataPoints = strengthDataPoints,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
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

@Composable
fun StrengthLineChart(
    dataPoints: List<Pair<Long, Float>>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val textPaintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        if (dataPoints.isEmpty()) return@Canvas

        val minTime = dataPoints.minOf { it.first }
        val maxTime = dataPoints.maxOf { it.first }
        val minValue = dataPoints.minOf { it.second } * 0.95f // subtle padding beneath min
        val maxValue = dataPoints.maxOf { it.second } * 1.05f // subtle padding above max

        val timeRange = (maxTime - minTime).coerceAtLeast(1)
        val valueRange = (maxValue - minValue).coerceAtLeast(1f)

        val width = size.width
        val height = size.height

        // Bottom and left margins for coordinates/labels (generous space for metrics and dates)
        val marginX = 85f
        val marginY = 48f

        val graphWidth = width - marginX
        val graphHeight = height - marginY

        val coordinates = dataPoints.map { point ->
            val x = marginX + ((point.first - minTime).toFloat() / timeRange) * graphWidth
            val y = graphHeight - ((point.second - minValue) / valueRange) * graphHeight
            Offset(x, y)
        }

        // Draw grid lines
        val gridLinesCount = 3
        for (i in 0..gridLinesCount) {
            val y = (graphHeight / gridLinesCount) * i
            drawLine(
                color = textPaintColor.copy(alpha = 0.08f),
                start = Offset(marginX, y),
                end = Offset(width, y),
                strokeWidth = 2f
            )
        }

        // Path for gradient filling under the line
        val fillPath = Path().apply {
            moveTo(coordinates.first().x, graphHeight)
            coordinates.forEach { lineTo(it.x, it.y) }
            lineTo(coordinates.last().x, graphHeight)
            close()
        }

        // Draw glowing gradient background
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.25f),
                    color.copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY = graphHeight
            )
        )

        // Draw actual line path
        val strokePath = Path().apply {
            moveTo(coordinates.first().x, coordinates.first().y)
            for (i in 1 until coordinates.size) {
                lineTo(coordinates[i].x, coordinates[i].y)
            }
        }

        drawPath(
            path = strokePath,
            color = color,
            style = Stroke(
                width = 6f,
                cap = StrokeCap.Round
            )
        )

        // Draw points on data coordinates
        coordinates.forEach { offset ->
            drawCircle(
                color = color,
                radius = 6f,
                center = offset
            )
            drawCircle(
                color = surfaceColor,
                radius = 3f,
                center = offset
            )
        }

        // Draw baseline axis
        drawLine(
            color = textPaintColor.copy(alpha = 0.2f),
            start = Offset(marginX, graphHeight),
            end = Offset(width, graphHeight),
            strokeWidth = 3f
        )

        // DRAW METRIC AXIS LABELS (Using Android Native Paint for precise typographic sizing)
        val paintColorVal = android.graphics.Color.argb(
            (textPaintColor.alpha * 255).toInt(),
            (textPaintColor.red * 255).toInt(),
            (textPaintColor.green * 255).toInt(),
            (textPaintColor.blue * 255).toInt()
        )
        val textPaint = android.graphics.Paint()
        textPaint.color = paintColorVal
        textPaint.textSize = 24f
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        textPaint.isAntiAlias = true

        // Render Max value label
        drawContext.canvas.nativeCanvas.drawText(
            String.format("%.1f", maxValue),
            marginX - 16f,
            30f,
            textPaint
        )

        // Render Mid value label
        drawContext.canvas.nativeCanvas.drawText(
            String.format("%.1f", (maxValue + minValue) / 2f),
            marginX - 16f,
            (graphHeight / 2f) + 10f,
            textPaint
        )

        // Render Min value label
        drawContext.canvas.nativeCanvas.drawText(
            String.format("%.1f", minValue),
            marginX - 16f,
            graphHeight - 6f,
            textPaint
        )

        // DRAW DATE LABELS
        val dateColorVal = android.graphics.Color.argb(
            (textPaintColor.alpha * 0.7f * 255).toInt(),
            (textPaintColor.red * 255).toInt(),
            (textPaintColor.green * 255).toInt(),
            (textPaintColor.blue * 255).toInt()
        )
        val datePaint = android.graphics.Paint()
        datePaint.color = dateColorVal
        datePaint.textSize = 22f
        datePaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
        datePaint.isAntiAlias = true

        val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
        val startDateStr = sdf.format(java.util.Date(minTime))
        val endDateStr = sdf.format(java.util.Date(maxTime))

        // Draw start date
        datePaint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(
            startDateStr,
            marginX,
            height - 8f,
            datePaint
        )

        // Draw end date
        datePaint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(
            endDateStr,
            width - 8f,
            height - 8f,
            datePaint
        )
    }
}
