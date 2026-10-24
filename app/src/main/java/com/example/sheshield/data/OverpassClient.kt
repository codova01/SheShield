package com.example.sheshield.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

object OverpassClient {
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.openstreetmap.fr/api/interpreter",
        "https://z.overpass-api.de/api/interpreter"
    )
    private const val ROUNDS = 3

    /** Returns the cached answer if we have one, otherwise asks Overpass (with retries) and caches it. */
    suspend fun query(context: Context, ql: String): JSONObject? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "overpass").apply { mkdirs() }
        val key = MessageDigest.getInstance("SHA-256").digest(ql.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cache = File(dir, "$key.json")

        if (cache.exists()) {
            try {
                return@withContext JSONObject(cache.readText())
            } catch (_: Exception) {
                cache.delete()
            }
        }

        for (round in 1..ROUNDS) {
            for (endpoint in endpoints) {
                try {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 6_000
                        readTimeout = 20_000
                        setRequestProperty("User-Agent", "SheShield-Hackathon/1.0 (github.com/codova01/SheShield)")
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    }
                    conn.outputStream.use {
                        it.write(("data=" + URLEncoder.encode(ql, "UTF-8")).toByteArray())
                    }
                    val code = conn.responseCode
                    if (code == 200) {
                        val text = conn.inputStream.bufferedReader().readText()
                        // A busy server can return 200 with an error remark and no data.
                        val json = JSONObject(text)
                        if (json.has("elements") && !json.optString("remark").contains("runtime error")) {
                            cache.writeText(text)
                            conn.disconnect()
                            Log.i("Overpass", "OK from $endpoint (round $round)")
                            return@withContext json
                        }
                        Log.w("Overpass", "$endpoint gave 200 but no usable data: ${json.optString("remark")}")
                    } else {
                        Log.w("Overpass", "$endpoint -> HTTP $code (round $round)")
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("Overpass", "$endpoint failed: ${e.javaClass.simpleName} ${e.message}")
                }
            }
            if (round < ROUNDS) delay(round * 4_000L) // wait 4 s, then 8 s, before trying all servers again
        }
        null
    }
}