package com.jacob.pokemonscanner.model

import java.time.Instant

enum class GiftAutomationState {
    IDLE,
    VERIFYING_GAME,
    HOME_SCREEN,
    OPENING_PROFILE,
    OPENING_FRIENDS,
    APPLYING_SORT,
    SCANNING_FRIENDS,
    OPENING_FRIEND,
    OPENING_GIFT,
    HANDLING_GIFT_RESULTS,
    CHECKING_SEND_ELIGIBILITY,
    SENDING_GIFT,
    RETURNING_TO_LIST,
    BAG_FULL_DETECTED,
    OPENING_ITEM_BAG,
    CLEANING_ITEMS,
    RETURNING_TO_FRIENDS,
    PAUSED,
    COMPLETED,
    RECOVERING,
    ERROR,
}

enum class GiftSortMode(val label: String) {
    GIFT_STATUS("Gift status"),
    FRIENDSHIP_LEVEL("Friendship level"),
}

enum class GiftSortDirection(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending"),
}

enum class CleanupItem(
    val displayName: String,
    val mayEverBeDiscarded: Boolean,
    val enabledByDefault: Boolean,
) {
    POTION("Potion", true, true),
    SUPER_POTION("Super Potion", true, true),
    HYPER_POTION("Hyper Potion", true, true),
    REVIVE("Revive", true, true),
    RAZZ_BERRY("Razz Berry", true, true),
    NANAB_BERRY("Nanab Berry", true, true),
    MAX_POTION("Max Potion", false, false),
    MAX_REVIVE("Max Revive", false, false),
    GOLDEN_RAZZ_BERRY("Golden Razz Berry", false, false),
    SILVER_PINAP_BERRY("Silver Pinap Berry", false, false),
    OTHER("Other item", false, false),
    ;

    companion object {
        val defaultSelection: Set<CleanupItem> = entries.filterTo(linkedSetOf()) { it.enabledByDefault }

        /** Exact-name matching is intentional: "Razz Berry" must never match "Golden Razz Berry". */
        fun fromExactRecognizedName(value: String): CleanupItem? {
            val normalized = value.trim().replace(Regex("\\s+"), " ").lowercase()
            return entries.firstOrNull { it != OTHER && it.displayName.lowercase() == normalized }
        }
    }
}

data class GiftAssistantSettings(
    val sortMode: GiftSortMode = GiftSortMode.GIFT_STATUS,
    val sortDirection: GiftSortDirection = GiftSortDirection.DESCENDING,
    val cleanupItems: Set<CleanupItem> = CleanupItem.defaultSelection,
    val interactionDelayMs: Long = 900,
    val screenTimeoutMs: Long = 12_000,
    val retryLimit: Int = 3,
    val minimumRecognitionConfidence: Float = 0.82f,
    val dryRun: Boolean = true,
    val reducedMotion: Boolean = false,
) {
    init {
        require(interactionDelayMs in 350..5_000)
        require(screenTimeoutMs in 3_000..60_000)
        require(retryLimit in 1..8)
        require(minimumRecognitionConfidence in 0.5f..1f)
    }
}

enum class GameScreen {
    HOME_MAP,
    TRAINER_PROFILE,
    FRIENDS_LIST,
    SORT_MENU,
    FRIEND_DETAIL,
    GIFT_POSTCARD,
    GIFT_RESULTS,
    GIFT_PICKER,
    GIFT_COMPOSE,
    FRIENDSHIP_MESSAGE,
    POSTCARD_PROMPT,
    STICKER_PICKER,
    BAG_FULL_DIALOG,
    ITEM_BAG,
    DISCARD_CONFIRMATION,
    OPEN_LIMIT_DIALOG,
    SEND_LIMIT_DIALOG,
    NO_GIFTS_DIALOG,
    NETWORK_ERROR,
    LOADING,
    GAME_NOT_FOREGROUND,
    SYSTEM_DIALOG,
    UNKNOWN,
}

enum class RecognitionSource { ACCESSIBILITY_NODE, OCR, TEMPLATE, CALIBRATION_PROFILE }

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(left <= right && top <= bottom)
    }

    val center: NormalizedPoint get() = NormalizedPoint((left + right) / 2f, (top + bottom) / 2f)
}

enum class AutomationAction {
    NONE,
    OPEN_PROFILE,
    OPEN_FRIENDS,
    OPEN_SORT,
    SELECT_GIFT_SORT,
    SELECT_FRIENDSHIP_SORT,
    TOGGLE_SORT_DIRECTION,
    CLOSE_SORT,
    SELECT_FRIEND,
    SCROLL_FRIENDS,
    OPEN_RECEIVED_GIFT,
    OPEN_GIFT,
    DISMISS_KNOWN_DIALOG,
    DISMISS_GIFT_RESULTS,
    SEND_GIFT,
    SELECT_GIFT,
    CONFIRM_SEND,
    BACK,
    OPEN_ITEM_BAG,
    OPEN_DISCARD,
    SELECT_ALL_QUANTITY,
    CONFIRM_DISCARD,
}

