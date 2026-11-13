package com.nuvio.app.features.randompicker

import co.touchlab.kermit.Logger
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.supportsPagination
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

internal data class PickerOutcome(
    val result: PickerResult? = null,
    val failedSources: Int = 0,
    val metadataFailures: Int = 0,
    val metadataAttempts: Int = 0,
    val successfulPages: Int = 0,
    val sourceGenres: Map<String, Set<String>> = emptyMap(),
    val timedOut: Boolean = false,
    val pageRequests: Int = 0,
    val noNewTitles: Boolean = false,
)

internal data class PickerBudget(val pages: Int = 12, val metadata: Int = 24, val selectionMs: Long = 12_000)

/** Caches contain only Shuffle projections, never full detail records. Each engine belongs to one profile/scope. */
internal class RandomPickerEngine(
    private val pageLoader: suspend (PickerSource, String?, Int?) -> CatalogPage,
    private val metaLoader: suspend (MetaPreview) -> MetaDetails?,
    private val random: Random = Random.Default,
    private val now: () -> Long = { WatchProgressClock.nowEpochMs() },
    private val previewMayChange: (PickerFilters) -> Boolean = { false },
    private val metadataVersion: () -> Any? = { Unit },
    private val isWatched: (MetaDetails) -> Boolean = { false },
) {
    private data class Entry<T>(val time: Long, val value: T)
    private data class PageKey(val source: String, val genre: String?, val offset: Int)
    private data class Cursor(
        var next: Int = 0,
        var window: Int = 4,
        var boundary: Int? = null,
        var first: Set<String>? = null,
        var last: Set<String>? = null,
        var exhausted: Boolean = false,
        val pending: MutableSet<String> = mutableSetOf(),
    )
    private data class Candidate(val preview: MetaPreview, val source: PickerSource, val id: String = "${preview.type}:${preview.id}")
    private val pages = linkedMapOf<PageKey, Entry<CatalogPage>>()
    private val metadata = linkedMapOf<String, Entry<MetaDetails>>()
    private val candidates = linkedMapOf<String, Entry<Candidate>>()
    private val cursors = linkedMapOf<Pair<String, String?>, Cursor>()
    private val recent = ArrayDeque<Set<String>>()
    private val history = mutableSetOf<String>()
    private val mutex = Mutex()
    private var scopeKey: Triple<PickerFilters, List<PickerSource>, Any?>? = null
    private var scopeStarted = 0L
    private var rotation = 0
    private val log = Logger.withTag("ShuffleTiming")

    fun canDisplay(result: PickerResult, filters: PickerFilters): Boolean = filters.matches(result.meta) &&
        (!filters.excludeWatched || !isWatched(result.meta) && !isWatched(result.preview.pickerMeta())) &&
        (!filters.excludePrevious || aliases(result).none { it in history })

    fun recordDisplayed(result: PickerResult) {
        val keys = aliases(result)
        history.addAll(keys); recent.addLast(keys)
        while (recent.size > 20) recent.removeFirst()
    }

    fun isRecent(result: PickerResult): Boolean = aliases(result).any { key -> recent.any { key in it } }

    suspend fun beginRetry(attempt: Int) = mutex.withLock {
        candidates.clear()
        pages.entries.removeAll { it.value.value.rawItemCount == 0 }
        // Refresh the final sample while retaining the pagination progress from this search.
        if (attempt == 2) pages.clear()
    }

    suspend fun pick(
        filters: PickerFilters, sources: List<PickerSource>, phase: (PickerPhase) -> Unit = {},
        budget: PickerBudget = PickerBudget(), excluded: Set<String> = emptySet(),
        recordSelection: Boolean = true, explore: Boolean = false,
    ): PickerOutcome = mutex.withLock {
        val start = now()
        val key = Triple(filters, sources, metadataVersion())
        if (scopeKey != key || filters.excludePrevious && start - scopeStarted >= 300_000) {
            pages.clear(); metadata.clear(); candidates.clear(); cursors.clear()
            scopeKey = key
            scopeStarted = start
        }
        var outcome = PickerOutcome()
        var pageRequests = 0; var metaChecks = 0; var cacheHits = 0; var pageCacheHits = 0
        val failed = mutableSetOf<String>()
        val genres = mutableMapOf<String, MutableSet<String>>()
        val eligible = sources.filter { it.selectedBy(filters) }
        if (!filters.isValid() || eligible.isEmpty()) return@withLock outcome
        val sequentialCatalogs = if (filters.excludePrevious) eligible.flatMap { source ->
            val property = source.catalog.extra.firstOrNull { it.name.equals("genre", true) }
            val options = property?.options.orEmpty()
            val selectedGenre = options.firstOrNull { it.equals(filters.genre, true) }
            val genres = when {
                selectedGenre != null -> listOf(selectedGenre)
                property?.isRequired == true -> options
                else -> listOf(null)
            }
            genres.map { genre -> Triple(source, genre, cursors.getOrPut(source.key to genre) { Cursor() }) }
        }.shuffled(random).toMutableList() else mutableListOf()
        if (!filters.excludePrevious) candidates.entries.removeAll { now() - it.value.time >= 300_000 }
        val checked = mutableSetOf<String>()
        val queued = ArrayDeque<String>()
        var fallback: PickerResult? = null
        var selected: PickerResult? = null
        val recentKeys = recent.flatten().toSet()
        val previewCanChange = previewMayChange(filters)
        fun allowed(result: PickerResult): Boolean = canDisplay(result, filters) && aliases(result).none { it in excluded }

        fun fresh(result: PickerResult): Boolean {
            if (!allowed(result)) return false
            if (aliases(result).none { it in recentKeys }) return true
            if (fallback == null) fallback = result
            return false
        }

        fun retire(id: String) {
            if (!filters.excludePrevious) return
            // A page stays pending across picks until each of its titles has a conclusive result.
            for ((_, _, cursor) in sequentialCatalogs) cursor.pending.remove(id)
            candidates.remove(id)
        }

        fun consider(candidate: Candidate, meta: MetaDetails, verified: Boolean) {
            val result = PickerResult(candidate.preview, meta, candidate.source)
            if (fresh(result)) selected = result
            else if (verified && !canDisplay(result, filters)) retire(candidate.id)
        }

        fun CoroutineScope.startMetadata(work: MutableList<Deferred<() -> Unit>>, limit: Int) {
            while (selected == null && queued.isNotEmpty() && work.size < limit) {
                ensureActive()
                val id = queued.removeFirst()
                val candidate = candidates[id]?.value ?: continue
                val (preview, source) = candidate
                if (id in checked) continue
                val base = preview.pickerMeta()
                val aliases = pickerAliases(base)
                if (filters.excludePrevious && aliases.any { it in history } ||
                    !previewCanChange && previewDefinitelyRejects(filters, base)) {
                    retire(id)
                    continue
                }
                if (aliases.any { it in excluded }) continue
                val cached = metadata[id]?.takeIf { now() - it.time < 300_000 }?.value
                if (cached != null) {
                    checked.add(id)
                    cacheHits++
                    consider(candidate, cached, verified = true)
                    continue
                }
                val result = PickerResult(preview, base, source)
                if (!previewCanChange && filters.matches(base) &&
                    (!allowed(result) || fallback != null && aliases(result).any { it in recentKeys })) {
                    if (!canDisplay(result, filters)) retire(id)
                    continue
                }
                if (metaChecks >= budget.metadata) continue
                checked.add(id)
                phase(PickerPhase.Enriching)
                metaChecks++
                work += async {
                    val fetched = loadPickerData { metaLoader(preview) }
                    val complete: () -> Unit = {
                        if (fetched == null) {
                            outcome = outcome.copy(metadataFailures = outcome.metadataFailures + 1)
                            consider(candidate, base, verified = false)
                        } else {
                            val merged = mergeShuffleMeta(fetched, base)
                            metadata[id] = Entry(now(), merged)
                            consider(candidate, merged, verified = true)
                        }
                    }
                    complete
                }
            }
        }

        val selectionDone = withTimeoutOrNull(budget.selectionMs) {
            if (!explore || filters.excludePrevious) queued.addAll(candidates.keys.shuffled(random))
            val shuffled = eligible.shuffled(random)
            val offset = rotation++ % shuffled.size
            val ordered = shuffled.drop(offset) + shuffled.take(offset)
            val requested = mutableSetOf<PageKey>()
            var wave = 0
            while (selected == null && wave < 24) {
                val jobs = if (pageRequests >= budget.pages || metaChecks >= budget.metadata) emptyList()
                else if (filters.excludePrevious) {
                    val ready = sequentialCatalogs.asSequence().filter { (source, genre, cursor) ->
                        !cursor.exhausted && cursor.pending.all { it in checked } &&
                            PageKey(source.key, genre, cursor.next) !in requested
                    }.take(minOf(4, budget.pages - pageRequests)).toList()
                    sequentialCatalogs.removeAll(ready)
                    sequentialCatalogs.addAll(ready)
                    ready.map { (source, genre, cursor) ->
                        val key = PageKey(source.key, genre, cursor.next)
                        requested.add(key)
                        Triple(source, key, cursor)
                    }
                } else buildList {
                    var attempts = 0
                    while (size < minOf(4, budget.pages - pageRequests) && attempts < ordered.size * 8 + 8) {
                        val source = ordered[(wave * 4 + attempts) % ordered.size]
                        val slot = attempts++
                        if (source.key in failed) continue
                        val property = source.catalog.extra.firstOrNull { it.name.equals("genre", true) }
                        val options = property?.options.orEmpty()
                        val genre = options.firstOrNull { it.equals(filters.genre, true) }
                            ?: if (property?.isRequired == true) options.getOrNull((wave + slot) % options.size.coerceAtLeast(1)) else null
                        val cursor = cursors.getOrPut(source.key to genre) { Cursor() }
                        val skip = when {
                            !source.catalog.supportsPagination() -> 0
                            cursor.next == 0 && slot == 0 -> 0
                            slot % 2 == 0 -> cursor.next + slot / 2 * 100
                            else -> random.nextInt(cursor.window.coerceAtLeast(1)) * 100
                        }.let { value -> cursor.boundary?.let { value.coerceAtMost((it - 1).coerceAtLeast(0)) } ?: value }
                        val key = PageKey(source.key, genre, skip)
                        if (requested.add(key)) add(Triple(source, key, cursor))
                    }
                }
                wave++
                if (jobs.isEmpty() && queued.isEmpty()) {
                    if (filters.excludePrevious || pageRequests >= budget.pages || metaChecks >= budget.metadata) break
                    continue
                }
                coroutineScope {
                    val pageWork = jobs.map { job ->
                        val (source, key, _) = job
                        val cached = pages[key]?.takeIf { now() - it.time < 300_000 }?.value
                        if (cached != null) pageCacheHits++
                        async(start = CoroutineStart.LAZY) {
                            val page = cached ?: loadPickerData {
                                pageLoader(source, key.genre, key.offset.takeIf { it > 0 })
                            }
                            val complete: () -> Unit = pageReady@{
                                val (source, key, cursor) = job
                                if (page == null) {
                                    failed.add(source.key)
                                    return@pageReady
                                }
                                outcome = outcome.copy(successfulPages = outcome.successfulPages + 1)
                                val time = pages[key]?.takeIf { now() - it.time < 300_000 }?.time ?: now()
                                if (!filters.excludePrevious) pages[key] = Entry(time, page.copy(items = page.items.take(2000)))
                                val fingerprint = page.items.map { "${it.type}:${it.id}" }.toSet()
                                val repeatedFirstPage = key.offset > 0 && fingerprint.isNotEmpty() && fingerprint == cursor.first
                                if (filters.excludePrevious) {
                                    if (key.offset == 0) cursor.first = fingerprint
                                    val next = page.nextSkip ?: (key.offset + page.rawItemCount)
                                    cursor.exhausted = !source.catalog.supportsPagination() || page.rawItemCount == 0 ||
                                        repeatedFirstPage || fingerprint.isNotEmpty() && fingerprint == cursor.last ||
                                        next <= key.offset
                                    cursor.last = fingerprint
                                    if (!cursor.exhausted) cursor.next = next
                                } else if (page.rawItemCount == 0 || repeatedFirstPage) {
                                    if (key.offset > 0) cursor.boundary = minOf(cursor.boundary ?: Int.MAX_VALUE, key.offset)
                                } else {
                                    if (key.offset == 0) cursor.first = fingerprint
                                    cursor.next = maxOf(cursor.next, page.nextSkip ?: key.offset + page.rawItemCount)
                                    cursor.window = (cursor.window + 4).coerceAtMost(1000)
                                }
                                val incoming = page.items.shuffled(random).map { item ->
                                    genres.getOrPut(source.key) { mutableSetOf() }.addAll(item.genres)
                                    val id = "${item.type}:${item.id}"
                                    val old = candidates[id]
                                    val combined = if (old == null) item else item.copy(
                                        imdbRating = item.imdbRating ?: old.value.preview.imdbRating,
                                        genres = (item.genres + old.value.preview.genres).distinctBy { it.lowercase() },
                                    )
                                    val candidate = Candidate(combined, source)
                                    candidates[id] = Entry(time, candidate)
                                    if (filters.excludePrevious) cursor.pending.add(id)
                                    id
                                }
                                if (!filters.excludePrevious) {
                                    while (candidates.size > 2000) candidates.remove(candidates.keys.first())
                                }
                                queued.addAll(0, incoming)
                            }
                            complete
                        }
                    }.toMutableList()
                    val metadataWork = mutableListOf<Deferred<() -> Unit>>()
                    try {
                        startMetadata(metadataWork, limit = if (pageWork.isEmpty()) 4 else 3)
                        if (selected == null && pageWork.isNotEmpty()) {
                            if (metadataWork.isEmpty()) phase(PickerPhase.Sampling)
                            pageRequests += pageWork.size
                            pageWork.forEach { it.start() }
                        }
                        while (selected == null) {
                            startMetadata(metadataWork, limit = if (pageWork.isEmpty()) 4 else 3)
                            if (pageWork.isEmpty() && metadataWork.isEmpty()) break
                            takeReady(pageWork, metadataWork).invoke()
                        }
                    } finally {
                        pageWork.forEach { it.cancel() }
                        metadataWork.forEach { it.cancel() }
                    }
                }
            }
            selected = selected ?: fallback
            true
        }
        while (pages.size > 48) pages.remove(pages.keys.first())
        while (metadata.size > 160) metadata.remove(metadata.keys.first())
        val noNewTitles = selected == null && fallback == null && selectionDone != null && filters.excludePrevious &&
            sequentialCatalogs.all { it.third.exhausted && it.third.pending.isEmpty() }
        if (!filters.excludePrevious) while (cursors.size > 256) cursors.remove(cursors.keys.first())
        val result = selected ?: fallback
        if (result != null && allowed(result)) {
            currentCoroutineContext().ensureActive()
            outcome = outcome.copy(result = result)
            if (recordSelection) recordDisplayed(result)
        }
        log.d { "totalMs=${now() - start} pages=$pageRequests metadataChecks=$metaChecks metadataCacheHits=$cacheHits pageCacheHits=$pageCacheHits" }
        outcome.copy(failedSources = failed.size, metadataAttempts = metaChecks, pageRequests = pageRequests,
            timedOut = selectionDone == null && outcome.result == null,
            sourceGenres = genres.mapValues { it.value.toSet() }, noNewTitles = noNewTitles)
    }

    private fun aliases(result: PickerResult) = pickerAliases(result.meta) + pickerAliases(result.preview.pickerMeta())
}

