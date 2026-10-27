package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerControlPartyMember
import com.nuvio.app.features.player.PlayerControlPartyState
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerControlChapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PartyControlsSerializationTest {

    @Test
    fun toControlsJsonIncludesBothForkEnhancementsAndPartyState() {
        val state = PlayerControlsState(
            title = "Test Episode",
            positionMs = 120_000L,
            durationMs = 360_000L,
            isMiniPlayer = true,
            playerUiMode = "codeine_xo",
            seekPreviewEnabled = true,
            animeUpscalerEnabled = true,
            svpMode = "auto",
            svpActive = true,
            audioNightModeEnabled = true,
            chapters = listOf(PlayerControlChapter(0L, "Opening"), PlayerControlChapter(60_000L, "A \"quoted\" chapter")),
            chaptersLabel = "Chapters",
            party = PlayerControlPartyState(
                active = true,
                code = "ABC123",
                isHost = true,
                synced = true,
                members = listOf(
                    PlayerControlPartyMember(id = "user1", name = "Alice", isHost = true),
                    PlayerControlPartyMember(id = "user2", name = "Bob"),
                ),
            ),
        )

        val jsonString = state.toControlsJson(isFullscreen = false)
        val json = Json.parseToJsonElement(jsonString).jsonObject

        // Verify fork enhancements
        assertEquals("Test Episode", json["title"]?.jsonPrimitive?.content)
        assertEquals(true, json["isMiniPlayer"]?.jsonPrimitive?.booleanOrNull)
        assertEquals("codeine_xo", json["playerUiMode"]?.jsonPrimitive?.content)
        assertEquals(true, json["seekPreviewEnabled"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, json["animeUpscalerEnabled"]?.jsonPrimitive?.booleanOrNull)
        assertEquals("auto", json["svpMode"]?.jsonPrimitive?.content)
        assertEquals(true, json["svpActive"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, json["audioNightModeEnabled"]?.jsonPrimitive?.booleanOrNull)
        val chaptersJson = json["chapters"]!!.jsonArray
        assertEquals(2, chaptersJson.size)
        assertEquals("60000", chaptersJson[1].jsonObject["startMs"]?.jsonPrimitive?.content)
        assertEquals("A \"quoted\" chapter", chaptersJson[1].jsonObject["title"]?.jsonPrimitive?.content)
        assertEquals("Chapters", json["chaptersLabel"]?.jsonPrimitive?.content)

        // Verify party state serialization
        val partyJson = json["party"]?.jsonObject
        assertNotNull(partyJson)
        assertEquals(true, partyJson["active"]?.jsonPrimitive?.booleanOrNull)
        assertEquals("ABC123", partyJson["code"]?.jsonPrimitive?.content)
        assertEquals(true, partyJson["isHost"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, partyJson["synced"]?.jsonPrimitive?.booleanOrNull)
        val membersJson = partyJson["members"]?.jsonArray
        assertNotNull(membersJson)
        assertEquals(2, membersJson.size)
        assertEquals("user1", membersJson[0].jsonObject["id"]?.jsonPrimitive?.content)
    }
}
