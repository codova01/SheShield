package com.example.sheshield

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.sheshield.data.ContactStore
import com.example.sheshield.data.SosPhase
import com.example.sheshield.data.SosPrefs
import com.example.sheshield.data.SosState
import com.example.sheshield.service.SosService
import com.example.sheshield.ui.ContactsScreen
import com.example.sheshield.ui.HelplinesScreen
import com.example.sheshield.ui.OnboardingScreen
import com.example.sheshield.ui.PlanTripScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CONTACTS = "contacts"
    const val VAULT = "vault"
    const val HELPLINES = "helplines"
    const val ROUTES = "routes"
}

private val REQUIRED_PERMS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.SEND_SMS
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val store = remember { ContactStore(context) }
    val start = if (store.onboardingDone) Routes.HOME else Routes.ONBOARDING

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                store.onboardingDone = true
                nav.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.CONTACTS) { ContactsScreen() }
        composable(Routes.VAULT) { Placeholder("Evidence vault") }
        composable(Routes.HELPLINES) { HelplinesScreen() }
        composable(Routes.ROUTES) { PlanTripScreen() }
    }
}

@Composable
fun HomeScreen(nav: NavController) {
    val context = LocalContext.current
    val phase by SosState.phase.collectAsState()
    val seconds by SosState.secondsLeft.collectAsState()
    val prefs = remember { SosPrefs(context) }
    var sensitivity by remember { mutableFloatStateOf(prefs.sensitivity) }
    var message by remember { mutableStateOf<String?>(null) }

    fun hasPerms() = REQUIRED_PERMS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            SosService.start(context, SosService.ACTION_MONITOR)
            message = "Permissions granted. Press SOS again."
        } else {
            message = "Location and SMS permission are needed for SOS."
        }
    }

    LaunchedEffect(Unit) {
        if (hasPerms()) SosService.start(context, SosService.ACTION_MONITOR)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text("SheShield", style = MaterialTheme.typography.headlineLarge)

        when (phase) {
            SosPhase.IDLE -> Button(
                onClick = {
                    if (ContactStore(context).load().isEmpty()) {
                        message = "Add at least one trusted contact first."
                    } else if (!hasPerms()) {
                        permLauncher.launch(REQUIRED_PERMS)
                    } else {
                        message = null
                        SosService.start(context, SosService.ACTION_TRIGGER)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            ) { Text("SOS") }

            SosPhase.COUNTDOWN -> {
                Text("Sending SOS in $seconds...", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = { SosService.start(context, SosService.ACTION_CANCEL) },
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                ) { Text("Cancel") }
            }

            SosPhase.ACTIVE -> {
                Text(
                    "SOS is active. Your contacts get your location every 60 seconds.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = { SosService.start(context, SosService.ACTION_SAFE) },
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                ) { Text("I'm safe") }
            }
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        OutlinedButton(onClick = { nav.navigate(Routes.ROUTES) }, modifier = Modifier.fillMaxWidth()) { Text("Plan a safe route") }
        OutlinedButton(onClick = { nav.navigate(Routes.CONTACTS) }, modifier = Modifier.fillMaxWidth()) { Text("Trusted contacts") }
        OutlinedButton(onClick = { nav.navigate(Routes.VAULT) }, modifier = Modifier.fillMaxWidth()) { Text("Evidence vault") }
        OutlinedButton(onClick = { nav.navigate(Routes.HELPLINES) }, modifier = Modifier.fillMaxWidth()) { Text("Helplines") }

        Spacer(Modifier.height(8.dp))
        Text("Shake sensitivity (left: low, right: high)", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = sensitivity,
            onValueChange = { sensitivity = it },
            onValueChangeFinished = { prefs.sensitivity = sensitivity }
        )
    }
}

@Composable
fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}