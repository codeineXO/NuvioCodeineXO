package com.nuvio.app.features.randompicker

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Owner serializes foreground/background jobs; preparing a title never records it as viewed. */
internal class PickerSession(
    private val engine: RandomPickerEngine,
    private val now: () -> Long = { WatchProgressClock.nowEpochMs() },
) {
    private data class Prepared(val time: Long, val result: PickerResult)
    private val queue = mutableListOf<Prepared>()
    private val queueLock = SynchronizedObject()
    private var version = 0L
    private var scopeKey: Pair<PickerFilters, List<PickerSource>>? = null
    private val log = Logger.withTag("ShuffleTiming")

    fun invalidate() = synchronized(queueLock) { queue.clear(); scopeKey = null; version++ }

    private fun sync(filters: PickerFilters, sources: List<PickerSource>): Long = synchronized(queueLock) {
        val key = filters to sources
        if (scopeKey != key) { queue.clear(); scopeKey = key; version++ }
        queue.removeAll { now() - it.time >= 300_000 || !engine.canDisplay(it.result, filters) ||
            sources.none { source -> source.key == it.result.source.key && source.selectedBy(filters) } }
        version
    }

    suspend fun pick(filters: PickerFilters, sources: List<PickerSource>, phase: (PickerPhase) -> Unit): PickerOutcome =
        pick(filters, sources, phase, retry = 0)

    suspend fun pick(filters: PickerFilters, sources: List<PickerSource>, phase: (PickerPhase) -> Unit, retry: Int): PickerOutcome {
        sync(filters, sources)
        val entry = synchronized(queueLock) {
            (queue.firstOrNull { !engine.isRecent(it.result) } ?: queue.firstOrNull())?.also { queue.remove(it) }
        }
        if (entry != null) {
            log.d { "queueHit=true remaining=${synchronized(queueLock) { queue.size }}" }
            return PickerOutcome(result = entry.result)
        }
        if (retry > 0 && !filters.excludePrevious) engine.beginRetry(retry)
        log.d { "queueHit=false" }
        return engine.pick(filters, sources, phase, recordSelection = false, explore = retry > 0)
    }

    fun displayed(result: PickerResult, filters: PickerFilters): Boolean {
        if (!engine.canDisplay(result, filters)) return false
        engine.recordDisplayed(result)
        return true
    }

    suspend fun refill(filters: PickerFilters, sources: List<PickerSource>) {
        val ticket = sync(filters, sources)
        var pages = 0; var metadata = 0
        val start = now()
        withTimeoutOrNull(15_000) {
            while (synchronized(queueLock) { version == ticket && queue.size < 3 } && pages < 8 && metadata < 16) {
                val remaining = (15_000 - (now() - start)).coerceAtLeast(1)
                val excluded = synchronized(queueLock) { queue.flatMap { pickerAliases(it.result.meta) + pickerAliases(it.result.preview.pickerMeta()) }.toSet() }
                val outcome = engine.pick(filters, sources,
                    budget = PickerBudget(8 - pages, 16 - metadata, minOf(12_000, remaining)),
                    excluded = excluded, recordSelection = false)
                currentCoroutineContext().ensureActive()
                pages += outcome.pageRequests; metadata += outcome.metadataAttempts
                val result = outcome.result ?: break
                synchronized(queueLock) { if (version == ticket) queue.add(Prepared(now(), result)) }
            }
        }
        log.d { "backgroundMs=${now() - start} prepared=${synchronized(queueLock) { queue.size }} pages=$pages metadataChecks=$metadata" }
    }
}
