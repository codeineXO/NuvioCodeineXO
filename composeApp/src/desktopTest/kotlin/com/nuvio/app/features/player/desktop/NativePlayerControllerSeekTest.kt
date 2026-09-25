package com.nuvio.app.features.player.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val failNativeCreate: NativePlayerCreate = { _, _, _, _, _, _, _, _, _ ->
    error("native create must not run in seek unit tests")
}

class NativePlayerControllerSeekTest {

    @Test
    fun seekToWithLargeForwardOffsetUsesSeekByForFastKeyframeSeeking() {
        var soughtTo: Pair<Long, Long>? = null
        var soughtBy: Pair<Long, Long>? = null
        val reportedPositionMs = 15_000L

        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = failNativeCreate,
            nativeDispose = {},
            nativeSeekTo = { handle, pos -> soughtTo = handle to pos },
            nativeSeekBy = { handle, offset -> soughtBy = handle to offset },
            nativePositionMs = { reportedPositionMs },
        )
        controller.setNativeHandleForTest(101L)

        // Intro skip: from 15s to 90s (+75s delta > 3s threshold)
        controller.seekTo(90_000L)

        assertEquals(101L to 75_000L, soughtBy)
        assertEquals(null, soughtTo)
    }

    @Test
    fun seekToWithLargeBackwardOffsetUsesSeekByForFastKeyframeSeeking() {
        var soughtTo: Pair<Long, Long>? = null
        var soughtBy: Pair<Long, Long>? = null
        val reportedPositionMs = 90_000L

        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = failNativeCreate,
            nativeDispose = {},
            nativeSeekTo = { handle, pos -> soughtTo = handle to pos },
            nativeSeekBy = { handle, offset -> soughtBy = handle to offset },
            nativePositionMs = { reportedPositionMs },
        )
        controller.setNativeHandleForTest(101L)

        // Scrubber seek backward: from 90s to 20s (-70s delta > 3s threshold)
        controller.seekTo(20_000L)

        assertEquals(101L to -70_000L, soughtBy)
        assertEquals(null, soughtTo)
    }

    @Test
    fun seekToWithSmallOffsetUsesExactSeekTo() {
        var soughtTo: Pair<Long, Long>? = null
        var soughtBy: Pair<Long, Long>? = null
        val reportedPositionMs = 10_000L

        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = failNativeCreate,
            nativeDispose = {},
            nativeSeekTo = { handle, pos -> soughtTo = handle to pos },
            nativeSeekBy = { handle, offset -> soughtBy = handle to offset },
            nativePositionMs = { reportedPositionMs },
        )
        controller.setNativeHandleForTest(101L)

        // Fine seek: from 10s to 12s (+2s delta <= 3s threshold)
        controller.seekTo(12_000L)

        assertEquals(101L to 12_000L, soughtTo)
        assertEquals(null, soughtBy)
    }

    @Test
    fun seekToZeroUsesExactSeekTo() {
        var soughtTo: Pair<Long, Long>? = null
        var soughtBy: Pair<Long, Long>? = null
        val reportedPositionMs = 60_000L

        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = failNativeCreate,
            nativeDispose = {},
            nativeSeekTo = { handle, pos -> soughtTo = handle to pos },
            nativeSeekBy = { handle, offset -> soughtBy = handle to offset },
            nativePositionMs = { reportedPositionMs },
        )
        controller.setNativeHandleForTest(101L)

        controller.seekTo(0L)

        assertEquals(101L to 0L, soughtTo)
        assertEquals(null, soughtBy)
    }

    @Test
    fun trySeekToReturnsFalseWhenHandleIsZero() {
        var soughtTo: Pair<Long, Long>? = null
        var soughtBy: Pair<Long, Long>? = null

        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = failNativeCreate,
            nativeDispose = {},
            nativeSeekTo = { handle, pos -> soughtTo = handle to pos },
            nativeSeekBy = { handle, offset -> soughtBy = handle to offset },
            nativePositionMs = { 0L },
        )

        val result = controller.trySeekTo(45_000L)

        assertFalse(result)
        assertEquals(null, soughtTo)
        assertEquals(null, soughtBy)
    }

    @Test
    fun trySeekToPerformsKeyframeSeekWhenHandleIsActive() {
        var soughtTo: Pair<Long, Long>? = null
        var soughtBy: Pair<Long, Long>? = null
        val reportedPositionMs = 5_000L

        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = failNativeCreate,
            nativeDispose = {},
            nativeSeekTo = { handle, pos -> soughtTo = handle to pos },
            nativeSeekBy = { handle, offset -> soughtBy = handle to offset },
            nativePositionMs = { reportedPositionMs },
        )
        controller.setNativeHandleForTest(101L)

        val result = controller.trySeekTo(85_000L)

        assertTrue(result)
        assertEquals(101L to 80_000L, soughtBy)
        assertEquals(null, soughtTo)
    }

    @Test
    fun seekByDelegatesDirectlyToNativeSeekBy() {
        var soughtBy: Pair<Long, Long>? = null

        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = failNativeCreate,
            nativeDispose = {},
            nativeSeekTo = { _, _ -> },
            nativeSeekBy = { handle, offset -> soughtBy = handle to offset },
            nativePositionMs = { 0L },
        )
        controller.setNativeHandleForTest(101L)

        controller.seekBy(-10_000L)

        assertEquals(101L to -10_000L, soughtBy)
    }
}

private fun NativePlayerController.setNativeHandleForTest(value: Long) {
    javaClass.getDeclaredField("handle").also { field ->
        field.isAccessible = true
        field.setLong(this, value)
    }
}
