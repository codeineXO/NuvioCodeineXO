package com.nuvio.app.features.discordrpc

import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.AppPresenceState
import com.nuvio.app.core.ui.PresenceSnapshot
import com.nuvio.app.features.settings.DiscordActivityMode
import com.nuvio.app.features.settings.DiscordRichPresenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private class DiscordDisconnected : Exception()

private const val ReconnectDelayMs = 15_000L

// Discord activity type: 0 = Playing, 2 = Listening, 3 = Watching, 5 = Competing.
// Nuvio is a media app, so every presence it publishes is a Watching one -- including the menus,
// where Discord renders "Watching Nuvio" instead of the default "Playing Nuvio" (a game).
private const val WatchingActivityType = 3

internal object DiscordPresenceManager {
    private val log = Logger.withTag("DiscordPresenceManager")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = DiscordIpcClient(DiscordConfig.CLIENT_ID)
    private var syncJob: Job? = null
    private var lastActivity: DiscordActivity? = null

    fun start() {
        if (DiscordConfig.CLIENT_ID.isBlank()) return
        DiscordRichPresenceRepository.ensureLoaded()
        scope.launch {
            DiscordRichPresenceRepository.enabled.collectLatest { enabled ->
                if (enabled) startSync() else stopSync()
            }
        }
    }

    fun shutdown() {
        runBlocking { stopSync() }
    }

    private suspend fun startSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                val connected = client.connect()
                if (connected) {
                    lastActivity = null
                    try {
                        combine(
                            AppPresenceState.current,
                            DiscordRichPresenceRepository.activityMode,
                        ) { snapshot, mode ->
                            snapshot to mode
                        }.collect { (snapshot, mode) ->
                            val activity = resolveDiscordActivity(snapshot, mode)
                            if (activity == lastActivity) return@collect
                            if (client.setActivity(activity)) {
                                lastActivity = activity
                            } else {
                                throw DiscordDisconnected()
                            }
                        }
                    } catch (e: DiscordDisconnected) {
                        log.d { "Discord IPC disconnected, retrying" }
                    }
                }
                delay(ReconnectDelayMs)
            }
        }
    }

    private suspend fun stopSync() {
        syncJob?.cancel()
        syncJob = null
        if (lastActivity != null) client.setActivity(null)
        delay(300L)
        lastActivity = null
        client.disconnect()
    }
}

private const val AppActivityName = "NuvioCodeineXO"
private const val StatusDisplayTypeDetails = 2
private const val ForkDownloadUrl = "https://github.com/codeineXO/NuvioCodeineXO"

private val DownloadButton = DiscordActivityButton(
    label = "Download NuvioCodeineXO",
    url = ForkDownloadUrl,
)

// Shown when nothing has been published yet, so the profile reads "Watching NuvioCodeineXO".
internal val IdleActivity = DiscordActivity(
    type = WatchingActivityType,
    name = AppActivityName,
    details = "Browsing NuvioCodeineXO",
    buttons = listOf(DownloadButton),
)

internal fun resolveDiscordActivity(
    snapshot: PresenceSnapshot?,
    mode: DiscordActivityMode,
): DiscordActivity? {
    return when {
        mode == DiscordActivityMode.ONLY_WATCHING -> {
            if (snapshot is PresenceSnapshot.Player) {
                snapshot.toDiscordActivity()
            } else {
                null
            }
        }
        else -> snapshot?.toDiscordActivity() ?: IdleActivity
    }
}

internal fun String.toDiscordEpisodeLabel(): String {
    val match = Regex("""S(\d+)E(\d+)(?:\s*-\s*(.*))?""").matchEntire(trim())
        ?: return this
    val season = match.groupValues[1]
    val episode = match.groupValues[2]
    val title = match.groupValues.getOrNull(3).orEmpty().trim()
    return if (title.isBlank()) "S$season, E$episode" else "S$season, E$episode: $title"
}

internal fun PresenceSnapshot.toDiscordActivity(): DiscordActivity = when (this) {
    is PresenceSnapshot.Tab -> DiscordActivity(
        type = WatchingActivityType,
        name = AppActivityName,
        details = "Browsing ${tab.name}",
        buttons = listOf(DownloadButton),
    )
    is PresenceSnapshot.Details -> DiscordActivity(
        type = WatchingActivityType,
        name = AppActivityName,
        details = "Viewing $title",
        statusDisplayType = StatusDisplayTypeDetails,
        assets = posterUrl?.takeIf { it.isNotBlank() }?.let {
            DiscordActivityAssets(
                largeImage = it,
                largeText = title,
            )
        },
        buttons = listOf(DownloadButton),
    )
    is PresenceSnapshot.StreamSelection -> {
        val episode = episodeLabel?.toDiscordEpisodeLabel()
        DiscordActivity(
            type = WatchingActivityType,
            name = AppActivityName,
            details = "Selecting stream",
            state = if (!episode.isNullOrBlank()) "$title ($episode)" else title,
            statusDisplayType = StatusDisplayTypeDetails,
            assets = posterUrl?.takeIf { it.isNotBlank() }?.let {
                DiscordActivityAssets(
                    largeImage = it,
                    largeText = title,
                )
            },
            buttons = listOf(DownloadButton),
        )
    }
    is PresenceSnapshot.Player -> {
        val episode = episodeLabel?.toDiscordEpisodeLabel()
        // Discord expects Unix timestamps in seconds, not milliseconds.
        val startSecs = (System.currentTimeMillis() - positionMs) / 1_000L
        val episodeThumb = episodeThumbnailUrl?.takeIf { it.isNotBlank() }
        DiscordActivity(
            type = WatchingActivityType,
            name = AppActivityName,
            details = title,
            state = if (isPlaying) episode else episode?.let { "$it • Paused" } ?: "Paused",
            statusDisplayType = StatusDisplayTypeDetails,
            timestamps = if (isPlaying) {
                // start + end -> Discord renders a live progress bar with time remaining.
                DiscordActivityTimestamps(
                    start = startSecs,
                    end = if (durationMs > 0L) startSecs + durationMs / 1_000L else null,
                )
            } else {
                null
            },
            assets = if (posterUrl != null || episodeThumb != null) {
                val hasSmallImage = episodeThumb != null && posterUrl != null
                DiscordActivityAssets(
                    largeImage = posterUrl ?: episodeThumb,
                    largeText = title,
                    smallImage = if (hasSmallImage) episodeThumb else null,
                    smallText = if (hasSmallImage) (episode ?: title) else null,
                )
            } else {
                null
            },
            buttons = listOf(DownloadButton),
        )
    }
}
