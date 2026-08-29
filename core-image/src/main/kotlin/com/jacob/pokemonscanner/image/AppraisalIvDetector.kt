package com.jacob.pokemonscanner.image

import android.graphics.Bitmap
import android.graphics.Color
import com.jacob.pokemonscanner.model.Detection
import com.jacob.pokemonscanner.model.IvSet
import kotlin.math.roundToInt

data class IvBarReading(val value: Int, val confidence: Float, val normalizedY: Float)
data class AppraisalReading(
    val ivs: Detection<IvSet>,
    val attack: IvBarReading,
    val defense: IvBarReading,
    val stamina: IvBarReading,
)

/**
 * Detector calibrated from normalized appraisal-card geometry. It accepts both the orange
 * partial-fill color and the red/pink 15-IV color, and samples a vertical band so animation
 * and antialiasing do not turn a segment boundary into an off-by-one result.
 */
class AppraisalIvDetector {
    private val rowCenters = floatArrayOf(0.771f, 0.816f, 0.861f)
    private val barStartX = 0.118f
    private val barEndX = 0.466f

    fun detect(bitmap: Bitmap): AppraisalReading {
        val rows = rowCenters.map { detectBar(bitmap, it) }
        val confidence = rows.minOf { it.confidence }
        val set = IvSet(rows[0].value, rows[1].value, rows[2].value, confidence)
        return AppraisalReading(
            ivs = Detection(
                value = set.takeIf { confidence >= 0.45f },
                confidence = confidence,
                rawValue = "${set.attack}/${set.defense}/${set.stamina}",
                warning = if (confidence < 0.45f) "Appraisal bars were not located confidently" else null,
            ),
            attack = rows[0], defense = rows[1], stamina = rows[2],
        )
    }

    private fun detectBar(bitmap: Bitmap, expectedY: Float): IvBarReading {
        val x0 = (bitmap.width * barStartX).roundToInt()
        val x1 = (bitmap.width * barEndX).roundToInt()
        val expected = (bitmap.height * expectedY).roundToInt()
        val searchRadius = (bitmap.height * 0.012f).roundToInt().coerceAtLeast(4)
        val halfBand = (bitmap.height * 0.0035f).roundToInt().coerceAtLeast(3)

        var bestY = expected
        var bestGeometry = -1f
        for (candidateY in expected - searchRadius..expected + searchRadius) {
            var barPixels = 0
            for (x in x0..x1 step 2) {
                val color = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), candidateY.coerceIn(0, bitmap.height - 1))
                if (isFilled(color) || isEmptyBar(color)) barPixels++
            }
            val geometry = barPixels.toFloat() / (((x1 - x0) / 2) + 1)
            if (geometry > bestGeometry) {
                bestGeometry = geometry
                bestY = candidateY
            }
        }

        val filledColumns = BooleanArray(x1 - x0 + 1)
        for (x in x0..x1) {
            var votes = 0
            var samples = 0
            for (y in bestY - halfBand..bestY + halfBand) {
                if (y in 0 until bitmap.height) {
                    if (isFilled(bitmap.getPixel(x, y))) votes++
                    samples++
                }
            }
            filledColumns[x - x0] = votes >= maxOf(2, samples / 3)
        }

        // Filled bars always start at the left cap. Isolated colored label/background pixels
        // are ignored by requiring a mostly continuous run from the beginning of the bar.
        val tolerance = maxOf(5, bitmap.width / 180)
        var misses = 0
        var rightmost = -1
        for (index in filledColumns.indices) {
            if (filledColumns[index]) {
                rightmost = index
                misses = 0
            } else if (rightmost >= 0) {
                misses++
                if (misses > tolerance) break
            } else if (index > tolerance) {
                break
            }
        }

        val fraction = if (rightmost < 0) 0f else (rightmost + 1f) / filledColumns.size
        val value = (fraction * 15f).roundToInt().coerceIn(0, 15)
        val quantizationError = kotlin.math.abs(fraction * 15f - value)
        val confidence = (bestGeometry * (1f - quantizationError.coerceAtMost(0.5f)))
            .coerceIn(0f, 1f)
        return IvBarReading(value, confidence, bestY.toFloat() / bitmap.height)
    }

    private fun isFilled(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        return hsv[1] >= 0.25f && hsv[2] >= 0.55f &&
            (hue in 8f..55f || hue <= 8f || hue >= 345f)
    }

    private fun isEmptyBar(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[1] <= 0.14f && hsv[2] in 0.70f..0.94f
    }
}
