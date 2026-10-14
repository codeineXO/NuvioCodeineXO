package com.nuvio.app.features.anime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AnimeFillerSettingsRepository {
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _enabled.value = AnimeFillerStorage.loadEnabled() ?: true
    }

    fun snapshot(): Boolean {
        ensureLoaded()
        return _enabled.value
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_enabled.value == enabled) return
        _enabled.value = enabled
        AnimeFillerStorage.saveEnabled(enabled)
    }
}
