package com.jacob.pokemonscanner.model

import java.time.Instant

data class Detection<T>(
    val value: T?,
    val confidence: Float,
    val rawValue: String? = null,
    val warning: String? = null,
) {
    init { require(confidence in 0f..1f) }
}

enum class TriState { YES, NO, UNKNOWN }

data class IvSet(
    val attack: Int,
    val defense: Int,
    val stamina: Int,
    val confidence: Float,
) {
    init {
        require(attack in 0..15 && defense in 0..15 && stamina in 0..15)
        require(confidence in 0f..1f)
    }

    val total: Int get() = attack + defense + stamina
    val percentage: Double get() = total * 100.0 / 45.0
}

data class PokemonScan(
    val speciesName: Detection<String> = Detection(null, 0f),
    val nickname: Detection<String> = Detection(null, 0f),
    val form: Detection<String> = Detection(null, 0f),
    val cp: Detection<Int> = Detection(null, 0f),
    val currentHp: Detection<Int> = Detection(null, 0f),
    val ivs: Detection<IvSet> = Detection(null, 0f),
    val possibleLevels: List<Double> = emptyList(),
    val levelConfidence: Float = 0f,
    val shiny: TriState = TriState.UNKNOWN,
    val favorite: TriState = TriState.UNKNOWN,
    val screenshotHash: String? = null,
    val capturedAt: Instant = Instant.now(),
)

enum class ScanMode { ASSISTED, AUTOMATIC, REPLAY }
enum class ScreenType { DETAIL, APPRAISAL, UNKNOWN }

enum class ScanPhase {
    IDLE,
    REQUESTING_PERMISSION,
    WAITING_FOR_SCREEN,
    WAITING_FOR_STABLE_FRAME,
    DETECTING_SCREEN_TYPE,
    READING_POKEMON_DETAILS,
    OPENING_APPRAISAL,
    WAITING_FOR_APPRAISAL,
    READING_APPRAISAL,
    VALIDATING_RESULT,
    SAVING_POKEMON,
    MOVING_TO_NEXT_POKEMON,
    FINISHED,
    PAUSED,
    ERROR,
}

enum class StopReason {
    USER_STOPPED,
    MAXIMUM_REACHED,
    REPEATED_POKEMON,
    REPEATING_SEQUENCE,
    SCREEN_NOT_FOUND,
    CAPTURE_REVOKED,
    ACCESSIBILITY_UNAVAILABLE,
    ADVANCE_FAILED,
    RECOGNITION_FAILED,
    COMPLETED,
}

enum class ScannerErrorCode {
    CAPTURE_PERMISSION_DENIED,
    CAPTURE_REVOKED,
    DETAIL_SCREEN_NOT_FOUND,
    APPRAISAL_SCREEN_NOT_FOUND,
    OCR_FAILED,
    IV_BARS_UNREADABLE,
    DATABASE_FAILURE,
    ACCESSIBILITY_DISABLED,
    GESTURE_FAILED,
    SCREEN_DID_NOT_CHANGE,
}

data class ScannerSnapshot(
    val phase: ScanPhase = ScanPhase.IDLE,
    val mode: ScanMode = ScanMode.ASSISTED,
    val scanCount: Int = 0,
    val failureCount: Int = 0,
    val current: PokemonScan? = null,
    val message: String = "Idle",
    val stopReason: StopReason? = null,
    val errorCode: ScannerErrorCode? = null,
)

data class NormalizedPoint(val x: Float, val y: Float) {
    init { require(x in 0f..1f && y in 0f..1f) }
}

data class AutomationProfile(
    val menuButton: NormalizedPoint = NormalizedPoint(0.86f, 0.92f),
    val appraiseMenuItem: NormalizedPoint = NormalizedPoint(0.73f, 0.73f),
    val nextAppraisalArrow: NormalizedPoint = NormalizedPoint(0.965f, 0.81f),
    val swipeStart: NormalizedPoint = NormalizedPoint(0.78f, 0.48f),
    val swipeEnd: NormalizedPoint = NormalizedPoint(0.22f, 0.48f),
    val gestureDurationMs: Long = 350,
    val screenTimeoutMs: Long = 8_000,
    val stableDurationMs: Long = 650,
    val maxScanCount: Int = 2_000,
    val maxAdvanceAttempts: Int = 3,
)
