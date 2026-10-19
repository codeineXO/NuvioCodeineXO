package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlaybackBufferPreset
import kotlin.test.Test
import kotlin.test.assertEquals

class NativePlayerControllerBufferTest {
    @Test
    fun updatesExistingPlayerImmediatelyAndSkipsUnchangedPreset() {
        val updates = mutableListOf<Pair<Long, PlaybackBufferPreset>>()
        val controller = controller(updates)
        controller.withHandle(101L)
        controller.setPlaybackBufferPreset(PlaybackBufferPreset.MEDIUM)
        controller.setPlaybackBufferPreset(PlaybackBufferPreset.MEDIUM)
        controller.setPlaybackBufferPreset(PlaybackBufferPreset.LARGE)
        assertEquals(listOf(101L to PlaybackBufferPreset.MEDIUM, 101L to PlaybackBufferPreset.LARGE), updates)
    }

    @Test
    fun selectionBeforePlayerCreationIsRetainedAndAppliesToNewHandles() {
        val updates = mutableListOf<Pair<Long, PlaybackBufferPreset>>()
        val controller = controller(updates)
        controller.setPlaybackBufferPreset(PlaybackBufferPreset.LARGE)
        assertEquals(emptyList(), updates)
        controller.withHandle(101L)
        controller.configureBuffer(101L)
        controller.withHandle(102L)
        controller.configureBuffer(102L)
        assertEquals(listOf(101L to PlaybackBufferPreset.LARGE, 102L to PlaybackBufferPreset.LARGE), updates)
    }

    @Test
    fun failedUpdateCanBeRetriedAndStaleHandlesAreIgnored() {
        var attempts = 0
        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeSetPlaybackBufferPreset = { _, _ ->
                attempts++
                if (attempts == 1) error("update failed")
            },
        )
        controller.withHandle(101L)
        controller.setPlaybackBufferPreset(PlaybackBufferPreset.MEDIUM)
        controller.setPlaybackBufferPreset(PlaybackBufferPreset.MEDIUM)
        controller.configureBuffer(99L)
        assertEquals(2, attempts)
    }

    private fun controller(updates: MutableList<Pair<Long, PlaybackBufferPreset>>) = NativePlayerController(
        host = NativePlayerHost(),
        nativeSetPlaybackBufferPreset = { handle, preset -> updates += handle to preset },
    )

    private fun NativePlayerController.withHandle(handle: Long) {
        javaClass.getDeclaredField("handle").apply { isAccessible = true }.setLong(this, handle)
    }

    private fun NativePlayerController.configureBuffer(handle: Long) {
        javaClass.getDeclaredMethod("applyPlaybackBufferPreset", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(this, handle)
    }
}
