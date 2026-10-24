package com.example.sheshield.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sheshield.data.ReportCategory
import com.example.sheshield.data.ReportSummary
import com.example.sheshield.data.bucketLabel

@Composable
fun CommunityWarningCard(title: String, summary: ReportSummary?) {
    if (summary == null) return
    val warning = summary.warningText()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning != null) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                warning ?: "Not enough community reports here around ${bucketLabel(summary.bucket)} yet to show a warning.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Includes seeded demo reports and anonymous feedback from this app.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun CabTipsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cab and auto tips", style = MaterialTheme.typography.titleSmall)
            Text("• Check that the number plate and driver photo match the app before you get in.", style = MaterialTheme.typography.bodySmall)
            Text("• Share the trip with a trusted contact before you start (button below).", style = MaterialTheme.typography.bodySmall)
            Text("• Pick up and drop off at a lit, busy spot such as a shop front, petrol pump or metro gate, not inside a quiet lane.", style = MaterialTheme.typography.bodySmall)
            Text("• At night, ask to be dropped on the main road nearest your destination and walk the last stretch only if it is lit and busy.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Tap 1: felt safe or uncomfortable. Tap 2: what happened. Tap 3: submit. */
@Composable
fun FeedbackDialog(
    destName: String,
    onDismiss: () -> Unit,
    onSubmit: (ReportCategory) -> Unit
) {
    var uncomfortable by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<ReportCategory?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How was $destName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!uncomfortable) {
                    Text("Were you uncomfortable at any point?")
                    Button(
                        onClick = { onSubmit(ReportCategory.FELT_SAFE) },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("No, I felt safe") }
                    OutlinedButton(
                        onClick = { uncomfortable = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Yes, I was uncomfortable") }
                } else {
                    Text("What happened?")
                    ReportCategory.entries.filter { it.negative }.forEach { cat ->
                        if (picked == cat) {
                            Button(
                                onClick = { picked = cat },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(cat.label) }
                        } else {
                            OutlinedButton(
                                onClick = { picked = cat },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(cat.label) }
                        }
                    }
                }
                Text(
                    "Anonymous: only the category, a ~100 m area and a 2-hour time window are saved. No name, no phone number.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            if (uncomfortable) {
                TextButton(
                    enabled = picked != null,
                    onClick = { picked?.let(onSubmit) }
                ) { Text("Submit") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

