package com.nuvio.app.features.discordrpc

import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.AppPresenceState
import com.nuvio.app.core.ui.PresenceSnapshot
import com.nuvio.app.features.settings.DiscordActivityMode
import com.nuvio.app.features.settings.DiscordActivityType
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

private data class SyncConfig(
    val snapshot: PresenceSnapshot?,
    val mode: DiscordActivityMode,
    val activityType: DiscordActivityType,
    val fullPoster: Boolean,
)

// Discord activity type: 0 = Playing, 2 = Listening, 3 = Watching, 5 = Competing.
// Using 0 (Playing/Game) enables Discord to render the full rich presence game card
// in menus and tabs. Activity type 3 (Watching) renders media playback as a watching
// presence with the TV/watch badge and media title.
private const val ActivityTypeGame = 0
private const val ActivityTypeWatching = 3

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
                            DiscordRichPresenceRepository.activityType,
                            DiscordRichPresenceRepository.fullPosterEnabled,
                        ) { snapshot, mode, type, fullPoster ->
                            SyncConfig(snapshot, mode, type, fullPoster)
                        }.collect { config ->
                            val activity = resolveDiscordActivity(
                                config.snapshot,
                                config.mode,
                                config.activityType,
                                config.fullPoster,
                            )
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
private const val DefaultAppIconUrl =
    "https://raw.githubusercontent.com/codeineXO/NuvioCodeineXO/main/composeApp/src/commonMain/composeResources/drawable/app_icon_original_transparent.png?v=2"
private const val MaxPosterUrlLength = 400
private const val ProxyContainWidth = 512
private const val ProxyContainHeight = 512

internal fun buildImdbUrl(imdbId: String?, title: String? = null): String? {
    if (!imdbId.isNullOrBlank()) {
        val cleanId = Regex("tt\\d+").find(imdbId)?.value ?: imdbId.trim()
        if (cleanId.startsWith("tt", ignoreCase = true)) {
            return "https://www.imdb.com/title/$cleanId/"
        }
    }
    if (!title.isNullOrBlank()) {
        val encoded = runCatching { java.net.URLEncoder.encode(title.trim(), "UTF-8") }.getOrNull()
        if (!encoded.isNullOrBlank()) {
            return "https://www.imdb.com/find/?q=$encoded"
        }
    }
    return null
}

internal fun buildEpisodeImdbUrl(
    episodeImdbId: String?,
    seriesImdbId: String?,
    seasonNumber: Int? = null,
    episodeLabel: String? = null,
    title: String? = null,
): String? {
    if (!episodeImdbId.isNullOrBlank()) {
        val cleanEpisodeId = Regex("tt\\d+").find(episodeImdbId)?.value ?: episodeImdbId.trim()
        if (cleanEpisodeId.startsWith("tt", ignoreCase = true)) {
            return "https://www.imdb.com/title/$cleanEpisodeId/"
        }
    }
    val resolvedSeason = seasonNumber ?: episodeLabel?.let { label ->
        Regex("""S(\d+)E\d+""").find(label.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
    if (!seriesImdbId.isNullOrBlank() && resolvedSeason != null && resolvedSeason > 0) {
        val cleanSeriesId = Regex("tt\\d+").find(seriesImdbId)?.value ?: seriesImdbId.trim()
        if (cleanSeriesId.startsWith("tt", ignoreCase = true)) {
            return "https://www.imdb.com/title/$cleanSeriesId/episodes/?season=$resolvedSeason"
        }
    }
    return buildImdbUrl(seriesImdbId, title)
}

private val DownloadButton = DiscordActivityButton(
    label = "Download NuvioCodeineXO",
    url = ForkDownloadUrl,
)

internal fun formatDiscordPosterAsset(url: String?, fullPosterEnabled: Boolean): String {
    if (url.isNullOrBlank()) return DefaultAppIconUrl
    if (!fullPosterEnabled) return url
    val trimmed = url.trim()
    if (trimmed.length > MaxPosterUrlLength) return trimmed
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }
    return runCatching {
        val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
        "https://wsrv.nl/?url=$encoded&w=$ProxyContainWidth&h=$ProxyContainHeight&fit=contain&cbg=transparent&output=png"
    }.getOrDefault(trimmed)
}

internal val sessionStartSecs = System.currentTimeMillis() / 1_000L

// Shown when nothing has been published yet.
internal fun createIdleActivity(activityType: DiscordActivityType = DiscordActivityType.WATCHING): DiscordActivity {
    val isWatching = activityType == DiscordActivityType.WATCHING
    return DiscordActivity(
        type = if (isWatching) ActivityTypeWatching else ActivityTypeGame,
        name = AppActivityName,
        details = "Exploring NuvioCodeineXO",
        state = "In Menus",
        statusDisplayType = StatusDisplayTypeDetails,
        timestamps = DiscordActivityTimestamps(start = sessionStartSecs),
        assets = DiscordActivityAssets(
            largeImage = DefaultAppIconUrl,
            largeText = AppActivityName,
        ),
        buttons = listOf(DownloadButton),
    )
}

internal val IdleActivity: DiscordActivity get() = createIdleActivity(DiscordActivityType.WATCHING)

internal fun resolveDiscordActivity(
    snapshot: PresenceSnapshot?,
    mode: DiscordActivityMode,
    activityType: DiscordActivityType = DiscordActivityType.WATCHING,
    fullPosterEnabled: Boolean = false,
): DiscordActivity? {
    return when {
        mode == DiscordActivityMode.ONLY_WATCHING -> {
            if (snapshot is PresenceSnapshot.Player) {
                snapshot.toDiscordActivity(activityType, fullPosterEnabled)
            } else {
                null
            }
        }
        else -> snapshot?.toDiscordActivity(activityType, fullPosterEnabled) ?: createIdleActivity(activityType)
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

internal fun PresenceSnapshot.toDiscordActivity(
    activityType: DiscordActivityType = DiscordActivityType.WATCHING,
    fullPosterEnabled: Boolean = false,
): DiscordActivity = when (this) {
    is PresenceSnapshot.Tab -> {
        val (tabDetails, tabState) = when (tab) {
            com.nuvio.app.AppScreenTab.Settings -> "Tweaking NuvioCodeineXO" to "In Settings"
            else -> "Exploring NuvioCodeineXO" to "Exploring Content"
        }
        val isWatching = activityType == DiscordActivityType.WATCHING
        DiscordActivity(
            type = if (isWatching) ActivityTypeWatching else ActivityTypeGame,
            name = AppActivityName,
            details = tabDetails,
            state = tabState,
            statusDisplayType = StatusDisplayTypeDetails,
            timestamps = DiscordActivityTimestamps(start = sessionStartSecs),
            assets = DiscordActivityAssets(
                largeImage = DefaultAppIconUrl,
                largeText = AppActivityName,
            ),
            buttons = listOf(DownloadButton),
        )
    }
    is PresenceSnapshot.Details -> {
        val imdbUrl = buildImdbUrl(imdbId, title)
        val isWatching = activityType == DiscordActivityType.WATCHING
        DiscordActivity(
            type = if (isWatching) ActivityTypeWatching else ActivityTypeGame,
            name = if (isWatching) title.takeIf { it.isNotBlank() } ?: AppActivityName else AppActivityName,
            details = title,
            detailsUrl = imdbUrl,
            state = "Overview",
            statusDisplayType = StatusDisplayTypeDetails,
            timestamps = DiscordActivityTimestamps(start = sessionStartSecs),
            assets = DiscordActivityAssets(
                largeImage = formatDiscordPosterAsset(posterUrl, fullPosterEnabled),
                largeText = title,
                largeUrl = imdbUrl,
            ),
            buttons = listOf(DownloadButton),
        )
    }
    is PresenceSnapshot.StreamSelection -> {
        val episode = episodeLabel?.toDiscordEpisodeLabel()
        val imdbUrl = buildImdbUrl(imdbId, title)
        val isWatching = activityType == DiscordActivityType.WATCHING
        DiscordActivity(
            type = if (isWatching) ActivityTypeWatching else ActivityTypeGame,
            name = if (isWatching) title.takeIf { it.isNotBlank() } ?: AppActivityName else AppActivityName,
            details = title,
            detailsUrl = imdbUrl,
            state = if (!episode.isNullOrBlank()) episode else "Selecting Stream",
            statusDisplayType = StatusDisplayTypeDetails,
            timestamps = DiscordActivityTimestamps(start = sessionStartSecs),
            assets = DiscordActivityAssets(
                largeImage = formatDiscordPosterAsset(posterUrl, fullPosterEnabled),
                largeText = title,
                largeUrl = imdbUrl,
            ),
            buttons = listOf(DownloadButton),
        )
    }
    is PresenceSnapshot.Player -> {
        val isSeriesContent = isSeries || !episodeLabel.isNullOrBlank()
        val episode = episodeLabel?.toDiscordEpisodeLabel()
        val playerState = if (isSeriesContent) {
            val epName = episode ?: "Episode"
            if (isPlaying) epName else "$epName • Paused"
        } else {
            if (isPlaying) "Watching" else "Paused"
        }
        val imdbUrl = buildImdbUrl(imdbId, title)
        val episodeImdbUrl = if (isSeriesContent) {
            buildEpisodeImdbUrl(
                episodeImdbId = episodeImdbId,
                seriesImdbId = imdbId,
                seasonNumber = seasonNumber,
                episodeLabel = episodeLabel,
                title = title,
            )
        } else {
            imdbUrl
        }
        // Discord expects Unix timestamps in seconds, not milliseconds.
        val startSecs = (System.currentTimeMillis() - positionMs) / 1_000L
        val episodeThumb = episodeThumbnailUrl?.takeIf { it.isNotBlank() }
        val poster = posterUrl?.takeIf { it.isNotBlank() }
        val formattedPoster = poster?.let { formatDiscordPosterAsset(it, fullPosterEnabled) }
        val isWatching = activityType == DiscordActivityType.WATCHING
        DiscordActivity(
            type = if (isWatching) ActivityTypeWatching else ActivityTypeGame,
            name = if (isWatching) title.takeIf { it.isNotBlank() } ?: AppActivityName else AppActivityName,
            details = title,
            detailsUrl = imdbUrl,
            state = playerState,
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
            assets = if (formattedPoster != null || episodeThumb != null) {
                val hasSmallImage = episodeThumb != null && formattedPoster != null
                DiscordActivityAssets(
                    largeImage = formattedPoster ?: episodeThumb,
                    largeText = title,
                    largeUrl = imdbUrl,
                    smallImage = if (hasSmallImage) episodeThumb else null,
                    smallText = if (hasSmallImage) (episode ?: title) else null,
                    smallUrl = if (hasSmallImage) episodeImdbUrl else null,
                )
            } else {
                DiscordActivityAssets(
                    largeImage = DefaultAppIconUrl,
                    largeText = title,
                    largeUrl = imdbUrl,
                )
            },
            buttons = listOf(DownloadButton),
        )
    }
}
