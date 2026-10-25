package com.example.sheshield.data

import android.content.Context
import java.util.UUID

/** Keeps a salted hash of the vault PIN. The PIN itself is never stored. */
class VaultLock(context: Context) {
    private val prefs = context.getSharedPreferences("sheshield_vault", Context.MODE_PRIVATE)

    fun isSet(): Boolean = prefs.contains("pin_hash")

    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString()
        prefs.edit()
            .putString("salt", salt)
            .putString("pin_hash", hash(salt, pin))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString("salt", null) ?: return false
        return prefs.getString("pin_hash", null) == hash(salt, pin)
    }

    private fun hash(salt: String, pin: String): String =
        Crypto.sha256Hex("$salt:$pin".toByteArray(Charsets.UTF_8))
}