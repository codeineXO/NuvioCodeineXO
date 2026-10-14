package com.nuvio.app.features.anime

internal expect object AnimeFillerStorage {
    fun loadEnabled(): Boolean?
    fun saveEnabled(enabled: Boolean)
    fun getCachedFillers(key: String): String?
    fun saveCachedFillers(key: String, payload: String)
}
