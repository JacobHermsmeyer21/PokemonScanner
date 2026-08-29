package com.jacob.pokemonscanner.domain

import com.jacob.pokemonscanner.model.*

object ScannerReducer {
    fun reduce(state: ScannerSnapshot, event: ScannerEvent): ScannerSnapshot = when (event) {
        is ScannerEvent.Start -> ScannerSnapshot(
            phase = ScanPhase.REQUESTING_PERMISSION,
            mode = event.mode,
            message = "Waiting for screen-capture permission",
        )
        ScannerEvent.PermissionGranted -> state.copy(
            phase = ScanPhase.WAITING_FOR_SCREEN,
            message = "Waiting for a Pokémon detail screen",
        )
        ScannerEvent.StableFrameFound -> state.copy(
            phase = ScanPhase.DETECTING_SCREEN_TYPE,
            message = "Stable screen found",
        )
        is ScannerEvent.ScreenDetected -> when (event.type) {
            ScreenType.DETAIL -> state.copy(
                phase = ScanPhase.READING_POKEMON_DETAILS,
                message = "Reading Pokémon details",
            )
            ScreenType.APPRAISAL -> state.copy(
                phase = ScanPhase.READING_APPRAISAL,
                message = "Reading appraisal",
            )
            ScreenType.UNKNOWN -> state.copy(
                phase = ScanPhase.WAITING_FOR_SCREEN,
                message = "Expected screen not found",
            )
        }
        is ScannerEvent.DetailsRead -> state.copy(
            phase = if (state.mode == ScanMode.AUTOMATIC) ScanPhase.OPENING_APPRAISAL else ScanPhase.VALIDATING_RESULT,
            current = event.scan,
            message = if (state.mode == ScanMode.AUTOMATIC) "Opening appraisal" else "Ready to validate",
        )
        ScannerEvent.AppraisalGestureSent -> state.copy(
            phase = ScanPhase.WAITING_FOR_APPRAISAL,
            message = "Waiting for appraisal overlay",
        )
        is ScannerEvent.AppraisalRead -> state.copy(
            phase = ScanPhase.VALIDATING_RESULT,
            current = state.current?.copy(ivs = event.ivs),
            message = "Validating detected values",
        )
        is ScannerEvent.Saved -> state.copy(
            phase = if (state.mode == ScanMode.AUTOMATIC) ScanPhase.MOVING_TO_NEXT_POKEMON else ScanPhase.FINISHED,
            scanCount = state.scanCount + 1,
            current = event.scan,
            message = if (state.mode == ScanMode.AUTOMATIC) "Moving to next Pokémon" else "Saved",
        )
        ScannerEvent.AdvanceGestureSent -> state.copy(
            phase = ScanPhase.WAITING_FOR_STABLE_FRAME,
            message = "Waiting for the next Pokémon",
        )
        ScannerEvent.Pause -> state.copy(phase = ScanPhase.PAUSED, message = "Paused")
        ScannerEvent.Resume -> state.copy(phase = ScanPhase.WAITING_FOR_STABLE_FRAME, message = "Resuming")
        is ScannerEvent.Stop -> state.copy(
            phase = ScanPhase.FINISHED,
            stopReason = event.reason,
            message = "Stopped: ${event.reason.name.lowercase().replace('_', ' ')}",
        )
        is ScannerEvent.Fail -> state.copy(
            phase = ScanPhase.ERROR,
            failureCount = state.failureCount + 1,
            errorCode = event.code,
            message = event.message,
        )
    }
}
