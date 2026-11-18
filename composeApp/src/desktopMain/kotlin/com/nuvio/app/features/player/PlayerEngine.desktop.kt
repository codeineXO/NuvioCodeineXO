package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.AppPresenceState
import com.nuvio.app.core.ui.LocalNuvioPlatformDensity
import com.nuvio.app.core.ui.PresenceSnapshot
import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.nuvio.app.features.player.desktop.DesktopPlayerPictureInPicture
import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.desktop.NativePlayerHost
import com.nuvio.app.features.player.desktop.desktopFullscreenChanges
import com.nuvio.app.features.autosync.AutoSyncMpvCoordinator
import com.nuvio.app.features.autosync.DesktopAutoSyncMpvPlatform
import com.nuvio.app.features.autosync.withAutoSyncMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

@Composable
actual fun PlatformPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    externalSubtitles: List<com.nuvio.app.features.streams.StreamSubtitle>,
    streamType: String?,
    useYoutubeChunkedPlayback: Boolean,
    modifier: Modifier,
    playWhenReady: Boolean,
    initialPositionMs: Long?,
    initialPositionRequestKey: String?,
    resizeMode: PlayerResizeMode,
    playbackEngine: com.nuvio.app.features.player.AndroidPlaybackEngine?,
    useNativeController: Boolean,
    playerControlsState: PlayerControlsState,
    onPlayerControlsAction: (PlayerControlsAction) -> Boolean,
    onPlayerControlsEvent: (String, Double) -> Boolean,
    onPlayerControlsScrubChange: (Long) -> Boolean,
    onPlayerControlsScrubFinished: (Long) -> Boolean,
    onInitialPositionHandled: (key: String, handled: Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
    sourceAvailable: Boolean,
) {
    if (DesktopHostOs.current == DesktopHostOs.MACOS ||
        DesktopHostOs.current == DesktopHostOs.WINDOWS ||
        DesktopHostOs.current == DesktopHostOs.LINUX
    ) {
        NativePlayerSurface(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceAvailable = sourceAvailable,
            sourceHeaders = sourceHeaders,
            externalSubtitles = externalSubtitles,
            modifier = modifier,
            playWhenReady = playWhenReady,
            resizeMode = resizeMode,
            initialPositionMs = initialPositionMs ?: 0L,
            initialPositionRequestKey = initialPositionRequestKey,
            playerControlsState = playerControlsState,
            useNativeController = useNativeController,
            onPlayerControlsAction = onPlayerControlsAction,
            onPlayerControlsEvent = onPlayerControlsEvent,
            onPlayerControlsScrubChange = onPlayerControlsScrubChange,
            onPlayerControlsScrubFinished = onPlayerControlsScrubFinished,
            onInitialPositionHandled = onInitialPositionHandled,
            onControllerReady = onControllerReady,
            onSnapshot = onSnapshot,
            onError = onError,
        )
        return
    }

    DesktopStubPlayerSurface(
        modifier = modifier,
        initialPositionRequestKey = initialPositionRequestKey,
        onInitialPositionHandled = onInitialPositionHandled,
        onControllerReady = onControllerReady,
        onSnapshot = onSnapshot,
    )
}

