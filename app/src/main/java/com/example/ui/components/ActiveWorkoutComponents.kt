package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.core.util.LocalVibrationEnabled
import com.example.core.util.performIfEnabled
import com.example.ui.theme.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import com.example.data.Exercise
import com.example.ui.viewmodel.ActiveSet

/**
 * Empty workout placeholder state.
 */
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

/**
 * 1. Workout Status Header
 * Premium top status dashboard with live metrics, progress feedback, and navigation controls.
 */
@Composable
fun WorkoutStatusHeader(
    workoutName: String,
    elapsedTime: String,
    completedSets: Int,
    totalSets: Int,
    onFinishClick: () -> Unit,
    onCancelClick: () -> Unit,
    onAddExerciseClick: () -> Unit,
    onRenameWorkout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember(workoutName) { mutableStateOf(workoutName) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isEditingName) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("workout_name_input"),
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (editedName.isNotBlank()) {
                                onRenameWorkout(editedName)
                            }
                            isEditingName = false
                            focusManager.clearFocus()
                        }),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (editedName.isNotBlank()) {
                                    onRenameWorkout(editedName)
                                }
                                isEditingName = false
                                focusManager.clearFocus()
                            }, modifier = Modifier.testTag("confirm_rename_button")) {
                                Icon(Icons.Default.Check, contentDescription = "Confirm", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { isEditingName = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = workoutName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("workout_name_text")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename workout",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Active Progress Subtext
                val progressPercent = if (totalSets > 0) (completedSets.toFloat() / totalSets * 100).toInt() else 0
                Text(
                    text = "$completedSets of $totalSets sets logged ($progressPercent%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            // Quick actions block
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Large "FINISH" pill button
                Button(
                    onClick = onFinishClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("finish_workout_top_btn")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("FINISH", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                }

                // Overflow Actions Menu Trigger
                Box {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("workout_status_menu_trigger")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Exercise", fontWeight = FontWeight.Bold) },
                            onClick = {
                                showMenu = false
                                onAddExerciseClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            modifier = Modifier.testTag("menu_add_exercise")
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Cancel Workout", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                            onClick = {
                                showMenu = false
                                onCancelClick()
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.testTag("menu_cancel_workout")
                        )
                    }
                }
            }
        }

        // Live Timer and Visual Progress Bar Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Active Timer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = elapsedTime,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Compact linear visual progression bar
            val progressFraction = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f
            LinearProgressIndicator(
                progress = progressFraction,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/**
 * TrainingStepper
 * Symmetric, tactile stepper control with inline text input capabilities.
 */
@Composable
fun TrainingStepper(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditing: Boolean,
    onEditToggle: (Boolean) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    subtext: String = "Tap to type"
) {
    val isVibrationEnabled = com.example.core.util.LocalVibrationEnabled.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = SlateMutedText
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateBackground, RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, SlateBorderColor.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Minus Button (48dp x 48dp minimum touch target)
            IconButton(
                onClick = {
                    haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                    onDecrement()
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(SlateElevatedSurface, shape = RoundedCornerShape(10.dp))
                    .testTag("${label.lowercase()}_decrement_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Display or Edit Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEditToggle(true) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (subtext == "Tap to type") "Tap to pick" else subtext,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = SlateMutedText.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isEditing) {
                    val numVal = value.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
                    val isWeight = label.contains("weight", ignoreCase = true)
                    val isKg = value.contains("kg", ignoreCase = true)
                    val config = if (isWeight) {
                        NumericPickerPresets.weightConfig(isMetric = isKg || !value.contains("lb", ignoreCase = true), currentKg = numVal)
                    } else {
                        NumericPickerPresets.repsConfig(currentReps = numVal.toInt().coerceAtLeast(1))
                    }

                    HumanNumericPickerSheet(
                        config = config,
                        initialValue = numVal,
                        onConfirm = { selected ->
                            val formatted = if (isWeight) {
                                selected.toString().removeSuffix(".0")
                            } else {
                                selected.toInt().toString()
                            }
                            onValueChange(formatted)
                            onEditToggle(false)
                        },
                        onDismiss = { onEditToggle(false) }
                    )
                }
            }

            // Plus Button (48dp x 48dp minimum touch target)
            IconButton(
                onClick = {
                    haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                    onIncrement()
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(SlateElevatedSurface, shape = RoundedCornerShape(10.dp))
                    .testTag("${label.lowercase()}_increment_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase $label",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * ActiveExerciseCard
 * A master training card component consolidating all exercise execution states, metrics, and tactical controls.
 */
@Composable
fun ActiveExerciseCard(
    exerciseName: String,
    category: String,
    identityLabel: String,
    isSuperset: Boolean,
    currentExerciseIndex: Int,
    totalExercisesCount: Int,
    currentSetNumber: Int,
    totalSetsCount: Int,
    completedSetsCount: Int,
    totalSetsInWorkout: Int,
    sets: List<ActiveSet>,
    currentSetIndex: Int,
    onSetClick: (Int) -> Unit,
    // Weight Input
    weight: Float,
    onWeightChange: (Float) -> Unit,
    // Reps Input
    reps: Int,
    onRepsChange: (Int) -> Unit,
    // RPE
    rpe: Int?,
    onRpeChange: (Int?) -> Unit,
    // Previous Session
    prevSummary: String,
    daysAgoText: String?,
    // Coaching Notes
    coachingCues: String?,
    onCuesClick: () -> Unit,
    // Complete Set
    onCompleteSetClick: () -> Unit,
    completeSetEnabled: Boolean,
    // Add/Remove sets
    onAddSetClick: () -> Unit,
    onRemoveSetClick: () -> Unit,
    totalSetsInExercise: Int,
    onRemoveExerciseClick: () -> Unit,
    modifier: Modifier = Modifier,
    weightSource: String = "Tap to type",
    isMetric: Boolean = true
) {
    val isVibrationEnabled = com.example.core.util.LocalVibrationEnabled.current
    var isEditingWeight by remember(weight) { mutableStateOf(false) }
    var weightInputText by remember(weight) { mutableStateOf(weight.toString().removeSuffix(".0")) }

    var isEditingReps by remember(reps) { mutableStateOf(false) }
    var repsInputText by remember(reps) { mutableStateOf(reps.toString()) }

    var isPrevExpanded by remember { mutableStateOf(false) }
    var isCuesExpanded by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(SlateElevatedSurface, shape = RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, if (isSuperset) KineticAccent.copy(alpha = 0.3f) else HumanPrimaryAccent.copy(alpha = 0.3f)), shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(if (isSuperset) KineticAccent else HumanPrimaryAccent)
        )

        // Main content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Identity Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSuperset) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Superset",
                            tint = KineticAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = identityLabel.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = KineticAccent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "Standard Exercise",
                            tint = HumanPrimaryAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "STANDARD EXERCISE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = HumanPrimaryAccent
                        )
                    }
                }

                // Delete/Remove exercise button
                IconButton(
                    onClick = onRemoveExerciseClick,
                    modifier = Modifier.size(24.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                ) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Remove Exercise", modifier = Modifier.size(16.dp))
                }
            }

            // 2. Exercise Title (Crossfade for seamless changing)
            Crossfade(
                targetState = exerciseName,
                animationSpec = tween(durationMillis = 200),
                label = "ExerciseNameChange"
            ) { targetName ->
                Column {
                    Text(
                        text = targetName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = Color.White
                    )
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = SlateMutedText
                    )
                }
            }

            // Compact progress line
            Text(
                text = "Exercise $currentExerciseIndex of $totalExercisesCount  •  Set $currentSetNumber of $totalSetsCount",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = if (isSuperset) KineticAccent else HumanPrimaryAccent
            )

            // 3. Sets Progress indicator strip (Clickable progress row)
            SetProgressStrip(
                sets = sets,
                currentSetIndex = currentSetIndex,
                onSetClick = onSetClick
            )

            HorizontalDivider(color = SlateBorderColor.copy(alpha = 0.4f), thickness = 1.dp)

            // 4. Weight & Reps matched pair (side-by-side columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Weight column
                TrainingStepper(
                    label = "Weight",
                    value = if (isEditingWeight) weightInputText else com.example.core.util.UnitConverter.formatWeight(weight.toDouble(), isMetric),
                    onValueChange = { input ->
                        weightInputText = input
                        val parsed = input.toFloatOrNull()
                        if (parsed != null && parsed >= 0f) {
                            onWeightChange(parsed)
                        }
                    },
                    isEditing = isEditingWeight,
                    onEditToggle = { isEditingWeight = it },
                    onDecrement = {
                        onWeightChange((weight - 2.5f).coerceAtLeast(0f))
                    },
                    onIncrement = {
                        onWeightChange(weight + 2.5f)
                    },
                    keyboardType = KeyboardType.Decimal,
                    subtext = weightSource,
                    modifier = Modifier.weight(1f)
                )

                // Repetitions column
                TrainingStepper(
                    label = "Repetitions",
                    value = if (isEditingReps) repsInputText else "$reps reps",
                    onValueChange = { input ->
                        repsInputText = input
                        val parsed = input.toIntOrNull()
                        if (parsed != null && parsed >= 1) {
                            onRepsChange(parsed)
                        }
                    },
                    isEditing = isEditingReps,
                    onEditToggle = { isEditingReps = it },
                    onDecrement = {
                        onRepsChange((reps - 1).coerceAtLeast(1))
                    },
                    onIncrement = {
                        onRepsChange(reps + 1)
                    },
                    keyboardType = KeyboardType.Number,
                    subtext = "Tap to type",
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = SlateBorderColor.copy(alpha = 0.4f), thickness = 1.dp)

            // 5. Expandable Previous Session & Coaching Notes
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Previous Session
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPrevExpanded = !isPrevExpanded }
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PREVIOUS SESSION",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = SlateMutedText.copy(alpha = 0.7f)
                        )
                        Icon(
                            imageVector = if (isPrevExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Previous Session Details",
                            tint = SlateMutedText.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = if (isSuperset) KineticAccent.copy(alpha = 0.7f) else HumanPrimaryAccent.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        if (isPrevExpanded) {
                            Column {
                                Text(
                                    text = prevSummary.ifBlank { "No prior history" },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                if (daysAgoText != null) {
                                    Text(
                                        text = daysAgoText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = SlateMutedText.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = if (prevSummary.isNotBlank()) {
                                    if (daysAgoText != null) "$prevSummary ($daysAgoText)" else prevSummary
                                } else {
                                    "No prior history"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (prevSummary.isNotBlank()) Color.White else SlateMutedText.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Coaching Note
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isCuesExpanded = !isCuesExpanded
                            onCuesClick()
                        }
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COACHING NOTE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = SlateMutedText.copy(alpha = 0.7f)
                        )
                        Icon(
                            imageVector = if (isCuesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Coaching Note Details",
                            tint = SlateMutedText.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = SlateSuccess.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        if (isCuesExpanded) {
                            Text(
                                text = coachingCues?.ifBlank { "Add coaching note" } ?: "Add coaching note",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (coachingCues.isNullOrBlank()) FontWeight.Normal else FontWeight.Medium,
                                    color = if (coachingCues.isNullOrBlank()) SlateMutedText.copy(alpha = 0.5f) else Color.White
                                ),
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = coachingCues?.ifBlank { "Add note" } ?: "Add note",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (coachingCues.isNullOrBlank()) FontWeight.Normal else FontWeight.Medium,
                                    color = if (coachingCues.isNullOrBlank()) SlateMutedText.copy(alpha = 0.5f) else Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = SlateBorderColor.copy(alpha = 0.4f), thickness = 1.dp)

            // 6. RPE Effort Control (minimized and cleanly integrated)
            EffortControl(
                rpe = rpe,
                onRpeChange = onRpeChange,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = SlateBorderColor.copy(alpha = 0.4f), thickness = 1.dp)

            // 7. Primary Call to Action: COMPLETE SET button
            Button(
                onClick = {
                    haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                    onCompleteSetClick()
                },
                enabled = completeSetEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HumanPrimaryAccent,
                    contentColor = Color.White,
                    disabledContainerColor = SlateElevatedSurface.copy(alpha = 0.5f),
                    disabledContentColor = SlateMutedText.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = "COMPLETE SET",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 15.sp)
                    )
                }
            }
        }
    }
}

/**
 * 2. Current Exercise Hero
 * Elevated display highlighting the current exercise, muscle categories, goals, previous benchmarks, and coaching tips.
 */
@Composable
fun CurrentExerciseHero(
    exerciseName: String,
    category: String,
    groupLabel: String?,
    currentSetNumber: Int,
    totalSets: Int,
    targetWeight: Float?,
    targetReps: Int?,
    prevSummary: String,
    coachingCues: String?,
    onCuesClick: () -> Unit,
    isMetric: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Tag + Group Label Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                if (!groupLabel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                text = groupLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Exercise Title
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 32.sp
            )

            // Current Position Progress Indicator Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = { },
                    label = { Text("Set $currentSetNumber of $totalSets", fontWeight = FontWeight.Black) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                if (targetWeight != null && targetWeight > 0f || targetReps != null) {
                    val prescText = buildString {
                        append("Target: ")
                        if (targetWeight != null && targetWeight > 0f) append(com.example.core.util.UnitConverter.formatWeight(targetWeight.toDouble(), isMetric))
                        if (targetReps != null) {
                            if (isNotEmpty()) append(" × ")
                            append("$targetReps")
                        }
                    }
                    SuggestionChip(
                        onClick = { },
                        label = { Text(prescText, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            // Info Grid: Previous Benchmarks & Coaching Cues
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Previous Performance
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "PREVIOUS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = prevSummary.ifBlank { "No prior history" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right Column: Coaching Cues
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCuesClick() },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "COACHING CUES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = coachingCues?.ifBlank { "Tap to add cues" } ?: "Tap to add cues",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3. Set Progress Strip
 * Horizontal layout of sets belonging to the active exercise showing completion states and easy interactions.
 */
@Composable
fun SetProgressStrip(
    sets: List<ActiveSet>,
    currentSetIndex: Int,
    onSetClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SETS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(end = 4.dp)
        )

        sets.forEachIndexed { index, set ->
            val isActive = index == currentSetIndex
            val isCompleted = set.isCompleted

            val containerColor = when {
                isActive -> MaterialTheme.colorScheme.primaryContainer
                isCompleted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }

            val contentColor = when {
                isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                isCompleted -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            val border = if (isActive) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            Box(
                modifier = Modifier
                    .background(containerColor, shape = RoundedCornerShape(12.dp))
                    .border(border, shape = RoundedCornerShape(12.dp))
                    .clickable { onSetClick(index) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Set ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * 4. Weight Control
 * High-precision tactile weight stepper with preset quick plate selectors.
 */
@Composable
fun WeightControl(
    weight: Float,
    isMetric: Boolean,
    onWeightChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isVibrationEnabled = com.example.core.util.LocalVibrationEnabled.current
    val haptic = LocalHapticFeedback.current
    
    var manualEdit by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf(weight.toString().removeSuffix(".0")) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(weight) {
        if (!manualEdit) {
            manualText = weight.toString().removeSuffix(".0")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "WEIGHT INPUT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
            Text(
                if (isMetric) "METRIC (kg)" else "IMPERIAL (lbs)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Stepper Selector block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Big touch target minus button (56dp target)
            IconButton(
                onClick = {
                    haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                    onWeightChange((weight - 2.5f).coerceAtLeast(0f))
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease weight by 2.5", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }

            // Numeric Display Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { manualEdit = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${weight.toString().removeSuffix(".0")} ${if (isMetric) "kg" else "lbs"}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Tap to pick",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                if (manualEdit) {
                    val currentKg = if (isMetric) weight.toDouble() else com.example.core.util.UnitConverter.lbToKg(weight.toDouble())
                    val config = remember(isMetric, currentKg) {
                        NumericPickerPresets.weightConfig(isMetric, currentKg)
                    }
                    val initialDisplay = if (isMetric) weight.toDouble() else com.example.core.util.UnitConverter.kgToLb(weight.toDouble())
                    HumanNumericPickerSheet(
                        config = config,
                        initialValue = initialDisplay,
                        onConfirm = { selected ->
                            val finalCanonicalKg = if (isMetric) selected.toFloat() else com.example.core.util.UnitConverter.lbToKg(selected).toFloat()
                            onWeightChange(finalCanonicalKg)
                            manualEdit = false
                        },
                        onDismiss = { manualEdit = false }
                    )
                }
            }

            // Big touch target plus button (56dp target)
            IconButton(
                onClick = {
                    haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                    onWeightChange(weight + 2.5f)
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase weight by 2.5", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }

        // Quick adjustment plate chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val plates = listOf(-10f, -5f, -2.5f, -1.25f, 1.25f, 2.5f, 5f, 10f)
            plates.forEach { plate ->
                val isPositive = plate > 0
                val textLabel = if (isPositive) "+${plate.toString().removeSuffix(".0")}" else plate.toString().removeSuffix(".0")
                val containerBg = if (isPositive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                val textColor = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                Box(
                    modifier = Modifier
                        .background(containerBg, shape = RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                            onWeightChange((weight + plate).coerceAtLeast(0f))
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = textLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = textColor
                    )
                }
            }
        }
    }
}

/**
 * 5. Rep Control
 * Tactile step adjustment widget for repetition targets.
 */
@Composable
fun RepControl(
    reps: Int,
    onRepsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isVibrationEnabled = com.example.core.util.LocalVibrationEnabled.current
    val haptic = LocalHapticFeedback.current
    
    var manualEdit by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf(reps.toString()) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(reps) {
        if (!manualEdit) {
            manualText = reps.toString()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "REPETITIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
            Text(
                "REPS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Stepper Block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Big touch target minus button (56dp target)
            IconButton(
                onClick = {
                    haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                    onRepsChange((reps - 1).coerceAtLeast(1))
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease reps by 1", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }

            // Numeric Display Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { manualEdit = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$reps reps",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Tap to pick",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                if (manualEdit) {
                    val config = remember(reps) {
                        NumericPickerPresets.repsConfig(reps)
                    }
                    HumanNumericPickerSheet(
                        config = config,
                        initialValue = reps.toDouble(),
                        onConfirm = { selected ->
                            onRepsChange(selected.toInt())
                            manualEdit = false
                        },
                        onDismiss = { manualEdit = false }
                    )
                }
            }

            // Big touch target plus button (56dp target)
            IconButton(
                onClick = {
                    haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                    onRepsChange(reps + 1)
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase reps by 1", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }

        // Quick Preset rep chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf(5, 8, 10, 12, 15)
            presets.forEach { target ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .background(
                            if (reps == target) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                            onRepsChange(target)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$target",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = if (reps == target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 6. Effort Control
 * Compact and tactile RPE selection segment chips, easy to press with fatigued hands.
 */
@Composable
fun EffortControl(
    rpe: Int?,
    onRpeChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isVibrationEnabled = com.example.core.util.LocalVibrationEnabled.current
    val haptic = LocalHapticFeedback.current
    

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "INTENSITY OF EFFORT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
            Text(
                if (rpe != null) "RPE $rpe" else "No RPE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (rpe != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Horizontal Row of big, direct RPE segment chips (5 to 10 + None)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rpeOptions = listOf(6, 7, 8, 9, 10)
            
            // "None" option
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .background(
                        if (rpe == null) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                        onRpeChange(null)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "None",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rpe == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            rpeOptions.forEach { option ->
                val isSelected = rpe == option
                val containerBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                val textClr = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(containerBg, shape = RoundedCornerShape(10.dp))
                        .clickable {
                            haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
                            onRpeChange(option)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$option",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = textClr
                    )
                }
            }
        }

        // Informative guidance string
        val rpeDesc = when (rpe) {
            10 -> "RPE 10 · Absolute Maximum Effort (No reps remaining)"
            9 -> "RPE 9 · Extremely Heavy (1 rep left in reserve)"
            8 -> "RPE 8 · Heavy Effort (2 reps left in reserve)"
            7 -> "RPE 7 · Moderate/Heavy (3 reps left in reserve)"
            6 -> "RPE 6 · Moderate Speed (4 reps left in reserve)"
            else -> "Rate of Perceived Exertion (10-point Scale)"
        }

        Text(
            text = rpeDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 7. Complete Set Button
 * Massive, finger-reachable call-to-action button with high-contrast styles.
 */
@Composable
fun CompleteSetButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isVibrationEnabled = com.example.core.util.LocalVibrationEnabled.current
    val haptic = LocalHapticFeedback.current
    

    Button(
        onClick = {
            haptic.performIfEnabled(isVibrationEnabled, HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("submit_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = "COMPLETE SET",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * 8. Rest State Panel
 * Beautiful full-screen/dominant panel displayed when the athlete enters the rest period.
 */
@Composable
fun RestStatePanel(
    restTimeRemaining: Int,
    totalRestDuration: Int,
    isPaused: Boolean,
    nextExerciseName: String,
    nextSetNumber: Int,
    nextTargetPrescription: String,
    onAddSecs: (Int) -> Unit,
    onReduceSecs: (Int) -> Unit,
    onSkip: () -> Unit,
    onPauseToggle: () -> Unit,
    modifier: Modifier = Modifier,
    nextIsSuperset: Boolean = false,
    nextIdentityLabel: String = ""
) {
    val mins = restTimeRemaining / 60
    val secs = restTimeRemaining % 60
    val clockStr = String.format("%02d:%02d", mins, secs)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = SlateElevatedSurface
        ),
        border = BorderStroke(2.dp, if (nextIsSuperset) KineticAccent.copy(alpha = 0.5f) else HumanPrimaryAccent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Label: RESTING
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (nextIsSuperset) KineticAccent else HumanPrimaryAccent
                    )
                    Text(
                        text = "RESTING",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        ),
                        color = if (nextIsSuperset) KineticAccent else HumanPrimaryAccent
                    )
                }

                Text(
                    text = if (isPaused) "PAUSED" else "ACTIVE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isPaused) AlertRed else SlateSuccess
                )
            }

            // 2. Countdown Display (Big Clock)
            Text(
                text = clockStr,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 54.sp),
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            // 3. Time Adjustment controls: −15s   Pause   +15s
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onReduceSecs(15) },
                    colors = ButtonDefaults.textButtonColors(contentColor = AlertRed),
                    modifier = Modifier.testTag("rest_guide_minus_15_button")
                ) {
                    Text("−15s", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, fontSize = 16.sp))
                }

                Button(
                    onClick = onPauseToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateBackground,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SlateBorderColor)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume rest timer" else "Pause rest timer",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPaused) "Resume" else "Pause",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                TextButton(
                    onClick = { onAddSecs(15) },
                    colors = ButtonDefaults.textButtonColors(contentColor = if (nextIsSuperset) KineticAccent else HumanPrimaryAccent),
                    modifier = Modifier.testTag("rest_guide_plus_15_button")
                ) {
                    Text("+15s", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, fontSize = 16.sp))
                }
            }

            HorizontalDivider(color = SlateBorderColor.copy(alpha = 0.5f))

            // 4. NEXT UP section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NEXT UP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = SlateMutedText.copy(alpha = 0.8f)
                )

                // Next Exercise Standard or Superset Identity Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    if (nextIsSuperset) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Superset",
                            tint = KineticAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (nextIdentityLabel.isNotBlank()) "SUPERSET • $nextIdentityLabel".uppercase() else "SUPERSET",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = KineticAccent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "Standard Exercise",
                            tint = HumanPrimaryAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "STANDARD EXERCISE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = HumanPrimaryAccent
                        )
                    }
                }

                Text(
                    text = nextExerciseName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Set $nextSetNumber  •  $nextTargetPrescription",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (nextIsSuperset) KineticAccent else HumanPrimaryAccent,
                    textAlign = TextAlign.Center
                )
            }

            HorizontalDivider(color = SlateBorderColor.copy(alpha = 0.5f))

            // 5. SKIP REST (button)
            Button(
                onClick = onSkip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlateBackground,
                    contentColor = if (nextIsSuperset) KineticAccent else HumanPrimaryAccent
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("rest_guide_skip_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("SKIP REST PERIOD", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp))
                }
            }
        }
    }
}

/**
 * 9. Superset Progress Panel
 * Clear interleaving round indicators for grouped workout exercises.
 */
@Composable
fun SupersetProgressPanel(
    groupLabel: String,
    groupType: String,
    currentRound: Int,
    totalRounds: Int,
    exercisesInGroup: List<Pair<String, Boolean>>,
    activeExerciseIndexInGroup: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title & Progress Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "$groupLabel ($groupType)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = "Round $currentRound of $totalRounds",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))

            // Stepped sequence list of group items
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                exercisesInGroup.forEachIndexed { index, (exName, isCompletedInRound) ->
                    val isActive = index == activeExerciseIndexInGroup
                    val stepIcon = when {
                        isCompletedInRound -> Icons.Default.CheckCircle
                        isActive -> Icons.Default.PlayArrow
                        else -> Icons.Default.Circle
                    }
                    val itemColor = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isCompletedInRound -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = stepIcon,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else itemColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "A${index + 1}. $exName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive) FontWeight.Black else FontWeight.Medium,
                            color = itemColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 10. Next Step Preview
 * Visual peek showing upcoming elements.
 */
@Composable
fun NextStepPreview(
    nextExerciseName: String?,
    nextSetNumber: Int?,
    nextPrescription: String?,
    modifier: Modifier = Modifier
) {
    if (nextExerciseName == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        "NEXT UP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = nextExerciseName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (nextSetNumber != null) {
                    Text(
                        "Set $nextSetNumber",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!nextPrescription.isNullOrBlank()) {
                    Text(
                        "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        nextPrescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * 13. Exercise Notes Sheet (Dialog based for maximum cross-device stability)
 */
@Composable
fun ExerciseNotesSheet(
    exerciseName: String,
    notes: String,
    onNotesSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textVal by remember(notes) { mutableStateOf(notes) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Coaching Cues",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = textVal,
                    onValueChange = { textVal = it },
                    placeholder = { Text("E.g., Keep chest high, explode at the bottom cue...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("coaching_notes_text_field"),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onNotesSave(textVal)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Cues", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/**
 * 14. Finish Workout Sheet
 * Summarizes the current training stats in an engaging card prior to final log submissions.
 */
@Composable
fun FinishWorkoutSheet(
    workoutName: String,
    durationText: String,
    completedSets: Int,
    totalSets: Int,
    onConfirmFinish: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("finish_workout_sheet")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Complete Strength Session?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = workoutName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Stats rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "DURATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$completedSets / $totalSets",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "SETS COMPLETED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Ready to log this awesome workout to your profile?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirmFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("confirm_finish_workout_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Log Session", fontWeight = FontWeight.Black)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Keep Training", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
