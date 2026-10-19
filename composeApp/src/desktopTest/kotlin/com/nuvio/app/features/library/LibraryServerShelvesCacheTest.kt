package com.nuvio.app.features.library

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryServerShelvesCacheTest {
    @Test
    fun reusesMetadataUntilProfileRevisionOrExplicitRefreshChanges() = runBlocking {
        val cache = LibraryServerShelvesCache()
        var loads = 0
        val load: suspend () -> List<LibraryServerShelf> = { loads++; emptyList() }
        cache.get(1, 0, load = load)
        cache.get(1, 0, load = load)
        assertEquals(1, loads)
        cache.get(1, 1, load = load)
        cache.get(2, 1, load = load)
        cache.get(2, 1, forceRefresh = true, load = load)
        assertEquals(4, loads)
    }

    @Test
    fun failedRefreshDoesNotReusePreviousSnapshot() = runBlocking {
        val cache = LibraryServerShelvesCache()
        var loads = 0
        cache.get(1, 0) { loads++; emptyList() }
        assertFailsWith<IllegalStateException> {
            cache.get(1, 0, forceRefresh = true) { error("failed request") }
        }
        cache.get(1, 0) { loads++; emptyList() }
        assertEquals(2, loads)
    }
}
