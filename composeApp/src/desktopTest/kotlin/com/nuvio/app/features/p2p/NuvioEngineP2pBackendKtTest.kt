package com.nuvio.app.features.p2p

import com.nuvio.app.features.player.DesktopBufferPreset
import com.nuvio.engine.NuvioTorrentProfile
import com.nuvio.engine.NuvioUploadMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NuvioEngineP2pBackendKtTest {

    @Test
    fun `nuvioEngineWindowBytes maps presets correctly`() {
        assertEquals(64L * 1024L * 1024L, nuvioEngineWindowBytes(DesktopBufferPreset.Metered))
        assertEquals(128L * 1024L * 1024L, nuvioEngineWindowBytes(DesktopBufferPreset.LowData))
        assertEquals(256L * 1024L * 1024L, nuvioEngineWindowBytes(DesktopBufferPreset.Balanced))
        assertEquals(512L * 1024L * 1024L, nuvioEngineWindowBytes(DesktopBufferPreset.Resilient))
    }

    @Test
    fun `buildNuvioEngineConfig maps settings and directories accurately`() {
        val stateDir = File("build/tmp/test-p2p-state")
        val cacheDir = File("build/tmp/test-p2p-cache")

        val config = buildNuvioEngineConfig(
            stateDirectory = stateDir,
            cacheDirectory = cacheDir,
            uploadEnabled = true,
            torrentProfile = P2pTorrentProfile.BALANCED,
            diskCacheCapacityBytes = 1024L * 1024L * 1024L,
            windowBytes = 256L * 1024L * 1024L,
        )

        assertEquals(stateDir, config.dataDirectory)
        assertEquals(cacheDir, config.cacheDirectory)
        assertEquals(256L * 1024L * 1024L, config.memoryCacheCapacityBytes)
        assertEquals(1024L * 1024L * 1024L, config.diskCacheCapacityBytes)
        assertEquals(NuvioTorrentProfile.Balanced, config.torrentProfile)
        assertEquals(NuvioUploadMode.Unlimited, config.uploadMode)
        assertEquals(0, config.streamInactivityTimeoutMilliseconds)
    }

    @Test
    fun `metadataWaitVerdict detects progress and timeouts correctly`() {
        // Normal wait within limits
        val waiting = metadataWaitVerdict(
            waitedMs = 5_000L,
            stalledForMs = 2_000L,
            knownPeers = 3L,
        )
        assertNull(waiting)

        // General timeout at or after 60s
        val generalTimeout = metadataWaitVerdict(
            waitedMs = 60_000L,
            stalledForMs = 0L,
            knownPeers = 5L,
        )
        assertEquals("Torrent metadata did not arrive within 60s", generalTimeout)

        // Stalled with 0 known peers
        val zeroPeersStall = metadataWaitVerdict(
            waitedMs = 25_000L,
            stalledForMs = 16_000L,
            knownPeers = 0L,
        )
        assertEquals("No peers found for this torrent", zeroPeersStall)

        // Stalled with unreachable peers
        val unreachablePeersStall = metadataWaitVerdict(
            waitedMs = 25_000L,
            stalledForMs = 16_000L,
            knownPeers = 4L,
        )
        assertEquals("No reachable peers for this torrent (4 found, none answered)", unreachablePeersStall)
    }

    @Test
    fun `unexpectedStreamStopError creates formatted error message on active stream`() {
        val error = unexpectedStreamStopError(
            requestId = 0L,
            eventStreamId = "stream-1",
            currentStreamId = "stream-1",
            message = "Socket reset",
            fallbackMessage = "Playback stopped unexpectedly",
        )
        assertEquals("Socket reset", error?.message)

        // If requestId != 0L, it was expected
        val expected = unexpectedStreamStopError(
            requestId = 10L,
            eventStreamId = "stream-1",
            currentStreamId = "stream-1",
            message = "Socket reset",
            fallbackMessage = "Playback stopped unexpectedly",
        )
        assertNull(expected)
    }

    @Test
    fun `selectTorrentsToPrune returns empty when within budget`() {
        val entry1 = TorrentCacheEntry("hash1", File("hash1"), 500L, 1000L)
        val entry2 = TorrentCacheEntry("hash2", File("hash2"), 500L, 2000L)
        val result = selectTorrentsToPrune(
            entries = listOf(entry1, entry2),
            currentTotalBytes = 1000L,
            capacityBytes = 2000L,
            activeTorrentId = null,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `selectTorrentsToPrune selects oldest inactive torrents first until within capacity`() {
        val entryOld = TorrentCacheEntry("hash_old", File("hash_old"), 600L, 1000L)
        val entryMid = TorrentCacheEntry("hash_mid", File("hash_mid"), 600L, 2000L)
        val entryNew = TorrentCacheEntry("hash_new", File("hash_new"), 600L, 3000L)
        val result = selectTorrentsToPrune(
            entries = listOf(entryMid, entryNew, entryOld),
            currentTotalBytes = 1800L,
            capacityBytes = 1000L,
            activeTorrentId = null,
        )
        // Needs to prune down to <= 1000L. Pruning entryOld leaves 1200L, then entryMid leaves 600L <= 1000L.
        assertEquals(2, result.size)
        assertEquals("hash_old", result[0].torrentId)
        assertEquals("hash_mid", result[1].torrentId)
    }

    @Test
    fun `selectTorrentsToPrune preserves active torrent from being selected for pruning`() {
        val entryActiveOld = TorrentCacheEntry("hash_active", File("hash_active"), 800L, 1000L)
        val entryInactiveNew = TorrentCacheEntry("hash_inactive", File("hash_inactive"), 800L, 2000L)
        val result = selectTorrentsToPrune(
            entries = listOf(entryActiveOld, entryInactiveNew),
            currentTotalBytes = 1600L,
            capacityBytes = 800L,
            activeTorrentId = "hash_active",
        )
        assertEquals(1, result.size)
        assertEquals("hash_inactive", result[0].torrentId)
    }

    @Test
    fun `selectTorrentsToPrune selects all inactive torrents when capacity is 0L`() {
        val entry1 = TorrentCacheEntry("hash1", File("hash1"), 300L, 1000L)
        val entry2 = TorrentCacheEntry("hash2", File("hash2"), 400L, 2000L)
        val result = selectTorrentsToPrune(
            entries = listOf(entry1, entry2),
            currentTotalBytes = 700L,
            capacityBytes = 0L,
            activeTorrentId = null,
        )
        assertEquals(2, result.size)
    }

    @Test
    fun `selectTorrentsToPrune protects single torrent when activeTorrentId is null and capacity is positive`() {
        val entry1 = TorrentCacheEntry("hash1", File("hash1"), 3000L, 1000L)
        val result = selectTorrentsToPrune(
            entries = listOf(entry1),
            currentTotalBytes = 3000L,
            capacityBytes = 2000L,
            activeTorrentId = null,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseTorrentNameFromResume parses torrent name from bencoded resume file`() {
        val tempResume = File("build/tmp/test-resume-${System.nanoTime()}.resume")
        try {
            val content = "d11:active_timei10e4:name12:MyMovie.2024e"
            tempResume.writeBytes(content.toByteArray(Charsets.UTF_8))
            val name = parseTorrentNameFromResume(tempResume)
            assertEquals("MyMovie.2024", name)
        } finally {
            tempResume.delete()
        }
    }

    @Test
    fun `scanTorrentCacheEntries maps directory names to infohash via resume directory`() {
        val tempRoot = File("build/tmp/test-resume-mapping-${System.nanoTime()}")
        try {
            val cacheDir = File(tempRoot, "payload").apply { mkdirs() }
            val resumeDir = File(tempRoot, "resume").apply { mkdirs() }

            val torrentDir = File(cacheDir, "MyMovie.2024").apply { mkdirs() }
            File(torrentDir, "movie.mkv").writeBytes(ByteArray(256))

            val resumeFile = File(resumeDir, "111b3864ea3a27055f66c1a3e54d7eef11bf93aa.resume")
            resumeFile.writeBytes("d11:active_timei10e4:name12:MyMovie.2024e".toByteArray(Charsets.UTF_8))

            val entries = scanTorrentCacheEntries(cacheDir, resumeDir)
            assertEquals(1, entries.size)
            assertEquals("111b3864ea3a27055f66c1a3e54d7eef11bf93aa", entries[0].torrentId)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `scanTorrentCacheEntries scans nested payload directory correctly`() {
        val tempDir = File("build/tmp/test-cache-scan-${System.nanoTime()}")
        try {
            val nestedPayload = File(tempDir, "payload")
            val torrentDir = File(nestedPayload, "a1b2c3d4e5")
            torrentDir.mkdirs()
            val sampleFile = File(torrentDir, "video.mp4")
            sampleFile.writeBytes(ByteArray(128))

            val entries = scanTorrentCacheEntries(tempDir)
            assertEquals(1, entries.size)
            assertEquals("a1b2c3d4e5", entries[0].torrentId)
            assertEquals(128L, entries[0].sizeBytes)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

