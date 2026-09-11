package com.jacob.pokemonscanner.automation

import android.graphics.Bitmap
import com.jacob.pokemonscanner.domain.GiftWorkflowReducer
import com.jacob.pokemonscanner.domain.ScreenRecognitionPort
import com.jacob.pokemonscanner.image.FrameBus
import com.jacob.pokemonscanner.image.GrayFrame
import com.jacob.pokemonscanner.image.StableFrameDetector
import com.jacob.pokemonscanner.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Instant

class GiftAutomationCoordinator(
    private val screenshotRecognizer: ScreenRecognitionPort<Bitmap>,
) {
    private val mutableState = MutableStateFlow(GiftWorkflowSnapshot())
    val state: StateFlow<GiftWorkflowSnapshot> = mutableState.asStateFlow()
    private val mutableLog = MutableStateFlow<List<SessionLogEntry>>(emptyList())
    val log: StateFlow<List<SessionLogEntry>> = mutableLog.asStateFlow()

    private var job: Job? = null
    private var settings = GiftAssistantSettings()
    private var scope: CoroutineScope? = null
    private var persistLog: suspend (SessionLogEntry) -> Unit = { }

    fun start(
        scope: CoroutineScope,
        settings: GiftAssistantSettings,
        persistLog: suspend (SessionLogEntry) -> Unit = { },
    ) {
        if (job?.isActive == true) return
        this.scope = scope
        this.settings = settings
        this.persistLog = persistLog
        mutableLog.value = emptyList()
        apply(GiftWorkflowReducer.reduce(mutableState.value, GiftWorkflowEvent.Start, settings))
        launchLoop()
    }

    fun pause() {
        job?.cancel()
        job = null
        AutomationServiceBridge.setRunActive(false)
        apply(GiftWorkflowReducer.reduce(mutableState.value, GiftWorkflowEvent.Pause, settings))
    }

    fun resume() {
        if (job?.isActive == true || mutableState.value.state !in setOf(GiftAutomationState.PAUSED, GiftAutomationState.ERROR)) return
        apply(GiftWorkflowReducer.reduce(mutableState.value, GiftWorkflowEvent.Resume, settings))
        launchLoop()
    }

    fun stop() {
        job?.cancel()
        job = null
        AutomationServiceBridge.setRunActive(false)
        AutomationServiceBridge.setManualControlCallback(null)
        FrameBus.clear()
        apply(GiftWorkflowReducer.reduce(mutableState.value, GiftWorkflowEvent.UserStop, settings))
    }

    fun captureLost() {
        job?.cancel()
        job = null
        AutomationServiceBridge.setRunActive(false)
        apply(GiftWorkflowReducer.reduce(mutableState.value, GiftWorkflowEvent.CaptureLost, settings))
    }

    private fun launchLoop() {
        val activeScope = scope ?: return
        AutomationServiceBridge.setManualControlCallback(::manualControlDetected)
        AutomationServiceBridge.setRunActive(true)
        job = activeScope.launch(Dispatchers.Default) { runLoop() }
    }

    private fun manualControlDetected() {
        job?.cancel()
        job = null
        AutomationServiceBridge.setRunActive(false)
        apply(GiftWorkflowReducer.reduce(mutableState.value, GiftWorkflowEvent.ManualControlDetected, settings))
    }

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            val controller = AutomationServiceBridge.controllerOrNull()
            if (controller == null) {
                apply(GiftWorkflowReducer.reduce(
                    mutableState.value,
                    GiftWorkflowEvent.ActionFailed(AutomationAction.NONE, "Accessibility service is unavailable"),
                    settings,
                ))
                if (stopIfTerminal()) return
                delay(500)
                continue
            }

            val observation = try {
                awaitStableObservation(controller, settings.screenTimeoutMs)
            } catch (_: TimeoutCancellationException) {
                apply(GiftWorkflowReducer.reduce(
                    mutableState.value,
                    GiftWorkflowEvent.ActionFailed(mutableState.value.lastAction, "Timed out waiting for a recognized stable screen"),
                    settings,
                ))
                if (stopIfTerminal()) return
                continue
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                apply(GiftWorkflowReducer.reduce(
                    mutableState.value,
                    GiftWorkflowEvent.ActionFailed(mutableState.value.lastAction, error.message ?: "Recognition failed"),
                    settings,
                ))
                if (stopIfTerminal()) return
                continue
            }

            val decision = GiftWorkflowReducer.reduce(
                mutableState.value,
                GiftWorkflowEvent.ScreenRecognized(observation),
                settings,
            )
            apply(decision)
            if (stopIfTerminal()) return
            val instruction = decision.instruction ?: continue

            if (settings.dryRun) {
                val target = instruction.target
                if (target == null) {
                    apply(GiftWorkflowReducer.reduce(
                        mutableState.value,
                        GiftWorkflowEvent.ActionFailed(instruction.action, "No safe target is available for guided mode"),
                        settings,
                    ))
                } else {
                    withContext(Dispatchers.Main) { controller.showTarget(target) }
                    apply(GiftWorkflowReducer.reduce(
                        mutableState.value,
                        GiftWorkflowEvent.GuidedTargetShown(instruction.action, target),
                        settings,
                    ))
                }
                AutomationServiceBridge.setRunActive(false)
                return
            }

            delay(settings.interactionDelayMs)
            FrameBus.clear()
            val succeeded = controller.execute(instruction, settings)
            if (!succeeded) {
                apply(GiftWorkflowReducer.reduce(
                    mutableState.value,
                    GiftWorkflowEvent.ActionFailed(instruction.action, "Gesture was rejected or no verified target was available"),
                    settings,
                ))
                if (stopIfTerminal()) return
            } else {
                delay(if (settings.reducedMotion) settings.interactionDelayMs else settings.interactionDelayMs + 250)
            }
        }
    }

    private suspend fun awaitStableObservation(
        controller: PokemonAutomationService,
        timeoutMs: Long,
    ): ScreenObservation = withTimeout(timeoutMs) {
        val stable = StableFrameDetector(requiredStableFrames = 2)
        while (true) {
            val frame = FrameBus.frames.first()
            val bitmap = frame.bitmap
            try {
                if (!stable.add(GrayFrame.fromBitmap(bitmap))) continue
                val visual = screenshotRecognizer.recognize(bitmap)
                val nodes = withContext(Dispatchers.Main) { controller.recognizeAccessibilityScreen() }
                return@withTimeout merge(nodes, visual)
            } finally {
                bitmap.recycle()
            }
        }
        error("unreachable")
    }

    private fun merge(nodes: ScreenObservation?, visual: ScreenObservation): ScreenObservation {
        if (nodes == null || nodes.screen == GameScreen.UNKNOWN || nodes.screen != visual.screen) {
            return if (nodes != null && visual.screen == GameScreen.UNKNOWN) nodes else visual
        }
        return nodes.copy(
            confidence = maxOf(nodes.confidence, visual.confidence),
            safeCues = nodes.safeCues + visual.safeCues,
            targets = visual.targets + nodes.targets,
            eligibleFriends = nodes.eligibleFriends.ifEmpty { visual.eligibleFriends },
            cleanupCandidates = nodes.cleanupCandidates.ifEmpty { visual.cleanupCandidates },
            pageFingerprint = nodes.pageFingerprint ?: visual.pageFingerprint,
            detectedSortMode = nodes.detectedSortMode ?: visual.detectedSortMode,
            detectedSortDirection = nodes.detectedSortDirection ?: visual.detectedSortDirection,
            hasReceivedGift = nodes.hasReceivedGift ?: visual.hasReceivedGift,
            canSendGift = nodes.canSendGift ?: visual.canSendGift,
            hasGiftToSend = nodes.hasGiftToSend ?: visual.hasGiftToSend,
            confirmedItem = nodes.confirmedItem ?: visual.confirmedItem,
            selectedQuantity = nodes.selectedQuantity ?: visual.selectedQuantity,
            availableQuantity = nodes.availableQuantity ?: visual.availableQuantity,
        )
    }

    private fun apply(decision: WorkflowDecision) {
        val previous = mutableState.value
        mutableState.value = decision.snapshot
        if (previous.state != decision.snapshot.state || previous.currentAction != decision.snapshot.currentAction) {
            appendLog(decision.snapshot.currentAction, if (decision.snapshot.state == GiftAutomationState.ERROR) LogSeverity.ERROR else LogSeverity.INFO)
        }
    }

    private fun appendLog(message: String, severity: LogSeverity) {
        val entry = SessionLogEntry(Instant.now(), mutableState.value.state, sanitize(message), severity)
        mutableLog.value = (mutableLog.value + entry).takeLast(200)
        scope?.launch(Dispatchers.IO) { persistLog(entry) }
    }

    private fun sanitize(message: String): String = message
        .replace(Regex("[A-Za-z0-9]{20,}"), "[session-id]")
        .take(240)

    private fun stopIfTerminal(): Boolean {
        if (mutableState.value.state !in setOf(
                GiftAutomationState.PAUSED,
                GiftAutomationState.ERROR,
                GiftAutomationState.COMPLETED,
            )
        ) return false
        AutomationServiceBridge.setRunActive(false)
        job = null
        return true
    }
}
