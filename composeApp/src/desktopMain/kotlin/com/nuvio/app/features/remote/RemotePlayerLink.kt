package com.nuvio.app.features.remote

import com.nuvio.app.features.player.AudioTrack
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.desktop.NativePlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The active desktop player as the phone remote sees it: its controls state out, a few control messages in. */
internal object RemotePlayerLink {
    /** The control messages the phone may send, the same ones the player's own on-screen controls send. */
    private val allowed = setOf(
        "back", "toggle", "seekBack", "seekForward", "volumeChange", "volumeChangeTemporary", "scrubFinish", "selectSource", "reloadSources",
        "selectBuiltInSubtitleTrack", "selectAddonSubtitle", "selectAudioTrack", "toggleAudioNightMode",
        "setAnimeUpscalerOption", "setAnimeUpscalerMode", "setSvpMode",
        "setPlaybackSpeed", "setEyeFilterEnabled", "selectChapter",
        // Subtitles → Customization.
        "subtitleDelayDelta", "subtitleDelayReset", "subtitleFontSizeDelta", "subtitleBottomOffsetDelta",
        "subtitleCustomStyleToggle", "subtitleFontDelta", "subtitleBoldToggle", "subtitleTextColor",
        "subtitleTextColorRgb", "subtitleTextOpacity", "subtitleOutlineEffectDelta", "subtitleOutlineColor",
        "subtitleOutlineColorRgb", "subtitleOutlineOpacity", "subtitleOutlineThicknessDelta",
        "subtitleAutoSyncReload", "subtitleAutoSyncCapture", "subtitleAutoSyncCue", "subtitleStyleReset",
    )

    /** The on-screen controls the phone may click, by name: the bar's panels and the "More playback controls". */
    private val clickable = mapOf(
        "sources" to "codeineSourcesButton",
        "subtitles" to "codeineSubtitlesButton",
        "audio" to "codeineAudioButton",
        "enhance" to "codeineShadersButton",
        "speed" to "codeineSpeedButton",
        "aspect" to "codeineResizeButton",
        "pip" to "codeinePipButton",
        "miniPlayer" to "codeineMiniPlayerButton",
        "party" to "codeinePartyButton",
        "stats" to "codeineStatsButton",
        "eyeFilter" to "codeineEyeFilterButton",
        "chapters" to "codeineChaptersButton",
        "fullscreen" to "codeineFullscreenButton",
        "subtitleImport" to "subtitleImportBtn",
        "subtitleTabSubtitles" to "subtitleTabSubtitlesBtn",
        "subtitleTabCustomization" to "subtitleTabCustomizationBtn",
    )

    private val _state = MutableStateFlow<PlayerControlsState?>(null)
    val state: StateFlow<PlayerControlsState?> = _state

    @Volatile
    private var controller: NativePlayerController? = null

    fun publish(from: NativePlayerController, state: PlayerControlsState) {
        controller = from
        _state.value = state
    }

    fun clear(from: NativePlayerController) {
        if (controller !== from) return
        controller = null
        _state.value = null
    }

    /** Audio tracks come from mpv, not the controls state. */
    fun audioTracks(): List<AudioTrack> = runCatching { controller?.getAudioTracks() }.getOrNull().orEmpty()

    /** Position and duration in ms, or null when no player is open. */
    fun progress(): Pair<Long, Long>? = runCatching { controller?.snapshot() }.getOrNull()
        ?.let { it.positionMs to it.durationMs }

    fun send(type: String, value: Double) {
        if (type in allowed) controller?.remoteEvent(type, value)
    }

    fun click(name: String) {
        clickable[name]?.let { controller?.remoteClick(it) }
    }

    /** Closes whatever panel is open on the PC, e.g. when the phone's sheet is dismissed. */
    fun closePanels() {
        controller?.closeRemotePanels()
    }
}
