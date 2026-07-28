package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billing.AppAccessState
import com.example.billing.CommercialConfig
import com.example.data.AuthState
import com.example.ui.viewmodel.StrengthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionAccessScreen(
    viewModel: StrengthViewModel,
    onSignOutComplete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val appAccessState by viewModel.appAccessState.collectAsState()
    val productInfo by viewModel.productInfo.collectAsState()
    val authState by viewModel.authState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "HUMAN STRENGTH",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Access Required",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            val titleText = if (appAccessState is com.example.billing.AppAccessState.Expired) {
                "YOUR TRIAL HAS ENDED"
            } else {
                "UNLOCK HUMAN STRENGTH"
            }

            val bodyText = if (appAccessState is com.example.billing.AppAccessState.Expired) {
                "Your training history, logged workouts, and custom routines are safe. Continue training with Human Strength for £24/year."
            } else {
                "Access all training modules, preserve local & cloud sync data, and build your custom routines with Human Strength."
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardMembership,
                            contentDescription = "Membership",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = productInfo?.title ?: "Human Strength Annual Membership",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            val priceCopy = when {
                                productInfo != null -> {
                                    if (productInfo?.hasFreeTrial == true) {
                                        "1 month free, then ${productInfo?.formattedPrice}/year"
                                    } else {
                                        "${productInfo?.formattedPrice}/year"
                                    }
                                }
                                else -> CommercialConfig.PLANNED_UK_PRICE
                            }
                            Text(
                                text = priceCopy,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "• Full application access across all training modules\n" +
                                "• Local & cloud sync data preservation guaranteed\n" +
                                "• Single annual membership with no tier restrictions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (appAccessState is AppAccessState.PaymentPending) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Payment Processing: Google Play is confirming your transaction. Application access will unlock automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = {
                    val activity = context as? Activity
                    if (activity != null) {
                        val launched = viewModel.launchPurchaseFlow(activity)
                        if (!launched) {
                            Toast.makeText(context, "Google Play Billing is unavailable", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Cannot start billing flow", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("expired_subscribe_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Subscribe via Play",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            OutlinedButton(
                onClick = {
                    Toast.makeText(context, "Restoring purchases…", Toast.LENGTH_SHORT).show()
                    viewModel.restorePurchases()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("expired_restore_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Restore Purchases",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cloud identity status & recovery
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Cloud Identity",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Cloud Account Status",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val authStatusText = when (val auth = authState) {
                        is AuthState.Authenticated -> {
                            val display = auth.profile.email?.ifBlank { null }
                                ?: auth.profile.displayName
                                ?: auth.profile.id
                            "Signed in as $display"
                        }
                        is AuthState.Offline -> "Guest / Offline mode (Local data preserved)"
                        else -> "Not signed in"
                    }

                    Text(
                        text = authStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = "Subscriptions are managed by Google Play. Signing out or deleting local app data does not cancel your Play subscription.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}
