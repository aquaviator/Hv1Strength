package com.example.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.security.MessageDigest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
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
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.ui.viewmodel.StrengthViewModel
import com.example.data.AuthState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    viewModel: StrengthViewModel,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authState by viewModel.authState.collectAsState()
    
    val sharedPrefs = remember { context.getSharedPreferences("strength_settings", Context.MODE_PRIVATE) }
    val defaultClientId = remember(context) {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) context.getString(resId) else "596361666131-4bdc26e3rrupaag2cn3tqcmlqdcdjqs8.apps.googleusercontent.com"
    }
    var webClientId by remember { mutableStateOf(sharedPrefs.getString("google_web_client_id", defaultClientId) ?: defaultClientId) }

    var showSimulationDialog by remember { mutableStateOf(false) }
    var simEmail by remember { mutableStateOf("athlete.active@gmail.com") }
    var simName by remember { mutableStateOf("Alex Mercer") }
    
    var showSignInErrorDialog by remember { mutableStateOf(false) }
    var signInErrorMessage by remember { mutableStateOf("") }
    
    // Check if we are already authenticated or in offline mode, and navigate if so
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated || authState is AuthState.Offline) {
            onNavigateToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // App Brand Header - Hero Element
            Image(
                painter = painterResource(id = com.example.R.drawable.human_banner),
                contentDescription = "Human V1 Strength Banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.5f)
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("welcome_logo_card"),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Information Bullet points
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                InfoBulletRow(
                    icon = Icons.Default.Security,
                    title = "Offline-First Design",
                    description = "Everything is saved locally in a secure SQLite database. Always responsive, always accessible."
                )
                InfoBulletRow(
                    icon = Icons.Default.Lock,
                    title = "Google Sign-In Account",
                    description = "Create a proper account profile to uniquely identify your workouts, and prepare for future secure cloud sync."
                )
            }

            Spacer(modifier = Modifier.height(56.dp))

            if (authState is AuthState.Loading) {
                CircularProgressIndicator(
                    color = Color(0xFF0066FF),
                    modifier = Modifier.testTag("welcome_loading_indicator")
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Authenticating...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF0066FF),
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                // Primary Action Button: Sign In with Google (Premium Pure White with Black Text)
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val credentialManager = CredentialManager.create(context)
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(webClientId)
                                    .setAutoSelectEnabled(true)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val response = credentialManager.getCredential(context, request)
                                handleCredentialResponse(response, viewModel, coroutineScope)
                            } catch (e: Exception) {
                                val errorMsg = e.localizedMessage ?: e.message ?: "Unknown error"
                                Log.e("WelcomeScreen", "Google Sign-In failed", e)
                                if (e.javaClass.simpleName.contains("Cancel") || errorMsg.contains("cancel", ignoreCase = true)) {
                                    Toast.makeText(context, "Sign-In Cancelled", Toast.LENGTH_SHORT).show()
                                } else {
                                    signInErrorMessage = errorMsg
                                    showSignInErrorDialog = true
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_signin_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sign In with Google",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Secondary Action Button: Continue Offline (Sleek Outline with White Text)
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.authRepository.signInAnonymously()
                            Toast.makeText(context, "Welcome! Mode: Offline-First Local", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("continue_offline_button"),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Continue Offline",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Google Sign-In Simulation Dialog for development / headless environments
    if (showSimulationDialog) {
        AlertDialog(
            onDismissRequest = { showSimulationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Google Sign-In Simulation")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Google Play Services or valid Client IDs are not present on this device. Would you like to simulate a successful Google verification to test account linkage, profiles, and settings?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    OutlinedTextField(
                        value = simName,
                        onValueChange = { simName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = simEmail,
                        onValueChange = { simEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("confirm_sim_signin"),
                    onClick = {
                        coroutineScope.launch {
                            // Mock Google sign-in using deterministic hashed ID token based on email
                            val mockIdToken = "simulated_token_" + simEmail.hashCode().toString()
                            viewModel.authRepository.signInWithGoogle(
                                idToken = mockIdToken,
                                displayName = simName,
                                email = simEmail,
                                photoUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80"
                            )
                            Toast.makeText(context, "Simulated Google Sign-In Successful!", Toast.LENGTH_SHORT).show()
                            showSimulationDialog = false
                        }
                    }
                ) {
                    Text("Authenticate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimulationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSignInErrorDialog) {
        val isNoCredentials = signInErrorMessage.contains("no credentials", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { showSignInErrorDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (isNoCredentials) Icons.Default.Info else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isNoCredentials) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(if (isNoCredentials) "Google Account Needed" else "Google Sign-In Failed")
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isNoCredentials) {
                        Text(
                            text = "A 'No credentials available' error occurred. This typically happens for one of two reasons:\n\n" +
                                    "1. **Emulator / No Account**: If you are in a Cloud Android Emulator, there is no Google account signed into this device's Google Play Services.\n\n" +
                                    "2. **Google Play App Signing**: If you installed this via Google Play (e.g. Internal Testing), Google re-signs the app. The running SHA-1 fingerprint shown below must be added to your Firebase / Google Cloud Console Project Settings.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "How to test on this Emulator:",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Click 'Use Sign-In Simulation' below. This will simulate a complete Google Sign-In and authenticate successfully with the Firebase SDK, allowing you to fully test the database syncing and live features without needing a physical device!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Google Sign-In could not be initialized or completed. This occurs when the SHA-1 signing fingerprint or package name do not match your Firebase console setup.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Error Details:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = signInErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Firebase Setup Reference:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val packageName = "com.aistudio.humanstrength.kfqjza"
                            val runningSha1 = getAppCertificateSha1(context)
                            
                            // Package Name Row with Copy Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "1. Registered Package Name:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Package Name", packageName)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Package name copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Package Name",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // SHA-1 Row with Copy Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "2. Running SHA-1 Fingerprint:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = runningSha1,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("SHA-1 Fingerprint", runningSha1)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "SHA-1 copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy SHA-1 Fingerprint",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Your app build has these values configured correctly in google-services.json.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    Text(
                        text = "Verify Web Client ID (or in Settings):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = webClientId,
                        onValueChange = { 
                            webClientId = it
                            sharedPrefs.edit().putString("google_web_client_id", it).apply()
                        },
                        label = { Text("Google Web Client ID") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showSignInErrorDialog = false
                            showSimulationDialog = true
                        }
                    ) {
                        Text("Use Sign-In Simulation")
                    }
                    
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showSignInErrorDialog = false
                            coroutineScope.launch {
                                try {
                                    val credentialManager = CredentialManager.create(context)
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(webClientId)
                                        .setAutoSelectEnabled(true)
                                        .build()

                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                    val response = credentialManager.getCredential(context, request)
                                    handleCredentialResponse(response, viewModel, coroutineScope)
                                } catch (e: Exception) {
                                    val errorMsg = e.localizedMessage ?: e.message ?: "Unknown error"
                                    Log.e("WelcomeScreen", "Google Sign-In retry failed", e)
                                    if (e.javaClass.simpleName.contains("Cancel") || errorMsg.contains("cancel", ignoreCase = true)) {
                                        Toast.makeText(context, "Sign-In Cancelled", Toast.LENGTH_SHORT).show()
                                    } else {
                                        signInErrorMessage = errorMsg
                                        showSignInErrorDialog = true
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Retry Google Sign-In")
                    }
                    
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showSignInErrorDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

private fun handleCredentialResponse(
    response: GetCredentialResponse,
    viewModel: StrengthViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val credential = response.credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            coroutineScope.launch {
                viewModel.authRepository.signInWithGoogle(
                    idToken = googleIdTokenCredential.idToken,
                    displayName = googleIdTokenCredential.displayName,
                    email = googleIdTokenCredential.id,
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                )
            }
        } catch (e: Exception) {
            Log.e("WelcomeScreen", "Failed to parse Google ID Token credential", e)
        }
    }
}

@Composable
fun InfoBulletRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getAppCertificateSha1(context: Context): String {
    try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        if (signatures != null && signatures.isNotEmpty()) {
            val md = MessageDigest.getInstance("SHA-1")
            val publicKey = md.digest(signatures[0].toByteArray())
            val hexString = StringBuilder()
            for (i in publicKey.indices) {
                val appendString = Integer.toHexString(0xFF and publicKey[i].toInt())
                if (appendString.length == 1) hexString.append("0")
                hexString.append(appendString)
                if (i < publicKey.size - 1) hexString.append(":")
            }
            return hexString.toString().uppercase()
        }
    } catch (e: Exception) {
        Log.e("WelcomeScreen", "Error getting signature", e)
    }
    return "Error retrieving signature"
}
