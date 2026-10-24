package com.example.sheshield.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TrustedContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String
)

class ContactStore(context: Context) {
    private val prefs = context.getSharedPreferences("sheshield_prefs", Context.MODE_PRIVATE)

    fun load(): List<TrustedContact> {
        val raw = prefs.getString("contacts", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            TrustedContact(o.getString("id"), o.getString("name"), o.getString("phone"))
        }
    }

    fun save(list: List<TrustedContact>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone))
        }
        prefs.edit().putString("contacts", arr.toString()).apply()
    }

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(v) = prefs.edit().putBoolean("onboarding_done", v).apply()
}

