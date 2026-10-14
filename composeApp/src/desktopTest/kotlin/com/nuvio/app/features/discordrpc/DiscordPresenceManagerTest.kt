package com.nuvio.app.features.discordrpc

import com.nuvio.app.AppScreenTab
import com.nuvio.app.core.ui.AppPresenceState
import com.nuvio.app.core.ui.PresenceSnapshot
import com.nuvio.app.features.settings.DiscordActivityType
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
        assertEquals(3, IdleActivity.type)
        assertEquals("Exploring NuvioCodeineXO", IdleActivity.details)
        assertEquals("In Menus", IdleActivity.state)
        assertNotNull(IdleActivity.assets)
        assertNotNull(IdleActivity.timestamps)
        assertEquals(1, IdleActivity.buttons?.size)
        assertEquals("Download NuvioCodeineXO", IdleActivity.buttons?.first()?.label)
        assertEquals("https://github.com/codeineXO/NuvioCodeineXO", IdleActivity.buttons?.first()?.url)

        val watchingIdle = createIdleActivity(DiscordActivityType.WATCHING)
        assertEquals("NuvioCodeineXO", watchingIdle.name)
        assertEquals(3, watchingIdle.type)

        val playingIdle = createIdleActivity(DiscordActivityType.PLAYING)
        assertEquals("NuvioCodeineXO", playingIdle.name)
        assertEquals(0, playingIdle.type)
    }

    @Test
    fun tabSnapshot_hasNuvioCodeineXONameAndDownloadButton() {
        val activity = PresenceSnapshot.Tab(AppScreenTab.Home).toDiscordActivity()
        assertEquals("NuvioCodeineXO", activity.name)
        assertEquals(3, activity.type)
        assertEquals("Exploring NuvioCodeineXO", activity.details)
        assertEquals("Exploring Content", activity.state)
        assertNotNull(activity.assets)
        assertNotNull(activity.timestamps)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)

        val playingTab = PresenceSnapshot.Tab(AppScreenTab.Home).toDiscordActivity(DiscordActivityType.PLAYING)
        assertEquals("NuvioCodeineXO", playingTab.name)
        assertEquals(0, playingTab.type)

        val settingsActivity = PresenceSnapshot.Tab(AppScreenTab.Settings).toDiscordActivity()
        assertEquals("NuvioCodeineXO", settingsActivity.name)
        assertEquals(3, settingsActivity.type)
        assertEquals("Tweaking NuvioCodeineXO", settingsActivity.details)
        assertEquals("In Settings", settingsActivity.state)
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
            imdbId = "tt0903747",
            episodeImdbId = "tt0959621",
            seasonNumber = 1,
            episodeNumber = 1,
            isSeries = true,
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("Breaking Bad", activity.name)
        assertEquals(3, activity.type)
        assertEquals("Breaking Bad", activity.details)
        assertEquals("https://www.imdb.com/title/tt0903747/", activity.detailsUrl)
        assertEquals("S01, E01: Pilot", activity.state)
        assertEquals(2, activity.statusDisplayType)

        assertNotNull(activity.assets)
        assertEquals("https://example.com/series_poster.jpg", activity.assets?.largeImage)
        assertEquals("Breaking Bad", activity.assets?.largeText)
        assertEquals("https://www.imdb.com/title/tt0903747/", activity.assets?.largeUrl)
        assertEquals("https://example.com/episode_thumb.jpg", activity.assets?.smallImage)
        assertEquals("S01, E01: Pilot", activity.assets?.smallText)
        assertEquals("https://www.imdb.com/title/tt0959621/", activity.assets?.smallUrl)

        assertNotNull(activity.buttons)
        assertEquals(1, activity.buttons?.size)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)
        assertEquals("https://github.com/codeineXO/NuvioCodeineXO", activity.buttons?.first()?.url)

        val playingActivity = snapshot.toDiscordActivity(DiscordActivityType.PLAYING)
        assertEquals("NuvioCodeineXO", playingActivity.name)
        assertEquals(0, playingActivity.type)
        assertEquals("Breaking Bad", playingActivity.details)
    }

    @Test
    fun playerSnapshot_forSeries_withoutEpisodeImdbId_fallsBackToSeasonEpisodesUrlInSmallUrl() {
        val snapshot = PresenceSnapshot.Player(
            title = "Breaking Bad",
            episodeLabel = "S01E01 - Pilot",
            posterUrl = "https://example.com/series_poster.jpg",
            episodeThumbnailUrl = "https://example.com/episode_thumb.jpg",
            isPlaying = true,
            positionMs = 15_000L,
            durationMs = 60_000L,
            imdbId = "tt0903747",
            isSeries = true,
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("https://www.imdb.com/title/tt0903747/", activity.assets?.largeUrl)
        assertEquals("https://www.imdb.com/title/tt0903747/episodes/?season=1", activity.assets?.smallUrl)
    }

    @Test
    fun playerSnapshot_forSeries_whenPaused_appendsPausedToEpisodeName() {
        val snapshot = PresenceSnapshot.Player(
            title = "Breaking Bad",
            episodeLabel = "S01E01 - Pilot",
            posterUrl = "https://example.com/series_poster.jpg",
            episodeThumbnailUrl = null,
            isPlaying = false,
            positionMs = 15_000L,
            durationMs = 60_000L,
            imdbId = "tt0903747",
            isSeries = true,
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("S01, E01: Pilot • Paused", activity.state)
        assertEquals(2, activity.statusDisplayType)
        assertNull(activity.timestamps)
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
            imdbId = "tt1375666",
            isSeries = false,
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("Inception", activity.name)
        assertEquals(3, activity.type)
        assertEquals("Inception", activity.details)
        assertEquals("https://www.imdb.com/title/tt1375666/", activity.detailsUrl)
        assertEquals("Watching", activity.state)
        assertEquals(2, activity.statusDisplayType)

        assertNotNull(activity.assets)
        assertEquals("https://example.com/inception_poster.jpg", activity.assets?.largeImage)
        assertEquals("Inception", activity.assets?.largeText)
        assertEquals("https://www.imdb.com/title/tt1375666/", activity.assets?.largeUrl)
        assertNull(activity.assets?.smallImage)
        assertNull(activity.assets?.smallText)

        assertNotNull(activity.buttons)
        assertEquals(1, activity.buttons?.size)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)
        assertEquals("https://github.com/codeineXO/NuvioCodeineXO", activity.buttons?.first()?.url)

        val playingActivity = snapshot.toDiscordActivity(DiscordActivityType.PLAYING)
        assertEquals("NuvioCodeineXO", playingActivity.name)
        assertEquals(0, playingActivity.type)
        assertEquals("Inception", playingActivity.details)
    }

    @Test
    fun playerSnapshot_forMovie_whenPaused_showsPausedState() {
        val snapshot = PresenceSnapshot.Player(
            title = "Inception",
            episodeLabel = null,
            posterUrl = "https://example.com/inception_poster.jpg",
            isPlaying = false,
            positionMs = 30_000L,
            durationMs = 120_000L,
            isSeries = false,
        )

        val activity = snapshot.toDiscordActivity()
        assertEquals("Inception", activity.name)
        assertEquals(3, activity.type)
        assertEquals("Paused", activity.state)
        assertEquals(2, activity.statusDisplayType)
        assertNull(activity.timestamps)
    }

    @Test
    fun playerSnapshot_withBlankTitle_fallsBackToAppActivityName() {
        val snapshot = PresenceSnapshot.Player(
            title = "",
            episodeLabel = null,
            posterUrl = null,
            isPlaying = true,
            positionMs = 0L,
            durationMs = 0L,
            isSeries = false,
        )

        val activity = snapshot.toDiscordActivity()
        assertEquals("NuvioCodeineXO", activity.name)
        assertEquals(3, activity.type)
    }

    @Test
    fun discordActivitySerialization_includesAllCustomFields() {
        val activity = DiscordActivity(
            type = 0,
            name = "NuvioCodeineXO",
            details = "Inception",
            detailsUrl = "https://www.imdb.com/title/tt1375666/",
            state = "Watching",
            statusDisplayType = 2,
            assets = DiscordActivityAssets(
                largeImage = "https://example.com/poster.jpg",
                largeText = "Inception",
                largeUrl = "https://www.imdb.com/title/tt1375666/",
                smallImage = "https://example.com/thumb.jpg",
                smallText = "Thumbnail",
            ),
            buttons = listOf(
                DiscordActivityButton(
                    label = "View on IMDb",
                    url = "https://www.imdb.com/title/tt1375666/",
                ),
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
        assertTrue(encoded.contains(""""details_url":"https://www.imdb.com/title/tt1375666/""""))
        assertTrue(encoded.contains(""""large_image":"https://example.com/poster.jpg""""))
        assertTrue(encoded.contains(""""large_url":"https://www.imdb.com/title/tt1375666/""""))
        assertTrue(encoded.contains(""""small_image":"https://example.com/thumb.jpg""""))
        assertTrue(encoded.contains(""""label":"View on IMDb""""))
        assertTrue(encoded.contains(""""url":"https://github.com/codeineXO/NuvioCodeineXO""""))
        assertTrue(encoded.contains("\"instance\":false"))
    }

    @Test
    fun discordActivitySerialization_omitsNullFields_toPreventDiscordGatewaySchemaRejection() {
        val idleEncoded = json.encodeToString(IdleActivity)
        assertTrue(idleEncoded.contains("\"name\":\"NuvioCodeineXO\""))
        assertTrue(idleEncoded.contains("\"details\":\"Exploring NuvioCodeineXO\""))
        assertTrue(idleEncoded.contains("\"state\":\"In Menus\""))
        assertTrue(idleEncoded.contains("\"type\":3"))
        assertTrue(idleEncoded.contains("\"status_display_type\":2"))
        assertTrue(idleEncoded.contains("\"instance\":false"))
        assertTrue(idleEncoded.contains("\"assets\""))
        assertTrue(idleEncoded.contains("\"timestamps\""))
        assertTrue(!idleEncoded.contains("\"assets\":null"))
        assertTrue(!idleEncoded.contains("\"timestamps\":null"))
        assertTrue(!idleEncoded.contains("\"state\":null"))

        val partial = DiscordActivity(
            type = 0,
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
        assertTrue(!partialEncoded.contains("status_display_type"))
    }

    @Test
    fun detailsSnapshot_withPoster_rendersPosterInAssets() {
        val snapshot = PresenceSnapshot.Details(
            title = "Dune: Part Two",
            posterUrl = "https://example.com/dune_poster.jpg",
            imdbId = "tt15239678",
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("Dune: Part Two", activity.name)
        assertEquals(3, activity.type)
        assertEquals("Dune: Part Two", activity.details)
        assertEquals("https://www.imdb.com/title/tt15239678/", activity.detailsUrl)
        assertEquals("Overview", activity.state)
        assertEquals(2, activity.statusDisplayType)
        assertNotNull(activity.assets)
        assertEquals("https://example.com/dune_poster.jpg", activity.assets?.largeImage)
        assertEquals("Dune: Part Two", activity.assets?.largeText)
        assertEquals("https://www.imdb.com/title/tt15239678/", activity.assets?.largeUrl)
        assertNotNull(activity.buttons)
        assertEquals(1, activity.buttons?.size)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)

        val playingActivity = snapshot.toDiscordActivity(DiscordActivityType.PLAYING)
        assertEquals("NuvioCodeineXO", playingActivity.name)
        assertEquals(0, playingActivity.type)
        assertEquals("Dune: Part Two", playingActivity.details)
    }

    @Test
    fun streamSelectionSnapshot_rendersChoosingStreamAndPoster() {
        val snapshot = PresenceSnapshot.StreamSelection(
            title = "Fallout",
            posterUrl = "https://example.com/fallout_poster.jpg",
            episodeLabel = "S01E01 - The End",
            imdbId = "tt12637874",
        )

        val activity = snapshot.toDiscordActivity()

        assertEquals("Fallout", activity.name)
        assertEquals(3, activity.type)
        assertEquals("Fallout", activity.details)
        assertEquals("https://www.imdb.com/title/tt12637874/", activity.detailsUrl)
        assertEquals("S01, E01: The End", activity.state)
        assertEquals(2, activity.statusDisplayType)
        assertNotNull(activity.assets)
        assertEquals("https://example.com/fallout_poster.jpg", activity.assets?.largeImage)
        assertEquals("Fallout", activity.assets?.largeText)
        assertEquals("https://www.imdb.com/title/tt12637874/", activity.assets?.largeUrl)
        assertNotNull(activity.buttons)
        assertEquals(1, activity.buttons?.size)
        assertEquals("Download NuvioCodeineXO", activity.buttons?.first()?.label)

        val playingActivity = snapshot.toDiscordActivity(DiscordActivityType.PLAYING)
        assertEquals("NuvioCodeineXO", playingActivity.name)
        assertEquals(0, playingActivity.type)
        assertEquals("Fallout", playingActivity.details)
    }

    @Test
    fun buildImdbUrl_formatsDirectTitleAndFallbackSearch() {
        assertEquals("https://www.imdb.com/title/tt1375666/", buildImdbUrl("tt1375666", "Inception"))
        assertEquals("https://www.imdb.com/title/tt1375666/", buildImdbUrl("tt1375666:1:1", "Inception"))
        assertEquals("https://www.imdb.com/find/?q=Inception", buildImdbUrl(null, "Inception"))
        assertEquals("https://www.imdb.com/find/?q=Dune%3A+Part+Two", buildImdbUrl(null, "Dune: Part Two"))
        assertNull(buildImdbUrl(null, null))
        assertNull(buildImdbUrl("", ""))
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
        assertEquals(createIdleActivity(DiscordActivityType.PLAYING), resolveDiscordActivity(null, com.nuvio.app.features.settings.DiscordActivityMode.ALL, DiscordActivityType.PLAYING))
    }

    @Test
    fun formatDiscordPosterAsset_whenDisabled_returnsRawUrl() {
        val raw = "https://image.tmdb.org/t/p/w500/poster.jpg"
        assertEquals(raw, formatDiscordPosterAsset(raw, fullPosterEnabled = false))
    }

    @Test
    fun formatDiscordPosterAsset_whenEnabled_formatsProxyUrlWithContain() {
        val raw = "https://image.tmdb.org/t/p/w500/poster.jpg"
        val formatted = formatDiscordPosterAsset(raw, fullPosterEnabled = true)
        assertTrue(formatted.startsWith("https://wsrv.nl/?url="))
        assertTrue(formatted.contains("fit=contain"))
        assertTrue(formatted.contains("cbg=transparent"))
        assertTrue(formatted.contains("output=png"))
    }

    @Test
    fun formatDiscordPosterAsset_whenUrlExceedsLimit_fallsBackToRawUrl() {
        val longUrl = "https://example.com/" + "a".repeat(450) + ".jpg"
        assertEquals(longUrl, formatDiscordPosterAsset(longUrl, fullPosterEnabled = true))
    }

    @Test
    fun formatDiscordPosterAsset_whenBlankOrNull_returnsDefaultAppIcon() {
        val defaultIcon = formatDiscordPosterAsset(null, fullPosterEnabled = true)
        assertTrue(defaultIcon.contains("app_icon_original_transparent.png"))
        assertEquals(defaultIcon, formatDiscordPosterAsset("", fullPosterEnabled = true))
    }

    @Test
    fun resolveDiscordActivity_withFullPosterEnabled_appliesProxyFormatting() {
        val snapshot = PresenceSnapshot.Details("Dune", "https://example.com/dune.jpg")
        val activity = resolveDiscordActivity(snapshot, com.nuvio.app.features.settings.DiscordActivityMode.ALL, fullPosterEnabled = true)
        assertNotNull(activity)
        assertTrue(activity.assets?.largeImage?.startsWith("https://wsrv.nl/?url=") == true)
    }

    @Test
    fun appPresenceState_navigationSnapshot_updatesCurrentWhenNoPlayerActive() {
        AppPresenceState.reset()
        val homeTab = PresenceSnapshot.Tab(AppScreenTab.Home)
        AppPresenceState.publish(homeTab)
        assertEquals(homeTab, AppPresenceState.current.value)

        val details = PresenceSnapshot.Details("Inception", "https://example.com/inception.jpg")
        AppPresenceState.publish(details)
        assertEquals(details, AppPresenceState.current.value)
    }

    @Test
    fun appPresenceState_whenPlayerActive_navigationUpdatesDoNotChangeCurrent() {
        AppPresenceState.reset()
        val homeTab = PresenceSnapshot.Tab(AppScreenTab.Home)
        AppPresenceState.publish(homeTab)

        val playerSnapshot = PresenceSnapshot.Player(
            title = "Inception",
            episodeLabel = null,
            posterUrl = "https://example.com/inception.jpg",
            isPlaying = true,
            positionMs = 12_000L,
            durationMs = 120_000L,
        )
        AppPresenceState.publish(playerSnapshot)
        assertEquals(playerSnapshot, AppPresenceState.current.value)

        // User goes to mini player and navigates across tabs and detail pages
        val searchTab = PresenceSnapshot.Tab(AppScreenTab.Search)
        AppPresenceState.publish(searchTab)
        assertEquals(playerSnapshot, AppPresenceState.current.value)

        val details = PresenceSnapshot.Details("Interstellar", "https://example.com/interstellar.jpg")
        AppPresenceState.publish(details)
        assertEquals(playerSnapshot, AppPresenceState.current.value)

        val stream = PresenceSnapshot.StreamSelection("Interstellar", "https://example.com/interstellar.jpg")
        AppPresenceState.publish(stream)
        assertEquals(playerSnapshot, AppPresenceState.current.value)

        // Discord RPC activity resolved while in mini player continues to show Inception
        val activity = resolveDiscordActivity(
            AppPresenceState.current.value,
            com.nuvio.app.features.settings.DiscordActivityMode.ALL,
        )
        assertNotNull(activity)
        assertEquals("Inception", activity.details)
    }

    @Test
    fun appPresenceState_clearPlayer_fallsBackToLatestNavigationSnapshot() {
        AppPresenceState.reset()
        val homeTab = PresenceSnapshot.Tab(AppScreenTab.Home)
        AppPresenceState.publish(homeTab)

        val playerSnapshot = PresenceSnapshot.Player(
            title = "Inception",
            episodeLabel = null,
            posterUrl = "https://example.com/inception.jpg",
            isPlaying = true,
            positionMs = 12_000L,
            durationMs = 120_000L,
        )
        AppPresenceState.publish(playerSnapshot)

        // Browse to Settings while video plays in mini player
        val settingsTab = PresenceSnapshot.Tab(AppScreenTab.Settings)
        AppPresenceState.publish(settingsTab)
        assertEquals(playerSnapshot, AppPresenceState.current.value)

        // Close mini player
        AppPresenceState.clearPlayer()
        assertEquals(settingsTab, AppPresenceState.current.value)

        val activity = resolveDiscordActivity(
            AppPresenceState.current.value,
            com.nuvio.app.features.settings.DiscordActivityMode.ALL,
        )
        assertNotNull(activity)
        assertEquals("Tweaking NuvioCodeineXO", activity.details)
        assertEquals("In Settings", activity.state)
    }
}
