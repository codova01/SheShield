package com.example.sheshield.data


enum class ReportCategory(val label: String, val weight: Double, val negative: Boolean) {
    CATCALLING("Catcalling", 2.0, true),
    STARING("Staring", 1.0, true),
    FOLLOWED("Followed", 3.0, true),
    CROWD("Crowding", 1.0, true),
    POOR_LIGHTING("Poor lighting", 1.0, true),
    FELT_SAFE("Felt safe", 0.0, false)
}

/** No user ID, no exact location: only a ~100 m grid cell and a 2-hour time bucket. */
data class CommunityReport(
    val category: ReportCategory,
    val lat: Double,
    val lng: Double,
    val bucket: Int,   // 0 = 12-2 AM, 1 = 2-4 AM ... 11 = 10 PM-12 AM
    val demo: Boolean
)

data class ReportSummary(val counts: Map<ReportCategory, Int>, val bucket: Int) {
    val negativeTotal: Int get() = counts.filter { it.key.negative }.values.sum()
    val feltSafe: Int get() = counts[ReportCategory.FELT_SAFE] ?: 0
    val penalty: Double get() = counts.entries.sumOf { it.key.weight * it.value }
    val meetsMinimum: Boolean get() = negativeTotal >= ReportStore.MIN_REPORTS

    /** Null until enough reports exist, so one report alone never shows a warning. */
    fun warningText(): String? {
        if (!meetsMinimum) return null
        val parts = counts.entries
            .filter { it.key.negative && it.value > 0 }
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.value} ${it.key.label.lowercase()}" }
        return "Reports here around ${bucketLabel(bucket)}: $parts"
    }
}

fun hourLabel(h: Int): String {
    val hh = h % 24
    val h12 = if (hh % 12 == 0) 12 else hh % 12
    return "$h12 ${if (hh < 12) "AM" else "PM"}"
}

fun bucketLabel(bucket: Int): String = "${hourLabel(bucket * 2)} to ${hourLabel(bucket * 2 + 2)}"