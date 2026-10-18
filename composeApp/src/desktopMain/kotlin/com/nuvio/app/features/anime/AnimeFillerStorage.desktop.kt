package com.nuvio.app.features.anime

import com.nuvio.app.core.storage.DesktopStorage

internal actual object AnimeFillerStorage {
    private const val ENABLED_KEY = "anime_filler_enabled"
    private const val HIDE_FILLERS_KEY = "anime_hide_fillers"
    private val settingsStore = DesktopStorage.store("nuvio_anime_filler_settings")
    private val cacheStore = DesktopStorage.store("nuvio_anime_filler_cache")

    actual fun loadEnabled(): Boolean? =
        if (settingsStore.contains(ENABLED_KEY)) settingsStore.getBoolean(ENABLED_KEY) else null

    actual fun saveEnabled(enabled: Boolean) {
        settingsStore.putBoolean(ENABLED_KEY, enabled)
    }

    actual fun loadHideFillers(): Boolean? =
        if (settingsStore.contains(HIDE_FILLERS_KEY)) settingsStore.getBoolean(HIDE_FILLERS_KEY) else null

    actual fun saveHideFillers(hide: Boolean) {
        settingsStore.putBoolean(HIDE_FILLERS_KEY, hide)
    }

    actual fun getCachedFillers(key: String): String? =
        if (cacheStore.contains(key)) cacheStore.getString(key) else null

    actual fun saveCachedFillers(key: String, payload: String) {
        cacheStore.putString(key, payload)
    }
}
