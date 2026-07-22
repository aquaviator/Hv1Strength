package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.StrengthViewModel
import com.example.data.AuthState
import com.example.data.UserProfile
import com.example.data.initials
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: StrengthViewModel,
    onNavigateBack: () -> Unit,
    onSignOutComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authState by viewModel.authState.collectAsState()
    val userProfile by viewModel.activeUserProfile.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    val isTrialExpired by viewModel.isTrialExpired.collectAsState()
    val simulateTrialExpired by viewModel.simulateTrialExpired.collectAsState()

    var showSignOutDialog by remember { mutableStateOf(false) }
    var deleteLocalDataOnSignOut by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    
    // Data export / delete dialog states
    var showExportJsonDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var showExportCsvDialog by remember { mutableStateOf(false) }
    var exportedCsvText by remember { mutableStateOf("") }
    var showDeleteLocalDataDialog by remember { mutableStateOf(false) }
    var showPlayBillingComingSoonDialog by remember { mutableStateOf(false) }

    // Navigation back if session is cleared
    LaunchedEffect(authState) {
        if (authState is AuthState.Initial) {
            onSignOutComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Profile", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("profile_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // SECTION 1: ACCOUNT
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("profile_account_section"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (userProfile?.authProvider == "google") "GOOGLE PROFILE" else "LOCAL PROFILE",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // User Avatar
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                )
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            var imageLoaded by remember { mutableStateOf(false) }

                            if (!userProfile?.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = userProfile?.photoUrl,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    onSuccess = { imageLoaded = true },
                                    onError = { imageLoaded = false }
                                )
                            }

                            if (userProfile?.photoUrl.isNullOrBlank() || !imageLoaded) {
                                Text(
                                    text = userProfile?.initials ?: "U",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 24.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.displayName ?: "Local Athlete",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("profile_display_name")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = userProfile?.email ?: "Offline Mode (No cloud backup)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("profile_email")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ProfileInfoRow(
                        icon = Icons.Default.VerifiedUser,
                        label = "Profile Type",
                        value = if (userProfile?.authProvider == "google") "Google Account" else "Local Profile"
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.VerifiedUser,
                        label = "Storage",
                        value = if (userProfile?.authProvider == "google") "Cloud synchronization enabled" else "Data stored on this device"
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Joined At",
                        value = if (userProfile != null) {
                            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(userProfile!!.createdAt))
                        } else "N/A"
                    )
                }
            }

            // ==========================================
            // SECTION 2: MEMBERSHIP
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("profile_membership_section"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "MEMBERSHIP",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardMembership,
                            contentDescription = "Membership status",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Subscription system not yet active",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "One-month full-access trial and annual membership are coming soon.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Planned UK price: £24 per year. All premium synchronization, coaching metrics, and active statistics tracking will be available standard during the preview period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ==========================================
            // SECTION 3: TRAINING
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("profile_training_section"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TRAINING",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.Scale,
                        label = "Preferred weight unit",
                        value = if (isMetric) "Metric (kg)" else "Imperial (lbs)"
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.TrendingUp,
                        label = "Experience level",
                        value = if (!userProfile?.trainingExperience.isNullOrBlank()) userProfile!!.trainingExperience!!.replaceFirstChar { it.uppercase() } else "Not set"
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.Cake,
                        label = "Date of birth",
                        value = if (!userProfile?.dateOfBirth.isNullOrBlank()) userProfile!!.dateOfBirth!! else "Not set"
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.Person,
                        label = "Assigned sex",
                        value = if (!userProfile?.sex.isNullOrBlank()) userProfile!!.sex!!.replaceFirstChar { it.uppercase() } else "Not set"
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { showEditProfileDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_profile_metadata_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Training & Bio", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ==========================================
            // SECTION 4: DATA
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("profile_data_section"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DATA",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.History,
                        label = "Workout history",
                        value = "${sessions.size} sessions logged"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Export JSON Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    exportedJsonText = viewModel.exportData()
                                    showExportJsonDialog = true
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("Export workout data as JSON", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Export CSV Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    exportedCsvText = viewModel.exportDataToCsv()
                                    showExportCsvDialog = true
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("Export workout history to CSV", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Delete Local Data Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteLocalDataDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text("Delete local workout data", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ==========================================
            // SECTION 5: SUPPORT
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("profile_support_section"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SUPPORT",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Contact Support Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "Support portal is coming soon!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Help & Support", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Coming soon", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }

                    // Privacy Policy Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "Privacy Policy is coming soon!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Privacy Policy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Coming soon", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }

                    // Terms of Service Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "Terms of Service are coming soon!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Terms of Service", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Coming soon", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Subscription Management Row (COMING SOON)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPlayBillingComingSoonDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SettingsApplications, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Subscription Management", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Coming soon (Candidate 4B)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Actions Link/Merge local data
            if (userProfile?.authProvider == "google") {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("profile_link_section"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Link Existing Local Data",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "If you logged workouts offline previously, you can merge and assign those sessions directly to your Google profile now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { showLinkDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("link_offline_data_button")
                        ) {
                            Text("Link Offline Data", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Danger Zone Sign Out
            Button(
                onClick = { showSignOutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("profile_signout_button")
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out of Account", fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Sign Out Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out Confirmation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Are you sure you want to sign out? Your current session will be closed.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = !deleteLocalDataOnSignOut,
                            onCheckedChange = { deleteLocalDataOnSignOut = !it },
                            modifier = Modifier.testTag("keep_data_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Keep local database records", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Safely preserve all sessions on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_signout_button"),
                    onClick = {
                        coroutineScope.launch {
                            viewModel.authRepository.signOut(keepLocalData = !deleteLocalDataOnSignOut)
                            Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                            showSignOutDialog = false
                        }
                    }
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Link Offline Data Dialog
    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Merge & Link Offline Records") },
            text = {
                Text(
                    text = "This will find any workout templates, sessions, body weights, and tape measurements labeled as 'offline' or unassigned on this device, and associate them with your active profile (${userProfile?.displayName}). Are you sure you want to proceed?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("confirm_link_button"),
                    onClick = {
                        coroutineScope.launch {
                            if (userProfile != null) {
                                viewModel.authRepository.linkOfflineDataToUser(userProfile!!.id)
                                Toast.makeText(context, "Offline data successfully merged!", Toast.LENGTH_LONG).show()
                            }
                            showLinkDialog = false
                        }
                    }
                ) {
                    Text("Link & Merge", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Profile details Dialog
    if (showEditProfileDialog) {
        if (userProfile != null) {
            var name by remember { mutableStateOf(userProfile!!.displayName ?: "") }
            var dob by remember { mutableStateOf(userProfile!!.dateOfBirth ?: "") }
            var sex by remember { mutableStateOf(userProfile!!.sex ?: "unspecified") }
            var exp by remember { mutableStateOf(userProfile!!.trainingExperience ?: "beginner") }

            AlertDialog(
                onDismissRequest = { showEditProfileDialog = false },
                title = { Text("Edit Profile Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_edit_name_input")
                        )

                        OutlinedTextField(
                            value = dob,
                            onValueChange = { dob = it },
                            label = { Text("Date of Birth (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_edit_dob_input")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Assigned Sex:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Male", "Female", "Other").forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (sex.lowercase() == item.lowercase()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (sex.lowercase() == item.lowercase()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { sex = item.lowercase() }
                                            .testTag("sex_option_${item.lowercase()}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            RadioButton(
                                                selected = sex.lowercase() == item.lowercase(),
                                                onClick = { sex = item.lowercase() }
                                            )
                                            Text(
                                                text = item,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Training Experience:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Beginner", "Intermediate", "Advanced").forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (exp.lowercase() == item.lowercase()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (exp.lowercase() == item.lowercase()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { exp = item.lowercase() }
                                            .testTag("exp_option_${item.lowercase()}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            RadioButton(
                                                selected = exp.lowercase() == item.lowercase(),
                                                onClick = { exp = item.lowercase() }
                                            )
                                            Text(
                                                text = item,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        modifier = Modifier.testTag("save_profile_details_button"),
                        onClick = {
                            viewModel.updateUserProfileBio(name, dob, sex, exp)
                            Toast.makeText(context, "Profile details saved!", Toast.LENGTH_SHORT).show()
                            showEditProfileDialog = false
                        }
                    ) {
                        Text("Save Details", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditProfileDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // Export JSON Dialog
    if (showExportJsonDialog) {
        AlertDialog(
            onDismissRequest = { showExportJsonDialog = false },
            title = { Text("Export Backup (JSON)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copy the backup string below to save your metrics safely offline:")
                    OutlinedTextField(
                        value = exportedJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Human Strength JSON Backup", exportedJsonText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                        showExportJsonDialog = false
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportJsonDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Export CSV Dialog
    if (showExportCsvDialog) {
        AlertDialog(
            onDismissRequest = { showExportCsvDialog = false },
            title = { Text("Export Workouts (CSV)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copy your workouts history CSV string below to use in spreadsheets:")
                    OutlinedTextField(
                        value = exportedCsvText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Human Strength CSV History", exportedCsvText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "CSV copied to clipboard", Toast.LENGTH_SHORT).show()
                        showExportCsvDialog = false
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportCsvDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Delete Local Data Dialog
    if (showDeleteLocalDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteLocalDataDialog = false },
            title = { Text("Delete Local Workout Data?") },
            text = {
                Text(
                    text = "This will permanently erase all your historical workout sessions, custom routines, body weights, and tape measurements from this device. Profile preferences and settings will be preserved. This action is irreversible and cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteLocalWorkoutData {
                            Toast.makeText(context, "All local workout data erased successfully.", Toast.LENGTH_LONG).show()
                        }
                        showDeleteLocalDataDialog = false
                    }
                ) {
                    Text("Permanently Erase")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLocalDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Play Billing Coming Soon Dialog
    if (showPlayBillingComingSoonDialog) {
        AlertDialog(
            onDismissRequest = { showPlayBillingComingSoonDialog = false },
            title = { Text("Subscription Management") },
            text = {
                Text(
                    text = "Google Play Billing integration is coming soon in the next update. You will be able to manage your Human Annual Membership subscriptions directly through Google Play.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPlayBillingComingSoonDialog = false }
                ) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
