package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.player.OpeningOverlay
import com.nuvio.app.features.player.PlaybackLoadingCoverMode
import com.nuvio.app.features.player.PlaybackLoadingCoverOwner
import com.nuvio.app.features.player.PublishPlaybackLoadingCover
import com.nuvio.app.features.player.subtitleLoadingStatusMessage
import com.nuvio.app.features.streams.StreamLaunch
import com.nuvio.app.features.streams.StreamsUiState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.debrid_resolving_stream
import nuvio.composeapp.generated.resources.player_loading_preparing
import nuvio.composeapp.generated.resources.streams_finding_source
import nuvio.composeapp.generated.resources.streams_loading_subtitles
import nuvio.composeapp.generated.resources.youtube_resolving_stream
import org.jetbrains.compose.resources.stringResource

/**
 * The stream page's loading screen. Desktop hands it to the app-level playback loading cover, which stays up
 * through navigation into the player; elsewhere it is drawn in place while [visible].
 */
@Composable
internal fun StreamLoadingScreen(
    visible: Boolean,
    launch: StreamLaunch,
    state: StreamsUiState,
    showStatus: Boolean,
    resolvingDebridStream: Boolean,
    resolvingYouTubeStream: Boolean = false,
    onBack: () -> Unit,
    preparingPlayback: Boolean = false,
) {
    if (!isDesktop && !visible) return
    val message = when {
        !showStatus -> null
        resolvingDebridStream -> stringResource(Res.string.debrid_resolving_stream)
        resolvingYouTubeStream -> stringResource(Res.string.youtube_resolving_stream)
        preparingPlayback -> stringResource(Res.string.player_loading_preparing)
        state.overlayMessage == stringResource(Res.string.streams_loading_subtitles) -> subtitleLoadingStatusMessage()
        state.overlayMessage != null -> state.overlayMessage
        state.autoPlayStream != null -> stringResource(Res.string.player_loading_preparing)
        else -> stringResource(Res.string.streams_finding_source)
    }
    val artwork = launch.background ?: launch.poster
    if (isDesktop) {
        PublishPlaybackLoadingCover(
            owner = PlaybackLoadingCoverOwner.StreamPage,
            mode = PlaybackLoadingCoverMode.Show.takeIf { visible },
            artwork = artwork,
            logo = launch.logo,
            title = launch.title,
            message = message,
            progress = null,
            onBack = onBack,
        )
        return
    }
    OpeningOverlay(
        artwork = artwork,
        logo = launch.logo,
        title = launch.title,
        onBack = onBack,
        usePageBackButton = true,
        horizontalSafePadding = 0.dp,
        message = message,
    )
}
