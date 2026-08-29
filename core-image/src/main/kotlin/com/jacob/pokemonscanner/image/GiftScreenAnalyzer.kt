package com.jacob.pokemonscanner.image

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jacob.pokemonscanner.domain.ScreenRecognitionPort
import com.jacob.pokemonscanner.model.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GiftScreenAnalyzer(
    private val calibrationProfile: ImportedCalibrationProfile? = null,
) : ScreenRecognitionPort<Bitmap> {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(frame: Bitmap): ScreenObservation {
        val recognized = recognizeText(frame)
        var classification = GiftCueClassifier.classify(recognized.text)
        if (classification.screen == GameScreen.UNKNOWN && looksLikeHomeMap(frame)) {
            classification = CueClassification(GameScreen.HOME_MAP, .88f, setOf("map geometry", "main menu ball"))
        }

        val targets = targetsFor(classification.screen, recognized, frame)
        val friends = if (classification.screen == GameScreen.FRIENDS_LIST) {
            findEligibleFriends(recognized, frame)
        } else emptyList()
        val cleanup = if (classification.screen == GameScreen.ITEM_BAG) {
            findCleanupCandidates(recognized, frame)
        } else emptyList()
        val (sortMode, sortDirection) = if (classification.screen == GameScreen.SORT_MENU) {
            detectSelectedSort(frame)
        } else null to null
        val confirmedItem = if (classification.screen == GameScreen.DISCARD_CONFIRMATION) {
            findExactItemName(recognized)
        } else null

        return ScreenObservation(
            screen = classification.screen,
            confidence = classification.confidence,
            source = if (classification.screen == GameScreen.HOME_MAP) RecognitionSource.TEMPLATE else RecognitionSource.OCR,
            safeCues = classification.safeCues,
            targets = targets,
            eligibleFriends = friends,
            cleanupCandidates = cleanup,
            pageFingerprint = if (classification.screen == GameScreen.FRIENDS_LIST) PerceptualHash.dHash(frame) else null,
            detectedSortMode = sortMode,
            detectedSortDirection = sortDirection,
            hasReceivedGift = if (classification.screen == GameScreen.FRIEND_DETAIL) {
                targets.containsKey(AutomationAction.OPEN_RECEIVED_GIFT).takeIf { it }
            } else null,
            canSendGift = if (classification.screen == GameScreen.FRIEND_DETAIL) detectSendGiftEnabled(frame, recognized) else null,
            hasGiftToSend = if (classification.screen == GameScreen.GIFT_PICKER) targets.containsKey(AutomationAction.SELECT_GIFT) else null,
            confirmedItem = confirmedItem,
            // Quantities deliberately remain unverified until a discard-dialog fixture is calibrated.
            selectedQuantity = null,
            availableQuantity = null,
        )
    }

    private suspend fun recognizeText(bitmap: Bitmap): Text = suspendCancellableCoroutine { continuation ->
        val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
        task.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        task.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        continuation.invokeOnCancellation { }
    }

    private fun targetsFor(screen: GameScreen, text: Text, bitmap: Bitmap): Map<AutomationAction, RecognitionTarget> {
        val targets = linkedMapOf<AutomationAction, RecognitionTarget>()
        fun fixed(action: AutomationAction, rect: NormalizedRect, label: String, confidence: Float = .86f) {
            val calibrated = calibrationProfile?.targets?.get(screen to action)
            targets[action] = RecognitionTarget(
                calibrated ?: rect,
                if (calibrated != null) .98f else confidence,
                RecognitionSource.CALIBRATION_PROFILE,
                label,
            )
        }
        fun fromText(action: AutomationAction, phrase: String, label: String, confidence: Float = .96f) {
            findTextBounds(text, phrase)?.let { rect ->
                targets[action] = RecognitionTarget(rect.normalized(bitmap), confidence, RecognitionSource.OCR, label)
            }
        }

        when (screen) {
            GameScreen.HOME_MAP -> fixed(
                AutomationAction.OPEN_PROFILE,
                NormalizedRect(.025f, .83f, .25f, .98f),
                "Trainer portrait",
                .88f,
            )
            GameScreen.TRAINER_PROFILE -> {
                fromText(AutomationAction.OPEN_FRIENDS, "FRIENDS", "Friends tab")
                if (AutomationAction.OPEN_FRIENDS !in targets) {
                    fixed(AutomationAction.OPEN_FRIENDS, NormalizedRect(.35f, .035f, .65f, .13f), "Friends tab", .82f)
                }
            }
            GameScreen.FRIENDS_LIST -> fixed(
                AutomationAction.OPEN_SORT,
                NormalizedRect(.74f, .84f, .99f, .98f),
                "Friend sort button",
                .92f,
            )
            GameScreen.SORT_MENU -> {
                fromText(AutomationAction.SELECT_GIFT_SORT, "GIFT", "Gift sort")
                fromText(AutomationAction.SELECT_FRIENDSHIP_SORT, "FRIENDSHIP LEVEL", "Friendship-level sort")
                fixed(AutomationAction.CLOSE_SORT, NormalizedRect(.78f, .89f, .96f, .98f), "Close sort menu", .94f)
                val selected = detectSelectedSort(bitmap).first
                val selectedAction = when (selected) {
                    GiftSortMode.GIFT_STATUS -> AutomationAction.SELECT_GIFT_SORT
                    GiftSortMode.FRIENDSHIP_LEVEL -> AutomationAction.SELECT_FRIENDSHIP_SORT
                    null -> null
                }
                selectedAction?.let { action -> targets[action]?.let { targets[AutomationAction.TOGGLE_SORT_DIRECTION] = it } }
            }
            GameScreen.GIFT_POSTCARD -> fromText(AutomationAction.OPEN_GIFT, "OPEN", "Open gift")
            GameScreen.GIFT_RESULTS -> fixed(
                AutomationAction.DISMISS_GIFT_RESULTS,
                NormalizedRect(.2f, .45f, .8f, .82f),
                "Continue from rewards",
                .90f,
            )
            GameScreen.FRIEND_DETAIL -> {
                fromText(AutomationAction.SEND_GIFT, "SEND GIFT", "Send Gift")
                fixed(AutomationAction.BACK, NormalizedRect(.39f, .88f, .61f, .99f), "Close friend detail", .90f)
                findTextBounds(text, "OPEN")?.let {
                    targets[AutomationAction.OPEN_RECEIVED_GIFT] = RecognitionTarget(
                        it.normalized(bitmap), .94f, RecognitionSource.OCR, "Open received gift",
                    )
                }
            }
            GameScreen.GIFT_PICKER -> fixed(
                AutomationAction.SELECT_GIFT,
                NormalizedRect(.03f, .18f, .48f, .49f),
                "First available gift",
                .90f,
            )
            GameScreen.GIFT_COMPOSE -> fromText(AutomationAction.CONFIRM_SEND, "SEND", "Send selected gift")
            GameScreen.FRIENDSHIP_MESSAGE,
            GameScreen.POSTCARD_PROMPT,
            GameScreen.STICKER_PICKER,
            -> fixed(
                AutomationAction.DISMISS_KNOWN_DIALOG,
                NormalizedRect(.30f, .80f, .70f, .96f),
                "Recognized dialog continue control",
                .82f,
            )
            GameScreen.BAG_FULL_DIALOG -> {
                fromText(AutomationAction.OPEN_ITEM_BAG, "MANAGE ITEMS", "Manage items")
                fromText(AutomationAction.OPEN_ITEM_BAG, "ITEM BAG", "Open Item Bag")
            }
            GameScreen.DISCARD_CONFIRMATION -> {
                fromText(AutomationAction.CONFIRM_DISCARD, "DISCARD", "Confirm discard")
                fromText(AutomationAction.SELECT_ALL_QUANTITY, "MAX", "Select all quantity")
            }
            else -> Unit
        }
        return targets
    }

    private fun findEligibleFriends(text: Text, bitmap: Bitmap): List<RecognizedFriendCandidate> =
        text.textBlocks.mapNotNull { block ->
            val normalized = block.text.replace(Regex("\\s+"), " ").trim()
            if (!normalized.contains(Regex("sent you a gift", RegexOption.IGNORE_CASE))) return@mapNotNull null
            val bounds = block.boundingBox ?: return@mapNotNull null
            val row = Rect(
                (bitmap.width * .045f).toInt(),
                (bounds.top - bitmap.height * .045f).toInt().coerceAtLeast(0),
                (bitmap.width * .94f).toInt(),
                (bounds.bottom + bitmap.height * .045f).toInt().coerceAtMost(bitmap.height),
            )
            val crop = Bitmap.createBitmap(bitmap, row.left, row.top, row.width(), row.height())
            val cropHash = try {
                PerceptualHash.dHash(crop)
            } finally {
                crop.recycle()
            }
            val hashMaterial = "${row.top}:${row.bottom}:$cropHash"
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(hashMaterial.toByteArray())
                .joinToString("") { "%02x".format(it) }
            RecognizedFriendCandidate(
                sessionFingerprint = fingerprint,
                target = RecognitionTarget(row.normalized(bitmap), .94f, RecognitionSource.OCR, "Friend with received gift"),
            )
        }.sortedBy { it.target.bounds.top }

    private fun findCleanupCandidates(text: Text, bitmap: Bitmap): List<CleanupCandidate> =
        text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val item = CleanupItem.fromExactRecognizedName(line.text) ?: return@mapNotNull null
            val box = line.boundingBox ?: return@mapNotNull null
            val trashBounds = NormalizedRect(.82f, (box.centerY().toFloat() / bitmap.height - .035f).coerceAtLeast(0f), .98f, (box.centerY().toFloat() / bitmap.height + .035f).coerceAtMost(1f))
            CleanupCandidate(
                item,
                RecognitionTarget(trashBounds, .92f, RecognitionSource.OCR, "Discard ${item.displayName}"),
            )
        }

    private fun findExactItemName(text: Text): CleanupItem? = text.textBlocks
        .flatMap { it.lines }
        .mapNotNull { CleanupItem.fromExactRecognizedName(it.text) }
        .firstOrNull()

    private fun findTextBounds(text: Text, phrase: String): Rect? {
        val normalizedPhrase = phrase.uppercase()
        return text.textBlocks.firstNotNullOfOrNull { block ->
            if (block.text.uppercase().replace(Regex("\\s+"), " ").contains(normalizedPhrase)) block.boundingBox else null
        } ?: text.textBlocks.flatMap { it.lines }.firstNotNullOfOrNull { line ->
            if (line.text.uppercase().contains(normalizedPhrase)) line.boundingBox else null
        }
    }

    private fun detectSelectedSort(bitmap: Bitmap): Pair<GiftSortMode?, GiftSortDirection?> {
        val candidates = listOf(
            GiftSortMode.FRIENDSHIP_LEVEL to .685f,
            GiftSortMode.GIFT_STATUS to .775f,
        )
        val measured = candidates.map { (mode, center) ->
            val top = ((center - .035f) * bitmap.height).toInt().coerceAtLeast(0)
            val bottom = ((center + .035f) * bitmap.height).toInt().coerceAtMost(bitmap.height)
            val left = (.925f * bitmap.width).toInt()
            val right = (.985f * bitmap.width).toInt()
            val ys = mutableListOf<Int>()
            val hsv = FloatArray(3)
            for (y in top until bottom step 2) for (x in left until right step 2) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                if (hsv[0] in 55f..125f && hsv[1] > .28f && hsv[2] > .58f) ys += y
            }
            Triple(mode, center, ys)
        }
        val selected = measured.maxByOrNull { it.third.size }?.takeIf { it.third.size >= 12 } ?: return null to null
        val centerPx = selected.second * bitmap.height
        val topCount = selected.third.count { it < centerPx }
        val bottomCount = selected.third.size - topCount
        val direction = if (bottomCount > topCount * 1.08f) GiftSortDirection.DESCENDING else GiftSortDirection.ASCENDING
        return selected.first to direction
    }

    private fun detectSendGiftEnabled(bitmap: Bitmap, text: Text): Boolean? {
        val label = findTextBounds(text, "SEND GIFT") ?: return null
        val top = (label.top - bitmap.height * .12f).toInt().coerceAtLeast(0)
        val bottom = (label.top - bitmap.height * .015f).toInt().coerceAtLeast(top + 1)
        val left = (bitmap.width * .06f).toInt()
        val right = (bitmap.width * .30f).toInt()
        var colorful = 0
        var samples = 0
        val hsv = FloatArray(3)
        for (y in top until bottom step 4) for (x in left until right step 4) {
            Color.colorToHSV(bitmap.getPixel(x, y), hsv)
            if (hsv[1] > .30f && hsv[2] > .55f) colorful++
            samples++
        }
        if (samples == 0) return null
        val ratio = colorful.toFloat() / samples
        return when {
            ratio > .08f -> true
            ratio < .025f -> false
            else -> null
        }
    }

    private fun looksLikeHomeMap(bitmap: Bitmap): Boolean {
        val hsv = FloatArray(3)
        var terrain = 0
        var terrainSamples = 0
        for (y in (bitmap.height * .12f).toInt() until (bitmap.height * .76f).toInt() step 10) {
            for (x in 0 until bitmap.width step 10) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                if (hsv[0] in 65f..155f && hsv[1] > .20f && hsv[2] > .32f) terrain++
                terrainSamples++
            }
        }
        var redWhiteBallPixels = 0
        var ballSamples = 0
        for (y in (bitmap.height * .86f).toInt() until (bitmap.height * .96f).toInt() step 3) {
            for (x in (bitmap.width * .38f).toInt() until (bitmap.width * .62f).toInt() step 3) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                if ((hsv[0] < 15f || hsv[0] > 345f) && hsv[1] > .45f && hsv[2] > .55f) redWhiteBallPixels++
                ballSamples++
            }
        }
        return terrain > terrainSamples * .28f && redWhiteBallPixels > ballSamples * .015f
    }

    private fun Rect.normalized(bitmap: Bitmap): NormalizedRect = NormalizedRect(
        (left.toFloat() / bitmap.width).coerceIn(0f, 1f),
        (top.toFloat() / bitmap.height).coerceIn(0f, 1f),
        (right.toFloat() / bitmap.width).coerceIn(0f, 1f),
        (bottom.toFloat() / bitmap.height).coerceIn(0f, 1f),
    )
}
