package com.nuvio.app.features.streams

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlin.concurrent.thread
import kotlinx.serialization.json.Json

internal actual object StreamLinkCacheStorage {
    private val store = DesktopStorage.store("nuvio_stream_link_cache")
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // An expired link is otherwise removed only when its title is opened again.
        thread(isDaemon = true, name = "stream-link-cache-purge") {
            runCatching { purgeExpired(System.currentTimeMillis()) }
        }
    }

    actual fun loadEntry(hashedKey: String): String? =
        store.getString(ProfileScopedKey.of(hashedKey))

    actual fun saveEntry(hashedKey: String, payload: String) {
        store.putString(ProfileScopedKey.of(hashedKey), payload)
    }

    actual fun removeEntry(hashedKey: String) {
        store.remove(ProfileScopedKey.of(hashedKey))
    }

    /** Drops entries, for every profile, older than the longest reuse window the settings offer. */
    internal fun purgeExpired(nowMs: Long) {
        val expired = store.keysWithPrefix("stream_link_").filter { key ->
            val cachedAtMs = store.getString(key)?.let { raw ->
                runCatching { json.decodeFromString(CachedStreamLink.serializer(), raw).cachedAtMs }.getOrNull()
            }
            cachedAtMs == null || cachedAtMs <= 0L || nowMs - cachedAtMs > MaxReuseWindowMs
        }
        if (expired.isNotEmpty()) store.removeAll(expired)
    }

    // Matches the largest "reuse last link" option (168 h) in PlaybackStreamSettings.
    private const val MaxReuseWindowMs = 168L * 60 * 60 * 1000
}
