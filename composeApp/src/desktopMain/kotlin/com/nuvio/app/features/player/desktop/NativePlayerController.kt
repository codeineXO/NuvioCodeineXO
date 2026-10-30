package com.nuvio.app.features.player.desktop

import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.AnimeShaderProvider
import com.nuvio.app.features.player.AnimeUpscalerMode
import com.nuvio.app.features.player.AnimeUpscalerOption
import com.nuvio.app.features.player.MediaContentType
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.SvpMotionOption
import com.nuvio.app.features.player.PlaybackBufferPreset
import com.nuvio.app.features.player.SvpProfile
import com.nuvio.app.features.player.SvpScriptProvider
import com.nuvio.app.features.player.PlayerControlAddonSubtitleItem
import com.nuvio.app.features.player.PlayerControlEpisodeItem
import com.nuvio.app.features.player.PlayerControlFilterItem
import com.nuvio.app.features.player.PlayerControlPartyState
import com.nuvio.app.features.player.PlayerControlSeasonItem
import com.nuvio.app.features.player.PlayerControlSourceItem
import com.nuvio.app.features.player.PlayerControlSubtitleCueItem
import com.nuvio.app.features.player.PlayerControlSubtitleLanguageItem
import com.nuvio.app.features.player.PlayerControlSubtitleOptionItem
import com.nuvio.app.features.player.AudioTrack
import com.nuvio.app.features.player.ImportedSubtitleFile
import com.nuvio.app.features.player.SubtitleImportBridge
import com.nuvio.app.features.player.findPreferredAudioTrackIndex
import com.nuvio.app.features.player.InAppPlayerManager
import com.nuvio.app.features.player.ParentalWarning
import com.nuvio.app.features.player.PlayerControlsAction
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerChapter
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerMediaInfo
import com.nuvio.app.features.player.mpvMediaInfo
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerPlaybackStats
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.SUBTITLE_DELAY_MAX_MS
import com.nuvio.app.features.player.findPreferredAudioTrackIndex
import com.nuvio.app.features.player.SUBTITLE_DELAY_MIN_MS
import com.nuvio.app.features.player.SubtitleColorSwatches
import com.nuvio.app.features.player.SubtitleOutlineColorSwatches
import com.nuvio.app.features.player.SubtitleOutlineEffect
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.addons.encodeUnsafeHttpUrlCharacters
import com.nuvio.app.features.player.SubtitleStyleState
import com.nuvio.app.features.player.SubtitleTrack
import com.nuvio.app.features.player.inferForcedSubtitleTrack
import com.nuvio.app.features.player.toStorageHexString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Component
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.concurrent.Volatile

internal typealias NativePlayerCreate = (
    Long,
    String,
    Array<String>,
    Boolean,
    Long,
    String,
    Int,
    Boolean,
    NativePlayerEventSink,
) -> Long

