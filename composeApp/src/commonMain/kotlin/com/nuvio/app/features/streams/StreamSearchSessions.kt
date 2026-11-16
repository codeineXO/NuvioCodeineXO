package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.fetchAddonStreamResponse
import com.nuvio.app.features.addons.streamCacheLifetimeMs
import com.nuvio.app.features.debrid.LocalDebridAvailabilityService
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.pluginContentId
import com.nuvio.app.features.servers.ServerStreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.streams_failed_to_load_scraper
import org.jetbrains.compose.resources.getString

/** One source search: the same title, episode and source setup for the same profile. */
internal data class StreamSearchKey(
    val profileId: Int,
    val type: String,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val sourceConfiguration: String,
)

/** The addons, servers and plugin scrapers one search queries. */
internal class StreamSearchTargets(
    val type: String,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val streamAddons: List<InstalledStreamAddonTarget>,
    val serverSources: List<ServerStreamSource>,
    val pluginProviderGroups: List<PluginProviderGroup>,
    /** The debrid account streams are checked with, as the checked streams are kept with the search. */
    val debridConfiguration: String = LocalDebridAvailabilityService.cacheCheckConfiguration(),
) {
    val totalTasks: Int
        get() = streamAddons.size + serverSources.size + pluginProviderGroups.sumOf { it.scrapers.size }

    /** Changes whenever the queried sources or the debrid account change, so a search for an older setup is not reused. */
    val sourceConfiguration: String
        get() = buildList {
            streamAddons.forEach { add("addon:${it.addonId}") }
            serverSources.forEach { add("server:${it.addonId}") }
            pluginProviderGroups.forEach { group ->
                add("plugin:${group.addonId}:${group.scrapers.joinToString(",") { it.id }}")
            }
            add("debrid:$debridConfiguration")
        }.joinToString("|")
}

/**
 * Keeps source searches alive independently of the screens that show them, so the stream screen,
 * the player's sources panel and the next-episode search share one search per title and episode.
 * A search in progress is joined, and a finished one is reused for a short time. Results are
 * memory-only because addon links may expire.
 */
internal object StreamSearchSessions {
    private const val MAX_SESSIONS = 12

    private val log = Logger.withTag("StreamSearchSessions")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val sessions = LinkedHashMap<StreamSearchKey, Session>()

    private data class Progress(
        val completions: List<StreamLoadCompletion> = emptyList(),
        val isComplete: Boolean = false,
    )

    private class Session(retained: List<StreamLoadCompletion> = emptyList()) {
        val progress = MutableStateFlow(Progress(completions = retained))
        var job: Job? = null
        var invalidated = false
    }

    /**
     * Emits every result of the search for [targets], first the ones that already arrived and
     * then new ones, and completes when the search ends. A new search starts only when none is
     * held for the same request or [forceRefresh] is set.
     */
    fun search(
        profileId: Int,
        targets: StreamSearchTargets,
        forceRefresh: Boolean,
    ): Flow<StreamLoadCompletion> = flow {
        val key = StreamSearchKey(
            profileId = profileId,
            type = targets.type,
            videoId = targets.videoId,
            season = targets.season,
            episode = targets.episode,
            sourceConfiguration = targets.sourceConfiguration,
        )
        val session = acquire(key, targets, forceRefresh)
        var emitted = 0
        emitAll(
            session.progress.transformWhile { progress ->
                while (emitted < progress.completions.size) {
                    emit(progress.completions[emitted])
                    emitted++
                }
                !progress.isComplete
            },
        )
    }

    /** Drops every held search, for example after the source setup or the profile changed. */
    suspend fun clear() = mutex.withLock {
        sessions.values.forEach { it.cancel() }
        sessions.clear()
    }

    private suspend fun acquire(key: StreamSearchKey, targets: StreamSearchTargets, forceRefresh: Boolean): Session {
        var created: Session? = null
        val selected = mutex.withLock {
            removeExpiredLocked()
            removeObsoleteLocked(key)
            var retained = emptyList<StreamLoadCompletion>()
            if (forceRefresh) {
                sessions.remove(key)?.cancel()
            } else {
                sessions[key]?.let { existing ->
                    val progress = existing.progress.value
                    retained = progress.completions.filter { it.isReusable(epochMs()) }
                    if (!progress.isComplete || retained.size == targets.totalTasks) {
                        log.d { "Joining source search for ${key.type} ${key.videoId}" }
                        return@withLock existing
                    }
                }
            }
            Session(retained).also { session ->
                val retainedKeys = retained.mapTo(mutableSetOf()) { it.sourceKey }
                session.job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        coroutineScope {
                            launchStreamSearchTasks(targets, forceRefresh, retainedKeys) { completion ->
                                session.progress.update { it.copy(completions = it.completions + completion) }
                            }
                        }
                    } finally {
                        withContext(NonCancellable) { complete(key, session) }
                    }
                }
                sessions[key] = session
                trimLocked()
                created = session
            }
        }
        withContext(NonCancellable) { created?.job?.start() }
        return selected
    }

    private suspend fun complete(key: StreamSearchKey, session: Session) {
        session.progress.update { it.copy(isComplete = true) }
        if (session.invalidated) return
        mutex.withLock {
            if (sessions[key] !== session) return@withLock
            val hasResults = session.progress.value.completions.any { it.isReusable(epochMs()) }
            if (!hasResults) sessions.remove(key)
        }
    }

    private fun removeExpiredLocked() {
        val now = epochMs()
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next().value
            if (!session.progress.value.isComplete) continue
            if (session.progress.value.completions.none { it.isReusable(now) }) {
                iterator.remove()
                session.cancel()
            }
        }
    }

    /** Another profile's searches, and searches for the same episode with an older source setup, are dropped. */
    private fun removeObsoleteLocked(requested: StreamSearchKey) {
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val (existing, session) = iterator.next()
            val otherProfile = existing.profileId != requested.profileId
            val sameEpisode = existing.type == requested.type &&
                existing.videoId == requested.videoId &&
                existing.season == requested.season &&
                existing.episode == requested.episode
            if (otherProfile || (sameEpisode && existing.sourceConfiguration != requested.sourceConfiguration)) {
                iterator.remove()
                session.cancel()
            }
        }
    }

    private fun trimLocked() {
        while (sessions.size > MAX_SESSIONS) {
            val oldest = sessions.entries.iterator()
            if (!oldest.hasNext()) return
            val session = oldest.next().value
            oldest.remove()
            session.cancel()
        }
    }

    private fun Session.cancel() {
        invalidated = true
        progress.update { it.copy(isComplete = true) }
        job?.cancel()
    }
}

