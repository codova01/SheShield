package com.example.sheshield.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import kotlin.math.roundToInt

object ReportStore {
    const val MIN_REPORTS = 3

    private var appContext: Context? = null
    private var seed: List<CommunityReport> = emptyList()
    private var mine: MutableList<CommunityReport> = mutableListOf()

    /** Goes up every time reports change, so screens can recompute warnings live. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun myFile(): File? = appContext?.let { File(it.filesDir, "my_reports.json") }

    fun init(context: Context) {
        appContext = context.applicationContext
        seed = try {
            val text = context.assets.open("reports_seed.json").bufferedReader().use { it.readText() }
            parse(text, demo = true)
        } catch (e: Exception) {
            Log.w("Reports", "No seed file found in assets: ${e.message}")
            emptyList()
        }
        mine = try {
            val f = myFile()
            if (f != null && f.exists()) parse(f.readText(), demo = false).toMutableList() else mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
        Log.i("Reports", "Loaded ${seed.size} seeded + ${mine.size} own reports")
    }

    private fun parse(text: String, demo: Boolean): List<CommunityReport> {
        val arr = JSONObject(text).getJSONArray("reports")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val cat = try { ReportCategory.valueOf(o.getString("c")) } catch (_: Exception) { null }
            if (cat == null) null
            else CommunityReport(cat, o.getDouble("lat"), o.getDouble("lng"), o.getInt("b"), demo)
        }
    }

    private fun saveMine() {
        val arr = JSONArray()
        mine.forEach {
            arr.put(JSONObject().put("c", it.category.name).put("lat", it.lat).put("lng", it.lng).put("b", it.bucket))
        }
        try {
            myFile()?.writeText(JSONObject().put("reports", arr).toString())
        } catch (_: Exception) { }
    }

    /** Saves an anonymous report: location rounded to a ~100 m grid, time rounded to a 2-hour bucket. */
    fun add(category: ReportCategory, lat: Double, lng: Double, hour: Int) {
        val gl = (lat * 1000).roundToInt() / 1000.0
        val gn = (lng * 1000).roundToInt() / 1000.0
        mine.add(CommunityReport(category, gl, gn, hour / 2, demo = false))
        saveMine()
        _version.value++
    }

    /** For re-recording the demo video. */
    fun resetMine() {
        mine.clear()
        saveMine()
        _version.value++
    }

    fun summarize(points: List<GeoPoint>, hour: Int, radiusM: Double): ReportSummary {
        val bucket = hour / 2
        if (points.isEmpty()) return ReportSummary(emptyMap(), bucket)
        val stride = maxOf(1, points.size / 400)
        val sampled = points.filterIndexed { i, _ -> i % stride == 0 } + points.last()
        val counts = mutableMapOf<ReportCategory, Int>()
        for (r in seed + mine) {
            if (r.bucket != bucket) continue
            val rp = GeoPoint(r.lat, r.lng)
            if (sampled.any { it.distanceToAsDouble(rp) <= radiusM }) {
                counts[r.category] = (counts[r.category] ?: 0) + 1
            }
        }
        return ReportSummary(counts, bucket)
    }

    fun summarizeAround(center: GeoPoint, hour: Int, radiusM: Double = 200.0): ReportSummary =
        summarize(listOf(center), hour, radiusM)
}

