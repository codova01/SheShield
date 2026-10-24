package com.example.sheshield.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class PermStep(val permission: String?, val title: String, val why: String)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val steps = remember {
        buildList {
            add(PermStep(Manifest.permission.ACCESS_FINE_LOCATION, "Location",
                "So your trusted contacts get a live map link when you send an SOS."))
            add(PermStep(Manifest.permission.SEND_SMS, "SMS",
                "So alerts go out as normal text messages, even with no internet."))
            add(PermStep(Manifest.permission.RECORD_AUDIO, "Microphone",
                "So SOS can record audio as evidence. It is encrypted and stays on your phone."))
            if (Build.VERSION.SDK_INT >= 33)
                add(PermStep(Manifest.permission.POST_NOTIFICATIONS, "Notifications",
                    "So SOS can keep running in the background with a discreet notification."))
        }
    }
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]

    fun next() { if (index < steps.lastIndex) index++ else onFinished() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> next() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Step ${index + 1} of ${steps.size}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(12.dp))
        Text(step.title, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(step.why, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { step.permission?.let { launcher.launch(it) } ?: next() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Allow ${step.title.lowercase()}") }
        TextButton(onClick = { next() }, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
    }
}