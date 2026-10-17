package com.nuvio.app.features.player

import androidx.compose.ui.Modifier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class PlayerShortcutRoutingTest {

    private fun testPlayerScreenArgs() = PlayerScreenArgs(
        profileId = 1,
        title = "Title",
        sourceUrl = "https://example.com/video.mp4",
        sourceAudioUrl = null,
        sourceHeaders = emptyMap(),
        sourceResponseHeaders = emptyMap(),
        streamType = null,
        providerName = "Provider",
        streamTitle = "Source",
        streamSubtitle = null,
        initialBingeGroup = null,
        pauseDescription = null,
        onBack = {},
        onOpenInExternalPlayer = null,
        onOpenExternalUrl = null,
        modifier = Modifier,
        logo = null,
        poster = null,
        background = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeTitle = null,
        episodeThumbnail = null,
        contentType = "movie",
        videoId = "tt1234567",
        parentMetaId = "tt1234567",
        parentMetaType = "movie",
        providerAddonId = null,
        torrentInfoHash = null,
        torrentFileIdx = null,
        torrentFilename = null,
        torrentTrackers = emptyList(),
        initialPositionMs = 0L,
        initialProgressFraction = null,
    )

    @Test
    fun unSyncedPartyDelegatesSeekToNativeFallback() = runTest {
        val runtime = PlayerScreenRuntime(testPlayerScreenArgs())
        runtime.scope = this
        runtime.playbackSnapshot = PlayerPlaybackSnapshot(positionMs = 15_000L, durationMs = 120_000L)

        // When not in party, seekFromNativeControls returns false and sets feedback
        val handled = runtime.seekFromNativeControls(3_000L, revealControls = false)
        assertFalse(handled)
        assertNotNull(runtime.gestureFeedback)
        assertEquals(listOf(3L), runtime.gestureFeedback?.messageArgs)
        assertEquals(GestureFeedbackIcon.SeekForward, runtime.gestureFeedback?.icon)

        val handledBack = runtime.seekFromNativeControls(-3_000L, revealControls = false)
        assertFalse(handledBack)
        assertNotNull(runtime.gestureFeedback)
        assertEquals(listOf(3L), runtime.gestureFeedback?.messageArgs)
        assertEquals(GestureFeedbackIcon.SeekBackward, runtime.gestureFeedback?.icon)
    }

    @Test
    fun shortcutActionsUseCorrectDeltas() = runTest {
        val runtime = PlayerScreenRuntime(testPlayerScreenArgs())
        runtime.scope = this
        runtime.playbackSnapshot = PlayerPlaybackSnapshot(positionMs = 30_000L, durationMs = 120_000L)

        // 3-second seeks
        runtime.seekFromNativeControls(3_000L, revealControls = false)
        assertEquals(listOf(3L), runtime.gestureFeedback?.messageArgs)
        assertEquals(GestureFeedbackIcon.SeekForward, runtime.gestureFeedback?.icon)

        runtime.seekFromNativeControls(-3_000L, revealControls = false)
        assertEquals(listOf(3L), runtime.gestureFeedback?.messageArgs)
        assertEquals(GestureFeedbackIcon.SeekBackward, runtime.gestureFeedback?.icon)

        // 10-second seeks
        runtime.seekFromNativeControls(10_000L, revealControls = false)
        assertEquals(listOf(10L), runtime.gestureFeedback?.messageArgs)
        assertEquals(GestureFeedbackIcon.SeekForward, runtime.gestureFeedback?.icon)

        runtime.seekFromNativeControls(-10_000L, revealControls = false)
        assertEquals(listOf(10L), runtime.gestureFeedback?.messageArgs)
        assertEquals(GestureFeedbackIcon.SeekBackward, runtime.gestureFeedback?.icon)

        // 1-second fine seeks
        runtime.seekFromNativeControls(1_000L, revealControls = false)
        assertEquals(listOf(1L), runtime.gestureFeedback?.messageArgs)
        assertEquals(GestureFeedbackIcon.SeekForward, runtime.gestureFeedback?.icon)

        runtime.seekFromNativeControls(-1_000L, revealControls = false)
        assertEquals(listOf(1L), runtime.gestureFeedback?.messageArgs)
        assertEquals(GestureFeedbackIcon.SeekBackward, runtime.gestureFeedback?.icon)
    }
}
