package com.nuvio.app.features.trailer

import com.nuvio.app.core.ui.remoteNavigationTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.features.player.EnterImmersivePlayerMode
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerControlsAction
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.toCssColorString
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.trailer_unable_to_play
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TrailerPlayer(
    source: TrailerPlaybackSource?,
    state: TrailerPlaybackState,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = "",
    onExitFullscreen: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onToggleFullscreen: (() -> Unit)? = null,
    onEscapeFullscreen: (() -> Unit)? = null,
) {
    val error = errorMessage ?: state.error
    EnterImmersivePlayerMode(keepScreenAwake = error == null && state.playWhenReady && !state.snapshot.isEnded)
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (source != null && !isLoading && error == null) {
            TrailerPlaybackSurface(
                source = source,
                state = state,
                resizeMode = PlayerResizeMode.Fit,
                title = title,
                onExitFullscreen = onExitFullscreen,
                onBack = onBack,
                onToggleFullscreen = onToggleFullscreen,
                onEscapeFullscreen = onEscapeFullscreen,
            )
        }
        when {
            error == null && (isLoading || source != null && state.snapshot.isLoading) -> {
                NuvioLoadingIndicator()
            }
            error != null -> Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.trailer_unable_to_play),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onRetry != null) {
                    TextButton(onClick = onRetry, modifier = Modifier.remoteNavigationTarget(shape = androidx.compose.material3.ButtonDefaults.shape)) { Text(stringResource(Res.string.action_retry)) }
                }
            }
        }
        if (!isDesktop) {
            TrailerPlayerControls(
                state = state,
                canControlPlayback = source != null && !isLoading && error == null,
                title = title,
                onExitFullscreen = onExitFullscreen,
            )
        }
    }
}

