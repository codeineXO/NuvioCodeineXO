package com.nuvio.app.features.streams

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CachedStreamLink(
    val url: String = "",
    val streamName: String,
    val addonName: String,
    val addonId: String,
    val cachedAtMs: Long,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val filename: String? = null,
    val videoSize: Long? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val sources: List<String> = emptyList(),
    val bingeGroup: String? = null,
    val streamType: String? = null,
    val contentLanguage: String? = null,
    val releaseText: String? = null,
    val classificationHint: com.nuvio.app.features.player.MediaClassificationHint? = null,
)

internal expect fun epochMs(): Long

internal fun CachedStreamLink.toPlaybackStream(): StreamItem = StreamItem(
    name = streamName,
    description = releaseText,
    url = url.takeIf(String::isNotBlank),
    infoHash = infoHash,
    fileIdx = fileIdx,
    sources = sources,
    addonName = addonName,
    addonId = addonId,
    streamType = streamType,
    classificationHint = classificationHint,
    behaviorHints = StreamBehaviorHints(filename = filename, videoSize = videoSize, bingeGroup = bingeGroup),
)

object StreamLinkCacheRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun contentKey(
        type: String,
        videoId: String,
        parentMetaId: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ): String {
        val normalizedType = type.lowercase()
        return if (!parentMetaId.isNullOrBlank() && season != null && episode != null) {
            "$normalizedType|${parentMetaId.trim()}|s$season|e$episode|$videoId"
        } else {
            "$normalizedType|$videoId"
        }
    }

    fun save(
        contentKey: String,
        url: String,
        streamName: String,
        addonName: String,
        addonId: String,
        requestHeaders: Map<String, String> = emptyMap(),
        responseHeaders: Map<String, String> = emptyMap(),
        filename: String? = null,
        videoSize: Long? = null,
        infoHash: String? = null,
        fileIdx: Int? = null,
        sources: List<String> = emptyList(),
        bingeGroup: String? = null,
        streamType: String? = null,
        contentLanguage: String? = null,
        releaseText: String? = null,
        classificationHint: com.nuvio.app.features.player.MediaClassificationHint? = null,
    ) {
        if (url.isNotBlank() && url.hasLikelyExpiringPlaybackCredentials()) {
            remove(contentKey)
            return
        }

        val entry = CachedStreamLink(
            url = url,
            streamName = streamName,
            addonName = addonName,
            addonId = addonId,
            cachedAtMs = epochMs(),
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
            filename = filename,
            videoSize = videoSize,
            infoHash = infoHash,
            fileIdx = fileIdx,
            sources = sources,
            bingeGroup = bingeGroup,
            streamType = streamType,
            contentLanguage = contentLanguage,
            releaseText = releaseText,
            classificationHint = com.nuvio.app.features.player.MediaClassification.fromHint(classificationHint)?.toHint(),
        )
        val payload = json.encodeToString(CachedStreamLink.serializer(), entry)
        StreamLinkCacheStorage.saveEntry(hashedKey(contentKey), payload)
    }

    fun remove(contentKey: String) {
        StreamLinkCacheStorage.removeEntry(hashedKey(contentKey))
    }

    internal fun updateClassification(contentKey: String, sourceUrl: String, infoHash: String?, fileIdx: Int?,
        hint: com.nuvio.app.features.player.MediaClassificationHint) {
        val validated = com.nuvio.app.features.player.MediaClassification.fromHint(hint)?.toHint() ?: return
        val key = hashedKey(contentKey)
        val raw = StreamLinkCacheStorage.loadEntry(key) ?: return
        val entry = runCatching { json.decodeFromString(CachedStreamLink.serializer(), raw) }.getOrNull() ?: return
        val matches = if (infoHash != null) entry.infoHash == infoHash && entry.fileIdx == fileIdx else entry.url == sourceUrl
        if (!matches || entry.classificationHint == validated) return
        // Retain the original timestamp and credentials: learning a type must not extend a link's lifetime.
        StreamLinkCacheStorage.saveEntry(key, json.encodeToString(CachedStreamLink.serializer(), entry.copy(classificationHint = validated)))
    }

    fun getValid(contentKey: String, maxAgeMs: Long): CachedStreamLink? {
        if (maxAgeMs <= 0L) return null
        val raw = StreamLinkCacheStorage.loadEntry(hashedKey(contentKey)) ?: return null
        val entry = runCatching {
            json.decodeFromString(CachedStreamLink.serializer(), raw)
        }.getOrNull() ?: run {
            StreamLinkCacheStorage.removeEntry(hashedKey(contentKey))
            return null
        }
        val age = epochMs() - entry.cachedAtMs
        if (entry.cachedAtMs <= 0L || age > maxAgeMs) {
            StreamLinkCacheStorage.removeEntry(hashedKey(contentKey))
            return null
        }
        if (entry.url.isNotBlank() && entry.url.hasLikelyExpiringPlaybackCredentials()) {
            StreamLinkCacheStorage.removeEntry(hashedKey(contentKey))
            return null
        }
        if (entry.url.isBlank() && entry.infoHash.isNullOrBlank()) {
            StreamLinkCacheStorage.removeEntry(hashedKey(contentKey))
            return null
        }
        return entry
    }

    private fun hashedKey(contentKey: String): String {
        val hash = contentKey.fold(0L) { acc, c -> acc * 31 + c.code }.toULong()
        return "stream_link_$hash"
    }
}
