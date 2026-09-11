package com.jacob.pokemonscanner.automation

import com.jacob.pokemonscanner.domain.FingerprintBuilder
import com.jacob.pokemonscanner.domain.FingerprintStopPolicy
import com.jacob.pokemonscanner.domain.ScannerReducer
import com.jacob.pokemonscanner.image.*
import com.jacob.pokemonscanner.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class AutomaticScanCoordinator(
    private val analyzer: PokemonScreenAnalyzer,
    private val stopPolicy: FingerprintStopPolicy = FingerprintStopPolicy(),
) {
    private val mutableState = MutableStateFlow(ScannerSnapshot())
    val state: StateFlow<ScannerSnapshot> = mutableState.asStateFlow()
    private var job: Job? = null

    fun start(
        scope: CoroutineScope,
        profile: AutomationProfile,
        save: suspend (PokemonScan) -> Unit,
    ) {
        if (job?.isActive == true) return
        stopPolicy.clear()
        mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.Start(ScanMode.AUTOMATIC))
        mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.PermissionGranted)
        job = scope.launch(Dispatchers.Default) { runLoop(profile, save) }
    }

    fun pause() {
        job?.cancel()
        job = null
        mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.Pause)
    }

    fun stop(reason: StopReason = StopReason.USER_STOPPED) {
        job?.cancel()
        job = null
        FrameBus.clear()
        mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.Stop(reason))
    }

    private suspend fun runLoop(profile: AutomationProfile, save: suspend (PokemonScan) -> Unit) {
        val controller = AutomationServiceBridge.controllerOrNull()
        if (controller == null) {
            fail(ScannerErrorCode.ACCESSIBILITY_DISABLED, "Enable the internal automation accessibility service")
            return
        }

        var carryDetails: PokemonScan? = null
        while (currentCoroutineContext().isActive) {
            if (mutableState.value.scanCount >= profile.maxScanCount) {
                stop(StopReason.MAXIMUM_REACHED)
                return
            }

            val screen = try {
                awaitStableScreen(profile.screenTimeoutMs)
            } catch (_: TimeoutCancellationException) {
                fail(ScannerErrorCode.DETAIL_SCREEN_NOT_FOUND, "Timed out waiting for a stable Pokemon screen")
                return
            } catch (error: Throwable) {
                fail(ScannerErrorCode.OCR_FAILED, error.message ?: "Screen analysis failed")
                return
            }

            when (screen.type) {
                ScreenType.DETAIL -> {
                    val details = screen.scan
                    if (details?.cp?.value == null || details.speciesName.value == null) {
                        fail(ScannerErrorCode.OCR_FAILED, "Name or CP could not be read")
                        return
                    }
                    carryDetails = details
                    mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.DetailsRead(details))
                    FrameBus.clear()
                    if (!controller.openAppraisal(profile)) {
                        fail(ScannerErrorCode.GESTURE_FAILED, "The appraisal gesture was rejected")
                        return
                    }
                    mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.AppraisalGestureSent)
                    delay(700)
                }

                ScreenType.APPRAISAL -> {
                    val appraisal = screen.appraisal?.ivs
                    val details = screen.scan ?: carryDetails
                    val ivs = appraisal?.value
                    if (details == null || ivs == null) {
                        fail(ScannerErrorCode.IV_BARS_UNREADABLE, "The three appraisal bars could not be read")
                        return
                    }
                    mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.AppraisalRead(appraisal))
                    val completed = details.copy(ivs = appraisal)
                    save(completed)
                    mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.Saved(completed))

                    val stopReason = stopPolicy.record(FingerprintBuilder.build(completed))
                    if (stopReason != null) {
                        stop(stopReason)
                        return
                    }

                    var advanced = false
                    for (attempt in 0 until profile.maxAdvanceAttempts) {
                        FrameBus.clear()
                        advanced = controller.advanceToNext(profile, useArrow = attempt < 2)
                        if (advanced) break
                    }
                    if (!advanced) {
                        fail(ScannerErrorCode.GESTURE_FAILED, "Could not advance to the next Pokémon")
                        return
                    }
                    mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.AdvanceGestureSent)
                    carryDetails = null
                    delay(700)
                }

                ScreenType.UNKNOWN -> {
                    fail(ScannerErrorCode.DETAIL_SCREEN_NOT_FOUND, "Pokemon detail or appraisal screen not detected")
                    return
                }
            }
        }
    }

    private suspend fun awaitStableScreen(timeoutMs: Long): AnalyzedScreen = withTimeout(timeoutMs) {
        val stable = StableFrameDetector()
        while (true) {
            val frame = FrameBus.frames.first()
            val bitmap = frame.bitmap
            try {
                if (stable.add(GrayFrame.fromBitmap(bitmap))) {
                    mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.StableFrameFound)
                    val analyzed = analyzer.analyze(bitmap)
                    mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.ScreenDetected(analyzed.type))
                    return@withTimeout analyzed
                }
            } finally {
                bitmap.recycle()
            }
        }
        error("unreachable")
    }

    private fun fail(code: ScannerErrorCode, message: String) {
        job = null
        mutableState.value = ScannerReducer.reduce(mutableState.value, ScannerEvent.Fail(code, message))
    }
}
