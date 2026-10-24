package com.example.sheshield.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

object RoutingClient {

    private fun baseUrl(walking: Boolean) =
        if (walking) "https://routing.openstreetmap.de/routed-foot/route/v1/foot"
        else "https://router.project-osrm.org/route/v1/driving"

    suspend fun fetchRoutes(
        from: GeoPoint,
        to: GeoPoint,
        walking: Boolean
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val coords = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
        val url = URL("${baseUrl(walking)}/$coords?alternatives=3&overview=full&geometries=geojson")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "SheShield-Hackathon/1.0 (github.com/codova01/SheShield)")
        }
        try {
            if (conn.responseCode != 200) return@withContext emptyList()
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val routes = json.optJSONArray("routes") ?: return@withContext emptyList()
            (0 until minOf(routes.length(), 3)).map { i ->
                val r = routes.getJSONObject(i)
                val arr = r.getJSONObject("geometry").getJSONArray("coordinates")
                val pts = (0 until arr.length()).map { j ->
                    val c = arr.getJSONArray(j)
                    GeoPoint(c.getDouble(1), c.getDouble(0)) // GeoJSON order is [lon, lat]
                }
                RouteOption(i, pts, r.getDouble("distance"), r.getDouble("duration"))
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }
}