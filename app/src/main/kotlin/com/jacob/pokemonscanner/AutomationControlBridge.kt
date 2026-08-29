package com.jacob.pokemonscanner

object AutomationControlBridge {
    @Volatile private var pauseCallback: (() -> Unit)? = null
    @Volatile private var stopCallback: (() -> Unit)? = null
    @Volatile private var captureLostCallback: (() -> Unit)? = null

    fun bind(onPause: () -> Unit, onStop: () -> Unit, onCaptureLost: () -> Unit) {
        pauseCallback = onPause
        stopCallback = onStop
        captureLostCallback = onCaptureLost
    }

    fun unbind() {
        pauseCallback = null
        stopCallback = null
        captureLostCallback = null
    }

    fun pause() = pauseCallback?.invoke()
    fun stop() = stopCallback?.invoke()
    fun captureLost() = captureLostCallback?.invoke()
}
