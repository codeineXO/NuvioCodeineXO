package com.nuvio.app.features.player

import androidx.compose.ui.Modifier
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsUiState
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerContentClassificationTest {
    @Test fun metadataAndCredentialRefreshUpdateTheActiveTypeAndRestoredLaunch() {
        val runtime = PlayerScreenRuntime(args())
        runtime.playerMeta = MetaDetails("tt123", "series", "Title")
        runtime.playerMetaContentKey = runtime.classificationContentKey
        runtime.metaUiState = MetaDetailsUiState(meta = runtime.playerMeta)
        runtime.refreshContentClassification()
        assertEquals(MediaContentType.LIVE_ACTION, runtime.mediaClassification.type)
        runtime.playerMeta = runtime.playerMeta!!.copy(genres = listOf("Anime"))
        runtime.refreshContentClassification()
        assertEquals(MediaContentType.ANIME, runtime.mediaClassification.type)
        runtime.activeSourceUrl = "https://example.test/refreshed"
        runtime.activeStream = StreamItem(addonName = "Test", addonId = "test", behaviorHints = StreamBehaviorHints(filename = "episode2.mkv"))
        runtime.activeSourceFilename = "episode2.mkv"
        runtime.activeVideoId = "tt123:1:2"
        runtime.refreshContentClassification()
        val restored = runtime.currentLaunch(PlayerLaunch(profileId = 1, title = "Title", sourceUrl = "old",
            streamTitle = "Stream", providerName = "Test", parentMetaId = "tt123", parentMetaType = "series"))
        assertEquals(MediaContentType.ANIME, restored.mediaContentType)
        assertEquals(MediaContentType.ANIME, MediaClassification.fromHint(restored.classificationHint)?.type)
        assertEquals("episode2.mkv", restored.sourceFilename)
        assertEquals(runtime.activeStream, restored.stream)
    }

    private fun args() = PlayerScreenArgs(
        profileId = 1, title = "Title", sourceUrl = "https://example.test/video", sourceAudioUrl = null,
        sourceHeaders = emptyMap(), sourceResponseHeaders = emptyMap(), streamType = null, providerName = "Test",
        streamTitle = "Stream", streamSubtitle = null, initialBingeGroup = null, pauseDescription = null,
        onBack = {}, onOpenInExternalPlayer = null, onOpenExternalUrl = null, modifier = Modifier,
        logo = null, poster = null, background = null, seasonNumber = 1, episodeNumber = 1, episodeTitle = null,
        episodeThumbnail = null, contentType = "series", videoId = "tt123:1:1", parentMetaId = "tt123", parentMetaType = "series",
        providerAddonId = null, torrentInfoHash = null, torrentFileIdx = null, torrentFilename = null, torrentTrackers = emptyList(),
        initialPositionMs = 0, initialProgressFraction = null,
    )
}
