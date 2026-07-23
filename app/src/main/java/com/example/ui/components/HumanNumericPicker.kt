package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.util.UnitConverter
import com.example.ui.screens.CasualTheme
import kotlin.math.roundToInt

/**
 * Candidate 5.2 - Universal Smart Workout Picker System Configuration.
 */
data class NumericPickerConfiguration(
    val title: String,
    val unitLabel: String? = null,
    val minimum: Double = 0.0,
    val maximum: Double = 1000.0,
    val step: Double = 1.0,
    val quickValues: List<Double> = emptyList(),
    val decimalPlaces: Int = 1,
    val allowCustomValue: Boolean = true,
    val displayFormatter: ((Double) -> String)? = null
) {
    fun formatValue(value: Double): String {
        displayFormatter?.let { return it(value) }
        return if (decimalPlaces == 0 || value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.${decimalPlaces}f", value)
        }
    }
}

/**
 * Compact numeric stepper row: [ - ]  value  [ + ]
 * Tapping the value text opens the wheel picker modal bottom sheet.
 */
@Composable
fun HumanNumericStepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    config: NumericPickerConfiguration,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val formattedValue = remember(value, config) { config.formatValue(value) }
    val displayUnit = config.unitLabel ?: ""

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CasualTheme.CardSurfaceElevated)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = {
                val newValue = (value - config.step).coerceAtLeast(config.minimum)
                if (newValue != value) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueChange(newValue)
                }
            },
            enabled = enabled && value > config.minimum,
            modifier = Modifier
                .size(48.dp)
                .testTag("stepper_decrement_button")
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease ${config.title}",
                tint = if (value > config.minimum) CasualTheme.TextPrimary else CasualTheme.TextSecondary.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { onOpenPicker() }
                .padding(vertical = 6.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (displayUnit.isNotBlank()) "$formattedValue $displayUnit" else formattedValue,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = CasualTheme.PrimaryAccent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("stepper_value_text")
            )
        }

        IconButton(
            onClick = {
                val newValue = (value + config.step).coerceAtMost(config.maximum)
                if (newValue != value) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueChange(newValue)
                }
            },
            enabled = enabled && value < config.maximum,
            modifier = Modifier
                .size(48.dp)
                .testTag("stepper_increment_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase ${config.title}",
                tint = if (value < config.maximum) CasualTheme.TextPrimary else CasualTheme.TextSecondary.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Material 3 Modal Bottom Sheet containing the wheel picker and quick values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HumanNumericPickerSheet(
    config: NumericPickerConfiguration,
    initialValue: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    valueList: List<Double>? = null
) {
    var selectedValue by remember(initialValue) { mutableStateOf(initialValue) }
    var showCustomDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CasualTheme.CardSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("numeric_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = config.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CasualTheme.TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = CasualTheme.TextSecondary)
                }
            }

            // Quick values chips
            if (config.quickValues.isNotEmpty()) {
                HumanQuickValues(
                    quickValues = config.quickValues,
                    selectedValue = selectedValue,
                    config = config,
                    onSelectValue = { selectedValue = it }
                )
            }

            // Wheel Column
            HumanWheelColumn(
                config = config,
                selectedValue = selectedValue,
                onValueChange = { selectedValue = it },
                customValueList = valueList
            )

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (config.allowCustomValue) {
                    TextButton(
                        onClick = { showCustomDialog = true },
                        modifier = Modifier.testTag("custom_value_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = CasualTheme.PrimaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Custom Value",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = CasualTheme.PrimaryAccent
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CasualTheme.TextSecondary.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("picker_cancel_button")
                    ) {
                        Text("Cancel", color = CasualTheme.TextSecondary, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            onConfirm(selectedValue)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CasualTheme.PrimaryAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("picker_done_button")
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCustomDialog) {
        HumanCustomValueEntryDialog(
            config = config,
            initialValue = selectedValue,
            onConfirm = { customVal ->
                selectedValue = customVal
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false }
        )
    }
}

/**
 * Snapping wheel picker column.
 */
@Composable
fun HumanWheelColumn(
    config: NumericPickerConfiguration,
    selectedValue: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    customValueList: List<Double>? = null
) {
    val haptic = LocalHapticFeedback.current
    val values = remember(config, customValueList) {
        if (customValueList != null && customValueList.isNotEmpty()) {
            customValueList
        } else {
            val list = mutableListOf<Double>()
            var curr = config.minimum
            val max = config.maximum
            val step = if (config.step > 0) config.step else 1.0
            while (curr <= max + 0.0001) {
                val rounded = (curr * 1000).roundToInt() / 1000.0
                list.add(rounded)
                curr += step
            }
            list
        }
    }

    val initialIndex = remember(selectedValue, values) {
        val idx = values.indexOfFirst { kotlin.math.abs(it - selectedValue) < 0.001 }
        if (idx >= 0) idx else values.indexOfMinByOrNull { kotlin.math.abs(it - selectedValue) } ?: 0
    }

    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState)

    val currentCenteredIndex by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) initialIndex
            else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closest = visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    kotlin.math.abs(itemCenter - viewportCenter)
                }
                closest?.index ?: initialIndex
            }
        }
    }

    LaunchedEffect(currentCenteredIndex) {
        if (currentCenteredIndex in values.indices) {
            val newValue = values[currentCenteredIndex]
            if (newValue != selectedValue) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange(newValue)
            }
        }
    }

    LaunchedEffect(selectedValue) {
        val targetIdx = values.indexOfFirst { kotlin.math.abs(it - selectedValue) < 0.001 }
            .takeIf { it >= 0 } ?: values.indexOfMinByOrNull { kotlin.math.abs(it - selectedValue) } ?: 0
        if (targetIdx != currentCenteredIndex && !lazyListState.isScrollInProgress) {
            lazyListState.animateScrollToItem(targetIdx)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Highlight background for center selected item
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CasualTheme.CardSurfaceElevated)
                .border(1.dp, CasualTheme.PrimaryAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        )

        LazyColumn(
            state = lazyListState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = 78.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("wheel_lazy_column"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(values) { index, itemValue ->
                val isSelected = index == currentCenteredIndex
                val alpha = if (isSelected) 1.0f else 0.4f
                val fontSize = if (isSelected) 22.sp else 16.sp
                val fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                val textColor = if (isSelected) CasualTheme.PrimaryAccent else CasualTheme.TextSecondary

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable {
                            if (!isSelected) {
                                onValueChange(itemValue)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = config.formatValue(itemValue) + (config.unitLabel?.let { " $it" } ?: ""),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = fontSize,
                            fontWeight = fontWeight
                        ),
                        color = textColor,
                        modifier = Modifier.alpha(alpha)
                    )
                }
            }
        }
    }
}

