package com.example.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LoggedSet
import com.example.ui.viewmodel.EnrichedSession
import com.example.ui.viewmodel.StrengthViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    viewModel: StrengthViewModel,
    completedWorkoutId: Int,
    onNavigateToHistory: () -> Unit,
    onNavigateToWorkouts: () -> Unit
) {
    val enrichedSessionFlow = remember(completedWorkoutId) {
        viewModel.getEnrichedSession(completedWorkoutId)
    }
    val enrichedSessionState by enrichedSessionFlow.collectAsState(initial = null)
    val allLoggedSets by viewModel.allLoggedSets.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()

    // Keep track of check state to make sure loading is distinct from failure
    var checkComplete by remember { mutableStateOf(false) }

    LaunchedEffect(enrichedSessionState) {
        if (enrichedSessionState != null) {
            checkComplete = true
        } else {
            // delay a tiny bit to check if it's truly not found
            kotlinx.coroutines.delay(500)
            checkComplete = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Summary", fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        val enriched = enrichedSessionState
        if (enriched == null) {
            if (!checkComplete) {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("summary_loading_indicator")
                        )
                        Text(
                            text = "Preparing your workout summary…",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                // Error state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .testTag("summary_error_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Workout saved, but the summary could not be loaded.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onNavigateToHistory,
                                modifier = Modifier.fillMaxWidth().testTag("error_go_history_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer)
                            ) {
                                Text("Go to History")
                            }
                            OutlinedButton(
                                onClick = onNavigateToWorkouts,
                                modifier = Modifier.fillMaxWidth().testTag("error_go_workouts_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer)
                            ) {
                                Text("Return to Workouts")
                            }
                        }
                    }
                }
            }
        } else {
            // Success state
            val session = enriched.session
            val completedSets = enriched.sets.filter { it.isCompleted }
            val distinctExercisesCount = completedSets.map { it.exerciseId }.distinct().size
            val totalSetsCount = completedSets.size

            val formattedDate = remember(session.startTime) {
                val calendar = Calendar.getInstance().apply { timeInMillis = session.startTime }
                DateFormat.format("EEEE, MMM d, yyyy", calendar).toString()
            }

            // Calculate personal bests or improvements
            val improvements = remember(enriched.sets, allLoggedSets, exercises) {
                val exercisesPerformed = completedSets.map { it.exerciseId }.distinct().mapNotNull { id -> exercises.find { it.id == id } }
                exercisesPerformed.mapNotNull { exercise ->
                    val todaySets = enriched.sets.filter { it.exerciseId == exercise.id && it.isCompleted }
                    val priorSets = allLoggedSets
                        .filter { it.exerciseId == exercise.id && it.isCompleted && it.sessionId != session.id && it.createdAt < session.startTime }
                        .groupBy { it.sessionId }
                    
                    val lastSessionId = priorSets.keys.maxOrNull()
                    val previousSets = if (lastSessionId != null) priorSets[lastSessionId] ?: emptyList() else emptyList()

                    val todayMaxWeight = todaySets.maxOfOrNull { it.weight } ?: 0f
                    val previousMaxWeight = previousSets.maxOfOrNull { it.weight } ?: 0f
                    val todayMaxReps = todaySets.maxOfOrNull { it.reps } ?: 0
                    val previousMaxReps = previousSets.maxOfOrNull { it.reps } ?: 0

                    val isNewPersonalBest = todayMaxWeight > previousMaxWeight && previousMaxWeight > 0f
                    val weightDiff = todayMaxWeight - previousMaxWeight

                    if (isNewPersonalBest) {
                        "${exercise.name}: New Personal Best of ${com.example.core.util.UnitConverter.formatWeight(todayMaxWeight.toDouble(), isMetric)}!"
                    } else if (weightDiff > 0) {
                        "${exercise.name}: Improved max weight by +${com.example.core.util.UnitConverter.formatWeight(weightDiff.toDouble(), isMetric)}!"
                    } else if (todayMaxReps > previousMaxReps && previousMaxReps > 0) {
                        "${exercise.name}: Improved max reps by +${todayMaxReps - previousMaxReps} reps!"
                    } else {
                        null
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Hero Banner
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("summary_hero_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "Workout complete!",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = session.templateName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Stats Dashboard Grid
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Performance Metrics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = "Duration",
                                value = "${enriched.durationMinutes} min",
                                icon = Icons.Default.Timer,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = "Volume",
                                value = com.example.core.util.UnitConverter.formatWeight(enriched.totalVolume.toDouble(), isMetric),
                                icon = Icons.Default.FitnessCenter,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = "Exercises",
                                value = "$distinctExercisesCount",
                                icon = Icons.Default.List,
                                color = MaterialTheme.colorScheme.primary
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = "Sets Completed",
                                value = "$totalSetsCount",
                                icon = Icons.Default.DoneAll,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Personal Bests Section
                if (improvements.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("summary_pb_card"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Achievements",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Achievements & PBs",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                improvements.forEach { text ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "•",
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Call to Action Buttons
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onNavigateToWorkouts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("summary_done_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Done",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onNavigateToHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("summary_view_history_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "View History",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
