package com.example.sheshield.data

import org.osmdroid.util.GeoPoint

data class RouteOption(
    val id: Int,
    val points: List<GeoPoint>,
    val distanceM: Double,
    val durationS: Double
)
