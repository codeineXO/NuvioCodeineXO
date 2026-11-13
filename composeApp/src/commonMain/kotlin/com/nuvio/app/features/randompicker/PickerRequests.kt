package com.nuvio.app.features.randompicker

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** One page-owned network limit. Cancelled consumers can hand warm requests to a foreground pick. */
internal class PickerRequests(
    private val scope: CoroutineScope, private val requestTimeoutMs: Long = 4_000,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private data class Entry(val work: Deferred<Any?>, var readers: Int = 0)
    private val lock = Mutex()
    private val permits = Semaphore(4)
    private val pending = mutableMapOf<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> request(key: String, block: suspend () -> T): T? {
        val entry = lock.withLock {
            pending[key]?.takeUnless { it.work.isCancelled } ?: Entry(scope.async(dispatcher, start = CoroutineStart.LAZY) {
                try { withTimeoutOrNull(requestTimeoutMs) { permits.withPermit { block() } } }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { null }
            }).also { pending[key] = it }
        }
        entry.work.invokeOnCompletion {
            scope.launch {
                lock.withLock { if (entry.readers == 0 && pending[key] === entry) pending.remove(key) }
            }
        }
        lock.withLock { entry.readers++ }
        try {
            return entry.work.await() as T?
        } finally {
            withContext(NonCancellable) {
                lock.withLock {
                    entry.readers--
                    if (entry.work.isCompleted && pending[key] === entry) pending.remove(key)
                }
            }
        }
    }

    suspend fun cancelUnusedArtwork() {
        cancelUnused { it.startsWith("art-meta:") }
    }

    suspend fun cancelUnusedData() {
        cancelUnused { !it.startsWith("art-meta:") }
    }

    private suspend fun cancelUnused(matches: (String) -> Boolean) {
        val jobs = lock.withLock {
            pending.filter { (key, value) -> matches(key) && value.readers == 0 }
                .also { entries -> entries.keys.forEach { pending.remove(it) } }.values.map { it.work }
        }
        jobs.forEach { it.cancel() }; jobs.joinAll()
    }

    suspend fun clear() {
        val jobs = lock.withLock { pending.values.map { it.work }.also { pending.clear() } }
        jobs.forEach { it.cancel() }; jobs.joinAll()
    }
}
