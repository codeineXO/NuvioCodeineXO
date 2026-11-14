package com.nuvio.app.features.remote

import com.nuvio.app.AppScreenTab
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.home.AppInterface
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.catalog.CatalogRepository
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import androidx.compose.ui.graphics.toArgb
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.home.components.HeroInfo
import com.nuvio.app.features.home.components.heroInfo
import com.nuvio.app.features.home.components.toPersistentHeroPreview
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.remote.RemoteAppBridge.Command
import com.nuvio.app.features.search.SearchRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.continueWatchingItemKey
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Browsing on the phone: Home's rows and Search's results go to the phone, and the phone's taps come back as the
 * same actions as the sidebar, a poster or a Continue Watching card on the PC.
 */
internal object RemoteHome {

    /**
     * `{"k":"home","hero":[…],"continue":[…],"rows":[…]}`, sent whenever Home's content changes. Rows follow the
     * order of the Home settings list, like the PC: each is a catalog or a collection (folder tiles).
     */
    val homeMessages: Flow<String> get() = combine(
        HomeRepository.uiState,
        HomeCatalogSettingsRepository.uiState,
        CollectionRepository.collections,
        RemoteAppBridge.continueWatching,
        RemoteAppBridge.heroItems.combine(RemoteViewAll.rows) { heroes, viewAll -> heroes to viewAll["home"].orEmpty() },
    ) { home, settings, collections, resume, (loadedHeroes, viewAll) ->
        val sections = home.sections.associateBy { it.key }
        val collectionsById = collections.associateBy { it.id }
        // Persistent Hero: no carousel; the phone mirrors the PC's hero, and cards carry the key it is matched by.
        val persistentHero = settings.appInterface == AppInterface.PersistentHero
        // The carousel as the PC shows it: each title with its details and ratings once Home has loaded them.
        val loaded = loadedHeroes.associateBy { it.stableKey() }
        val heroes = if (settings.heroEnabled) {
            home.heroItems.map { (loaded[it.stableKey()] ?: it).let { meta -> meta to heroInfo(meta) } }
        } else emptyList()
        buildJsonObject {
            put("k", "home")
            put("layout", settings.appInterface.key)
            put("loading", home.isLoading && home.sections.isEmpty())
            put("heroEnabled", settings.heroEnabled)
            putJsonArray("hero") {
                heroes.forEach { (meta, info) -> addJsonObject { hero(meta, info) } }
            }
            putJsonArray("continue") { resume.forEach { addJsonObject { continueWatching(it, persistentHero) } } }
            put("continueViewAll", RemoteViewAll.CONTINUE_WATCHING in viewAll)
            putJsonArray("rows") {
                settings.items.filter { it.enabled }.forEach { entry ->
                    if (entry.isCollection) {
                        collectionsById[entry.collectionId]?.takeIf { it.folders.isNotEmpty() }?.let { addJsonObject { collection(it, it.id in viewAll) } }
                    } else {
                        sections[entry.key]?.takeIf { it.items.isNotEmpty() }?.let { addJsonObject { catalogRow(it, heroKeys = persistentHero, viewAll = it.key in viewAll) } }
                    }
                }
            }
        }.toString()
    }.distinctUntilChanged()

    /** `{"k":"hero","hero":{…}|null}`: the title Home's Persistent Hero shows on the PC, details loaded. */
    val heroMessages: Flow<String> get() = RemoteAppBridge.persistentHero.map { meta ->
        val info = meta?.let { heroInfo(it) }
        buildJsonObject {
            put("k", "hero")
            if (meta == null || info == null) put("hero", null as String?) else putJsonObject("hero") { hero(meta, info) }
        }.toString()
    }.distinctUntilChanged()

