package com.nuvio.app.features.details.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Scale
import com.nuvio.app.features.details.MetaDetails
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.ceil

@Composable
internal fun PreloadSeasonPosterHoverPreviews(meta: MetaDetails) {
    val urls = remember(meta) {
        val groupedEpisodes = meta.videos.filter { it.season != null }.groupBy { it.season!! }
        groupedEpisodes.keys.mapNotNull { season ->
            resolveSeasonPoster(season, groupedEpisodes, meta) ?: meta.poster ?: meta.background
        }.filter(String::isNotBlank).distinct()
    }
    val requests = urls.map { rememberSeasonPosterHoverRequest(it) }
    val context = LocalPlatformContext.current
    LaunchedEffect(context, requests) {
        val loader = SingletonImageLoader.get(context)
        val concurrency = Semaphore(4)
        coroutineScope {
            requests.forEach { request ->
                launch { concurrency.withPermit { loader.execute(request) } }
            }
        }
    }
}

@Composable
internal fun rememberSeasonPosterHoverRequest(url: String): ImageRequest {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    // Match desktop artwork decode headroom, and share the exact request with the popup.
    val width = with(density) { ceil(110.dp.toPx() * 1.25).toInt() }
    val height = with(density) { ceil(165.dp.toPx() * 1.25).toInt() }
    return remember(context, url, width, height) {
        ImageRequest.Builder(context)
            .data(url)
            .size(width, height)
            .scale(Scale.FILL)
            .build()
    }
}
