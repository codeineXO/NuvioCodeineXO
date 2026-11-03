package com.nuvio.app.core.ui

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.memory.MemoryCache
import kotlinx.coroutines.Dispatchers

private const val ImageMemoryCacheBytes = 256L * 1024 * 1024

/** Bound combined still/animated decode work so image arrivals cannot occupy every CPU worker. */
internal fun desktopArtworkDecodeParallelism(processors: Int): Int =
    (processors.coerceAtLeast(1) / 2).coerceIn(1, 3)

internal val DesktopArtworkDecodeDispatcher by lazy {
    Dispatchers.Default.limitedParallelism(desktopArtworkDecodeParallelism(Runtime.getRuntime().availableProcessors()))
}

internal actual val platformProvidesImageLoader: Boolean = false

internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder {
    return decoderCoroutineContext(DesktopArtworkDecodeDispatcher).memoryCache {
        MemoryCache.Builder()
            .maxSizeBytes(ImageMemoryCacheBytes)
            .build()
    }
}

internal actual fun ComponentRegistry.Builder.addPlatformDecoders() {
    add(DesktopArtworkInterceptor())
    add(SkiaGifDecoder.Factory())
    add(SvgEmbeddedImageDecoder.Factory())
    add(DesktopRasterDecoder.Factory())
}
