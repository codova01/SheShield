package com.example.sheshield.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt

object RoutingClient {

    private var appContext: Context? = null

    /** Called once from SheShieldApp. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun baseUrl(walking: Boolean) =
        if (walking) "https://routing.openstreetmap.de/routed-foot/route/v1/foot"
        else "https://router.project-osrm.org/route/v1/driving"

    // "v2_" so the old saved single-route results are ignored
    private fun cacheFile(from: GeoPoint, to: GeoPoint, walking: Boolean): File? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, "routes").apply { mkdirs() }
        val name = "v2_%s_%.5f_%.5f_%.5f_%.5f.json".format(
            Locale.US, if (walking) "foot" else "drive",
            from.latitude, from.longitude, to.latitude, to.longitude
        )
        return File(dir, name)
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "SheShield-Hackathon/1.0 (github.com/codova01/SheShield)")
        }
        return try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parseOsrm(text: String): List<RouteOption> = try {
        val routes = JSONObject(text).optJSONArray("routes")
        if (routes == null) emptyList() else (0 until routes.length()).map { i ->
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
    }

    private fun toJson(list: List<RouteOption>): String {
        val arr = JSONArray()
        list.forEach { r ->
            val pts = JSONArray()
            r.points.forEach { pts.put(JSONArray().put(it.latitude).put(it.longitude)) }
            arr.put(JSONObject().put("d", r.distanceM).put("t", r.durationS).put("p", pts))
        }
        return JSONObject().put("routes", arr).toString()
    }

    private fun fromJson(text: String): List<RouteOption> {
        val arr = JSONObject(text).getJSONArray("routes")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val p = o.getJSONArray("p")
            val pts = (0 until p.length()).map { j ->
                val c = p.getJSONArray(j)
                GeoPoint(c.getDouble(0), c.getDouble(1))
            }
            RouteOption(i, pts, o.getDouble("d"), o.getDouble("t"))
        }
    }

    /** Waypoints on both sides of the straight line between start and destination. */
    private fun viaPoints(from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val midLat = (from.latitude + to.latitude) / 2
        val midLon = (from.longitude + to.longitude) / 2
        val k = cos(Math.toRadians(midLat))
        val vx = (to.longitude - from.longitude) * k
        val vy = to.latitude - from.latitude
        val len = sqrt(vx * vx + vy * vy)
        if (len == 0.0) return emptyList()
        val px = -vy / len
        val py = vx / len
        val out = mutableListOf<GeoPoint>()
        for (f in listOf(0.22, 0.38)) {
            for (side in listOf(1.0, -1.0)) {
                val off = len * f * side
                out.add(GeoPoint(midLat + py * off, midLon + px * off / k))
            }
        }
        return out
    }

    /** True if almost all of route a runs along route b (so it is not a real alternative). */
    private fun similar(a: List<GeoPoint>, b: List<GeoPoint>): Boolean {
        if (a.isEmpty() || b.size < 2) return false
        val step = maxOf(1, a.size / 60)
        var close = 0
        var total = 0
        var i = 0
        while (i < a.size) {
            total++
            if (distToPolylineM(a[i], b) < 100.0) close++
            i += step
        }
        return close.toDouble() / total > 0.9
    }

    private fun distToPolylineM(p: GeoPoint, line: List<GeoPoint>): Double {
        var best = Double.MAX_VALUE
        for (k in 0 until line.size - 1) {
            val d = distToSegM(p, line[k], line[k + 1])
            if (d < best) best = d
        }
        return best
    }

    private fun distToSegM(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val mx = 111_320.0 * cos(Math.toRadians(p.latitude))
        val my = 110_540.0
        val ax = (a.longitude - p.longitude) * mx
        val ay = (a.latitude - p.latitude) * my
        val bx = (b.longitude - p.longitude) * mx
        val by = (b.latitude - p.latitude) * my
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt(cx * cx + cy * cy)
    }

    suspend fun fetchRoutes(
        from: GeoPoint,
        to: GeoPoint,
        walking: Boolean
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val cache = cacheFile(from, to, walking)

        // 1. Saved copy first (works offline)
        if (cache != null && cache.exists()) {
            try {
                val saved = fromJson(cache.readText())
                if (saved.isNotEmpty()) return@withContext saved
            } catch (_: Exception) {
                cache.delete()
            }
        }

        // 2. Ask OSRM for its own alternatives
        val base = baseUrl(walking)
        val coords = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
        val text = httpGet("$base/$coords?alternatives=3&overview=full&geometries=geojson")
            ?: return@withContext emptyList()
        val found = parseOsrm(text).take(3)
        if (found.isEmpty()) return@withContext emptyList()

        // 3. Fewer than 3? Try routes through a waypoint on either side of the straight line
        val result = found.toMutableList()
        var failed = false
        if (result.size < 3) {
            val shortest = result.minOf { it.distanceM }
            for (via in viaPoints(from, to)) {
                if (result.size >= 3) break
                delay(300)
                val viaCoords = "${from.longitude},${from.latitude};" +
                        "${via.longitude},${via.latitude};${to.longitude},${to.latitude}"
                val vText = httpGet("$base/$viaCoords?alternatives=false&overview=full&geometries=geojson")
                if (vText == null) {
                    failed = true
                    continue
                }
                val cand = parseOsrm(vText).firstOrNull() ?: continue
                if (cand.distanceM > shortest * 1.5) continue          // silly detour
                if (result.any { similar(cand.points, it.points) }) continue  // same road again
                result.add(cand)
            }
        }

        val finalList = result.mapIndexed { i, r -> RouteOption(i, r.points, r.distanceM, r.durationS) }
        if (cache != null && (finalList.size >= 2 || !failed)) {
            try {
                cache.writeText(toJson(finalList))
            } catch (_: Exception) {
            }
        }
        finalList
    }
}