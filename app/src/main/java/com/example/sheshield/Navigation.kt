package com.example.sheshield

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.sheshield.ui.theme.*
import com.example.sheshield.ui.VaultScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CONTACTS = "contacts"
    const val VAULT = "vault"
    const val COMPLAINT = "complaint"
    const val HELPLINES = "helplines"
    const val ROUTES = "routes"
    const val PRIVACY = "privacy"
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

    SheShieldTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SheCream
        ) {
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
                composable(Routes.VAULT) { VaultScreen() }         // replaced when the vault is built
                composable(Routes.COMPLAINT) { Placeholder("Complaint generator") } // replaced when the complaint is built
                composable(Routes.HELPLINES) { HelplinesScreen() }
                composable(Routes.ROUTES) { PlanTripScreen() }
                composable(Routes.PRIVACY) { PrivacyScreen() }
            }
        }
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
    var showShake by remember { mutableStateOf(false) }

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
            message = "Location and SMS permission are needed for SOS. You can allow them in phone settings."
        }
    }

    LaunchedEffect(Unit) {
        if (hasPerms()) SosService.start(context, SosService.ACTION_MONITOR)
    }

    fun onSosTapped() {
        if (ContactStore(context).load().isEmpty()) {
            message = "Add at least one trusted contact first."
        } else if (!hasPerms()) {
            permLauncher.launch(REQUIRED_PERMS)
        } else {
            message = null
            SosService.start(context, SosService.ACTION_TRIGGER)
        }
    }

    val listShape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldIcon(SheAccent, Modifier.size(26.dp, 32.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "SheShield",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = SheBrown
            )
        }
        Text(
            "Her freedom should never come with fear",
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            color = SheBrownSoft,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))

        // SOS circle (changes with the alert state)
        when (phase) {
            SosPhase.IDLE -> SosCircle("SOS", SheRed, SheRedHalo, 40.sp) { onSosTapped() }

            SosPhase.COUNTDOWN -> {
                SosCircle("$seconds", SheAmber, SheAmberHalo, 60.sp) {
                    SosService.start(context, SosService.ACTION_CANCEL)
                }
                Text(
                    "Sending SOS... tap the circle to cancel",
                    color = SheBrown,
                    textAlign = TextAlign.Center
                )
            }

            SosPhase.ACTIVE -> {
                SosCircle("I'm safe", SheGreen, SheGreenHalo, 30.sp) {
                    SosService.start(context, SosService.ACTION_SAFE)
                }
                Text(
                    "SOS is active. Your contacts get your location every 60 seconds. Tap the circle when you are safe.",
                    color = SheBrown,
                    textAlign = TextAlign.Center
                )
            }
        }

        message?.let {
            Text(it, color = SheBrownSoft, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(2.dp))

        // Main action
        HeroCard(
            icon = Icons.Outlined.LocationOn,
            title = "Plan a safe route",
            subtitle = "Ranked routes and community warnings"
        ) { nav.navigate(Routes.ROUTES) }

        // Calm stacked list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(listShape)
                .background(Color.White)
                .border(1.dp, SheBorder, listShape)
        ) {
            ListRow(Icons.Outlined.Group, "Trusted contacts", "Up to 5") {
                nav.navigate(Routes.CONTACTS)
            }
            RowDivider()
            ListRow(Icons.Outlined.Vibration, "Shake sensitivity", "How easily a shake starts SOS") {
                showShake = true
            }
            RowDivider()
            ListRow(Icons.Outlined.Lock, "Vault", "Evidence") {
                nav.navigate(Routes.VAULT)
            }
            RowDivider()
            ListRow(Icons.Outlined.Description, "Complaint", "PDF or text") {
                nav.navigate(Routes.COMPLAINT)
            }
            RowDivider()
            ListRow(Icons.Outlined.Call, "Helplines", "112, 1091, 181, 1930") {
                nav.navigate(Routes.HELPLINES)
            }
        }

        // Privacy link: looks tappable, but is not a button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { nav.navigate(Routes.PRIVACY) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.PrivacyTip,
                contentDescription = null,
                tint = SheAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Privacy: what stays on your phone",
                color = SheAccent,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline
            )
            Spacer(Modifier.width(4.dp))
            Text("›", color = SheAccent, fontSize = 18.sp)
        }
    }

    if (showShake) {
        AlertDialog(
            onDismissRequest = { showShake = false },
            title = { Text("Shake sensitivity") },
            text = {
                Column {
                    Text("Shake your phone 3 times to start SOS. Move right to make it easier to trigger.")
                    Slider(
                        value = sensitivity,
                        onValueChange = { sensitivity = it },
                        onValueChangeFinished = { prefs.sensitivity = sensitivity }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Low")
                        Text("High")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShake = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SosCircle(
    label: String,
    color: Color,
    halo: Color,
    fontSize: TextUnit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(190.dp).background(halo, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(154.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ShePeach)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SheBrown, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = SheBrown)
            Text(subtitle, fontSize = 13.sp, color = SheBrownSoft)
        }
        Text("›", fontSize = 24.sp, color = SheBrownSoft)
    }
}

@Composable
private fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SheBrown, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = SheBrown)
            Text(subtitle, fontSize = 13.sp, color = SheBrownSoft)
        }
        Text("›", fontSize = 24.sp, color = SheBrownSoft)
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 58.dp)
            .height(1.dp)
            .background(SheBorder)
    )
}

@Composable
private fun ShieldIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.18f)
            lineTo(w, h * 0.55f)
            quadraticBezierTo(w, h * 0.85f, w * 0.5f, h)
            quadraticBezierTo(0f, h * 0.85f, 0f, h * 0.55f)
            lineTo(0f, h * 0.18f)
            close()
        }
        drawPath(path, color, style = Stroke(width = w * 0.1f, join = StrokeJoin.Round))
    }
}

@Composable
fun PrivacyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Your privacy", style = MaterialTheme.typography.headlineMedium)

        PrivacyCard(
            "Stays on your phone",
            listOf(
                "Your trusted contacts.",
                "SOS settings and shake sensitivity.",
                "Evidence recordings, encrypted on the phone with a key kept in the Android Keystore.",
                "Your feedback about places. There is no account and no login."
            )
        )
        PrivacyCard(
            "Sent only when you choose",
            listOf(
                "An SMS with your live location link to your trusted contacts when SOS is active, or when you press Share trip.",
                "Start and destination points go to a public routing service (OSRM) to draw routes, and the map is loaded from OpenStreetMap."
            )
        )
        PrivacyCard(
            "Community feedback",
            listOf(
                "In this version, feedback stays on this phone and nothing is uploaded.",
                "Some warnings you see are seeded demo reports, clearly labelled as demo data.",
                "A future version would share only a category, a rough 100 m grid area and a 2-hour time slot. No name, no phone number, no user ID."
            )
        )
        PrivacyCard(
            "Permissions we ask for",
            listOf(
                "Location: to send your position in an emergency and plan routes.",
                "SMS: to alert your contacts without needing internet.",
                "Microphone: to record evidence when SOS starts.",
                "Notifications: to keep SOS running discreetly in the background."
            )
        )
    }
}

@Composable
private fun PrivacyCard(title: String, lines: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}