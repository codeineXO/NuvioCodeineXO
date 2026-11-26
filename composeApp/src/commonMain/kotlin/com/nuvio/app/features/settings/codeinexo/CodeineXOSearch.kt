package com.nuvio.app.features.settings.codeinexo

import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.isDesktop
import com.nuvio.app.isWindows
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/** One searchable row of a CodeineXO section. [isVisible] mirrors the row's own condition in its section file. */
internal class CodeineXOSearchRow(
    val key: String,
    val titleRes: StringResource,
    val descriptionRes: StringResource? = null,
    val isVisible: () -> Boolean = { true },
)

private fun row(
    key: String,
    titleRes: StringResource,
    descriptionRes: StringResource? = null,
    isVisible: () -> Boolean = { true },
) = CodeineXOSearchRow(key, titleRes, descriptionRes, isVisible)

private val desktopOnly: () -> Boolean = { isDesktop }
private val windowsOnly: () -> Boolean = { isWindows }

/** Search entries for every row of each section, in the order the section shows them. */
internal val CodeineXOSection.searchRows: List<CodeineXOSearchRow>
    get() = when (this) {
        CodeineXOSection.Appearance -> listOf(
            row("xo-app-scale", Res.string.settings_appearance_app_scale, isVisible = desktopOnly),
            row("xo-surface-style", Res.string.settings_surface_style),
            row("xo-aura", Res.string.xo_aura_title, Res.string.xo_aura_description),
            row("xo-aura-customize", Res.string.aura_customize),
            row("xo-poster-glow", Res.string.settings_poster_glow, Res.string.settings_poster_glow_description, desktopOnly),
        )
        CodeineXOSection.Navigation -> listOf(
            row("xo-navigation-feel", Res.string.settings_navigation_feel, Res.string.settings_navigation_feel_description),
            row("xo-keyboard-navigation", Res.string.settings_keyboard_navigation, Res.string.settings_keyboard_navigation_description),
        )
        CodeineXOSection.HomePosters -> listOf(
            row("xo-app-interface", Res.string.settings_app_interface),
            row("xo-poster-gutter-size", Res.string.settings_poster_gutter_size, isVisible = desktopOnly),
            row("xo-hide-labels", Res.string.settings_poster_hide_labels),
            row("xo-hide-year", Res.string.settings_poster_hide_year),
            row("xo-always-animate-collections", Res.string.settings_homescreen_always_animate_collections),
        )
        CodeineXOSection.DetailsPage -> listOf(
            row("xo-metadata-info", Res.string.settings_show_metadata_info, Res.string.settings_show_metadata_info_description),
            row("xo-episode-cards", Res.string.settings_meta_episode_cards, Res.string.settings_meta_episode_cards_description),
        )
        CodeineXOSection.PlayerInterface -> listOf(
            row("xo-player-ui", Res.string.settings_playback_player_ui),
            row("xo-player-ui-scale", Res.string.settings_playback_player_ui_scale, isVisible = desktopOnly),
            row("xo-show-clock", Res.string.settings_playback_show_clock_in_player),
            row("xo-p2p-show-stats", Res.string.settings_p2p_show_stats_title, isVisible = { P2pSettingsRepository.isVisible }),
            row("xo-seekbar-while-seeking", Res.string.settings_playback_show_seekbar_while_seeking),
        )
        CodeineXOSection.SeekPreviews -> listOf(
            row("xo-seek-previews", Res.string.settings_playback_seek_previews),
            row("xo-seekr-api-key", Res.string.settings_playback_seekr_api_key),
            row("xo-seek-preview-dragging", Res.string.settings_seek_preview_dragging),
            row("xo-seek-preview-hovering", Res.string.settings_seek_preview_hovering),
            row("xo-seek-preview-keyboard", Res.string.settings_seek_preview_keyboard),
            row("xo-seek-preview-count", Res.string.settings_seek_preview_count),
            row("xo-seek-preview-size", Res.string.settings_seek_preview_size),
        )
        CodeineXOSection.ViewingComfort -> listOf(
            row("xo-eye-filter", Res.string.xo_eye_filter_title, Res.string.xo_eye_filter_description),
            row("xo-eye-filter-color", Res.string.xo_eye_filter_color),
            row("xo-eye-filter-opacity", Res.string.xo_eye_filter_opacity),
            row("xo-frosted-matte", Res.string.xo_frosted_matte_title, Res.string.xo_frosted_matte_description),
        )
        CodeineXOSection.PlaybackEngine -> listOf(
            row("xo-playback-buffer", Res.string.playback_buffer_title),
            row("xo-hls-max-resolution", Res.string.hls_max_resolution_title),
        )
        CodeineXOSection.Video -> listOf(
            row("xo-anime-upscaler", Res.string.xo_anime_upscaler, Res.string.xo_anime_upscaler_search),
            row("xo-upscaler-mode", Res.string.xo_upscaler_mode),
            row("xo-motion-interpolation", Res.string.xo_motion_interpolation, Res.string.xo_motion_interpolation_search),
            row("xo-mpv-config-enabled", Res.string.settings_playback_mpv_config_enabled, isVisible = windowsOnly),
            row("xo-mpv-config", Res.string.settings_playback_mpv_config, Res.string.settings_playback_mpv_config_description, windowsOnly),
        )
        CodeineXOSection.Audio -> listOf(
            row("xo-audio-night-mode", Res.string.settings_playback_audio_night_mode, Res.string.settings_playback_audio_night_mode_desc),
        )
        CodeineXOSection.Anime -> listOf(
            row("xo-anime-filler-detection", Res.string.xo_anime_filler_detection_title, Res.string.xo_anime_filler_detection_description),
            row("xo-hide-anime-filler", Res.string.xo_hide_anime_filler_title, Res.string.xo_hide_anime_filler_description),
        )
        CodeineXOSection.Streams -> listOf(
            row("xo-stream-sort", Res.string.stream_filter_settings_sort),
            row("xo-stream-filters", Res.string.stream_filter_settings_filters),
            row("xo-stream-autoplay-filters", Res.string.stream_filter_autoplay_respects),
            row("xo-stream-reset", Res.string.stream_filter_reset_all),
        )
        CodeineXOSection.P2p -> listOf(
            row("xo-p2p", Res.string.settings_p2p_title, Res.string.settings_p2p_subtitle),
            row("xo-p2p-hide-stats", Res.string.settings_p2p_hide_stats_title),
            row("xo-p2p-profile", Res.string.settings_p2p_profile_title),
            row("xo-p2p-cache-size", Res.string.settings_p2p_cache_size_title),
            row("xo-p2p-clear-cache", Res.string.settings_p2p_clear_cache_title),
        )
        CodeineXOSection.Subtitles -> listOf(
            row("xo-subtitle-font", Res.string.xo_subtitle_font),
            row("xo-subtitle-text-color", Res.string.settings_playback_subtitle_text_color),
            row("xo-subtitle-outline-style", Res.string.xo_outline_style),
            row("xo-subtitle-outline-thickness", Res.string.xo_outline_thickness),
            row("xo-subtitle-outline-color", Res.string.settings_playback_subtitle_outline_color, Res.string.compose_player_box_color),
            row("xo-subtitle-outline-opacity", Res.string.compose_player_outline_opacity, Res.string.compose_player_box_opacity),
        )
        CodeineXOSection.Discord -> listOf(
            row("xo-discord-rich-presence", Res.string.settings_advanced_discord_rich_presence),
            row("xo-discord-activity-type", Res.string.settings_advanced_discord_activity_type),
            row("xo-discord-activity-mode", Res.string.settings_advanced_discord_activity_mode),
            row("xo-discord-full-poster", Res.string.settings_advanced_discord_full_poster),
            row("xo-discord-episode-small-image", Res.string.settings_advanced_discord_episode_small_image),
            row("xo-discord-episode-name", Res.string.settings_advanced_discord_episode_name),
        )
        CodeineXOSection.Downloads -> listOf(
            row("xo-download-location", Res.string.downloads_choose_directory),
            row("xo-download-location-default", Res.string.downloads_location_default),
        )
        CodeineXOSection.DebugLogging -> listOf(
            row("xo-debug-log", Res.string.debug_log_enabled),
            row("xo-debug-log-redact", Res.string.debug_log_redact),
            row("xo-debug-log-open-folder", Res.string.debug_log_open_folder),
        )
    }

/** Search entries for rows on the hub page itself (not inside a folder). */
internal val codeineXOHubSearchRows: List<CodeineXOSearchRow> = listOf(
    row("xo-onboarding", Res.string.xo_onboarding_title, Res.string.xo_onboarding_description),
    row("xo-support", Res.string.xo_support_discord_dm),
)
