package com.jacob.pokemonscanner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacob.pokemonscanner.automation.GiftAutomationCoordinator
import com.jacob.pokemonscanner.image.GiftScreenAnalyzer
import com.jacob.pokemonscanner.model.GiftAssistantSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    private val preferences = GiftPreferencesRepository(context)
    private val coordinator = GiftAutomationCoordinator(GiftScreenAnalyzer())

    val workflow = coordinator.state
    val liveLog = coordinator.log
    val settings = preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        GiftAssistantSettings(),
    )
    val onboardingComplete = preferences.onboardingComplete.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false,
    )
    val savedLog = preferences.savedLog.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    init {
        AutomationControlBridge.bind(::pause, ::stop)
    }

    fun start() = coordinator.start(viewModelScope, settings.value, preferences::appendLog)
    fun pause() = coordinator.pause()
    fun resume() = coordinator.resume()
    fun stop() = coordinator.stop()
    fun captureLost() = coordinator.captureLost()

    fun updateSettings(transform: (GiftAssistantSettings) -> GiftAssistantSettings) {
        viewModelScope.launch { preferences.updateSettings(transform(settings.value)) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { preferences.completeOnboarding() }
    }

    fun clearLog() {
        viewModelScope.launch { preferences.clearLog() }
    }

    override fun onCleared() {
        coordinator.stop()
        AutomationControlBridge.unbind()
        super.onCleared()
    }
}
