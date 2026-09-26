package com.nuvio.app.features.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object DiscordRichPresenceRepository {
    val isSupported: Boolean
        get() = DiscordRichPresencePlatform.isSupported

    private val _enabled = MutableStateFlow(isSupported)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _activityMode = MutableStateFlow(DiscordActivityMode.ALL)
    val activityMode: StateFlow<DiscordActivityMode> = _activityMode.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _enabled.value = DiscordRichPresenceStorage.loadEnabled() ?: isSupported
        _activityMode.value = DiscordRichPresenceStorage.loadActivityMode() ?: DiscordActivityMode.ALL
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_enabled.value == enabled) return
        _enabled.value = enabled
        DiscordRichPresenceStorage.saveEnabled(enabled)
    }

    fun setActivityMode(mode: DiscordActivityMode) {
        ensureLoaded()
        if (_activityMode.value == mode) return
        _activityMode.value = mode
        DiscordRichPresenceStorage.saveActivityMode(mode)
    }
}
