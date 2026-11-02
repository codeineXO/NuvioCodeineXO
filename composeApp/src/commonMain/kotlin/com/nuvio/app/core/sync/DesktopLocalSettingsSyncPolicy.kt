package com.nuvio.app.core.sync

import com.nuvio.app.core.ui.PosterCardStyleUiState
import com.nuvio.app.features.details.MetaEpisodeCardStyle
import com.nuvio.app.features.details.MetaScreenSettingsUiState
import com.nuvio.app.features.player.AnimeUpscalerMode
import com.nuvio.app.features.player.AnimeUpscalerOption
import com.nuvio.app.features.player.PlaybackBufferPreset
import com.nuvio.app.features.player.PlayerSettingsUiState
import com.nuvio.app.features.player.PlayerUiMode
import com.nuvio.app.features.player.SubtitleStyleState
import com.nuvio.app.features.player.SvpMotionOption
import com.nuvio.app.isDesktop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Filter the wire payload, not local storage: existing per-profile preferences need no migration. */
internal fun posterSettingsForSync(payload: String, desktop: Boolean = isDesktop): String =
    withoutLocalFields(payload, setOf("glowEnabled"), desktop)

internal fun metaSettingsForSync(payload: String, desktop: Boolean = isDesktop): String =
    withoutLocalFields(payload, setOf("episodeCardStyle", "showMetadataInfo"), desktop)

internal fun restorePosterSettingsFromSync(
    remote: String,
    local: String?,
    desktop: Boolean = isDesktop,
): String = preservingLocalFields(remote, local, setOf("glowEnabled"), desktop)

internal fun restoreMetaSettingsFromSync(
    remote: String,
    local: String?,
    desktop: Boolean = isDesktop,
): String = preservingLocalFields(remote, local, setOf("episodeCardStyle", "showMetadataInfo"), desktop)

private fun parsePayload(payload: String?): JsonObject? =
    payload?.takeIf { it.isNotBlank() }?.let {
        runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull()
    }

private fun withoutLocalFields(payload: String, keys: Set<String>, desktop: Boolean): String {
    if (!desktop) return payload
    val fields = parsePayload(payload) ?: return ""
    return JsonObject(fields.filterKeys { it !in keys }).toString()
}

private fun preservingLocalFields(remote: String, local: String?, keys: Set<String>, desktop: Boolean): String {
    if (!desktop) return remote
    val fields = parsePayload(remote).orEmpty().filterKeys { it !in keys }.toMutableMap()
    // Missing local values deliberately stay absent, so repository defaults apply.
    parsePayload(local)?.filterKeys { it in keys }?.let(fields::putAll)
    return if (fields.isEmpty()) "" else JsonObject(fields).toString()
}

/** State projections avoid reading disk before a repository's setter has persisted its new state. */
internal fun playerSettingsSyncState(state: PlayerSettingsUiState, desktop: Boolean = isDesktop): PlayerSettingsUiState =
    if (!desktop) state else state.copy(
        playerUiMode = PlayerUiMode.CODEINE_XO,
        animeUpscalerEnabled = false,
        animeUpscalerOption = AnimeUpscalerOption.OFF,
        animeUpscalerMode = AnimeUpscalerMode.FAST,
        svpMotionOption = SvpMotionOption.OFF,
        playbackBufferPreset = PlaybackBufferPreset.MEDIUM,
        audioNightModeEnabled = false,
        nvidiaRtxSuperResolutionEnabled = false,
        showSeekbarWhileSeeking = true,
        showClockInPlayer = true,
        showRemainingTime = false,
        seekPreviewEnabled = true,
        seekPreviewWhileDragging = true,
        seekPreviewWhileHovering = true,
        seekPreviewWhileKeyboardSeeking = true,
        seekPreviewThumbnailCount = 5,
        seekrApiKey = "",
        subtitleStyle = state.subtitleStyle.copy(
            fontName = SubtitleStyleState.DEFAULT.fontName,
            outlineEffect = SubtitleStyleState.DEFAULT.outlineEffect,
            textColor = SubtitleStyleState.DEFAULT.textColor,
            outlineColor = SubtitleStyleState.DEFAULT.outlineColor,
            outlineWidth = SubtitleStyleState.DEFAULT.outlineWidth,
        ),
    )

internal fun posterSettingsSyncState(state: PosterCardStyleUiState, desktop: Boolean = isDesktop): PosterCardStyleUiState =
    if (!desktop) state else state.copy(glowEnabled = true)

internal fun metaSettingsSyncState(state: MetaScreenSettingsUiState, desktop: Boolean = isDesktop): MetaScreenSettingsUiState =
    if (!desktop) state else state.copy(
        episodeCardStyle = MetaEpisodeCardStyle.Horizontal,
        showMetadataInfo = false,
    )
