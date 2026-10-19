package com.nuvio.app.features.player.seekpreview

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BoundedSheetCacheTest {
    @Test
    fun evictsLeastRecentlyUsedSheetWhenByteBudgetIsFull() = runBlocking {
        val loads = mutableListOf<String>()
        val cache = BoundedSheetCache(maxDecodedBytes = 800) { url ->
            loads += url
            ImageBitmap(10, 10)
        }
        cache.get("a")
        cache.get("b")
        cache.get("a")
        cache.get("c")
        cache.get("a")
        cache.get("b")
        assertEquals(listOf("a", "b", "c", "b"), loads)
    }

    @Test
    fun sheetCountLimitStillApplies() = runBlocking {
        var loads = 0
        val cache = BoundedSheetCache(maxDecodedSheets = 1) { loads++; ImageBitmap(1, 1) }
        cache.get("a")
        cache.get("b")
        cache.get("a")
        assertEquals(3, loads)
    }

    @Test
    fun oversizedSheetsAreReturnedWithoutEvictingUsefulCachedSheets() = runBlocking {
        val loads = mutableListOf<String>()
        val cache = BoundedSheetCache(maxDecodedBytes = 400) { url ->
            loads += url
            if (url == "large") ImageBitmap(20, 20) else ImageBitmap(10, 10)
        }
        val small = cache.get("small")
        assertEquals(20, cache.get("large")?.width)
        assertSame(small, cache.get("small"))
        cache.get("large")
        assertEquals(listOf("small", "large", "large"), loads)
    }

    @Test
    fun concurrentRequestsShareOneLoad() = runBlocking {
        val loaded = CompletableDeferred<ImageBitmap?>()
        var loads = 0
        val cache = BoundedSheetCache { loads++; loaded.await() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { cache.get("a") }
        val second = async(start = CoroutineStart.UNDISPATCHED) { cache.get("a") }
        val bitmap = ImageBitmap(1, 1)
        loaded.complete(bitmap)
        assertSame(bitmap, first.await())
        assertSame(bitmap, second.await())
        assertEquals(1, loads)
    }

    @Test
    fun clearPreventsOldLoadFromReplacingNewCachedResult() = runBlocking {
        val oldLoad = CompletableDeferred<ImageBitmap?>()
        val fresh = ImageBitmap(1, 1)
        var loads = 0
        val cache = BoundedSheetCache { if (++loads == 1) oldLoad.await() else fresh }
        val old = async(start = CoroutineStart.UNDISPATCHED) { cache.get("a") }
        cache.clear()
        assertSame(fresh, cache.get("a"))
        oldLoad.complete(ImageBitmap(2, 2))
        assertEquals(2, old.await()?.width)
        assertSame(fresh, cache.get("a"))
        assertEquals(2, loads)
    }

    @Test
    fun cancelledOwnerReleasesWaitersAndAllowsRetry() = runBlocking {
        val hold = CompletableDeferred<Unit>()
        var loads = 0
        val cache = BoundedSheetCache {
            if (++loads == 1) hold.await()
            ImageBitmap(1, 1)
        }
        val owner = async(start = CoroutineStart.UNDISPATCHED) { cache.get("a") }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { cache.get("a") }
        owner.cancel()
        owner.join()
        try {
            waiter.await()
            error("waiter must observe cancellation")
        } catch (_: CancellationException) { }
        assertEquals(1, cache.get("a")?.width)
        assertEquals(2, loads)
    }

    @Test
    fun failedLoadCanBeRetried() = runBlocking {
        var loads = 0
        val cache = BoundedSheetCache { loads++; null }
        assertNull(cache.get("a"))
        assertNull(cache.get("a"))
        assertEquals(2, loads)
    }
}
