package com.nuvio.app.features.randompicker

import co.touchlab.kermit.Logger
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.supportsPagination
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.*
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
)

internal data class PickerBudget(val pages: Int = 12, val metadata: Int = 24, val selectionMs: Long = 12_000, val totalMs: Long = 15_000) {
    companion object { val Background = PickerBudget(8, 16) }
}

/** Caches contain only Shuffle projections, never full detail records. Each engine belongs to one profile/scope. */
internal class RandomPickerEngine(
    private val pageLoader: suspend (PickerSource, String?, Int?) -> CatalogPage,
    private val metaLoader: suspend (MetaPreview) -> MetaDetails?,
    private val random: Random = Random.Default,
    private val now: () -> Long = { WatchProgressClock.nowEpochMs() },
    private val cardPreparer: suspend (PickerResult) -> PickerResult = { it },
    private val previewMayChange: (PickerFilters) -> Boolean = { false },
    private val metadataVersion: () -> Any? = { Unit },
    private val isWatched: (MetaDetails) -> Boolean = { false },
) {
    private data class Entry<T>(val time: Long, val value: T)
    private data class PageKey(val source: String, val genre: String?, val offset: Int)
    private data class Cursor(var next: Int = 0, var window: Int = 4, var boundary: Int? = null, var first: Set<String>? = null)
    private val pages = linkedMapOf<PageKey, Entry<CatalogPage>>()
    private val metadata = linkedMapOf<String, Entry<MetaDetails>>()
    private val candidates = linkedMapOf<String, Entry<Pair<MetaPreview, PickerSource>>>()
    private val cursors = linkedMapOf<Pair<String, String?>, Cursor>()
    private val recent = ArrayDeque<Set<String>>()
    private val history = mutableSetOf<String>()
    private val mutex = Mutex()
    private var scopeKey: Triple<PickerFilters, List<PickerSource>, Any?>? = null
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

    suspend fun prepareCard(result: PickerResult, timeoutMs: Long = 3_000): PickerResult {
        val prepared = try { withTimeoutOrNull(timeoutMs) { cardPreparer(result) } }
            catch (error: CancellationException) { throw error } catch (_: Exception) { null }
        return prepared ?: result.copy(artwork = null)
    }

    suspend fun beginRetry(attempt: Int) = mutex.withLock {
        candidates.clear()
        pages.entries.removeAll { it.value.value.rawItemCount == 0 }
        // Revisit sources periodically rather than retaining an empty/stale sample for five minutes.
        if (attempt % 3 == 0) { pages.clear(); metadata.clear(); cursors.clear() }
    }

    suspend fun pick(
        filters: PickerFilters, sources: List<PickerSource>, phase: (PickerPhase) -> Unit = {},
        budget: PickerBudget = PickerBudget(), excluded: Set<String> = emptySet(),
        recordSelection: Boolean = true, explore: Boolean = false,
    ): PickerOutcome = mutex.withLock {
        val key = Triple(filters, sources, metadataVersion())
        if (scopeKey != key) {
            pages.clear(); metadata.clear(); candidates.clear(); cursors.clear(); scopeKey = key
        }
        val start = now()
        var outcome = PickerOutcome()
        var pageRequests = 0; var metaChecks = 0; var cacheHits = 0; var pageCacheHits = 0
        var metadataMs = 0L; var artworkMs = 0L
        val genres = mutableMapOf<String, MutableSet<String>>()
        val eligible = sources.filter { it.selectedBy(filters) }
        if (!filters.isValid() || eligible.isEmpty()) return@withLock outcome
        candidates.entries.removeAll { now() - it.value.time >= 300_000 }
        val checked = mutableSetOf<String>()
        var fallback: PickerResult? = null
        var selected: PickerResult? = null
        val recentKeys = recent.flatten().toSet()
        fun allowed(result: PickerResult): Boolean = canDisplay(result, filters) && aliases(result).none { it in excluded }

        suspend fun examine(): PickerResult? {
            // Shuffling precedes any requests; response speed never chooses the winner.
            for ((preview, source) in candidates.values.map { it.value }.shuffled(random)) {
                currentCoroutineContext().ensureActive()
                val id = "${preview.type}:${preview.id}"
                if (!checked.add(id)) continue
                val base = preview.pickerMeta()
                val originalAliases = pickerAliases(base)
                if (originalAliases.any { it in excluded || filters.excludePrevious && it in history }) continue
                if (previewDefinitelyRejects(filters, base) && !previewMayChange(filters)) continue
                val cached = metadata[id]?.takeIf { now() - it.time < 300_000 }?.value
                var meta = cached ?: base
                var loaded = cached != null
                if (cached != null) cacheHits++
                val needsCheck = !filters.matches(base) || previewMayChange(filters)
                if (!loaded && needsCheck) {
                    if (metaChecks >= budget.metadata) continue
                    phase(PickerPhase.Enriching)
                    val began = now(); metaChecks++
                    val fetched = try { withTimeoutOrNull(4_000) { metaLoader(preview) } }
                        catch (error: CancellationException) { throw error } catch (_: Exception) { null }
                    metadataMs += now() - began
                    if (fetched == null) outcome = outcome.copy(metadataFailures = outcome.metadataFailures + 1)
                    else { meta = mergeShuffleMeta(fetched, base); metadata[id] = Entry(now(), meta); loaded = true }
                }
                if (!filters.matches(meta)) continue
                var result = PickerResult(preview, meta, source)
                if (!allowed(result)) continue
                // Avoid doing card work for a recent title while fresh alternatives remain.
                val isRecent = aliases(result).any { it in recentKeys }
                if (isRecent && fallback != null) continue
                if (!loaded) {
                    if (metaChecks >= budget.metadata) continue
                    phase(PickerPhase.Enriching)
                    val began = now(); metaChecks++
                    val fetched = try { withTimeoutOrNull(4_000) { metaLoader(preview) } }
                        catch (error: CancellationException) { throw error } catch (_: Exception) { null }
                    metadataMs += now() - began
                    if (fetched == null) outcome = outcome.copy(metadataFailures = outcome.metadataFailures + 1)
                    else { meta = mergeShuffleMeta(fetched, base); metadata[id] = Entry(now(), meta) }
                    result = result.copy(meta = meta)
                }
                if (!allowed(result)) continue
                if (isRecent) { fallback = result; continue }
                return result
            }
            return null
        }

        val selectionDone = withTimeoutOrNull(budget.selectionMs) {
            if (!explore) selected = examine()
            val shuffled = eligible.shuffled(random)
            val offset = rotation++ % shuffled.size
            val ordered = shuffled.drop(offset) + shuffled.take(offset)
            val requested = mutableSetOf<PageKey>()
            val failed = mutableSetOf<String>()
            var wave = 0
            while (selected == null && pageRequests < budget.pages && metaChecks < budget.metadata && wave < 24) {
                phase(PickerPhase.Sampling)
                val jobs = buildList {
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
                if (jobs.isEmpty()) continue
                pageRequests += jobs.size
                coroutineScope {
                    val work = jobs.map { (source, key, _) ->
                        val cached = pages[key]?.takeIf { now() - it.time < 300_000 }?.value
                        if (cached != null) pageCacheHits++
                        async { cached ?: try {
                            withTimeoutOrNull(4_000) { pageLoader(source, key.genre, key.offset.takeIf { it > 0 }) }
                        } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
                    } }
                    try {
                        for (index in jobs.indices) {
                            val (source, key, cursor) = jobs[index]
                            val page = work[index].await()
                            if (page == null) { failed.add(source.key); continue }
                            outcome = outcome.copy(successfulPages = outcome.successfulPages + 1)
                            val time = pages[key]?.takeIf { now() - it.time < 300_000 }?.time ?: now()
                            pages[key] = Entry(time, page.copy(items = page.items.take(2000)))
                            val fingerprint = page.items.map { "${it.type}:${it.id}" }.toSet()
                            if (page.rawItemCount == 0 || key.offset > 0 && fingerprint == cursor.first) {
                                if (key.offset > 0) cursor.boundary = minOf(cursor.boundary ?: Int.MAX_VALUE, key.offset)
                            } else {
                                if (key.offset == 0) cursor.first = fingerprint
                                cursor.next = maxOf(cursor.next, page.nextSkip ?: key.offset + page.rawItemCount)
                                cursor.window = (cursor.window + 4).coerceAtMost(1000)
                            }
                            page.items.forEach { item ->
                                genres.getOrPut(source.key) { mutableSetOf() }.addAll(item.genres)
                                val id = "${item.type}:${item.id}"
                                val old = candidates[id]
                                val combined = if (old == null) item else item.copy(
                                    imdbRating = item.imdbRating ?: old.value.first.imdbRating,
                                    genres = (item.genres + old.value.first.genres).distinctBy { it.lowercase() },
                                )
                                candidates[id] = Entry(time, combined to source)
                            }
                            while (candidates.size > 2000) candidates.remove(candidates.keys.first())
                            selected = examine()
                            if (selected != null) break
                        }
                    } finally { work.forEach { it.cancel() } }
                }
                outcome = outcome.copy(failedSources = failed.size)
            }
            selected = selected ?: fallback
            true
        }
        while (pages.size > 48) pages.remove(pages.keys.first())
        while (metadata.size > 160) metadata.remove(metadata.keys.first())
        while (cursors.size > 256) cursors.remove(cursors.keys.first())
        val result = selected ?: fallback
        if (result != null) {
            phase(PickerPhase.Preparing)
            val began = now()
            val remaining = (budget.totalMs - (now() - start)).coerceAtLeast(1).coerceAtMost(3_000)
            val settled = prepareCard(result, remaining)
            artworkMs = now() - began
            if (allowed(settled)) {
                currentCoroutineContext().ensureActive()
                outcome = outcome.copy(result = settled)
                if (recordSelection) recordDisplayed(settled)
            }
        }
        log.d { "totalMs=${now() - start} samplingMs=${(now() - start - metadataMs - artworkMs).coerceAtLeast(0)} metadataMs=$metadataMs artworkMs=$artworkMs pages=$pageRequests metadataChecks=$metaChecks metadataCacheHits=$cacheHits pageCacheHits=$pageCacheHits" }
        outcome.copy(metadataAttempts = metaChecks, pageRequests = pageRequests, timedOut = selectionDone == null && outcome.result == null,
            sourceGenres = genres.mapValues { it.value.toSet() })
    }

    private fun aliases(result: PickerResult) = pickerAliases(result.meta) + pickerAliases(result.preview.pickerMeta())
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
