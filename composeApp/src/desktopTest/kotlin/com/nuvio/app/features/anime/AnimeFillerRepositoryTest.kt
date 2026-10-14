package com.nuvio.app.features.anime

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
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
}
