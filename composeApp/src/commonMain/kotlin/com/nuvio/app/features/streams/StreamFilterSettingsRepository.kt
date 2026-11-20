package com.nuvio.app.features.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shared stream filter and sort. The stream page and Playback settings edit it; the player's
 * sources panel and (optionally) autoplay only read it. Saved on this device, never synced.
 */
object StreamFilterSettingsRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<StreamFilterSettings> = _settings.asStateFlow()

    fun update(transform: (StreamFilterSettings) -> StreamFilterSettings) {
        val before = _settings.value
        _settings.update(transform)
        val after = _settings.value
        if (after.copy(query = "") != before.copy(query = "")) save(after)
    }

    fun reset() = update { it.reset() }

    /** The filter autoplay should apply, or null when autoplay ignores filters. */
    fun autoPlayFilter(): StreamFilterSettings? =
        _settings.value.takeIf { it.autoPlayRespectsFilters }

    private fun load(): StreamFilterSettings =
        StreamFilterSettingsStorage.loadPayload()
            ?.let { payload -> runCatching { json.decodeFromString<StreamFilterSettings>(payload) }.getOrNull() }
            ?: StreamFilterSettings()

    private fun save(settings: StreamFilterSettings) {
        runCatching { StreamFilterSettingsStorage.savePayload(json.encodeToString(settings)) }
    }
}