@Composable
private fun NativePlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String? = null,
    sourceAvailable: Boolean,
    sourceHeaders: Map<String, String>,
    externalSubtitles: List<com.nuvio.app.features.streams.StreamSubtitle>,
    modifier: Modifier,
    playWhenReady: Boolean,
    resizeMode: PlayerResizeMode,
    initialPositionMs: Long,
    initialPositionRequestKey: String?,
    playerControlsState: PlayerControlsState,
    useNativeController: Boolean = true,
    onPlayerControlsAction: (PlayerControlsAction) -> Boolean,
    onPlayerControlsEvent: (String, Double) -> Boolean,
    onPlayerControlsScrubChange: (Long) -> Boolean,
    onPlayerControlsScrubFinished: (Long) -> Boolean,
    onInitialPositionHandled: (key: String, handled: Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val platformDensity = LocalNuvioPlatformDensity.current
    val host = remember { NativePlayerHost() }
    val controller = remember(host) { NativePlayerController(host) }
    val hostFirstPaintComplete = remember { mutableStateOf(false) }
    val hostFirstFullSizePaintComplete = remember { mutableStateOf(false) }
    val playbackHeaders = remember(sourceHeaders) { sanitizePlaybackHeaders(sourceHeaders) }
    val latestExternalSubtitles = rememberUpdatedState(externalSubtitles)
    val latestPlayerControlsState = rememberUpdatedState(playerControlsState)
    val autoSyncScope = rememberCoroutineScope()
    // Kept across sources so its message count, part of the overlay's notification token, only grows.
    val autoSyncPlatform = remember {
        DesktopAutoSyncMpvPlatform(
            currentNotificationToken = { latestPlayerControlsState.value.notificationToken },
        )
    }
    val autoSyncCoordinator = remember(sourceUrl, playbackHeaders, autoSyncScope) {
        AutoSyncMpvCoordinator(
            scope = autoSyncScope,
            sourceUrl = sourceUrl,
            sourceHeaders = playbackHeaders,
            getSubtitleHeaders = { subtitleUrl ->
                latestExternalSubtitles.value
                    .firstOrNull { it.url == subtitleUrl }
                    ?.headers
                    .orEmpty()
            },
            getPreferredLanguage = { PlayerSettingsRepository.uiState.value.preferredSubtitleLanguage },
            platform = autoSyncPlatform,
        )
    }
    DisposableEffect(autoSyncCoordinator) {
        onDispose { autoSyncCoordinator.dispose() }
    }
    val readyController = remember(controller, autoSyncCoordinator) {
        // setSubtitleUri replaces the external subtitles, so the retimed copy takes the original's place.
        autoSyncCoordinator.wrap(controller) { _, path -> controller.setSubtitleUri(path) }
    }
    val controlsState = playerControlsState.withAutoSyncMessage(autoSyncPlatform.message)
    val latestControlsState = rememberUpdatedState(controlsState)
    val latestOnPlayerControlsAction = rememberUpdatedState(onPlayerControlsAction)
    val latestOnPlayerControlsEvent = rememberUpdatedState(onPlayerControlsEvent)
    val latestOnPlayerControlsScrubChange = rememberUpdatedState(onPlayerControlsScrubChange)
    val latestOnPlayerControlsScrubFinished = rememberUpdatedState(onPlayerControlsScrubFinished)
    val latestOnInitialPositionHandled = rememberUpdatedState(onInitialPositionHandled)
    val latestOnError = rememberUpdatedState(onError)
    val playerSettings by PlayerSettingsRepository.uiState.collectAsState()
    val decoderPriority = playerSettings.decoderPriority
    val nvidiaRtxSuperResolutionEnabled = playerSettings.nvidiaRtxSuperResolutionEnabled
    val animeUpscalerOption = playerSettings.animeUpscalerOption
    val animeUpscalerMode = playerSettings.animeUpscalerMode
    val svpMotionOption = playerSettings.svpMotionOption
    val audioNightModeEnabled = playerSettings.audioNightModeEnabled
    val playerUiScale = playerSettings.playerUiScale

    SideEffect {
        if (!sourceAvailable || !controller.isAttachedTo(sourceUrl, playbackHeaders)) {
            onSnapshot(PlayerPlaybackSnapshot(isLoading = true))
        }
        // Publish early for navigation's release barrier, but keep track restoration gated on
        // a loaded snapshot belonging to this source.
        onControllerReady(readyController)
    }

    DisposableEffect(host) {
        DesktopPlayerPictureInPicture.setHost(host)
        DesktopPlayerPictureInPicture.setController(controller)
        host.onDisplayableChanged = { displayable ->
            if (!displayable) {
                hostFirstPaintComplete.value = false
                hostFirstFullSizePaintComplete.value = false
            }
        }
        host.onFirstPaint = {
            hostFirstPaintComplete.value = true
        }
        host.onFirstFullSizePaint = {
            hostFirstFullSizePaintComplete.value = true
        }
        onDispose {
            DesktopPlayerPictureInPicture.release()
            host.onDisplayableChanged = null
            host.onFirstPaint = null
            host.onFirstFullSizePaint = null
        }
    }

    LaunchedEffect(controller) {
        controller.setControlCallbacks(
            onAction = { action -> latestOnPlayerControlsAction.value(action) },
            onEvent = { type, value -> latestOnPlayerControlsEvent.value(type, value) },
            onScrubChange = { positionMs -> latestOnPlayerControlsScrubChange.value(positionMs) },
            onScrubFinished = { positionMs -> latestOnPlayerControlsScrubFinished.value(positionMs) },
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    // The controls overlay owns the player shortcuts. After alt-tab, desktop
    // window focus can return to the AWT/Compose host instead of the embedded
    // WebView, so explicitly hand keyboard focus back to the native controls
    // whenever the player window becomes active again.
    DisposableEffect(controller, hostFirstFullSizePaintComplete.value) {
        val uninstall = if (hostFirstFullSizePaintComplete.value) {
            controller.installWindowFocusForwarding()
        } else {
            null
        }
        onDispose { uninstall?.invoke() }
    }

    LaunchedEffect(host, useNativeController) {
        if (!useNativeController) {
            host.setControlsVisible(false)
        }
    }

    LaunchedEffect(controller, sourceAudioUrl) {
        if (!sourceAudioUrl.isNullOrBlank()) {
            controller.setAudioUri(sourceAudioUrl)
        }
    }

    LaunchedEffect(
        controller,
        sourceAvailable,
        sourceUrl,
        sourceAudioUrl,
        playbackHeaders,
        decoderPriority,
        nvidiaRtxSuperResolutionEnabled,
        hostFirstFullSizePaintComplete.value,
        initialPositionMs,
        initialPositionRequestKey,
    ) {
        if (!sourceAvailable || !hostFirstFullSizePaintComplete.value) {
            return@LaunchedEffect
        }
        delay(16L)
        controller.attach(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = playbackHeaders,
            playWhenReady = playWhenReady,
            initialPositionMs = initialPositionMs,
            decoderPriority = decoderPriority,
            nvidiaRtxSuperResolutionEnabled = nvidiaRtxSuperResolutionEnabled,
            onError = { message -> latestOnError.value(message) },
        )
        initialPositionRequestKey?.let { key ->
            latestOnInitialPositionHandled.value(key, initialPositionMs > 0L)
        }
    }

    LaunchedEffect(controller, sourceAvailable, playWhenReady) {
        if (!sourceAvailable) return@LaunchedEffect
        if (playWhenReady) {
            controller.play()
        } else {
            controller.pause()
        }
    }

    LaunchedEffect(controller, resizeMode) {
        controller.setResizeMode(resizeMode)
    }

    LaunchedEffect(controller, playerSettings.playbackBufferPreset) {
        controller.setPlaybackBufferPreset(playerSettings.playbackBufferPreset)
    }

    LaunchedEffect(controller, animeUpscalerOption, animeUpscalerMode, svpMotionOption, playerControlsState.mediaContentType) {
        controller.setVideoEnhancements(
            mediaContentType = playerControlsState.mediaContentType,
            animeUpscalerOption = animeUpscalerOption,
            animeUpscalerMode = animeUpscalerMode,
            svpMotionOption = svpMotionOption,
        )
    }

    LaunchedEffect(controller, audioNightModeEnabled) {
        controller.setAudioNightMode(audioNightModeEnabled)
    }

    LaunchedEffect(controller, playerControlsState, playerUiScale) {
        controller.updateControls(playerControlsState)
        DesktopPlayerPictureInPicture.setWindowTitle(playerControlsState.pipWindowTitle)
    }

    LaunchedEffect(controller) {
        DesktopPlayerPictureInPicture.changes.drop(1).collect {
            controller.updateControls(latestControlsState.value)
        }
    }

    LaunchedEffect(controller) {
        AppPresenceState.current.collect { snapshot ->
            val player = snapshot as? PresenceSnapshot.Player
            controller.setNowPlayingMetadata(player?.title, player?.episodeLabel, player?.posterUrl)
        }
    }

    LaunchedEffect(controller) {
        desktopFullscreenChanges.drop(1).collect {
            controller.onDesktopFullscreenChanged()
        }
    }

    // Do not label snapshots or tracks from the outgoing native handle as the new episode.
    LaunchedEffect(controller, readyController, sourceAvailable, sourceUrl, playbackHeaders) {
        while (true) {
            if (sourceAvailable && controller.isAttachedTo(sourceUrl, playbackHeaders)) {
                onControllerReady(readyController)
                onSnapshot(controller.snapshot())
            }
            delay(250L)
        }
    }

    val pipChanges by DesktopPlayerPictureInPicture.changes.collectAsState()
    val isInPip = pipChanges >= 0 && DesktopPlayerPictureInPicture.isEnabled

    val surfaceRevealReady = LocalPlayerSurfaceRevealReady.current
    // The native window paints solid black from the moment it is shown, while mpv and the controls page are still
    // starting. Keep it collapsed behind an app-drawn loading screen until the page reports its first painted frame.
    val controlsPainted = remember(host, sourceUrl) { mutableStateOf(false) }
    DisposableEffect(controller, controlsPainted) {
        controller.onControlsPainted = { controlsPainted.value = true }
        onDispose { controller.onControlsPainted = null }
    }
    LaunchedEffect(controlsPainted) {
        // Never strand the video behind the placeholder if the page cannot report back.
        delay(CONTROLS_PAINT_TIMEOUT_MS)
        controlsPainted.value = true
    }
    // On Back the player shuts down before navigation; park the window at once so the frozen last frame is not
    // left on screen meanwhile, and let the black player page fade away once the release completes.
    val releasingForNavigation by controller.releasingForNavigation.collectAsState()
    // Stop waiting for the page once there is no loading screen to show (loaded, failed, or turned off), so the
    // handshake never holds back the picture or an error.
    val surfaceRevealed = !releasingForNavigation &&
        surfaceRevealReady && (controlsPainted.value || !playerControlsState.showOpeningOverlay)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // SwingPanel keeps the stable AWT host. PiP moves only the native player
        // surface to the independent window, so Compose never loses its host peer.
        CompositionLocalProvider(LocalDensity provides platformDensity) {
            SwingPanel(
                factory = { host },
                // Docked, the player page is parked off-screen at full size, so the host keeps its size and the video
                // comes back without a resize.
                modifier = if (isInPip && !DesktopPlayerPictureInPicture.isDocked) {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .requiredSize(1.dp)
                } else if (hostFirstPaintComplete.value) {
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (surfaceRevealed) Modifier else Modifier.layout { measurable, constraints ->
                                // Full size so the player is created and starts loading right away (attach waits
                                // for a full-size paint), but parked below the window with one row left on screen
                                // so it still receives paints, until the controls page has drawn its overlay.
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placeable.place(0, (placeable.height - 1).coerceAtLeast(0))
                                }
                            }
                        )
                } else {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .requiredSize(1.dp)
                },
                background = Color.Black,
            )
        }

        // Mirrors the controls page: no loading screen when the loading overlay is turned off, nor while closing.
        if (!surfaceRevealed && !isInPip && !releasingForNavigation && playerControlsState.showOpeningOverlay) {
            OpeningOverlay(
                artwork = playerControlsState.openingArtwork,
                logo = playerControlsState.openingLogo,
                title = playerControlsState.openingTitle,
                onBack = { latestOnPlayerControlsAction.value(PlayerControlsAction.Back) },
                horizontalSafePadding = 0.dp,
                message = playerControlsState.openingMessage,
                progress = playerControlsState.openingProgress,
                usePageBackButton = true,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Placeholder overlay shown when video has moved to PiP window. The docked mini player only shows this page
        // for the moment it takes to move the video back on expand, so keep it plain black then.
        if (isInPip) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (!DesktopPlayerPictureInPicture.isDocked) Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = playerControlsState.pipPlaceholderTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { DesktopPlayerPictureInPicture.clear() },
                    ) {
                        Text(playerControlsState.pipRestoreLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopStubPlayerSurface(
    modifier: Modifier,
    initialPositionRequestKey: String?,
    onInitialPositionHandled: (key: String, handled: Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
) {
    val controller = remember { DesktopStubPlayerController() }

    LaunchedEffect(controller) {
        onControllerReady(controller)
        onSnapshot(PlayerPlaybackSnapshot(isLoading = false))
    }

    LaunchedEffect(initialPositionRequestKey) {
        initialPositionRequestKey?.let { key -> onInitialPositionHandled(key, false) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Desktop in-app playback is not available yet.",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private class DesktopStubPlayerController : PlayerEngineController {
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun seekBy(offsetMs: Long) = Unit
    override fun retry() = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun getAudioTracks(): List<AudioTrack> = emptyList()
    override fun getSubtitleTracks(): List<SubtitleTrack> = emptyList()
    override fun applyAudioLanguagePreferences(languages: List<String>) = Unit
    override fun selectAudioTrack(index: Int) = Unit
    override fun selectSubtitleTrack(index: Int) = Unit
    override fun setSubtitleUri(url: String) = Unit
    override fun clearExternalSubtitle() = Unit
    override fun clearExternalSubtitleAndSelect(trackIndex: Int) = Unit
}

private const val CONTROLS_PAINT_TIMEOUT_MS = 5_000L
