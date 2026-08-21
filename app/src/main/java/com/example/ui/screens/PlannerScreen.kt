package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.PlannedWorkout
import com.example.data.WorkoutTemplate
import com.example.planner.TrainingPlanner
import com.example.ui.viewmodel.StrengthViewModel
import com.example.core.sync.SyncManager
import com.example.ui.components.ExperienceStatusCard
import com.example.ui.presentation.syncPresentation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(viewModel: StrengthViewModel, notificationOccurrenceId: String? = null,
    onNotificationHandled: () -> Unit = {}, onBack: () -> Unit, onWorkoutStarted: () -> Unit) {
    val planned by viewModel.plannedWorkouts.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val adherence by viewModel.plannerAdherence.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val syncStatus by SyncManager.currentStatus.collectAsState()
    val syncQueueSize by SyncManager.queueSize.collectAsState()
    val syncError by SyncManager.lastError.collectAsState()
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var showSchedule by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlannedWorkout?>(null) }
    var editTarget by remember { mutableStateOf<PlannedWorkout?>(null) }
    var editPlan by remember { mutableStateOf<com.example.data.TrainingPlan?>(null) }
    var routineTarget by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var routineDetails by remember { mutableStateOf<List<StrengthViewModel.TemplateExerciseState>>(emptyList()) }
    LaunchedEffect(editTarget?.seriesId) { editPlan = editTarget?.let { viewModel.plannerViewModel.getPlan(it.seriesId) } }
    LaunchedEffect(routineTarget?.id) { routineDetails = routineTarget?.let { viewModel.getTemplateDetails(it.id) } ?: emptyList() }
    val start = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
    val visible = planned.filter { it.scheduledEpochDay in start.toEpochDay()..start.plusDays(6).toEpochDay() }
    val notificationTarget = planned.firstOrNull { it.id == notificationOccurrenceId }
    LaunchedEffect(notificationOccurrenceId, notificationTarget?.id) {
        if (notificationOccurrenceId != null) {
            notificationTarget?.let { anchor = LocalDate.ofEpochDay(it.scheduledEpochDay) }
            onNotificationHandled()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.navigateToActiveWorkoutEvent.collect { onWorkoutStarted() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Training plan") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = { TextButton(onClick = { anchor = LocalDate.now() }) { Text("Today") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { showSchedule = true }) { Icon(Icons.Default.Add, "Schedule workout") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (notificationOccurrenceId != null) item {
                Card(Modifier.fillMaxWidth().semantics { contentDescription = "Reminder destination" }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Workout reminder", style = MaterialTheme.typography.titleMedium)
                        Text(notificationTarget?.let {
                            val date = LocalDate.ofEpochDay(it.scheduledEpochDay).format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
                            "$date · ${it.routineName} · ${TrainingPlanner.visibleStatus(it, LocalDate.now().toEpochDay()).name.lowercase().replaceFirstChar(Char::uppercase)}"
                        } ?: "This planned workout is no longer available.")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { anchor = anchor.minusWeeks(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week") }
                    Text("${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))}", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { anchor = anchor.plusWeeks(1) }) { Icon(Icons.Default.ArrowForward, "Next week") }
                }
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (0L..6L).forEach { offset ->
                        val day = start.plusDays(offset)
                        val count = planned.count { it.scheduledEpochDay == day.toEpochDay() }
                        AssistChip(onClick = { anchor = day }, label = { Text("${day.dayOfWeek.name.take(1)}\n${day.dayOfMonth}${if (count > 0) " •$count" else ""}") },
                            modifier = Modifier.semantics { contentDescription = "${day.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))}, $count planned" })
                    }
                }
            }
            item {
                ExperienceStatusCard(syncPresentation(authState, syncStatus, syncQueueSize, syncError), "planner_sync_status")
            }
            item {
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Adherence", style = MaterialTheme.typography.titleMedium)
                    Text(if (adherence.completionPercent == null) "Complete planned sessions to build an adherence summary." else "${adherence.completed} of ${adherence.planned} planned sessions completed (${adherence.completionPercent}%).")
                    Text("${adherence.skipped} skipped · ${adherence.missed} currently missed · ${adherence.currentWeekCompleted} completed this week")
                    Text(adherence.recentTrend, style = MaterialTheme.typography.bodySmall)
                } }
            }
            if (visible.isEmpty()) item {
                Card(Modifier.fillMaxWidth().clickable { showSchedule = true }) { Column(Modifier.padding(20.dp)) {
                    Text("No workouts planned this week", style = MaterialTheme.typography.titleMedium)
                    Text("Schedule workout", color = MaterialTheme.colorScheme.primary)
                } }
            } else {
                items(visible, key = { it.id }) { item ->
                    PlannedWorkoutCard(item, LocalDate.now(), onStart = {
                        viewModel.startPlannedWorkout(item)
                    }, onSkip = { viewModel.plannerViewModel.skip(item) },
                        onReschedule = { editTarget = item },
                        onDelete = { deleteTarget = item },
                        onView = { routineTarget = templates.firstOrNull { it.globalId == item.templateGlobalId } },
                        routineAvailable = templates.any { it.globalId == item.templateGlobalId },
                        onRestore = { viewModel.plannerViewModel.restoreSkipped(item) })
                }
            }
        }
    }
    if (showSchedule) ScheduleDialog(templates, onDismiss = { showSchedule = false }) { template, date, minute, days, end, reminder ->
        viewModel.scheduleRoutine(template, date, minute, days, end, reminder); showSchedule = false
    }
    editTarget?.let { item -> EditOccurrenceDialog(item, editPlan, templates, onDismiss = { editTarget = null }) { template, date, minute, future, days, end, reminder ->
        if (future) viewModel.plannerViewModel.editFuture(item, template, date, minute, days, end, reminder)
        else viewModel.plannerViewModel.update(item.copy(templateId = template.id, templateGlobalId = template.globalId,
            routineName = template.name, scheduledEpochDay = date.toEpochDay(), preferredMinuteOfDay = minute,
            detachedFromSeries = true, reminderEnabled = reminder && minute != null))
        editTarget = null
    } }
    deleteTarget?.let { item -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Remove planned workout?") },
        text = { Text("Remove only this occurrence or this and future planned occurrences? Completed history is never changed.") },
        confirmButton = { TextButton(onClick = { viewModel.plannerViewModel.deleteOne(item); deleteTarget = null }) { Text("This occurrence") } },
        dismissButton = { TextButton(onClick = { viewModel.plannerViewModel.deleteFuture(item); deleteTarget = null }) { Text("Future series") } }) }
    routineTarget?.let { template -> AlertDialog(onDismissRequest = { routineTarget = null }, title = { Text(template.name) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (routineDetails.isEmpty()) Text("This routine currently has no available exercises.")
            else routineDetails.forEachIndexed { index, detail -> Text("${index + 1}. ${exercises.firstOrNull { it.id == detail.exerciseId }?.name ?: "Exercise unavailable"}") }
            Text("This is the current routine definition used when the planned workout starts.", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { routineTarget = null }) { Text("Back to planner") } }) }
}

