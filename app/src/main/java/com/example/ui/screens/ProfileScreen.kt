package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    
    var showSignOutDialog by remember { mutableStateOf(false) }
    var deleteLocalDataOnSignOut by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    val userProfile by viewModel.activeUserProfile.collectAsState()
    val profile = userProfile

    // Navigation back if session is cleared
    LaunchedEffect(authState) {
        if (authState is AuthState.Initial) {
            onSignOutComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Profile", fontWeight = FontWeight.Black) },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // User Avatar Section
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                var imageLoaded by remember { mutableStateOf(false) }

                if (!profile?.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile?.photoUrl,
                        contentDescription = "Profile Photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        onSuccess = { imageLoaded = true },
                        onError = { imageLoaded = false }
                    )
                }

                if (profile?.photoUrl.isNullOrBlank() || !imageLoaded) {
                    Text(
                        text = profile?.initials ?: "U",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 36.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // User Identity Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = profile?.displayName ?: "Offline User",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("profile_display_name")
                )
                Text(
                    text = profile?.email ?: "Offline-only Mode (No cloud backup)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("profile_email")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Account Metadata Card
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
                    Text(
                        text = "ACCOUNT STATUS",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.VerifiedUser,
                        label = "Provider",
                        value = if (profile?.authProvider == "google") "Google Authenticated" else "Local SQLite"
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Joined At",
                        value = if (profile != null) {
                            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(profile.createdAt))
                        } else "N/A"
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.AccessTime,
                        label = "Last Sync / Session",
                        value = if (profile != null) {
                            val sdf = java.text.SimpleDateFormat("HH:mm, MMM dd", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(profile.lastLoginAt))
                        } else "Continuous Local"
                    )
                }
            }

            // Google Actions Section: Data Linking Card
            if (profile?.authProvider == "google") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.MergeType, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Link Existing Local Data",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = "If you previously logged weight entries or routine templates while offline, you can associate and merge those historical records directly into your active Google Account profile now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = { showLinkDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("link_offline_data_button")
                        ) {
                            Text("Link Data to Profile", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Edit Profile Button (Extra visual richness & custom metrics)
            if (profile != null) {
                OutlinedButton(
                    onClick = { showEditProfileDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_metadata_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Profile Details", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Danger Zone: Sign Out Button
            Button(
                onClick = { showSignOutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("profile_signout_button")
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out")
                Spacer(modifier = Modifier.width(10.dp))
                Text("Sign Out of Account", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Sign Out Options Dialog
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

    // Link Local Offline Data Dialog
    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Merge & Link Offline Records") },
            text = {
                Text(
                    text = "This will find any workout templates, sessions, body weights, and tape measurements labeled as 'offline' or unassigned on this device, and associate them with your active profile (${profile?.displayName}). Are you sure you want to proceed?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("confirm_link_button"),
                    onClick = {
                        coroutineScope.launch {
                            if (profile != null) {
                                viewModel.authRepository.linkOfflineDataToUser(profile.id)
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

    // Edit Profile Metadata Dialog
    if (showEditProfileDialog) {
        if (profile != null) {
            var dob by remember { mutableStateOf(profile.dateOfBirth ?: "") }
            var sex by remember { mutableStateOf(profile.sex ?: "unspecified") }
            var exp by remember { mutableStateOf(profile.trainingExperience ?: "beginner") }

            AlertDialog(
                onDismissRequest = { showEditProfileDialog = false },
                title = { Text("Edit Bio Details") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = dob,
                            onValueChange = { dob = it },
                            label = { Text("Date of Birth (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Assigned Sex:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            listOf("Male", "Female", "Other").forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = sex.lowercase() == item.lowercase(), onClick = { sex = item.lowercase() })
                                    Text(item)
                                }
                            }
                        }

                        Text("Training Experience:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Beginner", "Intermediate", "Advanced").forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = exp.lowercase() == item.lowercase(), onClick = { exp = item.lowercase() })
                                    Text(item, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateUserProfileBio(dob, sex, exp)
                            Toast.makeText(context, "Profile bio updated!", Toast.LENGTH_SHORT).show()
                            showEditProfileDialog = false
                        }
                    ) {
                        Text("Save Details")
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
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