/** Queries every addon, server and plugin scraper of [targets] in parallel and reports each result. */
private fun CoroutineScope.launchStreamSearchTasks(
    targets: StreamSearchTargets,
    forceRefresh: Boolean,
    retainedKeys: Set<String>,
    onCompletion: (StreamLoadCompletion) -> Unit,
) {
    val log = Logger.withTag("StreamSearch")
    targets.streamAddons.filterNot { it.addonId in retainedKeys }.forEach { addon ->
        launch {
            val url = buildAddonResourceUrl(
                manifestUrl = addon.manifest.transportUrl,
                resource = "stream",
                type = targets.type,
                id = targets.videoId,
            )
            log.d { "Fetching streams from ${addon.addonName}" }
            var cacheExpiresAtMs = 0L
            val group = runCatchingUnlessCancelled {
                val response = fetchAddonStreamResponse(url = url, forceRefresh = forceRefresh)
                cacheExpiresAtMs = epochMs() + response.streamCacheLifetimeMs()
                StreamParser.parse(
                    payload = response.body,
                    addonName = addon.addonName,
                    addonId = addon.addonId,
                    addonLogo = addon.manifest.logoUrl,
                )
            }.fold(
                onSuccess = { streams ->
                    log.d { "Got ${streams.size} streams from ${addon.addonName}" }
                    AddonStreamGroup(addon.addonName, addon.addonId, streams, isLoading = false)
                },
                onFailure = { error ->
                    log.w(error) { "Failed to fetch streams from ${addon.addonName}" }
                    AddonStreamGroup(addon.addonName, addon.addonId, emptyList(), isLoading = false, error = error.message)
                },
            )
            onCompletion(StreamLoadCompletion.Addon(group.withDebridAvailability(), cacheExpiresAtMs))
        }
    }

    targets.serverSources.filterNot { it.addonId in retainedKeys }.forEach { source ->
        launch { onCompletion(StreamLoadCompletion.Addon(source.load())) }
    }

    targets.pluginProviderGroups.forEach { providerGroup ->
        providerGroup.scrapers.filterNot { "plugin:${it.id}" in retainedKeys }.forEach { scraper ->
            launch {
                val completion = PluginRepository.executeScraper(
                    scraper = scraper,
                    tmdbId = pluginContentId(
                        videoId = targets.videoId,
                        season = targets.season,
                        episode = targets.episode,
                    ),
                    mediaType = targets.type,
                    season = targets.season,
                    episode = targets.episode,
                ).fold(
                    onSuccess = { results ->
                        StreamLoadCompletion.PluginScraper(
                            addonId = providerGroup.addonId,
                            sourceKey = "plugin:${scraper.id}",
                            streams = results.map { result ->
                                result.toStreamItem(
                                    scraper = scraper,
                                    addonName = providerGroup.addonName,
                                    addonId = providerGroup.addonId,
                                    includeScraperNameInSubtitle = false,
                                )
                            },
                            error = null,
                        )
                    },
                    onFailure = { error ->
                        log.w(error) { "Plugin scraper failed: ${scraper.name}" }
                        StreamLoadCompletion.PluginScraper(
                            addonId = providerGroup.addonId,
                            sourceKey = "plugin:${scraper.id}",
                            streams = emptyList(),
                            error = error.message ?: getString(Res.string.streams_failed_to_load_scraper, scraper.name),
                        )
                    },
                )
                onCompletion(completion)
            }
        }
    }
}

/**
 * Checks which torrents of an installed addon are cached with the debrid service, once per search
 * as on TV, so every screen joining the search gets the checked streams instead of asking again.
 * A failed check leaves the streams unchecked rather than ending the whole search.
 */
private suspend fun AddonStreamGroup.withDebridAvailability(): AddonStreamGroup {
    if (streams.isEmpty()) return this
    val eligibleGroupIds = setOf(addonId)
    if (!LocalDebridAvailabilityService.hasPendingCacheCheck(listOf(this), eligibleGroupIds)) return this
    val checking = LocalDebridAvailabilityService.markChecking(listOf(this), eligibleGroupIds).firstOrNull() ?: this
    return runCatchingUnlessCancelled {
        LocalDebridAvailabilityService.annotateCachedAvailability(listOf(checking), eligibleGroupIds).firstOrNull()
    }.getOrNull() ?: this
}

/** Ends the loading state of sources a dropped search never reported. */
internal fun StreamsUiState.withoutPendingGroups(): StreamsUiState {
    if (groups.none { it.isLoading } && !isAnyLoading) return this
    val settled = groups.map { group -> if (group.isLoading) group.copy(isLoading = false) else group }
    return copy(
        groups = settled,
        isAnyLoading = false,
        emptyStateReason = settled.toEmptyStateReason(anyLoading = false),
    )
}
