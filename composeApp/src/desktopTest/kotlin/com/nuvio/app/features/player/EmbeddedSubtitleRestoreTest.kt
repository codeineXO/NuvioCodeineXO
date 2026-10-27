package com.nuvio.app.features.player

import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddedSubtitleRestoreTest {
    private val preference = PersistedPlayerTrackPreference(
        subtitleType = PersistedSubtitleSelectionType.INTERNAL,
        subtitleTrackId = "2",
        subtitleLanguage = "en",
        subtitleName = "English (PGS)",
        subtitleIsForced = false,
    )
    private val pgs = SubtitleTrack(0, "2", "English (PGS)", "en")

    @Test
    fun waitsForLoadedPlayerAndTracksBeforeRestoring() {
        val controller = RecordingController()
        val runtime = runtime(controller)
        runtime.playbackSnapshot = PlayerPlaybackSnapshot(isLoading = true)
        runtime.subtitleTracks = listOf(pgs)
        assertFalse(runtime.restorePersistedTrackPreferenceIfNeeded { preference })
        assertTrue(controller.selections.isEmpty())

        runtime.playbackSnapshot = PlayerPlaybackSnapshot(isLoading = false)
        runtime.subtitleTracks = emptyList()
        assertFalse(runtime.restorePersistedTrackPreferenceIfNeeded { preference })
        assertFalse(runtime.trackPreferenceRestoreApplied)
    }

    @Test
    fun waitsForAcknowledgementAndStopsSelectingOnceConfirmed() {
        val controller = RecordingController()
        val runtime = runtime(controller)
        runtime.subtitleTracks = listOf(pgs)

        assertFalse(runtime.restorePersistedTrackPreferenceIfNeeded { preference })
        assertEquals(listOf(0), controller.selections)
        assertFalse(runtime.trackPreferenceRestoreApplied)
        assertFalse(runtime.preferredSubtitleSelectionApplied)
        assertEquals(-1, runtime.selectedSubtitleIndex)

        runtime.subtitleTracks = listOf(pgs.copy(isSelected = true))
        assertTrue(runtime.restorePersistedTrackPreferenceIfNeeded { preference })
        assertTrue(runtime.trackPreferenceRestoreApplied)
        assertTrue(runtime.preferredSubtitleSelectionApplied)
        assertEquals(0, runtime.selectedSubtitleIndex)
        assertTrue(runtime.restorePersistedTrackPreferenceIfNeeded { preference })
        assertEquals(listOf(0), controller.selections)
    }

    @Test
    fun manualOffOrAnotherSubtitleCancelsPendingRestore() {
        for (manualIndex in listOf(-1, 1)) {
            val controller = RecordingController()
            val runtime = runtime(controller)
            runtime.subtitleTracks = listOf(pgs)
            assertFalse(runtime.restorePersistedTrackPreferenceIfNeeded { preference })

            runtime.isUserExplicitSubtitleSelection = true
            runtime.selectedSubtitleIndex = manualIndex
            runtime.preferredSubtitleSelectionApplied = true
            assertTrue(runtime.restorePersistedTrackPreferenceIfNeeded { preference })
            assertEquals(listOf(0), controller.selections)
            assertEquals(manualIndex, runtime.selectedSubtitleIndex)
        }
    }

    @Test
    fun nextEpisodeResolvesItsOwnTracksInsteadOfReusingTheOldId() {
        val controller = RecordingController()
        val runtime = runtime(controller)
        runtime.resetIdentityStateIfNeeded()
        runtime.subtitleTracks = listOf(pgs)
        assertFalse(runtime.restorePersistedTrackPreferenceIfNeeded { preference })

        runtime.activeSourceUrl = "https://example.invalid/episode-2.mkv"
        runtime.resetIdentityStateIfNeeded()
        assertTrue(runtime.subtitleTracks.isEmpty())
        assertFalse(runtime.restorePersistedTrackPreferenceIfNeeded { preference })
        runtime.subtitleTracks = listOf(
            SubtitleTrack(0, "2", "Japanese (PGS)", "ja"),
            pgs.copy(index = 1, id = "3"),
        )
        assertFalse(runtime.restorePersistedTrackPreferenceIfNeeded { preference })
        assertEquals(listOf(0, 1), controller.selections)
    }

    private fun runtime(controller: RecordingController) = PlayerScreenRuntime(
        PlayerScreenArgs(
            profileId = 1, title = "Subtitle restore test",
            sourceUrl = "https://example.invalid/episode-1.mkv", sourceAudioUrl = null,
            sourceHeaders = emptyMap(), sourceResponseHeaders = emptyMap(), streamType = null,
            providerName = "Test", streamTitle = "Test", streamSubtitle = null,
            initialBingeGroup = null, pauseDescription = null, onBack = {},
            onOpenInExternalPlayer = null, onOpenExternalUrl = null, modifier = Modifier,
            logo = null, poster = null, background = null, seasonNumber = 1, episodeNumber = 1,
            episodeTitle = null, episodeThumbnail = null, contentType = "series",
            videoId = "subtitle-restore-test:1:1", parentMetaId = "subtitle-restore-test",
            parentMetaType = "series", providerAddonId = null, torrentInfoHash = null,
            torrentFileIdx = null, torrentFilename = null, torrentTrackers = emptyList(),
            initialPositionMs = 0L, initialProgressFraction = null,
        ),
    ).apply {
        playerController = controller
        playbackSnapshot = PlayerPlaybackSnapshot(isLoading = false)
    }

    private class RecordingController : PlayerEngineController {
        val selections = mutableListOf<Int>()
        override fun selectSubtitleTrack(index: Int) { selections += index }
        override fun getAudioTracks() = emptyList<AudioTrack>()
        override fun getSubtitleTracks() = emptyList<SubtitleTrack>()
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun seekBy(offsetMs: Long) = Unit
        override fun retry() = Unit
        override fun setPlaybackSpeed(speed: Float) = Unit
        override fun applyAudioLanguagePreferences(languages: List<String>) = Unit
        override fun selectAudioTrack(index: Int) = Unit
        override fun setSubtitleUri(url: String) = Unit
        override fun clearExternalSubtitle() = Unit
        override fun clearExternalSubtitleAndSelect(trackIndex: Int) { selections += trackIndex }
    }
}
