package com.jacob.pokemonscanner.domain

import com.jacob.pokemonscanner.model.GiftAssistantSettings
import com.jacob.pokemonscanner.model.ScreenObservation
import com.jacob.pokemonscanner.model.WorkflowInstruction

fun interface ScreenRecognitionPort<Frame> {
    suspend fun recognize(frame: Frame): ScreenObservation
}

fun interface GestureExecutionPort {
    suspend fun execute(instruction: WorkflowInstruction, settings: GiftAssistantSettings): Boolean
}
