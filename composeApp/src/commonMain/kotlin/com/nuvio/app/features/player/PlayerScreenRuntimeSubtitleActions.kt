package com.nuvio.app.features.player

import com.nuvio.app.core.i18n.localizedNoSubtitleLinesFound
import com.nuvio.app.core.i18n.localizedSubtitleLinesLoadError
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.autosync.AutoSyncPlayerController
import com.nuvio.app.features.streams.StreamLinkCacheRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun PlayerScreenRuntime.fetchAddonSubtitlesForActiveItem() {
    if (activeSourceUrl.startsWith("file:") && externalSubtitles.isNotEmpty()) {
        SubtitleRepository.clear()
        return
    }
    // Without a network the addon lookup can only hang; keep the local state untouched.
    if (isNetworkOffline()) return
    val type = activeAddonSubtitleType.takeIf { it.isNotBlank() } ?: return
    val videoId = activeVideoId?.takeIf { it.isNotBlank() } ?: return
    val extras = activeSubtitleExtras
    // For P2P streams activeSourceUrl is a torrent:// sentinel — the real HTTP URL
    // comes from the local engine once the stream is resolved, mirroring NuvioTV where
    // currentStreamUrl is set to the local engine URL for torrents.
    val hashableUrl = p2pResolvedSourceUrl ?: activeSourceUrl
    if (extras?.videoHash == null && hashableUrl.isNotBlank() &&
        (hashableUrl.startsWith("http://", true) || hashableUrl.startsWith("https://", true))
    ) {
        // Compute hash lazily — same pattern as NuvioTV.
        SubtitleRepository.fetchAddonSubtitlesWithHashComputation(
            type = type,
            videoId = videoId,
            extras = extras,
            sourceUrl = hashableUrl,
            sourceHeaders = if (p2pResolvedSourceUrl != null) emptyMap() else activeSourceHeaders,
            onExtrasUpdated = { enriched ->
                activeSubtitleExtras = enriched
                persistComputedVideoHash(enriched)
            },
        )
    } else {
        SubtitleRepository.fetchAddonSubtitles(type, videoId, extras)
    }
}

/**
 * After lazy hash computation succeeds, patch the *existing* cached stream link
 * entry with the newly computed hash. Uses a read-modify-write so that we never
 * create a new entry (respecting server-stream exclusions and saveForReuse=false)
 * and never reset cachedAtMs.
 */
private fun PlayerScreenRuntime.persistComputedVideoHash(enriched: SubtitleRequestExtras) {
    if (!playerSettingsUiState.streamReuseLastLinkEnabled) return
    val vid = activeVideoId ?: return
    val cacheKey = StreamLinkCacheRepository.contentKey(
        type = contentType ?: parentMetaType,
        videoId = vid,
        parentMetaId = parentMetaId,
        season = activeSeasonNumber,
        episode = activeEpisodeNumber,
    )
    StreamLinkCacheRepository.updateVideoHash(cacheKey, enriched.videoHash, enriched.videoSize)
}

internal fun PlayerScreenRuntime.setSubtitleDelay(delayMs: Int) {
    (playerController as? AutoSyncPlayerController)?.cancelForManualSubtitleDelay() // AutoSync hook
    val clamped = delayMs.coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS)
    subtitleDelayMs = clamped
    PlayerTrackPreferenceStorage.saveSubtitleDelayMs(playbackSession.videoId, clamped)
    playerController?.setSubtitleDelayMs(clamped)
}

internal fun PlayerScreenRuntime.subtitleAutoSyncState(secondary: Boolean): SubtitleAutoSyncUiState =
    if (secondary) secondarySubtitles.syncState else subtitleAutoSyncState

private fun PlayerScreenRuntime.updateSubtitleAutoSyncState(secondary: Boolean, state: SubtitleAutoSyncUiState) {
    if (secondary) secondarySubtitles.syncState = state else subtitleAutoSyncState = state
}

private fun PlayerScreenRuntime.subtitleCueSource(secondary: Boolean): Pair<String, Boolean>? {
    val subtitle = (if (secondary) secondarySubtitles.active?.addon else selectedAddonSubtitle) ?: return null
    val retimedPath = secondarySubtitles.retimedPath.takeIf { secondary }
    val local = retimedPath != null || subtitle.language == SubtitleImportedLanguageKey ||
        subtitle.url.startsWith("file:", ignoreCase = true)
    return (retimedPath ?: subtitle.url) to local
}

internal fun PlayerScreenRuntime.loadSubtitleAutoSyncCues(force: Boolean = false, secondary: Boolean = false) {
    val source = subtitleCueSource(secondary) ?: return
    val state = subtitleAutoSyncState(secondary)
    if (state.isLoading || (!force && state.cues.isNotEmpty())) return
    val (url, local) = source
    if (!local && isNetworkOffline()) return
    val videoId = playbackSession.videoId
    val headers = sanitizePlaybackHeaders(activeSourceHeaders)
    updateSubtitleAutoSyncState(secondary, state.copy(isLoading = true, errorMessage = null))
    scope.launch {
        val result = runCatching {
            val body = if (local) SubtitleImportPlatform.readSubtitleText(url) else httpGetTextWithHeaders(url, headers)
            PlayerSubtitleCueParser.parse(body, url)
        }
        (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        if (source != subtitleCueSource(secondary) || videoId != playbackSession.videoId ||
            !subtitleAutoSyncState(secondary).isLoading
        ) return@launch
        val current = subtitleAutoSyncState(secondary)
        val cues = result.getOrNull()
        val error = when {
            cues == null -> result.exceptionOrNull()?.message ?: localizedSubtitleLinesLoadError()
            cues.isEmpty() -> localizedNoSubtitleLinesFound()
            else -> null
        }
        updateSubtitleAutoSyncState(secondary, current.copy(
            cues = cues ?: current.cues,
            isLoading = false,
            errorMessage = error,
        ))
    }
}

internal fun PlayerScreenRuntime.captureSubtitleAutoSyncTime(secondary: Boolean = false) {
    if (secondary) secondarySubtitles.cancelAutomaticSync = true
    updateSubtitleAutoSyncState(secondary, subtitleAutoSyncState(secondary).copy(
        capturedPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L),
        errorMessage = null,
    ))
    loadSubtitleAutoSyncCues(secondary = secondary)
}

internal fun PlayerScreenRuntime.applySubtitleAutoSyncCue(cue: SubtitleSyncCue, secondary: Boolean = false) {
    val capturedPositionMs = subtitleAutoSyncState(secondary).capturedPositionMs ?: return
    val newDelayMs = (capturedPositionMs - cue.startTimeMs - SUBTITLE_AUTO_SYNC_REACTION_COMPENSATION_MS)
        .toInt()
        .coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS)
    if (secondary) setSecondarySubtitleDelay(newDelayMs) else setSubtitleDelay(newDelayMs)
}
