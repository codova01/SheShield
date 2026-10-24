package com.example.sheshield.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** A coloured line on the map (used for routes). */
data class MapRoute(val points: List<GeoPoint>, val color: Int)

@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    userLocation: GeoPoint?,
    destination: GeoPoint? = null,
    routes: List<MapRoute> = emptyList()
) {
    val context = LocalContext.current

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = "SheShield-Hackathon/1.0 (github.com/codova01/SheShield)"
            // New folder name, so the old blocked (403) tiles are never read again
            val base = File(context.filesDir, "osm_v2")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(28.6139, 77.2090)) // Delhi, until we know more
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            mapView.controller.setZoom(15.0)
            mapView.controller.animateTo(it)
        }
    }

    LaunchedEffect(destination) {
        destination?.let {
            mapView.controller.setZoom(15.0)
            mapView.controller.animateTo(it)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            routes.forEach { r ->
                val line = Polyline().apply {
                    setPoints(r.points)
                    outlinePaint.color = r.color
                    outlinePaint.strokeWidth = 12f
                }
                map.overlays.add(line)
            }
            userLocation?.let { loc ->
                map.overlays.add(Marker(map).apply {
                    position = loc
                    title = "You"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            destination?.let { dest ->
                map.overlays.add(Marker(map).apply {
                    position = dest
                    title = "Destination"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            map.overlays.add(CopyrightOverlay(map.context)) // required map credit
            map.invalidate()
        }
    )
}