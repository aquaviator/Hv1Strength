package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.ui.viewmodel.StrengthViewModel
import com.example.BuildConfig
import com.example.data.AuthState
import com.example.data.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: StrengthViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToSyncDebug: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Collect settings states from view model
    val isMetric by viewModel.isMetric.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsState()
    val defaultRestTimerDuration by viewModel.defaultRestTimerDuration.collectAsState()
    val soundOn by viewModel.soundOn.collectAsState()
    val vibrationOn by viewModel.vibrationOn.collectAsState()
    val defaultWarmupSets by viewModel.defaultWarmupSets.collectAsState()
    val autoCompleteBehavior by viewModel.autoCompleteBehavior.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()
    val timerPreferences by viewModel.timerPreferences.collectAsState()

    // Import/Export dialog states
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var showCsvDialog by remember { mutableStateOf(false) }
    var exportedCsvText by remember { mutableStateOf("") }

    val userProfile by viewModel.activeUserProfile.collectAsState()

    Scaffold(
        topBar = {
            HighDensityHeader(
                title = "Settings",
                userProfile = userProfile,
                onProfileClick = onNavigateToProfile
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Account Profile Section
            val authState by viewModel.authState.collectAsState()
            SettingsSectionCard(title = "ACCOUNT & PROFILE") {
                val profile = (authState as? AuthState.Authenticated)?.profile
                val subtitle = if (profile != null) {
                    "Signed in as ${profile.displayName}"
                } else {
                    "Offline local mode. Click to sign in."
                }
                SettingsClickableRow(
                    icon = Icons.Default.AccountCircle,
                    title = if (profile != null) "Manage Profile & Data" else "Connect Google Account",
                    subtitle = subtitle,
                    onClick = onNavigateToProfile
                )
            }

            // General Settings Card
            SettingsSectionCard(title = "GENERAL") {
                // Metric Unit Toggle
                SettingsToggleRow(
                    icon = Icons.Default.Straighten,
                    title = "Metric Units (kg)",
                    subtitle = "Use kilograms instead of pounds",
                    checked = isMetric,
                    onCheckedChange = { viewModel.setMetric(it) }
                )

                // Theme Dropdown selection row
                var themeExpanded by remember { mutableStateOf(false) }
                SettingsClickableRow(
                    icon = Icons.Default.Palette,
                    title = "Theme Preferences",
                    subtitle = "Current: ${theme.replaceFirstChar { it.uppercase() }}",
                    onClick = { themeExpanded = true }
                ) {
                    DropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System Default") },
                            onClick = {
                                viewModel.setTheme("system")
                                themeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Light Theme") },
                            onClick = {
                                viewModel.setTheme("light")
                                themeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dark Theme") },
                            onClick = {
                                viewModel.setTheme("dark")
                                themeExpanded = false
                            }
                        )
                    }
                }

                // Keep Screen Awake Toggle
                SettingsToggleRow(
                    icon = Icons.Default.Lightbulb,
                    title = "Keep Screen Awake",
                    subtitle = "Prevent screen from dimming during workouts",
                    checked = keepScreenAwake,
                    onCheckedChange = { viewModel.setKeepScreenAwake(it) }
                )

                // Sound On/Off
                SettingsToggleRow(
                    icon = Icons.Default.VolumeUp,
                    title = "Sound Notifications",
                    subtitle = "Beep when rest timer reaches zero",
                    checked = soundOn,
                    onCheckedChange = { viewModel.setSoundOn(it) }
                )

                // Vibration On/Off
                SettingsToggleRow(
                    icon = Icons.Default.Vibration,
                    title = "Vibration Alerts",
                    subtitle = "Vibrate on active timers and checkpoints",
                    checked = vibrationOn,
                    onCheckedChange = { viewModel.setVibrationOn(it) }
                )
            }

            // Workout Preferences Card
            SettingsSectionCard(title = "WORKOUT PREFERENCES") {
                // Default Warmup Sets
                var showWarmupDialog by remember { mutableStateOf(false) }
                SettingsClickableRow(
                    icon = Icons.Default.FitnessCenter,
                    title = "Default Warm-up Sets",
                    subtitle = "Currently configured: $defaultWarmupSets sets",
                    onClick = { showWarmupDialog = true }
                )

                if (showWarmupDialog) {
                    var inputSets by remember { mutableStateOf(defaultWarmupSets.toString()) }
                    AlertDialog(
                        onDismissRequest = { showWarmupDialog = false },
                        title = { Text("Set Default Warmup Sets") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Configure how many warm-up sets are automatically generated for exercises.", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = inputSets,
                                    onValueChange = { inputSets = it.filter { char -> char.isDigit() } },
                                    label = { Text("Number of sets") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val sets = inputSets.toIntOrNull() ?: 0
                                viewModel.setDefaultWarmupSets(sets)
                                showWarmupDialog = false
                            }) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWarmupDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Default Rest Timer Selection
                var showRestTimerDialog by remember { mutableStateOf(false) }
                SettingsClickableRow(
                    icon = Icons.Default.HourglassEmpty,
                    title = "Default Rest Interval",
                    subtitle = "Currently configured: $defaultRestTimerDuration seconds",
                    onClick = { showRestTimerDialog = true }
                )

                if (showRestTimerDialog) {
                    var inputDuration by remember { mutableStateOf(defaultRestTimerDuration.toString()) }
                    AlertDialog(
                        onDismissRequest = { showRestTimerDialog = false },
                        title = { Text("Configure Default Rest Timer") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Select or enter custom rest countdown (seconds).", style = MaterialTheme.typography.bodyMedium)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val shortcuts = listOf(30, 45, 60, 90, 120, 180)
                                    shortcuts.take(3).forEach { shortcut ->
                                        TextButton(onClick = { inputDuration = shortcut.toString() }) {
                                            Text("${shortcut}s")
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val shortcuts = listOf(30, 45, 60, 90, 120, 180)
                                    shortcuts.drop(3).forEach { shortcut ->
                                        TextButton(onClick = { inputDuration = shortcut.toString() }) {
                                            Text("${shortcut}s")
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = inputDuration,
                                    onValueChange = { inputDuration = it.filter { char -> char.isDigit() } },
                                    label = { Text("Rest duration (seconds)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val dur = inputDuration.toIntOrNull() ?: 90
                                viewModel.setDefaultRestTimerDuration(dur)
                                showRestTimerDialog = false
                            }) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestTimerDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Auto Complete Behavior Toggle
                SettingsToggleRow(
                    icon = Icons.Default.Check,
                    title = "Auto-Complete Active Sets",
                    subtitle = "Auto-focus/advance when completing sets",
                    checked = autoCompleteBehavior,
                    onCheckedChange = { viewModel.setAutoCompleteBehavior(it) }
                )

                // Auto Scroll Behavior Toggle
                SettingsToggleRow(
                    icon = Icons.Default.SwapVert,
                    title = "Auto-Scroll list",
                    subtitle = "Auto-scroll viewport to active exercise card",
                    checked = autoScroll,
                    onCheckedChange = { viewModel.setAutoScroll(it) }
                )

                // Timer sound/vibration Preferences Selection
                var timerPrefExpanded by remember { mutableStateOf(false) }
                SettingsClickableRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Rest Timer Preferences",
                    subtitle = "Current preference: ${timerPreferences.replaceFirstChar { it.uppercase() }}",
                    onClick = { timerPrefExpanded = true }
                ) {
                    DropdownMenu(
                        expanded = timerPrefExpanded,
                        onDismissRequest = { timerPrefExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Standard Alert") },
                            onClick = {
                                viewModel.setTimerPreferences("standard")
                                timerPrefExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Loud Long Alarm") },
                            onClick = {
                                viewModel.setTimerPreferences("loud")
                                timerPrefExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Silent Flashing / Vibration") },
                            onClick = {
                                viewModel.setTimerPreferences("silent")
                                timerPrefExpanded = false
                            }
                        )
                    }
                }
            }

            // Data & Backup section
            SettingsSectionCard(title = "DATABASE BACKUP & EXPORT") {
                // Export JSON
                SettingsClickableRow(
                    icon = Icons.Default.CloudDownload,
                    title = "Export JSON Backup",
                    subtitle = "Generate offline backup JSON for security and migration",
                    onClick = {
                        coroutineScope.launch {
                            exportedJsonText = viewModel.exportData()
                            showExportDialog = true
                        }
                    }
                )

                // Export CSV
                SettingsClickableRow(
                    icon = Icons.Default.GridOn,
                    title = "Export Workout History to CSV",
                    subtitle = "Create spreadsheets of sets, weights, reps, and RPE",
                    onClick = {
                        coroutineScope.launch {
                            exportedCsvText = viewModel.exportDataToCsv()
                            showCsvDialog = true
                        }
                    }
                )

                // Import JSON
                SettingsClickableRow(
                    icon = Icons.Default.CloudUpload,
                    title = "Import JSON Backup",
                    subtitle = "Restore database and user settings from JSON copy",
                    onClick = {
                        importText = ""
                        showImportDialog = true
                    }
                )
            }

            // About Application Info Card
            SettingsSectionCard(title = "ABOUT HUMAN V1") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.human_logo),
                        contentDescription = "Human V1 Strength Logo",
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "HUMAN V1 STRENGTH",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Train. Track. Transform.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 1.dp)

                SettingsInfoRow(
                    icon = Icons.Default.Info,
                    title = "Application Version",
                    value = "v0.0.0.2 (Human Brand Release)"
                )
                SettingsInfoRow(
                    icon = Icons.Default.Settings,
                    title = "Room Database Schema",
                    value = "v2"
                )
                SettingsInfoRow(
                    icon = Icons.Default.Security,
                    title = "Offline Privacy",
                    value = "100% Client-Side. No telemetry logs."
                )
                SettingsInfoRow(
                    icon = Icons.Default.Code,
                    title = "License",
                    value = "Open-source under MIT"
                )
            }

            // Developer Tools Section Card
            if (BuildConfig.DEBUG) {
                SettingsSectionCard(title = "DEVELOPER TOOLS") {
                    SettingsClickableRow(
                        icon = Icons.Default.BugReport,
                        title = "Sync Debug Utility",
                        subtitle = "Inspect offline identity, command queue, and sync metadata",
                        onClick = onNavigateToSyncDebug
                    )

                    var showClientIdDialog by remember { mutableStateOf(false) }
                    val sharedPrefs = context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE)
                    val defaultClientId = remember(context) {
                        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                        if (resId != 0) context.getString(resId) else "596361666131-4bdc26e3rrupaag2cn3tqcmlqdcdjqs8.apps.googleusercontent.com"
                    }
                    var currentClientId by remember { mutableStateOf(sharedPrefs.getString("google_web_client_id", defaultClientId) ?: defaultClientId) }

                    SettingsClickableRow(
                        icon = Icons.Default.VpnKey,
                        title = "Google Web Client ID",
                        subtitle = "Configure OAuth Client ID for live Google Sign-In",
                        onClick = { showClientIdDialog = true }
                    )

                    if (showClientIdDialog) {
                        var tempClientId by remember { mutableStateOf(currentClientId) }
                        AlertDialog(
                            onDismissRequest = { showClientIdDialog = false },
                            title = { Text("Google Web Client ID") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Enter your OAuth Web Client ID from the Google/Firebase Console to test live authentication:", style = MaterialTheme.typography.bodyMedium)
                                    OutlinedTextField(
                                        value = tempClientId,
                                        onValueChange = { tempClientId = it },
                                        label = { Text("Web Client ID") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "Package: com.aistudio.humanstrength.kfqjza\nEnsure this package and your debug SHA-1 signing fingerprint are correctly configured in your Console.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    sharedPrefs.edit().putString("google_web_client_id", tempClientId).apply()
                                    currentClientId = tempClientId
                                    showClientIdDialog = false
                                    Toast.makeText(context, "Google Web Client ID Saved", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Save")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClientIdDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Export JSON dialog with Clipboard support
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("JSON Backup Ready") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Copy this backup string and save it securely in a safe location. You can paste this backup later to restore your metrics completely.")
                    OutlinedTextField(
                        value = exportedJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        label = { Text("Backup Data") }
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("copy_json_backup_button"),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Human V1 Backup", exportedJsonText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Export CSV dialog with Clipboard support
    if (showCsvDialog) {
        AlertDialog(
            onDismissRequest = { showCsvDialog = false },
            title = { Text("CSV Spreadsheet Ready") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your workout logs database is reformatted into tidy CSV rows. Copy it to import directly into Google Sheets or Microsoft Excel.")
                    OutlinedTextField(
                        value = exportedCsvText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        label = { Text("CSV Rows") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Human V1 CSV Export", exportedCsvText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "CSV copied to clipboard!", Toast.LENGTH_SHORT).show()
                        showCsvDialog = false
                    }
                ) {
                    Text("Copy CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Import JSON Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Database JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Paste your previously copied backup JSON string below to completely restore workouts, templates, measurements, and preferences.")
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text("Paste JSON backup string here...") }
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("confirm_import_button"),
                    onClick = {
                        if (importText.trim().isEmpty()) {
                            Toast.makeText(context, "Input is empty!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.importData(
                            jsonStr = importText,
                            onSuccess = { result ->
                                val summary = "Your data backup has been successfully restored:\n\n" +
                                        "• Completed Workouts: ${result.sessionsImported}\n" +
                                        "• Workout Templates: ${result.templatesImported}\n" +
                                        "• Custom Exercises: ${result.exercisesImported}\n" +
                                        "• Measurements: ${result.measurementsImported}\n" +
                                        "• Skipped Duplicates: ${result.skippedDuplicates}\n" +
                                        "• Failed Records: ${result.failedRecords}"
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Restore Summary")
                                    .setMessage(summary)
                                    .setPositiveButton("Awesome") { d, _ -> d.dismiss() }
                                    .show()
                                showImportDialog = false
                            },
                            onError = { errorMsg ->
                                Toast.makeText(context, "Import failed: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    Text("Validate & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("switch_${title.replace(" ", "_").lowercase()}")
        )
    }
}

@Composable
fun SettingsClickableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    content: @Composable (BoxScope.() -> Unit)? = null
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (content != null) {
            content()
        }
    }
}

@Composable
fun SettingsInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
