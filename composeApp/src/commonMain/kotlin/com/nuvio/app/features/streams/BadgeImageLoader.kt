package com.nuvio.app.features.streams

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.nuvio.app.core.ui.addPlatformDecoders
import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

private const val BadgeMemoryCacheBytes = 16L * 1024 * 1024

/**
 * Separate ImageLoader for badge images without CacheControlCacheStrategy.
 * Badges should stay cached indefinitely. Animated badges (GIFs) are decoded by the platform hook.
 */
internal object BadgeImageLoader {

    private val lock = SynchronizedObject()

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: PlatformContext): ImageLoader {
        instance?.let { return it }
        synchronized(lock) {
            instance?.let { return it }
            val loader = ImageLoader.Builder(context)
                .memoryCachePolicy(CachePolicy.ENABLED)
                // Badges are small; the default percent-of-heap sizing would reserve far more than they use.
                .memoryCache { MemoryCache.Builder().maxSizeBytes(BadgeMemoryCacheBytes).build() }
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .components { addPlatformDecoders() }
                .build()
            instance = loader
            return loader
        }
    }
}
