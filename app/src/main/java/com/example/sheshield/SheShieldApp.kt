package com.example.sheshield

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration

class SheShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            load(this@SheShieldApp, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = "SheShield-Hackathon/1.0 (github.com/codova01/SheShield)"
        }
    }
}

