package com.example.sheshield.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sheshield.data.IncidentStore
import com.example.sheshield.data.StoredIncident
import com.example.sheshield.data.VaultLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm:ss a", Locale.getDefault()).format(Date(ms))

private fun fmtTime(ms: Long): String =
    SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(ms))

/** Plays one decrypted clip at a time and deletes the temporary file afterwards. */
private class ClipPlayer {
    private var player: MediaPlayer? = null
    private var tempFile: File? = null

    fun play(file: File, onDone: () -> Unit) {
        stopPlayback()
        tempFile = file
        try {
            val p = MediaPlayer()
            player = p
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener {
                stopPlayback()
                onDone()
            }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            stopPlayback()
            onDone()
        }
    }

    fun stopPlayback() {
        try { player?.stop() } catch (e: Exception) { }
        try { player?.release() } catch (e: Exception) { }
        player = null
        tempFile?.delete()
        tempFile = null
    }
}

@Composable
fun VaultScreen() {
    val context = LocalContext.current
    val lock = remember { VaultLock(context) }
    var pinIsSet by remember { mutableStateOf(lock.isSet()) }
    var unlocked by remember { mutableStateOf(false) }

    when {
        !pinIsSet -> PinSetup { pin ->
            lock.setPin(pin)
            pinIsSet = true
            unlocked = true
        }
        !unlocked -> PinEntry(
            verify = { lock.verify(it) },
            onSuccess = { unlocked = true }
        )
        else -> VaultContent()
    }
}

// ---------- PIN screens ----------

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            if (text.length <= 4 && text.all { ch -> ch.isDigit() }) onChange(text)
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PinSetup(onDone: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text("Create a vault PIN", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This 4-digit PIN locks your evidence on this phone. If you forget it, the only way to reset it is to clear the app's data, which also deletes the evidence.",
            style = MaterialTheme.typography.bodyMedium
        )
        PinField("PIN", pin) { pin = it }
        PinField("Confirm PIN", confirm) { confirm = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (pin.length != 4) error = "Use exactly 4 digits."
                else if (pin != confirm) error = "The two PINs do not match."
                else onDone(pin)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Save PIN") }
    }
}

@Composable
private fun PinEntry(verify: (String) -> Boolean, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text("Evidence vault", style = MaterialTheme.typography.headlineSmall)
        Text("Enter your 4-digit PIN to open it.", style = MaterialTheme.typography.bodyMedium)
        PinField("PIN", pin) { pin = it }
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

// ---------- Unlocked content ----------

@Composable
private fun VaultContent() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedId != null) {
        selectedId = null
        refresh++
    }

    val current = selectedId
    if (current == null) {
        val items = remember(refresh) { IncidentStore.list(context) }
        IncidentList(items) { selectedId = it }
    } else {
        IncidentDetail(current) {
            selectedId = null
            refresh++
        }
    }
}

