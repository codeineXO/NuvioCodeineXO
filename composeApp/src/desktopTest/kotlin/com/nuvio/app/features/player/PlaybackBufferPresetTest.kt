package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackBufferPresetTest {
    @Test
    fun presetsHaveStableKeysIdsAndExactLimits() {
        assertEquals(listOf("low", "medium", "large"), PlaybackBufferPreset.entries.map { it.storageKey })
        assertEquals(listOf(0, 1, 2), PlaybackBufferPreset.entries.map { it.nativeId })
        assertEquals(listOf(256, 512, 1024), PlaybackBufferPreset.entries.map { it.forwardMiB })
        assertEquals(listOf(64, 128, 256), PlaybackBufferPreset.entries.map { it.backwardMiB })
        assertEquals(listOf(120, 180, 300), PlaybackBufferPreset.entries.map { it.readAheadSeconds })
        PlaybackBufferPreset.entries.forEach { assertEquals(it, PlaybackBufferPreset.fromStorageKey(it.storageKey)) }
        assertEquals(PlaybackBufferPreset.LOW, PlaybackBufferPreset.fromStorageKey("balanced"))
    }

    @Test
    fun absentOrUnknownPreferencesAndFreshStateUseMedium() {
        listOf(null, "", "unknown", "LARGE").forEach {
            assertEquals(PlaybackBufferPreset.MEDIUM, PlaybackBufferPreset.fromStorageKey(it))
        }
        assertEquals(PlaybackBufferPreset.MEDIUM, PlayerSettingsUiState().playbackBufferPreset)
        PlayerSettingsRepository.clearLocalState()
        assertEquals(PlaybackBufferPreset.MEDIUM, PlayerSettingsRepository.uiState.value.playbackBufferPreset)
    }
}