private suspend fun <T> takeReady(first: MutableList<Deferred<T>>, second: MutableList<Deferred<T>>): T {
    val (jobs, job, value) = select {
        for (job in first) job.onAwait { Triple(first, job, it) }
        for (job in second) job.onAwait { Triple(second, job, it) }
    }
    jobs.remove(job)
    return value
}

private suspend fun <T> loadPickerData(load: suspend () -> T?): T? = try {
    withTimeoutOrNull(4_000) { load() }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

internal fun mergeShuffleMeta(meta: MetaDetails, base: MetaDetails): MetaDetails = meta.copy(
    poster = meta.poster ?: base.poster, background = meta.background ?: base.background, logo = meta.logo ?: base.logo,
    description = meta.description ?: base.description, releaseInfo = meta.releaseInfo ?: base.releaseInfo,
    genres = meta.genres.ifEmpty { base.genres }, imdbRating = meta.imdbRating ?: base.imdbRating,
    imdbScore = meta.imdbScore ?: base.imdbScore, tmdbScore = meta.tmdbScore ?: base.tmdbScore,
    status = meta.status ?: base.status,
    runtime = meta.runtime ?: base.runtime, ageRating = meta.ageRating ?: base.ageRating,
    lastAirDate = meta.lastAirDate ?: base.lastAirDate, language = meta.language ?: base.language,
    seasonCount = meta.seasonCount ?: base.seasonCount,
)

/** Only fields whose precedence cannot be changed by enrichment are safe early rejections. */
internal fun previewDefinitelyRejects(filters: PickerFilters, meta: MetaDetails): Boolean =
    filters.minimumRating != null && meta.pickerRating()?.let { it < filters.minimumRating } == true
