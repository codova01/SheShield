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
import com.example.sheshield.data.DemoPlace
import com.example.sheshield.data.DemoPlaces
import com.example.sheshield.data.RouteOption
import com.example.sheshield.data.RoutingClient
import com.example.sheshield.data.TransportMode
import com.example.sheshield.data.TripRequest
import com.example.sheshield.data.TripState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Calendar

private val routeColors = listOf(Color(0xFF2E7D32), Color(0xFFEF6C00), Color(0xFF1565C0))

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
    var loading by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        fetchLocation(context) { userLoc = it }
    }

    val startPoint: GeoPoint? = startPlace?.let { GeoPoint(it.lat, it.lng) } ?: userLoc
    val startLabel = startPlace?.name ?: "My current location"

    Column(Modifier.fillMaxSize()) {
        OsmMap(
            modifier = Modifier.fillMaxWidth().height(240.dp),
            userLocation = startPoint,
            destination = selected?.let { GeoPoint(it.lat, it.lng) },
            routes = routes.mapIndexed { i, r ->
                MapRoute(r.points, routeColors[i % routeColors.size].toArgb())
            }
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
                                routes = emptyList()
                                startMenuOpen = false
                            }
                        )
                    }
                    DemoPlaces.all.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                startPlace = p
                                routes = emptyList()
                                startMenuOpen = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it; selected = null; routes = emptyList() },
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
                TransportMode.values().forEach { m ->
                    val mod = Modifier.weight(1f)
                    if (m == mode) {
                        Button(
                            onClick = { mode = m },
                            modifier = mod,
                            contentPadding = PaddingValues(0.dp)
                        ) { Text(m.label, maxLines = 1) }
                    } else {
                        OutlinedButton(
                            onClick = { mode = m; routes = emptyList() },
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
                    status = null
                    routeError = null
                    loading = true
                    scope.launch {
                        val result = RoutingClient.fetchRoutes(
                            from = from,
                            to = GeoPoint(dest.lat, dest.lng),
                            walking = mode == TransportMode.WALKING
                        )
                        routes = result
                        if (result.isEmpty()) {
                            routeError = "Couldn't get routes. Check your internet connection and try again."
                        } else {
                            status = "${result.size} route(s) from $startLabel to ${dest.name} at ${formatTime(hour, minute)}. Safety scores come next."
                        }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(if (loading) "Finding routes..." else "Find safe routes") }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            routeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            routes.forEachIndexed { i, r ->
                Text(
                    "Route ${i + 1}: %.1f km, %d min".format(r.distanceM / 1000, (r.durationS / 60).toInt()),
                    color = routeColors[i % routeColors.size],
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}