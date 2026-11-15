package com.nuvio.app.features.home.components

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import androidx.compose.ui.graphics.painter.Painter
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.fetchHomeHeroDetails
import com.nuvio.app.features.home.stableKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class PersistentHeroTarget(val item: MetaPreview, val row: Any? = null, val index: Int? = null)

internal fun persistentHeroNeighbors(
    rows: Map<Any, List<MetaPreview>>,
    current: PersistentHeroTarget,
    previous: PersistentHeroTarget?,
): List<MetaPreview> {
    val key = current.item.stableKey()
    val rowKey = current.row?.takeIf { it in rows } ?: previous?.row?.takeIf { row ->
        rows[row]?.any { it.stableKey() == key } == true
    } ?: rows.entries.firstOrNull { (_, items) -> items.any { it.stableKey() == key } }?.key
    val row = rows[rowKey] ?: return emptyList()
    val index = current.index?.takeIf { row.getOrNull(it)?.stableKey() == key }
        ?: row.indexOfFirst { it == current.item }.takeIf { it >= 0 }
        ?: row.indexOfFirst { it.stableKey() == key }
    if (index < 0) return emptyList()

    val previousIndex = previous?.takeIf { it.row == null || it.row == rowKey }?.let { target ->
        target.index?.takeIf { row.getOrNull(it)?.stableKey() == target.item.stableKey() }
            ?: row.indexOfFirst { it.stableKey() == target.item.stableKey() }
    }
    val step = if (previousIndex != null && previousIndex > index) -1 else 1
    return listOfNotNull(row.getOrNull(index + step), row.getOrNull(index - step))
        .filter { it.stableKey() != key }
        .distinctBy { it.stableKey() }
}

// Accessed by the pane and its effects on the composition dispatcher.
internal class PersistentHeroRequests<K, V : Any>(private val scope: CoroutineScope) {
    private class Request<V>(val result: Deferred<V?>, val speculative: Boolean)

    private val requests = mutableMapOf<K, Request<V>>()

    suspend fun load(key: K, foreground: Boolean, fetch: suspend () -> V?): V? {
        fun start(): Request<V> {
            return Request(scope.async(start = CoroutineStart.LAZY) { fetch() }, speculative = !foreground)
                .also { requests[key] = it }
        }

        val request = requests[key] ?: start()
        val result = request.result.await()
        if (result != null || !foreground || !request.speculative) return result

        // A selected title gets a fresh attempt after an unsuccessful speculative load.
        val retry = requests[key]?.takeUnless { it === request } ?: start()
        return retry.result.await()
    }

    fun retain(keep: (K) -> Boolean) {
        val iterator = requests.iterator()
        while (iterator.hasNext()) {
            val (key, request) = iterator.next()
            if (!keep(key)) {
                iterator.remove()
                request.result.cancel()
            }
        }
    }
}

internal class PersistentHeroLoader(
    scope: CoroutineScope,
    private val context: PlatformContext,
    private val imageLoader: ImageLoader,
    private val width: Int,
    private val height: Int,
) {
    private data class ArtworkKey(val title: String, val url: String, val width: Int)

    private val details = PersistentHeroRequests<String, MetaDetails>(scope)
    private val artwork = PersistentHeroRequests<ArtworkKey, Painter>(scope)

    suspend fun details(item: MetaPreview, foreground: Boolean) = details.load(item.stableKey(), foreground) {
        withTimeoutOrNull(2_500) { fetchHomeHeroDetails(item) }
    }

    suspend fun backdrop(item: MetaPreview, url: String, foreground: Boolean): Painter? =
        artwork(item, url, width, foreground)

    suspend fun logo(item: MetaPreview, url: String): Painter? =
        artwork(item, url, (width / 2).coerceAtLeast(1), foreground = true)

    private suspend fun artwork(item: MetaPreview, url: String, width: Int, foreground: Boolean): Painter? {
        return artwork.load(ArtworkKey(item.stableKey(), url, width), foreground) {
            val request = ImageRequest.Builder(context).data(url).size(width, height).build()
            withTimeoutOrNull(1_750) {
                (imageLoader.execute(request) as? SuccessResult)?.image?.asPainter(context)
            }
        }
    }

    suspend fun preload(item: MetaPreview) = coroutineScope {
        launch { details(item, foreground = false) }
        val backdrop = item.backdropPreviewUrl()
        if (backdrop != null) launch { backdrop(item, backdrop, foreground = false) }
    }

    fun retain(titles: Set<String>) {
        details.retain { it in titles }
        artwork.retain { it.title in titles }
    }
}

internal fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

internal fun MetaPreview.backdropPreviewUrl(): String? =
    banner.nonBlank() ?: landscapePoster.nonBlank() ?: poster.nonBlank() ?: rawPosterUrl.nonBlank()
