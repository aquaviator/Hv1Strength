package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Exercise
import com.example.ui.screens.ExerciseIntention
import com.example.ui.theme.HumanDarkBackground
import com.example.ui.theme.HumanDarkSurface
import com.example.ui.theme.HumanDarkSurfaceVariant
import com.example.ui.theme.HumanElectricBlue
import com.example.ui.theme.HumanElectricBlueDark
import com.example.ui.theme.HumanElectricBlueLight
import com.example.ui.viewmodel.StrengthViewModel.TemplateExerciseState
import com.example.ui.viewmodel.StrengthViewModel.TemplateSetState
import kotlinx.coroutines.launch

// ============================================================================
// KINETIK SLATE DESIGN TOKENS
// ============================================================================
val KineticAccent = Color(0xFF00E5FF)       // Cyan for supersets/circuits
val HumanPrimaryAccent = Color(0xFF0066FF)  // Vibrant athletic blue
val SlateBackground = Color(0xFF0A0A0B)     // Deep near-black background
val SlateElevatedSurface = Color(0xFF141416)// Elevated surface container
val SlateBorderColor = Color(0xFF2C2D31)    // Subtle outline
val SlateMutedText = Color(0xFF8E8E93)      // Grey labels
val SlateSuccess = Color(0xFF10B981)        // Emerald green

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HumanRoutineBuilderScreen(
    routineName: String,
    onRoutineNameChange: (String) -> Unit,
    routineNameError: String?,
    selectedExercises: SnapshotStateList<TemplateExerciseState>,
    exercises: List<Exercise>,
    isMetric: Boolean,
    totalSets: Int,
    estDurationMin: Int,
    totalVolume: Float,
    musclesCount: Map<String, Int>,
    trainingFocus: String,
    difficulty: String,
    onBackClick: () -> Unit,
    onCancelClick: () -> Unit,
    onAddExerciseClick: () -> Unit,
    onSaveClick: () -> Unit,
    pairingDialogIndex: Int?,
    onPairingDialogIndexChange: (Int?) -> Unit,
    showRestConfigGroupIndex: String?,
    onShowRestConfigGroupIndexChange: (String?) -> Unit,
    isEditing: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val isKeyboardVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current

    // Tracks which exercise card is expanded
    var expandedExerciseId by remember { mutableStateOf<String?>(null) }
    
    // Automatically scroll expanded exercise into view
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(expandedExerciseId) {
        if (expandedExerciseId != null) {
            val idx = selectedExercises.indexOfFirst { it.exerciseId == expandedExerciseId }
            if (idx >= 0) {
                coroutineScope.launch {
                    listState.animateScrollToItem(idx)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 1. Redesigned Screen Header
            HumanBuilderHeader(
                routineName = routineName,
                onRoutineNameChange = onRoutineNameChange,
                routineNameError = routineNameError,
                isEditing = isEditing,
                onBackClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBackClick()
                },
                onCancelClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCancelClick()
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 2. live routine summary strip (replacing progress bar)
            RoutineSummaryStrip(
                exerciseCount = selectedExercises.size,
                totalWorkingSets = totalSets,
                estDurationMin = estDurationMin,
                groupCount = selectedExercises.mapNotNull { it.supersetGroupId }.distinct().size
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Content
            Box(modifier = Modifier.weight(1f)) {
                if (selectedExercises.isEmpty()) {
                    // Empty state when no exercises added
                    RoutineBuilderEmptyState(onAddExerciseClick = onAddExerciseClick)
                } else {
                    // Precompute superset letters to display group tags
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
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Render all exercises or groups
                        // To properly render supersets together, we can iterate
                        // and render them inside a custom visual container
                        var renderedGroupIds = mutableSetOf<String>()

                        selectedExercises.forEachIndexed { exIndex, templateExercise ->
                            val groupId = templateExercise.supersetGroupId
                            val isGrouped = !groupId.isNullOrEmpty()

                            if (isGrouped && groupId != null) {
                                if (!renderedGroupIds.contains(groupId)) {
                                    renderedGroupIds.add(groupId)

                                    // Render Superset/Circuit Group Container
                                    val groupExercises = selectedExercises.filter { it.supersetGroupId == groupId }
                                    val groupIndices = selectedExercises.mapIndexedNotNull { index, te -> 
                                        if (te.supersetGroupId == groupId) index else null 
                                    }
                                    
                                    item(key = "group_$groupId") {
                                        val isCircuit = groupExercises.size >= 3
                                        val groupLetter = supersetLabels[selectedExercises.indexOfFirst { it.supersetGroupId == groupId }]?.firstOrNull()?.toString() ?: "A"
                                        
                                        WorkoutGroupContainer(
                                            groupId = groupId,
                                            groupLetter = groupLetter,
                                            isCircuit = isCircuit,
                                            exercisesInGroup = groupExercises,
                                            exercises = exercises,
                                            isMetric = isMetric,
                                            onUpdateRounds = { newRounds ->
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                // Sync sets count for all exercises in this superset
                                                selectedExercises.forEachIndexed { sIdx, se ->
                                                    if (se.supersetGroupId == groupId) {
                                                        val currentSets = se.sets
                                                        val updatedSets = if (newRounds > currentSets.size) {
                                                            val lastSet = currentSets.lastOrNull()
                                                            val added = List(newRounds - currentSets.size) {
                                                                TemplateSetState(
                                                                    setType = lastSet?.setType ?: "WORKING",
                                                                    targetRepsMin = lastSet?.targetRepsMin ?: 8,
                                                                    targetRepsMax = lastSet?.targetRepsMax ?: 12,
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
                                            },
                                            onDissolveGroup = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                selectedExercises.forEachIndexed { sIdx, se ->
                                                    if (se.supersetGroupId == groupId) {
                                                        selectedExercises[sIdx] = se.copy(supersetGroupId = null)
                                                    }
                                                }
                                            },
                                            onConfigureGroupRest = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onShowRestConfigGroupIndexChange(groupId)
                                            },
                                            onMoveUp = { innerIdx ->
                                                val listIdx = groupIndices.getOrNull(innerIdx) ?: -1
                                                if (listIdx > 0) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val temp = selectedExercises[listIdx]
                                                    selectedExercises[listIdx] = selectedExercises[listIdx - 1]
                                                    selectedExercises[listIdx - 1] = temp
                                                }
                                            },
                                            onMoveDown = { innerIdx ->
                                                val listIdx = groupIndices.getOrNull(innerIdx) ?: -1
                                                if (listIdx >= 0 && listIdx < selectedExercises.size - 1) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val temp = selectedExercises[listIdx]
                                                    selectedExercises[listIdx] = selectedExercises[listIdx + 1]
                                                    selectedExercises[listIdx + 1] = temp
                                                }
                                            },
                                            onRemoveFromGroup = { innerIdx ->
                                                val listIdx = groupIndices.getOrNull(innerIdx) ?: -1
                                                if (listIdx >= 0) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    selectedExercises[listIdx] = selectedExercises[listIdx].copy(supersetGroupId = null)
                                                }
                                            },
                                            onDeleteExercise = { innerIdx ->
                                                val listIdx = groupIndices.getOrNull(innerIdx) ?: -1
                                                if (listIdx >= 0) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    selectedExercises.removeAt(listIdx)
                                                }
                                            },
                                            expandedExerciseId = expandedExerciseId,
                                            onToggleExpand = { id ->
                                                expandedExerciseId = if (expandedExerciseId == id) null else id
                                            },
                                            onUpdateExercise = { innerIdx, updated ->
                                                val listIdx = groupIndices.getOrNull(innerIdx) ?: -1
                                                if (listIdx >= 0) {
                                                    selectedExercises[listIdx] = updated
                                                }
                                            },
                                            onPairClick = { innerIdx ->
                                                val listIdx = groupIndices.getOrNull(innerIdx) ?: -1
                                                if (listIdx >= 0) {
                                                    onPairingDialogIndexChange(listIdx)
                                                }
                                            },
                                            supersetLabels = supersetLabels
                                        )
                                    }
                                }
                            } else {
                                // Render normal Ungrouped exercise card
                                item(key = "exercise_${templateExercise.exerciseId}_$exIndex") {
                                    val exerciseObj = exercises.find { it.id == templateExercise.exerciseId }
                                    if (exerciseObj != null) {
                                        val isExpanded = expandedExerciseId == templateExercise.exerciseId

                                        ExercisePrescriptionCard(
                                            templateExercise = templateExercise,
                                            exerciseObj = exerciseObj,
                                            exIndex = exIndex,
                                            isMetric = isMetric,
                                            isExpanded = isExpanded,
                                            onToggleExpand = {
                                                expandedExerciseId = if (isExpanded) null else templateExercise.exerciseId
                                            },
                                            onUpdate = { updated ->
                                                selectedExercises[exIndex] = updated
                                            },
                                            onDelete = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                selectedExercises.removeAt(exIndex)
                                            },
                                            onMoveUp = {
                                                if (exIndex > 0) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val temp = selectedExercises[exIndex]
                                                    selectedExercises[exIndex] = selectedExercises[exIndex - 1]
                                                    selectedExercises[exIndex - 1] = temp
                                                }
                                            },
                                            onMoveDown = {
                                                if (exIndex < selectedExercises.size - 1) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val temp = selectedExercises[exIndex]
                                                    selectedExercises[exIndex] = selectedExercises[exIndex + 1]
                                                    selectedExercises[exIndex + 1] = temp
                                                }
                                            },
                                            onPairClick = {
                                                onPairingDialogIndexChange(exIndex)
                                            },
                                            labelTag = null
                                        )
                                    }
                                }
                            }
                        }

                        // Add Exercise Substantial Button inside the builder content
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            AddExerciseCard(onClick = onAddExerciseClick)
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }

            // 3. Persistent Bottom Action Area (Fades on keyboard visible if desired, but we keep reachable)
            BuilderBottomActionBar(
                isKeyboardVisible = isKeyboardVisible,
                onSaveClick = onSaveClick,
                isSaveEnabled = routineName.isNotBlank() && selectedExercises.isNotEmpty() && selectedExercises.all { it.sets.isNotEmpty() }
            )
        }
    }
}

