package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamModelsTest {

    private val hexHash = "0123456789abcdef0123456789abcdef01234567"
    private val base32Hash = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    // -----------------------------------------------------------------------
    // isTorrentStream
    // -----------------------------------------------------------------------

    @Test
    fun `torrent scheme url in url field is detected as torrent stream`() {
        val stream = stream(url = "torrent://$hexHash")
        assertTrue(stream.isTorrentStream)
    }

    @Test
    fun `torrent scheme url with fileIdx path is detected as torrent stream`() {
        val stream = stream(url = "torrent://$hexHash/0")
        assertTrue(stream.isTorrentStream)
    }

    @Test
    fun `torrent scheme url is detected case-insensitively`() {
        val stream = stream(url = "TORRENT://$hexHash")
        assertTrue(stream.isTorrentStream)
    }

    @Test
    fun `torrent scheme url in externalUrl is detected as torrent stream`() {
        val stream = stream(externalUrl = "torrent://$hexHash")
        assertTrue(stream.isTorrentStream)
    }

    @Test
    fun `torrent scheme url with leading whitespace is detected as torrent stream`() {
        val stream = stream(url = "  torrent://$hexHash")
        assertTrue(stream.isTorrentStream)
        assertNull(stream.playableDirectUrl)
    }

    @Test
    fun `magnet url with leading whitespace is detected as torrent stream`() {
        val stream = stream(url = "\nmagnet:?xt=urn:btih:$hexHash")
        assertTrue(stream.isTorrentStream)
        assertNull(stream.playableDirectUrl)
    }

    @Test
    fun `torrent url sentinel torrent-null-string is still detected as torrent scheme`() {
        val stream = stream(url = "torrent://null")
        assertTrue(stream.isTorrentStream)
    }

    @Test
    fun `plain http url is not a torrent stream`() {
        val stream = stream(url = "https://cdn.example.com/video.mp4")
        assertFalse(stream.isTorrentStream)
    }

    @Test
    fun `plain http url with torrent in path is not a torrent stream`() {
        val stream = stream(url = "https://cdn.example.com/torrent/download.mp4")
        assertFalse(stream.isTorrentStream)
    }

    // -----------------------------------------------------------------------
    // playableDirectUrl — torrent:// and magnet: are never surfaced
    // -----------------------------------------------------------------------

    @Test
    fun `torrent scheme url yields null playableDirectUrl`() {
        val stream = stream(url = "torrent://$hexHash")
        assertNull(stream.playableDirectUrl)
    }

    @Test
    fun `torrent scheme url in externalUrl yields null playableDirectUrl`() {
        val stream = stream(externalUrl = "torrent://$hexHash")
        assertNull(stream.playableDirectUrl)
    }

    @Test
    fun `magnet url yields null playableDirectUrl`() {
        val stream = stream(url = "magnet:?xt=urn:btih:$hexHash")
        assertNull(stream.playableDirectUrl)
    }

    @Test
    fun `plain http url is surfaced as playableDirectUrl`() {
        val url = "https://cdn.example.com/video.mp4"
        val stream = stream(url = url)
        assertEquals(url, stream.playableDirectUrl)
    }

    @Test
    fun `plain http externalUrl is surfaced only for external opening`() {
        val url = "https://example.com/watch"
        val stream = stream(externalUrl = url)

        assertNull(stream.playableDirectUrl)
        assertEquals(url, stream.externalOpenUrl)
        assertTrue(stream.shouldOpenExternally)
        assertTrue(stream.isSelectableForPlayback(debridEnabled = false))
    }

    @Test
    fun `torrent url does not fall through to http externalUrl as playable`() {
        val httpUrl = "https://cdn.example.com/video.mp4"
        val stream = stream(
            url = "torrent://$hexHash",
            externalUrl = httpUrl,
        )

        assertNull(stream.playableDirectUrl)
        assertEquals(httpUrl, stream.externalOpenUrl)
        assertFalse(stream.shouldOpenExternally)
    }

    // -----------------------------------------------------------------------
    // p2pInfoHash extraction
    // -----------------------------------------------------------------------

    @Test
    fun `p2pInfoHash extracts hex hash from torrent scheme url`() {
        val stream = stream(url = "torrent://$hexHash")
        assertEquals(hexHash, stream.p2pInfoHash)
    }

    @Test
    fun `p2pInfoHash extracts base32 hash from torrent scheme url`() {
        val stream = stream(url = "torrent://$base32Hash")
        assertEquals(base32Hash, stream.p2pInfoHash)
    }

    @Test
    fun `p2pInfoHash extracts uppercase hex hash from uppercase scheme`() {
        val upperHash = hexHash.uppercase()
        val stream = stream(url = "TORRENT://$upperHash")
        assertEquals(upperHash, stream.p2pInfoHash)
    }

    @Test
    fun `p2pInfoHash stops at fileIdx path segment`() {
        val stream = stream(url = "torrent://$hexHash/3")
        assertEquals(hexHash, stream.p2pInfoHash)
    }

    @Test
    fun `p2pInfoHash stops at query separator`() {
        val stream = stream(url = "torrent://$hexHash?index=2")
        assertEquals(hexHash, stream.p2pInfoHash)
    }

    @Test
    fun `p2pInfoHash extracts hex hash from magnet url`() {
        val stream = stream(url = "magnet:?xt=urn:btih:$hexHash&dn=Test")
        assertEquals(hexHash, stream.p2pInfoHash)
    }

    @Test
    fun `p2pInfoHash extracts base32 hash from magnet url`() {
        val stream = stream(url = "magnet:?xt=urn:btih:$base32Hash&dn=Test")
        assertEquals(base32Hash, stream.p2pInfoHash)
    }

    @Test
    fun `dedicated infoHash field wins over different hash in torrent url`() {
        val dedicated = "fedcba9876543210fedcba9876543210fedcba98"
        val stream = stream(
            url = "torrent://$hexHash",
            infoHash = dedicated,
        )
        assertEquals(dedicated, stream.p2pInfoHash)
    }

    @Test
    fun `torrent-null sentinel yields null p2pInfoHash`() {
        val stream = stream(url = "torrent://null")
        assertNull(stream.p2pInfoHash)
    }

    @Test
    fun `torrent url with invalid hash length yields null p2pInfoHash`() {
        val stream = stream(url = "torrent://abcdef123456")
        assertNull(stream.p2pInfoHash)
    }

    @Test
    fun `plain http url yields null p2pInfoHash`() {
        val stream = stream(url = "https://cdn.example.com/video.mp4")
        assertNull(stream.p2pInfoHash)
    }

    // -----------------------------------------------------------------------
    // p2pFileIdx extraction
    // -----------------------------------------------------------------------

    @Test
    fun `p2pFileIdx extracts trailing index segment from torrent url`() {
        val stream = stream(url = "torrent://$hexHash/3")
        assertEquals(3, stream.p2pFileIdx)
    }

    @Test
    fun `p2pFileIdx extracts index query parameter from torrent url`() {
        val stream = stream(url = "torrent://$hexHash?index=3")
        assertEquals(3, stream.p2pFileIdx)
    }

    @Test
    fun `p2pFileIdx extracts index query parameter after other parameters`() {
        val stream = stream(url = "torrent://$hexHash?source=addon&index=12")
        assertEquals(12, stream.p2pFileIdx)
    }

    @Test
    fun `p2pFileIdx accepts fileIdx query parameter case insensitively`() {
        val stream = stream(url = "torrent://$hexHash?FILEIDX=5")
        assertEquals(5, stream.p2pFileIdx)
    }

    @Test
    fun `dedicated fileIdx field wins over torrent url segment`() {
        val stream = stream(url = "torrent://$hexHash/3", fileIdx = 7)
        assertEquals(7, stream.p2pFileIdx)
    }

    @Test
    fun `dedicated fileIdx field wins over torrent url query`() {
        val stream = stream(url = "torrent://$hexHash?index=3", fileIdx = 7)
        assertEquals(7, stream.p2pFileIdx)
    }

    @Test
    fun `client resolve fileIdx is used when stream fileIdx is missing`() {
        val stream = stream(
            infoHash = hexHash,
            clientResolve = StreamClientResolve(infoHash = hexHash, fileIdx = 4),
        )
        assertEquals(4, stream.p2pFileIdx)
    }

    @Test
    fun `p2pFileIdx is null without a path segment`() {
        val stream = stream(url = "torrent://$hexHash")
        assertNull(stream.p2pFileIdx)
    }

    @Test
    fun `p2pFileIdx is null for non-numeric path segment`() {
        val stream = stream(url = "torrent://$hexHash/name.mkv")
        assertNull(stream.p2pFileIdx)
    }

    @Test
    fun `p2pFileIdx ignores query parameters`() {
        val stream = stream(url = "torrent://$hexHash/2?foo=bar")
        assertEquals(2, stream.p2pFileIdx)
    }

    // -----------------------------------------------------------------------
    // Parser integration — torrent:// in JSON url field
    // -----------------------------------------------------------------------

    @Test
    fun `parser preserves torrent scheme url and stream is correctly classified`() {
        val streams = StreamParser.parse(
            payload = """
                {
                  "streams": [
                    {
                      "url": "torrent://$hexHash",
                      "name": "1080p"
                    }
                  ]
                }
            """.trimIndent(),
            addonName = "Addon",
            addonId = "addon.id",
        )

        val stream = streams.single()
        assertTrue(stream.isTorrentStream)
        assertNull(stream.playableDirectUrl)
        assertEquals(hexHash, stream.p2pInfoHash)
    }

    @Test
    fun `parser keeps normal http stream playable and not torrent`() {
        val streams = StreamParser.parse(
            payload = """
                {
                  "streams": [
                    {
                      "url": "https://cdn.example.com/video.mp4",
                      "name": "1080p"
                    }
                  ]
                }
            """.trimIndent(),
            addonName = "Addon",
            addonId = "addon.id",
        )

        val stream = streams.single()
        assertFalse(stream.isTorrentStream)
        assertEquals("https://cdn.example.com/video.mp4", stream.playableDirectUrl)
        assertNull(stream.p2pInfoHash)
    }

    @Test
    fun `parser keeps externalUrl as external target instead of playable URL`() {
        val streams = StreamParser.parse(
            payload = """
                {
                  "streams": [
                    {
                      "externalUrl": "https://megogo.net/ua/search-extended?query=Barbie",
                      "name": "Watch on Megogo"
                    }
                  ]
                }
            """.trimIndent(),
            addonName = "Ukrainian Streams",
            addonId = "addon.ukrainian.streams",
        )

        val stream = streams.single()
        assertNull(stream.playableDirectUrl)
        assertEquals("https://megogo.net/ua/search-extended?query=Barbie", stream.externalOpenUrl)
        assertTrue(stream.shouldOpenExternally)
        assertTrue(stream.isSelectableForPlayback(debridEnabled = false))
    }

    // -----------------------------------------------------------------------
    // parseStreamSize & resolvedVideoSize
    // -----------------------------------------------------------------------

    @Test
    fun `parseStreamSize parses size with emoji prefix`() {
        val gib = 1024L * 1024L * 1024L
        val mib = 1024L * 1024L
        assertEquals((8.45 * gib).toLong(), parseStreamSize("Inception.2010.1080p.BluRay.x264\n💾 8.45 GB ⚙️ 24\nTorrentio"))
        assertEquals((2.3 * gib).toLong(), parseStreamSize("Movie\n💾 2.3 GB"))
        assertEquals(850L * mib, parseStreamSize("Episode\n💾 850 MB"))
        assertEquals((1.2 * gib).toLong(), parseStreamSize("💾 1.2 GiB"))
        assertEquals(550L * mib, parseStreamSize("💾 550 MiB"))
        assertEquals((1.5 * gib * 1024L).toLong(), parseStreamSize("💾 1.5 TB"))
        assertEquals((1.45 * gib).toLong(), parseStreamSize("💾 1,45 GB"))
    }

    @Test
    fun `parseStreamSize parses bracketed and labeled sizes`() {
        val gib = 1024L * 1024L * 1024L
        val mib = 1024L * 1024L
        assertEquals((2.4 * gib).toLong(), parseStreamSize("Movie.2024.1080p [2.4 GB]"))
        assertEquals((2.4 * gib).toLong(), parseStreamSize("Movie.2024.1080p (2.4GB)"))
        assertEquals(450L * mib, parseStreamSize("Show S01E01 [450 MB]"))
        assertEquals((2.4 * gib).toLong(), parseStreamSize("Size: 2.4 GB"))
        assertEquals((1.25 * gib).toLong(), parseStreamSize("File Size: 1.25 GB"))
        assertEquals((2.45 * gib).toLong(), parseStreamSize("1080p | 2.45 GB | 5.1"))
    }

    @Test
    fun `parseStreamSize returns null for non-size text`() {
        assertNull(parseStreamSize(null))
        assertNull(parseStreamSize(""))
        assertNull(parseStreamSize("   "))
        assertNull(parseStreamSize("Movie (2024) 1080p 5.1 CH"))
        assertNull(parseStreamSize("H.264 10bit HDR"))
        assertNull(parseStreamSize("S01E02 720p"))
    }

    @Test
    fun `resolvedVideoSize falls back through available sources`() {
        val gib = 1024L * 1024L * 1024L
        val baseStream = stream(url = "https://example.com/video.mp4")

        // 1. Title/Description fallback
        val textStream = baseStream.copy(description = "Movie 1080p\n💾 4.5 GB")
        assertEquals((4.5 * gib).toLong(), textStream.resolvedVideoSize)

        // 2. Cached size overrides text
        val cachedStream = textStream.copy(
            debridCacheStatus = StreamDebridCacheStatus(
                providerId = "rd",
                providerName = "RealDebrid",
                state = StreamDebridCacheState.CACHED,
                cachedSize = 5_000_000_000L,
            ),
        )
        assertEquals(5_000_000_000L, cachedStream.resolvedVideoSize)

        // 3. BehaviorHints videoSize overrides cached size
        val hintsStream = cachedStream.copy(
            behaviorHints = StreamBehaviorHints(videoSize = 6_000_000_000L),
        )
        assertEquals(6_000_000_000L, hintsStream.resolvedVideoSize)
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private fun stream(
        url: String? = null,
        infoHash: String? = null,
        fileIdx: Int? = null,
        externalUrl: String? = null,
        clientResolve: StreamClientResolve? = null,
    ): StreamItem = StreamItem(
        url = url,
        infoHash = infoHash,
        fileIdx = fileIdx,
        externalUrl = externalUrl,
        clientResolve = clientResolve,
        addonName = "TestAddon",
        addonId = "test.addon",
    )
}