data class RecognitionTarget(
    val bounds: NormalizedRect,
    val confidence: Float,
    val source: RecognitionSource,
    val safeLabel: String,
) {
    init { require(confidence in 0f..1f) }
}

data class RecognizedFriendCandidate(
    /** Session-only hash. Trainer names must not be persisted or logged. */
    val sessionFingerprint: String,
    val target: RecognitionTarget,
)

data class CleanupCandidate(
    val item: CleanupItem,
    val target: RecognitionTarget,
    val recognizedQuantity: Int? = null,
)

data class ScreenObservation(
    val screen: GameScreen,
    val confidence: Float,
    val source: RecognitionSource,
    val safeCues: Set<String> = emptySet(),
    val targets: Map<AutomationAction, RecognitionTarget> = emptyMap(),
    val eligibleFriends: List<RecognizedFriendCandidate> = emptyList(),
    val cleanupCandidates: List<CleanupCandidate> = emptyList(),
    val pageFingerprint: String? = null,
    val detectedSortMode: GiftSortMode? = null,
    val detectedSortDirection: GiftSortDirection? = null,
    val hasReceivedGift: Boolean? = null,
    val canSendGift: Boolean? = null,
    val hasGiftToSend: Boolean? = null,
    val confirmedItem: CleanupItem? = null,
    val selectedQuantity: Int? = null,
    val availableQuantity: Int? = null,
    val capturedAt: Instant = Instant.now(),
) {
    init { require(confidence in 0f..1f) }
}

enum class GiftStopReason(val label: String) {
    USER_STOPPED("Stopped by user"),
    MANUAL_CONTROL("Paused when manual control was detected"),
    OPEN_LIMIT_REACHED("Gift-opening limit reached"),
    SEND_LIMIT_REACHED("Gift-sending limit reached"),
    NO_ELIGIBLE_FRIENDS("No more eligible friends"),
    NO_GIFTS_TO_SEND("No gifts remain to send"),
    CAPTURE_LOST("Screen capture ended"),
    UNSAFE_TO_CONTINUE("Safe continuation was not possible"),
    COMPLETED("Completed"),
}

enum class LogSeverity { INFO, WARNING, ERROR }

data class SessionLogEntry(
    val timestamp: Instant,
    val state: GiftAutomationState,
    val message: String,
    val severity: LogSeverity = LogSeverity.INFO,
)

data class GiftWorkflowSnapshot(
    val state: GiftAutomationState = GiftAutomationState.IDLE,
    val resumeState: GiftAutomationState? = null,
    val currentAction: String = "Ready",
    val giftsOpened: Int = 0,
    val giftsSent: Int = 0,
    val friendsSkipped: Int = 0,
    val itemsDiscarded: Map<CleanupItem, Int> = emptyMap(),
    val processedFriendFingerprints: Set<String> = emptySet(),
    val currentFriendFingerprint: String? = null,
    val scannedEmptyPages: Set<String> = emptySet(),
    val currentScreen: GameScreen = GameScreen.UNKNOWN,
    val recognitionConfidence: Float = 0f,
    val recognitionSource: RecognitionSource? = null,
    val retryCount: Int = 0,
    val lastAction: AutomationAction = AutomationAction.NONE,
    val lastTarget: RecognitionTarget? = null,
    val sortVerified: Boolean = false,
    val giftCountedForCurrentFriend: Boolean = false,
    val pendingDiscardItem: CleanupItem? = null,
    val pendingDiscardQuantity: Int? = null,
    val stopReason: GiftStopReason? = null,
    val errorMessage: String? = null,
    val recoveryAction: String? = null,
    val startedAt: Instant? = null,
)

sealed interface GiftWorkflowEvent {
    data object Start : GiftWorkflowEvent
    data class ScreenRecognized(val observation: ScreenObservation) : GiftWorkflowEvent
    data class ActionFailed(val action: AutomationAction, val reason: String) : GiftWorkflowEvent
    data object Pause : GiftWorkflowEvent
    data object Resume : GiftWorkflowEvent
    data object UserStop : GiftWorkflowEvent
    data object ManualControlDetected : GiftWorkflowEvent
    data object CaptureLost : GiftWorkflowEvent
    data class GuidedTargetShown(val action: AutomationAction, val target: RecognitionTarget) : GiftWorkflowEvent
}

data class WorkflowInstruction(
    val action: AutomationAction,
    val target: RecognitionTarget? = null,
    val description: String,
)

data class WorkflowDecision(
    val snapshot: GiftWorkflowSnapshot,
    val instruction: WorkflowInstruction? = null,
)
