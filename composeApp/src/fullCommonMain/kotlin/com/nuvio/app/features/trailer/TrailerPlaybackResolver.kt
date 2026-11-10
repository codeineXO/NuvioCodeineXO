package com.nuvio.app.features.trailer

import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

actual object TrailerPlaybackResolver {
    private val extractor by lazy { InAppYouTubeExtractor() }
    private val cacheMutex = Mutex()
    private var playbackCache = emptyMap<String, CachedPlaybackSource>()

    actual suspend fun resolveFromYouTubeUrl(youtubeUrl: String): TrailerPlaybackSource? {
        if (youtubeUrl.isBlank()) return null
        return extractor.extractPlaybackSource(youtubeUrl)
    }

    actual suspend fun resolveSingleUrlFromYouTubeUrl(youtubeUrl: String): String? {
        if (youtubeUrl.isBlank()) return null
        return extractor.extractSingleUrl(youtubeUrl)
    }

    private data class CachedPlaybackSource(
        val source: TrailerPlaybackSource,
        val cachedAt: TimeMark,
    )
}

private val PlaybackCacheTtl = 10.minutes
