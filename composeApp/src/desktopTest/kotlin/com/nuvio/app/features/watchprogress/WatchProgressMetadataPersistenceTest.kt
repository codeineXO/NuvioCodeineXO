package com.nuvio.app.features.watchprogress

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetadataProvenance
import com.nuvio.app.features.details.MetadataSnapshot
import com.nuvio.app.features.tracking.WatchProgressSource
import com.nuvio.app.features.watching.sync.ProgressSyncRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WatchProgressMetadataPersistenceTest {
    @Test fun provenanceSurvivesDiskRoundTripWithProgressIdentityUnchanged() {
        val original = entry().copy(metadataSnapshot = snapshot())
        val restored = WatchProgressCodec.decodeEntries(WatchProgressCodec.encodeEntries(listOf(original))).single()
        assertEquals(original.withResolvedProgressKey(), restored)
        assertEquals(snapshot(), restored.metadataSnapshot)
    }

    @Test fun olderProgressPayloadsRemainReadableWithoutInventedAttribution() {
        val payload = """{"entries":[{"contentType":"series","parentMetaId":"tt123","parentMetaType":"series",
            "videoId":"tt123:1:1","title":"Saved title","lastPositionMs":40000,"durationMs":100000,"lastUpdatedEpochMs":100}]}"""
        val restored = WatchProgressCodec.decodeEntries(payload).single()
        assertEquals("Saved title", restored.title)
        assertNull(restored.metadataSnapshot)
    }

    @Test fun subsequentPlaybackUpdatesRetainSavedSourceAndNewMetadataCanReplaceIt() {
        val saved = entry().copy(metadataSnapshot = snapshot())
        val playing = entry().copy(lastPositionMs = 50_000, lastUpdatedEpochMs = 200)
        val updated = listOf(saved).resolveIdentityForUpsert(playing)
        assertEquals(snapshot(), updated.metadataSnapshot)
        assertEquals(playing.lastPositionMs, updated.lastPositionMs)
        val replacement = snapshot().copy(provenance = snapshot().provenance.copy(sourceName = "New addon"))
        assertEquals(replacement, listOf(saved).resolveIdentityForUpsert(playing.copy(metadataSnapshot = replacement)).metadataSnapshot)
    }

    @Test fun metadataEnrichmentSavesActualSourceAndDoesNotEraseItWhenSourceIsUnknown() {
        val original = entry()
        val meta = MetaDetails("tt123", "series", "Resolved title", imdbId = "tt123", provenance = snapshot().provenance)
        val enriched = enrichWatchProgressEntry(original, meta)
        assertEquals("Example addon", enriched.metadataSnapshot?.provenance?.sourceName)
        assertEquals("tt123", enriched.metadataSnapshot?.provenance?.knownIds?.get("IMDb"))
        assertEquals(original.lastPositionMs, enriched.lastPositionMs)
        assertEquals(enriched.metadataSnapshot, enrichWatchProgressEntry(enriched, meta.copy(provenance = MetadataProvenance())).metadataSnapshot)
    }

    @Test fun providerProjectionUsesSavedSourceWithoutReplacingProviderProgressOrAddingRows() {
        val saved = entry().copy(metadataSnapshot = snapshot())
        val remote = entry().copy(title = "Trakt title", lastPositionMs = 60_000, source = WatchProgressSourceTraktPlayback)
        for (source in listOf(WatchProgressSource.TRAKT, WatchProgressSource.SIMKL)) {
            val projected = projectWatchProgressSourceEntries(source, listOf(saved, saved.copy(parentMetaId = "unrelated")), listOf(remote)).single()
            assertEquals(remote.copy(metadataSnapshot = snapshot()), projected)
        }
    }

    @Test fun sameTitleNextEpisodeCanReuseSourceButOtherTitlesAndContentTypesCannot() {
        val saved = entry().copy(metadataSnapshot = snapshot())
        val nextEpisode = entry().copy(videoId = "tt123:1:2", episodeNumber = 2)
        assertEquals(snapshot(), listOf(saved).resolveIdentityForUpsert(nextEpisode).metadataSnapshot)
        assertNull(listOf(saved).resolveIdentityForUpsert(nextEpisode.copy(parentMetaId = "tt456")).metadataSnapshot)
        assertNull(listOf(saved).resolveIdentityForUpsert(entry().copy(parentMetaType = "movie")).metadataSnapshot)
        assertFalse(saved.needsRemoteMetadataEnrichment())
    }

    @Test fun syncRefreshKeepsLocalAttributionWhileAcceptingRemotePlaybackProgress() {
        val saved = entry().copy(metadataSnapshot = snapshot())
        val remote = ProgressSyncRecord(
            contentId = saved.parentMetaId, contentType = saved.contentType, videoId = saved.videoId,
            season = saved.seasonNumber, episode = saved.episodeNumber, position = 60_000, duration = 100_000,
            lastWatched = 300, progressKey = saved.resolvedProgressKey(),
        )
        val merged = WatchProgressRepository.mergeWatchProgressEntriesPreservingUnsynced(listOf(remote), listOf(saved), emptySet()).values.single()
        assertEquals(snapshot(), merged.metadataSnapshot)
        assertEquals(remote.position, merged.lastPositionMs)
        assertEquals(remote.lastWatched, merged.lastUpdatedEpochMs)
    }

    private fun snapshot() = MetadataSnapshot("tt123", MetadataProvenance(
        sourceName = "Example addon", sourceId = "example.meta", requestedId = "tmdb:42", lookupId = "tt123",
        fetchedAtMs = 50, enrichmentSources = setOf("TMDB"), knownIds = mapOf("TMDB" to "42"),
    ))

    private fun entry() = WatchProgressEntry(
        contentType = "series", parentMetaId = "tt123", parentMetaType = "series", videoId = "tt123:1:1",
        title = "Saved title", poster = "poster", background = "background", seasonNumber = 1, episodeNumber = 1,
        lastPositionMs = 40_000, durationMs = 100_000, lastUpdatedEpochMs = 100,
    )
}
