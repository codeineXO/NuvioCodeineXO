package com.nuvio.app.features.player

import com.nuvio.app.features.player.desktop.toControlsJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PlaybackStatsMetadataTest {
    private fun metadata(addon: AddonSubtitle? = null, embedded: SubtitleTrack? = null) =
        buildPlaybackStatsMetadata(
            addonLogo = null, streamDescription = " ", playbackEngine = "MPV",
            serverPlayback = null,
            audioTrack = AudioTrack(0, "audio", "Title", "eng"),
            subtitleTrack = embedded, addonSubtitle = addon,
            addonSource = "Addon", embeddedSource = "Embedded",
        )

    @Test
    fun addonMetadataTakesPrecedenceOverEmbeddedTrack() {
        val result = metadata(
            AddonSubtitle("addon", "https://example.test/sub.srt", "fra", "French addon"),
            SubtitleTrack(0, "embedded", "English embedded", "eng"),
        )
        assertEquals("French addon", result.subtitleName)
        assertEquals("fra", result.subtitleLanguage)
        assertEquals("Addon", result.subtitleSource)
        assertEquals("eng", result.audioLanguage)
    }

    @Test
    fun embeddedAndAbsentSelectionsDoNotInventMetadata() {
        val embedded = metadata(embedded = SubtitleTrack(0, "embedded", "English", "eng"))
        assertEquals("Embedded", embedded.subtitleSource)
        assertEquals("English", embedded.subtitleName)
        val absent = metadata()
        assertNull(absent.subtitleName)
        assertNull(absent.subtitleSource)
        assertNull(absent.streamDescription)
        assertNull(absent.serverSummary)
    }

    @Test
    fun bridgeEscapesMetadataAndDoesNotSendParallelInfoPayload() {
        val description = "Line one\nA \"quoted\" description \\ end"
        val state = PlayerControlsState(
            playbackStatsMetadata = metadata().copy(streamDescription = description),
        )
        val json = Json.parseToJsonElement(state.toControlsJson(false)).jsonObject
        val info = json.getValue("playbackStatsMetadata").jsonObject
        assertEquals(description, info.getValue("streamDescription").jsonPrimitive.content)
        assertEquals("MPV", info.getValue("playbackEngine").jsonPrimitive.content)
        assertFalse("streamInfoSections" in json)
        assertEquals(JsonNull, Json.parseToJsonElement(PlayerControlsState().toControlsJson(false))
            .jsonObject["playbackStatsMetadata"])
    }
}
