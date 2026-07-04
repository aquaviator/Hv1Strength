package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.identity.DeviceIdGenerator
import com.example.core.identity.HumanUserIdGenerator
import com.example.core.sync.SyncManager
import com.example.data.CommandQueueEntity
import com.example.ui.viewmodel.StrengthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDebugScreen(
    viewModel: StrengthViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Real-time Cloud Sync Observability States
    val syncStatus by SyncManager.currentStatus.collectAsState()
    val syncPendingUploads by SyncManager.pendingUploads.collectAsState()
    val syncPendingDownloads by SyncManager.pendingDownloads.collectAsState()
    val syncQueueSize by SyncManager.queueSize.collectAsState()
    val syncLastSync by SyncManager.lastSync.collectAsState()
    val syncLastError by SyncManager.lastError.collectAsState()
    val syncConflictCount by SyncManager.conflictCount.collectAsState()
    val syncLastSuccessfulUpload by SyncManager.lastSuccessfulUpload.collectAsState()
    val syncLastSuccessfulDownload by SyncManager.lastSuccessfulDownload.collectAsState()
    val parentWarnings by SyncManager.parentWarnings.collectAsState()

    // Debug State Variables
    var humanUserId by remember { mutableStateOf("Loading...") }
    var deviceId by remember { mutableStateOf("Loading...") }
    var allCommands by remember { mutableStateOf<List<CommandQueueEntity>>(emptyList()) }
    
    // Counts for pending records
    var pendingUploadCount by remember { mutableStateOf(0) }
    var pendingDeleteCount by remember { mutableStateOf(0) }
    
    // Sample details for Revisions and Sync States
    var revisionInfoList by remember { mutableStateOf<List<String>>(emptyList()) }

    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        coroutineScope.launch {
            // Get IDs
            humanUserId = HumanUserIdGenerator.getOrGenerateOfflineHumanId(context)
            deviceId = DeviceIdGenerator.getOrGenerateDeviceId(context)

            // Get DB Instance safely
            val db = com.example.data.StrengthDatabase.getDatabase(context, coroutineScope)
            val dao = db.strengthDao()

            // Fetch All Commands in history/queue
            val commands = withContext(Dispatchers.IO) {
                dao.getAllCommands()
            }
            allCommands = commands

            // Calculate pending uploads and deletes
            val profilesUp = withContext(Dispatchers.IO) { dao.getPendingUploadProfiles().size }
            val weightsUp = withContext(Dispatchers.IO) { dao.getPendingUploadBodyWeights().size }
            val weightsDel = withContext(Dispatchers.IO) { dao.getPendingDeleteBodyWeights().size }
            val tapeUp = withContext(Dispatchers.IO) { dao.getPendingUploadTapeMeasurements().size }
            val tapeDel = withContext(Dispatchers.IO) { dao.getPendingDeleteTapeMeasurements().size }
            val templatesUp = withContext(Dispatchers.IO) { dao.getPendingUploadTemplates().size }
            val templatesDel = withContext(Dispatchers.IO) { dao.getPendingDeleteTemplates().size }
            val exercisesUp = withContext(Dispatchers.IO) { dao.getPendingUploadExercises().size }
            val exercisesDel = withContext(Dispatchers.IO) { dao.getPendingDeleteExercises().size }
            val sessionsUp = withContext(Dispatchers.IO) { dao.getPendingUploadSessions().size }
            val sessionsDel = withContext(Dispatchers.IO) { dao.getPendingDeleteSessions().size }
            val setsUp = withContext(Dispatchers.IO) { dao.getPendingUploadLoggedSets().size }
            val setsDel = withContext(Dispatchers.IO) { dao.getPendingDeleteLoggedSets().size }

            pendingUploadCount = profilesUp + weightsUp + tapeUp + templatesUp + exercisesUp + sessionsUp + setsUp
            pendingDeleteCount = weightsDel + tapeDel + templatesDel + exercisesDel + sessionsDel + setsDel

            // Fetch a few revision samples
            val sampleWeights = withContext(Dispatchers.IO) { dao.getPendingUploadBodyWeights() }
            val sampleTemplates = withContext(Dispatchers.IO) { dao.getPendingUploadTemplates() }

            val infoList = mutableListOf<String>()
            if (sampleWeights.isNotEmpty()) {
                infoList.add("BodyWeight Sample: Global ID = ${sampleWeights.first().globalId.take(15)}..., Rev = ${sampleWeights.first().revision}, Status = ${sampleWeights.first().syncStatus}")
            }
            if (sampleTemplates.isNotEmpty()) {
                infoList.add("Template Sample: Global ID = ${sampleTemplates.first().globalId.take(15)}..., Rev = ${sampleTemplates.first().revision}, Status = ${sampleTemplates.first().syncStatus}")
            }
            if (infoList.isEmpty()) {
                infoList.add("No unsynced active records found to sample.")
            }
            revisionInfoList = infoList
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Sync Diagnostics Panel", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Identifiers Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PLATFORM IDENTITIES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    IdentityRow(label = "Current Human User ID", value = humanUserId, context = context)
                    IdentityRow(label = "Current Device ID", value = deviceId, context = context)
                }
            }

            // 2. Parent Missing Warnings Card (If any)
            if (parentWarnings.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warnings",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                "PARENT RELATIONSHIP RESOLUTION WARNINGS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                        parentWarnings.forEach { warning ->
                            Text(
                                text = "• $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // 3. Sync Metadata & Queue Summary Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("LOCAL STATE METRICS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    StatRow(label = "Pending Local Uploads (Dirty)", count = pendingUploadCount)
                    StatRow(label = "Pending Local Deletes (Soft-Deleted)", count = pendingDeleteCount)
                    StatRow(label = "Total Commands in Queue", count = allCommands.size)
                    
                    val pendingCmds = allCommands.count { it.status == "PENDING" }
                    val failedCmds = allCommands.count { it.status == "FAILED" }
                    val poisonedCmds = allCommands.count { it.status == "POISONED" }
                    val succeededCmds = allCommands.count { it.status == "SUCCEEDED" }
                    val processingCmds = allCommands.count { it.status == "PROCESSING" }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusBadge(label = "PENDING", count = pendingCmds, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                        StatusBadge(label = "FAILED", count = failedCmds, color = Color(0xFFE5A93B), modifier = Modifier.weight(1f))
                        StatusBadge(label = "POISONED", count = poisonedCmds, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        StatusBadge(label = "SUCCEEDED", count = succeededCmds, color = Color(0xFF4CAF50), modifier = Modifier.weight(1f))
                    }
                }
            }

            // 4. Real-Time Cloud Sync Metrics Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CLOUD SYNC STATS (REAL-TIME)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Sync Status", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = syncStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (syncStatus) {
                                "Idle" -> Color(0xFF4CAF50)
                                "Uploading", "Downloading" -> MaterialTheme.colorScheme.secondary
                                "Offline" -> MaterialTheme.colorScheme.outline
                                "Error" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Last Error Message", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = syncLastError ?: "None",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (syncLastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
                        )
                    }

                    StatRow(label = "Active Cloud Uploads", count = syncPendingUploads)
                    StatRow(label = "Active Cloud Downloads", count = syncPendingDownloads)
                    StatRow(label = "Detected Conflict Count", count = syncConflictCount)

                    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Last Successful Upload", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = syncLastSuccessfulUpload?.let { sdf.format(Date(it)) } ?: "Never",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Last Successful Download", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = syncLastSuccessfulDownload?.let { sdf.format(Date(it)) } ?: "Never",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val db = com.example.data.StrengthDatabase.getDatabase(context, coroutineScope)
                                val repo = com.example.data.StrengthRepository(db.strengthDao(), context)
                                val syncEngine = com.example.core.sync.SyncEngineImpl(context, repo)
                                syncEngine.synchronizeAll()
                                refreshTrigger++
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Trigger Manual Cloud Sync", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 5. Samples & Revision Tracking
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("REVISIONS & CONFLICT STATES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    revisionInfoList.forEach { info ->
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                        )
                    }
                }
            }

            // 6. Commands Queue List Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("OFFLINE COMMAND ARCHIVE (${allCommands.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    if (allCommands.isEmpty()) {
                        Box(
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No command records exist in database", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    } else {
                        allCommands.forEach { command ->
                            CommandItemView(command)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        Text(text = count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
fun IdentityRow(label: String, value: String, context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(label, value)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy to clipboard",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun StatRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (count > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CommandItemView(command: CommandQueueEntity) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(command.createdAt) { sdf.format(Date(command.createdAt)) }

    val statusColor = when (command.status) {
        "SUCCEEDED" -> Color(0xFF4CAF50)
        "PENDING" -> MaterialTheme.colorScheme.outline
        "PROCESSING" -> MaterialTheme.colorScheme.secondary
        "FAILED" -> Color(0xFFE5A93B)
        "POISONED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command.commandType,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = command.status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }

        Text("Command ID: ${command.commandId}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Text("Target Entity: ${command.entityType} (${command.entityGlobalId})", style = MaterialTheme.typography.bodySmall)
        Text("Attempts: ${command.attempts}/5", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        
        command.nextRetryAt?.let { retryTime ->
            Text(
                text = "Next Retry Scheduled At: ${sdf.format(Date(retryTime))}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE5A93B)
            )
        }

        command.errorMessage?.let { error ->
            Text(
                text = "Last Error: $error",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text("Device ID: ${command.originDeviceId}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Text("Payload: ${command.payloadJson}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Enqueued: $formattedDate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
    }
}
