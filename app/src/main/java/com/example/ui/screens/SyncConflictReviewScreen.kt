package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.presentation.SyncConflictSummary
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConflictReviewScreen(
    conflicts: List<SyncConflictSummary>,
    onUseOffline: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Items need review") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("conflict_review_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Human V1 online data and this phone contain incompatible edits to the same item. Neither version has been overwritten.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Item-level resolution is not available in this hotfix. You can keep training offline or sign out.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(conflicts, key = { "${it.entityType}:${it.entityReference}" }) { conflict ->
                ConflictSummaryCard(conflict)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onUseOffline, modifier = Modifier.fillMaxWidth().testTag("conflict_use_offline")) {
                        Text("Use offline for now")
                    }
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().testTag("conflict_sign_out")) {
                        Text("Sign out")
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("conflict_return_to_app")) {
                        Text("Return to app")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictSummaryCard(conflict: SyncConflictSummary) {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(conflict.displayLabel, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(conflict.entityType)
            Text("Reference: ${conflict.entityReference}")
            Text("This phone: ${formatter.format(Date(conflict.localModifiedAt))}")
            Text("Online: ${conflict.onlineModifiedAt?.let { formatter.format(Date(it)) } ?: "Time unavailable"}")
            Text("Status: ${conflict.status}")
            Text("Resolution: ${if (conflict.resolutionAvailable) "Available" else "Not available in this version"}")
        }
    }
}
