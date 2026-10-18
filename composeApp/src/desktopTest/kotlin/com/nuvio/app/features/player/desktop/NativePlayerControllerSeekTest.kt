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
            nativeDurationMs = { 0L },
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
            nativeDurationMs = { 0L },
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
            nativeDurationMs = { 0L },
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
            nativeDurationMs = { 0L },
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
            nativeDurationMs = { 0L },
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
            nativeDurationMs = { 0L },
        )
        controller.setNativeHandleForTest(101L)

        controller.seekBy(-10_000L)

        assertEquals(101L to -10_000L, soughtBy)
    }

    @Test
    fun seekToNearEndClampsToSafeBoundaryAndUsesExactSeekTo() {
        var soughtTo: Pair<Long, Long>? = null
        var soughtBy: Pair<Long, Long>? = null
        val reportedPositionMs = 20_000L
        val reportedDurationMs = 120_000L

        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = failNativeCreate,
            nativeDispose = {},
            nativeSeekTo = { handle, pos -> soughtTo = handle to pos },
            nativeSeekBy = { handle, offset -> soughtBy = handle to offset },
            nativePositionMs = { reportedPositionMs },
            nativeDurationMs = { reportedDurationMs },
        )
        controller.setNativeHandleForTest(101L)

        // Seek all the way to 120_000L (end of duration)
        controller.seekTo(120_000L)

        // Clamped to duration - 1500L = 118_500L, and since it is near end, uses exact seekTo
        assertEquals(101L to 118_500L, soughtTo)
        assertEquals(null, soughtBy)
    }

    @Test
    fun seekToNearEndDoesNotUseSeekByEvenWithLargeForwardDelta() {
        var soughtTo: Pair<Long, Long>? = null
        var soughtBy: Pair<Long, Long>? = null
        val reportedPositionMs = 10_000L
        val reportedDurationMs = 100_000L

        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = failNativeCreate,
            nativeDispose = {},
            nativeSeekTo = { handle, pos -> soughtTo = handle to pos },
            nativeSeekBy = { handle, offset -> soughtBy = handle to offset },
            nativePositionMs = { reportedPositionMs },
            nativeDurationMs = { reportedDurationMs },
        )
        controller.setNativeHandleForTest(101L)

        // Seeking to 95s (within 45s of 100s duration, delta is +85s > 3s)
        controller.seekTo(95_000L)

        assertEquals(101L to 95_000L, soughtTo)
        assertEquals(null, soughtBy)
    }
}

private fun NativePlayerController.setNativeHandleForTest(value: Long) {
    javaClass.getDeclaredField("handle").also { field ->
        field.isAccessible = true
        field.setLong(this, value)
    }
}
