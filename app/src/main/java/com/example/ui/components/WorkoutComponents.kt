package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Exercise
import com.example.data.WorkoutTemplate
import com.example.ui.viewmodel.StrengthViewModel.TemplateExerciseState

@Composable
fun RoutineCard(
    template: WorkoutTemplate,
    routineExercises: List<Exercise>,
    templateDetails: List<TemplateExerciseState>,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExerciseClick: (Exercise) -> Unit,
    isMetric: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        val wStr = if (s.targetWeight != null) " @ ${com.example.core.util.UnitConverter.formatWeight(s.targetWeight.toDouble(), isMetric)}" else ""
                        "${rStr}${wStr}"
                    } ?: ""

                    ExerciseRow(
                        exercise = exercise,
                        setsCount = setsCount,
                        targetSummary = targetSummary,
                        onClick = { onExerciseClick(exercise) }
                    )
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

@Composable
fun ExerciseRow(
    exercise: Exercise,
    setsCount: Int,
    targetSummary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
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

@Composable
fun SearchComponents(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    filtersList: List<String>,
    modifier: Modifier = Modifier,
    placeholderText: String = "Search..."
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholderText) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("exercise_search_input"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filtersList.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
fun ExercisePicker(
    exercises: List<Exercise>,
    onExerciseSelected: (Exercise) -> Unit,
    selectedExercises: List<Exercise>,
    onExerciseDeselected: (Exercise) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(exercises, key = { it.id }) { exercise ->
            val isSelected = selectedExercises.contains(exercise)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                    .clickable {
                        if (isSelected) onExerciseDeselected(exercise)
                        else onExerciseSelected(exercise)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = exercise.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                        if (checked == true) onExerciseSelected(exercise)
                        else onExerciseDeselected(exercise)
                    }
                )
            }
        }
    }
}

@Composable
fun RoutineHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RoutineFooter(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    secondaryText: String?,
    onSecondaryClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (secondaryText != null && onSecondaryClick != null) {
            OutlinedButton(
                onClick = onSecondaryClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(secondaryText, fontWeight = FontWeight.Bold)
            }
        }
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(primaryText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyRoutineState(
    onCreateRoutineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = "No templates",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "No Routines Built",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Create one to follow your specific workout plans.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onCreateRoutineClick,
                modifier = Modifier.testTag("create_routine_button_empty"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Build a Routine", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TemplateList(
    templates: List<WorkoutTemplate>,
    exercises: List<Exercise>,
    onStartTemplate: (WorkoutTemplate) -> Unit,
    onEditTemplate: (WorkoutTemplate) -> Unit,
    onDuplicateTemplate: (WorkoutTemplate) -> Unit,
    onDeleteTemplate: (WorkoutTemplate) -> Unit,
    onExerciseClick: (Exercise) -> Unit,
    getTemplateDetails: suspend (Long) -> List<TemplateExerciseState>,
    deserializeExerciseIds: (String) -> List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        templates.forEach { template ->
            // Use the stateless RoutineCard inside our template list
            val routineExerciseIds = deserializeExerciseIds(template.exerciseIdsJson)
            val routineExercises = routineExerciseIds.mapNotNull { id -> exercises.find { it.id == id } }

            // To support the details fetch in a stateless way, let's keep it clean
            androidx.compose.runtime.remember(template.id) {
                // Keep minimal local state for details, but it's okay because we render list items with stable keys
            }
            // State for holding template details inside List
            var detailsState by androidx.compose.runtime.remember(template.id) {
                androidx.compose.runtime.mutableStateOf<List<TemplateExerciseState>>(emptyList())
            }
            androidx.compose.runtime.LaunchedEffect(template.id) {
                detailsState = getTemplateDetails(template.id.toLong())
            }

            RoutineCard(
                template = template,
                routineExercises = routineExercises,
                templateDetails = detailsState,
                onStart = { onStartTemplate(template) },
                onEdit = { onEditTemplate(template) },
                onDuplicate = { onDuplicateTemplate(template) },
                onDelete = { onDeleteTemplate(template) },
                onExerciseClick = onExerciseClick,
                modifier = Modifier.testTag("routine_card_${template.id}")
            )
        }
    }
}