@Composable private fun PlannedWorkoutCard(item: PlannedWorkout, today: LocalDate, onStart: () -> Unit, onSkip: () -> Unit, onReschedule: () -> Unit, onDelete: () -> Unit,
    onView: () -> Unit, routineAvailable: Boolean, onRestore: () -> Unit) {
    val status = TrainingPlanner.visibleStatus(item, today.toEpochDay()).name.lowercase().replaceFirstChar { it.uppercase() }
    val date = LocalDate.ofEpochDay(item.scheduledEpochDay)
    Card(Modifier.fillMaxWidth().semantics { contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))}, ${item.routineName}, $status" }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), style = MaterialTheme.typography.labelLarge)
            Text(item.routineName, style = MaterialTheme.typography.titleMedium)
            Text(item.preferredMinuteOfDay?.let { LocalTime.of(it / 60, it % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }
                ?: "No preferred time")
            Text(status)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.status == "PLANNED") Button(onClick = onStart, enabled = routineAvailable) { Icon(Icons.Default.PlayArrow, null); Text(if (date < today) "Start late" else "Start") }
                TextButton(onClick = onView, enabled = routineAvailable) { Text(if (routineAvailable) "View routine" else "Routine unavailable") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mutable = item.status != "COMPLETED"
                TextButton(onClick = onReschedule, enabled = mutable) { Text("Reschedule") }
                TextButton(onClick = onSkip, enabled = item.status == "PLANNED") { Text("Skip") }
                if (item.status == "SKIPPED") TextButton(onClick = onRestore) { Text("Restore") }
                TextButton(onClick = onDelete, enabled = mutable) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ScheduleDialog(templates: List<WorkoutTemplate>, onDismiss: () -> Unit,
    onSave: (WorkoutTemplate, LocalDate, Int?, Set<DayOfWeek>, LocalDate?, Boolean) -> Unit) {
    var selected by remember { mutableStateOf<WorkoutTemplate?>(templates.firstOrNull()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var weekly by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(setOf(LocalDate.now().dayOfWeek)) }
    var reminder by remember { mutableStateOf(false) }
    var minute by remember { mutableStateOf<Int?>(null) }
    var noEnd by remember { mutableStateOf(false) }
    var end by remember(date) { mutableStateOf(date.plusWeeks(12)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Schedule workout") }, text = { Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (templates.isEmpty()) Text("Create a routine before scheduling a workout.") else templates.forEach { t ->
            FilterChip(selected = selected?.id == t.id, onClick = { selected = t }, label = { Text(t.name) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { date = date.minusDays(1) }) { Text("Previous day") }; Text(date.toString()); TextButton(onClick = { date = date.plusDays(1) }) { Text("Next day") } }
        Row { Checkbox(weekly, { weekly = it }); Text("Repeat weekly") }
        if (weekly) {
            Text("Repeat on")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { DayOfWeek.entries.forEach { day -> FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(day.name.take(2)) }) } }
            Row { Checkbox(noEnd, { noEnd = it }); Text("No end date") }
            if (!noEnd) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { end = end.minusDays(1) }) { Text("Earlier end") }; Text(end.toString()); TextButton(onClick = { end = end.plusDays(1) }) { Text("Later end") } }
        }
        PreferredTimeSelector(minute) { minute = it; if (it == null) reminder = false }
        Row { Checkbox(reminder, { reminder = it }, enabled = minute != null); Text("Remind me at the preferred time") }
        Text("Workouts use the routine's current definition when started.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { Button(onClick = { selected?.let { onSave(it, date, minute, if (weekly) days else emptySet(), if (weekly && !noEnd) end else null, reminder) } }, enabled = selected != null && (!weekly || (days.isNotEmpty() && (noEnd || !end.isBefore(date))))) { Text("Schedule") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun EditOccurrenceDialog(item: PlannedWorkout, plan: com.example.data.TrainingPlan?, templates: List<WorkoutTemplate>, onDismiss: () -> Unit,
    onSave: (WorkoutTemplate, LocalDate, Int?, Boolean, Set<DayOfWeek>, LocalDate?, Boolean) -> Unit) {
    var selected by remember { mutableStateOf(templates.firstOrNull { it.globalId == item.templateGlobalId } ?: templates.firstOrNull()) }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(item.scheduledEpochDay)) }
    var minute by remember { mutableStateOf(item.preferredMinuteOfDay) }
    var future by remember { mutableStateOf(false) }
    var days by remember(plan?.weekdaysMask) { mutableStateOf<Set<DayOfWeek>>(DayOfWeek.entries.filterTo(mutableSetOf()) { plan?.let { p -> TrainingPlanner.includes(p.weekdaysMask, it) } == true }.ifEmpty { mutableSetOf(date.dayOfWeek) }) }
    var noEnd by remember(plan?.recurrenceEndEpochDay) { mutableStateOf(plan?.recurrenceEndEpochDay == null) }
    var end by remember(plan?.recurrenceEndEpochDay) { mutableStateOf(plan?.recurrenceEndEpochDay?.let(LocalDate::ofEpochDay) ?: date.plusWeeks(12)) }
    var reminder by remember { mutableStateOf(item.reminderEnabled) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit planned workout") }, text = { Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        templates.forEach { t -> FilterChip(selected = selected?.id == t.id, onClick = { selected = t }, label = { Text(t.name) }) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { date = date.minusDays(1) }) { Text("Previous day") }; Text(date.toString()); TextButton(onClick = { date = date.plusDays(1) }) { Text("Next day") } }
        PreferredTimeSelector(minute) { minute = it }
        if (!item.detachedFromSeries) {
            Row { RadioButton(!future, { future = false }); Text("This workout only") }
            Row { RadioButton(future, { future = true }); Text("This and future workouts") }
            if (future) {
                Text("First affected workout: ${LocalDate.ofEpochDay(item.scheduledEpochDay)}. Completed, skipped, deleted and separately rescheduled history will not change.")
                Text("Repeat on")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { DayOfWeek.entries.forEach { day -> FilterChip(day in days, { days = if (day in days) days - day else days + day }, { Text(day.name.take(2)) }) } }
                Row { Checkbox(noEnd, { noEnd = it }); Text("No end date") }
                if (!noEnd) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { end = end.minusDays(1) }) { Text("Earlier end") }; Text(end.toString()); TextButton(onClick = { end = end.plusDays(1) }) { Text("Later end") } }
            }
        }
        Row { Checkbox(reminder, { reminder = it }, enabled = minute != null); Text("Remind me at the preferred time") }
    } }, confirmButton = { Button(onClick = { selected?.let { onSave(it, date, minute, future, days, if (noEnd) null else end, reminder) } },
        enabled = selected != null && (!future || (days.isNotEmpty() && (noEnd || !end.isBefore(date))))) { Text(if (future) "Update future workouts" else "Update workout") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PreferredTimeSelector(value: Int?, onValueChange: (Int?) -> Unit) {
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    var showPicker by remember { mutableStateOf(false) }
    val label = value?.let { LocalTime.of(it / 60, it % 60).format(formatter) } ?: "No preferred time"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Preferred time (you can still start at any time)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.semantics { contentDescription = "Choose preferred time, currently $label" }) { Text(label) }
            if (value != null) TextButton(onClick = { onValueChange(null) }) { Text("No preferred time") }
        }
    }
    if (showPicker) {
        val initial = value ?: (9 * 60)
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(onDismissRequest = { showPicker = false }, title = { Text("Choose preferred time") },
            text = { TimePicker(state = state, modifier = Modifier.semantics { contentDescription = "Preferred workout time" }) },
            confirmButton = { TextButton(onClick = { onValueChange(state.hour * 60 + state.minute); showPicker = false }) { Text("Use time") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } })
    }
}
