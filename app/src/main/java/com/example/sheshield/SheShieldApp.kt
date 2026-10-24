package com.example.sheshield

import android.app.Application
import org.osmdroid.config.Configuration

class SheShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue =
            "SheShield-Hackathon/1.0 (github.com/codova01/SheShield)"
    }
}