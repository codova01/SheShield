package com.example.sheshield.data

data class RouteContext(
    val lengthM: Double,
    val mainRoadPct: Int,
    val lanePct: Int,
    val litPct: Int,
    val shopsNearby: Int,
    val hospitals: Int,
    val police: Int,
    val metroStations: Int,
    val isolatedStretchesM: List<Int>
) {
    val longestIsolatedM: Int get() = isolatedStretchesM.maxOrNull() ?: 0
}

