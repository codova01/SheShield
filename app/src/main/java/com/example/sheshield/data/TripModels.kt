package com.example.sheshield.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TransportMode(val label: String) {
    CAB("Cab"), AUTO("Auto"), METRO("Metro"), WALKING("Walking")
}

data class TripRequest(
    val destination: DemoPlace,
    val hour: Int,
    val minute: Int,
    val mode: TransportMode,
    val alone: Boolean
)

object TripState {
    private val _request = MutableStateFlow<TripRequest?>(null)
    val request: StateFlow<TripRequest?> = _request

    fun set(r: TripRequest) {
        _request.value = r
    }
}

