package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPlaybackBufferPresetStorageTest {
    @Test
    fun preferencePersistsAndProfilesRemainIndependent() {
        val file = Files.createTempFile("playback-buffer", ".properties")
        var profileId = 1
        val store = DesktopStorage.Store(file)
        val storage = DesktopPlaybackBufferPresetStorage(store) {
            ProfileScopedKey.of(DesktopPlaybackBufferPresetStorage.KEY, profileId)
        }
        try {
            assertNull(storage.loadPlaybackBufferPreset())
            storage.savePlaybackBufferPreset("medium")
            profileId = 2
            assertNull(storage.loadPlaybackBufferPreset())
            storage.savePlaybackBufferPreset("large")
            profileId = 1
            assertEquals("medium", storage.loadPlaybackBufferPreset())
            store.flushSync()
            val reloaded = DesktopPlaybackBufferPresetStorage(DesktopStorage.Store(file)) {
                ProfileScopedKey.of(DesktopPlaybackBufferPresetStorage.KEY, 2)
            }
            assertEquals("large", reloaded.loadPlaybackBufferPreset())
        } finally {
            store.flushSync()
            Files.deleteIfExists(file)
        }
    }
}
