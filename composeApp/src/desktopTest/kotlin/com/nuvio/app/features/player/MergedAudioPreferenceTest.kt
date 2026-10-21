package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class MergedAudioPreferenceTest {
    @Test
    fun untaggedPrimaryLanguageWinsOverTaggedSecondary() {
        val tracks = listOf(AudioTrack(0, "1", "English", "en"), AudioTrack(1, "2", "Japanese", null))
        assertEquals(1, findPreferredAudioTrackIndex(tracks, listOf("ja", "en")))
    }

    @Test
    fun explicitLanguageTagWinsOverMisleadingTitle() {
        val tracks = listOf(AudioTrack(0, "1", "Japanese", "en"), AudioTrack(1, "2", "Japanese", "ja"))
        assertEquals(1, findPreferredAudioTrackIndex(tracks, listOf("ja")))
        assertEquals(-1, findPreferredAudioTrackIndex(tracks.take(1), listOf("ja")))
    }
}
