package com.nuvio.app.features.home.components

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import androidx.compose.ui.graphics.painter.Painter
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.home.HomeHeroMeta
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.fetchHomeHeroDetails
import com.nuvio.app.features.home.fetchHomeHeroRatings
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.home.withHeroMeta
import com.nuvio.app.features.mdblist.MdbListSettings
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
    private val logoHeight: Int,
    private val ratingSettings: MdbListSettings?,
) {
    private data class ArtworkKey(val title: String, val url: String, val width: Int, val height: Int)
    data class Logo(val url: String, val painter: Painter)

    private val details = PersistentHeroRequests<String, MetaDetails>(scope)
    private val artwork = PersistentHeroRequests<ArtworkKey, Painter>(scope)
    private val ratings = PersistentHeroRequests<String, List<MetaExternalRating>>(scope)

    private suspend fun details(item: MetaPreview, foreground: Boolean) = details.load(item.stableKey(), foreground) {
        withTimeoutOrNull(2_500) { fetchHomeHeroDetails(item) }
    }

    suspend fun enrich(item: MetaPreview, foreground: Boolean): MetaPreview {
        val meta = details(item, foreground)
        return item.withHeroMeta(HomeHeroMeta(meta)).copy(
            banner = item.banner.nonBlank() ?: meta?.background.nonBlank() ?: item.landscapePoster.nonBlank(),
            poster = item.poster.nonBlank() ?: meta?.poster.nonBlank(),
            description = item.description.nonBlank() ?: meta?.description.nonBlank(),
        )
    }

    suspend fun backdrop(item: MetaPreview, foreground: Boolean): Painter? = withTimeoutOrNull(2_800) {
        val banner = item.banner.nonBlank() ?: item.landscapePoster.nonBlank()
        val poster = item.poster.nonBlank() ?: item.rawPosterUrl.nonBlank()
        val known = banner ?: poster
        val image = known?.let { artwork(item, it, width, height, foreground) }
        if (image != null && banner != null) return@withTimeoutOrNull image
        if (image != null) {
            // Give a landscape image a short head start before settling on an already decoded poster.
            return@withTimeoutOrNull withTimeoutOrNull(120) {
                details(item, foreground)?.background.nonBlank()?.takeUnless { it == known }
                    ?.let { artwork(item, it, width, height, foreground) }
            } ?: image
        }

        val meta = details(item, foreground)
        listOfNotNull(meta?.background.nonBlank(), poster, meta?.poster.nonBlank(), item.rawPosterUrl.nonBlank())
            .distinct().filterNot { it == known }
            .firstNotNullOfOrNull { artwork(item, it, width, height, foreground) }
    }

    suspend fun logo(item: MetaPreview, foreground: Boolean, currentLogo: Logo? = null): Logo? {
        val catalogLogo = item.logo.nonBlank()
        val logoWidth = (width / 2).coerceAtLeast(1)
        suspend fun loadLogo(url: String): Logo? =
            currentLogo?.takeIf { it.url == url }
                ?: artwork(item, url, logoWidth, logoHeight, foreground)?.let { Logo(url, it) }

        if (catalogLogo != null) {
            loadLogo(catalogLogo)?.let { return it }
        }
        val url = details(item, foreground)?.logo.nonBlank()?.takeUnless { it == catalogLogo } ?: return null
        return loadLogo(url)
    }

    suspend fun ratings(item: MetaPreview, foreground: Boolean): List<MetaExternalRating>? {
        val settings = ratingSettings ?: return null
        return ratings.load(item.stableKey(), foreground) {
            withTimeoutOrNull(2_500) { fetchHomeHeroRatings(item, settings) { details(item, foreground) } }
                ?.takeIf { it.isNotEmpty() }
        }
    }

    private suspend fun artwork(item: MetaPreview, url: String, width: Int, height: Int, foreground: Boolean): Painter? {
        return artwork.load(ArtworkKey(item.stableKey(), url, width, height), foreground) {
            val request = ImageRequest.Builder(context).data(url).size(width, height).build()
            withTimeoutOrNull(1_750) {
                (imageLoader.execute(request) as? SuccessResult)?.image?.asPainter(context)
            }
        }
    }

    suspend fun preload(item: MetaPreview) = coroutineScope {
        launch { details(item, foreground = false) }
        launch { backdrop(item, foreground = false) }
        launch { logo(item, foreground = false) }
        launch { ratings(item, foreground = false) }
    }

    fun retain(titles: Set<String>) {
        details.retain { it in titles }
        artwork.retain { it.title in titles }
        ratings.retain { it in titles }
    }
}

internal fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
