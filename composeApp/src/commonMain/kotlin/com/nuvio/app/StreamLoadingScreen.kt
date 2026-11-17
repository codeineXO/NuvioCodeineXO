package com.nuvio.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.nuvio.app.navigation.playerEnterFadeMillis
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.player.OpeningOverlay
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

@Composable
internal fun StreamLoadingScreen(
    launch: StreamLaunch,
    state: StreamsUiState,
    showStatus: Boolean,
    resolvingDebridStream: Boolean,
    resolvingYouTubeStream: Boolean = false,
    onBack: () -> Unit,
    preparingPlayback: Boolean = false,
) {
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
    OpeningOverlay(
        artwork = launch.background ?: launch.poster,
        logo = launch.logo,
        title = launch.title,
        onBack = onBack,
        usePageBackButton = true,
        horizontalSafePadding = 0.dp,
        message = message,
    )
}

/**
 * Desktop fades the loading screen over the stream list, and keeps it up while the player fades in on top, instead
 * of cutting between them. It swallows pointer input so the list beneath cannot be clicked through it.
 */
@Composable
internal fun FadingStreamLoadingScreen(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    if (!isDesktop) {
        if (visible) content()
        return
    }
    val fadeMillis = playerEnterFadeMillis()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(fadeMillis, easing = FastOutSlowInEasing)),
        // Outlasts the player's own fade-in, so the list never shows through while the player covers this screen.
        exit = fadeOut(tween(fadeMillis, delayMillis = fadeMillis, easing = FastOutSlowInEasing)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
        ) {
            content()
        }
    }
}
