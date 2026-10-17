package com.nuvio.app.features.player

import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackBufferCalculationTest {

    private fun forkCacheAheadSeconds(
        rawPosition: Double,
        initialStart: Double,
        cacheDuration: Double,
        cacheTime: Double,
    ): Double {
        val effectivePosition = if (initialStart > 0.0 && rawPosition + 5.0 < initialStart) {
            initialStart
        } else {
            max(rawPosition, 0.0)
        }

        if (cacheDuration.isFinite() && cacheDuration > 0.0) {
            return cacheDuration
        }

        if (cacheTime.isFinite() && cacheTime > 0.0) {
            if (cacheTime >= effectivePosition) {
                return cacheTime - effectivePosition
            }
            return 0.0
        }

        return 0.0
    }

    private fun upstreamCacheAheadSeconds(
        rawPosition: Double,
        initialStart: Double,
        cacheDuration: Double,
        cacheTime: Double,
    ): Double {
        val raw = max(rawPosition, 0.0)
        val effectivePosition = if (initialStart > 0.0 && raw + 5.0 < initialStart) {
            initialStart
        } else {
            raw
        }

        if (cacheTime.isFinite() && cacheTime > 0.0) {
            if (cacheTime >= effectivePosition - 5.0) {
                return max(cacheTime - effectivePosition, 0.0)
            }
            return max(cacheTime - raw, 0.0)
        }

        if (cacheDuration.isFinite() && cacheDuration > 0.0) {
            return cacheDuration
        }

        return 0.0
    }

    @Test
    fun coldHttpPlaybackAgreesWhenBothReportConsistentMetrics() {
        val rawPosition = 0.0
        val initialStart = 0.0
        val cacheDuration = 15.0
        val cacheTime = 15.0

        val fork = forkCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)
        val upstream = upstreamCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)

        assertEquals(15.0, fork)
        assertEquals(15.0, upstream)
    }

    @Test
    fun midFileResumeProtectsAgainstByte0Readahead() {
        val rawPosition = 0.2 // Initial container probe at byte 0 before seek lands
        val initialStart = 600.0 // Resume target at 10 minutes
        val cacheDuration = 10.0 // Demuxer duration at resume target
        val cacheTime = 1.5 // Leftover cache-time from byte 0 probing

        val fork = forkCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)
        val upstream = upstreamCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)

        // Fork prioritizes cacheDuration (10.0s), which correctly measures buffer at resumed position
        assertEquals(10.0, fork)
        // Upstream uses cacheTime (1.5s - 0.2s = 1.3s), falsely underreporting the buffer
        assertEquals(1.3, upstream, 0.001)
    }

    @Test
    fun repeatedShortSeeksPreserveDemuxerDurationAccuracy() {
        val initialStart = 0.0
        val rawPosition = 120.0
        val cacheDuration = 18.5
        val cacheTime = 138.5

        val fork = forkCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)
        val upstream = upstreamCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)

        assertEquals(18.5, fork)
        assertEquals(18.5, upstream)
    }

    @Test
    fun rebufferingReportsZeroWhenDurationIsZero() {
        val rawPosition = 50.0
        val initialStart = 0.0
        val cacheDuration = 0.0
        val cacheTime = 50.0 // At current playhead

        val fork = forkCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)
        val upstream = upstreamCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)

        assertEquals(0.0, fork)
        assertEquals(0.0, upstream)
    }

    @Test
    fun fallbackToCacheTimeWhenCacheDurationUnavailable() {
        val rawPosition = 40.0
        val initialStart = 0.0
        val cacheDuration = 0.0
        val cacheTime = 52.0

        val fork = forkCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)
        val upstream = upstreamCacheAheadSeconds(rawPosition, initialStart, cacheDuration, cacheTime)

        assertEquals(12.0, fork)
        assertEquals(12.0, upstream)
    }
}
