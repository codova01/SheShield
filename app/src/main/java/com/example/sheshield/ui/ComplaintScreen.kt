package com.example.sheshield.ui


import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.sheshield.data.ComplainantInfo
import com.example.sheshield.data.ComplaintBuilder
import com.example.sheshield.data.ComplaintPdf
import com.example.sheshield.data.IncidentStore
import com.example.sheshield.data.StoredIncident
import com.example.sheshield.data.VaultLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun whenText(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(ms))

private fun sharePdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share complaint PDF"))
    } catch (e: Exception) {
        // nothing to open the share sheet with
    }
}

private fun shareText(context: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share complaint text"))
    } catch (e: Exception) {
        // nothing to open the share sheet with
    }
}

@Composable
fun ComplaintScreen(onOpenVault: () -> Unit) {
    val context = LocalContext.current
    val lock = remember { VaultLock(context) }
    var unlocked by remember { mutableStateOf(false) }

    when {
        !lock.isSet() -> NeedPin(onOpenVault)
        !unlocked -> ComplaintPin(
            verify = { lock.verify(it) },
            onSuccess = { unlocked = true }
        )
        else -> ComplaintFlow()
    }
}

@Composable
private fun NeedPin(onOpenVault: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text("Complaint generator", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your complaint is built from your encrypted evidence, so it is protected by the vault PIN. " +
                    "Open the vault once to create your PIN, then come back here.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onOpenVault,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Open vault and set PIN") }
    }
}

@Composable
private fun ComplaintPin(verify: (String) -> Boolean, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text("Complaint generator", style = MaterialTheme.typography.headlineSmall)
        Text("Enter your vault PIN to continue.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = pin,
            onValueChange = { t -> if (t.length <= 4 && t.all { it.isDigit() }) pin = t },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (verify(pin)) onSuccess() else {
                    error = "Wrong PIN."
                    pin = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Unlock") }
    }
}

@Composable
private fun ComplaintFlow() {
    val context = LocalContext.current
    val incidents = remember { IncidentStore.list(context) }
    var chosen by remember { mutableStateOf<StoredIncident?>(null) }

    BackHandler(enabled = chosen != null) { chosen = null }

    val current = chosen
    if (current == null) {
        IncidentPicker(incidents) { chosen = it }
    } else {
        ComplaintForm(current)
    }
}

@Composable
private fun IncidentPicker(items: List<StoredIncident>, onPick: (StoredIncident) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Complaint generator", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose the incident you want a complaint for. The letter is filled in from your saved record: time, location, evidence and fingerprints.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (items.isEmpty()) {
            Text(
                "No incidents saved yet. When you trigger SOS, it is saved in the vault automatically.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        items.forEach { s ->
            Card(Modifier.fillMaxWidth().clickable { onPick(s) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SOS alert", style = MaterialTheme.typography.titleMedium)
                    Text(whenText(s.incident.startedAt), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${s.incident.points.size} location points, ${s.incident.attachments.size} audio clips",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ComplaintForm(stored: StoredIncident) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(stored.incident.notes) }
    var generated by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = generated != null) {
        generated = null
        message = null
    }

    val text = generated
    if (text == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Your details", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Everything here is optional. Anything you leave empty stays in the letter as a [placeholder] you can fill in later. Nothing you type is saved or uploaded.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Your full name") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Your phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = station, onValueChange = { station = it },
                label = { Text("Police station (for example: Hauz Khas)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = details, onValueChange = { details = it },
                label = { Text("What happened, who was involved, anything you remember") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
            )
            Button(
                onClick = {
                    generated = ComplaintBuilder.build(
                        stored,
                        ComplainantInfo(name, phone, station, details)
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Generate complaint") }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Your complaint", style = MaterialTheme.typography.headlineSmall)

            Button(
                onClick = {
                    val file = ComplaintPdf.create(context, text)
                    if (file == null) {
                        message = "Couldn't create the PDF. You can still share it as text."
                    } else {
                        message = null
                        sharePdf(context, file)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Share as PDF") }
            OutlinedButton(
                onClick = { shareText(context, text) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Share as text") }
            OutlinedButton(
                onClick = { generated = null; message = null },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Edit details") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Card(Modifier.fillMaxWidth()) {
                Text(
                    text,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Where to file", style = MaterialTheme.typography.titleSmall)
                    Text("• Emergency: call 112.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "• Any police station will take your complaint. If the incident happened in another area, they can register it as a Zero FIR and transfer it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("• Women's helplines: 1091 or 181.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "• Online harassment or cyber crime: call 1930 or use cybercrime.gov.in.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "This is a draft to help you. Review it before submitting. It is not legal advice.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}