internal class NativePlayerController(
    private val host: NativePlayerHost,
    private val nativeCreate: NativePlayerCreate = { hostViewPtr, source, headers, playWhenReady, positionMs, controlsUrl, decoder, rtx, sink ->
        NativePlayerBridge.ensureLoaded()
        NativePlayerBridge.create(hostViewPtr, source, headers, playWhenReady, positionMs, controlsUrl, decoder, rtx, sink)
    },
    private val nativeDispose: (Long) -> Unit = NativePlayerBridge::dispose,
    private val nativeSeekTo: (Long, Long) -> Unit = NativePlayerBridge::seekTo,
    private val nativeSeekBy: (Long, Long) -> Unit = NativePlayerBridge::seekBy,
    private val nativePositionMs: (Long) -> Long = NativePlayerBridge::positionMs,
    private val nativeDurationMs: (Long) -> Long = { handle ->
        if (handle <= 0x10000L) 0L else runCatching { NativePlayerBridge.durationMs(handle) }.getOrDefault(0L)
    },
    private val isHostDisplayable: () -> Boolean = { host.isDisplayable },
    private val resolveHostView: () -> Long = { AwtNativeViewResolver.resolveNativeViewPointer(host) },
    private val createWaitTimeoutMs: Long = 5_000L,
    private val releaseTimeoutMs: Long = 10_000L,
    private val onCreateWaitCompleted: () -> Unit = {},
    private val nativeSetPlaybackBufferPreset: (Long, PlaybackBufferPreset) -> Unit = NativePlayerBridge::setPlaybackBufferPreset,
) : PlayerEngineController {
    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val log = Logger.withTag("NativePlayerControls")

        /** Cap on waiting for the previous player's teardown so a hung one cannot block playback. */
        const val TEARDOWN_WAIT_MS = 5_000L

        @Volatile
        var rememberedVolumeLevel: Float = DesktopPlayerVolumeStorage.loadVolumeLevel() ?: 1f

        @Volatile
        var rememberedResizeMode: PlayerResizeMode = PlayerResizeMode.Fit

        val subtitleDownloadClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    private data class ReleaseCallback(
        val onReleased: () -> Unit,
        val onFailed: (String) -> Unit,
    )

    private val lifecycleLock = Any()
    private val playerCommandDispatcher = Dispatchers.IO.limitedParallelism(1)
    @Volatile
    private var playerScope = CoroutineScope(SupervisorJob() + playerCommandDispatcher)
    @Volatile
    private var downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var inFlightSubtitleDownloadJob: Job? = null
    @Volatile
    private var cachedSubtitleTracks: List<NativeMpvTrack> = emptyList()
    @Volatile
    private var cachedAudioTracks: List<NativeMpvTrack> = emptyList()
    @Volatile
    private var pendingSubtitleUri: String? = null

    private fun ensureScopesActive() {
        synchronized(lifecycleLock) {
            if (!playerScope.isActive) {
                playerScope = CoroutineScope(SupervisorJob() + playerCommandDispatcher)
            }
            if (!downloadScope.isActive) {
                downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }
        }
    }

    private fun cancelInFlightSubtitleDownload() {
        inFlightSubtitleDownloadJob?.cancel()
        inFlightSubtitleDownloadJob = null
    }

    private fun executePlayerCommand(action: (Long) -> Unit) {
        val current = handle.takeIf { it != 0L } ?: return
        val source = pendingSource
        ensureScopesActive()
        playerScope.launch {
            val activeHandle = handle.takeIf { it != 0L } ?: return@launch
            if (activeHandle != current || pendingSource !== source) return@launch
            action(activeHandle)
        }
    }

    private var currentNowPlayingTitle: String = ""
    private var currentNowPlayingSubtitle: String = ""
    private var currentArtworkUrl: String = ""

    @Volatile
    private var handle: Long = 0L
    private var playbackBufferPreset = PlayerSettingsRepository.uiState.value.playbackBufferPreset
    private var lastAppliedPlaybackBuffer: Pair<Long, PlaybackBufferPreset>? = null

    /** Native teardown of the previous player, if one is still running. */
    @Volatile
    private var disposeInFlight: Thread? = null

    @Volatile
    private var pendingSource: PendingSource? = null
    @Volatile
    private var attachedSource: PendingSource? = null

    fun isAttachedTo(sourceUrl: String, sourceHeaders: Map<String, String>): Boolean =
        synchronized(lifecycleLock) {
            val attached = attachedSource
            !releaseRequested && handle != 0L && attached != null && attached === pendingSource &&
                attached.sourceUrl == sourceUrl && attached.headerLines == sourceHeaders.toHeaderLines()
        }
    @Volatile
    private var releaseRequested: Boolean = false
    val isReleasing: Boolean
        get() = releaseRequested
    private val createsInFlight = mutableSetOf<Thread>()
    private var createWaitInFlight: Thread? = null
    private val releaseCallbacks = mutableListOf<ReleaseCallback>()
    private var releaseInFlight: Thread? = null
    private var releaseTimedOut: Boolean = false
    private var terminalReleaseFailure: String? = null
    private var controlsState = PlayerControlsState()
    @Volatile
    private var currentVolumeLevel = rememberedVolumeLevel.coerceDesktopPlayerVolumeLevel()
    private var pendingSubtitleDelayMs: Int? = null
    private var pendingSubtitleStyle: SubtitleStyleState? = null
    private var pendingUseLibass: Boolean = false
    private var mediaContentType: MediaContentType = MediaContentType.UNKNOWN
    private var animeUpscalerOption: AnimeUpscalerOption = AnimeUpscalerOption.OFF
    private var animeUpscalerMode: AnimeUpscalerMode = AnimeUpscalerMode.FAST
    private var svpMotionOption: SvpMotionOption = SvpMotionOption.AUTO
    private var overrideAnimeUpscaler: Boolean? = null
    private var overrideSvpProfile: String? = null
    @Volatile
    private var animeUpscalerActive: Boolean = false
    @Volatile
    private var isAutoBypassed: Boolean = false
    @Volatile
    private var svpActive: Boolean = false
    @Volatile
    private var currentSvpProfile: String = ""
    private var rememberedAudioNightModeEnabled: Boolean = false
    private var isPlaybackStatsActive: Boolean = false
    private var currentPlaybackStats: PlayerPlaybackStats? = null
    private var statsJob: Job? = null
    private var lastSentControlsStructureKey: NativeControlsStructureKey? = null
    private var onAction: (PlayerControlsAction) -> Boolean = { false }
    private var onEvent: (String, Double) -> Boolean = { _, _ -> false }
    private var onScrubChange: (Long) -> Boolean = { false }
    private var onScrubFinished: (Long) -> Boolean = { false }
    private val eventSink = NativePlayerEventSink { type, value ->
        SwingUtilities.invokeLater {
            handlePlayerEvent(type, value)
        }
    }

    private fun setPlaybackStatsActive(active: Boolean) {
        if (isPlaybackStatsActive == active) return
        isPlaybackStatsActive = active
        statsJob?.cancel()
        statsJob = null
        if (active) {
            ensureScopesActive()
            statsJob = playerScope.launch {
                while (isActive && isPlaybackStatsActive) {
                    val current = handle
                    if (current != 0L) {
                        currentPlaybackStats = WindowsMpvSubStyleHelper.getPlaybackStats(current)
                        SwingUtilities.invokeLater {
                            if (!releaseRequested && isPlaybackStatsActive) {
                                lastSentControlsStructureKey = null
                                updateControls(controlsState)
                            }
                        }
                    }
                    delay(350L)
                }
            }
        } else {
            currentPlaybackStats = null
            lastSentControlsStructureKey = null
            updateControls(controlsState)
        }
    }

    fun attach(
        sourceUrl: String,
        sourceHeaders: Map<String, String>,
        playWhenReady: Boolean,
        initialPositionMs: Long,
        decoderPriority: Int,
        nvidiaRtxSuperResolutionEnabled: Boolean,
        onError: (String?) -> Unit,
    ) {
        ensureScopesActive()
        val pending = PendingSource(
            sourceUrl = sourceUrl,
            headerLines = sourceHeaders.toHeaderLines(),
            playWhenReady = playWhenReady,
            initialPositionMs = initialPositionMs.coerceAtLeast(0L),
            decoderPriority = decoderPriority,
            nvidiaRtxSuperResolutionEnabled = nvidiaRtxSuperResolutionEnabled,
            onError = onError,
        )
        var terminalFailure: String? = null
        val accepted = synchronized(lifecycleLock) {
            when {
                releaseRequested -> false
                terminalReleaseFailure != null -> {
                    terminalFailure = terminalReleaseFailure
                    false
                }
                else -> {
                    pendingSource = pending
                    true
                }
            }
        }
        if (!accepted) {
            terminalFailure?.let { message -> SwingUtilities.invokeLater { pending.onError(message) } }
            return
        }
        log.d {
            "attach requested source=${sourceUrl.toPlaybackLogKey()} headers=${sourceHeaders.size} " +
                "playWhenReady=$playWhenReady initialPositionMs=$initialPositionMs decoderPriority=$decoderPriority"
        }
        host.onPeerReady = { attachPending() }
        if (isHostDisplayable()) {
            attachPending()
        }
    }

    private fun attachPending() {
        val pending = pendingSource ?: return
        SwingUtilities.invokeLater {
            val terminalFailure = synchronized(lifecycleLock) { terminalReleaseFailure }
            if (terminalFailure != null) {
                if (!releaseRequested && pendingSource === pending) pending.onError(terminalFailure)
                return@invokeLater
            }
            if (
                releaseRequested ||
                pendingSource !== pending ||
                !isHostDisplayable()
            ) {
                return@invokeLater
            }
            if (deferAttachUntilCreatesComplete()) return@invokeLater
            disposePlayerHandle()
            val teardown = disposeInFlight
            if (teardown == null || !teardown.isAlive) {
                createPlayer(pending)
                return@invokeLater
            }
            // The previous player is still tearing down natively. It owns child windows of this
            // same host, so creating the next one on top of it races its teardown and can leave the
            // new player wedged (controls never resized, playback never starts). Wait for it, but
            // off the EDT, because the teardown itself needs the EDT to keep pumping messages.
            Thread({
                runCatching { teardown.join(TEARDOWN_WAIT_MS) }
                val teardownCompleted = !teardown.isAlive
                SwingUtilities.invokeLater {
                    val terminalFailure = synchronized(lifecycleLock) { terminalReleaseFailure }
                    if (terminalFailure != null) {
                        if (!releaseRequested && pendingSource === pending) pending.onError(terminalFailure)
                        return@invokeLater
                    }
                    if (!releaseRequested && isHostDisplayable() && pendingSource === pending) {
                        if (teardownCompleted) {
                            createPlayer(pending)
                        } else {
                            log.w { "attach aborted because previous native teardown exceeded ${TEARDOWN_WAIT_MS}ms" }
                            if (!releaseRequested && pendingSource === pending) {
                                pending.onError("Previous player is still shutting down. Please try again.")
                            }
                        }
                    }
                }
            }, "nuvio-player-attach").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun deferAttachUntilCreatesComplete(): Boolean = synchronized(lifecycleLock) {
        createsInFlight.removeAll { worker -> !worker.isAlive }
        if (createsInFlight.isEmpty()) return@synchronized false
        if (createWaitInFlight?.isAlive == true) return@synchronized true
        val workers = createsInFlight.toList()
        val pendingAtWaitStart = pendingSource
        val worker = Thread({
            val deadlineNanos = System.nanoTime() + createWaitTimeoutMs * 1_000_000L
            var interrupted = false
            workers.forEach { create ->
                while (create.isAlive) {
                    val remainingNanos = deadlineNanos - System.nanoTime()
                    if (remainingNanos <= 0L) break
                    try {
                        val millis = (remainingNanos / 1_000_000L).coerceAtLeast(1L)
                        create.join(millis)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
            synchronized(lifecycleLock) {
                if (createWaitInFlight === Thread.currentThread()) createWaitInFlight = null
            }
            runCatching { onCreateWaitCompleted() }
            SwingUtilities.invokeLater {
                if (releaseRequested || !runCatching { isHostDisplayable() }.getOrDefault(false)) {
                    return@invokeLater
                }
                val (current, terminalFailure) = synchronized(lifecycleLock) {
                    createsInFlight.removeAll { create -> !create.isAlive }
                    pendingSource to terminalReleaseFailure
                }
                if (terminalFailure != null) {
                    current?.onError(terminalFailure)
                    return@invokeLater
                }
                val capturedCreatesRemain = workers.any { create -> create.isAlive }
                when {
                    current == null -> Unit
                    !capturedCreatesRemain -> attachPending()
                    current !== pendingAtWaitStart -> attachPending()
                    else -> current.onError("Previous player is still starting. Please try again.")
                }
            }
        }, "nuvio-player-create-wait").apply { isDaemon = true }
        createWaitInFlight = worker
        worker.start()
        true
    }

    private fun createPlayer(pending: PendingSource) {
        val terminalFailure = synchronized(lifecycleLock) { terminalReleaseFailure }
        if (terminalFailure != null) {
            if (!releaseRequested && pendingSource === pending) pending.onError(terminalFailure)
            return
        }
        // Resolving the AWT peer must happen on the EDT; everything after it must not.
        val hostViewPtr = runCatching { resolveHostView() }
            .getOrElse { error ->
                log.w(error) { "attach failed to resolve host source=${pending.sourceUrl.toPlaybackLogKey()}" }
                if (!releaseRequested && pendingSource === pending) pending.onError(error.message)
                return
            }
        val resolvedSource = if (pending.sourceUrl.startsWith("file:", ignoreCase = true)) {
            runCatching { java.io.File(java.net.URI(pending.sourceUrl)).absolutePath }.getOrElse {
                val stripped = pending.sourceUrl.replaceFirst(Regex("^file:/{1,3}", RegexOption.IGNORE_CASE), "")
                runCatching { java.net.URLDecoder.decode(stripped, "UTF-8") }.getOrDefault(stripped)
            }
        } else {
            pending.sourceUrl
        }

        // Native create blocks until the player's own UI thread finishes initialising, and that
        // thread creates child windows of the AWT host, which needs the EDT to keep pumping
        // messages. Creating on the EDT is therefore the same circular wait that the teardown had:
        // the app stops responding and Windows closes it as "stopped interacting" (Hang 1002).
        // Create off the EDT and come back to it for the parts that touch Swing state.
        val createWorker = Thread({
            try {
                runCatching {
                    nativeCreate(
                        hostViewPtr,
                        resolvedSource,
                        pending.headerLines.toTypedArray(),
                        pending.playWhenReady,
                        pending.initialPositionMs,
                        NativePlayerBridge.controlsPageUrl,
                        pending.decoderPriority,
                        pending.nvidiaRtxSuperResolutionEnabled,
                        eventSink,
                    ).also { if (it == 0L) error("Native player did not return a handle.") }
                }.onSuccess { created ->
                    val accepted = synchronized(lifecycleLock) {
                        if (!releaseRequested && terminalReleaseFailure == null && pendingSource === pending) {
                            handle = created
                            attachedSource = pending
                            lastAppliedPlaybackBuffer = null
                            true
                        } else {
                            false
                        }
                    }
                    if (!accepted) {
                        runCatching { nativeDispose(created) }
                            .onFailure { error -> recordTerminalDisposeFailure(error, "superseded create", created) }
                        return@onSuccess
                    }
                    SwingUtilities.invokeLater {
                        val shouldConfigure = synchronized(lifecycleLock) {
                            when {
                                handle != created -> false
                                pendingSource !== pending || !isHostDisplayable() -> {
                                    handle = 0L
                                    attachedSource = null
                                    lastSentControlsStructureKey = null
                                    startTrackedDisposeLocked(created)
                                    false
                                }
                                else -> true
                            }
                        }
                        if (!shouldConfigure) return@invokeLater
                        log.d {
                            "attach created handle=$created source=${resolvedSource.toPlaybackLogKey()} " +
                                "initialPositionMs=${pending.initialPositionMs}"
                        }
                        applyRememberedVolume()
                        updateControls(controlsState)
                        setResizeMode(rememberedResizeMode)
                        if (currentNowPlayingTitle.isNotEmpty() || currentArtworkUrl.isNotEmpty()) {
                            setNowPlayingMetadata(currentNowPlayingTitle, currentNowPlayingSubtitle, currentArtworkUrl)
                        }
                        applyPendingSubtitleSettings(created)
                        applyPlaybackBufferPreset(created)
                        pendingSubtitleUri?.let { setSubtitleUri(it) }
                        applyCoordinatedEnhancements(created)
                        applyAudioNightMode(created, rememberedAudioNightModeEnabled)
                    }
                }.onFailure { error ->
                    log.w(error) { "attach failed source=${pending.sourceUrl.toPlaybackLogKey()}" }
                    SwingUtilities.invokeLater {
                        if (!releaseRequested && pendingSource === pending) pending.onError(error.message)
                    }
                }
            } finally {
                synchronized(lifecycleLock) {
                    createsInFlight.remove(Thread.currentThread())
                }
            }
        }, "nuvio-player-create").apply { isDaemon = true }
        var admissionFailure: String? = null
        val admitted = synchronized(lifecycleLock) {
            when {
                releaseRequested || pendingSource !== pending -> false
                terminalReleaseFailure != null -> {
                    admissionFailure = terminalReleaseFailure
                    false
                }
                else -> {
                    createsInFlight += createWorker
                    createWorker.start()
                    true
                }
            }
        }
        if (!admitted) {
            admissionFailure?.let { message ->
                SwingUtilities.invokeLater {
                    if (!releaseRequested && pendingSource === pending) pending.onError(message)
                }
            }
            return
        }
    }

    fun setControlCallbacks(
        onAction: (PlayerControlsAction) -> Boolean,
        onEvent: (String, Double) -> Boolean,
        onScrubChange: (Long) -> Boolean,
        onScrubFinished: (Long) -> Boolean,
    ) {
        this.onAction = onAction
        this.onEvent = onEvent
        this.onScrubChange = onScrubChange
        this.onScrubFinished = onScrubFinished
        log.d { "control callbacks attached handle=$handle" }
        host.onCursorActivity = {
            this.onEvent("cursorActivity", 0.0)
        }
    }

    // The embedded controls WebView can lose keyboard focus when the desktop
    // window is reactivated (for example after alt-tab). Re-apply native focus
    // whenever the host window gains focus. Returns the uninstaller; null until
    // the host is inside a window (the ancestor does not exist before first paint).
    fun installWindowFocusForwarding(): (() -> Unit)? {
        val window = SwingUtilities.getWindowAncestor(host) ?: return null
        val listener = object : WindowAdapter() {
            override fun windowGainedFocus(event: WindowEvent) {
                requestKeyboardFocus()
            }
        }
        window.addWindowFocusListener(listener)
        return { window.removeWindowFocusListener(listener) }
    }

    @Synchronized
    fun updateControls(state: PlayerControlsState) {
        val wasMiniPlayer = controlsState.isMiniPlayer
        host.setControlsVisible(state.controlsVisible)
        val currentHandle = handle
        val current = currentHandle.takeIf { it != 0L } ?: run {
            controlsState = state
            return
        }
        val stateWithVolume = state.copy(
            volumeLevel = resolveDesktopPlayerVolumeLevel(
                requestedLevel = state.volumeLevel,
                currentLevel = currentVolumeLevel,
                rememberedLevel = rememberedVolumeLevel,
            ),
        )
        val stateWithEnhancements = stateWithVolume.copy(
            animeUpscalerActive = animeUpscalerActive,
            animeUpscalerEnabled = animeUpscalerActive,
            isAutoBypassed = isAutoBypassed,
            animeUpscalerOption = animeUpscalerOption,
            animeUpscalerModeIndex = animeUpscalerMode.index,
            svpMode = overrideSvpProfile ?: when (svpMotionOption) {
                SvpMotionOption.OFF -> "off"
                SvpMotionOption.AUTO -> "auto"
                SvpMotionOption.GLOBAL -> "global"
            },
            svpActive = svpActive,
            svpProfile = currentSvpProfile,
            svpAvailable = runCatching { NativePlayerBridge.isSvpAvailable() }.getOrDefault(false),
            mediaContentType = mediaContentType,
            playbackStats = currentPlaybackStats,
        )
        currentVolumeLevel = stateWithEnhancements.volumeLevel ?: currentVolumeLevel
        controlsState = stateWithEnhancements
        val isFullscreen = isDesktopAppFullscreen(SwingUtilities.getWindowAncestor(host))
        val structureKey = NativeControlsStructureKey(
            state = stateWithEnhancements.nativeControlsStructureKey(),
            isFullscreen = isFullscreen,
            isInPip = DesktopPlayerPictureInPicture.isEnabled,
        )
        if (structureKey == lastSentControlsStructureKey) return
        lastSentControlsStructureKey = structureKey
        log.d {
            "updateControls handle=$current title=${stateWithEnhancements.title.take(40)} " +
                "pos=${stateWithEnhancements.positionMs} duration=${stateWithEnhancements.durationMs} " +
                "speed=${stateWithEnhancements.playbackSpeedLabel} audioLabel=${stateWithEnhancements.audioLabel} " +
                "subsLabel=${stateWithEnhancements.subtitlesLabel} fullscreen=$isFullscreen"
        }
        NativePlayerBridge.updateControls(current, stateWithEnhancements.toControlsJson(isFullscreen, ::refreshControlsForImage))
        if (wasMiniPlayer && !state.isMiniPlayer) {
            requestKeyboardFocus()
        }
    }

    private fun refreshControlsForImage() {
        SwingUtilities.invokeLater {
            if (releaseRequested) return@invokeLater
            lastSentControlsStructureKey = null
            updateControls(controlsState)
        }
    }

    fun onDesktopFullscreenChanged() {
        lastSentControlsStructureKey = null
        updateControls(controlsState)
        requestKeyboardFocus()
    }

    fun reparentSurface(host: Component): Boolean {
        val current = handle.takeIf { it != 0L } ?: return false
        if (!host.isDisplayable) return false
        val pointer = runCatching { AwtNativeViewResolver.resolveNativeViewPointer(host) }
            .onFailure { error -> log.w(error) { "failed to resolve PiP native host ${host.javaClass.name}" } }
            .getOrNull() ?: return false
        return NativePlayerBridge.reparentSurface(current, pointer)
    }

    fun layoutNativeSubviews() {
        val current = handle.takeIf { it != 0L } ?: return
        NativePlayerBridge.layoutNativeSubviews(current)
    }

    override fun requestKeyboardFocus() {
        SwingUtilities.invokeLater {
            val current = handle.takeIf { it != 0L } ?: return@invokeLater
            if (DesktopPlayerPictureInPicture.isEnabled) {
                DesktopPlayerPictureInPicture.pipWindow?.videoHolderPanel?.requestFocusInWindow()
                NativePlayerBridge.requestFocus(current)
                return@invokeLater
            }
            if (!isHostDisplayable()) return@invokeLater
            val window = SwingUtilities.getWindowAncestor(host)
            if (window != null && !window.isFocused) {
                window.toFront()
                window.requestFocus()
            }
            if (!host.requestFocusInWindow()) {
                host.requestFocus()
            }
            NativePlayerBridge.requestFocus(current)
        }
    }

    fun setNowPlayingMetadata(title: String?, subtitle: String?, artworkUrl: String?) {
        if (DesktopHostOs.current != DesktopHostOs.MACOS) return
        currentNowPlayingTitle = title.orEmpty()
        currentNowPlayingSubtitle = subtitle.orEmpty()
        currentArtworkUrl = artworkUrl.orEmpty()
        handle.takeIf { it != 0L }?.let { current ->
            NativePlayerBridge.setNowPlayingMetadata(
                current,
                currentNowPlayingTitle,
                currentNowPlayingSubtitle,
                currentArtworkUrl,
            )
        }
    }

    fun setResizeMode(mode: PlayerResizeMode) {
        rememberedResizeMode = mode
        handle.takeIf { it != 0L }?.let { current ->
            NativePlayerBridge.setResizeMode(
                handle = current,
                mode = when (mode) {
                    PlayerResizeMode.Fit -> 0
                    PlayerResizeMode.Fill -> 1
                    PlayerResizeMode.Zoom -> 2
                    PlayerResizeMode.Stretch -> 3
                },
            )
        }
    }

    fun setAnimeUpscaler(enabled: Boolean, mode: AnimeUpscalerMode) {
        animeUpscalerOption = if (enabled) AnimeUpscalerOption.ALWAYS_ON else AnimeUpscalerOption.OFF
        animeUpscalerMode = mode
        overrideAnimeUpscaler = enabled
        handle.takeIf { it != 0L }?.let { current ->
            applyCoordinatedEnhancements(current)
        }
    }

    fun setVideoEnhancements(
        mediaContentType: MediaContentType,
        animeUpscalerOption: AnimeUpscalerOption,
        animeUpscalerMode: AnimeUpscalerMode,
        svpMotionOption: SvpMotionOption,
    ) {
        this.mediaContentType = mediaContentType
        this.animeUpscalerOption = animeUpscalerOption
        this.animeUpscalerMode = animeUpscalerMode
        this.svpMotionOption = svpMotionOption
        handle.takeIf { it != 0L }?.let { current ->
            applyCoordinatedEnhancements(current)
        }
    }

    fun setPlaybackBufferPreset(preset: PlaybackBufferPreset) = synchronized(lifecycleLock) {
        playbackBufferPreset = preset
        handle.takeIf { it != 0L }?.let(::applyPlaybackBufferPreset)
    }

    private fun applyPlaybackBufferPreset(target: Long) = synchronized(lifecycleLock) {
        if (handle != target || releaseRequested) return@synchronized
        val update = target to playbackBufferPreset
        if (lastAppliedPlaybackBuffer == update) return@synchronized
        runCatching { nativeSetPlaybackBufferPreset(target, playbackBufferPreset) }
            .onSuccess { lastAppliedPlaybackBuffer = update }
            .onFailure { log.w(it) { "Failed to update playback buffer handle=$target" } }
    }

    private fun computeUpscalerState(): Pair<Boolean, Boolean> {
        if (overrideAnimeUpscaler != null) {
            val active = overrideAnimeUpscaler == true
            return Pair(active, false)
        }
        return when (animeUpscalerOption) {
            AnimeUpscalerOption.OFF -> Pair(false, false)
            AnimeUpscalerOption.ALWAYS_ON -> Pair(true, false)
            AnimeUpscalerOption.AUTO -> {
                when (mediaContentType) {
                    MediaContentType.ANIME, MediaContentType.CARTOON -> Pair(true, false)
                    MediaContentType.LIVE_ACTION, MediaContentType.UNKNOWN -> Pair(false, true)
                }
            }
        }
    }

    private fun computeSvpState(): Pair<Boolean, String> {
        val resolvedMode = overrideSvpProfile ?: when (svpMotionOption) {
            SvpMotionOption.OFF -> "off"
            SvpMotionOption.AUTO -> "auto"
            SvpMotionOption.GLOBAL -> "global"
        }
        return when (resolvedMode) {
            "off" -> Pair(false, "")
            "anime" -> Pair(true, "anime")
            "cinema" -> Pair(true, "cinema")
            "auto" -> {
                if (mediaContentType == MediaContentType.ANIME || mediaContentType == MediaContentType.CARTOON) {
                    Pair(true, "anime")
                } else {
                    Pair(false, "")
                }
            }
            "global" -> {
                if (mediaContentType == MediaContentType.ANIME || mediaContentType == MediaContentType.CARTOON) {
                    Pair(true, "anime")
                } else {
                    Pair(true, "cinema")
                }
            }
            else -> Pair(false, "")
        }
    }

    private fun applyCoordinatedEnhancements(targetHandle: Long) {
        val (upscalerActive, autoBypassed) = computeUpscalerState()
        animeUpscalerActive = upscalerActive
        isAutoBypassed = autoBypassed

        val isSvpRuntimeAvailable = runCatching { NativePlayerBridge.isSvpAvailable() }.getOrDefault(false)
        val (rawSvpIsActive, svpProf) = computeSvpState()
        val svpIsActive = rawSvpIsActive && isSvpRuntimeAvailable
        svpActive = svpIsActive
        currentSvpProfile = if (isSvpRuntimeAvailable) svpProf else ""

        val shaderPath = if (upscalerActive) {
            AnimeShaderProvider.getShaderPath(animeUpscalerMode).orEmpty()
        } else {
            ""
        }
        runCatching {
            NativePlayerBridge.setGlslShaders(targetHandle, shaderPath)
        }.onFailure { error ->
            log.w(error) { "Failed to set GLSL shaders handle=$targetHandle active=$upscalerActive mode=$animeUpscalerMode" }
        }

        val scriptPath = if (svpIsActive && svpProf.isNotEmpty()) {
            val prof = if (svpProf == "cinema") SvpProfile.CINEMA else SvpProfile.ANIME
            SvpScriptProvider.getScriptPath(prof).orEmpty()
        } else {
            ""
        }
        val svpEnabled = svpIsActive && scriptPath.isNotEmpty()
        runCatching {
            NativePlayerBridge.setSvpFilter(targetHandle, svpEnabled, scriptPath)
        }.onFailure { error ->
            log.w(error) { "Failed to set SVP filter handle=$targetHandle enabled=$svpEnabled script=$scriptPath" }
        }

        lastSentControlsStructureKey = null
        updateControls(controlsState)
    }

    fun setAudioNightMode(enabled: Boolean) {
        rememberedAudioNightModeEnabled = enabled
        handle.takeIf { it != 0L }?.let { current ->
            applyAudioNightMode(current, enabled)
        }
    }

    private fun applyAudioNightMode(targetHandle: Long, enabled: Boolean) {
        val filter = if (enabled) {
            "dynaudnorm=f=75:g=25:p=0.55:m=2.0"
        } else {
            ""
        }
        runCatching {
            NativePlayerBridge.setAudioFilter(targetHandle, filter)
        }.onFailure { error ->
            log.w(error) { "Failed to set audio filter handle=$targetHandle enabled=$enabled" }
        }
    }

    private fun handlePlayerEvent(type: String, value: Double) {
        if (type.shouldLogNativeControlEvent()) {
            log.d { "event received handle=$handle type=$type value=$value" }
        }
        when (type) {
            "controlsUnavailable" -> {
                log.e { "Player controls initialization failed HRESULT=${value.toLong()} handle=$handle" }
                pendingSource?.onError("Player controls could not be loaded. Please reopen the player.")
            }
            "svp_unavailable" -> {
                log.w { "SVP unavailable: VapourSynth runtime is missing." }
                controlsState = controlsState.copy(
                    notificationMessage = if (DesktopHostOs.current == DesktopHostOs.WINDOWS)
                        "Motion smoothing is unavailable. Check its status in Settings."
                    else "Motion Smoothing requires VapourSynth or SVP 4 to be installed.",
                    notificationToken = System.currentTimeMillis()
                )
                overrideSvpProfile = "off"
                lastSentControlsStructureKey = null
                updateControls(controlsState)
            }
            "svp_error" -> {
                log.w { "SVP error occurred during script execution." }
                controlsState = controlsState.copy(
                    notificationMessage = "Motion Smoothing error occurred. Disabled SVP.",
                    notificationToken = System.currentTimeMillis()
                )
                overrideSvpProfile = "off"
                lastSentControlsStructureKey = null
                updateControls(controlsState)
            }
            "cursorActivity" -> host.noteCursorActivity()
            "scrubChange" -> {
                val handled = onScrubChange(value.toLong())
                log.d { "scrubChange positionMs=${value.toLong()} handled=$handled handle=$handle" }
                if (!handled) {
                    updateLocalProgress(value.toLong())
                }
            }
            "scrubFinish" -> {
                val scrubHandled = onScrubFinished(value.toLong())
                log.d { "scrubFinish positionMs=${value.toLong()} handled=$scrubHandled handle=$handle" }
                if (!scrubHandled) {
                    seekTo(value.toLong())
                }
            }
            "keyboardFineSeekBack",
            "keyboardFineSeekForward" -> {
                val handled = onScrubFinished(value.toLong())
                log.d { "$type positionMs=${value.toLong()} handled=$handled handle=$handle" }
                if (!handled) {
                    seekTo(value.toLong())
                }
            }
            "toggleFullscreen" -> {
                if (onEvent(type, value)) 
                    return
                if (DesktopPlayerPictureInPicture.isEnabled) {
                    DesktopPlayerPictureInPicture.toggle()
                } else {
                    toggleDesktopAppFullscreen(SwingUtilities.getWindowAncestor(host))
                    onDesktopFullscreenChanged()
                }
            }
            "dragWindow" -> NativePlayerBridge.beginWindowDrag(handle)
            "externalSubtitleImported" -> {
                val activeHandle = handle
                if (activeHandle != 0L) {
                    val info = runCatching { NativePlayerBridge.popLastImportedSubtitle(activeHandle) }.getOrNull()
                    if (!info.isNullOrBlank()) {
                        val parts = info.split("|", limit = 2)
                        val filePath = parts.getOrNull(0).orEmpty()
                        val fileName = parts.getOrNull(1).takeIf { !it.isNullOrBlank() }
                            ?: filePath.substringAfterLast('\\').substringAfterLast('/')
                        if (filePath.isNotBlank()) {
                            SubtitleImportBridge.onImport?.invoke(ImportedSubtitleFile(filePath, fileName))
                        }
                    }
                }
            }
            "miniPlayerDragX" -> InAppPlayerManager.dragBy(value.toFloat(), 0f)
            "miniPlayerDragY" -> InAppPlayerManager.dragBy(0f, value.toFloat())
            "resizePip" -> DesktopPlayerPictureInPicture.resizeWindow(value)
            "volumeChange" -> setFallbackVolume(value.toFloat())
            "volumeChangeTemporary" -> setTemporaryVolume(value.toFloat())
            "setPlaybackSpeed" -> {
                if (onEvent(type, value)) return
                setPlaybackSpeed(value.toFloat())
            }
            "setAnimeUpscalerOption" -> {
                overrideAnimeUpscaler = null
                val option = when (value.toInt()) {
                    0 -> AnimeUpscalerOption.OFF
                    1 -> AnimeUpscalerOption.AUTO
                    2 -> AnimeUpscalerOption.ALWAYS_ON
                    else -> AnimeUpscalerOption.OFF
                }
                animeUpscalerOption = option
                PlayerSettingsRepository.setAnimeUpscalerOption(option)
                handle.takeIf { it != 0L }?.let { applyCoordinatedEnhancements(it) }
            }
            "forceAnimeUpscalerOn" -> {
                overrideAnimeUpscaler = true
                animeUpscalerOption = AnimeUpscalerOption.ALWAYS_ON
                PlayerSettingsRepository.setAnimeUpscalerOption(AnimeUpscalerOption.ALWAYS_ON)
                handle.takeIf { it != 0L }?.let { applyCoordinatedEnhancements(it) }
            }
            "setAnimeUpscalerMode" -> {
                val mode = AnimeUpscalerMode.fromIndex(value.toInt())
                animeUpscalerMode = mode
                PlayerSettingsRepository.setAnimeUpscalerMode(mode)
                handle.takeIf { it != 0L }?.let { applyCoordinatedEnhancements(it) }
            }
            "setSvpMode" -> {
                val mode = value.toInt()
                if (mode != 0 && !runCatching { NativePlayerBridge.isSvpAvailable() }.getOrDefault(false)) {
                    controlsState = controlsState.copy(
                        notificationMessage = if (DesktopHostOs.current == DesktopHostOs.WINDOWS)
                            "Motion smoothing is unavailable. Check its status in Settings."
                        else "Motion Smoothing requires VapourSynth or SVP 4 to be installed.",
                        notificationToken = System.currentTimeMillis()
                    )
                    overrideSvpProfile = "off"
                    svpMotionOption = SvpMotionOption.OFF
                    PlayerSettingsRepository.setSvpMotionOption(SvpMotionOption.OFF)
                    lastSentControlsStructureKey = null
                    updateControls(controlsState)
                } else {
                    overrideSvpProfile = null
                    when (mode) {
                        0 -> {
                            svpMotionOption = SvpMotionOption.OFF
                            PlayerSettingsRepository.setSvpMotionOption(SvpMotionOption.OFF)
                        }
                        1 -> {
                            svpMotionOption = SvpMotionOption.AUTO
                            PlayerSettingsRepository.setSvpMotionOption(SvpMotionOption.AUTO)
                        }
                        2, 3 -> {
                            svpMotionOption = SvpMotionOption.GLOBAL
                            PlayerSettingsRepository.setSvpMotionOption(SvpMotionOption.GLOBAL)
                        }
                    }
                    handle.takeIf { it != 0L }?.let { applyCoordinatedEnhancements(it) }
                }
            }
            "toggleAnimeUpscaler" -> {
                val enabled = value >= 0.5
                overrideAnimeUpscaler = enabled
                val option = if (enabled) AnimeUpscalerOption.ALWAYS_ON else AnimeUpscalerOption.OFF
                animeUpscalerOption = option
                PlayerSettingsRepository.setAnimeUpscalerOption(option)
                handle.takeIf { it != 0L }?.let { applyCoordinatedEnhancements(it) }
            }
            "setPlaybackStatsVisible" -> setPlaybackStatsActive(value > 0.5)
            "togglePlaybackStats" -> setPlaybackStatsActive(!isPlaybackStatsActive)
            else -> {
                val eventHandled = onEvent(type, value)
                if (type.shouldLogNativeControlEvent()) {
                    log.d { "event delegated type=$type handled=$eventHandled handle=$handle" }
                }
                if (eventHandled) return
                val action = type.toPlayerControlsAction()
                if (action == null) return
                val actionHandled = onAction(action)
                log.d { "action delegated action=$action handled=$actionHandled handle=$handle" }
                if (!actionHandled) {
                    handleFallbackAction(action)
                }
            }
        }
    }

    @Synchronized
    private fun updateLocalProgress(positionMs: Long) {
        controlsState = controlsState.copy(positionMs = positionMs)
        updateControls(controlsState)
    }

    private fun handleFallbackAction(action: PlayerControlsAction) {
        log.d { "fallback action=$action handle=$handle" }
        when (action) {
            PlayerControlsAction.TogglePlayback,
            PlayerControlsAction.KeyboardTogglePlayback -> {
                val current = handle
                if (current == 0L) return
                val isEnded = NativePlayerBridge.isEnded(current)
                val isPaused = NativePlayerBridge.isPaused(current)
                val duration = nativeDurationMs(current)
                val pos = nativePositionMs(current)
                val trulyEnded = isEnded && (duration <= 0L || pos >= duration - 3_000L)
                if (trulyEnded) {
                    NativePlayerBridge.seekTo(current, 0L)
                    NativePlayerBridge.setPaused(current, false)
                } else {
                    NativePlayerBridge.setPaused(current, !isPaused)
                }
            }
            PlayerControlsAction.SeekBack,
            PlayerControlsAction.KeyboardSeekBack -> fallbackSeekBy(-3_000L)
            PlayerControlsAction.KeyboardSeek10Back -> fallbackSeekBy(-10_000L)
            PlayerControlsAction.SeekForward,
            PlayerControlsAction.KeyboardSeekForward -> fallbackSeekBy(3_000L)
            PlayerControlsAction.KeyboardSeek10Forward -> fallbackSeekBy(10_000L)
            PlayerControlsAction.KeyboardFineSeekBack -> fallbackSeekBy(-1_000L)
            PlayerControlsAction.KeyboardFineSeekForward -> fallbackSeekBy(1_000L)
            PlayerControlsAction.KeyboardVolumeDown -> adjustFallbackVolume(-10f)
            PlayerControlsAction.KeyboardVolumeUp -> adjustFallbackVolume(10f)
            PlayerControlsAction.PictureInPicture -> togglePictureInPictureFromShortcut()
            PlayerControlsAction.Speed -> cycleFallbackSpeed()
            else -> Unit
        }
    }

    fun togglePictureInPictureFromShortcut() {
        DesktopPlayerPictureInPicture.toggle()
    }

    @Synchronized
    private fun adjustFallbackVolume(delta: Float) {
        val current = handle
        if (current != 0L) {
            val currentLevel = resolveDesktopPlayerVolumeLevel(
                requestedLevel = null,
                currentLevel = currentVolumeLevel,
                rememberedLevel = rememberedVolumeLevel,
            )
            val nextLevel = (currentLevel + (delta / 100f)).coerceDesktopPlayerVolumeLevel()
            setFallbackVolume(nextLevel)
        }
    }

    @Synchronized
    private fun setFallbackVolume(level: Float) {
        val current = handle
        if (current != 0L) {
            val nextLevel = level.coerceDesktopPlayerVolumeLevel()
            rememberedVolumeLevel = nextLevel
            currentVolumeLevel = nextLevel
            DesktopPlayerVolumeStorage.saveVolumeLevel(nextLevel)
            NativePlayerBridge.setVolume(current, nextLevel)
            controlsState = controlsState.copy(volumeLevel = nextLevel)
            updateControls(controlsState)
        }
    }

    @Synchronized
    private fun setTemporaryVolume(level: Float) {
        val current = handle
        if (current != 0L) {
            val nextLevel = level.coerceDesktopPlayerVolumeLevel()
            currentVolumeLevel = nextLevel
            NativePlayerBridge.setVolume(current, nextLevel)
            controlsState = controlsState.copy(volumeLevel = nextLevel)
            updateControls(controlsState)
        }
    }

    @Synchronized
    private fun applyRememberedVolume() {
        val current = handle
        if (current == 0L) return
        val level = rememberedVolumeLevel.coerceDesktopPlayerVolumeLevel()
        currentVolumeLevel = level
        NativePlayerBridge.setVolume(current, level)
        controlsState = controlsState.copy(volumeLevel = level)
        log.d { "applied remembered volume level=$level handle=$current" }
    }

    private fun fallbackSeekBy(offsetMs: Long) {
        val current = handle
        if (current != 0L) {
            nativeSeekBy(current, offsetMs)
        }
    }

    private fun cycleFallbackSpeed() {
        val current = handle
        if (current == 0L) return
        val speeds = listOf(1f, 1.25f, 1.5f, 2f)
        val currentSpeed = NativePlayerBridge.speed(current)
        val next = speeds.firstOrNull { it > currentSpeed + 0.01f } ?: speeds.first()
        NativePlayerBridge.setSpeed(current, next)
    }

    fun snapshot(): PlayerPlaybackSnapshot {
        val current = handle
        if (current == 0L) return PlayerPlaybackSnapshot(isLoading = true)
        return runCatching {
            val isLoading = NativePlayerBridge.isLoading(current)
            val isPaused = NativePlayerBridge.isPaused(current)
            val durationMs = NativePlayerBridge.durationMs(current)
            val positionMs = NativePlayerBridge.positionMs(current)
            val rawBufferedMs = NativePlayerBridge.bufferedPositionMs(current)
            val rawIsEnded = NativePlayerBridge.isEnded(current)
            val isEnded = rawIsEnded && (durationMs <= 0L || positionMs >= durationMs - 3_000L)
            val bufferedPositionMs = if (durationMs > 2_000L && (rawBufferedMs >= durationMs - 2_000L || positionMs >= durationMs - 1_500L)) {
                durationMs
            } else {
                rawBufferedMs
            }
            val cacheSpeed = WindowsMpvSubStyleHelper.getCacheSpeed(current)
            PlayerPlaybackSnapshot(
                isLoading = isLoading,
                isPlaying = !isPaused && !isLoading && !isEnded,
                isPaused = isPaused,
                isEnded = isEnded,
                durationMs = durationMs,
                positionMs = positionMs,
                bufferedPositionMs = bufferedPositionMs,
                playbackSpeed = NativePlayerBridge.speed(current),
                downloadSpeedBytes = cacheSpeed,
                downloadSpeedAvailable = WindowsMpvSubStyleHelper.supportsCacheSpeed(),
            )
        }.getOrDefault(PlayerPlaybackSnapshot(isLoading = true))
    }

    fun releaseBeforeNavigation(onReleased: () -> Unit) {
        releaseBeforeNavigation(onReleased, onReleaseFailed = {})
    }

    override fun releaseBeforeNavigation(
        onReleased: () -> Unit,
        onReleaseFailed: (String) -> Unit,
    ) {
        synchronized(lifecycleLock) {
            releaseRequested = true
        }
        host.resetCursorVisibility()
        invalidateForRelease()
        val callback = ReleaseCallback(onReleased, onReleaseFailed)
        var shouldStart = false
        var immediateFailure: String? = null
        val worker = synchronized(lifecycleLock) {
            val active = releaseInFlight?.takeIf { it.isAlive }
            when {
                terminalReleaseFailure != null -> {
                    immediateFailure = terminalReleaseFailure
                    active ?: Thread.currentThread()
                }
                active != null && releaseTimedOut -> {
                    immediateFailure = "Native player is still shutting down. Please retry or quit Nuvio."
                    active
                }
                else -> {
                    releaseCallbacks += callback
                    active ?: Thread({
                    awaitThreadCompletion(disposeInFlight, "native teardown")
                    awaitInFlightCreates()
                    // A create worker publishes/configures its result on the EDT before leaving
                    // createsInFlight. Release must drain those already-queued callbacks before
                    // taking the final disposal snapshot: a stale publication can reject its
                    // handle and start a tracked disposal after the create thread has exited.
                    awaitSwingCallbacksQueuedByCreates()
                    awaitThreadCompletion(disposeInFlight, "native teardown queued by create")
                    var failureMessage = synchronized(lifecycleLock) { terminalReleaseFailure }
                    val current = if (failureMessage == null) detachPlayerHandle() else 0L
                    if (current != 0L) {
                        log.d { "pre-navigation dispose started handle=$current" }
                        runCatching { nativeDispose(current) }
                            .onSuccess { log.d { "pre-navigation dispose completed handle=$current" } }
                            .onFailure { error ->
                                failureMessage = "Native player shutdown failed. Quit Nuvio before removing the player."
                                log.w(error) { "pre-navigation dispose failed handle=$current" }
                            }
                    }
                    val callbacks = synchronized(lifecycleLock) {
                        releaseInFlight = null
                        releaseTimedOut = false
                        if (failureMessage != null) terminalReleaseFailure = failureMessage
                        releaseCallbacks.toList().also { releaseCallbacks.clear() }
                    }
                    SwingUtilities.invokeLater {
                        callbacks.forEach { pending ->
                            val invoke = if (failureMessage == null) {
                                { pending.onReleased() }
                            } else {
                                { pending.onFailed(failureMessage) }
                            }
                            runCatching(invoke)
                                .onFailure { error -> log.w(error) { "release callback failed" } }
                        }
                    }
                }, "nuvio-player-release").apply {
                    isDaemon = true
                    releaseInFlight = this
                    releaseTimedOut = false
                    shouldStart = true
                }
            }
        }
        }
        if (immediateFailure != null) {
            SwingUtilities.invokeLater {
                runCatching { callback.onFailed(immediateFailure) }
                    .onFailure { error -> log.w(error) { "immediate release failure callback failed" } }
            }
            return
        }
        if (shouldStart) {
            worker.start()
            startReleaseWatchdog(worker)
        }
    }

    private fun startReleaseWatchdog(worker: Thread) {
        Thread({
            try {
                Thread.sleep(releaseTimeoutMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return@Thread
            }
            val callbacks = synchronized(lifecycleLock) {
                if (releaseInFlight !== worker || !worker.isAlive) return@synchronized emptyList()
                releaseTimedOut = true
                releaseCallbacks.toList().also { releaseCallbacks.clear() }
            }
            if (callbacks.isEmpty()) return@Thread
            SwingUtilities.invokeLater {
                callbacks.forEach { pending ->
                    runCatching {
                        pending.onFailed("Native player shutdown timed out. Please retry or quit Nuvio.")
                    }.onFailure { error -> log.w(error) { "release timeout callback failed" } }
                }
            }
        }, "nuvio-player-release-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun dispose() {
        host.resetCursorVisibility()
        val accepted = synchronized(lifecycleLock) {
            if (releaseRequested) {
                false
            } else {
                pendingSource = null
                true
            }
        }
        if (!accepted) return
        host.onPeerReady = null
        cancelInFlightSubtitleDownload()
        statsJob?.cancel()
        statsJob = null
        isPlaybackStatsActive = false
        currentPlaybackStats = null
        playerScope.cancel()
        downloadScope.cancel()
        disposePlayerHandle()
    }

    private fun cancelPendingAttachment() {
        synchronized(lifecycleLock) {
            pendingSource = null
        }
        host.onPeerReady = null
    }

    private fun invalidateForRelease() {
        cancelPendingAttachment()
        cancelInFlightSubtitleDownload()
        statsJob?.cancel()
        statsJob = null
        isPlaybackStatsActive = false
        currentPlaybackStats = null
        host.onCursorActivity = null
        onAction = { false }
        onEvent = { _, _ -> false }
        onScrubChange = { false }
        onScrubFinished = { false }
    }

    private fun awaitInFlightCreates() {
        while (true) {
            val workers = synchronized(lifecycleLock) {
                createsInFlight.removeAll { worker -> !worker.isAlive }
                createsInFlight.toList()
            }
            if (workers.isEmpty()) return
            workers.forEach { worker -> awaitThreadCompletion(worker, "native create") }
        }
    }

    private fun awaitSwingCallbacksQueuedByCreates() {
        val completed = CountDownLatch(1)
        SwingUtilities.invokeLater { completed.countDown() }
        var interrupted = false
        while (true) {
            try {
                completed.await()
                break
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
            log.w { "interrupted while awaiting queued native create callbacks; completion was preserved" }
        }
    }

    private fun awaitThreadCompletion(thread: Thread?, operation: String) {
        if (thread == null || thread === Thread.currentThread()) return
        var interrupted = false
        while (thread.isAlive) {
            try {
                thread.join()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
            log.w { "interrupted while awaiting $operation; completion was preserved" }
        }
    }

    private fun detachPlayerHandle(): Long = synchronized(lifecycleLock) {
        val current = handle
        handle = 0L
        attachedSource = null
        cancelInFlightSubtitleDownload()
        cachedSubtitleTracks = emptyList()
        cachedAudioTracks = emptyList()
        pendingSubtitleUri = null
        lastSentControlsStructureKey = null
        current
    }

    private fun recordTerminalDisposeFailure(error: Throwable, operation: String, nativeHandle: Long) {
        synchronized(lifecycleLock) {
            terminalReleaseFailure = "Native player shutdown failed. Quit Nuvio before removing the player."
        }
        log.w(error) { "$operation dispose failed handle=$nativeHandle" }
    }

    /** Must be called while holding [lifecycleLock]. */
    private fun startTrackedDisposeLocked(current: Long) {
        val prior = disposeInFlight?.takeIf { it.isAlive }
        disposeInFlight = Thread(
            {
                awaitThreadCompletion(prior, "prior native teardown")
                runCatching { nativeDispose(current) }
                .onFailure { error -> recordTerminalDisposeFailure(error, "native", current) }
            },
            "nuvio-player-dispose",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun disposePlayerHandle() {
        synchronized(lifecycleLock) {
            if (releaseRequested) return
            val current = handle
            if (current == 0L) return
            handle = 0L
            attachedSource = null
            cancelInFlightSubtitleDownload()
            cachedSubtitleTracks = emptyList()
            cachedAudioTracks = emptyList()
            pendingSubtitleUri = null
            lastSentControlsStructureKey = null
            // Register and start teardown atomically so terminal release cannot sample an empty
            // worker slot between detaching the handle and starting native disposal.
            startTrackedDisposeLocked(current)
        }
    }

    override fun play() {
        log.d { "play handle=$handle" }
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.setPaused(it, false) }
    }

    override fun pause() {
        log.d { "pause handle=$handle" }
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.setPaused(it, true) }
    }

    override fun seekTo(positionMs: Long) {
        log.d { "seekTo positionMs=$positionMs handle=$handle" }
        handle.takeIf { it != 0L }?.let { performSeekTo(it, positionMs) }
    }

    override fun trySeekTo(positionMs: Long): Boolean {
        val current = handle.takeIf { it != 0L } ?: return false
        log.d { "trySeekTo positionMs=$positionMs handle=$current" }
        performSeekTo(current, positionMs)
        return true
    }

    override fun seekBy(offsetMs: Long) {
        log.d { "seekBy offsetMs=$offsetMs handle=$handle" }
        handle.takeIf { it != 0L }?.let { current ->
            val durationMs = controlsState.durationMs.takeIf { it > 0L }
                ?: nativeDurationMs(current).takeIf { it > 0L }
                ?: 0L
            val currentPos = nativePositionMs(current).takeIf { it > 0L }
                ?: controlsState.positionMs.takeIf { it > 0L }
                ?: 0L
            if (durationMs > 2_000L) {
                val targetMs = (currentPos + offsetMs).coerceIn(0L, durationMs - 1_500L)
                val nearEnd = targetMs >= durationMs - 45_000L
                if (nearEnd) {
                    nativeSeekTo(current, targetMs)
                    return@let
                }
                val safeOffset = targetMs - currentPos
                nativeSeekBy(current, safeOffset)
            } else {
                nativeSeekBy(current, offsetMs)
            }
        }
    }

    private fun performSeekTo(current: Long, positionMs: Long) {
        val durationMs = controlsState.durationMs.takeIf { it > 0L }
            ?: nativeDurationMs(current).takeIf { it > 0L }
            ?: 0L
        val clampedPosition = if (durationMs > 2_000L) {
            positionMs.coerceIn(0L, durationMs - 1_500L)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        if (clampedPosition == 0L) {
            nativeSeekTo(current, 0L)
            return
        }
        val nearEnd = durationMs > 0L && clampedPosition >= durationMs - 45_000L
        val currentPos = nativePositionMs(current).takeIf { it > 0L }
            ?: controlsState.positionMs.takeIf { it > 0L }
            ?: 0L
        val offsetMs = clampedPosition - currentPos
        if (!nearEnd && kotlin.math.abs(offsetMs) > 3_000L) {
            nativeSeekBy(current, offsetMs)
        } else {
            nativeSeekTo(current, clampedPosition)
        }
    }

    override fun retry() {
        val pending = pendingSource ?: return
        attach(
            sourceUrl = pending.sourceUrl,
            sourceHeaders = pending.headerLines.toHeaderMap(),
            playWhenReady = pending.playWhenReady,
            initialPositionMs = pending.initialPositionMs,
            decoderPriority = pending.decoderPriority,
            nvidiaRtxSuperResolutionEnabled = pending.nvidiaRtxSuperResolutionEnabled,
            onError = pending.onError,
        )
    }

    override fun setPlaybackSpeed(speed: Float) {
        log.d { "setPlaybackSpeed speed=$speed handle=$handle" }
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.setSpeed(it, speed) }
    }

    override fun getAudioTracks(): List<AudioTrack> {
        val tracks = decodeTracks { NativePlayerBridge.audioTracksJson(it) }
        cachedAudioTracks = tracks
        return tracks.map { track ->
            AudioTrack(
                index = track.index,
                id = track.id,
                label = track.label,
                language = track.language.takeUnless(String::isBlank),
                isSelected = track.selected,
            )
        }
    }

    override suspend fun getChapters(): List<PlayerChapter> {
        if (!PlayerSettingsRepository.uiState.value.androidExoChaptersEnabled) return emptyList()
        val current = handle.takeIf { it != 0L } ?: return emptyList()
        val count = NativePlayerBridge.stringProperty(current, "chapter-list/count").toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            val seconds = NativePlayerBridge.stringProperty(current, "chapter-list/$index/time").toDoubleOrNull()
                ?: return@mapNotNull null
            PlayerChapter(
                startMs = (seconds * 1000.0).toLong(),
                title = NativePlayerBridge.stringProperty(current, "chapter-list/$index/title").takeIf { it.isNotBlank() },
            )
        }
    }

    override suspend fun getMediaInfo(): PlayerMediaInfo {
        val current = handle.takeIf { it != 0L } ?: return PlayerMediaInfo()
        return mpvMediaInfo { name -> NativePlayerBridge.stringProperty(current, name).takeIf { it.isNotBlank() } }
    }

    override fun getSubtitleTracks(): List<SubtitleTrack> {
        val tracks = decodeTracks { NativePlayerBridge.subtitleTracksJson(it) }
        cachedSubtitleTracks = tracks
        return tracks.map { track ->
            SubtitleTrack(
                index = track.index,
                id = track.id,
                label = track.label,
                language = track.language.takeUnless(String::isBlank),
                isSelected = track.selected,
                isForced = track.forced || inferForcedSubtitleTrack(
                    label = track.label,
                    language = track.language,
                    trackId = track.id,
                ),
            )
        }
    }

    override fun applyAudioLanguagePreferences(languages: List<String>) {
        handle.takeIf { it != 0L }?.let { NativePlayerBridge.setAudioLanguages(it, languages.joinToString(",")) }
        val trackIndex = findPreferredAudioTrackIndex(getAudioTracks(), languages)
        if (trackIndex >= 0) selectAudioTrack(trackIndex)
    }

    override fun selectAudioTrack(index: Int) {
        val current = handle.takeIf { it != 0L } ?: return
        val cachedTrackId = resolveTrackId(index, cachedAudioTracks)
        executePlayerCommand { activeHandle ->
            val trackId = cachedTrackId ?: run {
                val tracks = decodeTracks(activeHandle) { NativePlayerBridge.audioTracksJson(it) }
                cachedAudioTracks = tracks
                resolveTrackId(index, tracks)
            }
            if (trackId == null) {
                log.w { "selectAudioTrack missing track index=$index count=${cachedAudioTracks.size} handle=$activeHandle" }
                return@executePlayerCommand
            }
            log.d { "selectAudioTrack index=$index trackId=$trackId count=${cachedAudioTracks.size} handle=$activeHandle" }
            NativePlayerBridge.selectAudioTrack(activeHandle, trackId)
        }
    }

    override fun selectSubtitleTrack(index: Int) {
        cancelInFlightSubtitleDownload()
        pendingSubtitleUri = null
        val current = handle.takeIf { it != 0L } ?: return
        if (index < 0) {
            log.d { "selectSubtitleTrack off handle=$current" }
            executePlayerCommand { activeHandle ->
                NativePlayerBridge.selectSubtitleTrack(activeHandle, -1)
            }
            return
        }
        val cachedTrackId = resolveTrackId(index, cachedSubtitleTracks)
        executePlayerCommand { activeHandle ->
            val trackId = cachedTrackId ?: run {
                val tracks = decodeTracks(activeHandle) { NativePlayerBridge.subtitleTracksJson(it) }
                cachedSubtitleTracks = tracks
                resolveTrackId(index, tracks)
            }
            if (trackId == null) {
                log.w { "selectSubtitleTrack missing track index=$index count=${cachedSubtitleTracks.size} handle=$activeHandle" }
                return@executePlayerCommand
            }
            log.d { "selectSubtitleTrack index=$index trackId=$trackId count=${cachedSubtitleTracks.size} handle=$activeHandle" }
            NativePlayerBridge.selectSubtitleTrack(activeHandle, trackId)
            applyPendingSubtitleSettingsDirect(activeHandle)
        }
    }

    override fun setSubtitleUri(url: String) {
        log.d { "setSubtitleUri ${url.toPlaybackLogKey()} handle=$handle" }
        cancelInFlightSubtitleDownload()
        pendingSubtitleUri = url
        val current = handle.takeIf { it != 0L } ?: return

        val isRemote = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        if (!isRemote) {
            executePlayerCommand { activeHandle ->
                NativePlayerBridge.clearExternalSubtitles(activeHandle)
                NativePlayerBridge.addSubtitleUrl(activeHandle, url)
                applyPendingSubtitleSettingsDirect(activeHandle)
            }
            return
        }

        val cachedPath = resolveCachedSubtitlePath(url)
        if (cachedPath != null) {
            log.d { "setSubtitleUri using cached file=$cachedPath handle=$current" }
            executePlayerCommand { activeHandle ->
                NativePlayerBridge.clearExternalSubtitles(activeHandle)
                NativePlayerBridge.addSubtitleUrl(activeHandle, cachedPath)
                applyPendingSubtitleSettingsDirect(activeHandle)
            }
            return
        }

        ensureScopesActive()
        inFlightSubtitleDownloadJob = downloadScope.launch {
            log.d { "setSubtitleUri downloading asynchronously url=${url.toPlaybackLogKey()}" }
            val downloadedPath = downloadSubtitleToCache(url)
            if (!isActive) return@launch
            if (downloadedPath != null) {
                log.d { "setSubtitleUri download complete file=$downloadedPath handle=$handle" }
                executePlayerCommand { activeHandle ->
                    NativePlayerBridge.clearExternalSubtitles(activeHandle)
                    NativePlayerBridge.addSubtitleUrl(activeHandle, downloadedPath)
                    applyPendingSubtitleSettingsDirect(activeHandle)
                }
            } else {
                log.w { "setSubtitleUri download failed for ${url.toPlaybackLogKey()}" }
            }
        }
    }

    override fun clearExternalSubtitle() {
        cancelInFlightSubtitleDownload()
        pendingSubtitleUri = null
        log.d { "clearExternalSubtitle handle=$handle" }
        executePlayerCommand { activeHandle ->
            NativePlayerBridge.clearExternalSubtitles(activeHandle)
        }
    }

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        cancelInFlightSubtitleDownload()
        pendingSubtitleUri = null
        val current = handle.takeIf { it != 0L } ?: return
        val cachedTrackId = if (trackIndex < 0) -1 else resolveTrackId(trackIndex, cachedSubtitleTracks)
        executePlayerCommand { activeHandle ->
            val trackId = if (trackIndex < 0) {
                -1
            } else {
                cachedTrackId ?: run {
                    val tracks = decodeTracks(activeHandle) { NativePlayerBridge.subtitleTracksJson(it) }
                    cachedSubtitleTracks = tracks
                    resolveTrackId(trackIndex, tracks)
                } ?: run {
                    log.w { "clearExternalSubtitleAndSelect missing track index=$trackIndex count=${cachedSubtitleTracks.size} handle=$activeHandle" }
                    return@executePlayerCommand
                }
            }
            log.d { "clearExternalSubtitleAndSelect trackIndex=$trackIndex trackId=$trackId handle=$activeHandle" }
            NativePlayerBridge.clearExternalSubtitlesAndSelect(activeHandle, trackId)
            applyPendingSubtitleSettingsDirect(activeHandle)
        }
    }

    override fun setSubtitleDelayMs(delayMs: Int) {
        val clamped = delayMs.coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS)
        pendingSubtitleDelayMs = clamped
        executePlayerCommand { activeHandle ->
            NativePlayerBridge.setSubtitleDelayMs(activeHandle, clamped)
        }
    }

    override fun applySubtitleStyle(style: SubtitleStyleState, useLibass: Boolean) {
        pendingSubtitleStyle = style
        pendingUseLibass = useLibass
        executePlayerCommand { activeHandle ->
            applySubtitleStyle(activeHandle, style, useLibass)
        }
    }

    private fun applyPendingSubtitleSettings(targetHandle: Long = handle) {
        val target = targetHandle.takeIf { it != 0L } ?: handle.takeIf { it != 0L } ?: return
        ensureScopesActive()
        playerScope.launch {
            val activeHandle = synchronized(lifecycleLock) {
                if (handle == target) handle else target
            }.takeIf { it != 0L } ?: return@launch
            applyPendingSubtitleSettingsDirect(activeHandle)
        }
    }

    private fun applyPendingSubtitleSettingsDirect(handle: Long) {
        if (handle == 0L) return
        pendingSubtitleDelayMs?.let { delayMs ->
            NativePlayerBridge.setSubtitleDelayMs(handle, delayMs)
        }
        pendingSubtitleStyle?.let { style ->
            applySubtitleStyle(handle, style, pendingUseLibass)
        }
    }

    private fun applySubtitleStyle(handle: Long, style: SubtitleStyleState, useLibass: Boolean) {
        val applyLibass = useLibass && style.outlineEffect != SubtitleOutlineEffect.BACKGROUND_BOX
        val isBackgroundBox = style.outlineEffect == SubtitleOutlineEffect.BACKGROUND_BOX
        val resolvedBoxColor = if (style.backgroundColor.alpha == 0f) {
            androidx.compose.ui.graphics.Color(0, 0, 0, (0.65f * 255f).toInt())
        } else {
            style.backgroundColor
        }
        val resolvedOutlineSize = when (style.outlineEffect) {
            SubtitleOutlineEffect.NONE -> 0f
            SubtitleOutlineEffect.BACKGROUND_BOX -> 0f
            SubtitleOutlineEffect.SOFT_GLOW -> (style.outlineWidth.toFloat() * 1.5f).coerceAtLeast(3f)
            SubtitleOutlineEffect.OUTLINE,
            SubtitleOutlineEffect.OUTLINE_AND_SHADOW -> if (style.outlineEffect.needsOutline) {
                style.outlineWidth.toFloat()
            } else {
                0f
            }
        }

        val resolvedBackgroundColor = if (isBackgroundBox) {
            resolvedBoxColor.toMpvColorString()
        } else {
            androidx.compose.ui.graphics.Color.Transparent.toMpvColorString()
        }
        val resolvedOutlineColor = if (isBackgroundBox) {
            resolvedBoxColor.toMpvColorString()
        } else {
            style.outlineColor.toMpvColorString()
        }

        NativePlayerBridge.applySubtitleStyle(
            handle = handle,
            textColor = style.textColor.toMpvColorString(),
            backgroundColor = resolvedBackgroundColor,
            outlineColor = resolvedOutlineColor,
            outlineSize = resolvedOutlineSize,
            bold = style.bold,
            fontSize = style.toMpvSubtitleFontSize(),
            subPos = style.toMpvSubtitlePosition(),
            useLibass = applyLibass,
            stripSdh = style.stripSdh,
        )

        WindowsMpvSubStyleHelper.applyExtendedSubtitleStyle(
            bridgeHandle = handle,
            style = style,
            useLibass = applyLibass,
        )
    }

    private fun decodeTracks(readJson: (Long) -> String): List<NativeMpvTrack> =
        decodeTracks(handle, readJson)

    private fun decodeTracks(handle: Long, readJson: (Long) -> String): List<NativeMpvTrack> {
        val current = handle.takeIf { it != 0L } ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<NativeMpvTrack>>(readJson(current))
        }.getOrDefault(emptyList())
    }

    private fun resolveCachedSubtitlePath(url: String): String? {
        val subtitlesDir = DesktopStorage.cacheDir.resolve("subtitles")
        if (!Files.exists(subtitlesDir)) return null
        val hash = hashUrl(url)
        val extensions = listOf(resolveSubtitleExtension(url), "srt", "vtt", "ass", "ssa", "sub").distinct()
        for (ext in extensions) {
            val file = subtitlesDir.resolve("$hash.$ext")
            if (Files.exists(file) && Files.isRegularFile(file) && Files.size(file) > 0L) {
                return file.toAbsolutePath().toString()
            }
        }
        return null
    }

    private suspend fun downloadSubtitleToCache(url: String): String? = withContext(Dispatchers.IO) {
        val subtitlesDir = DesktopStorage.cacheDir.resolve("subtitles")
        runCatching { Files.createDirectories(subtitlesDir) }
        val hash = hashUrl(url)
        val initialExt = resolveSubtitleExtension(url)
        val tempFile = subtitlesDir.resolve("$hash-${System.nanoTime()}.tmp")

        try {
            val encodedUrl = url.encodeUnsafeHttpUrlCharacters()
            val request = Request.Builder()
                .url(encodedUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val call = subtitleDownloadClient.newCall(request)

            val response = suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    call.cancel()
                }
                call.enqueue(object : okhttp3.Callback {
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        continuation.resumeWith(Result.success(response))
                    }
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        continuation.resumeWith(Result.failure(e))
                    }
                })
            }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    log.w { "downloadSubtitleToCache HTTP ${resp.code} for ${url.toPlaybackLogKey()}" }
                    return@withContext null
                }
                val body = resp.body ?: return@withContext null
                body.byteStream().use { input ->
                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }

            if (!Files.exists(tempFile) || Files.size(tempFile) == 0L) {
                Files.deleteIfExists(tempFile)
                return@withContext null
            }

            val headerBytes = ByteArray(128)
            val readCount = Files.newInputStream(tempFile).use { it.read(headerBytes) }
            val finalExt = if (readCount > 0) {
                detectSubtitleExtension(headerBytes.copyOf(readCount), initialExt)
            } else {
                initialExt
            }
            val finalTargetFile = subtitlesDir.resolve("$hash.$finalExt")
            try {
                Files.move(tempFile, finalTargetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(tempFile, finalTargetFile, StandardCopyOption.REPLACE_EXISTING)
            }
            finalTargetFile.toAbsolutePath().toString()
        } catch (e: Exception) {
            if (e is CancellationException) {
                log.d { "downloadSubtitleToCache cancelled for ${url.toPlaybackLogKey()}" }
            } else {
                log.w(e) { "downloadSubtitleToCache error for ${url.toPlaybackLogKey()}" }
            }
            null
        } finally {
            runCatching { Files.deleteIfExists(tempFile) }
        }
    }
}

private fun hashUrl(url: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(url.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun resolveSubtitleExtension(url: String): String {
    val cleanUrl = url.substringBefore('?').substringBefore('#')
    val ext = cleanUrl.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (ext) {
        "srt", "vtt", "ass", "ssa", "sub" -> ext
        else -> "srt"
    }
}

private fun detectSubtitleExtension(firstBytes: ByteArray, fallbackExt: String): String {
    val header = String(firstBytes, 0, minOf(firstBytes.size, 128), Charsets.UTF_8).trimStart()
    return when {
        header.startsWith("WEBVTT", ignoreCase = true) -> "vtt"
        header.startsWith("[Script Info]", ignoreCase = true) -> "ass"
        else -> fallbackExt
    }
}

private fun String.toPlaybackLogKey(): String {
    val scheme = substringBefore(':', missingDelimiterValue = "unknown")
        .takeIf { it.isNotBlank() }
        ?: "unknown"
    return "scheme=$scheme length=$length hash=${hashCode()}"
}

private fun String.shouldLogNativeControlEvent(): Boolean {
    val normalized = lowercase()
    return normalized.contains("audio") ||
        normalized.contains("subtitle") ||
        normalized.contains("speed") ||
        normalized.contains("scrub") ||
        normalized.contains("seek") ||
        normalized.contains("episode") ||
        normalized == "resize" ||
        normalized == "toggle"
}

@Serializable
private data class NativeMpvTrack(
    val index: Int = 0,
    val id: String = "",
    val label: String = "",
    val language: String = "",
    val selected: Boolean = false,
    val forced: Boolean = false,
)

private fun resolveTrackId(index: Int, tracks: List<NativeMpvTrack>): Int? =
    tracks.firstNotNullOfOrNull { track ->
        if (track.index == index) {
            track.id.toIntOrNull()
        } else {
            null
        }
    } ?: tracks.getOrNull(index)?.id?.toIntOrNull()

private fun Color.toMpvColorString(): String {
    val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
    val redInt = (red * 255f).toInt().coerceIn(0, 255)
    val greenInt = (green * 255f).toInt().coerceIn(0, 255)
    val blueInt = (blue * 255f).toInt().coerceIn(0, 255)
    return buildString {
        append('#')
        append(alphaInt.toHexByte())
        append(redInt.toHexByte())
        append(greenInt.toHexByte())
        append(blueInt.toHexByte())
    }
}

private fun SubtitleStyleState.toMpvSubtitlePosition(): Int =
    (100 - (bottomOffset / 2)).coerceIn(0, 150)

private fun SubtitleStyleState.toMpvSubtitleFontSize(): Float =
    (fontSizeSp * 3f).coerceIn(18f, 96f)

private fun Int.toHexByte(): String {
    val digits = "0123456789ABCDEF"
    val value = coerceIn(0, 255)
    return buildString {
        append(digits[value / 16])
        append(digits[value % 16])
    }
}

private data class PendingSource(
    val sourceUrl: String,
    val headerLines: List<String>,
    val playWhenReady: Boolean,
    val initialPositionMs: Long,
    val decoderPriority: Int,
    val nvidiaRtxSuperResolutionEnabled: Boolean,
    val onError: (String?) -> Unit,
)

private fun Map<String, String>.toHeaderLines(): List<String> =
    entries.mapNotNull { (key, value) ->
        val cleanKey = key.trim()
        val cleanValue = value.trim()
        if (cleanKey.isBlank() || cleanValue.isBlank()) {
            null
        } else {
            "$cleanKey: $cleanValue"
        }
    }

private fun List<String>.toHeaderMap(): Map<String, String> =
    mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@mapNotNull null
        line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }.toMap()

private fun String.toPlayerControlsAction(): PlayerControlsAction? =
    when (this) {
        "toggleChrome" -> PlayerControlsAction.ToggleChrome
        "back" -> PlayerControlsAction.Back
        "toggle" -> PlayerControlsAction.TogglePlayback
        "keyboardToggle" -> PlayerControlsAction.KeyboardTogglePlayback
        "seekBack" -> PlayerControlsAction.SeekBack
        "keyboardSeekBack" -> PlayerControlsAction.KeyboardSeekBack
        "keyboardSeek10Back" -> PlayerControlsAction.KeyboardSeek10Back
        "keyboardFineSeekBack" -> PlayerControlsAction.KeyboardFineSeekBack
        "seekForward" -> PlayerControlsAction.SeekForward
        "keyboardSeekForward" -> PlayerControlsAction.KeyboardSeekForward
        "keyboardSeek10Forward" -> PlayerControlsAction.KeyboardSeek10Forward
        "keyboardFineSeekForward" -> PlayerControlsAction.KeyboardFineSeekForward
        "keyboardVolumeDown" -> PlayerControlsAction.KeyboardVolumeDown
        "keyboardVolumeUp" -> PlayerControlsAction.KeyboardVolumeUp
        "pictureInPicture", "pip" -> PlayerControlsAction.PictureInPicture
        "resize" -> PlayerControlsAction.ResizeMode
        "speed" -> PlayerControlsAction.Speed
        "subtitles" -> PlayerControlsAction.Subtitles
        "audio" -> PlayerControlsAction.Audio
        "sources" -> PlayerControlsAction.Sources
        "episodes" -> PlayerControlsAction.Episodes
        "external" -> PlayerControlsAction.OpenExternalPlayer
        "submitIntro" -> PlayerControlsAction.SubmitIntro
        "videoSettings" -> PlayerControlsAction.VideoSettings
        "minimize" -> PlayerControlsAction.Minimize
        "expand" -> PlayerControlsAction.Expand
        else -> null
    }

private data class NativeControlsStructureKey(
    val state: PlayerControlsState,
    val isFullscreen: Boolean,
    val isInPip: Boolean,
)

internal fun PlayerControlsState.toControlsJson(isFullscreen: Boolean, onImageReady: () -> Unit = {}): String =
    buildString {
        append('{')
        appendJsonField("isMiniPlayer", isMiniPlayer)
        append(',')
        appendJsonField("playerUiMode", playerUiMode)
        append(',')
        appendJsonField("seekPreviewEnabled", seekPreviewEnabled)
        append(",")
        appendJsonField("seekPreviewWhileDragging", seekPreviewWhileDragging)
        append(",")
        appendJsonField("seekPreviewWhileHovering", seekPreviewWhileHovering)
        append(",")
        appendJsonField("seekPreviewWhileKeyboardSeeking", seekPreviewWhileKeyboardSeeking)
        append(",")
        appendJsonField("seekPreviewThumbnailCount", seekPreviewThumbnailCount)
        append(',')
        appendJsonField("seekPreviewVttUrl", seekPreviewVttUrl)
        append(',')
        appendJsonField("seekrApiKey", seekrApiKey)
        append(',')
        appendJsonField("seekrImdbId", seekrImdbId)
        append(',')
        appendJsonField("seekrTmdbId", seekrTmdbId)
        append(',')
        appendJsonField("seekrSeason", seekrSeason)
        append(',')
        appendJsonField("seekrEpisode", seekrEpisode)
        append(',')
        appendJsonField("animeUpscalerEnabled", animeUpscalerEnabled)
        append(',')
        appendJsonField("animeUpscalerModeIndex", animeUpscalerModeIndex)
        append(',')
        appendJsonField("animeUpscalerOption", animeUpscalerOption.name.lowercase())
        append(',')
        appendJsonField("animeUpscalerActive", animeUpscalerActive)
        append(',')
        appendJsonField("mediaContentType", mediaContentType.name.lowercase())
        append(',')
        appendJsonField("isAutoBypassed", isAutoBypassed)
        append(',')
        appendJsonField("svpMode", svpMode)
        append(',')
        appendJsonField("svpActive", svpActive)
        append(',')
        appendJsonField("svpProfile", svpProfile)
        append(',')
        appendJsonField("svpAvailable", svpAvailable)
        append(',')
        appendJsonField("svpRuntimeBundled", DesktopHostOs.current == DesktopHostOs.WINDOWS)
        append(',')
        appendJsonField("audioNightModeEnabled", audioNightModeEnabled)
        append(',')
        appendJsonField("showSeekbarWhileSeeking", showSeekbarWhileSeeking)
        append(',')
        appendJsonField("showRemainingTime", showRemainingTime)
        append(',')
        appendJsonField("title", title)
        append(',')
        appendJsonField("episodeText", episodeText)
        append(',')
        appendJsonField("streamTitle", streamTitle)
        append(',')
        appendJsonField("providerName", providerName)
        append(',')
        appendJsonField("pauseOverlayEnabled", pauseOverlayEnabled)
        append(',')
        appendJsonField("pauseOverlayWatchingLabel", pauseOverlayWatchingLabel)
        append(',')
        appendJsonField("pauseOverlayLogo", pauseOverlayLogo.orEmpty())
        append(',')
        appendJsonField("pauseOverlayEpisodeInfo", pauseOverlayEpisodeInfo)
        append(',')
        appendJsonField("pauseOverlayEpisodeTitle", pauseOverlayEpisodeTitle)
        append(',')
        appendJsonField("pauseOverlayDescription", pauseOverlayDescription)
        append(',')
        appendJsonField("resizeModeLabel", resizeModeLabel)
        append(',')
        appendJsonField("playbackSpeedLabel", playbackSpeedLabel)
        append(',')
        appendJsonField("holdToSpeedEnabled", holdToSpeedEnabled)
        append(',')
        appendJsonField("holdToSpeedValue", holdToSpeedValue)
        append(',')
        appendJsonField("isFullscreen", isFullscreen)
        append(',')
        appendJsonVolumeLevel("volumeLevel", volumeLevel)
        append(',')
        appendJsonField("subtitlesLabel", subtitlesLabel)
        append(',')
        appendJsonField("audioLabel", audioLabel)
        append(',')
        appendJsonField("sourcesLabel", sourcesLabel)
        append(',')
        appendJsonField("episodesLabel", episodesLabel)
        append(',')
        appendJsonField("externalPlayerLabel", externalPlayerLabel)
        append(',')
        appendJsonField("playLabel", playLabel)
        append(',')
        appendJsonField("pauseLabel", pauseLabel)
        append(',')
        appendJsonField("closeLabel", closeLabel)
        append(',')
        appendJsonField("mutedLabel", mutedLabel)
        append(',')
        appendJsonField("volumeLevelLabelFormat", volumeLevelLabelFormat)
        append(',')
        appendJsonField("submitIntroLabel", submitIntroLabel)
        append(',')
        appendJsonField("videoSettingsLabel", videoSettingsLabel)
        append(',')
        appendJsonField("playbackErrorTitle", playbackErrorTitle)
        append(',')
        appendJsonField("playbackErrorMessage", playbackErrorMessage)
        append(',')
        appendJsonField("playbackErrorActionLabel", playbackErrorActionLabel)
        append(',')
        appendJsonField("sourcesPanelTitle", sourcesPanelTitle)
        append(',')
        appendJsonField("episodesPanelTitle", episodesPanelTitle)
        append(',')
        appendJsonField("streamsPanelTitle", streamsPanelTitle)
        append(',')
        appendJsonField("allFilterLabel", allFilterLabel)
        append(',')
        appendJsonField("reloadLabel", reloadLabel)
        append(',')
        appendJsonField("backLabel", backLabel)
        append(',')
        appendJsonField("panelCloseLabel", panelCloseLabel)
        append(',')
        appendJsonField("cancelLabel", cancelLabel)
        append(',')
        appendJsonField("playingLabel", playingLabel)
        append(',')
        appendJsonField("noStreamsLabel", noStreamsLabel)
        append(',')
        appendJsonField("noEpisodesLabel", noEpisodesLabel)
        append(',')
        appendJsonField("submitIntroPanelTitle", submitIntroPanelTitle)
        append(',')
        appendJsonField("submitIntroSegmentTypeLabel", submitIntroSegmentTypeLabel)
        append(',')
        appendJsonField("submitIntroSegmentIntroLabel", submitIntroSegmentIntroLabel)
        append(',')
        appendJsonField("submitIntroSegmentRecapLabel", submitIntroSegmentRecapLabel)
        append(',')
        appendJsonField("submitIntroSegmentOutroLabel", submitIntroSegmentOutroLabel)
        append(',')
        appendJsonField("submitIntroStartTimeLabel", submitIntroStartTimeLabel)
        append(',')
        appendJsonField("submitIntroEndTimeLabel", submitIntroEndTimeLabel)
        append(',')
        appendJsonField("submitIntroCaptureLabel", submitIntroCaptureLabel)
        append(',')
        appendJsonField("submitIntroSubmitLabel", submitIntroSubmitLabel)
        append(',')
        appendJsonField("p2pConsentTitle", p2pConsentTitle)
        append(',')
        appendJsonField("p2pConsentBody", p2pConsentBody)
        append(',')
        appendJsonField("p2pConsentEnableLabel", p2pConsentEnableLabel)
        append(',')
        appendJsonField("p2pConsentCancelLabel", p2pConsentCancelLabel)
        append(',')
        appendJsonField("speedPanelTitle", speedPanelTitle)
        append(',')
        appendJsonField("audioTracksPanelTitle", audioTracksPanelTitle)
        append(',')
        appendJsonField("noAudioTracksLabel", noAudioTracksLabel)
        append(',')
        appendJsonField("subtitlesPanelTitle", subtitlesPanelTitle)
        append(',')
        appendJsonField("subtitleLanguagesLabel", subtitleLanguagesLabel)
        append(',')
        appendJsonField("subtitleBuiltInTabLabel", subtitleBuiltInTabLabel)
        append(',')
        appendJsonField("subtitleAddonsTabLabel", subtitleAddonsTabLabel)
        append(',')
        appendJsonField("subtitleStyleTabLabel", subtitleStyleTabLabel)
        append(',')
        appendJsonField("customSubtitleStyleLabel", customSubtitleStyleLabel)
        append(',')
        appendJsonField("forcedLabel", forcedLabel)
        append(',')
        appendJsonField("noneLabel", noneLabel)
        append(',')
        appendJsonField("fetchSubtitlesLabel", fetchSubtitlesLabel)
        append(',')
        appendJsonField("subtitleDelayLabel", subtitleDelayLabel)
        append(',')
        appendJsonField("resetLabel", resetLabel)
        append(',')
        appendJsonField("autoSyncLabel", autoSyncLabel)
        append(',')
        appendJsonField("reloadSmallLabel", reloadSmallLabel)
        append(',')
        appendJsonField("captureLineLabel", captureLineLabel)
        append(',')
        appendJsonField("selectAddonSubtitleFirstLabel", selectAddonSubtitleFirstLabel)
        append(',')
        appendJsonField("loadingSubtitleLinesLabel", loadingSubtitleLinesLabel)
        append(',')
        appendJsonField("fontSizeLabel", fontSizeLabel)
        append(',')
        appendJsonField("outlineLabel", outlineLabel)
        append(',')
        appendJsonField("boldLabel", boldLabel)
        append(',')
        appendJsonField("bottomOffsetLabel", bottomOffsetLabel)
        append(',')
        appendJsonField("colorLabel", colorLabel)
        append(',')
        appendJsonField("textOpacityLabel", textOpacityLabel)
        append(',')
        appendJsonField("outlineColorLabel", outlineColorLabel)
        append(',')
        appendJsonField("noSubtitleLinesFoundLabel", noSubtitleLinesFoundLabel)
        append(',')
        appendJsonField("resetDefaultsLabel", resetDefaultsLabel)
        append(',')
        appendJsonField("onLabel", onLabel)
        append(',')
        appendJsonField("offLabel", offLabel)
        append(',')
        appendJsonField("themeAccentColor", themeAccentColor)
        append(',')
        appendJsonArrayField("themeAccentGradientColors", themeAccentGradientColors) { append(it.toJsonString()) }
        append(',')
        appendJsonField("themeAccentStrongColor", themeAccentStrongColor)
        append(',')
        appendJsonField("themeOnAccentColor", themeOnAccentColor)
        append(',')
        appendJsonField("themeFocusColor", themeFocusColor)
        append(',')
        appendJsonField("themeSelectedSurfaceColor", themeSelectedSurfaceColor)
        append(',')
        appendJsonField("themeSelectedSurfaceHoverColor", themeSelectedSurfaceHoverColor)
        append(',')
        appendJsonField("themeSelectedRingColor", themeSelectedRingColor)
        append(',')
        appendJsonField("themeTimelineFillColor", themeTimelineFillColor)
        append(',')
        appendJsonField("themeTimelineTrackColor", themeTimelineTrackColor)
        append(',')
        appendJsonField("themeBufferingColor", themeBufferingColor)
        append(',')
        appendJsonField("themeBufferingTrackColor", themeBufferingTrackColor)
        append(',')
        appendJsonField("themeControlForegroundColor", themeControlForegroundColor)
        append(',')
        appendJsonField("themeSurfaceElevatedColor", themeSurfaceElevatedColor)
        append(',')
        appendJsonField("themeSurfaceCardColor", themeSurfaceCardColor)
        append(',')
        appendJsonField("themeSurfacePopoverColor", themeSurfacePopoverColor)
        append(',')
        appendJsonField("themeTextPrimaryColor", themeTextPrimaryColor)
        append(',')
        appendJsonField("themeTextSecondaryColor", themeTextSecondaryColor)
        append(',')
        appendJsonField("themeTextMutedColor", themeTextMutedColor)
        append(',')
        appendJsonField("themeBorderDefaultColor", themeBorderDefaultColor)
        append(',')
        appendJsonField("themeDangerColor", themeDangerColor)
        append(',')
        appendJsonField("isPlaying", isPlaying)
        append(',')
        appendJsonField("isLoading", isLoading)
        append(',')
        appendJsonField(
            "pipLabel",
            if (DesktopHostOs.current == DesktopHostOs.WINDOWS ||
                DesktopHostOs.current == DesktopHostOs.MACOS
            ) {
                pipLabel
            } else {
                ""
            },
        )
        append(',')
        appendJsonField("minimizeLabel", minimizeLabel)
        append(',')
        appendJsonField("lockLabel", lockLabel)
        append(',')
        appendJsonField("unlockLabel", unlockLabel)
        append(',')
        appendJsonField("isInPip", DesktopPlayerPictureInPicture.isEnabled)
        append(',')
        appendJsonField("controlsVisible", controlsVisible)
        append(',')
        appendJsonArrayField("parentalWarnings", parentalWarnings) { appendParentalWarningJson(it) }
        append(',')
        appendJsonField("showParentalGuide", showParentalGuide)
        append(',')
        appendJsonField("showOpeningOverlay", showOpeningOverlay)
        append(',')
        appendJsonField("openingArtwork", openingArtwork.orEmpty())
        append(',')
        appendJsonField("openingLogo", openingLogo.orEmpty())
        append(',')
        appendJsonField("openingTitle", openingTitle)
        append(',')
        appendJsonField("openingMessage", openingMessage.orEmpty())
        append(',')
        appendJsonField("openingProgress", openingProgress)
        append(',')
        appendJsonField("skipPromptVisible", skipPromptVisible)
        append(',')
        appendJsonField("skipPromptLabel", skipPromptLabel)
        append(',')
        appendJsonField("skipPromptStartMs", skipPromptStartMs)
        append(',')
        appendJsonField("skipPromptEndMs", skipPromptEndMs)
        append(',')
        appendJsonField("skipPromptDismissed", skipPromptDismissed)
        append(',')
        appendJsonField("nextEpisodeVisible", nextEpisodeVisible)
        append(',')
        appendJsonField("nextEpisodeHeaderLabel", nextEpisodeHeaderLabel)
        append(',')
        appendJsonField("nextEpisodeTitle", nextEpisodeTitle)
        append(',')
        appendJsonField("nextEpisodeThumbnail", nextEpisodeThumbnail)
        append(',')
        appendJsonField("nextEpisodeThumbnailBlurred", nextEpisodeThumbnailBlurred)
        append(',')
        appendJsonField("nextEpisodeStatus", nextEpisodeStatus)
        append(',')
        appendJsonField("nextEpisodeActionLabel", nextEpisodeActionLabel)
        append(',')
        appendJsonField("nextEpisodePlayable", nextEpisodePlayable)
        append(',')
        appendJsonField("nextEpisodeIsFiller", nextEpisodeIsFiller)
        append(',')
        appendJsonField("nextEpisodeIsMixed", nextEpisodeIsMixed)
        append(',')
        appendJsonField("showSubmitIntro", showSubmitIntro)
        append(',')
        appendJsonField("showVideoSettings", showVideoSettings)
        append(',')
        appendJsonField("showSources", showSources)
        append(',')
        appendJsonField("showEpisodes", showEpisodes)
        append(',')
        appendJsonField("showExternalPlayer", showExternalPlayer)
        append(',')
        appendJsonField("durationMs", durationMs)
        append(',')
        appendJsonField("positionMs", positionMs)
        append(',')
        appendJsonField("bufferedPositionMs", bufferedPositionMs)
        append(',')
        appendJsonField("sourceIsLoading", sourceIsLoading)
        append(',')
        appendJsonArrayField("sourceFilters", sourceFilters) { appendFilterItemJson(it) }
        append(',')
        appendJsonArrayField("sourceItems", sourceItems) { appendSourceItemJson(it) }
        append(',')
        appendJsonArrayField("episodeItems", episodeItems) { appendEpisodeItemJson(it) }
        append(',')
        appendJsonArrayField("episodeSeasons", episodeSeasons) { appendSeasonItemJson(it) }
        append(',')
        appendJsonField("episodeStreamsVisible", episodeStreamsVisible)
        append(',')
        appendJsonField("episodeStreamsIsLoading", episodeStreamsIsLoading)
        append(',')
        appendJsonField("selectedEpisodeLabel", selectedEpisodeLabel)
        append(',')
        appendJsonArrayField("episodeStreamFilters", episodeStreamFilters) { appendFilterItemJson(it) }
        append(',')
        appendJsonArrayField("episodeStreamItems", episodeStreamItems) { appendSourceItemJson(it) }
        append(',')
        appendJsonField("blurUnwatchedEpisodes", blurUnwatchedEpisodes)
        append(',')
        appendJsonField("submitIntroSegmentType", submitIntroSegmentType)
        append(',')
        appendJsonField("submitIntroContentKey", submitIntroContentKey)
        append(',')
        appendJsonField("submitIntroStartTime", submitIntroStartTime)
        append(',')
        appendJsonField("submitIntroEndTime", submitIntroEndTime)
        append(',')
        appendJsonField("isSubmitIntroSubmitting", isSubmitIntroSubmitting)
        append(',')
        appendJsonField("submitIntroStatusMessage", submitIntroStatusMessage)
        append(',')
        appendJsonField("showP2pConsent", showP2pConsent)
        append(',')
        appendJsonField("subtitleActiveTab", subtitleActiveTab)
        append(',')
        appendJsonArrayField("subtitleLanguageItems", subtitleLanguageItems) { appendSubtitleLanguageItemJson(it) }
        append(',')
        appendJsonArrayField("subtitleOptionItems", subtitleOptionItems) { appendSubtitleOptionItemJson(it) }
        append(',')
        appendJsonField("selectedSubtitleLanguageKey", selectedSubtitleLanguageKey)
        append(',')
        appendJsonField("selectedSubtitleOptionId", selectedSubtitleOptionId)
        append(',')
        appendJsonArrayField("addonSubtitleItems", addonSubtitleItems) { appendAddonSubtitleItemJson(it) }
        append(',')
        appendJsonField("isLoadingAddonSubtitles", isLoadingAddonSubtitles)
        append(',')
        appendJsonField("selectedAddonSubtitleId", selectedAddonSubtitleId)
        append(',')
        appendJsonField("useCustomSubtitles", useCustomSubtitles)
        append(',')
        appendJsonField("customSubtitleStylingEnabled", customSubtitleStylingEnabled)
        append(',')
        appendJsonField("subtitleDelayMs", subtitleDelayMs)
        append(',')
        appendJsonField("hasSelectedAddonSubtitle", hasSelectedAddonSubtitle)
        append(',')
        appendJsonField("subtitleAutoSyncCapturedPositionMs", subtitleAutoSyncCapturedPositionMs)
        append(',')
        appendJsonArrayField("subtitleAutoSyncCues", subtitleAutoSyncCues) { appendSubtitleCueItemJson(it) }
        append(',')
        appendJsonField("subtitleAutoSyncIsLoading", subtitleAutoSyncIsLoading)
        append(',')
        appendJsonField("subtitleAutoSyncErrorMessage", subtitleAutoSyncErrorMessage)
        append(',')
        appendJsonField("subtitleStyle", subtitleStyle)
        append(',')
        appendJsonArrayField("subtitleColorSwatches", SubtitleColorSwatches.map { it.toStorageHexString() }) { append(it.toJsonString()) }
        append(',')
        appendJsonArrayField("subtitleOutlineColorSwatches", SubtitleOutlineColorSwatches.map { it.toStorageHexString() }) { append(it.toJsonString()) }
        append(',')
        appendJsonField("closeModalsToken", closeModalsToken)
        append(',')
        appendJsonField("submitIntroSuccessToken", submitIntroSuccessToken)
        append(',')
        appendJsonField("notificationMessage", notificationMessage)
        append(',')
        appendJsonField("notificationToken", notificationToken)
        append(',')
        appendJsonField("showPlaybackTimeOverlay", showPlaybackTimeOverlay)
        append(',')
        appendJsonField("showTorrentStatsOverlay", showTorrentStatsOverlay)
        append(',')
        appendJsonField("torrentStatsText", torrentStatsText)
        if (playbackStats != null) {
            append(",\"playbackStats\":{")
            appendJsonField("videoCodec", playbackStats.videoCodec)
            append(',')
            appendJsonField("videoDecoder", playbackStats.videoDecoder)
            append(',')
            appendJsonField("videoResolution", playbackStats.videoResolution)
            append(',')
            appendJsonField("videoColor", playbackStats.videoColor)
            append(',')
            append("\"containerFps\":").append(playbackStats.containerFps)
            append(',')
            append("\"outputFps\":").append(playbackStats.outputFps)
            append(',')
            append("\"displayFps\":").append(playbackStats.displayFps)
            append(',')
            appendJsonField("videoBitrateKbps", playbackStats.videoBitrateKbps)
            append(',')
            appendJsonField("droppedFrames", playbackStats.droppedFrames)
            append(',')
            appendJsonField("voDroppedFrames", playbackStats.voDroppedFrames)
            append(',')
            appendJsonField("audioCodec", playbackStats.audioCodec)
            append(',')
            appendJsonField("audioChannels", playbackStats.audioChannels)
            append(',')
            appendJsonField("audioSampleRateHz", playbackStats.audioSampleRateHz)
            append(',')
            appendJsonField("audioBitrateKbps", playbackStats.audioBitrateKbps)
            append(',')
            append("\"cacheDurationSec\":").append(playbackStats.cacheDurationSec)
            append(',')
            appendJsonField("cacheUsedBytes", playbackStats.cacheUsedBytes)
            append(',')
            appendJsonField("cacheSpeedBytes", playbackStats.cacheSpeedBytes)
            append('}')
        } else {
            append(",\"playbackStats\":null")
        }
        append(',')
        appendJsonArrayField("badges", badges) {
            append("{\"name\":").append(it.name.toJsonString())
            append(",\"imageUrl\":").append(it.imageURL.toJsonString())
            append(",\"tagColor\":").append(it.tagColor.toJsonString())
            append(",\"tagStyle\":").append(it.tagStyle.toJsonString())
            append(",\"borderColor\":").append(it.borderColor.toJsonString())
            append('}')
        }
        append(',')
        appendJsonArrayField("chapters", chapters) {
            append("{\"startMs\":").append(it.startMs)
            append(",\"title\":").append(it.title.toJsonString())
            append('}')
        }
        append(',')
        appendJsonField("chaptersLabel", chaptersLabel)
        append(',')
        appendJsonField("chaptersPanelTitle", chaptersPanelTitle)
        append(',')
        appendJsonField("chapterRemainingTemplate", chapterRemainingTemplate)
        append(',')
        appendJsonField("autoSyncedLabel", autoSyncedLabel)
        append(',')
        append("\"playbackStatsMetadata\":")
        val metadata = playbackStatsMetadata?.let {
            it.copy(providerLogo = it.providerLogo?.let { logo -> WebviewImages.resolve(logo, onImageReady) })
        }
        append(partyJson.encodeToString(metadata))
        append(",\"party\":")
        append(partyJson.encodeToString(PlayerControlPartyState.serializer(), party.withWebviewImages(onImageReady)))
        append('}')
    }

private val partyJson = Json { encodeDefaults = true }

private fun PlayerControlPartyState.withWebviewImages(onImageReady: () -> Unit): PlayerControlPartyState =
    copy(profile = profile.copy(imageUrl = if (active) "" else WebviewImages.resolve(profile.imageUrl, onImageReady)))

private fun PlayerControlsState.nativeControlsStructureKey(): PlayerControlsState =
    copy(
        isPlaying = false,
        isLoading = false,
        durationMs = 0L,
        positionMs = 0L,
        bufferedPositionMs = 0L,
    )

private fun StringBuilder.appendJsonField(name: String, value: String) {
    append('"').append(name).append("\":")
    append(value.toJsonString())
}

private fun StringBuilder.appendJsonField(name: String, value: Boolean) {
    append('"').append(name).append("\":").append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Long) {
    append('"').append(name).append("\":").append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Float?) {
    append('"').append(name).append("\":")
    if (value == null || value.isNaN() || value.isInfinite()) {
        append("null")
    } else if (name == "holdToSpeedValue") {
        append(value.coerceIn(1f, 4f))
    } else {
        append(value.coerceIn(0f, 1f))
    }
}

private fun StringBuilder.appendJsonVolumeLevel(name: String, value: Float?) {
    append('"').append(name).append("\":")
    if (value == null || value.isNaN() || value.isInfinite()) {
        append("null")
    } else {
        append(value.coerceDesktopPlayerVolumeLevel())
    }
}

private fun StringBuilder.appendJsonField(name: String, value: Int) {
    append('"').append(name).append("\":").append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: SubtitleStyleState) {
    append('"').append(name).append("\":")
    appendSubtitleStyleJson(value)
}

private inline fun <T> StringBuilder.appendJsonArrayField(
    name: String,
    values: List<T>,
    appendValue: StringBuilder.(T) -> Unit,
) {
    append('"').append(name).append("\":[")
    values.forEachIndexed { index, value ->
        if (index > 0) append(',')
        appendValue(value)
    }
    append(']')
}

private fun StringBuilder.appendFilterItemJson(item: PlayerControlFilterItem) {
    append('{')
    appendJsonField("id", item.id)
    append(',')
    appendJsonField("label", item.label)
    append(',')
    appendJsonField("isSelected", item.isSelected)
    append(',')
    appendJsonField("isLoading", item.isLoading)
    append(',')
    appendJsonField("hasError", item.hasError)
    append('}')
}

private fun StringBuilder.appendSeasonItemJson(item: PlayerControlSeasonItem) {
    append('{')
    appendJsonField("season", item.season)
    append(',')
    appendJsonField("label", item.label)
    append(',')
    appendJsonField("isSelected", item.isSelected)
    append('}')
}

private fun StringBuilder.appendSourceItemJson(item: PlayerControlSourceItem) {
    append('{')
    appendJsonField("index", item.index)
    append(',')
    appendJsonField("filterId", item.filterId)
    append(',')
    appendJsonField("label", item.label)
    append(',')
    appendJsonField("subtitle", item.subtitle)
    append(',')
    appendJsonField("addonName", item.addonName)
    append(',')
    appendJsonField("addonLogo", item.addonLogo)
    append(',')
    appendJsonField("showAddonLogo", item.showAddonLogo)
    append(',')
    appendJsonField("isCurrent", item.isCurrent)
    append(',')
    appendJsonField("isEnabled", item.isEnabled)
    append(',')
    appendJsonField("formattedSize", item.formattedSize)
    append(',')
    appendJsonField("badgePlacement", item.badgePlacement)
    append(',')
    appendJsonArrayField("badges", item.badges) { badge ->
        append('{')
        appendJsonField("name", badge.name)
        append(',')
        appendJsonField("imageURL", badge.imageURL)
        append(',')
        appendJsonField("tagColor", badge.tagColor)
        append(',')
        appendJsonField("tagStyle", badge.tagStyle)
        append(',')
        appendJsonField("borderColor", badge.borderColor)
        append('}')
    }
    append('}')
}

private fun StringBuilder.appendEpisodeItemJson(item: PlayerControlEpisodeItem) {
    append('{')
    appendJsonField("index", item.index)
    append(',')
    appendJsonField("id", item.id)
    append(',')
    appendJsonField("title", item.title)
    append(',')
    appendJsonField("code", item.code)
    append(',')
    appendJsonField("overview", item.overview)
    append(',')
    appendJsonField("thumbnail", item.thumbnail)
    append(',')
    appendJsonField("released", item.released)
    append(',')
    appendJsonField("rating", item.rating)
    append(',')
    appendJsonField("season", item.season)
    append(',')
    appendJsonField("episode", item.episode)
    append(',')
    appendJsonField("isCurrent", item.isCurrent)
    append(',')
    appendJsonField("isWatched", item.isWatched)
    append(',')
    appendJsonField("isFiller", item.isFiller)
    append(',')
    appendJsonField("isMixed", item.isMixed)
    append('}')
}

private fun StringBuilder.appendAddonSubtitleItemJson(item: PlayerControlAddonSubtitleItem) {
    append('{')
    appendJsonField("index", item.index)
    append(',')
    appendJsonField("id", item.id)
    append(',')
    appendJsonField("display", item.display)
    append(',')
    appendJsonField("language", item.language)
    append(',')
    appendJsonField("languageLabel", item.languageLabel)
    append(',')
    appendJsonField("addonName", item.addonName)
    append(',')
    appendJsonField("isSelected", item.isSelected)
    append('}')
}

private fun StringBuilder.appendSubtitleLanguageItemJson(item: PlayerControlSubtitleLanguageItem) {
    append('{')
    appendJsonField("key", item.key)
    append(',')
    appendJsonField("label", item.label)
    append(',')
    appendJsonField("count", item.count)
    append(',')
    appendJsonField("isSelected", item.isSelected)
    append('}')
}

private fun StringBuilder.appendSubtitleOptionItemJson(item: PlayerControlSubtitleOptionItem) {
    append('{')
    appendJsonField("id", item.id)
    append(',')
    appendJsonField("languageKey", item.languageKey)
    append(',')
    appendJsonField("kind", item.kind)
    append(',')
    appendJsonField("index", item.index)
    append(',')
    appendJsonField("sourceLabel", item.sourceLabel)
    append(',')
    appendJsonField("title", item.title)
    append(',')
    appendJsonField("metadata", item.metadata)
    append(',')
    appendJsonField("isSelected", item.isSelected)
    append(',')
    appendJsonField("isAutoSynced", item.isAutoSynced)
    append('}')
}

private fun StringBuilder.appendSubtitleCueItemJson(item: PlayerControlSubtitleCueItem) {
    append('{')
    appendJsonField("index", item.index)
    append(',')
    appendJsonField("timeMs", item.timeMs)
    append(',')
    appendJsonField("timeLabel", item.timeLabel)
    append(',')
    appendJsonField("text", item.text)
    append('}')
}

private fun StringBuilder.appendParentalWarningJson(item: ParentalWarning) {
    append('{')
    appendJsonField("label", item.label)
    append(',')
    appendJsonField("severity", item.severity)
    append('}')
}

private fun StringBuilder.appendSubtitleStyleJson(style: SubtitleStyleState) {
    append('{')
    appendJsonField("textColor", style.textColor.toStorageHexString())
    append(',')
    appendJsonField("outlineColor", style.outlineColor.toStorageHexString())
    append(',')
    appendJsonField("backgroundColor", style.backgroundColor.toStorageHexString())
    append(',')
    appendJsonField("outlineEnabled", style.outlineEnabled)
    append(',')
    appendJsonField("bold", style.bold)
    append(',')
    appendJsonField("fontSizeSp", style.fontSizeSp)
    append(',')
    appendJsonField("bottomOffset", style.bottomOffset)
    append(',')
    appendJsonField("fontName", style.fontName)
    append(',')
    appendJsonField("outlineEffect", style.outlineEffect.id)
    append(',')
    appendJsonField("outlineWidth", style.outlineWidth)
    append('}')
}

private fun String.toJsonString(): String =
    buildString(length + 2) {
        append('"')
        for (char in this@toJsonString) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