/**
 * Quick values chips row.
 */
@Composable
fun HumanQuickValues(
    quickValues: List<Double>,
    selectedValue: Double,
    config: NumericPickerConfiguration,
    onSelectValue: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "QUICK SELECT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 10.sp
            ),
            color = CasualTheme.TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_values_row")
        ) {
            items(quickValues) { qVal ->
                val isSelected = kotlin.math.abs(qVal - selectedValue) < 0.001
                val chipColor = if (isSelected) CasualTheme.PrimaryAccent else CasualTheme.CardSurfaceElevated
                val textColor = if (isSelected) Color.White else CasualTheme.TextPrimary
                val borderColor = if (isSelected) CasualTheme.PrimaryAccent else CasualTheme.Divider

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = chipColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectValue(qVal)
                        }
                        .testTag("quick_value_chip_${config.formatValue(qVal)}")
                ) {
                    Text(
                        text = config.formatValue(qVal) + (config.unitLabel?.let { " $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Fallback keyboard entry dialog.
 */
@Composable
fun HumanCustomValueEntryDialog(
    config: NumericPickerConfiguration,
    initialValue: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputText by remember { mutableStateOf(config.formatValue(initialValue)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            keyboardController?.hide()
            focusManager.clearFocus()
            onDismiss()
        },
        title = {
            Text(
                text = "Enter Custom ${config.title}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CasualTheme.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        errorMessage = null
                    },
                    suffix = config.unitLabel?.let { { Text(it, color = CasualTheme.TextSecondary) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (config.decimalPlaces > 0) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = inputText.toDoubleOrNull()
                            if (parsed != null && parsed >= config.minimum && parsed <= config.maximum) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onConfirm(parsed)
                            } else {
                                errorMessage = "Must be between ${config.minimum} and ${config.maximum}"
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CasualTheme.PrimaryAccent,
                        unfocusedBorderColor = CasualTheme.Divider,
                        focusedTextColor = CasualTheme.TextPrimary,
                        unfocusedTextColor = CasualTheme.TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_value_text_field")
                )

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = CasualTheme.ErrorRed
                    )
                }
            }
        },
        containerColor = CasualTheme.CardSurface,
        confirmButton = {
            Button(
                onClick = {
                    val parsed = inputText.toDoubleOrNull()
                    if (parsed != null && parsed >= config.minimum && parsed <= config.maximum) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onConfirm(parsed)
                    } else {
                        errorMessage = "Must be between ${config.minimum} and ${config.maximum}"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CasualTheme.PrimaryAccent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("custom_value_apply_button")
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CasualTheme.TextSecondary.copy(alpha = 0.4f)),
                modifier = Modifier.testTag("custom_value_cancel_button")
            ) {
                Text("Cancel", color = CasualTheme.TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

/**
 * Common configuration presets for weight, reps, sets, rest, and rpe.
 */
object NumericPickerPresets {
    fun weightConfig(
        isMetric: Boolean,
        currentKg: Double,
        previousKg: Double? = null
    ): NumericPickerConfiguration {
        val unitLabel = if (isMetric) "kg" else "lbs"
        val displayCurrent = if (isMetric) currentKg else UnitConverter.kgToLb(currentKg)
        val step = if (isMetric) 2.5 else 5.0
        val min = 0.0
        val max = 500.0

        val base = if (displayCurrent > 0) displayCurrent else (previousKg?.let { if (isMetric) it else UnitConverter.kgToLb(it) } ?: 20.0)
        val q1 = (base - step * 2).coerceAtLeast(min)
        val q2 = (base - step).coerceAtLeast(min)
        val q3 = base
        val q4 = base + step
        val q5 = base + step * 2
        val quick = listOf(q1, q2, q3, q4, q5).map { (it * 10).roundToInt() / 10.0 }.distinct().filter { it >= min }

        return NumericPickerConfiguration(
            title = "Weight",
            unitLabel = unitLabel,
            minimum = min,
            maximum = max,
            step = step,
            quickValues = quick,
            decimalPlaces = if (isMetric) 1 else 0,
            allowCustomValue = true
        )
    }

    fun repsConfig(currentReps: Int, previousReps: Int? = null): NumericPickerConfiguration {
        val quick = listOf(5.0, 8.0, 10.0, 12.0, 15.0)
        return NumericPickerConfiguration(
            title = "Repetitions",
            unitLabel = "reps",
            minimum = 1.0,
            maximum = 100.0,
            step = 1.0,
            quickValues = quick,
            decimalPlaces = 0,
            allowCustomValue = true
        )
    }

    fun setsConfig(currentSets: Int): NumericPickerConfiguration {
        return NumericPickerConfiguration(
            title = "Sets",
            unitLabel = "sets",
            minimum = 1.0,
            maximum = 20.0,
            step = 1.0,
            quickValues = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
            decimalPlaces = 0,
            allowCustomValue = false
        )
    }

    fun rpeConfig(): NumericPickerConfiguration {
        return NumericPickerConfiguration(
            title = "RPE",
            unitLabel = "RPE",
            minimum = 5.0,
            maximum = 10.0,
            step = 0.5,
            quickValues = listOf(6.0, 7.0, 8.0, 9.0, 10.0),
            decimalPlaces = 1,
            allowCustomValue = false
        )
    }

    fun restConfig(currentSeconds: Int): NumericPickerConfiguration {
        val quick = listOf(30.0, 45.0, 60.0, 90.0, 120.0, 180.0)
        return NumericPickerConfiguration(
            title = "Rest Duration",
            unitLabel = "sec",
            minimum = 0.0,
            maximum = 600.0,
            step = 15.0,
            quickValues = quick,
            decimalPlaces = 0,
            allowCustomValue = true,
            displayFormatter = { sec ->
                val totalSec = sec.toInt()
                if (totalSec < 60) "${totalSec}s"
                else {
                    val m = totalSec / 60
                    val s = totalSec % 60
                    if (s == 0) "${m}m" else "${m}m ${s}s"
                }
            }
        )
    }
}

private inline fun <T> List<T>.indexOfMinByOrNull(selector: (T) -> Double): Int? {
    if (isEmpty()) return null
    var minIdx = 0
    var minVal = selector(this[0])
    for (i in 1 until size) {
        val v = selector(this[i])
        if (v < minVal) {
            minVal = v
            minIdx = i
        }
    }
    return minIdx
}
