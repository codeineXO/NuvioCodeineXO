package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalWindowInfo
import com.nuvio.app.core.ui.desktopDecodeEdge
import com.nuvio.app.isDesktop

/**
 * The artwork behind the desktop loading screens. Each screen (stream page, player, the player's controls page)
 * is a separate surface whose image loads asynchronously, so a fresh one would draw black for a few frames at
 * every handover. Keeping the last decoded artwork lets each new screen paint it from its very first frame.
 */
internal object OpeningArtworkCache {
    private var entry by mutableStateOf<Pair<String, Painter>?>(null)

    fun painterFor(url: String?): Painter? = entry?.takeIf { url != null && it.first == url }?.second

    fun put(url: String, painter: Painter) {
        entry = url to painter
    }

    fun clear() {
        entry = null
    }
}

/**
 * Decodes [url] ahead of time so the first loading screen already has its artwork. It asks for exactly what a
 * full-window loading screen will (window size plus desktop headroom, cropped to fill), so that screen's own
 * request is a memory-cache hit rather than a second decode.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PrefetchOpeningArtwork(url: String?) {
    if (!isDesktop || url.isNullOrBlank()) return
    val context = LocalPlatformContext.current
    val windowSize = LocalWindowInfo.current.containerSize
    LaunchedEffect(url, windowSize) {
        if (OpeningArtworkCache.painterFor(url) != null) return@LaunchedEffect
        if (windowSize.width <= 0 || windowSize.height <= 0) return@LaunchedEffect
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context)
                .data(url)
                .size(desktopDecodeEdge(windowSize.width), desktopDecodeEdge(windowSize.height))
                .scale(Scale.FILL)
                .build(),
        )
        val image = (result as? SuccessResult)?.image ?: return@LaunchedEffect
        if (OpeningArtworkCache.painterFor(url) == null) {
            OpeningArtworkCache.put(url, image.asPainter(context))
        }
    }
}
