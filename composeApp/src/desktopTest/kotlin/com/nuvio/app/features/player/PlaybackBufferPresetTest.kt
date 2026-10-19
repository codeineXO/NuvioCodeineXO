package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackBufferPresetTest {
    @Test
    fun presetsHaveStableKeysIdsAndExactLimits() {
        assertEquals(listOf("balanced", "medium", "large"), PlaybackBufferPreset.entries.map { it.storageKey })
        assertEquals(listOf(0, 1, 2), PlaybackBufferPreset.entries.map { it.nativeId })
        assertEquals(listOf(256, 512, 1024), PlaybackBufferPreset.entries.map { it.forwardMiB })
        assertEquals(listOf(64, 128, 256), PlaybackBufferPreset.entries.map { it.backwardMiB })
        assertEquals(listOf(120, 180, 300), PlaybackBufferPreset.entries.map { it.readAheadSeconds })
        PlaybackBufferPreset.entries.forEach { assertEquals(it, PlaybackBufferPreset.fromStorageKey(it.storageKey)) }
    }

    @Test
    fun absentOrUnknownPreferencesAndFreshStateUseBalanced() {
        listOf(null, "", "unknown", "LARGE").forEach {
            assertEquals(PlaybackBufferPreset.BALANCED, PlaybackBufferPreset.fromStorageKey(it))
        }
        assertEquals(PlaybackBufferPreset.BALANCED, PlayerSettingsUiState().playbackBufferPreset)
        PlayerSettingsRepository.clearLocalState()
        assertEquals(PlaybackBufferPreset.BALANCED, PlayerSettingsRepository.uiState.value.playbackBufferPreset)
    }
}
