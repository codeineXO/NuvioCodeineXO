package com.nuvio.app.features.player

import com.nuvio.app.features.player.httpInitialLoadingProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpLoadingProgressTest {
    @Test
    fun noBufferReportsNoProgress() {
        assertEquals(0f, httpInitialLoadingProgress(0L))
        assertEquals(0f, httpInitialLoadingProgress(-5_000L))
    }

    @Test
    fun progressScalesWithBufferedAhead() {
        assertEquals(0.095f, httpInitialLoadingProgress(1_000L), absoluteTolerance = 1e-5f)
        assertEquals(0.475f, httpInitialLoadingProgress(5_000L), absoluteTolerance = 1e-5f)
    }

    @Test
    fun progressStopsShortOfCompleteWhileBuffering() {
        assertEquals(0.95f, httpInitialLoadingProgress(10_000L), absoluteTolerance = 1e-5f)
        assertTrue(httpInitialLoadingProgress(600_000L) < 1f)
        assertEquals(0.95f, httpInitialLoadingProgress(600_000L), absoluteTolerance = 1e-5f)
    }

    @Test
    fun startsBelowTheP2pPlayerStageStart() {
        assertTrue(httpInitialLoadingProgress(1L) < P2pInitialPlayerStageStart)
    }
}
