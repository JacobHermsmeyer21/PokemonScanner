package com.jacob.pokemonscanner.image

import android.graphics.Bitmap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicLong

data class CapturedFrame(
    val id: Long,
    val capturedAtMillis: Long,
    val bitmap: Bitmap,
)

object FrameBus {
    private val ids = AtomicLong()
    private val channel = Channel<CapturedFrame>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { frame -> frame.bitmap.recycle() },
    )
    val frames = channel.receiveAsFlow()

    fun publish(bitmap: Bitmap) {
        val frame = CapturedFrame(ids.incrementAndGet(), System.currentTimeMillis(), bitmap)
        if (channel.trySend(frame).isFailure) bitmap.recycle()
    }

    fun clear() {
        while (true) {
            val frame = channel.tryReceive().getOrNull() ?: break
            frame.bitmap.recycle()
        }
    }
}
