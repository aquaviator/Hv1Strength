package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.data.Exercise
import com.example.ui.viewmodel.ActiveSet

@Composable
fun EmptyWorkoutState(
    onAddExerciseClick: () -> Unit,
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
                contentDescription = "No exercises",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Empty Workout Session",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Add exercises to customize and start logging your progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAddExerciseClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Exercise", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WorkoutHeader(
    templateName: String,
    startTime: Long,
    exercisesCount: Int,
    activeExerciseIndex: Int,
    elapsedTime: String,
    onRenameActiveWorkout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(startTime) {
        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(startTime))
    }
    val defaultContextName = "Strength Session - $dateStr"

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember(templateName) { mutableStateOf(templateName) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        if (isEditingName) {
            androidx.compose.foundation.text.BasicTextField(
                value = editedName,
                onValueChange = { newValue ->
                    editedName = newValue
                    val flushedName = if (newValue.isBlank()) defaultContextName else newValue
                    onRenameActiveWorkout(flushedName)
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val finalName = if (editedName.isBlank()) defaultContextName else editedName
                        onRenameActiveWorkout(finalName)
                        editedName = finalName
                        isEditingName = false
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workout_name_input"),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        if (editedName.isEmpty()) {
                            Text(
                                text = defaultContextName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        } else {
                            innerTextField()
                        }
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = {
                        val finalName = if (editedName.isBlank()) defaultContextName else editedName
                        onRenameActiveWorkout(finalName)
                        editedName = finalName
                        isEditingName = false
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = {
                        editedName = templateName
                        isEditingName = false
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("cancel_rename_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEditingName = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = templateName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("workout_name_text")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Workout Name",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val progressIndex = if (exercisesCount > 0) activeExerciseIndex else 0
            Text(
                text = "Exercise $progressIndex of $exercisesCount",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Timer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = elapsedTime,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun WorkoutFooter(
    onCancelClick: () -> Unit,
    onFinishClick: () -> Unit,
    isCompletingWorkout: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onCancelClick,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cancel", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onFinishClick,
            enabled = !isCompletingWorkout,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isCompletingWorkout) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saving...", fontWeight = FontWeight.Bold)
            } else {
                Text("Finish Session", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RestTimerCard(
    restTimeRemaining: Int?,
    onReduceRestTime: (Int) -> Unit,
    onSkipRestGuide: () -> Unit,
    onAddRestTime: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (restTimeRemaining != null) {
        val remaining = restTimeRemaining
        val formattedTime = remember(remaining) {
            val mins = remaining / 60
            val secs = remaining % 60
            String.format("%02d:%02d", mins, secs)
        }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Rest",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Rest guide · $formattedTime",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onReduceRestTime(15) },
                    modifier = Modifier.testTag("rest_guide_minus_15_button")
                ) {
                    Text("-15s", fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { onSkipRestGuide() },
                    modifier = Modifier.testTag("rest_guide_skip_button")
                ) {
                    Text("Skip", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = { onAddRestTime(15) },
                    modifier = Modifier.testTag("rest_guide_plus_15_button")
                ) {
                    Text("+15s", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun NotesSection(
    notes: String,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        label = { Text("Set Notes") },
        placeholder = { Text("Add special performance cues or feedback...") },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@Composable
fun SetRow(
    setNumber: Int,
    set: ActiveSet,
    onClick: () -> Unit,
    onToggleCompletion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isChecked = set.isCompleted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleCompletion()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Toggle Complete",
                    tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = "Set $setNumber",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (isChecked) {
                    val rpeStr = if (set.rpe != null) " @ RPE ${set.rpe}" else ""
                    Text(
                        text = "${set.weight.toString().removeSuffix(".0")} kg × ${set.reps} reps$rpeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    val targetList = mutableListOf<String>()
                    if (set.targetWeight != null && set.targetWeight > 0f) targetList.add("${set.targetWeight} kg")
                    if (set.targetRepsMin != null) targetList.add("${set.targetRepsMin} reps")
                    val targetText = if (targetList.isNotEmpty()) "Target: ${targetList.joinToString(" × ")}" else "Tap to edit"
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit Set",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun ExerciseCard(
    exercise: Exercise,
    setsCount: Int,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${exercise.category} · $setsCount Sets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

@Composable
fun GroupHeader(
    title: String,
    roundsCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Superset Block",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
        }

        Text(
            text = "[ Rounds: $roundsCount ]",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ActiveExerciseSection(
    exercise: Exercise,
    setNumber: Int,
    totalSets: Int,
    weight: Float,
    reps: Int,
    rpe: Int?,
    onWeightChange: (Float) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRpeChange: (Int?) -> Unit,
    onCompleteSet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Set $setNumber of $totalSets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Weight Stepper
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WEIGHT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { onWeightChange((weight - 2.5f).coerceAtLeast(0f)) }) {
                            Icon(Icons.Default.RemoveCircleOutline, "Decrease Weight")
                        }
                        Text(
                            text = "${weight.toString().removeSuffix(".0")} kg",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { onWeightChange(weight + 2.5f) }) {
                            Icon(Icons.Default.AddCircleOutline, "Increase Weight")
                        }
                    }
                }

                // Reps Stepper
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "REPS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { onRepsChange((reps - 1).coerceAtLeast(1)) }) {
                            Icon(Icons.Default.RemoveCircleOutline, "Decrease Reps")
                        }
                        Text(
                            text = "$reps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { onRepsChange(reps + 1) }) {
                            Icon(Icons.Default.AddCircleOutline, "Increase Reps")
                        }
                    }
                }
            }

            // Simple complete button
            Button(
                onClick = onCompleteSet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, "Complete Set")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Complete Set", fontWeight = FontWeight.Bold)
            }
        }
    }
}
