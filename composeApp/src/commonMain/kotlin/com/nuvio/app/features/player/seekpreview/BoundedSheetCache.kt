package com.nuvio.app.features.player.seekpreview

import androidx.compose.ui.graphics.ImageBitmap
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

internal class BoundedSheetCache(
    private val maxDecodedSheets: Int = 5,
) {
    private val mutex = Mutex()
    private val memoryCache = object : LinkedHashMap<String, ImageBitmap>(maxDecodedSheets + 2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > maxDecodedSheets
        }
    }

    suspend fun get(sheetUrl: String): ImageBitmap? {
        mutex.withLock {
            memoryCache[sheetUrl]?.let { return it }
        }

        return withContext(Dispatchers.Default) {
            val response = runCatching {
                httpRequestRaw(
                    method = "GET",
                    url = sheetUrl,
                    headers = emptyMap(),
                    body = "",
                    maxResponseBodyBytes = 12 * 1024 * 1024,
                )
            }.getOrNull() ?: return@withContext null

            if (response.status !in 200..299 || response.bodyBytes.isEmpty()) {
                return@withContext null
            }

            val bitmap = runCatching {
                response.bodyBytes.decodeToImageBitmap()
            }.getOrNull() ?: return@withContext null

            mutex.withLock {
                memoryCache[sheetUrl] = bitmap
            }
            bitmap
        }
    }

    suspend fun clear() {
        mutex.withLock {
            memoryCache.clear()
        }
    }
}
