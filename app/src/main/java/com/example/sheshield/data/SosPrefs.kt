package com.example.sheshield.data

import android.content.Context

class SosPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sheshield_prefs", Context.MODE_PRIVATE)

    // 0f = least sensitive, 1f = most sensitive
    var sensitivity: Float
        get() = prefs.getFloat("shake_sensitivity", 0.5f)
        set(v) = prefs.edit().putFloat("shake_sensitivity", v).apply()
}