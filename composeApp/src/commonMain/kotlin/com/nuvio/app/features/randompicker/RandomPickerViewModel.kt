package com.nuvio.app.features.randompicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Desktop installs the backing store; shared code never imports JVM storage. */
internal object RandomPickerPreferences {
    var load: (Int) -> String? = { null }
    var save: (Int, String) -> Unit = { _, _ -> }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(value: String?): PickerFilters = runCatching {
        if (value == null) return@runCatching PickerFilters()
        val root = json.parseToJsonElement(value).jsonObject
        var decoded = json.decodeFromJsonElement<PickerFilters>(root)
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        if (version < 3) {
            val types = (root["types"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty().toSet()
            decoded = decoded.copy(contentStyle = if ("anime" in types) "anime" else "",
                format = types.intersect(setOf("movie", "series")).singleOrNull().orEmpty())
        }
        decoded = decoded.copy(schemaVersion = 3,
            contentStyle = decoded.contentStyle.takeIf { it in setOf("", "live_action", "anime") }.orEmpty(),
            format = decoded.format.takeIf { it in setOf("", "movie", "series") }.orEmpty(),
            status = if (decoded.format == "movie") "" else decoded.status)
        if (version >= 2) {
            return@runCatching decoded.copy(
                minimumRating = decoded.minimumRating?.takeIf { it in 1..10 },
                decade = decoded.decade?.takeIf { it == -1 || it in 1900..9990 && it % 10 == 0 },
            )
        }
        fun strings(name: String) = (root[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val keys = strings("sources").filter { it.split('\n').size == 3 }
        val addons = keys.map { it.substringBefore('\n') }.distinct()
        decoded.copy(
            addonUrl = addons.singleOrNull(),
            catalogKey = keys.singleOrNull(),
            genre = strings("genres").singleOrNull()?.takeIf { it.isNotBlank() },
            minimumRating = null, decade = null,
        )
    }.getOrDefault(PickerFilters())
    fun encode(filters: PickerFilters): String = json.encodeToString(filters)
}

internal class RandomPickerViewModel(private val profileId: Int) : ViewModel() {
    private val state = MutableStateFlow(PickerState(filters = RandomPickerPreferences.decode(
        runCatching { RandomPickerPreferences.load(profileId) }.getOrNull(),
    )))
    val uiState = state.asStateFlow()
    private var pickingJob: Job? = null
    private var backgroundJob: Job? = null
    private var cleanupJob: Job? = null
    private var active = false
    private var presentation = 0L
    private var metadataProviders: List<com.nuvio.app.features.addons.AddonManifest?> = emptyList()
    private var enrichmentSettings: com.nuvio.app.features.tmdb.TmdbSettings? = null
    private var artworkPreparer: (suspend (PickerResult, PickerRequests) -> PickerResult)? = null
    private val generation = MutableStateFlow(0L)
    private val observedGenres = mutableMapOf<String, Set<String>>()
    private val requests = PickerRequests(viewModelScope)
    private val engine = RandomPickerEngine(
        pageLoader = { source, genre, skip ->
            requests.request("page:${source.key}:$genre:$skip") {
                fetchCatalogPage(source.manifestUrl, source.catalog.type, source.catalog.id,
                    genre = genre, skip = skip, maxItems = 2000, shareInFlight = false)
            } ?: error("Catalog request unavailable")
        },
        metaLoader = { preview ->
            requests.request("meta:${preview.type}:${preview.id}") {
                MetaDetailsRepository.fetchForShuffle(preview.type, preview.id, preview.pickerMeta())
            }
        },
        cardPreparer = { result ->
            requests.cancelUnusedData()
            artworkPreparer?.invoke(result, requests) ?: result
        },
        metadataVersion = { TmdbSettingsRepository.snapshot() to AddonRepository.uiState.value.addons.filter { it.isActive }.map { it.manifest } },
        isWatched = { meta ->
            if (com.nuvio.app.features.profiles.ProfileRepository.activeProfileId != profileId) false
            else pickerIsWatched(meta, WatchedRepository.uiState.value.watchedKeys, WatchedRepository.fullyWatchedSeriesKeys.value)
        },
        previewMayChange = { filters ->
            TmdbSettingsRepository.ensureLoaded()
            val settings = TmdbSettingsRepository.snapshot()
            val addonRatingCanChange = filters.minimumRating != null && AddonRepository.uiState.value.addons.any { addon ->
                addon.isActive && addon.manifest?.resources?.any { it.name == "meta" } == true
            }
            addonRatingCanChange || settings.enabled && (filters.genre != null || filters.decade != null || filters.status.isNotBlank() || filters.contentStyle.isNotEmpty())
        },
    )
    private val session = PickerSession(engine)
    init {
        WatchedRepository.ensureLoaded()
        AddonRepository.initialize()
        viewModelScope.launch {
            AddonRepository.uiState.collect { addons ->
                val sources = pickerSources(addons.addons)
                val loading = addons.addons.any { it.enabled && it.isRefreshing }
                val filters = if (loading) state.value.filters else normalized(state.value.filters, sources)
                val providers = addons.addons.filter { it.isActive }.map { it.manifest }
                if (sources != state.value.sources || providers != metadataProviders) { metadataProviders = providers; invalidate() }
                if (filters != state.value.filters) persist(filters)
                state.update { it.copy(
                    sources = sources, filters = filters,
                    genres = availableGenres(sources, filters),
                    phase = if (it.phase == PickerPhase.LoadingSources && !loading) PickerPhase.Idle else it.phase,
                ) }
                if (!loading) scheduleBackground()
            }
        }
    }

    private fun availableGenres(sources: List<PickerSource>, filters: PickerFilters): List<String> =
        pickerGenres(sources, filters, observedGenres)

    private fun normalized(filters: PickerFilters, sources: List<PickerSource>): PickerFilters {
        val pruned = filters.copy(status = if (filters.format == "movie") "" else filters.status).pruneSources(sources)
        val options = availableGenres(sources, pruned)
        return pruned.copy(genre = pruned.genre?.let { chosen -> options.firstOrNull { it.equals(chosen, true) } })
    }
    private fun persist(filters: PickerFilters) {
        val saved = runCatching { RandomPickerPreferences.save(profileId, RandomPickerPreferences.encode(filters)) }.isSuccess
        state.update { it.copy(persistenceFailed = !saved) }
    }

    fun update(requested: PickerFilters) {
        invalidate()
        val loading = state.value.phase == PickerPhase.LoadingSources
        val filters = if (loading) requested else normalized(requested, state.value.sources)
        persist(filters)
        state.update { it.copy(filters = filters, genres = availableGenres(it.sources, filters), phase = if (loading) PickerPhase.LoadingSources else PickerPhase.Idle,
            result = null, resultPopupOpen = false, failedSources = 0, metadataFailures = 0, timedOut = false) }
        scheduleBackground()
    }

    fun reset() = update(PickerFilters())

    fun setActive(visible: Boolean, prepare: suspend (PickerResult, PickerRequests) -> PickerResult) {
        artworkPreparer = prepare
        active = visible
        if (visible) {
            TmdbSettingsRepository.ensureLoaded()
            val currentSettings = TmdbSettingsRepository.snapshot()
            if (enrichmentSettings != currentSettings) { enrichmentSettings = currentSettings; invalidate() }
        }
        if (!visible) { cancel(); artworkPreparer = null }
        else scheduleBackground()
    }

    fun dismissResult() {
        cancel()
        state.update { it.copy(resultPopupOpen = false) }
        scheduleBackground()
    }

    fun stopSearch() {
        cancel()
    }

    private fun invalidate() {
        cancel()
        session.invalidate()
    }

    fun cancel() {
        generation.update { it + 1 }
        val oldPicking = pickingJob; val oldBackground = backgroundJob
        oldPicking?.cancel(); pickingJob = null
        oldBackground?.cancel(); backgroundJob = null
        val previous = cleanupJob
        cleanupJob = viewModelScope.launch { oldPicking?.join(); oldBackground?.join(); previous?.join(); requests.clear() }
        state.update { if (it.picking) it.copy(phase = PickerPhase.Idle) else it }
    }

    private fun scheduleBackground() {
        if (!active || state.value.picking || state.value.phase == PickerPhase.LoadingSources || backgroundJob?.isActive == true) return
        val snapshot = state.value
        if (snapshot.sources.none { it.selectedBy(snapshot.filters) }) return
        val ticket = generation.value
        backgroundJob = viewModelScope.launch {
            cleanupJob?.join()
            if (!active || generation.value != ticket || state.value.filters != snapshot.filters || state.value.sources != snapshot.sources) return@launch
            try { withContext(Dispatchers.Default) { session.refill(snapshot.filters, snapshot.sources) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Background preparation is best effort; the next click can retry. */ }
        }
    }
    fun pick() {
        val snapshot = state.value
        if (snapshot.picking || snapshot.phase == PickerPhase.LoadingSources || !snapshot.filters.isValid()) return
        val requestGeneration = generation.value
        val background = backgroundJob
        backgroundJob?.cancel(); backgroundJob = null
        state.update { it.copy(phase = PickerPhase.Sampling, failedSources = 0, metadataFailures = 0, timedOut = false) }
        pickingJob = viewModelScope.launch {
            try {
                background?.cancelAndJoin()
                cleanupJob?.join()
                val outcome = searchUntilResult(
                    attempt = { retry ->
                        withTimeoutOrNull(15_000) {
                            withContext(Dispatchers.Default) {
                                session.pick(snapshot.filters, snapshot.sources, phase = { phase ->
                                    state.update { if (generation.value == requestGeneration) it.copy(phase = phase) else it }
                                }, retry = retry)
                            }
                        } ?: PickerOutcome(timedOut = true)
                    },
                    onRetry = { failure ->
                        state.update { if (generation.value == requestGeneration) it.copy(
                            phase = PickerPhase.Retrying, timedOut = false,
                            failedSources = failure.failedSources, metadataFailures = failure.metadataFailures,
                        ) else it }
                    },
                )
                if (outcome.result != null && !session.displayed(outcome.result, snapshot.filters)) {
                    state.update { it.copy(phase = PickerPhase.Empty) }; return@launch
                }
                outcome.sourceGenres.forEach { (key, genres) -> observedGenres[key] = observedGenres[key].orEmpty() + genres }
                outcome.result?.let { result -> observedGenres[result.source.key] = observedGenres[result.source.key].orEmpty() + result.meta.genres }
                val displayed = outcome.copy(result = outcome.result?.copy(presentationId = ++presentation))
                state.update { it.withOutcome(displayed).copy(genres = availableGenres(it.sources, it.filters)) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.update { it.copy(phase = PickerPhase.Failed) } }
            finally { scheduleBackground() }
        }
    }
}

internal fun PickerState.withOutcome(outcome: PickerOutcome): PickerState = copy(
    result = outcome.result ?: result,
    resultPopupOpen = outcome.result != null || resultPopupOpen,
    phase = when {
        outcome.result != null -> PickerPhase.Idle
        outcome.timedOut || outcome.successfulPages == 0 && outcome.failedSources > 0 -> PickerPhase.Failed
        outcome.metadataAttempts > 0 && outcome.metadataFailures == outcome.metadataAttempts -> PickerPhase.Failed
        else -> PickerPhase.Empty
    },
    failedSources = outcome.failedSources,
    metadataFailures = outcome.metadataFailures,
    timedOut = outcome.timedOut,
)
