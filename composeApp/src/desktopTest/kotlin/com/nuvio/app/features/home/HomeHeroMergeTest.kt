package com.nuvio.app.features.home

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.mdblist.MdbListSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeHeroMergeTest {
    private val item = MetaPreview(id = "tt123", type = "movie", name = "Example", imdbRating = "8.4")
    private val ratings = listOf(MetaExternalRating("imdb", 8.8))

    @Test
    fun ratingsRespectOptOutAndImdbFallback() {
        val enriched = item.copy(externalRatings = ratings)
        assertEquals(ratings, enriched.heroRatings(true, true).external)
        assertNull(enriched.heroRatings(true, true).imdb)
        assertEquals("8.4", enriched.heroRatings(true, false).imdb)
        assertTrue(enriched.heroRatings(true, false).external.isEmpty())
        assertNull(enriched.heroRatings(false, false).imdb)
        assertNull(item.copy(imdbRating = "0").heroRatings(true, false).imdb)
        assertNull(item.copy(imdbRating = "invalid").heroRatings(true, false).imdb)
    }

    @Test
    fun lateResultsFromThePreviousProfileCannotPopulateTheNewScope() = runBlocking {
        withTimeout(10_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val previous = HomeHeroMetaRepository(
                detailsLoader = {
                    entered.complete(Unit)
                    release.await()
                    MetaDetails(it.id, it.type, it.name, ageRating = "R")
                },
                ratingsLoader = { _, _, _ -> ratings },
            )
            val oldLoad = launch { previous.load(listOf(item), MdbListSettings(enabled = true, apiKey = "old")) }
            entered.await()
            val current = HomeHeroMetaRepository(
                detailsLoader = { MetaDetails(it.id, it.type, it.name, ageRating = "PG") },
                ratingsLoader = { _, _, _ -> error("Disabled hero ratings must not be requested") },
            )
            assertTrue(current.metas.value.isEmpty())
            current.load(listOf(item), null)
            release.complete(Unit)
            oldLoad.join()
            val currentMeta = current.metas.value.getValue(item.stableKey())
            assertEquals("PG", currentMeta.details?.ageRating)
            assertTrue(currentMeta.ratings.isEmpty())
            assertEquals(ratings, previous.metas.value.getValue(item.stableKey()).ratings)
        }
    }

    @Test
    fun cancelledLoadsDoNotPublishLateDetails() = runBlocking {
        withTimeout(10_000) {
            val entered = CompletableDeferred<Unit>()
            val repository = HomeHeroMetaRepository(detailsLoader = {
                entered.complete(Unit)
                awaitCancellation()
            })
            val loading = launch { repository.load(listOf(item), null) }
            entered.await()
            loading.cancelAndJoin()
            assertTrue(loading.isCancelled)
            assertNull(repository.metas.value[item.stableKey()]?.details)
        }
    }

    @Test
    fun detailsRequestsStayBoundedAndInactiveHeroEntriesArePruned() = runBlocking {
        withTimeout(10_000) {
            val active = AtomicInteger()
            val maximum = AtomicInteger()
            val calls = AtomicInteger()
            val repository = HomeHeroMetaRepository(detailsLoader = {
                val concurrent = active.incrementAndGet()
                maximum.accumulateAndGet(concurrent, ::maxOf)
                try {
                    calls.incrementAndGet()
                    delay(10)
                    MetaDetails(it.id, it.type, it.name)
                } finally {
                    active.decrementAndGet()
                }
            })
            val items = (1..9).map { item.copy(id = "tt$it") }
            repository.load(items, null)
            assertEquals(9, calls.get())
            assertTrue(maximum.get() in 1..4)
            repository.load(listOf(items.first()), null)
            assertEquals(setOf(items.first().stableKey()), repository.metas.value.keys)
        }
    }
}
