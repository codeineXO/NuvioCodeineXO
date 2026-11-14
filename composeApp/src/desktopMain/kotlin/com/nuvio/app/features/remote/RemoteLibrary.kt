package com.nuvio.app.features.remote

import com.nuvio.app.features.cloud.CloudLibraryRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.features.library.LibraryDisplaySettingsRepository
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.buildLibraryVerticalProjection
import com.nuvio.app.features.library.isRemoteTrackingSource
import com.nuvio.app.features.library.LibraryLayoutMode
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.LibrarySortOption
import com.nuvio.app.features.library.LibrarySourceMode
import com.nuvio.app.features.library.availableLibrarySortOptions
import com.nuvio.app.features.library.effectiveLibrarySortOption
import com.nuvio.app.features.library.librarySectionTitle
import com.nuvio.app.features.library.sortLibrarySections
import com.nuvio.app.features.library.toMetaPreview
import com.nuvio.app.features.remote.RemoteLibraryActions.Action
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * The Library on the phone, like the PC's: Saved (its sections, in the PC's sort order and layout) and Cloud (the
 * cloud accounts' files, played on the PC), with the PC's sort, layout and Downloads buttons.
 */
internal object RemoteLibrary {
    /** `{"k":"library","title":…,"sort":…,"sorts":[…],"layout":…,"rows":[…],"cloud":{…},"labels":{…}}`. */
    val messages: Flow<String> get() = combine(
        LibraryRepository.uiState,
        LibraryDisplaySettingsRepository.uiState,
        CloudLibraryRepository.uiState,
    ) { state, display, cloud ->
        val sort = effectiveLibrarySortOption(display.sortOption, state.sourceMode)
        val sections = sortLibrarySections(state.sections, sort, state.sourceMode).filter { it.items.isNotEmpty() }
        val titles = sections.map { librarySectionTitle(it.type, it.displayTitle) }
        // The grid layout, like the PC's: a local Library is one grid of every title; a tracking source shows one
        // list at a time (the phone picks it from `rows`). Both filter by type.
        val tracking = state.sourceMode.isRemoteTrackingSource
        val grid = if (tracking) emptyList() else {
            buildLibraryVerticalProjection(state.sections, state.sourceMode, null, null, sort).entries.map { it.item }
        }
        val categories = (sections.flatMap { it.items } + grid).map { it.category() }.distinct()
        val sorts = availableLibrarySortOptions(state.sourceMode).map { it.name to sortLabel(it) }
        val title = getString(
            when (state.sourceMode) {
                LibrarySourceMode.LOCAL -> Res.string.library_title
                LibrarySourceMode.TRAKT -> Res.string.library_trakt_title
                LibrarySourceMode.SIMKL -> Res.string.library_simkl_title
                LibrarySourceMode.MDBLIST -> Res.string.library_mdblist_title
            },
        )
        val labels = listOf(
            "saved" to getString(Res.string.library_source_saved),
            "cloud" to getString(Res.string.library_source_cloud),
            "connectTitle" to getString(Res.string.cloud_library_connect_title),
            "connectMessage" to getString(Res.string.cloud_library_connect_message),
            "list" to getString(Res.string.library_filter_list),
            "allTypes" to getString(Res.string.library_filter_all_types),
        )
        buildJsonObject {
            put("k", "library")
            put("title", title)
            putJsonObject("labels") { labels.forEach { (key, value) -> put(key, value) } }
            put("loading", state.isLoading || !state.isLoaded)
            put("error", state.errorMessage)
            put("layout", if (display.layoutMode == LibraryLayoutMode.VERTICAL) "grid" else "rows")
            put("tracking", tracking)
            putJsonObject("typeLabels") { categories.forEach { put(it, localizedMediaTypeLabel(it)) } }
            putJsonObject("grid") { putTitles(grid) }
            put("sort", sort.name)
            putJsonArray("sorts") { sorts.forEach { (key, label) -> addJsonObject { put("key", key); put("label", label) } } }
            putJsonArray("rows") {
                sections.forEachIndexed { index, section ->
                    addJsonObject {
                        put("key", section.type)
                        put("title", titles[index])
                        putTitles(section.items)
                    }
                }
            }
            putJsonObject("cloud") {
                put("connected", cloud.hasConnectedProvider)
                put("loading", cloud.isRefreshing || cloud.providers.any { it.isLoading })
                put("error", cloud.providers.firstNotNullOfOrNull { it.errorMessage })
                putJsonArray("items") {
                    cloud.items.forEach { item ->
                        addJsonObject {
                            put("key", item.stableKey)
                            put("name", item.name)
                            put("provider", item.providerName)
                            put("status", item.status)
                            put("size", item.sizeBytes)
                            put("progress", item.progressFraction)
                            putJsonArray("files") {
                                item.playableFiles.forEach { file ->
                                    addJsonObject { put("key", file.stableKey); put("name", file.name); put("size", file.sizeBytes) }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }.distinctUntilChanged()

    /** `libraryLayout`, `librarySort` (`value`), `libraryDownloads`, `cloudLoad` or `cloudPlay` (`item`, `file`). */
    fun handle(cmd: String, json: JsonObject) {
        fun text(name: String) = json[name]?.jsonPrimitive?.contentOrNull
        SwingUtilities.invokeLater {
            when (cmd) {
                "libraryLayout" -> LibraryDisplaySettingsRepository.setLayoutMode(
                    if (LibraryDisplaySettingsRepository.uiState.value.layoutMode == LibraryLayoutMode.VERTICAL) {
                        LibraryLayoutMode.HORIZONTAL
                    } else LibraryLayoutMode.VERTICAL,
                )
                "librarySort" -> LibrarySortOption.entries.firstOrNull { it.name == text("value") }
                    ?.let(LibraryDisplaySettingsRepository::setSortOption)
                "libraryDownloads" -> RemoteLibraryActions.send(Action.Downloads)
                "cloudLoad" -> CloudLibraryRepository.ensureLoaded()
                "cloudPlay" -> {
                    val item = text("item") ?: return@invokeLater
                    val file = text("file") ?: return@invokeLater
                    RemoteLibraryActions.send(Action.PlayCloud(item, file))
                }
            }
        }
    }

    /** A title in a section by the section's `row` key, or with no `row`, in the local Library's grid. */
    fun posterAt(row: String?, item: Int): MetaPreview? {
        val state = LibraryRepository.uiState.value
        val sort = effectiveLibrarySortOption(LibraryDisplaySettingsRepository.uiState.value.sortOption, state.sourceMode)
        val items = if (row == null) {
            buildLibraryVerticalProjection(state.sections, state.sourceMode, null, null, sort).entries.map { it.item }
        } else {
            sortLibrarySections(state.sections, sort, state.sourceMode).firstOrNull { it.type == row }?.items
        }
        return items?.getOrNull(item)?.toMetaPreview()
    }

    /** Titles as posters, with each one's type (`categories`) for the type filter. */
    private fun JsonObjectBuilder.putTitles(items: List<LibraryItem>) {
        val previews = items.map { it.toMetaPreview() }
        put("shape", shapeOf(previews))
        putJsonArray("items") { previews.forEach { poster(it, previews.isLandscape()) } }
        putJsonArray("categories") { items.forEach { add(it.category()) } }
    }

    private fun LibraryItem.category(): String = (mediaCategory ?: type).trim().lowercase()

    private suspend fun sortLabel(option: LibrarySortOption): String = getString(
        when (option) {
            LibrarySortOption.DEFAULT -> Res.string.library_sort_provider_order
            LibrarySortOption.ADDED_DESC -> Res.string.library_sort_added_desc
            LibrarySortOption.ADDED_ASC -> Res.string.library_sort_added_asc
            LibrarySortOption.RELEASED_DESC -> Res.string.library_sort_released_desc
            LibrarySortOption.RELEASED_ASC -> Res.string.library_sort_released_asc
            LibrarySortOption.TITLE_ASC -> Res.string.library_sort_title_asc
            LibrarySortOption.TITLE_DESC -> Res.string.library_sort_title_desc
        },
    )
}
