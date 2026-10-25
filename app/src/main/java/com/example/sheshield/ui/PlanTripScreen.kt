package com.example.sheshield.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.sheshield.data.ContactStore
import com.example.sheshield.data.DemoPlace
import com.example.sheshield.data.DemoPlaces
import com.example.sheshield.data.ReportStore
import com.example.sheshield.data.RouteAnalyzer
import com.example.sheshield.data.RouteContext
import com.example.sheshield.data.RouteOption
import com.example.sheshield.data.RoutingClient
import com.example.sheshield.data.SafetyScorer
import com.example.sheshield.data.ScoredRoute
import com.example.sheshield.data.TransportMode
import com.example.sheshield.data.TripRequest
import com.example.sheshield.data.TripState
import com.example.sheshield.service.SmsHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Calendar

// Best route is green, middle is orange, worst is red
private val rankColors = listOf(Color(0xFF2E7D32), Color(0xFFEF6C00), Color(0xFFC62828))
private fun colorForRank(rank: Int) = rankColors[minOf(rank, rankColors.lastIndex)]

// How long we wait for the map-data analysis before showing what we have
private const val ANALYSIS_BUDGET_MS = 45_000L

@SuppressLint("MissingPermission")
private fun fetchLocation(context: Context, onResult: (GeoPoint?) -> Unit) {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) {
        onResult(null)
        return
    }
    LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { loc ->
            onResult(loc?.let { GeoPoint(it.latitude, it.longitude) })
        }
        .addOnFailureListener { onResult(null) }
}

private fun formatTime(hour: Int, minute: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    val ampm = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(h12, minute, ampm)
}

/** Sends the trip details and a Google Maps link to every trusted contact. Returns a message for the screen. */
private fun shareTrip(
    context: Context,
    startLabel: String,
    from: GeoPoint,
    dest: DemoPlace,
    mode: TransportMode,
    best: ScoredRoute,
    hour: Int,
    minute: Int
): String {
    val contacts = ContactStore(context).load()
    if (contacts.isEmpty()) return "Add trusted contacts first, then try again."
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return "SMS permission is off. Allow SMS for SheShield in phone settings to share trips."

    val travelMode = when (mode) {
        TransportMode.CAB, TransportMode.AUTO -> "driving"
        TransportMode.METRO -> "transit"
        TransportMode.WALKING -> "walking"
    }
    val link = "https://www.google.com/maps/dir/?api=1" +
            "&origin=${from.latitude},${from.longitude}" +
            "&destination=${dest.lat},${dest.lng}&travelmode=$travelMode"
    val minutes = (best.route.durationS / 60).toInt()
    val km = "%.1f".format(best.route.distanceM / 1000)
    val scoreText = " Route safety score ${best.score}/100."
    val text = "SheShield trip share: I'm going from $startLabel to ${dest.name} by ${mode.label.lowercase()}. " +
            "ETA about $minutes min ($km km), planned for ${formatTime(hour, minute)}." +
            "$scoreText Follow the route: $link"
    contacts.forEach { SmsHelper.send(context, it.phone, text) }
    return "Trip shared with ${contacts.size} trusted contact(s)."
}

