package com.jacob.pokemonscanner

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.jacob.pokemonscanner.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.giftAssistantDataStore by preferencesDataStore("gift_assistant")

class GiftPreferencesRepository(private val context: Context) {
    val settings: Flow<GiftAssistantSettings> = context.giftAssistantDataStore.data.map { values ->
        GiftAssistantSettings(
            sortMode = values[SORT_MODE]?.let { runCatching { GiftSortMode.valueOf(it) }.getOrNull() }
                ?: GiftSortMode.GIFT_STATUS,
            sortDirection = values[SORT_DIRECTION]?.let { runCatching { GiftSortDirection.valueOf(it) }.getOrNull() }
                ?: GiftSortDirection.DESCENDING,
            cleanupItems = values[CLEANUP_ITEMS]
                ?.mapNotNullTo(linkedSetOf()) { runCatching { CleanupItem.valueOf(it) }.getOrNull() }
                ?: CleanupItem.defaultSelection,
            interactionDelayMs = values[INTERACTION_DELAY] ?: 900L,
            screenTimeoutMs = values[SCREEN_TIMEOUT] ?: 12_000L,
            retryLimit = values[RETRY_LIMIT] ?: 3,
            minimumRecognitionConfidence = values[MIN_CONFIDENCE] ?: .82f,
            dryRun = values[DRY_RUN] ?: true,
            reducedMotion = values[REDUCED_MOTION] ?: false,
        )
    }

    val onboardingComplete: Flow<Boolean> = context.giftAssistantDataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    val savedLog: Flow<List<SessionLogEntry>> = context.giftAssistantDataStore.data.map { values ->
        values[SESSION_LOG].orEmpty().lineSequence().mapNotNull(::decodeLog).toList()
    }

    suspend fun updateSettings(value: GiftAssistantSettings) {
        context.giftAssistantDataStore.edit { preferences ->
            preferences[SORT_MODE] = value.sortMode.name
            preferences[SORT_DIRECTION] = value.sortDirection.name
            preferences[CLEANUP_ITEMS] = value.cleanupItems.mapTo(linkedSetOf()) { it.name }
            preferences[INTERACTION_DELAY] = value.interactionDelayMs
            preferences[SCREEN_TIMEOUT] = value.screenTimeoutMs
            preferences[RETRY_LIMIT] = value.retryLimit
            preferences[MIN_CONFIDENCE] = value.minimumRecognitionConfidence
            preferences[DRY_RUN] = value.dryRun
            preferences[REDUCED_MOTION] = value.reducedMotion
        }
    }

    suspend fun completeOnboarding() {
        context.giftAssistantDataStore.edit { it[ONBOARDING_COMPLETE] = true }
    }

    suspend fun appendLog(entry: SessionLogEntry) {
        context.giftAssistantDataStore.edit { preferences ->
            val current = preferences[SESSION_LOG].orEmpty().lineSequence().filter(String::isNotBlank).toList()
            preferences[SESSION_LOG] = (current + encodeLog(entry)).takeLast(200).joinToString("\n")
        }
    }

    suspend fun clearLog() {
        context.giftAssistantDataStore.edit { it.remove(SESSION_LOG) }
    }

    private fun encodeLog(entry: SessionLogEntry): String = listOf(
        entry.timestamp.toString(),
        entry.state.name,
        entry.severity.name,
        entry.message.replace("|", "/").replace("\n", " ").take(240),
    ).joinToString("|")

    private fun decodeLog(line: String): SessionLogEntry? {
        val parts = line.split('|', limit = 4)
        if (parts.size != 4) return null
        return runCatching {
            SessionLogEntry(
                Instant.parse(parts[0]),
                GiftAutomationState.valueOf(parts[1]),
                parts[3],
                LogSeverity.valueOf(parts[2]),
            )
        }.getOrNull()
    }

    private companion object {
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val SORT_DIRECTION = stringPreferencesKey("sort_direction")
        val CLEANUP_ITEMS = stringSetPreferencesKey("cleanup_items")
        val INTERACTION_DELAY = longPreferencesKey("interaction_delay")
        val SCREEN_TIMEOUT = longPreferencesKey("screen_timeout")
        val RETRY_LIMIT = intPreferencesKey("retry_limit")
        val MIN_CONFIDENCE = floatPreferencesKey("minimum_confidence")
        val DRY_RUN = booleanPreferencesKey("dry_run")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val SESSION_LOG = stringPreferencesKey("session_log")
    }
}
