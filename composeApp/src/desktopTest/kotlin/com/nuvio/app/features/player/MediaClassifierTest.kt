package com.nuvio.app.features.player

import com.nuvio.app.features.details.MetaClassificationHints
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsParser
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadStatus
import com.nuvio.app.features.streams.CachedStreamLink
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamClientResolve
import com.nuvio.app.features.streams.StreamClientResolveRaw
import com.nuvio.app.features.streams.StreamClientResolveStream
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.toPlaybackStream
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaClassifierTest {
    private val generic = MetaDetails("tt123", "series", "Title")
    private fun stream(filename: String? = null) = StreamItem(name = "Provider 1080p", addonId = "test", addonName = "Test",
        behaviorHints = StreamBehaviorHints(filename = filename))

    @Test fun animeIdentitiesBeatGenericMetadataAndNormalizeWhitespace() {
        for (prefix in listOf("kitsu:", "mal:", "anidb:", "anilist:")) {
            assertEquals(MediaContentType.ANIME, MediaClassifier.classify(generic, " series ", "  ${prefix.uppercase()}123  "))
        }
        assertEquals(MediaContentType.ANIME, MediaClassifier.classify(generic, " Anime "))
        assertEquals(MediaContentType.ANIME, MediaClassifier.classify(generic.copy(genres = listOf(" Anime "))))
    }

    @Test fun animationUsesOriginButJapaneseLiveActionDoesNotBecomeAnime() {
        for ((country, language) in listOf("JP, US" to null, "jpn" to "en", "Japan" to null,
            null to "ja-JP", null to "jpn", "US" to "en, ja_JP", null to " Japanese ")) {
            assertEquals(MediaContentType.ANIME, MediaClassifier.classify(generic.copy(genres = listOf(" Animation "), country = country, language = language)))
            assertEquals(MediaContentType.LIVE_ACTION, MediaClassifier.classify(generic.copy(country = country, language = language)))
        }
        assertEquals(MediaContentType.CARTOON, MediaClassifier.classify(generic.copy(genres = listOf("Animation"), country = "US", language = "en")))
        assertFalse(MediaClassifier.isJapaneseOrigin("NotJapan", "notJapanese"))
    }

    @Test fun cachedLinkAndManualSelectionUseIdenticalEvidence() {
        val manual = stream("[Erai-raws] Title - 01.mkv").copy(description = "Dual audio")
        val cached = CachedStreamLink("https://example.test/video", manual.streamLabel, "Test", "test", 1L,
            filename = manual.classificationFilename, releaseText = manual.classificationReleaseText)
        val expected = MediaClassifier.detectPlayback(generic, "series", "tt123", stream = manual)
        assertEquals(MediaContentType.ANIME, expected.type)
        assertEquals(expected, MediaClassifier.detectPlayback(generic, "series", "tt123", stream = cached.toPlaybackStream()))
        assertEquals(expected, MediaClassifier.detectPlayback(generic, "series", "tt123", filename = cached.filename,
            streamTitle = cached.streamName, streamSubtitle = cached.releaseText))
    }

    @Test fun releaseEvidenceIncludesDescriptionsAndResolvedFilenames() {
        val candidates = listOf(
            stream().copy(title = "[Judas] Title"),
            stream().copy(description = "[SubsPlease] Title"),
            stream().copy(clientResolve = StreamClientResolve(filename = "[EMBER] Title.mkv")),
            stream().copy(clientResolve = StreamClientResolve(stream = StreamClientResolveStream(raw = StreamClientResolveRaw(filename = "[ASW] Title.mkv")))),
            stream("episode.mkv").copy(clientResolve = StreamClientResolve(stream = StreamClientResolveStream(raw = StreamClientResolveRaw(filename = "[ASW] Title.mkv")))),
        )
        candidates.forEach { assertEquals(MediaContentType.ANIME, MediaClassifier.detectPlayback(generic, stream = it).type) }
        assertEquals(MediaContentType.LIVE_ACTION, MediaClassifier.detectPlayback(generic,
            stream = stream("Title.1080p.HEVC.10bit.Dual.Audio.mkv")).type)
    }

    @Test fun providerArraysAndCsvPreserveGenreAndOriginEvidence() {
        val meta = MetaDetailsParser.parse("""{"meta":{"id":"tt123","type":"series","name":"Title","genres":"Action, Animation","country":["US","JP"],"language":["en","ja-JP"]}}""")
        assertEquals(MediaContentType.ANIME, MediaClassifier.classify(meta))
    }

    @Test fun localizedGenresUseCanonicalEvidenceAndFreshMetadataBeatsSavedHint() {
        val canonical = generic.copy(genres = listOf("アニメーション"), classificationHints = MetaClassificationHints(isAnimation = true, country = "JP", language = "ja"))
        val animeHint = MediaClassifier.detectPlayback(canonical).toHint()
        assertEquals(MediaContentType.ANIME, MediaClassifier.detectPlayback(generic, hint = animeHint).type)
        val liveAction = generic.copy(classificationHints = MetaClassificationHints(isAnimation = false, country = "JP"))
        assertEquals(MediaContentType.LIVE_ACTION, MediaClassifier.detectPlayback(liveAction,
            filename = "[Judas] Title.mkv", hint = animeHint).type)
        assertEquals(MediaContentType.CARTOON, MediaClassifier.detectPlayback(canonical.copy(classificationHints = MetaClassificationHints(isAnimation = true, country = "US", language = "en")), hint = animeHint).type)
    }

    @Test fun fallbackAndMalformedHintsCannotBeSavedAsConfirmedEvidence() {
        assertNull(MediaClassifier.detectPlayback(generic).toHint())
        assertNull(MediaClassifier.detectPlayback(null).toHint())
        for (hint in listOf(MediaClassificationHint("anime", "TYPE_FALLBACK"), MediaClassificationHint("unknown", "ANIME_ID"),
            MediaClassificationHint("live_action", "ANIME_ID"), MediaClassificationHint("anime", "future_reason"))) {
            assertNull(MediaClassification.fromHint(hint))
            assertEquals(MediaContentType.LIVE_ACTION, MediaClassifier.detectPlayback(generic, hint = hint).type)
        }
    }

    @Test fun cloudFilesUseReleaseNamesWithoutInventingAnIdentity() {
        assertEquals(MediaContentType.ANIME, MediaClassifier.detectPlayback(null, "cloud", "torbox:Torrent:123",
            filename = "episode.mkv", streamTitle = "episode.mkv", streamSubtitle = "[SubsPlease] Collection").type)
        assertEquals(MediaContentType.UNKNOWN, MediaClassifier.detectPlayback(null, "cloud", "torbox:Torrent:123", filename = "unknown.mkv").type)
    }

    @Test fun oldSavedRecordsStillDecodeAndDownloadsKeepOriginalEvidenceOffline() {
        val legacyCache = Json.decodeFromString(CachedStreamLink.serializer(), """{"url":"https://example.test/video","streamName":"Stream","addonName":"Test","addonId":"test","cachedAtMs":1}""")
        assertNull(legacyCache.classificationHint)
        assertNull(legacyCache.releaseText)
        val item = DownloadItem("id", "series", "tt123", "series", "tt123:1:1", "Title",
            streamTitle = "1080p", providerName = "Test", sourceUrl = "https://example.test/video", fileName = "Title-S01E01.mkv",
            status = DownloadStatus.Completed, createdAtEpochMs = 1, updatedAtEpochMs = 1)
        val legacy = Json.decodeFromString(DownloadItem.serializer(), Json.encodeToString(DownloadItem.serializer(), item))
        assertNull(legacy.originalFilename)
        assertNull(legacy.classificationHint)
        val downloaded = item.copy(originalFilename = "[Judas] Title.mkv")
        assertEquals(MediaContentType.ANIME, MediaClassifier.detectPlayback(null, downloaded.parentMetaType, downloaded.parentMetaId,
            filename = downloaded.originalFilename, streamTitle = downloaded.streamTitle).type)
        val metadataHint = MediaClassifier.detectPlayback(generic.copy(genres = listOf("Anime"))).toHint()
        val restored = Json.decodeFromString(DownloadItem.serializer(), Json.encodeToString(DownloadItem.serializer(), item.copy(classificationHint = metadataHint)))
        assertEquals(MediaContentType.ANIME, MediaClassifier.detectPlayback(null, "series", "tt123", hint = restored.classificationHint).type)
    }

    @Test fun lateMetadataCorrectsFallbackAndStaysKnownAcrossEpisodeSources() {
        val tracker = PlaybackContentClassifier()
        val key = ClassificationContentKey(1, "series", "tt123")
        val fallback = MediaClassifier.detectPlayback(generic)
        assertEquals(MediaContentType.LIVE_ACTION, tracker.update(key, "source1", fallback).type)
        assertEquals(MediaContentType.ANIME, tracker.update(key, "source1", MediaClassifier.detectPlayback(generic.copy(genres = listOf("Anime")))).type)
        assertEquals(MediaContentType.ANIME, tracker.update(key, "source2", fallback).type)
        assertEquals(MediaContentType.LIVE_ACTION, tracker.update(key.copy(id = "tt456"), "source2", fallback).type)
        assertEquals(MediaContentType.LIVE_ACTION, tracker.update(key.copy(profileId = 2), "source2", fallback).type)
    }

    @Test fun releaseEvidenceIsDiscardedWhenItsSourceChanges() {
        val tracker = PlaybackContentClassifier()
        val key = ClassificationContentKey(1, "series", "tt123")
        val fallback = MediaClassifier.detectPlayback(generic)
        val release = MediaClassifier.detectPlayback(generic, filename = "[Judas] Title.mkv")
        assertEquals(MediaContentType.ANIME, tracker.update(key, "source1", release).type)
        assertEquals(MediaContentType.ANIME, tracker.update(key, "source1", fallback).type)
        assertEquals(MediaContentType.LIVE_ACTION, tracker.update(key, "source2", fallback).type)
    }

    @Test fun metadataIsAcceptedOnlyForTheRequestedContentAndProfile() {
        val key = ClassificationContentKey(1, "series", "tt123")
        assertTrue(classificationMetadataMatches(generic, key))
        assertFalse(classificationMetadataMatches(generic.copy(id = "tt456"), key))
        assertTrue(acceptsClassificationMetadata(key, key))
        assertFalse(acceptsClassificationMetadata(key, key.copy(id = "tt456")))
        assertFalse(acceptsClassificationMetadata(key, key.copy(profileId = 2)))
    }
}
