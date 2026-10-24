package com.example.sheshield.service

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** Triggers after 3 strong shakes within 2 seconds. */
class ShakeDetector(
    private val getSensitivity: () -> Float,
    private val onShake: () -> Unit
) : SensorEventListener {

    private var count = 0
    private var windowStart = 0L
    private var lastShake = 0L

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val threshold = 3.2f - getSensitivity() * 1.2f   // 3.2g (low) to 2.0g (high)

        if (g > threshold) {
            val now = System.currentTimeMillis()
            if (now - lastShake < 300) return
            lastShake = now
            if (now - windowStart > 2000) {
                count = 0
                windowStart = now
            }
            count++
            if (count >= 3) {
                count = 0
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

