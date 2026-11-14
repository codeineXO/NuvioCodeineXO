package com.nuvio.app.features.remote

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.search.SearchHistoryRepository
import com.nuvio.app.features.search.SearchRepository
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.discover_all_genres
import nuvio.composeapp.generated.resources.discover_catalog_context
import nuvio.composeapp.generated.resources.media_anime
import nuvio.composeapp.generated.resources.media_channels
import nuvio.composeapp.generated.resources.media_movies
import nuvio.composeapp.generated.resources.media_series
import nuvio.composeapp.generated.resources.media_tv
import org.jetbrains.compose.resources.getString

/** What the PC's Search shows before you type, on the phone: recent searches, and Discover with its pickers. */
internal object RemoteSearch {
    /**
     * `{"k":"discover","recent":[…],"types":[…],"catalogs":[…],"genres":[…],"source":…,"items":[…]}`. Each picker
     * is a list of `{key,label}` with the selected `key` in `type`, `catalog` and `genre` ("" for All Genres).
     */
    val messages: Flow<String> get() = combine(
        SearchHistoryRepository.uiState,
        SearchHistoryRepository.enabled,
        SearchRepository.discoverUiState,
    ) { recent, historyEnabled, discover ->
        val types = discover.typeOptions.map { it to typeLabel(it) }
        val catalog = discover.selectedCatalog
        val genres = buildList {
            if (catalog?.genreRequired != true) add("" to getString(Res.string.discover_all_genres))
            discover.genreOptions.forEach { add(it to it) }
        }
        val source = catalog?.let { getString(Res.string.discover_catalog_context, it.addonName, typeLabel(it.type)) }
        buildJsonObject {
            put("k", "discover")
            putJsonArray("recent") { if (historyEnabled) recent.forEach { add(it) } }
            putJsonArray("types") { options(types) }
            put("type", discover.selectedType)
            putJsonArray("catalogs") { options(discover.catalogOptions.map { it.key to it.catalogName }) }
            put("catalog", discover.selectedCatalogKey)
            putJsonArray("genres") { options(genres) }
            put("genre", discover.selectedGenre.orEmpty())
            put("source", source)
            put("loading", discover.isLoading)
            put("more", discover.canLoadMore)
            put("error", discover.errorMessage)
            put("shape", shapeOf(discover.items))
            putJsonArray("items") { discover.items.forEach { poster(it, discover.items.isLandscape()) } }
        }.toString()
    }.distinctUntilChanged()

    /** `searchRemove` (a recent `query`), `discoverType`/`discoverCatalog`/`discoverGenre` (`value`) or `discoverMore`. */
    fun handle(cmd: String, json: JsonObject) {
        val value = json["value"]?.jsonPrimitive?.contentOrNull
        SwingUtilities.invokeLater {
            when (cmd) {
                "searchRemove" -> json["query"]?.jsonPrimitive?.contentOrNull?.let(SearchHistoryRepository::removeSearch)
                "discoverType" -> value?.let(SearchRepository::selectDiscoverType)
                "discoverCatalog" -> value?.let(SearchRepository::selectDiscoverCatalog)
                "discoverGenre" -> SearchRepository.selectDiscoverGenre(value?.ifEmpty { null })
                "discoverMore" -> SearchRepository.loadMoreDiscover()
            }
        }
    }

    fun posterAt(item: Int): MetaPreview? = SearchRepository.discoverUiState.value.items.getOrNull(item)

    /** The PC's names for content types. */
    private suspend fun typeLabel(type: String): String = when (type.lowercase()) {
        "movie" -> getString(Res.string.media_movies)
        "series" -> getString(Res.string.media_series)
        "anime" -> getString(Res.string.media_anime)
        "channel" -> getString(Res.string.media_channels)
        "tv" -> getString(Res.string.media_tv)
        else -> type.replaceFirstChar { it.titlecase() }
    }

    private fun JsonArrayBuilder.options(options: List<Pair<String, String>>) =
        options.forEach { (key, label) -> addJsonObject { put("key", key); put("label", label) } }
}
