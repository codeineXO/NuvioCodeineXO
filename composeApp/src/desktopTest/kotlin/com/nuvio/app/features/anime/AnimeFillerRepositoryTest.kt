package com.nuvio.app.features.anime

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.groupedEpisodesForDisplay
import com.nuvio.app.features.player.skip.PlayerNextEpisodeRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimeFillerRepositoryTest {

    @Test
    fun testSlugify() {
        assertEquals("naruto", AnimeFillerRepository.slugify("Naruto"))
        assertEquals("naruto-shippuden", AnimeFillerRepository.slugify("Naruto: Shippuden"))
        assertEquals("one-piece", AnimeFillerRepository.slugify("One Piece"))
        assertEquals("bleach-thousand-year-blood-war", AnimeFillerRepository.slugify("Bleach: Thousand-Year Blood War"))
        assertEquals("hunter-x-hunter-2011", AnimeFillerRepository.slugify("Hunter x Hunter (2011)"))
    }

    @Test
    fun testParseAflHtmlCondensedList() {
        val sampleHtml = """
            <div id="Condensed">
              <div class="mixed_canon/filler">
                <span class="Label">Mixed Canon/Filler Episodes:</span>
                <span class="Episodes">
                  <a href="javascript://" onclick="jumpToNum(7);">7</a>,
                  <a href="javascript://" onclick="jumpToNum(9);">9</a>,
                  <a href="javascript://" onclick="jumpToNum(14);">14-16</a>
                </span>
              </div>
              <div class="filler">
                <span class="Label">Filler Episodes:</span>
                <span class="Episodes">
                  <a href="javascript://" onclick="jumpToNum(26);">26</a>,
                  <a href="javascript://" onclick="jumpToNum(97);">97</a>,
                  <a href="javascript://" onclick="jumpToNum(101);">101-104</a>
                </span>
              </div>
            </div>
        """.trimIndent()

        val result = AnimeFillerRepository.parseAflHtml(sampleHtml)
        val fillers = result.fillers
        val mixed = result.mixed

        assertTrue(fillers.contains(26))
        assertTrue(fillers.contains(97))
        assertTrue(fillers.contains(101))
        assertTrue(fillers.contains(102))
        assertTrue(fillers.contains(103))
        assertTrue(fillers.contains(104))
        assertFalse(fillers.contains(7))
        assertFalse(fillers.contains(9))
        assertFalse(fillers.contains(14))
        assertEquals(6, fillers.size)

        assertTrue(mixed.contains(7))
        assertTrue(mixed.contains(9))
        assertTrue(mixed.contains(14))
        assertTrue(mixed.contains(15))
        assertTrue(mixed.contains(16))
        assertFalse(mixed.contains(26))
        assertEquals(5, mixed.size)
    }

    @Test
    fun testParseAflHtmlTableRows() {
        val sampleHtml = """
            <table class="EpisodeList">
              <tbody>
                <tr class="manga_canon odd" id="eps-1"><td class="Number">1</td><td class="Type"><span>Manga Canon</span></td></tr>
                <tr class="filler even" id="eps-2"><td class="Number">2</td><td class="Type"><span>Filler</span></td></tr>
                <tr class="mixed_canon/filler odd" id="eps-3"><td class="Number">3</td><td class="Type"><span>Mixed Canon/Filler</span></td></tr>
              </tbody>
            </table>
        """.trimIndent()

        val result = AnimeFillerRepository.parseAflHtml(sampleHtml)
        assertFalse(result.fillers.contains(1))
        assertTrue(result.fillers.contains(2))
        assertFalse(result.fillers.contains(3))
        assertEquals(1, result.fillers.size)

        assertFalse(result.mixed.contains(1))
        assertFalse(result.mixed.contains(2))
        assertTrue(result.mixed.contains(3))
        assertEquals(1, result.mixed.size)
    }

    @Test
    fun testParseAflHtmlEmptyWhenNoFillers() {
        val sampleHtml = """
            <table class="EpisodeList">
              <tbody>
                <tr class="manga_canon odd" id="eps-1"><td class="Number">1</td><td class="Type"><span>Manga Canon</span></td></tr>
              </tbody>
            </table>
        """.trimIndent()

        val result = AnimeFillerRepository.parseAflHtml(sampleHtml)
        assertTrue(result.isEmpty)
    }

    @Test
    fun testMetaVideoIsFillerAndIsMixed() {
        val video = MetaVideo(
            id = "vid_1",
            title = "Episode 1",
            season = 1,
            episode = 1,
        )
        assertFalse(video.isFiller)
        assertFalse(video.isMixed)

        val fillerVideo = video.copy(isFiller = true)
        assertTrue(fillerVideo.isFiller)
        assertFalse(fillerVideo.isMixed)

        val mixedVideo = video.copy(isMixed = true)
        assertFalse(mixedVideo.isFiller)
        assertTrue(mixedVideo.isMixed)
    }

    @Test
    fun testAnimeFillerSettingsRepository() {
        AnimeFillerSettingsRepository.ensureLoaded()
        val original = AnimeFillerSettingsRepository.snapshot()

        try {
            AnimeFillerSettingsRepository.setEnabled(true)
            assertTrue(AnimeFillerSettingsRepository.snapshot())
            assertTrue(AnimeFillerSettingsRepository.enabled.value)

            AnimeFillerSettingsRepository.setEnabled(false)
            assertFalse(AnimeFillerSettingsRepository.snapshot())
            assertFalse(AnimeFillerSettingsRepository.enabled.value)
        } finally {
            AnimeFillerSettingsRepository.setEnabled(original)
        }
    }

    @Test
    fun testAnimeFillerSettingsRepositoryHideFillers() {
        AnimeFillerSettingsRepository.ensureLoaded()
        val original = AnimeFillerSettingsRepository.snapshotHideFillers()

        try {
            AnimeFillerSettingsRepository.setHideFillers(true)
            assertTrue(AnimeFillerSettingsRepository.snapshotHideFillers())
            assertTrue(AnimeFillerSettingsRepository.hideFillers.value)

            AnimeFillerSettingsRepository.setHideFillers(false)
            assertFalse(AnimeFillerSettingsRepository.snapshotHideFillers())
            assertFalse(AnimeFillerSettingsRepository.hideFillers.value)
        } finally {
            AnimeFillerSettingsRepository.setHideFillers(original)
        }
    }

    @Test
    fun testResolveNextEpisodeSkippingFillers() {
        val episodes = listOf(
            MetaVideo(id = "ep1", title = "Episode 1", season = 1, episode = 1, isFiller = false, isMixed = false),
            MetaVideo(id = "ep2", title = "Episode 2", season = 1, episode = 2, isFiller = true, isMixed = false),
            MetaVideo(id = "ep3", title = "Episode 3", season = 1, episode = 3, isFiller = true, isMixed = false),
            MetaVideo(id = "ep4", title = "Episode 4", season = 1, episode = 4, isFiller = false, isMixed = true),
            MetaVideo(id = "ep5", title = "Episode 5", season = 1, episode = 5, isFiller = false, isMixed = false),
        )

        // When hideFillers is false, Ep 1 next is Ep 2 (filler)
        val nextSequential = PlayerNextEpisodeRules.resolveNextEpisode(episodes, 1, 1, hideFillers = false)
        assertEquals("ep2", nextSequential?.id)

        // When hideFillers is true, Ep 1 next skips Ep 2 & 3 (fillers) to Ep 4 (mixed)
        val nextSkippingFillers = PlayerNextEpisodeRules.resolveNextEpisode(episodes, 1, 1, hideFillers = true)
        assertEquals("ep4", nextSkippingFillers?.id)

        // From mixed episode 4 with hideFillers = true, next is canon episode 5
        val nextFromMixed = PlayerNextEpisodeRules.resolveNextEpisode(episodes, 1, 4, hideFillers = true)
        assertEquals("ep5", nextFromMixed?.id)

        // If user is currently watching filler episode 2, pressing next with hideFillers = true skips filler 3 and jumps to mixed episode 4
        val nextFromFiller = PlayerNextEpisodeRules.resolveNextEpisode(episodes, 1, 2, hideFillers = true)
        assertEquals("ep4", nextFromFiller?.id)

        // Anime catalog with null seasons: episodes only have episode numbers
        val animeEpisodesNullSeason = listOf(
            MetaVideo(id = "a1", title = "Anime Ep 1", season = null, episode = 1, isFiller = false, isMixed = false),
            MetaVideo(id = "a2", title = "Anime Ep 2", season = null, episode = 2, isFiller = true, isMixed = false),
            MetaVideo(id = "a3", title = "Anime Ep 3", season = null, episode = 3, isFiller = false, isMixed = false),
        )
        val animeNextSequential = PlayerNextEpisodeRules.resolveNextEpisode(animeEpisodesNullSeason, null, 1, hideFillers = false)
        assertEquals("a2", animeNextSequential?.id)

        val animeNextSkipFiller = PlayerNextEpisodeRules.resolveNextEpisode(animeEpisodesNullSeason, null, 1, hideFillers = true)
        assertEquals("a3", animeNextSkipFiller?.id)

        // Matching by currentVideoId when season is null or missing
        val animeNextById = PlayerNextEpisodeRules.resolveNextEpisode(animeEpisodesNullSeason, null, null, hideFillers = false, currentVideoId = "a1")
        assertEquals("a2", animeNextById?.id)
    }

    @Test
    fun testGroupedEpisodesForDisplayHideFillers() {
        val meta = MetaDetails(
            id = "test-anime",
            type = "series",
            name = "Test Anime",
            videos = listOf(
                MetaVideo(id = "ep1", title = "Ep 1", season = 1, episode = 1, isFiller = false, isMixed = false),
                MetaVideo(id = "ep2", title = "Ep 2", season = 1, episode = 2, isFiller = true, isMixed = false),
                MetaVideo(id = "ep3", title = "Ep 3", season = 1, episode = 3, isFiller = false, isMixed = true),
                MetaVideo(id = "ep4", title = "Ep 4", season = 1, episode = 4, isFiller = false, isMixed = false),
            ),
        )

        // Default / hideFillerEpisodes = false retains all episodes
        val allGrouped = meta.groupedEpisodesForDisplay(hideFillerEpisodes = false)
        assertEquals(4, allGrouped[1]?.size)

        // hideFillerEpisodes = true removes ep2 (filler), but retains ep3 (mixed) and ep1, ep4 (canon)
        val filteredGrouped = meta.groupedEpisodesForDisplay(hideFillerEpisodes = true)
        val season1 = filteredGrouped[1].orEmpty()
        assertEquals(3, season1.size)
        assertEquals(listOf("ep1", "ep3", "ep4"), season1.map { it.id })
    }

    @Test
    fun testGroupedEpisodesForDisplayAllFillerSeason() {
        val meta = MetaDetails(
            id = "test-anime-all-filler-season",
            type = "series",
            name = "Test Anime All Filler Season",
            videos = listOf(
                MetaVideo(id = "s1e1", title = "S1 Ep 1", season = 1, episode = 1, isFiller = false, isMixed = false),
                MetaVideo(id = "s2e1", title = "S2 Ep 1", season = 2, episode = 1, isFiller = true, isMixed = false),
                MetaVideo(id = "s2e2", title = "S2 Ep 2", season = 2, episode = 2, isFiller = true, isMixed = false),
            ),
        )

        val filteredGrouped = meta.groupedEpisodesForDisplay(hideFillerEpisodes = true)
        // Season 2 should remain as a key in the map to prevent crashes and preserve season tabs
        assertTrue(filteredGrouped.containsKey(2))
        assertTrue(filteredGrouped[2].orEmpty().isEmpty())
        assertEquals(1, filteredGrouped[1]?.size)
    }
}
