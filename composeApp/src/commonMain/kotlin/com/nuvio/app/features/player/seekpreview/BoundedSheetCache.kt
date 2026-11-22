package com.nuvio.app.features.player.seekpreview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.isDesktop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

internal class BoundedSheetCache(
    private val maxDecodedSheets: Int = 5,
    private val maxDecodedBytes: Long = if (isDesktop) 32L * 1024 * 1024 else Long.MAX_VALUE,
    private val loadSheet: suspend (String) -> ImageBitmap? = ::loadPreviewSheet,
    /** Called when a signed sheet URL has expired (HTTP 403); the caller should look the title up again. */
    private val onExpired: () -> Unit = {},
) {
    private val mutex = Mutex()
    private val memoryCache = LinkedHashMap<String, ImageBitmap>(maxDecodedSheets + 2, 0.75f, true)
    private val inFlight = mutableMapOf<String, CompletableDeferred<ImageBitmap?>>()
    private var decodedBytes = 0L

    init {
        require(maxDecodedSheets > 0 && maxDecodedBytes > 0)
    }

    /** Signed URLs change on every lookup; cache by the stable path so refreshed tracks reuse decoded sheets. */
    suspend fun get(sheetUrl: String): ImageBitmap? {
        val key = sheetUrl.substringBefore('?')
        var owner = false
        val pending = mutex.withLock {
            memoryCache[key]?.let { return it }
            // A load cancelled with its owner (e.g. a superseded preload) must not be joined.
            inFlight[key]?.takeUnless { it.isCancelled } ?: CompletableDeferred<ImageBitmap?>().also {
                inFlight[key] = it
                owner = true
            }
        }
        if (!owner) {
            try {
                return pending.await()
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                // The owner was cancelled, not us: start our own load.
                return get(sheetUrl)
            }
        }
        try {
            val bitmap = try {
                loadSheet(sheetUrl)
            } catch (_: SeekrSheetExpiredException) {
                onExpired()
                null
            }
            mutex.withLock {
                // clear() removes the request identity; old loads may still serve their callers.
                if (inFlight[key] === pending) {
                    if (bitmap != null && bitmap.decodedSize() <= maxDecodedBytes) {
                        memoryCache.put(key, bitmap)?.let { decodedBytes -= it.decodedSize() }
                        decodedBytes += bitmap.decodedSize()
                        while (memoryCache.size > maxDecodedSheets || decodedBytes > maxDecodedBytes) {
                            val oldest = memoryCache.entries.iterator()
                            decodedBytes -= oldest.next().value.decodedSize()
                            oldest.remove()
                        }
                    }
                    inFlight.remove(key)
                }
            }
            pending.complete(bitmap)
            return bitmap
        } catch (error: Throwable) {
            if (error is CancellationException) pending.cancel(error) else pending.completeExceptionally(error)
            throw error
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (inFlight[key] === pending) inFlight.remove(key)
                }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            memoryCache.clear()
            decodedBytes = 0L
            inFlight.clear()
        }
    }

    private fun ImageBitmap.decodedSize(): Long =
        width.toLong() * height * if (config == ImageBitmapConfig.F16) 8 else 4
}

/** Signed sprite URLs answer 403 once they expire. */
internal class SeekrSheetExpiredException : Exception("Seekr sprite URL expired")

private suspend fun loadPreviewSheet(sheetUrl: String): ImageBitmap? =
    withContext(Dispatchers.Default) {
        val response = runCatching {
            httpRequestRaw(
                method = "GET",
                url = sheetUrl,
                headers = emptyMap(),
                body = "",
                maxResponseBodyBytes = 12 * 1024 * 1024,
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            return@withContext null
        }

        if (response.status == 403) throw SeekrSheetExpiredException()
        if (response.status !in 200..299 || response.bodyBytes.isEmpty()) {
            return@withContext null
        }

        runCatching { response.bodyBytes.decodeToImageBitmap() }.getOrNull()
    }
