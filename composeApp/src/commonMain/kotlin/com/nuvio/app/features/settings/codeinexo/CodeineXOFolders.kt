package com.nuvio.app.features.settings.codeinexo

import com.nuvio.app.features.settings.SettingsPage
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/** Folders on the CodeineXO hub, in display order. Each opens its own page holding [sections]. */
internal enum class CodeineXOFolder(
    val page: SettingsPage,
    val descriptionRes: StringResource,
    val sections: List<CodeineXOSection>,
) {
    Appearance(
        SettingsPage.XoAppearance,
        Res.string.xo_folder_appearance_description,
        listOf(CodeineXOSection.Appearance, CodeineXOSection.Navigation),
    ),
    HomeDetails(
        SettingsPage.XoHomeDetails,
        Res.string.xo_folder_home_details_description,
        listOf(CodeineXOSection.HomePosters, CodeineXOSection.DetailsPage),
    ),
    Player(
        SettingsPage.XoPlayer,
        Res.string.xo_folder_player_description,
        listOf(CodeineXOSection.PlayerInterface, CodeineXOSection.SeekPreviews, CodeineXOSection.ViewingComfort),
    ),
    VideoAudio(
        SettingsPage.XoVideoAudio,
        Res.string.xo_folder_video_audio_description,
        listOf(CodeineXOSection.PlaybackEngine, CodeineXOSection.Video, CodeineXOSection.Audio),
    ),
    Anime(
        SettingsPage.XoAnime,
        Res.string.xo_folder_anime_description,
        listOf(CodeineXOSection.Anime),
    ),
    StreamsP2p(
        SettingsPage.XoStreamsP2p,
        Res.string.xo_folder_streams_p2p_description,
        listOf(CodeineXOSection.Streams, CodeineXOSection.P2p),
    ),
    Subtitles(
        SettingsPage.XoSubtitles,
        Res.string.xo_folder_subtitles_description,
        listOf(CodeineXOSection.Subtitles),
    ),
    Discord(
        SettingsPage.XoDiscord,
        Res.string.xo_folder_discord_description,
        listOf(CodeineXOSection.Discord),
    ),
    System(
        SettingsPage.XoSystem,
        Res.string.xo_folder_system_description,
        listOf(CodeineXOSection.Downloads, CodeineXOSection.DebugLogging),
    ),
}

/** A folder is shown only when at least one of its sections is visible on this platform. */
internal val CodeineXOFolder.isVisible: Boolean
    get() = sections.any { it.isVisible }

internal fun codeineXOFolderFor(page: SettingsPage): CodeineXOFolder? =
    CodeineXOFolder.entries.firstOrNull { it.page == page }
