package com.nuvio.app.features.settings.codeinexo

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.diagnostics.DebugLogSettings
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.settings.DiscordRichPresenceRepository
import com.nuvio.app.features.settings.DownloadLocationSettings
import com.nuvio.app.features.settings.SeekPreviewSettingsSection
import com.nuvio.app.features.settings.SettingsPage
import com.nuvio.app.features.settings.StreamFilterSettingsSection
import com.nuvio.app.features.settings.debugLoggingSettingsItem
import com.nuvio.app.isDesktop
import com.nuvio.app.isWindows
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Every section of the CodeineXO settings, in display order.
 * [alsoShownOn] is the stock settings page that shows the same section (same settings, same values).
 */
internal enum class CodeineXOSection(
    val titleRes: StringResource,
    val alsoShownOn: SettingsPage?,
) {
    Appearance(Res.string.xo_section_appearance, SettingsPage.Layout),
    Navigation(Res.string.xo_section_navigation, SettingsPage.Layout),
    HomePosters(Res.string.xo_section_home_posters, SettingsPage.Layout),
    DetailsPage(Res.string.xo_section_details_page, SettingsPage.Layout),
    PlayerInterface(Res.string.xo_section_player_interface, SettingsPage.PlaybackInterface),
    SeekPreviews(Res.string.settings_seek_preview_section, SettingsPage.PlaybackInterface),
    ViewingComfort(Res.string.xo_section_viewing_comfort, SettingsPage.PlaybackInterface),
    PlaybackEngine(Res.string.xo_section_playback_engine, null),
    Video(Res.string.xo_section_video_enhancements, SettingsPage.PlaybackVideo),
    Audio(Res.string.xo_section_audio_enhancement, SettingsPage.PlaybackAudio),
    Anime(Res.string.xo_section_anime, SettingsPage.PlaybackSkipSegments),
    Streams(Res.string.stream_filter_settings_section, null),
    P2p(Res.string.settings_playback_section_p2p, SettingsPage.PlaybackP2p),
    Subtitles(Res.string.settings_playback_section_subtitle_rendering, null),
    Discord(Res.string.settings_advanced_section_discord, null),
    Downloads(Res.string.compose_settings_root_downloads_title, null),
    DebugLogging(Res.string.debug_log_section, null),
}

internal val CodeineXOSection.isVisible: Boolean
    get() = when (this) {
        CodeineXOSection.Navigation,
        CodeineXOSection.Video,
        CodeineXOSection.Audio,
        CodeineXOSection.Downloads -> isDesktop
        CodeineXOSection.PlaybackEngine -> isWindows
        CodeineXOSection.P2p -> P2pSettingsRepository.isVisible
        CodeineXOSection.Discord -> isDesktop && DiscordRichPresenceRepository.isSupported
        CodeineXOSection.DebugLogging -> DebugLogSettings.isSupported
        else -> true
    }

/** Adds the visible [sections] as list items, one item per section. */
internal fun LazyListScope.codeineXOSectionItems(sections: List<CodeineXOSection>, isTablet: Boolean) {
    sections.filter { it.isVisible }.forEach { section ->
        if (section == CodeineXOSection.DebugLogging) {
            debugLoggingSettingsItem(isTablet)
        } else {
            item { CodeineXOSectionContent(section, isTablet) }
        }
    }
}

@Composable
private fun CodeineXOSectionContent(section: CodeineXOSection, isTablet: Boolean) {
    when (section) {
        CodeineXOSection.Appearance -> AppearanceSection(isTablet)
        CodeineXOSection.Navigation -> NavigationSection(isTablet)
        CodeineXOSection.HomePosters -> HomePostersSection(isTablet)
        CodeineXOSection.DetailsPage -> DetailsPageSection(isTablet)
        CodeineXOSection.PlayerInterface -> PlayerInterfaceSection(isTablet)
        CodeineXOSection.SeekPreviews -> {
            val playerSettings by remember {
                PlayerSettingsRepository.ensureLoaded()
                PlayerSettingsRepository.uiState
            }.collectAsStateWithLifecycle()
            SeekPreviewSettingsSection(playerSettings, isTablet)
        }
        CodeineXOSection.ViewingComfort -> ViewingComfortSection(isTablet)
        CodeineXOSection.PlaybackEngine -> PlaybackEngineSection(isTablet)
        CodeineXOSection.Video -> VideoEnhancementsSection(isTablet)
        CodeineXOSection.Audio -> AudioEnhancementSection(isTablet)
        CodeineXOSection.Anime -> AnimeSection(isTablet)
        CodeineXOSection.Streams -> StreamFilterSettingsSection(isTablet = isTablet)
        CodeineXOSection.P2p -> P2pSection(isTablet)
        CodeineXOSection.Subtitles -> SubtitlesSection(isTablet)
        CodeineXOSection.Discord -> DiscordSection(isTablet)
        CodeineXOSection.Downloads -> DownloadLocationSettings(isTablet)
        CodeineXOSection.DebugLogging -> Unit
    }
}
