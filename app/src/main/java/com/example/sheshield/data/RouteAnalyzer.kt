package com.example.sheshield.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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

    // Samples are 50 m apart, so 20 samples is about 1 km of route per request.
    private const val CHUNK_SAMPLES = 20

    // Shared by all routes, so at most 2 Overpass requests run at the same time in total.
    private val gate = Semaphore(2)

    /**
     * Analyses one route. Chunks are fetched through a shared gate (2 at a time overall).
     * If some chunks fail, we still use the ones that worked (as long as at least half did)
     * and ignore the uncovered part of the route.
     * Never starts new requests after [deadline] (epoch millis).
     */
    suspend fun analyze(
        context: Context,
        route: RouteOption,
        deadline: Long = System.currentTimeMillis() + 40_000L
    ): RouteContext? {
        val samples = resample(route.points, 50.0)
        if (samples.size < 2) return null

        val ranges = ArrayList<Pair<Int, Int>>()
        var start = 0
        while (start < samples.size - 1) {
            val end = minOf(start + CHUNK_SAMPLES, samples.size - 1)
            ranges.add(start to end)
            start = end
        }

        val results: List<JSONObject?> = coroutineScope {
            ranges.map { (a, b) ->
                async {
                    gate.withPermit {
                        OverpassClient.query(context, buildQuery(samples.subList(a, b + 1)), deadline)
                    }
                }
            }.awaitAll()
        }

        val okCount = results.count { it != null }
        Log.i("RouteAnalyzer", "$okCount of ${results.size} chunks loaded")
        if (okCount == 0 || okCount * 2 < results.size) {
            Log.w("RouteAnalyzer", "Too few chunks loaded, no analysis for this route")
            return null
        }

        val covered = BooleanArray(samples.size)
        val elements = mutableListOf<JSONObject>()
        val seen = HashSet<String>()
        results.forEachIndexed { idx, json ->
            if (json == null) return@forEachIndexed
            val (a, b) = ranges[idx]
            for (i in a..b) covered[i] = true
            val arr = json.optJSONArray("elements") ?: return@forEachIndexed
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val key = e.optString("type") + ":" + e.optLong("id")
                if (seen.add(key)) elements.add(e)
            }
        }

        return withContext(Dispatchers.Default) { measure(samples, covered, elements) }
    }

    private fun measure(
        samples: List<GeoPoint>,
        covered: BooleanArray,
        elements: List<JSONObject>
    ): RouteContext? {
        val roads = mutableListOf<Road>()
        val shops = mutableListOf<GeoPoint>()
        val hospitals = mutableListOf<GeoPoint>()
        val police = mutableListOf<GeoPoint>()
        val metro = mutableListOf<GeoPoint>()
        val buses = mutableListOf<GeoPoint>()
        val autos = mutableListOf<GeoPoint>()
        val lamps = mutableListOf<GeoPoint>()

        for (e in elements) {
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val amenity = tags.optString("amenity")
            val railway = tags.optString("railway")
            val highway = tags.optString("highway")

            if (e.has("geometry") && tags.has("highway")) {
                val g = e.getJSONArray("geometry")
                val pts = (0 until g.length()).map {
                    val o = g.getJSONObject(it)
                    GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
                }
                roads.add(Road(highway, tags.optString("lit").ifEmpty { null }, pts))
                continue
            }

            val point = when {
                e.has("center") -> e.getJSONObject("center").let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) }
                e.has("lat") -> GeoPoint(e.getDouble("lat"), e.getDouble("lon"))
                else -> continue
            }
            when {
                highway == "street_lamp" -> lamps.add(point)
                highway == "bus_stop" -> buses.add(point)
                amenity == "taxi" -> autos.add(point)
                amenity == "hospital" -> hospitals.add(point)
                amenity == "police" -> police.add(point)
                railway == "station" || railway == "subway_entrance" -> metro.add(point)
                else -> shops.add(point)
            }
        }

        // Walk along the route, sample by sample, skipping parts where data didn't load.
        val segLen = DoubleArray(samples.size) { i ->
            if (i == 0) 0.0 else samples[i - 1].distanceToAsDouble(samples[i])
        }
        val fullLen = segLen.sum()
        var total = 0.0
        for (i in samples.indices) if (covered[i]) total += segLen[i]
        if (total <= 0.0) return null

        var main = 0.0
        var lane = 0.0
        var lit = 0.0
        var run = 0.0
        val isolated = mutableListOf<Int>()

        for (i in samples.indices) {
            if (!covered[i]) {
                run = 0.0   // unknown data is not the same as "isolated"
                continue
            }
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
            if (road != null && road.type in MAIN) main += w else lane += w

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

        return RouteContext(
            lengthM = fullLen,
            mainRoadPct = (main / total * 100).roundToInt(),
            lanePct = (lane / total * 100).roundToInt(),
            litPct = (lit / total * 100).roundToInt(),
            shopsNearby = countNear(shops, samples, 100.0),
            hospitals = clusterCount(hospitals, samples, 500.0, 60.0),
            police = clusterCount(police, samples, 500.0, 60.0),
            metroStations = clusterCount(metro, samples, 500.0, 150.0),
            isolatedStretchesM = isolated,
            busStops = clusterCount(buses, samples, 200.0, 60.0),
            autoStands = clusterCount(autos, samples, 300.0, 100.0)
        )
    }

    private fun countNear(list: List<GeoPoint>, samples: List<GeoPoint>, maxD: Double) =
        list.count { pt -> samples.any { it.distanceToAsDouble(pt) <= maxD } }

    /** Counts places near the route, merging points that are close together (e.g. several metro entrances = one station). */
    private fun clusterCount(
        list: List<GeoPoint>,
        samples: List<GeoPoint>,
        maxD: Double,
        mergeM: Double
    ): Int {
        val near = list.filter { pt -> samples.any { it.distanceToAsDouble(pt) <= maxD } }
        val reps = mutableListOf<GeoPoint>()
        for (p in near) if (reps.none { it.distanceToAsDouble(p) <= mergeM }) reps.add(p)
        return reps.size
    }

    /**
     * Uses bounding boxes instead of "around:" polylines. Overpass answers these much faster.
     * 0.001 degree is roughly 110 m.
     */
    private fun buildQuery(chunk: List<GeoPoint>): String {
        val south = chunk.minOf { it.latitude }
        val north = chunk.maxOf { it.latitude }
        val west = chunk.minOf { it.longitude }
        val east = chunk.maxOf { it.longitude }
        fun bb(pad: Double) = String.format(
            Locale.US, "%.5f,%.5f,%.5f,%.5f", south - pad, west - pad, north + pad, east + pad
        )
        val tight = bb(0.0004)   // roads, street lamps (~40 m)
        val mid = bb(0.0015)     // shops, taxi stands (~150 m)
        val bus = bb(0.002)      // bus stops (~200 m)
        val wide = bb(0.005)     // hospital, police, metro (~500 m)
        val dollar = "$"
        return """
[out:json][timeout:15];
way["highway"~"^(motorway|trunk|primary|secondary|tertiary)${'$'}dollar"](${'$'}tight)->.roads;
(
  nwr["shop"]($mid);
  nwr["amenity"~"^($SHOP_AMENITIES)$dollar"]($mid);
)->.shops;
nwr["amenity"~"^(hospital|police)$dollar"]($wide)->.help;
(
  nwr["railway"="station"]["station"="subway"]($wide);
  nwr["railway"="subway_entrance"]($wide);
  nwr["railway"="station"]["network"~"Delhi Metro",i]($wide);
)->.metro;
node["highway"="bus_stop"]($bus)->.bus;
nwr["amenity"="taxi"]($mid)->.taxi;
node["highway"="street_lamp"]($tight)->.lamps;
.roads out geom;
.shops out center;
(.help;.metro;.bus;.taxi;) out center;
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