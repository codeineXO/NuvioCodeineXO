package com.nuvio.app.features.streams

import kotlin.test.*

class FilenameEpisodeMatcherTest {
    @Test
    fun nextSeasonKeepsReleaseIdentityAndPadding() {
        assertEquals("Show.S02E01.1080p.Group.mkv", FilenameEpisodeMatcher.buildNextFilename(
            "Show.S01E12.1080p.Group.mkv", 1, 12, 2, 1))
        assertEquals("Show.02x01.mkv", FilenameEpisodeMatcher.buildNextFilename("Show.01x12.mkv", 1, 12, 2, 1))
        assertEquals("Show.EP 004.mkv", FilenameEpisodeMatcher.buildNextFilename("Show.EP 003.mkv", null, 3, null, 4))
    }

    @Test
    fun missingOrUnrelatedMetadataDoesNotProduceAMatch() {
        assertNull(FilenameEpisodeMatcher.extractSeasonEpisode("Show.1920x1080.mkv"))
        assertNull(FilenameEpisodeMatcher.buildNextFilename("Show.S01E03.mkv", 1, 4, 1, 5))
        assertFalse(FilenameEpisodeMatcher.matches(null, "Show.S01E03.mkv"))
        assertFalse(FilenameEpisodeMatcher.matches("", ""))
        assertTrue(FilenameEpisodeMatcher.matches("SHOW.S01E03.MKV", "Show.S01E03.mkv"))
    }
}
