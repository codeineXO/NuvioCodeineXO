package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.features.cloud.CloudLibraryContentType
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.streams.StreamItem

internal val PlayerScreenRuntime.classificationContentKey: ClassificationContentKey
    get() = ClassificationContentKey(profileId, parentMetaType, parentMetaId)

internal fun classificationMetadataMatches(meta: MetaDetails?, key: ClassificationContentKey): Boolean =
    meta != null && meta.id.trim() == key.id.trim() && meta.type.trim().equals(key.type.trim(), true)

internal fun acceptsClassificationMetadata(requested: ClassificationContentKey, current: ClassificationContentKey): Boolean =
    requested == current

internal val PlayerScreenRuntime.classificationMeta: MetaDetails?
    get() {
        if (parentMetaType == CloudLibraryContentType) return null
        val key = classificationContentKey
        val candidates = listOfNotNull(
            metaUiState.meta?.takeIf { classificationMetadataMatches(it, key) },
            playerMeta?.takeIf { playerMetaContentKey == key || classificationMetadataMatches(it, key) },
        )
        // A sparse screen snapshot must not hide richer metadata fetched for playback.
        return candidates.maxByOrNull { meta ->
            when {
                meta.classificationHints?.isAnimation != null -> 2
                MediaClassifier.detectPlayback(meta, key.type, key.id).isConfirmed -> 1
                else -> 0
            }
        } ?: MetaDetailsRepository.peek(parentMetaType, parentMetaId)
    }

internal fun PlayerScreenRuntime.detectContentClassification(stream: StreamItem? = activeStream): MediaClassification =
    MediaClassifier.detectPlayback(
        meta = classificationMeta,
        fallbackItemType = parentMetaType,
        fallbackItemId = parentMetaId,
        stream = stream,
        filename = if (stream === activeStream) activeSourceFilename ?: activeTorrentFilename else null,
        streamTitle = if (stream === activeStream) activeStreamTitle else null,
        streamSubtitle = if (stream === activeStream) activeStreamSubtitle else null,
        hint = if (stream === activeStream) activeClassificationHint else stream?.classificationHint,
    )

internal fun PlayerScreenRuntime.refreshContentClassification() {
    val key = classificationContentKey
    val result = contentClassifier.update(key, "$activePlaybackIdentity|$activeVideoId", detectContentClassification())
    mediaClassification = result
    val logKey = Triple(key, result, args.playbackOrigin)
    if (lastLoggedClassification != logKey) {
        lastLoggedClassification = logKey
        Logger.withTag("MediaClassification").d {
            "profile=${key.profileId} contentIdHash=${key.id.hashCode()} type=${result.type.value} " +
                "reason=${result.reason.name} path=${args.playbackOrigin}"
        }
    }
    // Also update a newly selected source when the title's classification hasn't changed.
    val hint = result.toHint() ?: return
    if (activeSourceUrl.startsWith("file:")) {
        com.nuvio.app.features.downloads.DownloadsRepository.updatePlaybackClassification(activeSourceUrl, hint)
    } else if (playerSettingsUiState.streamReuseLastLinkEnabled && parentMetaType != CloudLibraryContentType) {
        val cacheKey = com.nuvio.app.features.streams.StreamLinkCacheRepository.contentKey(
            contentType ?: parentMetaType, activeVideoId ?: return, parentMetaId, activeSeasonNumber, activeEpisodeNumber)
        com.nuvio.app.features.streams.StreamLinkCacheRepository.updateClassification(
            cacheKey, activeSourceUrl, activeTorrentInfoHash, activeTorrentFileIdx, hint)
    }
}
