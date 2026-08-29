package com.jacob.pokemonscanner.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CaptureStatus {
    private val mutableActive = MutableStateFlow(false)
    val active = mutableActive.asStateFlow()
    internal fun setActive(value: Boolean) { mutableActive.value = value }
}
