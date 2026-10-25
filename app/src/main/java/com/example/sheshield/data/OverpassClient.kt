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
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )
    private const val ROUNDS = 2

    /**
     * Returns the cached answer if we have one, otherwise asks Overpass (GET request) and caches it.
     * Gives up (returns null) once the deadline (epoch millis) has passed, so the UI never hangs.
     */
    suspend fun query(
        context: Context,
        ql: String,
        deadline: Long = Long.MAX_VALUE
    ): JSONObject? = withContext(Dispatchers.IO) {
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

        val encoded = URLEncoder.encode(ql, "UTF-8")

        for (round in 1..ROUNDS) {
            for (endpoint in endpoints) {
                if (System.currentTimeMillis() > deadline) {
                    Log.w("Overpass", "Deadline reached, giving up")
                    return@withContext null
                }
                val t0 = System.currentTimeMillis()
                try {
                    val conn = (URL("$endpoint?data=$encoded").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5_000
                        readTimeout = 15_000
                        setRequestProperty("User-Agent", "SheShield-Hackathon/1.0 (github.com/codova01/SheShield)")
                    }
                    val code = conn.responseCode
                    if (code == 200) {
                        val text = conn.inputStream.bufferedReader().readText()
                        val json = JSONObject(text)
                        if (json.has("elements") && !json.optString("remark").contains("runtime error")) {
                            cache.writeText(text)
                            conn.disconnect()
                            Log.i("Overpass", "OK ${System.currentTimeMillis() - t0} ms, ${text.length} bytes from $endpoint")
                            return@withContext json
                        }
                        Log.w("Overpass", "$endpoint gave 200 but no usable data: ${json.optString("remark")}")
                    } else {
                        Log.w("Overpass", "$endpoint -> HTTP $code after ${System.currentTimeMillis() - t0} ms")
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("Overpass", "$endpoint failed after ${System.currentTimeMillis() - t0} ms: ${e.javaClass.simpleName} ${e.message}")
                }
            }
            if (round < ROUNDS) delay(2_000L)
        }
        null
    }
}