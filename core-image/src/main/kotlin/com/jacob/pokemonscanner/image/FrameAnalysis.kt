package com.jacob.pokemonscanner.image

import android.graphics.Bitmap
import java.security.MessageDigest
import kotlin.math.abs

data class GrayFrame(val width: Int, val height: Int, val pixels: ByteArray) {
    init { require(pixels.size == width * height) }

    companion object {
        fun fromBitmap(bitmap: Bitmap, targetWidth: Int = 180): GrayFrame {
            val targetHeight = (bitmap.height * targetWidth.toFloat() / bitmap.width).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            val colors = IntArray(targetWidth * targetHeight)
            scaled.getPixels(colors, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            if (scaled !== bitmap) scaled.recycle()
            val gray = ByteArray(colors.size)
            colors.forEachIndexed { index, color ->
                val r = color shr 16 and 0xff
                val g = color shr 8 and 0xff
                val b = color and 0xff
                gray[index] = ((r * 30 + g * 59 + b * 11) / 100).toByte()
            }
            return GrayFrame(targetWidth, targetHeight, gray)
        }
    }
}

class StableFrameDetector(
    private val differenceThreshold: Float = 0.018f,
    private val requiredStableFrames: Int = 4,
) {
    private var previous: GrayFrame? = null
    private var stableFrames = 0

    fun add(frame: GrayFrame): Boolean {
        val old = previous
        previous = frame
        if (old == null || old.width != frame.width || old.height != frame.height) {
            stableFrames = 0
            return false
        }

        // Ignore the animated sprite/top background and compare the stable information-card area.
        val top = (frame.height * 0.34f).toInt()
        val bottom = (frame.height * 0.90f).toInt()
        var totalDifference = 0L
        var samples = 0
        for (y in top until bottom step 2) {
            for (x in 0 until frame.width step 2) {
                val index = y * frame.width + x
                totalDifference += abs((old.pixels[index].toInt() and 0xff) - (frame.pixels[index].toInt() and 0xff))
                samples++
            }
        }
        val difference = totalDifference.toFloat() / (samples * 255f)
        stableFrames = if (difference <= differenceThreshold) stableFrames + 1 else 0
        return stableFrames >= requiredStableFrames
    }

    fun reset() {
        previous = null
        stableFrames = 0
    }
}

object PerceptualHash {
    fun dHash(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val colors = IntArray(72)
        scaled.getPixels(colors, 0, 9, 0, 0, 9, 8)
        if (scaled !== bitmap) scaled.recycle()
        var value = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val left = luminance(colors[y * 9 + x])
            val right = luminance(colors[y * 9 + x + 1])
            if (left > right) value = value or (1L shl bit)
            bit++
        }
        return value.toULong().toString(16).padStart(16, '0')
    }

    fun sha256(bitmap: Bitmap): String {
        val bytes = java.io.ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun luminance(color: Int): Int {
        val r = color shr 16 and 0xff
        val g = color shr 8 and 0xff
        val b = color and 0xff
        return (r * 30 + g * 59 + b * 11) / 100
    }
}
