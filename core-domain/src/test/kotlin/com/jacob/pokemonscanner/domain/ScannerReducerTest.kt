package com.jacob.pokemonscanner.domain

import com.jacob.pokemonscanner.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerReducerTest {
    @Test fun automaticScanMovesFromDetailsToAppraisal() {
        var state = ScannerReducer.reduce(ScannerSnapshot(), ScannerEvent.Start(ScanMode.AUTOMATIC))
        state = ScannerReducer.reduce(state, ScannerEvent.PermissionGranted)
        state = ScannerReducer.reduce(state, ScannerEvent.StableFrameFound)
        state = ScannerReducer.reduce(state, ScannerEvent.ScreenDetected(ScreenType.DETAIL))
        state = ScannerReducer.reduce(state, ScannerEvent.DetailsRead(PokemonScan()))
        assertEquals(ScanPhase.OPENING_APPRAISAL, state.phase)
    }
}