@Composable
fun PlanTripScreen() {
    val context = LocalContext.current
    val now = remember { Calendar.getInstance() }
    val scope = rememberCoroutineScope()

    var userLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DemoPlace?>(null) }
    var hour by remember { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(now.get(Calendar.MINUTE)) }
    var mode by remember { mutableStateOf(TransportMode.CAB) }
    var alone by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }

    // Start point: a demo place, or null meaning "my current location"
    var startPlace by remember {
        mutableStateOf<DemoPlace?>(DemoPlaces.all.first { it.name == "Connaught Place" })
    }
    var startMenuOpen by remember { mutableStateOf(false) }

    var routes by remember { mutableStateOf<List<RouteOption>>(emptyList()) }
    var contexts by remember { mutableStateOf<List<RouteContext?>>(emptyList()) }

    // loading = fetching the routes themselves (quick). analysing = checking map data in the background.
    var loading by remember { mutableStateOf(false) }
    var analysing by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var routeNote by remember { mutableStateOf<String?>(null) }
    // Bumped on every new search or reset, so a slow old search can't overwrite newer results
    var searchId by remember { mutableIntStateOf(0) }

    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }
    var shareMsg by remember { mutableStateOf<String?>(null) }

    // Changes whenever a report is added, so warnings and scores update live
    val reportVersion by ReportStore.version.collectAsState()

    fun clearResults() {
        searchId++
        routes = emptyList()
        contexts = emptyList()
        loading = false
        analysing = false
        routeError = null
        routeNote = null
        status = null
    }

    LaunchedEffect(Unit) {
        fetchLocation(context) { userLoc = it }
    }

    val startPoint: GeoPoint? = startPlace?.let { GeoPoint(it.lat, it.lng) } ?: userLoc
    val startLabel = startPlace?.name ?: "My current location"

    val routeReports = remember(routes, hour, reportVersion) {
        routes.map { ReportStore.summarize(it.points, hour, 150.0) }
    }
    val destReports = remember(selected, hour, reportVersion) {
        selected?.let { ReportStore.summarizeAround(GeoPoint(it.lat, it.lng), hour) }
    }
    val scored = remember(routes, contexts, routeReports, hour, mode, alone) {
        SafetyScorer.scoreAll(routes, contexts, routeReports, hour, mode, alone)
    }

    if (showFeedback) {
        val dest = selected
        if (dest != null) {
            FeedbackDialog(
                destName = dest.name,
                onDismiss = { showFeedback = false },
                onSubmit = { category ->
                    ReportStore.add(category, dest.lat, dest.lng, hour)
                    showFeedback = false
                    feedbackMsg = "Thanks. Your anonymous feedback is saved on this phone and the warnings have updated."
                }
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        OsmMap(
            modifier = Modifier.fillMaxWidth().height(240.dp),
            userLocation = startPoint,
            destination = selected?.let { GeoPoint(it.lat, it.lng) },
            routes = scored.map { MapRoute(it.route.points, colorForRank(it.rank).toArgb()) }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Plan a safe route", style = MaterialTheme.typography.headlineSmall)

            // Start from
            Box {
                OutlinedButton(
                    onClick = { startMenuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start from: $startLabel") }
                DropdownMenu(
                    expanded = startMenuOpen,
                    onDismissRequest = { startMenuOpen = false }
                ) {
                    if (userLoc != null) {
                        DropdownMenuItem(
                            text = { Text("My current location") },
                            onClick = {
                                startPlace = null
                                clearResults()
                                startMenuOpen = false
                            }
                        )
                    }
                    DemoPlaces.all.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                startPlace = p
                                clearResults()
                                startMenuOpen = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    selected = null
                    clearResults()
                    feedbackMsg = null
                    shareMsg = null
                },
                label = { Text("Where are you going?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (selected == null) {
                Text("Demo places in Delhi:", style = MaterialTheme.typography.labelMedium)
                DemoPlaces.search(query).forEach { p ->
                    Text(
                        "${p.name}, ${p.area}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = p; query = p.name }
                            .padding(vertical = 10.dp)
                    )
                }
            }

            // Community warning for the destination (step 34)
            selected?.let { dest ->
                CommunityWarningCard("Community reports near ${dest.name}", destReports)
            }

            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m -> hour = h; minute = m },
                        hour, minute, false
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Visit time: ${formatTime(hour, minute)}") }

            Text("How are you travelling?", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransportMode.entries.forEach { m ->
                    val mod = Modifier.weight(1f)
                    if (m == mode) {
                        Button(
                            onClick = { mode = m },
                            modifier = mod,
                            contentPadding = PaddingValues(0.dp)
                        ) { Text(m.label, maxLines = 1) }
                    } else {
                        OutlinedButton(
                            onClick = { mode = m },
                            modifier = mod,
                            contentPadding = PaddingValues(0.dp)
                        ) { Text(m.label, maxLines = 1) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Travelling alone", modifier = Modifier.weight(1f))
                Switch(checked = alone, onCheckedChange = { alone = it })
            }

            Button(
                enabled = selected != null && startPoint != null && !loading,
                onClick = {
                    val dest = selected ?: return@Button
                    val from = startPoint ?: return@Button
                    TripState.set(TripRequest(dest, hour, minute, mode, alone))

                    clearResults()                       // also bumps searchId
                    val myId = searchId
                    shareMsg = null
                    loading = true
                    val appCtx = context.applicationContext
                    val label = startLabel
                    val walking = mode == TransportMode.WALKING

                    scope.launch {
                        // Step 1: get the routes. This is quick, and the spinner stops right after it.
                        val result = RoutingClient.fetchRoutes(
                            from = from,
                            to = GeoPoint(dest.lat, dest.lng),
                            walking = walking
                        )
                        if (myId != searchId) return@launch   // a newer search replaced this one

                        routes = result
                        loading = false

                        if (result.isEmpty()) {
                            routeError = "Couldn't get routes. Check your internet connection and try again."
                            return@launch
                        }
                        status = "${result.size} route(s) from $label to ${dest.name} for ${formatTime(hour, minute)}."
                        if (result.size == 1) {
                            routeNote = "Only one route is available for this trip."
                        }


                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(if (loading) "Finding routes..." else "Find safe routes") }

            routeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            routeNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            if (analysing) {
                Text(
                    "Checking lighting, shops and transport along the routes. This can take up to 30 seconds.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            scored.sortedBy { it.rank }.forEach { s ->
                val color = if (analysing) MaterialTheme.colorScheme.primary else colorForRank(s.rank)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val title = "Route ${s.index + 1} · Safety ${s.score}/100" +
                                if (s.rank == 0) " · Recommended" else ""
                        Text(title, color = color, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "%.1f km · %d min".format(s.route.distanceM / 1000, (s.route.durationS / 60).toInt()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Community warning on the route (step 34)
                        s.reports?.warningText()?.let { w ->
                            Text(w, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!analysing) {
                            s.reasons.forEach { reason ->
                                Text("• $reason", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Cab tips and share trip (step 31)
            if (scored.isNotEmpty()) {
                if (mode == TransportMode.CAB || mode == TransportMode.AUTO) {
                    CabTipsCard()
                }
                Button(
                    onClick = {
                        val dest = selected
                        val from = startPoint
                        val best = scored.minByOrNull { it.rank }
                        if (dest != null && from != null && best != null) {
                            shareMsg = shareTrip(context, startLabel, from, dest, mode, best, hour, minute)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Share this trip with trusted contacts") }
                shareMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            // Post-visit feedback (step 35)
            if (selected != null) {
                OutlinedButton(
                    onClick = { showFeedback = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("After your visit: how was it?") }
                feedbackMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = { ReportStore.resetMine(); feedbackMsg = "Demo feedback cleared." }) {
                    Text("Reset demo feedback")
                }
            }
        }
    }
}