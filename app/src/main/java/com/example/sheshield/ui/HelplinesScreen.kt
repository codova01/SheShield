package com.example.sheshield.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class Helpline(val number: String, val title: String, val note: String)

private val HELPLINES = listOf(
    Helpline("112", "Emergency (police, ambulance, fire)",
        "Use this first if you are in immediate danger."),
    Helpline("1091", "Women helpline (police)",
        "For harassment, stalking, or when you feel unsafe."),
    Helpline("181", "Women helpline",
        "Support and referral for women in distress."),
    Helpline("1930", "National cyber crime helpline",
        "For online fraud and cyber crime. For online harassment, also use the portal below."),
    Helpline("7827170170", "National Commission for Women",
        "Helpline for harassment and violence against women.")
)

@Composable
fun HelplinesScreen() {
    val context = LocalContext.current

    fun dial(number: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        } catch (e: Exception) { /* no dialer available */ }
    }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) { /* no browser available */ }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Helplines", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tap a button to open the dialer with the number filled in. You press call yourself.",
            style = MaterialTheme.typography.bodyMedium
        )

        HELPLINES.forEach { h ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(h.title, style = MaterialTheme.typography.titleMedium)
                    Text(h.note, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { dial(h.number) },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Call ${h.number}") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Where to file a complaint", style = MaterialTheme.typography.titleLarge)

        GuideCard(
            "In immediate danger",
            "Call 112, or go to the nearest police station or a safe, busy place first."
        )
        GuideCard(
            "Stalking or harassment in person",
            "Call 1091, or visit the nearest police station and ask to register a complaint. Ask for a copy or receipt of what you file."
        )
        GuideCard(
            "Online harassment or blackmail",
            "Before blocking, take screenshots that show the username, the profile link and the time. Do not delete the messages. Report the account on the platform, then file a complaint on the national cyber crime portal, or call 1930."
        )
        Button(
            onClick = { openUrl("https://cybercrime.gov.in") },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Open cybercrime.gov.in") }

        Text(
            "This is general guidance, not legal advice.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GuideCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

