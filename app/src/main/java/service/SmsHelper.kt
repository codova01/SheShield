package com.example.sheshield.service

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object SmsHelper {
    @Suppress("DEPRECATION")
    fun send(context: Context, phone: String, text: String) {
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            val parts = sms.divideMessage(text)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
        } catch (e: Exception) {
            Log.e("SheShield", "SMS failed to $phone", e)
        }
    }
}

