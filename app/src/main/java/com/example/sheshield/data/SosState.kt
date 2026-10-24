package com.example.sheshield.data


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SosPhase { IDLE, COUNTDOWN, ACTIVE }

object SosState {
    private val _phase = MutableStateFlow(SosPhase.IDLE)
    val phase: StateFlow<SosPhase> = _phase

    private val _secondsLeft = MutableStateFlow(0)
    val secondsLeft: StateFlow<Int> = _secondsLeft

    fun set(phase: SosPhase, seconds: Int = 0) {
        _phase.value = phase
        _secondsLeft.value = seconds
    }
}