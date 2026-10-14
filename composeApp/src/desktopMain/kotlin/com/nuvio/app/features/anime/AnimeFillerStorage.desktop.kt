package com.nuvio.app.features.anime

import com.nuvio.app.core.storage.DesktopStorage

internal actual object AnimeFillerStorage {
    private const val ENABLED_KEY = "anime_filler_enabled"
    private val settingsStore = DesktopStorage.store("nuvio_anime_filler_settings")
    private val cacheStore = DesktopStorage.store("nuvio_anime_filler_cache")

    actual fun loadEnabled(): Boolean? =
        if (settingsStore.contains(ENABLED_KEY)) settingsStore.getBoolean(ENABLED_KEY) else null

    actual fun saveEnabled(enabled: Boolean) {
        settingsStore.putBoolean(ENABLED_KEY, enabled)
    }

    actual fun getCachedFillers(key: String): String? =
        if (cacheStore.contains(key)) cacheStore.getString(key) else null

    actual fun saveCachedFillers(key: String, payload: String) {
        cacheStore.putString(key, payload)
    }
}
