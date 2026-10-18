package com.nuvio.app.features.anime

import java.util.concurrent.ConcurrentHashMap

internal actual object AnimeFillerStorage {
    private var enabled: Boolean? = null
    private var hideFillers: Boolean? = null
    private val cache = ConcurrentHashMap<String, String>()

    actual fun loadEnabled(): Boolean? = enabled

    actual fun saveEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    actual fun loadHideFillers(): Boolean? = hideFillers

    actual fun saveHideFillers(hide: Boolean) {
        this.hideFillers = hide
    }

    actual fun getCachedFillers(key: String): String? = cache[key]

    actual fun saveCachedFillers(key: String, payload: String) {
        cache[key] = payload
    }
}
