package com.nuvio.app.features.player.desktop

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HlsMasterTrimmerQualityTest {
    private val base = URI.create("https://cdn.example/show/master.m3u8")

    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480,CODECS="avc1.64001f,mp4a.40.2"
        480.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=6200000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
        1080.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=4100000,RESOLUTION=1920x1080,CODECS="hvc1.2.4.L120,mp4a.40.2"
        1080h.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
        720.m3u8
    """.trimIndent()

    @Test
    fun uncappedPicksHighestVariant() {
        val trimmed = assertNotNull(HlsMasterTrimmer.trim(master, base))
        assertEquals(1, trimmed.selectedIndex)
        assertTrue(trimmed.master.contains("https://cdn.example/show/1080.m3u8"))
        assertEquals(4, trimmed.variants.size)
    }

    @Test
    fun capPicksBestVariantAtOrBelowCap() {
        val trimmed = assertNotNull(HlsMasterTrimmer.trim(master, base, maxHeight = 720))
        assertEquals(3, trimmed.selectedIndex)
        assertTrue(trimmed.master.contains("720.m3u8"))
    }

    @Test
    fun capBelowEveryVariantFallsBackToLowest() {
        val trimmed = assertNotNull(HlsMasterTrimmer.trim(master, base, maxHeight = 360))
        assertEquals(0, trimmed.selectedIndex)
    }

    @Test
    fun explicitVariantWinsOverCap() {
        val trimmed = assertNotNull(HlsMasterTrimmer.trim(master, base, maxHeight = 480, variantIndex = 2))
        assertEquals(2, trimmed.selectedIndex)
        assertTrue(trimmed.master.contains("1080h.m3u8"))
        assertTrue(!trimmed.master.contains("/1080.m3u8"))
    }

    @Test
    fun optionsAreSortedAndNameCodecForSameHeight() {
        val trimmed = assertNotNull(HlsMasterTrimmer.trim(master, base))
        val labels = hlsQualityOptions(trimmed.variants).map { it.label }
        assertEquals(
            listOf("1080p · 6.2 Mbps · AVC", "1080p · 4.1 Mbps · HEVC", "720p · 2.5 Mbps", "480p · 800 kbps"),
            labels,
        )
    }
}