    /**
     * `{"k":"catalog","title":…,"collection":…}`: the page a row's "View All" opened on the PC, and for a catalog
     * its titles as far as they have loaded (`items`, `loading`, `more`). A collection's folders (its `collection`
     * id) and Continue Watching the phone draws from Home's rows.
     */
    val catalogMessages: Flow<String> get() = combine(
        RemoteAppBridge.route,
        RemoteViewAll.page,
        CatalogRepository.uiState,
    ) { route, page, catalog ->
        val isCatalog = route == "catalog"
        val items = if (isCatalog) catalog.items else emptyList()
        buildJsonObject {
            put("k", "catalog")
            put("title", page?.title)
            put("collection", page?.collectionId)
            put("loading", isCatalog && catalog.isLoading)
            put("more", isCatalog && catalog.canLoadMore)
            put("error", catalog.errorMessage.takeIf { isCatalog })
            put("shape", shapeOf(items))
            putJsonArray("items") { items.forEach { poster(it, items.isLandscape()) } }
        }.toString()
    }.distinctUntilChanged()

    /** `{"k":"search","loading":…,"rows":[…]}`, the results for the query typed on the phone or the PC. */
    val searchMessages: Flow<String> get() = SearchRepository.uiState.map { search ->
        buildJsonObject {
            put("k", "search")
            put("loading", search.isLoading)
            putJsonArray("rows") { search.sections.forEachIndexed { index, section -> addJsonObject { catalogRow(section, "$index") } } }
        }.toString()
    }.distinctUntilChanged()

