package com.nuvio.app.features.anime

internal actual object AnimeFillerStorage {
    private var enabled: Boolean? = null
    private val cache = mutableMapOf<String, String>()

    actual fun loadEnabled(): Boolean? = enabled

    actual fun saveEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    actual fun getCachedFillers(key: String): String? = cache[key]

    actual fun saveCachedFillers(key: String, payload: String) {
        cache[key] = payload
    }
}
