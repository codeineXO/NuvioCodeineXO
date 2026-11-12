package com.nuvio.app.features.remote

import com.nuvio.app.AppScreenTab
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.remote.RemoteAppBridge.Command
import com.nuvio.app.features.search.SearchRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Browsing on the phone: Home's rows and Search's results go to the phone, and the phone's taps come back as the
 * same actions as the sidebar, a poster or a Continue Watching card on the PC.
 */
internal object RemoteHome {
    /** Posters per row on the phone; the PC's "View All" has the rest. */
    private const val ROW_LIMIT = 24

    /**
     * `{"k":"home","hero":[…],"continue":[…],"rows":[…]}`, sent whenever Home's content changes. Rows follow the
     * order of the Home settings list, like the PC: each is a catalog or a collection (folder tiles).
     */
    val homeMessages: Flow<String> = combine(
        HomeRepository.uiState,
        HomeCatalogSettingsRepository.uiState,
        CollectionRepository.collections,
        RemoteAppBridge.continueWatching,
    ) { home, settings, collections, resume ->
        val sections = home.sections.associateBy { it.key }
        val collectionsById = collections.associateBy { it.id }
        buildJsonObject {
            put("k", "home")
            put("loading", home.isLoading && home.sections.isEmpty())
            putJsonArray("hero") { home.heroItems.forEach { addJsonObject { hero(it) } } }
            putJsonArray("continue") { resume.forEach { addJsonObject { continueWatching(it) } } }
            putJsonArray("rows") {
                settings.items.filter { it.enabled }.forEach { entry ->
                    if (entry.isCollection) {
                        collectionsById[entry.collectionId]?.takeIf { it.folders.isNotEmpty() }?.let { addJsonObject { collection(it) } }
                    } else {
                        sections[entry.key]?.takeIf { it.items.isNotEmpty() }?.let { addJsonObject { row(it) } }
                    }
                }
            }
        }.toString()
    }.distinctUntilChanged()

    /** `{"k":"search","loading":…,"rows":[…]}`, the results for the query typed on the phone or the PC. */
    val searchMessages: Flow<String> = SearchRepository.uiState.map { search ->
        buildJsonObject {
            put("k", "search")
            put("loading", search.isLoading)
            putJsonArray("rows") { search.sections.forEachIndexed { index, section -> addJsonObject { row(section, "$index") } } }
        }.toString()
    }.distinctUntilChanged()

    /**
     * One browsing message from the phone: `tab` (home, search, shuffle, library, settings), `open` (a poster by
     * `from` hero/row/search, the row's `row` key and `item`), `folder` (a collection's folder tile by `row` and
     * `item` ids), `resume` (a Continue Watching card by `item`) or `search` (`query`).
     */
    fun handle(cmd: String, json: JsonObject) {
        val item = json["item"]?.jsonPrimitive?.intOrNull
        val row = json["row"]?.jsonPrimitive?.contentOrNull
        when (cmd) {
            "tab" -> when (val tab = json["tab"]?.jsonPrimitive?.contentOrNull) {
                "shuffle" -> RemoteAppBridge.send(Command.Shuffle)
                else -> AppScreenTab.entries.firstOrNull { it.name.equals(tab, ignoreCase = true) }
                    ?.let { RemoteAppBridge.send(Command.SelectTab(it)) }
            }
            "open" -> {
                val meta = when (json["from"]?.jsonPrimitive?.contentOrNull) {
                    "hero" -> HomeRepository.uiState.value.heroItems.getOrNull(item ?: -1)
                    "row" -> HomeRepository.uiState.value.sections.firstOrNull { it.key == row }?.items?.getOrNull(item ?: -1)
                    "search" -> SearchRepository.uiState.value.sections.getOrNull(row?.toIntOrNull() ?: -1)?.items?.getOrNull(item ?: -1)
                    else -> null
                }
                meta?.let { RemoteAppBridge.send(Command.OpenTitle(it)) }
            }
            "folder" -> {
                val collection = CollectionRepository.collections.value.firstOrNull { it.id == row }
                val folder = collection?.folders?.getOrNull(item ?: -1) ?: return
                RemoteAppBridge.send(Command.OpenFolder(collection.id, folder.id, folder.title.ifBlank { collection.title }))
            }
            "resume" -> RemoteAppBridge.continueWatching.value.getOrNull(item ?: -1)
                ?.let { RemoteAppBridge.send(Command.Resume(it)) }
            "search" -> json["query"]?.jsonPrimitive?.contentOrNull
                ?.take(200)
                ?.let(RemoteAppBridge::requestSearch)
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.hero(meta: MetaPreview) {
        put("name", meta.name)
        put("image", meta.banner ?: meta.landscapePoster ?: meta.poster)
        put("logo", meta.logo)
        put("description", meta.description)
        put("year", meta.releaseInfo)
        put("rating", meta.imdbRating)
        put("genres", meta.genres.take(3).joinToString(" · "))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.continueWatching(item: ContinueWatchingItem) {
        put("title", item.title)
        put("episode", if (item.seasonNumber != null && item.episodeNumber != null) "S${item.seasonNumber} E${item.episodeNumber}" else null)
        put("subtitle", item.episodeTitle ?: item.subtitle.takeIf { it.isNotBlank() })
        put("image", item.episodeThumbnail ?: item.background ?: item.imageUrl ?: item.poster)
        put("progress", item.progressFraction)
        val leftMinutes = ((item.durationMs - item.resumePositionMs) / 60_000).takeIf { item.durationMs > 0 && it > 0 }
        put("left", leftMinutes?.let { "${it}m left" })
    }

    /** A collection's row: its folders as tiles, opened on the PC by `folder`. */
    private fun kotlinx.serialization.json.JsonObjectBuilder.collection(collection: Collection) {
        put("kind", "collection")
        put("key", collection.id)
        put("title", collection.title)
        val shape = collection.folders.first().tileShape.lowercase()
        put("shape", if (shape == "landscape" || shape == "wide") "landscape" else "poster")
        putJsonArray("items") {
            collection.folders.forEach { folder ->
                addJsonObject {
                    put("name", folder.title)
                    put("image", folder.coverImageUrl)
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.row(section: HomeCatalogSection, key: String = section.key) {
        put("kind", "catalog")
        put("key", key)
        put("title", section.title)
        val landscape = section.items.firstOrNull()?.posterShape == PosterShape.Landscape
        put("shape", if (landscape) "landscape" else "poster")
        putJsonArray("items") { section.items.take(ROW_LIMIT).forEach { poster(it, landscape) } }
    }

    private fun JsonArrayBuilder.poster(meta: MetaPreview, landscape: Boolean) = addJsonObject {
        put("name", meta.name)
        put("image", if (landscape) meta.landscapePoster ?: meta.banner ?: meta.poster else meta.poster ?: meta.landscapePoster)
    }
}
