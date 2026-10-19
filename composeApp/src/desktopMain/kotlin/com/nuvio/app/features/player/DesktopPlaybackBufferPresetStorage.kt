package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal class DesktopPlaybackBufferPresetStorage(
    private val store: DesktopStorage.Store,
    private val scopedKey: () -> String = { ProfileScopedKey.of(KEY) },
) : PlaybackBufferPresetStorage {
    override fun loadPlaybackBufferPreset(): String? = store.getString(scopedKey())
    override fun savePlaybackBufferPreset(preset: String) = store.putString(scopedKey(), preset)

    companion object {
        const val KEY = "playback_buffer_preset"
    }
}
