package com.jacob.pokemonscanner.model

sealed interface ScannerEvent {
    data class Start(val mode: ScanMode) : ScannerEvent
    data object PermissionGranted : ScannerEvent
    data object StableFrameFound : ScannerEvent
    data class ScreenDetected(val type: ScreenType) : ScannerEvent
    data class DetailsRead(val scan: PokemonScan) : ScannerEvent
    data object AppraisalGestureSent : ScannerEvent
    data class AppraisalRead(val ivs: Detection<IvSet>) : ScannerEvent
    data class Saved(val scan: PokemonScan) : ScannerEvent
    data object AdvanceGestureSent : ScannerEvent
    data object Pause : ScannerEvent
    data object Resume : ScannerEvent
    data class Stop(val reason: StopReason) : ScannerEvent
    data class Fail(val code: ScannerErrorCode, val message: String) : ScannerEvent
}