@Composable
private fun TrailerPlaybackSurface(
    source: TrailerPlaybackSource,
    state: TrailerPlaybackState,
    resizeMode: PlayerResizeMode,
    title: String,
    onExitFullscreen: (() -> Unit)?,
    onBack: (() -> Unit)?,
    onToggleFullscreen: (() -> Unit)?,
    onEscapeFullscreen: (() -> Unit)?,
) {
    val presentationId = remember { state.presentationId }
    val resumePositionMs = remember { state.snapshot.positionMs }
    var restoringPosition by remember { mutableStateOf(resumePositionMs > 0L) }
    var controller by remember { mutableStateOf<PlayerEngineController?>(null) }
    var snapshot by remember { mutableStateOf(PlayerPlaybackSnapshot()) }
    val playerSettings by PlayerSettingsRepository.uiState.collectAsState()
    val themeColors = MaterialTheme.nuvio.colors
    val accentGradient = MaterialTheme.themePalette.accentGradient

    val dismissAction = {
        (onExitFullscreen ?: onBack).invokeIfPresent()
    }
    val toggleFullscreenAction = {
        (onToggleFullscreen ?: onExitFullscreen).invokeIfPresent()
    }

    val playerControlsState = remember(title, playerSettings.playerUiMode, playerSettings.showRemainingTime, themeColors, accentGradient) {
        PlayerControlsState(
            title = title,
            playerUiMode = playerSettings.playerUiMode.storageKey,
            showRemainingTime = playerSettings.showRemainingTime,
            isMiniPlayer = false,
            themeAccentColor = themeColors.accent.toCssColorString(),
            themeAccentGradientColors = accentGradient
                .takeIf { it.size > 1 }
                .orEmpty()
                .map { it.toCssColorString() },
            themeAccentStrongColor = themeColors.accentStrong.toCssColorString(),
            themeOnAccentColor = themeColors.onAccent.toCssColorString(),
            themeFocusColor = themeColors.focusRing.toCssColorString(),
            themeSelectedSurfaceColor = themeColors.accent.copy(alpha = 0.24f).toCssColorString(),
            themeSelectedSurfaceHoverColor = themeColors.accent.copy(alpha = 0.34f).toCssColorString(),
            themeSelectedRingColor = themeColors.accent.copy(alpha = 0.35f).toCssColorString(),
            themeTimelineFillColor = themeColors.playerTimelineFill.toCssColorString(),
            themeTimelineTrackColor = themeColors.playerTimelineTrack.toCssColorString(),
            themeBufferingColor = themeColors.playerBuffering.toCssColorString(),
            themeBufferingTrackColor = themeColors.playerBuffering.copy(alpha = 0.35f).toCssColorString(),
            themeControlForegroundColor = themeColors.playerControlsForeground.toCssColorString(),
            themeSurfaceElevatedColor = themeColors.surfaceElevated.toCssColorString(),
            themeSurfaceCardColor = themeColors.surfaceCard.toCssColorString(),
            themeSurfacePopoverColor = themeColors.surfacePopover.toCssColorString(),
            themeTextPrimaryColor = themeColors.textPrimary.toCssColorString(),
            themeTextSecondaryColor = themeColors.textSecondary.toCssColorString(),
            themeTextMutedColor = themeColors.textMuted.toCssColorString(),
            themeBorderDefaultColor = themeColors.borderDefault.toCssColorString(),
            themeDangerColor = themeColors.danger.toCssColorString(),
        )
    }

    LaunchedEffect(controller, snapshot.isLoading, snapshot.durationMs, restoringPosition) {
        val player = controller
        if (restoringPosition && player != null && !snapshot.isLoading && snapshot.durationMs > 0L &&
            presentationId == state.presentationId
        ) {
            player.seekTo(resumePositionMs.coerceAtMost(snapshot.durationMs))
            restoringPosition = false
        }
    }
    DisposableEffect(state) {
        onDispose {
            if (state.controller === controller) state.controller = null
        }
    }
    PlatformPlayerSurface(
        sourceUrl = source.videoUrl,
        sourceAudioUrl = source.audioUrl,
        sourceHeaders = source.headers,
        useYoutubeChunkedPlayback = true,
        modifier = Modifier.fillMaxSize(),
        playWhenReady = state.playWhenReady && !restoringPosition && !state.snapshot.isEnded &&
            presentationId == state.presentationId,
        resizeMode = resizeMode,
        useNativeController = isDesktop,
        playerControlsState = playerControlsState,
        onPlayerControlsAction = { action ->
            when (action) {
                PlayerControlsAction.Back -> {
                    dismissAction()
                    true
                }
                PlayerControlsAction.TogglePlayback, PlayerControlsAction.KeyboardTogglePlayback -> {
                    state.togglePlayback()
                    true
                }
                PlayerControlsAction.SeekBack,
                PlayerControlsAction.KeyboardSeekBack,
                PlayerControlsAction.KeyboardSeek10Back -> {
                    state.controller?.seekBy(-10_000L)
                    true
                }
                PlayerControlsAction.SeekForward,
                PlayerControlsAction.KeyboardSeekForward,
                PlayerControlsAction.KeyboardSeek10Forward -> {
                    state.controller?.seekBy(10_000L)
                    true
                }
                else -> false
            }
        },
        onPlayerControlsEvent = { type, value ->
            when (type) {
                "toggleFullscreen" -> {
                    (if (value >= 0.5) onEscapeFullscreen else onToggleFullscreen ?: onExitFullscreen).invokeIfPresent()
                    true
                }
                "close", "back" -> {
                    dismissAction()
                    true
                }
                else -> false
            }
        },
        onControllerReady = {
            controller = it
            if (presentationId == state.presentationId) state.controller = it
        },
        onSnapshot = {
            snapshot = it
            if (!restoringPosition && presentationId == state.presentationId) state.snapshot = it
        },
        onError = { if (presentationId == state.presentationId) state.error = it },
    )
    if (restoringPosition) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}

private fun (() -> Unit)?.invokeIfPresent(): Boolean {
    this?.invoke() ?: return false
    return true
}
