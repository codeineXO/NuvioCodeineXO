package com.nuvio.app.features.discordrpc

import com.nuvio.app.AppScreenTab
import com.nuvio.app.core.ui.PresenceSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscordPresenceManagerTest {

    private val json = discordIpcJson

    @Test
    fun idleActivity_hasNuvioCodeineXONameAndDownloadButton() {
        assertEquals("NuvioCodeineXO", IdleActivity.name)
        assertEquals(0, IdleActivity.type)
        assertEquals("Browsing NuvioCodeineXO", IdleActivity.details)
        assertEquals("In Menus", IdleActivity.state)
        assertNotNull(IdleActivity.assets)
        assertNotNull(IdleActivity.timestamps)
        assertEquals(1, IdleActivity.buttons?.size)
        assertEquals("Download NuvioCodeineXO", IdleActivity.buttons?.first()?.label)
        assertEquals("https://github.com/codeineXO/NuvioCodeineXO", IdleActivity.buttons?.first()?.url)
    }

    @Test
    fun tabSnapshot_hasNuvioCodeineXONameAndDownloadButton() {
        val activity = PresenceSnapshot.Tab(AppScreenTab.Home).toDiscordActivity()
        assertEquals("NuvioCodeineXO", activity.name)
        assertEquals("Browsing Home", activity.details)
        assertEquals("Exploring Content", activity.state)
        assertNotNull(activity.assets)
        assertNotNull(activity.timestamps)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)
    }

    @Test
    fun playerSnapshot_forSeries_includesEpisodeThumbnailInSmallImage() {
        val snapshot = PresenceSnapshot.Player(
            title = "Breaking Bad",
            episodeLabel = "S01E01 - Pilot",
            posterUrl = "https://example.com/series_poster.jpg",
            episodeThumbnailUrl = "https://example.com/episode_thumb.jpg",
            isPlaying = true,
            positionMs = 15_000L,
            durationMs = 60_000L,
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("NuvioCodeineXO", activity.name)
        assertEquals("Breaking Bad", activity.details)
        assertEquals("S01, E01: Pilot", activity.state)
        assertEquals(2, activity.statusDisplayType)

        assertNotNull(activity.assets)
        assertEquals("https://example.com/series_poster.jpg", activity.assets?.largeImage)
        assertEquals("Breaking Bad", activity.assets?.largeText)
        assertEquals("https://example.com/episode_thumb.jpg", activity.assets?.smallImage)
        assertEquals("S01, E01: Pilot", activity.assets?.smallText)

        assertNotNull(activity.buttons)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)
        assertEquals("https://github.com/codeineXO/NuvioCodeineXO", activity.buttons?.first()?.url)
    }

    @Test
    fun playerSnapshot_forMovie_omitsSmallImageAndShowsMovieTitleInDetails() {
        val snapshot = PresenceSnapshot.Player(
            title = "Inception",
            episodeLabel = null,
            posterUrl = "https://example.com/inception_poster.jpg",
            episodeThumbnailUrl = null,
            isPlaying = true,
            positionMs = 30_000L,
            durationMs = 120_000L,
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("NuvioCodeineXO", activity.name)
        assertEquals("Inception", activity.details)
        assertEquals("Watching", activity.state)
        assertEquals(2, activity.statusDisplayType)

        assertNotNull(activity.assets)
        assertEquals("https://example.com/inception_poster.jpg", activity.assets?.largeImage)
        assertEquals("Inception", activity.assets?.largeText)
        assertNull(activity.assets?.smallImage)
        assertNull(activity.assets?.smallText)

        assertNotNull(activity.buttons)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)
    }

    @Test
    fun discordActivitySerialization_includesAllCustomFields() {
        val activity = DiscordActivity(
            type = 3,
            name = "NuvioCodeineXO",
            details = "Inception",
            state = "Watching",
            statusDisplayType = 2,
            assets = DiscordActivityAssets(
                largeImage = "https://example.com/poster.jpg",
                largeText = "Inception",
                smallImage = "https://example.com/thumb.jpg",
                smallText = "Thumbnail",
            ),
            buttons = listOf(
                DiscordActivityButton(
                    label = "Download NuvioCodeineXO",
                    url = "https://github.com/codeineXO/NuvioCodeineXO",
                ),
            ),
        )

        val encoded = json.encodeToString(activity)

        assertTrue(encoded.contains(""""name":"NuvioCodeineXO""""))
        assertTrue(encoded.contains(""""details":"Inception""""))
        assertTrue(encoded.contains(""""status_display_type":2"""))
        assertTrue(encoded.contains(""""large_image":"https://example.com/poster.jpg""""))
        assertTrue(encoded.contains(""""small_image":"https://example.com/thumb.jpg""""))
        assertTrue(encoded.contains(""""label":"Download NuvioCodeineXO""""))
        assertTrue(encoded.contains(""""url":"https://github.com/codeineXO/NuvioCodeineXO""""))
        assertTrue(encoded.contains("\"instance\":false"))
    }

    @Test
    fun discordActivitySerialization_omitsNullFields_toPreventDiscordGatewaySchemaRejection() {
        val idleEncoded = json.encodeToString(IdleActivity)
        assertTrue(idleEncoded.contains("\"name\":\"NuvioCodeineXO\""))
        assertTrue(idleEncoded.contains("\"details\":\"Browsing NuvioCodeineXO\""))
        assertTrue(idleEncoded.contains("\"state\":\"In Menus\""))
        assertTrue(idleEncoded.contains("\"type\":0"))
        assertTrue(idleEncoded.contains("\"instance\":false"))
        assertTrue(idleEncoded.contains("\"assets\""))
        assertTrue(idleEncoded.contains("\"timestamps\""))
        assertTrue(!idleEncoded.contains("\"assets\":null"))
        assertTrue(!idleEncoded.contains("\"timestamps\":null"))
        assertTrue(!idleEncoded.contains("\"state\":null"))

        val partial = DiscordActivity(
            type = 3,
            name = "NuvioCodeineXO",
            details = "Minimal",
        )
        val partialEncoded = json.encodeToString(partial)
        assertTrue(partialEncoded.contains("\"name\":\"NuvioCodeineXO\""))
        assertTrue(partialEncoded.contains("\"details\":\"Minimal\""))
        assertTrue(!partialEncoded.contains("\"assets\""))
        assertTrue(!partialEncoded.contains("\"timestamps\""))
        assertTrue(!partialEncoded.contains("\"state\""))
        assertTrue(!partialEncoded.contains("\"buttons\""))
    }

    @Test
    fun detailsSnapshot_withPoster_rendersPosterInAssets() {
        val snapshot = PresenceSnapshot.Details(
            title = "Dune: Part Two",
            posterUrl = "https://example.com/dune_poster.jpg",
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("NuvioCodeineXO", activity.name)
        assertEquals("Viewing Dune: Part Two", activity.details)
        assertEquals(2, activity.statusDisplayType)
        assertNotNull(activity.assets)
        assertEquals("https://example.com/dune_poster.jpg", activity.assets?.largeImage)
        assertEquals("Dune: Part Two", activity.assets?.largeText)
        assertNotNull(activity.buttons)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)
    }

    @Test
    fun streamSelectionSnapshot_rendersChoosingStreamAndPoster() {
        val snapshot = PresenceSnapshot.StreamSelection(
            title = "Fallout",
            posterUrl = "https://example.com/fallout_poster.jpg",
            episodeLabel = "S01E01 - The End",
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("NuvioCodeineXO", activity.name)
        assertEquals("Choosing Stream", activity.details)
        assertEquals("Fallout (S01, E01: The End)", activity.state)
        assertEquals(2, activity.statusDisplayType)
        assertNotNull(activity.assets)
        assertEquals("https://example.com/fallout_poster.jpg", activity.assets?.largeImage)
        assertEquals("Fallout", activity.assets?.largeText)
        assertNotNull(activity.buttons)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)
    }

    @Test
    fun resolveDiscordActivity_onlyWatchingMode_filtersOutNonPlayerSnapshots() {
        val playerSnapshot = PresenceSnapshot.Player(
            title = "Inception",
            episodeLabel = null,
            posterUrl = "https://example.com/inception.jpg",
            isPlaying = true,
            positionMs = 0L,
            durationMs = 1000L,
        )
        val detailsSnapshot = PresenceSnapshot.Details("Inception", "https://example.com/inception.jpg")
        val streamSnapshot = PresenceSnapshot.StreamSelection("Inception", "https://example.com/inception.jpg")
        val tabSnapshot = PresenceSnapshot.Tab(AppScreenTab.Home)

        // In ONLY_WATCHING mode, player returns active activity, but all others return null
        assertNotNull(resolveDiscordActivity(playerSnapshot, com.nuvio.app.features.settings.DiscordActivityMode.ONLY_WATCHING))
        assertNull(resolveDiscordActivity(detailsSnapshot, com.nuvio.app.features.settings.DiscordActivityMode.ONLY_WATCHING))
        assertNull(resolveDiscordActivity(streamSnapshot, com.nuvio.app.features.settings.DiscordActivityMode.ONLY_WATCHING))
        assertNull(resolveDiscordActivity(tabSnapshot, com.nuvio.app.features.settings.DiscordActivityMode.ONLY_WATCHING))
        assertNull(resolveDiscordActivity(null, com.nuvio.app.features.settings.DiscordActivityMode.ONLY_WATCHING))
    }

    @Test
    fun resolveDiscordActivity_allMode_rendersAllSnapshots() {
        val detailsSnapshot = PresenceSnapshot.Details("Inception", "https://example.com/inception.jpg")
        val streamSnapshot = PresenceSnapshot.StreamSelection("Inception", "https://example.com/inception.jpg")

        assertNotNull(resolveDiscordActivity(detailsSnapshot, com.nuvio.app.features.settings.DiscordActivityMode.ALL))
        assertNotNull(resolveDiscordActivity(streamSnapshot, com.nuvio.app.features.settings.DiscordActivityMode.ALL))
        assertEquals(IdleActivity, resolveDiscordActivity(null, com.nuvio.app.features.settings.DiscordActivityMode.ALL))
    }
}
