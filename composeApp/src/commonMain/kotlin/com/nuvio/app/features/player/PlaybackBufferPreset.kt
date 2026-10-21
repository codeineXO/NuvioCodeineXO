package com.nuvio.app.features.player

enum class PlaybackBufferPreset(
    val storageKey: String,
    val nativeId: Int,
    val forwardMiB: Int,
    val backwardMiB: Int,
    val readAheadSeconds: Int,
) {
    LOW("low", 0, 256, 64, 120),
    MEDIUM("medium", 1, 512, 128, 180),
    LARGE("large", 2, 1024, 256, 300);

    companion object {
        @Deprecated("Renamed to LOW", ReplaceWith("PlaybackBufferPreset.LOW"))
        val BALANCED: PlaybackBufferPreset get() = LOW

        fun fromStorageKey(key: String?): PlaybackBufferPreset =
            when (key) {
                "balanced" -> LOW
                else -> entries.firstOrNull { it.storageKey == key } ?: MEDIUM
            }
    }
}

/** Optional storage capability for the desktop-native buffer setting. */
internal interface PlaybackBufferPresetStorage {
    fun loadPlaybackBufferPreset(): String?
    fun savePlaybackBufferPreset(preset: String)
}

internal fun PlayerSettingsStorage.loadPlaybackBufferPreset(): String? =
    (this as? PlaybackBufferPresetStorage)?.loadPlaybackBufferPreset()

internal fun PlayerSettingsStorage.savePlaybackBufferPreset(preset: String) {
    (this as? PlaybackBufferPresetStorage)?.savePlaybackBufferPreset(preset)
}