// ============================================================================
// HEADER COMPONENT (WITH INLINE ROUTINE NAME EDITING)
// ============================================================================
@Composable
fun HumanBuilderHeader(
    routineName: String,
    onRoutineNameChange: (String) -> Unit,
    routineNameError: String?,
    isEditing: Boolean,
    onBackClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 4.dp)
    ) {
        // Top Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(SlateElevatedSurface, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to exercises",
                    tint = Color.White
                )
            }

            Text(
                text = if (isEditing) "EDIT ROUTINE" else "NEW ROUTINE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                ),
                color = KineticAccent
            )

            TextButton(
                onClick = onCancelClick,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red.copy(alpha = 0.8f))
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Large dominant routine name editing area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = if (routineNameError != null) Color.Red else SlateBorderColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(bottom = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit name",
                    tint = SlateMutedText,
                    modifier = Modifier.size(18.dp)
                )

                BasicTextField(
                    value = routineName,
                    onValueChange = onRoutineNameChange,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(KineticAccent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("routine_name_editor_input"),
                    decorationBox = { innerTextField ->
                        if (routineName.isEmpty()) {
                            Text(
                                text = "Routine Name (e.g., Push Day)",
                                style = TextStyle(
                                    color = SlateMutedText,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black
                                )
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        if (routineNameError != null) {
            Text(
                text = routineNameError,
                color = Color.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ============================================================================
// COMPACT ROUTINE SUMMARY STRIP (REPLACING PROGRESS DECORATION)
// ============================================================================
@Composable
fun RoutineSummaryStrip(
    exerciseCount: Int,
    totalWorkingSets: Int,
    estDurationMin: Int,
    groupCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateElevatedSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left summary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = HumanPrimaryAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${exerciseCount} exercises",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
                Text(
                    text = "•",
                    color = SlateMutedText,
                    fontSize = 12.sp
                )
                Text(
                    text = "${totalWorkingSets} sets",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }

            // Right duration and groups
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (groupCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(KineticAccent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, KineticAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${groupCount} grouped",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = KineticAccent
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = SlateMutedText,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "~${estDurationMin} min",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = KineticAccent
                )
            }
        }
    }
}

// ============================================================================
// EXERCISE PRESCRIPTION CARD (COMPACT INFORMATION + EXPANDED CONTROLS)
// ============================================================================
@Composable
fun ExercisePrescriptionCard(
    templateExercise: TemplateExerciseState,
    exerciseObj: Exercise,
    exIndex: Int,
    isMetric: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUpdate: (TemplateExerciseState) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPairClick: () -> Unit,
    labelTag: String? = null // Optional Superset labels such as "A1"
) {
    val intention = remember(templateExercise.notes) { ExerciseIntention.fromSerializedString(templateExercise.notes) }
    val isGrouped = !labelTag.isNullOrEmpty()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = if (isGrouped) RoundedCornerShape(12.dp) else RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) SlateElevatedSurface.copy(alpha = 1.0f) else SlateElevatedSurface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) {
                if (isGrouped) KineticAccent.copy(alpha = 0.35f) else HumanPrimaryAccent.copy(alpha = 0.35f)
            } else {
                SlateBorderColor.copy(alpha = if (isGrouped) 0.3f else 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant Left Accent Indicator Strip for the Active Card
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(
                            if (isGrouped) KineticAccent else HumanPrimaryAccent,
                            RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                        )
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card Header
                ExerciseCardHeader(
                    exerciseName = exerciseObj.name,
                    category = exerciseObj.category,
                    labelTag = labelTag,
                    isExpanded = isExpanded,
                    onToggleExpand = onToggleExpand,
                    onDelete = onDelete,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onPairClick = onPairClick,
                    canMoveUp = exIndex > 0,
                    canMoveDown = true // Let it be enabled generally, boundary checks inside screen
                )

                if (!isExpanded) {
                    // Collapsed State details: High-end athletic dashboard alignment
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateBackground.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sets
                            Column {
                                Text(
                                    text = "SETS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp, 
                                        fontWeight = FontWeight.Bold, 
                                        letterSpacing = 1.sp
                                    ),
                                    color = SlateMutedText
                                )
                                Text(
                                    text = "${templateExercise.sets.size}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.sp, 
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = Color.White
                                )
                            }
                            
                            // Vertical Divider
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(SlateBorderColor))
                            
                            // Reps
                            Column {
                                Text(
                                    text = "REPS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp, 
                                        fontWeight = FontWeight.Bold, 
                                        letterSpacing = 1.sp
                                    ),
                                    color = SlateMutedText
                                )
                                Text(
                                    text = "${intention.targetRepsMin}–${intention.targetRepsMax}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.sp, 
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = Color.White
                                )
                            }
                            
                            // Vertical Divider
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(SlateBorderColor))
                            
                            // Rest
                            Column {
                                Text(
                                    text = "REST",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp, 
                                        fontWeight = FontWeight.Bold, 
                                        letterSpacing = 1.sp
                                    ),
                                    color = SlateMutedText
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(11.dp), tint = SlateMutedText)
                                    Text(
                                        text = "${templateExercise.restSeconds}s",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 14.sp, 
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Starting Weight Badge
                        if (intention.startingWeight != null) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isGrouped) KineticAccent.copy(alpha = 0.12f) else HumanPrimaryAccent.copy(alpha = 0.12f), 
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp, 
                                        if (isGrouped) KineticAccent.copy(alpha = 0.25f) else HumanPrimaryAccent.copy(alpha = 0.25f), 
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "START: ${intention.startingWeight}kg",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGrouped) KineticAccent else HumanPrimaryAccent,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                } else {
                    // Expanded State details: In-depth set configuration & strategy tools
                    HorizontalDivider(color = SlateBorderColor)

                    // Sets & Reps Config rows (Prescription Editor)
                    SetPrescriptionEditor(
                        templateExercise = templateExercise,
                        intention = intention,
                        isMetric = isMetric,
                        onUpdate = onUpdate
                    )

                    HorizontalDivider(color = SlateBorderColor)

                    // Strategy and Notes subsection header
                    Text(
                        text = "TRAINING GOAL & GUIDANCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = if (isGrouped) KineticAccent else HumanPrimaryAccent
                    )

                    // Goal chip options: Premium athletic capsule shapes with distinct icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val goals = listOf(
                            "Strength" to Icons.Default.FitnessCenter,
                            "Hypertrophy" to Icons.Default.TrendingUp,
                            "Endurance" to Icons.Default.DirectionsRun,
                            "Technique" to Icons.Default.Star,
                            "Custom" to Icons.Default.Edit
                        )
                        goals.forEach { (g, icon) ->
                            val isSelected = intention.goal == g
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) {
                                            if (isGrouped) KineticAccent else HumanPrimaryAccent
                                        } else SlateBackground,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.Transparent else SlateBorderColor,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        val updated = intention.copy(goal = g)
                                        onUpdate(templateExercise.copy(notes = updated.toSerializedString()))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isSelected) Color.Black else SlateMutedText
                                    )
                                    Text(
                                        text = g,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else SlateMutedText
                                    )
                                }
                            }
                        }
                    }

                    // Inline Rest editor for expanded mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REST GUIDE", 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold, 
                            letterSpacing = 0.5.sp, 
                            color = SlateMutedText
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val r = (templateExercise.restSeconds - 15).coerceAtLeast(0)
                                    onUpdate(templateExercise.copy(restSeconds = r))
                                },
                                modifier = Modifier.size(32.dp).background(SlateBackground, CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp), tint = Color.White)
                            }

                            Text(
                                text = "${templateExercise.restSeconds}s", 
                                fontWeight = FontWeight.Black, 
                                fontSize = 14.sp, 
                                color = Color.White
                            )

                            IconButton(
                                onClick = {
                                    val r = templateExercise.restSeconds + 15
                                    onUpdate(templateExercise.copy(restSeconds = r))
                                },
                                modifier = Modifier.size(32.dp).background(SlateBackground, CircleShape)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = Color.White)
                            }
                        }
                    }

                    // Strategy Notes field: Refined into an actual physical Coach Notes clipboard container
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, SlateBorderColor, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isGrouped) KineticAccent else HumanPrimaryAccent
                            )
                            Text(
                                text = "COACH SETUP & PERFORMANCE NOTES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = SlateMutedText
                            )
                        }
                        
                        BasicTextField(
                            value = intention.userNotes,
                            onValueChange = {
                                val updated = intention.copy(userNotes = it)
                                onUpdate(templateExercise.copy(notes = updated.toSerializedString()))
                            },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            cursorBrush = SolidColor(if (isGrouped) KineticAccent else HumanPrimaryAccent),
                            decorationBox = { innerTextField ->
                                if (intention.userNotes.isEmpty()) {
                                    Text(
                                        text = "Tap to add setup instructions (e.g. bench height, grip)...",
                                        fontSize = 13.sp,
                                        color = SlateMutedText.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// EXERCISE CARD HEADER COMPONENT
// ============================================================================
@Composable
fun ExerciseCardHeader(
    exerciseName: String,
    category: String,
    labelTag: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPairClick: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 1. Exercise Type Identity Label (Tracked and understated)
            if (labelTag.isNullOrEmpty()) {
                Text(
                    text = "STANDARD EXERCISE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = SlateMutedText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            } else {
                Text(
                    text = "SUPERSET • $labelTag",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = KineticAccent.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // 2. Exercise Name (Strongest visual element)
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White
            )

            // 3. Category (Understated uppercase subtitle)
            Text(
                text = category.uppercase(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = SlateMutedText.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Action icons / menu trigger
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Explicit Expand/Collapse Button: Refined to reduce visual weight
            val cleanName = exerciseName.replace(" ", "_")
            val actionText = if (isExpanded) "Collapse" else "Expand"
            
            TextButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .testTag("expand_collapse_control_$cleanName")
                    .semantics { 
                        contentDescription = "$actionText $exerciseName"
                    },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isExpanded) {
                        if (!labelTag.isNullOrEmpty()) KineticAccent else HumanPrimaryAccent
                    } else SlateMutedText.copy(alpha = 0.8f)
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (isExpanded) {
                // Large move up/down icons inside the expanded state
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.ArrowUpward, "Move up", tint = if (canMoveUp) Color.White else SlateBorderColor)
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.ArrowDownward, "Move down", tint = if (canMoveDown) Color.White else SlateBorderColor)
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Exercise menu", tint = Color.White)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SlateElevatedSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Pair / Add to Superset", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = KineticAccent) },
                        onClick = {
                            showMenu = false
                            onPairClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move Up", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showMenu = false
                            onMoveUp()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move Down", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showMenu = false
                            onMoveDown()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove Movement", color = Color.Red) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

// ============================================================================
// REUSABLE PRESCRIPTION WRITER / EDITOR
// ============================================================================
@Composable
fun SetPrescriptionEditor(
    templateExercise: TemplateExerciseState,
    intention: ExerciseIntention,
    isMetric: Boolean,
    onUpdate: (TemplateExerciseState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Main set/rep parameters using compact steppers suitable for one-handed use
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Sets Stepper Card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(SlateBackground, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("SETS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SlateMutedText)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${templateExercise.sets.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            val setsList = templateExercise.sets.toMutableList()
                            if (setsList.size > 1) {
                                setsList.removeAt(setsList.size - 1)
                                onUpdate(templateExercise.copy(sets = setsList))
                            }
                        },
                        modifier = Modifier.size(32.dp).background(SlateElevatedSurface, CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp), tint = Color.White)
                    }

                    IconButton(
                        onClick = {
                            val setsList = templateExercise.sets.toMutableList()
                            val lastSet = setsList.lastOrNull()
                            val newSet = TemplateSetState(
                                setType = lastSet?.setType ?: "WORKING",
                                targetRepsMin = intention.targetRepsMin,
                                targetRepsMax = intention.targetRepsMax,
                                targetWeight = intention.startingWeight ?: lastSet?.targetWeight
                            )
                            setsList.add(newSet)
                            onUpdate(templateExercise.copy(sets = setsList))
                        },
                        modifier = Modifier.size(32.dp).background(SlateElevatedSurface, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = Color.White)
                    }
                }
            }

            // 2. Reps target bounds card
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .background(SlateBackground, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("REPS TARGET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SlateMutedText)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${intention.targetRepsMin} - ${intention.targetRepsMax}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Min:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SlateMutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val minReps = (intention.targetRepsMin - 1).coerceAtLeast(1)
                                val updated = intention.copy(targetRepsMin = minReps)
                                val setsList = templateExercise.sets.map { it.copy(targetRepsMin = minReps) }
                                onUpdate(templateExercise.copy(notes = updated.toSerializedString(), sets = setsList))
                            },
                            modifier = Modifier.size(24.dp).background(SlateElevatedSurface, CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp), tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                val minReps = (intention.targetRepsMin + 1).coerceAtMost(intention.targetRepsMax)
                                val updated = intention.copy(targetRepsMin = minReps)
                                val setsList = templateExercise.sets.map { it.copy(targetRepsMin = minReps) }
                                onUpdate(templateExercise.copy(notes = updated.toSerializedString(), sets = setsList))
                            },
                            modifier = Modifier.size(24.dp).background(SlateElevatedSurface, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Max:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SlateMutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val maxReps = (intention.targetRepsMax - 1).coerceAtLeast(intention.targetRepsMin)
                                val updated = intention.copy(targetRepsMax = maxReps)
                                val setsList = templateExercise.sets.map { it.copy(targetRepsMax = maxReps) }
                                onUpdate(templateExercise.copy(notes = updated.toSerializedString(), sets = setsList))
                            },
                            modifier = Modifier.size(24.dp).background(SlateElevatedSurface, CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp), tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                val maxReps = (intention.targetRepsMax + 1).coerceIn(1, 100)
                                val updated = intention.copy(targetRepsMax = maxReps)
                                val setsList = templateExercise.sets.map { it.copy(targetRepsMax = maxReps) }
                                onUpdate(templateExercise.copy(notes = updated.toSerializedString(), sets = setsList))
                            },
                            modifier = Modifier.size(24.dp).background(SlateElevatedSurface, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
            }

            // 3. Starting weight stepper card
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .background(SlateBackground, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("WEIGHT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SlateMutedText)
                Spacer(modifier = Modifier.height(4.dp))
                val currentWeight = intention.startingWeight ?: 20f
                Text(
                    text = if (intention.startingWeight != null) "${currentWeight.toInt()}kg" else "--",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            val w = (currentWeight - 2.5f).coerceAtLeast(0f)
                            val updated = intention.copy(startingWeight = w)
                            val setsList = templateExercise.sets.map { it.copy(targetWeight = w) }
                            onUpdate(templateExercise.copy(notes = updated.toSerializedString(), sets = setsList))
                        },
                        modifier = Modifier.size(32.dp).background(SlateElevatedSurface, CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp), tint = Color.White)
                    }

                    IconButton(
                        onClick = {
                            val w = if (intention.startingWeight == null) 20f else currentWeight + 2.5f
                            val updated = intention.copy(startingWeight = w)
                            val setsList = templateExercise.sets.map { it.copy(targetWeight = w) }
                            onUpdate(templateExercise.copy(notes = updated.toSerializedString(), sets = setsList))
                        },
                        modifier = Modifier.size(32.dp).background(SlateElevatedSurface, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = Color.White)
                    }
                }
            }
        }
        
        // Show list of individual sets as simple elegant rows
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            templateExercise.sets.forEachIndexed { sIdx, ts ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateBackground, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(HumanPrimaryAccent.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${sIdx + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = HumanPrimaryAccent
                            )
                        }
                        
                        // Set Type cycle text
                        Box(
                            modifier = Modifier
                                .background(SlateElevatedSurface, RoundedCornerShape(4.dp))
                                .clickable {
                                    val types = listOf("WORKING", "WARMUP", "DROP", "AMRAP")
                                    val currentIdx = types.indexOf(ts.setType)
                                    val nextType = types[(currentIdx + 1) % types.size]
                                    val list = templateExercise.sets.toMutableList()
                                    list[sIdx] = ts.copy(setType = nextType)
                                    onUpdate(templateExercise.copy(sets = list))
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = ts.setType,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Reps input display
                        Text(
                            text = "${ts.targetRepsMin ?: 8}-${ts.targetRepsMax ?: 12} reps",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        // Weight input display
                        Text(
                            text = if (ts.targetWeight != null) "${ts.targetWeight} kg" else "No Weight",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KineticAccent
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// SUPERSET & WORKOUT GROUP CONTAINER
// ============================================================================
@Composable
fun WorkoutGroupContainer(
    groupId: String,
    groupLetter: String,
    isCircuit: Boolean,
    exercisesInGroup: List<TemplateExerciseState>,
    exercises: List<Exercise>,
    isMetric: Boolean,
    onUpdateRounds: (Int) -> Unit,
    onDissolveGroup: () -> Unit,
    onConfigureGroupRest: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemoveFromGroup: (Int) -> Unit,
    onDeleteExercise: (Int) -> Unit,
    expandedExerciseId: String?,
    onToggleExpand: (String) -> Unit,
    onUpdateExercise: (Int, TemplateExerciseState) -> Unit,
    onPairClick: (Int) -> Unit,
    supersetLabels: Map<Int, String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateBackground),
        border = BorderStroke(1.dp, KineticAccent.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Group Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(KineticAccent, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = groupLetter,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = Color.Black
                        )
                    }

                    Text(
                        text = if (isCircuit) "CIRCUIT CONTAINER" else "SUPERSET CONTAINER",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = KineticAccent
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onConfigureGroupRest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Timer, "Group Rest", modifier = Modifier.size(18.dp), tint = KineticAccent)
                    }

                    IconButton(
                        onClick = onDissolveGroup,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.LayersClear, "Ungroup", modifier = Modifier.size(18.dp), tint = Color.Red)
                    }
                }
            }

            HorizontalDivider(color = KineticAccent.copy(alpha = 0.12f), thickness = 1.dp)

            // Interactive group stats summary with Rounds Stepper
            val maxRounds = exercisesInGroup.maxOfOrNull { it.sets.size } ?: 3
            val commonRest = exercisesInGroup.firstOrNull()?.restSeconds ?: 90

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateElevatedSurface, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ROUNDS / ROUND SETS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SlateMutedText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { if (maxRounds > 1) onUpdateRounds(maxRounds - 1) },
                            modifier = Modifier
                                .size(28.dp)
                                .background(SlateBackground, CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp), tint = Color.White)
                        }

                        Text("$maxRounds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)

                        IconButton(
                            onClick = { onUpdateRounds(maxRounds + 1) },
                            modifier = Modifier
                                .size(28.dp)
                                .background(SlateBackground, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("POST-ROUND REST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SlateMutedText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${commonRest}s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                }
            }

            // List of exercises in this superset
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercisesInGroup.forEachIndexed { innerIdx, templateExercise ->
                    val exerciseObj = exercises.find { it.id == templateExercise.exerciseId }
                    if (exerciseObj != null) {
                        val isExpanded = expandedExerciseId == templateExercise.exerciseId
                        val globalIdx = supersetLabels.keys.toList().getOrNull(innerIdx) ?: innerIdx

                        ExercisePrescriptionCard(
                            templateExercise = templateExercise,
                            exerciseObj = exerciseObj,
                            exIndex = globalIdx,
                            isMetric = isMetric,
                            isExpanded = isExpanded,
                            onToggleExpand = { onToggleExpand(templateExercise.exerciseId) },
                            onUpdate = { updated -> onUpdateExercise(innerIdx, updated) },
                            onDelete = { onDeleteExercise(innerIdx) },
                            onMoveUp = { onMoveUp(innerIdx) },
                            onMoveDown = { onMoveDown(innerIdx) },
                            onPairClick = { onPairClick(innerIdx) },
                            labelTag = "$groupLetter${innerIdx + 1}"
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// ADD EXERCISE ACTION COMPONENT
// ============================================================================
@Composable
fun AddExerciseCard(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("exercise_browser_add_exercise_button_inline"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SlateElevatedSurface,
            contentColor = Color.White
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = null,
                tint = KineticAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Add Exercise",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ============================================================================
// REDESIGNED PERSISTENT BOTTOM ACTIONS (NO COMPETITION)
// ============================================================================
@Composable
fun BuilderBottomActionBar(
    isKeyboardVisible: Boolean,
    onSaveClick: () -> Unit,
    isSaveEnabled: Boolean
) {
    AnimatedVisibility(
        visible = !isKeyboardVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 0.dp)
        ) {
            Button(
                onClick = onSaveClick,
                enabled = isSaveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("routine_editor_review_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HumanPrimaryAccent,
                    contentColor = Color.White,
                    disabledContainerColor = SlateElevatedSurface,
                    disabledContentColor = SlateMutedText
                )
            ) {
                Text(
                    text = "Review & Save Routine ➔",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ============================================================================
// EMPTY STATE COMPONENT (FOR BUILDER CONTENT)
// ============================================================================
@Composable
fun RoutineBuilderEmptyState(onAddExerciseClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(SlateElevatedSurface, RoundedCornerShape(20.dp))
                .border(1.dp, SlateBorderColor, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = KineticAccent,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Build Your Session",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add the movements, sets and rest periods that define this strength session.",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateMutedText,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddExerciseClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HumanPrimaryAccent)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Text("Add first exercise", fontWeight = FontWeight.Bold)
            }
        }
    }
}
