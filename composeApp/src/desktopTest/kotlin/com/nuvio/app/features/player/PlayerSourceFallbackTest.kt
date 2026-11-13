package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerSourceFallbackTest {
    @Test
    fun manualModeRetriesTheFirstUntriedPlayableSourceWhenEnabled() {
        val settings = PlayerSettingsUiState(
            streamAutoPlayMode = StreamAutoPlayMode.MANUAL,
            streamAutoPlayFallbackOnFailure = true,
        )
        val failed = StreamItem(url = "https://example.com/failed.mp4", addonName = "Test", addonId = "test")
        val replacement = StreamItem(url = "https://example.com/replacement.mp4", addonName = "Test", addonId = "test")

        assertTrue(settings.streamAutoPlayFallbackActive)
        assertFalse(settings.copy(streamAutoPlayFallbackOnFailure = false).streamAutoPlayFallbackActive)
        assertFalse(settings.copy(externalPlayerEnabled = true).streamAutoPlayFallbackActive)
        assertEquals(
            replacement,
            nextSourceFallbackCandidate(
                streams = listOf(failed, replacement),
                settings = settings,
                triedKeys = setOf(requireNotNull(failed.playerSourceIdentityKey())),
                failedAddonId = null,
                failedInfoHash = null,
                installedAddonNames = emptySet(),
                debridEnabled = false,
                activeResolverProviderId = null,
            ),
        )
    }
}
