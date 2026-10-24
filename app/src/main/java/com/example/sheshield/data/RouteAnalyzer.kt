package com.example.sheshield.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

object RouteAnalyzer {

    private class Road(val type: String, val lit: String?, val pts: List<GeoPoint>)

    private val MAIN = setOf(
        "motorway", "trunk", "primary", "secondary", "tertiary",
        "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link"
    )
    private val LANE = setOf("residential", "living_street", "service", "unclassified")
    private const val SHOP_AMENITIES = "restaurant|cafe|pharmacy|bank|atm|fuel|fast_food|marketplace"

    // Samples are 50 m apart, so 40 samples is about 2 km of route per request.
    private const val CHUNK_SAMPLES = 40

    suspend fun analyze(context: Context, route: RouteOption): RouteContext? {
        val samples = resample(route.points, 50.0)
        if (samples.size < 2) return null

        // Ask Overpass about small pieces of the route. Each piece is cached on its own,
        // so if one fails, tapping "Find safe routes" again only retries the missing pieces.
        val elements = mutableListOf<JSONObject>()
        val seen = HashSet<String>()
        var start = 0
        while (start < samples.size - 1) {
            val end = minOf(start + CHUNK_SAMPLES, samples.size - 1)
            val chunk = samples.subList(start, end + 1)
            val json = OverpassClient.query(context, buildQuery(chunk))
            if (json == null) {
                Log.w("RouteAnalyzer", "Chunk $start-$end failed, giving up on this route")
                return null
            }
            val arr = json.optJSONArray("elements")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    val key = e.optString("type") + ":" + e.optLong("id")
                    if (seen.add(key)) elements.add(e)
                }
            }
            start = end
        }

        val roads = mutableListOf<Road>()
        val shops = mutableListOf<GeoPoint>()
        val hospitals = mutableListOf<GeoPoint>()
        val police = mutableListOf<GeoPoint>()
        val metro = mutableListOf<GeoPoint>()
        val lamps = mutableListOf<GeoPoint>()

        for (e in elements) {
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val amenity = tags.optString("amenity")

            if (e.has("geometry") && tags.has("highway")) {
                val g = e.getJSONArray("geometry")
                val pts = (0 until g.length()).map {
                    val o = g.getJSONObject(it)
                    GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
                }
                roads.add(Road(tags.getString("highway"), tags.optString("lit").ifEmpty { null }, pts))
                continue
            }

            val point = when {
                e.has("center") -> e.getJSONObject("center").let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) }
                e.has("lat") -> GeoPoint(e.getDouble("lat"), e.getDouble("lon"))
                else -> continue
            }
            when {
                tags.optString("highway") == "street_lamp" -> lamps.add(point)
                amenity == "hospital" -> hospitals.add(point)
                amenity == "police" -> police.add(point)
                tags.optString("railway") == "station" -> metro.add(point)
                else -> shops.add(point)
            }
        }

        // Walk along the route, sample by sample.
        val segLen = DoubleArray(samples.size) { i ->
            if (i == 0) 0.0 else samples[i - 1].distanceToAsDouble(samples[i])
        }
        val total = segLen.sum()
        if (total <= 0.0) return null

        var main = 0.0
        var lane = 0.0
        var lit = 0.0
        var run = 0.0
        val isolated = mutableListOf<Int>()

        for (i in samples.indices) {
            val p = samples[i]
            val w = segLen[i]

            var best: Road? = null
            var bestD = 30.0
            for (r in roads) {
                for (k in 0 until r.pts.size - 1) {
                    val d = distToSegM(p, r.pts[k], r.pts[k + 1])
                    if (d < bestD) { bestD = d; best = r }
                }
            }
            val road = best
            if (road != null) {
                if (road.type in MAIN) main += w
                else if (road.type in LANE) lane += w
            }

            val lampNear = lamps.any { it.distanceToAsDouble(p) <= 30.0 }
            if (road?.lit == "yes" || lampNear) lit += w

            val active = shops.any { it.distanceToAsDouble(p) <= 100.0 }
            if (active) {
                if (run >= 300.0) isolated.add(run.toInt())
                run = 0.0
            } else {
                run += w
            }
        }
        if (run >= 300.0) isolated.add(run.toInt())

        fun near(list: List<GeoPoint>, maxD: Double) =
            list.count { pt -> samples.any { it.distanceToAsDouble(pt) <= maxD } }

        return RouteContext(
            lengthM = total,
            mainRoadPct = (main / total * 100).roundToInt(),
            lanePct = (lane / total * 100).roundToInt(),
            litPct = (lit / total * 100).roundToInt(),
            shopsNearby = near(shops, 100.0),
            hospitals = near(hospitals, 500.0),
            police = near(police, 500.0),
            metroStations = near(metro, 500.0),
            isolatedStretchesM = isolated
        )
    }

    private fun buildQuery(chunk: List<GeoPoint>): String {
        val poly = chunk
            .filterIndexed { i, _ -> i % 3 == 0 || i == chunk.lastIndex }
            .joinToString(",") { "%.5f,%.5f".format(Locale.US, it.latitude, it.longitude) }
        val dollar = "$"
        return """
[out:json][timeout:25];
way(around:35,$poly)["highway"]->.roads;
(
  node(around:120,$poly)["shop"];
  node(around:120,$poly)["amenity"~"^($SHOP_AMENITIES)$dollar"];
)->.shops;
nwr(around:500,$poly)["amenity"~"^(hospital|police)$dollar"]->.help;
nwr(around:500,$poly)["railway"="station"]["station"="subway"]->.metro;
node(around:30,$poly)["highway"="street_lamp"]->.lamps;
.roads out geom;
.shops out;
(.help;.metro;) out center;
.lamps out;
""".trimIndent()
    }

    /** One point roughly every stepM metres along the route. */
    private fun resample(points: List<GeoPoint>, stepM: Double): List<GeoPoint> {
        if (points.size < 2) return points
        val out = mutableListOf(points.first())
        var acc = 0.0
        for (i in 1 until points.size) {
            acc += points[i - 1].distanceToAsDouble(points[i])
            if (acc >= stepM) { out.add(points[i]); acc = 0.0 }
        }
        if (out.last() != points.last()) out.add(points.last())
        return out
    }

    /** Distance in metres from point p to the segment a-b (flat-earth maths, fine at city scale). */
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
}