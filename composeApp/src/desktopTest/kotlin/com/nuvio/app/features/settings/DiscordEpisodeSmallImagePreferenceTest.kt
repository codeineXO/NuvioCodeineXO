package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.DesktopStorage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiscordEpisodeSmallImagePreferenceTest {
    @Test
    fun imageVisibility_survivesReload_withoutChangingOtherSettings() {
        val directory = Files.createTempDirectory("discord-image-preference")
        val file = directory.resolve("settings.properties")
        try {
            val store = DesktopStorage.Store(file)
            val preference = DiscordEpisodeSmallImagePreference(store)
            assertNull(preference.load())
            store.putBoolean("full_poster_enabled", true)
            for (enabled in listOf(false, true)) {
                preference.save(enabled)
                store.flushSync()
                val reloaded = DesktopStorage.Store(file)
                assertEquals(enabled, DiscordEpisodeSmallImagePreference(reloaded).load())
                assertEquals(true, reloaded.getBoolean("full_poster_enabled"))
            }
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
