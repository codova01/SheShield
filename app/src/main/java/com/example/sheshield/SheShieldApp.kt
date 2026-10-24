
package com.example.sheshield

import android.app.Application
import com.example.sheshield.data.OfflineSeed
import com.example.sheshield.data.ReportStore
import org.osmdroid.config.Configuration

class SheShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Configuration.getInstance().userAgentValue =
            "SheShield-Hackathon/1.0 (github.com/codova01/SheShield)"

        OfflineSeed.install(this)
        ReportStore.init(this)
    }
}
