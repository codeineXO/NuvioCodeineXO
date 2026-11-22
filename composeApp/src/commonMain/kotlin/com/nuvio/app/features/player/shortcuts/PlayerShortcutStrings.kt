package com.nuvio.app.features.player.shortcuts

import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

val PlayerShortcutGroup.titleRes: StringResource
    get() = when (this) {
        PlayerShortcutGroup.Playback -> Res.string.player_shortcut_group_playback
        PlayerShortcutGroup.Tracks -> Res.string.player_shortcut_group_tracks
        PlayerShortcutGroup.Panels -> Res.string.player_shortcut_group_panels
        PlayerShortcutGroup.View -> Res.string.player_shortcut_group_view
    }

val PlayerShortcutAction.titleRes: StringResource
    get() = when (this) {
        PlayerShortcutAction.TogglePlay -> Res.string.player_shortcut_toggle_play
        PlayerShortcutAction.SeekBack -> Res.string.player_shortcut_seek_back
        PlayerShortcutAction.SeekForward -> Res.string.player_shortcut_seek_forward
        PlayerShortcutAction.SeekBackLong -> Res.string.player_shortcut_seek_back_long
        PlayerShortcutAction.SeekForwardLong -> Res.string.player_shortcut_seek_forward_long
        PlayerShortcutAction.SeekBackFine -> Res.string.player_shortcut_seek_back_fine
        PlayerShortcutAction.SeekForwardFine -> Res.string.player_shortcut_seek_forward_fine
        PlayerShortcutAction.FrameBack -> Res.string.player_shortcut_frame_back
        PlayerShortcutAction.FrameForward -> Res.string.player_shortcut_frame_forward
        PlayerShortcutAction.VolumeUp -> Res.string.player_shortcut_volume_up
        PlayerShortcutAction.VolumeDown -> Res.string.player_shortcut_volume_down
        PlayerShortcutAction.Mute -> Res.string.player_shortcut_mute
        PlayerShortcutAction.SpeedDown -> Res.string.player_shortcut_speed_down
        PlayerShortcutAction.SpeedUp -> Res.string.player_shortcut_speed_up
        PlayerShortcutAction.SpeedReset -> Res.string.player_shortcut_speed_reset
        PlayerShortcutAction.NextEpisode -> Res.string.player_shortcut_next_episode
        PlayerShortcutAction.CycleSubtitles -> Res.string.player_shortcut_cycle_subtitles
        PlayerShortcutAction.CycleAudio ->Res.string.player_shortcut_cycle_audio
        PlayerShortcutAction.ToggleSubtitles -> Res.string.player_shortcut_toggle_subtitles
        PlayerShortcutAction.SubtitleDelayDown -> Res.string.player_shortcut_subtitle_delay_down
        PlayerShortcutAction.SubtitleDelayUp -> Res.string.player_shortcut_subtitle_delay_up
        PlayerShortcutAction.SubtitleOpacity -> Res.string.player_shortcut_subtitle_opacity
        PlayerShortcutAction.AudioPanel -> Res.string.player_shortcut_audio_panel
        PlayerShortcutAction.SubtitlesPanel -> Res.string.player_shortcut_subtitles_panel
        PlayerShortcutAction.SpeedPanel -> Res.string.player_shortcut_speed_panel
        PlayerShortcutAction.QualityPanel -> Res.string.player_shortcut_quality_panel
        PlayerShortcutAction.EpisodesPanel -> Res.string.player_shortcut_episodes_panel
        PlayerShortcutAction.SourcesPanel -> Res.string.player_shortcut_sources_panel
        PlayerShortcutAction.ChaptersPanel -> Res.string.player_shortcut_chapters_panel
        PlayerShortcutAction.ShadersPanel -> Res.string.player_shortcut_shaders_panel
        PlayerShortcutAction.EyeFilterPanel -> Res.string.player_shortcut_eye_filter_panel
        PlayerShortcutAction.PartyPanel -> Res.string.player_shortcut_party_panel
        PlayerShortcutAction.Fullscreen -> Res.string.player_shortcut_fullscreen
        PlayerShortcutAction.MiniPlayer -> Res.string.player_shortcut_mini_player
        PlayerShortcutAction.PictureInPicture -> Res.string.player_shortcut_pip
        PlayerShortcutAction.Resize -> Res.string.player_shortcut_resize
        PlayerShortcutAction.StreamStats -> Res.string.player_shortcut_stream_stats
        PlayerShortcutAction.EyeFilterToggle -> Res.string.player_shortcut_eye_filter_toggle
        PlayerShortcutAction.GrainFilterToggle -> Res.string.player_shortcut_grain_filter_toggle
        PlayerShortcutAction.Screenshot -> Res.string.player_shortcut_screenshot
        PlayerShortcutAction.CheatSheet -> Res.string.player_shortcut_cheat_sheet
    }
