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

    /** Storage is per profile; reload so one profile's filters never show or save under another. */
    fun onProfileChanged() {
        lastTitleId = null
        _settings.value = load()
    }

    private var lastTitleId: String? = null

    /** Clears the search when the stream page opens a different title, so it doesn't follow the user. */
    fun onTitleOpened(titleId: String) {
        if (titleId == lastTitleId) return
        lastTitleId = titleId
        if (_settings.value.query.isNotEmpty()) _settings.update { it.copy(query = "") }
    }

    /** The filter autoplay should apply, or null when autoplay ignores filters. Never includes the search. */
    fun autoPlayFilter(): StreamFilterSettings? =
        _settings.value.takeIf { it.autoPlayRespectsFilters }?.copy(query = "")

    private fun load(): StreamFilterSettings =
        StreamFilterSettingsStorage.loadPayload()
            ?.let { payload -> runCatching { json.decodeFromString<StreamFilterSettings>(payload) }.getOrNull() }
            ?: StreamFilterSettings()

    private fun save(settings: StreamFilterSettings) {
        runCatching { StreamFilterSettingsStorage.savePayload(json.encodeToString(settings)) }
    }
}