@Composable
private fun IncidentList(items: List<StoredIncident>, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Evidence vault", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Everything here is encrypted on this phone. Each record and each audio clip has a SHA-256 fingerprint and a timestamp.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (items.isEmpty()) {
            Text(
                "No incidents saved yet. When you trigger SOS, it is saved here automatically.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        items.forEach { s ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(s.incident.id) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SOS alert", style = MaterialTheme.typography.titleMedium)
                    Text(fmt(s.incident.startedAt), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${s.incident.points.size} location points, " +
                                "${s.incident.attachments.size} audio clips",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Fingerprint: ${s.sha256.take(12)}...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun IncidentDetail(id: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var stored by remember { mutableStateOf(IncidentStore.get(context, id)) }
    val s = stored

    if (s == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This record could not be opened.", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onClose) { Text("Back") }
        }
    } else {
        DetailBody(
            s = s,
            onSaveNotes = { n ->
                IncidentStore.setNotes(context, id, n)
                stored = IncidentStore.get(context, id)
            },
            onRefresh = { stored = IncidentStore.get(context, id) },
            onDelete = {
                IncidentStore.delete(context, id)
                onClose()
            },
            onBack = onClose
        )
    }
}

@Composable
private fun DetailBody(
    s: StoredIncident,
    onSaveNotes: (String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val inc = s.incident
    var notes by remember { mutableStateOf(inc.notes) }
    var saved by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var playingFile by remember { mutableStateOf<String?>(null) }
    val player = remember { ClipPlayer() }

    DisposableEffect(Unit) {
        onDispose { player.stopPlayback() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back to vault") }
        Text("SOS alert", style = MaterialTheme.typography.headlineMedium)

        Text("Started: ${fmt(inc.startedAt)}")
        Text("Ended: " + (inc.endedAt?.let { fmt(it) } ?: "not marked safe"))
        Text(
            "Contacts alerted: " +
                    (if (inc.alerted.isEmpty()) "none recorded" else inc.alerted.joinToString())
        )

        Text("Location points (${inc.points.size})", style = MaterialTheme.typography.titleMedium)
        if (inc.points.isEmpty()) Text("No location points were recorded.")
        inc.points.forEach { p ->
            if (p.lat != null && p.lng != null) {
                Text(
                    "${fmtTime(p.time)}   ${"%.5f".format(p.lat)}, ${"%.5f".format(p.lng)}   (tap to open map)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://maps.google.com/?q=${p.lat},${p.lng}")
                                    )
                                )
                            } catch (e: Exception) { /* no app to open the link */ }
                        }
                        .padding(vertical = 6.dp),
                    fontSize = 14.sp
                )
            } else {
                Text("${fmtTime(p.time)}   location unavailable", fontSize = 14.sp)
            }
        }

        val clips = inc.attachments.filter { it.kind == "audio" }.sortedBy { it.savedAt }
        Text("Audio evidence (${clips.size})", style = MaterialTheme.typography.titleMedium)
        if (clips.isEmpty()) {
            Text(
                "No audio was recorded. This can happen if the microphone permission was not allowed.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        clips.forEach { clip ->
            val clipVerified = remember(clip.fileName) {
                IncidentStore.verifyAttachment(context, clip)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(clip.label, style = MaterialTheme.typography.titleSmall)
                    Text("Saved: ${fmt(clip.savedAt)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "SHA-256: ${clip.sha256.take(16)}...  " +
                                (if (clipVerified) "(verified)" else "(DOES NOT MATCH)"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    val isPlaying = playingFile == clip.fileName
                    OutlinedButton(
                        onClick = {
                            if (isPlaying) {
                                player.stopPlayback()
                                playingFile = null
                            } else {
                                val file = IncidentStore.decryptToCache(context, clip)
                                if (file != null) {
                                    playingFile = clip.fileName
                                    player.play(file) { playingFile = null }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isPlaying) "Stop" else "Play") }
                }
            }
        }
        TextButton(onClick = onRefresh) { Text("Refresh") }

        Text("Integrity", style = MaterialTheme.typography.titleMedium)
        Text(
            if (s.verified) "Verified: this record matches its fingerprint."
            else "Warning: this record does not match its fingerprint.",
            color = if (s.verified) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.error
        )
        Text("Last saved: ${fmt(s.savedAt)}")
        Text("SHA-256:", style = MaterialTheme.typography.labelLarge)
        Text(s.sha256, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text(
            "The fingerprint changes if the record changes. It helps show the record was not altered after it was saved. It is not a legal guarantee.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Your notes", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it; saved = false },
            label = { Text("What happened, who was involved, anything you remember") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
        )
        Button(
            onClick = { onSaveNotes(notes); saved = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Save notes") }
        if (saved) Text("Notes saved and re-encrypted.", style = MaterialTheme.typography.bodySmall)

        OutlinedButton(
            onClick = { showDelete = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Delete this record", color = MaterialTheme.colorScheme.error) }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete this record?") },
            text = { Text("This permanently deletes the incident and its audio from your phone. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }
}