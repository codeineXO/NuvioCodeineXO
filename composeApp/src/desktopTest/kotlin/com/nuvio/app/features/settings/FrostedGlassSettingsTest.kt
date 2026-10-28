package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrostedGlassSettingsTest {
    @Test
    fun localPreferenceSurvivesReloadAndProfileChanges() {
        val file = Files.createTempFile("frosted-glass-settings", ".properties")
        val store = DesktopStorage.Store(file)
        var profile = 1
        val storage = DesktopFrostedGlassSettingsStorage(store) {
            ProfileScopedKey.of("frosted_glass_enabled", profile)
        }
        try {
            assertNull(storage.loadFrostedGlassEnabled())
            storage.saveFrostedGlassEnabled(false)

            profile = 2
            assertNull(storage.loadFrostedGlassEnabled())
            storage.saveFrostedGlassEnabled(true)
            profile = 1
            assertEquals(false, storage.loadFrostedGlassEnabled())

            store.flushSync()
            val reloadedStore = DesktopStorage.Store(file)
            val reloaded = DesktopFrostedGlassSettingsStorage(reloadedStore) {
                ProfileScopedKey.of("frosted_glass_enabled", profile)
            }
            assertEquals(false, reloaded.loadFrostedGlassEnabled())
            profile = 2
            assertEquals(true, reloaded.loadFrostedGlassEnabled())
            reloadedStore.remove(ProfileScopedKey.of("frosted_glass_enabled", profile))
            assertNull(reloaded.loadFrostedGlassEnabled())
            reloadedStore.flushSync()
        } finally {
            store.flushSync()
            Files.deleteIfExists(file)
        }
    }
}
