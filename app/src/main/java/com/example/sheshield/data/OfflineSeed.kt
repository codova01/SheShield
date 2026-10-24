package com.example.sheshield.data

import android.content.Context
import java.io.File

object OfflineSeed {
    /** Copies bundled demo data from assets into app storage, only for files that aren't there yet. */
    fun install(context: Context) {
        for (folder in listOf("overpass", "routes")) {
            try {
                val names = context.assets.list(folder) ?: continue
                val dir = File(context.filesDir, folder).apply { mkdirs() }
                for (name in names) {
                    val target = File(dir, name)
                    if (target.exists()) continue
                    context.assets.open("$folder/$name").use { input ->
                        target.outputStream().use { out -> input.copyTo(out) }
                    }
                }
            } catch (_: Exception) {
                // nothing bundled yet, which is fine
            }
        }
    }
}