    /**
     * One browsing message from the phone: `tab` (home, search, shuffle, library, settings), `open` (a poster by
     * `from` hero/row/search, the row's `row` key and `item`), `folder` (a collection's folder tile by `row` and
     * `item` ids), `resume` (a Continue Watching card by `item`), `search` (`query`), `layout` (the Home layout
     * by its `value` key, or the next one, like the PC's Switch Layout button) or `heroPick` (a poster, or a Continue
     * Watching card `from` continue, shown in the PC's Persistent Hero as if hovered).
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
            "open" -> posterAt(json)?.let { RemoteAppBridge.send(Command.OpenTitle(it)) }
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
            "viewAll" -> row?.let(RemoteViewAll::open)
            "catalogMore" -> SwingUtilities.invokeLater { if (RemoteAppBridge.route.value == "catalog") CatalogRepository.loadMore() }
            "heroPick" -> {
                val meta = if (json["from"]?.jsonPrimitive?.contentOrNull == "continue") {
                    RemoteAppBridge.continueWatching.value.getOrNull(item ?: -1)?.toPersistentHeroPreview()
                } else posterAt(json)
                meta?.let(RemoteAppBridge::pickHero)
            }
            "layout" -> {
                val value = json["value"]?.jsonPrimitive?.contentOrNull
                SwingUtilities.invokeLater {
                    val current = HomeCatalogSettingsRepository.uiState.value.appInterface
                    HomeCatalogSettingsRepository.setAppInterface(AppInterface.entries.firstOrNull { it.key == value } ?: current.next())
                }
            }
        }
    }

    fun posterAt(json: JsonObject): MetaPreview? {
        when (json["from"]?.jsonPrimitive?.contentOrNull) {
            "persistent" -> return RemoteAppBridge.persistentHero.value
            "shuffle" -> return RemoteShufflePage.picked()
        }
        val item = json["item"]?.jsonPrimitive?.intOrNull ?: return null
        val row = json["row"]?.jsonPrimitive?.contentOrNull
        return when (json["from"]?.jsonPrimitive?.contentOrNull) {
            "hero" -> if (HomeCatalogSettingsRepository.uiState.value.heroEnabled) {
                HomeRepository.uiState.value.heroItems.getOrNull(item)
            } else null
            "row" -> HomeRepository.uiState.value.sections.firstOrNull { it.key == row }?.items?.getOrNull(item)
            "search" -> SearchRepository.uiState.value.sections.getOrNull(row?.toIntOrNull() ?: -1)?.items?.getOrNull(item)
            "catalog" -> CatalogRepository.uiState.value.items.getOrNull(item)
            "folder" -> RemoteFolderPage.posterAt(row, item)
            "discover" -> RemoteSearch.posterAt(item)
            "library" -> RemoteLibrary.posterAt(row, item)
            "libraryGrid" -> RemoteLibrary.posterAt(null, item)
            else -> null
        }
    }

    private fun JsonObjectBuilder.continueWatching(item: ContinueWatchingItem, heroKey: Boolean) {
        put("key", continueWatchingItemKey(item))
        if (heroKey) put("heroKey", item.toPersistentHeroPreview().stableKey())
        put("title", item.title)
        put("episode", if (item.seasonNumber != null && item.episodeNumber != null) "S${item.seasonNumber} E${item.episodeNumber}" else null)
        put("subtitle", item.episodeTitle ?: item.subtitle.takeIf { it.isNotBlank() })
        put("image", item.episodeThumbnail ?: item.background ?: item.imageUrl ?: item.poster)
        put("progress", item.progressFraction)
        val leftMinutes = ((item.durationMs - item.resumePositionMs) / 60_000).takeIf { item.durationMs > 0 && it > 0 }
        // Like the PC's card: "1h 46m left", or "19m left" under an hour.
        put("left", leftMinutes?.let { if (it >= 60) "${it / 60}h ${it % 60}m left" else "${it}m left" })
    }

    /** A collection's row: its folders as tiles, opened on the PC by `folder`. */
    private fun JsonObjectBuilder.collection(collection: Collection, viewAll: Boolean) {
        put("kind", "collection")
        put("key", collection.id)
        put("title", collection.title)
        put("viewAll", viewAll)
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
}

/** Posters per row on the phone; "View All" has the rest. */
private const val ROW_LIMIT = 24

// The phone's posters and rows, shared by Home, Search, View All and collection folders.

internal fun List<MetaPreview>.isLandscape(): Boolean = firstOrNull()?.posterShape == PosterShape.Landscape

internal fun shapeOf(items: List<MetaPreview>): String = if (items.isLandscape()) "landscape" else "poster"

internal fun JsonObjectBuilder.posterIdentity(meta: MetaPreview) {
    put("id", meta.id)
    put("type", meta.type)
    put("name", meta.name)
    put("year", meta.releaseInfo)
}

/** A row of posters; `viewAll` where the PC shows it, `heroKeys` for the Persistent Hero to match its cards. */
internal fun JsonObjectBuilder.catalogRow(
    section: HomeCatalogSection,
    key: String = section.key,
    title: String = section.title,
    viewAll: Boolean = false,
    heroKeys: Boolean = false,
) {
    put("kind", "catalog")
    put("key", key)
    put("title", title)
    put("viewAll", viewAll)
    put("shape", shapeOf(section.items))
    putJsonArray("items") { section.items.take(ROW_LIMIT).forEach { poster(it, section.items.isLandscape(), heroKeys) } }
}

internal fun JsonArrayBuilder.poster(meta: MetaPreview, landscape: Boolean, heroKey: Boolean = false) = addJsonObject {
    posterIdentity(meta)
    if (heroKey) put("heroKey", meta.stableKey())
    put("image", if (landscape) meta.landscapePoster ?: meta.banner ?: meta.poster else meta.poster ?: meta.landscapePoster)
}

/** The hero's info rows, with the same rating settings as the PC's hero. */
internal suspend fun heroInfo(meta: MetaPreview): HeroInfo {
    val mdbList = MdbListSettingsRepository.uiState.value
    return meta.heroInfo(
        showImdbRating = MetaScreenSettingsRepository.uiState.value.showOverallRatings,
        showMdbListRatings = mdbList.isActive && mdbList.showOnHero,
    )
}

internal fun JsonObjectBuilder.hero(meta: MetaPreview, info: HeroInfo) {
    posterIdentity(meta)
    put("key", meta.stableKey())
    put("image", meta.banner ?: meta.poster ?: meta.landscapePoster)
    put("logo", meta.logo)
    put("description", meta.description)
    // The PC hero's info rows: facts, the age rating and status badge, language, and ratings with their logos.
    putJsonArray("facts") { info.facts.forEach { add(it) } }
    putJsonArray("badges") { info.badges.forEach { add(it) } }
    put("language", info.language)
    putJsonArray("ratings") {
        info.ratings.forEach {
            addJsonObject {
                put("name", it.name)
                put("logo", "/rating/${it.logoFile}")
                put("value", it.value)
                put("color", "#%06X".format(it.color.toArgb() and 0xFFFFFF))
            }
        }
    }
}
