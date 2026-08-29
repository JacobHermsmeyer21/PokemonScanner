package com.jacob.pokemonscanner.image

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jacob.pokemonscanner.model.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AnalyzedScreen(
    val type: ScreenType,
    val scan: PokemonScan?,
    val appraisal: AppraisalReading?,
)

class PokemonScreenAnalyzer(
    private val ivDetector: AppraisalIvDetector = AppraisalIvDetector(),
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(bitmap: Bitmap): AnalyzedScreen {
        val text = recognize(bitmap)
        val cp = findInteger(text, bitmap, 0.02f, 0.16f, Regex("(?:CP)?\\s*([0-9]{1,5})", RegexOption.IGNORE_CASE))
        val hp = findInteger(text, bitmap, 0.40f, 0.54f, Regex("([0-9]{1,4})\\s*/\\s*[0-9]{1,4}\\s*HP", RegexOption.IGNORE_CASE))
        val name = findName(text, bitmap)
        val appraisal = ivDetector.detect(bitmap)
        val appraisalPresent = appraisal.attack.confidence >= 0.45f &&
            appraisal.defense.confidence >= 0.45f && appraisal.stamina.confidence >= 0.45f

        val scan = if (cp.value != null || name.value != null) PokemonScan(
            speciesName = name,
            cp = cp,
            currentHp = hp,
            favorite = detectFavorite(bitmap),
            screenshotHash = PerceptualHash.dHash(bitmap),
        ) else null

        return AnalyzedScreen(
            type = when {
                appraisalPresent -> ScreenType.APPRAISAL
                scan != null && cp.value != null -> ScreenType.DETAIL
                else -> ScreenType.UNKNOWN
            },
            scan = scan,
            appraisal = appraisal.takeIf { appraisalPresent },
        )
    }

    private suspend fun recognize(bitmap: Bitmap): Text = suspendCancellableCoroutine { continuation ->
        val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
        task.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        task.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        continuation.invokeOnCancellation { /* ML Kit task completes without retaining this continuation. */ }
    }

    private fun findInteger(text: Text, bitmap: Bitmap, top: Float, bottom: Float, regex: Regex): Detection<Int> {
        val candidates = text.textBlocks.flatMap { it.lines }.filter { line ->
            val box = line.boundingBox ?: return@filter false
            box.centerY().toFloat() / bitmap.height in top..bottom
        }
        for (line in candidates) {
            val match = regex.find(line.text.replace(" ", "")) ?: continue
            val number = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
            return Detection(number, 0.92f, line.text)
        }
        return Detection(null, 0f, warning = "Text not found in expected region")
    }

    private fun findName(text: Text, bitmap: Bitmap): Detection<String> {
        val ignored = setOf("CP", "HP", "WEIGHT", "HEIGHT", "STARDUST", "ATTACK", "DEFENSE")
        val candidate = text.textBlocks.flatMap { it.lines }.filter { line ->
            val box = line.boundingBox ?: return@filter false
            val y = box.centerY().toFloat() / bitmap.height
            y in 0.37f..0.49f && line.text.length in 2..28 && line.text.any(Char::isLetter)
        }.filterNot { line -> ignored.any { line.text.uppercase().contains(it) } }
            .maxByOrNull { it.boundingBox?.height() ?: 0 }
        return if (candidate == null) Detection(null, 0f, warning = "Name was not found")
        else Detection(candidate.text.trim(), 0.86f, candidate.text)
    }

    private fun detectFavorite(bitmap: Bitmap): TriState {
        val left = (bitmap.width * 0.84f).toInt()
        val right = (bitmap.width * 0.96f).toInt()
        val top = (bitmap.height * 0.045f).toInt()
        val bottom = (bitmap.height * 0.11f).toInt()
        var yellow = 0
        var sampled = 0
        val hsv = FloatArray(3)
        for (y in top until bottom step 3) for (x in left until right step 3) {
            Color.colorToHSV(bitmap.getPixel(x, y), hsv)
            if (hsv[0] in 35f..60f && hsv[1] > 0.45f && hsv[2] > 0.65f) yellow++
            sampled++
        }
        return if (yellow > sampled * 0.035f) TriState.YES else TriState.NO
    }
}
