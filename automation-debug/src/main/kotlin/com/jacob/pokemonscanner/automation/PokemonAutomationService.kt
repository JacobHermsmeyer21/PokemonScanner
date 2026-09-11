package com.jacob.pokemonscanner.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jacob.pokemonscanner.image.GiftCueClassifier
import com.jacob.pokemonscanner.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

class PokemonAutomationService : AccessibilityService() {
    private var suppressManualControlUntil = 0L
    private var overlay: TargetOverlayView? = null

    override fun onServiceConnected() {
        AutomationServiceBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START &&
            System.currentTimeMillis() > suppressManualControlUntil
        ) {
            AutomationServiceBridge.notifyManualControl()
        }
    }

    override fun onInterrupt() {
        AutomationServiceBridge.notifyManualControl()
    }

    override fun onDestroy() {
        hideTarget()
        AutomationServiceBridge.detach(this)
        super.onDestroy()
    }

    fun recognizeAccessibilityScreen(): ScreenObservation? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != POKEMON_GO_PACKAGE) return ScreenObservation(
            GameScreen.GAME_NOT_FOREGROUND, 1f, RecognitionSource.ACCESSIBILITY_NODE,
        )
        val nodes = buildList { collectNodes(root, this) }
        if (nodes.isEmpty()) return null
        val classification = GiftCueClassifier.classify(nodes.joinToString(" ") { it.text })
        if (classification.screen == GameScreen.UNKNOWN) return null
        val targets = linkedMapOf<AutomationAction, RecognitionTarget>()
        fun target(action: AutomationAction, vararg phrases: String) {
            nodes.firstOrNull { node -> phrases.any { node.text.contains(it, ignoreCase = true) } }?.let { node ->
                targets[action] = node.toTarget(phrases.first())
            }
        }
        target(AutomationAction.OPEN_FRIENDS, "Friends")
        target(AutomationAction.OPEN_SORT, "Sort")
        target(AutomationAction.SELECT_GIFT_SORT, "Gift")
        target(AutomationAction.SELECT_FRIENDSHIP_SORT, "Friendship level")
        target(AutomationAction.OPEN_GIFT, "Open")
        target(AutomationAction.SEND_GIFT, "Send Gift")
        target(AutomationAction.CONFIRM_SEND, "Send")
        target(AutomationAction.OPEN_ITEM_BAG, "Manage Items", "Item Bag")
        target(AutomationAction.CONFIRM_DISCARD, "Discard", "Yes")
        target(AutomationAction.SELECT_ALL_QUANTITY, "Max", "All")

        val friendCandidates = nodes.filter { it.text.contains(Regex("sent you a\\s+gift", RegexOption.IGNORE_CASE)) }
            .map { node ->
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(node.text.trim().lowercase().toByteArray())
                    .joinToString("") { "%02x".format(it) }
                RecognizedFriendCandidate(hash, node.toTarget("Friend with received gift"))
            }
        val cleanup = nodes.mapNotNull { node ->
            CleanupItem.fromExactRecognizedName(node.text)?.let { item ->
                CleanupCandidate(item, node.toTarget("Discard ${item.displayName}"))
            }
        }
        val selectedSortNode = nodes.firstOrNull { it.selected && (
            it.text.contains("Gift", true) || it.text.contains("Friendship level", true)
        ) }
        val selectedMode = when {
            selectedSortNode?.text?.contains("Friendship level", true) == true -> GiftSortMode.FRIENDSHIP_LEVEL
            selectedSortNode?.text?.contains("Gift", true) == true -> GiftSortMode.GIFT_STATUS
            else -> null
        }
        val selectedDirection = selectedSortNode?.text?.let { label ->
            when {
                label.contains("ascending", true) || label.contains("up", true) -> GiftSortDirection.ASCENDING
                label.contains("descending", true) || label.contains("down", true) -> GiftSortDirection.DESCENDING
                else -> null
            }
        }
        val sendNode = nodes.firstOrNull { it.text.equals("Send Gift", true) }
        val exactItem = nodes.mapNotNull { CleanupItem.fromExactRecognizedName(it.text) }.firstOrNull()
        val pageHash = MessageDigest.getInstance("SHA-256")
            .digest(nodes.joinToString("|") { "${it.bounds.top}:${it.bounds.bottom}:${it.text}" }.toByteArray())
            .joinToString("") { "%02x".format(it) }

        return ScreenObservation(
            screen = classification.screen,
            confidence = classification.confidence.coerceAtLeast(.97f),
            source = RecognitionSource.ACCESSIBILITY_NODE,
            safeCues = classification.safeCues,
            targets = targets,
            eligibleFriends = friendCandidates,
            cleanupCandidates = cleanup,
            pageFingerprint = pageHash,
            detectedSortMode = selectedMode,
            detectedSortDirection = selectedDirection,
            hasReceivedGift = nodes.any { it.text.contains("Open Gift", true) }.takeIf { classification.screen == GameScreen.FRIEND_DETAIL },
            canSendGift = sendNode?.enabled,
            hasGiftToSend = nodes.any { it.clickable && it.text.contains("Gift", true) }.takeIf { classification.screen == GameScreen.GIFT_PICKER },
            confirmedItem = exactItem.takeIf { classification.screen == GameScreen.DISCARD_CONFIRMATION },
        )
    }

    suspend fun execute(instruction: WorkflowInstruction, settings: GiftAssistantSettings): Boolean {
        val action = instruction.action
        if (action == AutomationAction.BACK) return performGlobalAction(GLOBAL_ACTION_BACK)
        if (action == AutomationAction.SCROLL_FRIENDS) {
            val scrollable = findFirstNode { it.isScrollable }
            if (scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) return true
            return swipe(NormalizedPoint(.5f, .80f), NormalizedPoint(.5f, .34f), 500)
        }
        if (action == AutomationAction.SCROLL_ITEMS) {
            val scrollable = findFirstNode { it.isScrollable }
            return scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        }

        val labels = labelsFor(action)
        if (labels.isNotEmpty()) {
            val node = findFirstNode { candidate ->
                candidate.isEnabled && labels.any { label ->
                    candidate.text?.toString()?.contains(label, ignoreCase = true) == true ||
                        candidate.contentDescription?.toString()?.contains(label, ignoreCase = true) == true
                }
            }
            if (node != null && clickNodeOrParent(node)) return true
        }
        val target = instruction.target ?: return false
        if (target.confidence < settings.minimumRecognitionConfidence) return false
        return tap(target.bounds.center, 180)
    }

    suspend fun openAppraisal(profile: AutomationProfile): Boolean {
        if (!tap(profile.menuButton, profile.gestureDurationMs)) return false
        delay(650)
        return tap(profile.appraiseMenuItem, profile.gestureDurationMs)
    }

    suspend fun advanceToNext(profile: AutomationProfile, useArrow: Boolean = true): Boolean =
        if (useArrow) tap(profile.nextAppraisalArrow, profile.gestureDurationMs)
        else swipe(profile.swipeStart, profile.swipeEnd, profile.gestureDurationMs)

    suspend fun tap(point: NormalizedPoint, durationMs: Long): Boolean {
        val metrics = resources.displayMetrics
        val path = Path().apply { moveTo(metrics.widthPixels * point.x, metrics.heightPixels * point.y) }
        return dispatch(path, durationMs)
    }

    suspend fun swipe(start: NormalizedPoint, end: NormalizedPoint, durationMs: Long): Boolean {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels * start.x, metrics.heightPixels * start.y)
            lineTo(metrics.widthPixels * end.x, metrics.heightPixels * end.y)
        }
        return dispatch(path, durationMs)
    }

    fun showTarget(target: RecognitionTarget) {
        hideTarget()
        val view = TargetOverlayView(target.bounds)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        getSystemService(WindowManager::class.java).addView(view, params)
        overlay = view
        Handler(Looper.getMainLooper()).postDelayed({ if (overlay === view) hideTarget() }, TARGET_DISPLAY_MS)
    }

    fun hideTarget() {
        val active = overlay ?: return
        overlay = null
        runCatching { getSystemService(WindowManager::class.java).removeView(active) }
    }

    private fun labelsFor(action: AutomationAction): List<String> = when (action) {
        AutomationAction.OPEN_FRIENDS -> listOf("Friends")
        AutomationAction.OPEN_SORT -> listOf("Sort")
        AutomationAction.SELECT_GIFT_SORT,
        AutomationAction.TOGGLE_SORT_DIRECTION,
        -> listOf("Gift")
        AutomationAction.SELECT_FRIENDSHIP_SORT -> listOf("Friendship level")
        AutomationAction.CLOSE_SORT -> listOf("Close")
        AutomationAction.OPEN_RECEIVED_GIFT,
        AutomationAction.OPEN_GIFT,
        -> listOf("Open")
        AutomationAction.SEND_GIFT -> listOf("Send Gift")
        AutomationAction.CONFIRM_SEND -> listOf("Send")
        AutomationAction.OPEN_ITEM_BAG -> listOf("Manage Items", "Item Bag")
        AutomationAction.SELECT_ALL_QUANTITY -> listOf("Max", "All")
        AutomationAction.CONFIRM_DISCARD -> listOf("Discard", "Yes")
        else -> emptyList()
    }

    private fun collectNodes(node: AccessibilityNodeInfo, output: MutableList<NodeSnapshot>) {
        val text = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            .joinToString(" ").trim()
        if (text.isNotEmpty()) {
            val bounds = Rect().also(node::getBoundsInScreen)
            output += NodeSnapshot(text, bounds, node.isClickable, node.isEnabled, node.isSelected)
        }
        for (index in 0 until node.childCount) node.getChild(index)?.let { collectNodes(it, output) }
    }

    private fun findFirstNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue += root
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun clickNodeOrParent(start: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = start
        repeat(5) {
            val current = node ?: return false
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = current.parent
        }
        return false
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { continuation ->
            suppressManualControlUntil = System.currentTimeMillis() + durationMs + 700
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(50))
            val accepted = dispatchGesture(
                GestureDescription.Builder().addStroke(stroke).build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null,
            )
            if (!accepted && continuation.isActive) continuation.resume(false)
        }

    private fun NodeSnapshot.toTarget(label: String): RecognitionTarget {
        val metrics = resources.displayMetrics
        return RecognitionTarget(
            NormalizedRect(
                (bounds.left.toFloat() / metrics.widthPixels).coerceIn(0f, 1f),
                (bounds.top.toFloat() / metrics.heightPixels).coerceIn(0f, 1f),
                (bounds.right.toFloat() / metrics.widthPixels).coerceIn(0f, 1f),
                (bounds.bottom.toFloat() / metrics.heightPixels).coerceIn(0f, 1f),
            ),
            .99f,
            RecognitionSource.ACCESSIBILITY_NODE,
            label,
        )
    }

    private inner class TargetOverlayView(private val target: NormalizedRect) : View(this@PokemonAutomationService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 214, 10)
            style = Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density * 6
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 255, 214, 10)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            val rect = android.graphics.RectF(target.left * width, target.top * height, target.right * width, target.bottom * height)
            canvas.drawRoundRect(rect, 22f, 22f, fill)
            canvas.drawRoundRect(rect, 22f, 22f, paint)
        }
    }

    private data class NodeSnapshot(
        val text: String,
        val bounds: Rect,
        val clickable: Boolean,
        val enabled: Boolean,
        val selected: Boolean,
    )

    companion object {
        private const val POKEMON_GO_PACKAGE = "com.nianticlabs.pokemongo"
        private const val TARGET_DISPLAY_MS = 8_000L
    }
}

object AutomationServiceBridge {
    @Volatile private var service: PokemonAutomationService? = null
    @Volatile private var runActive = false
    @Volatile private var manualControlCallback: (() -> Unit)? = null
    private val connectedState = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = connectedState.asStateFlow()
    val isConnected: Boolean get() = service != null
    fun controllerOrNull(): PokemonAutomationService? = service
    fun setRunActive(active: Boolean) { runActive = active }
    fun setManualControlCallback(callback: (() -> Unit)?) { manualControlCallback = callback }
    internal fun notifyManualControl() {
        if (runActive) manualControlCallback?.invoke()
    }
    internal fun attach(value: PokemonAutomationService) {
        service = value
        connectedState.value = true
    }

    internal fun detach(value: PokemonAutomationService) {
        if (service === value) {
            service = null
            connectedState.value = false
            if (runActive) manualControlCallback?.invoke()
        }
    }
}
