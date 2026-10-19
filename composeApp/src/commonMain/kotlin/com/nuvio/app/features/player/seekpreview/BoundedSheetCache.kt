package com.nuvio.app.features.player.seekpreview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.isDesktop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

internal class BoundedSheetCache(
    private val maxDecodedSheets: Int = 5,
    private val maxDecodedBytes: Long = if (isDesktop) 32L * 1024 * 1024 else Long.MAX_VALUE,
    private val loadSheet: suspend (String) -> ImageBitmap? = ::loadPreviewSheet,
) {
    private val mutex = Mutex()
    private val memoryCache = LinkedHashMap<String, ImageBitmap>(maxDecodedSheets + 2, 0.75f, true)
    private val inFlight = mutableMapOf<String, CompletableDeferred<ImageBitmap?>>()
    private var decodedBytes = 0L

    init {
        require(maxDecodedSheets > 0 && maxDecodedBytes > 0)
    }

    suspend fun get(sheetUrl: String): ImageBitmap? {
        var owner = false
        val pending = mutex.withLock {
            memoryCache[sheetUrl]?.let { return it }
            inFlight[sheetUrl] ?: CompletableDeferred<ImageBitmap?>().also {
                inFlight[sheetUrl] = it
                owner = true
            }
        }
        if (!owner) return pending.await()
        try {
            val bitmap = loadSheet(sheetUrl)
            mutex.withLock {
                // clear() removes the request identity; old loads may still serve their callers.
                if (inFlight[sheetUrl] === pending) {
                    if (bitmap != null && bitmap.decodedSize() <= maxDecodedBytes) {
                        memoryCache.put(sheetUrl, bitmap)?.let { decodedBytes -= it.decodedSize() }
                        decodedBytes += bitmap.decodedSize()
                        while (memoryCache.size > maxDecodedSheets || decodedBytes > maxDecodedBytes) {
                            val oldest = memoryCache.entries.iterator()
                            decodedBytes -= oldest.next().value.decodedSize()
                            oldest.remove()
                        }
                    }
                    inFlight.remove(sheetUrl)
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
                    if (inFlight[sheetUrl] === pending) inFlight.remove(sheetUrl)
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

        if (response.status !in 200..299 || response.bodyBytes.isEmpty()) {
            return@withContext null
        }

        runCatching { response.bodyBytes.decodeToImageBitmap() }.getOrNull()
    }
