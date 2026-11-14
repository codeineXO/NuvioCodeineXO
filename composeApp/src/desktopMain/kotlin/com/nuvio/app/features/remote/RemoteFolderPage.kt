package com.nuvio.app.features.remote

import com.nuvio.app.features.collection.FolderDetailRepository
import com.nuvio.app.features.collection.FolderViewMode
import com.nuvio.app.features.home.MetaPreview
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A collection folder's page on the phone, like the PC: a row per source, or tabs over a grid that loads more. */
internal object RemoteFolderPage {
    /** `{"k":"folder","title":…,"rows":[…]}`, or with `"tabs"`, `"tab"` and a grid's `"items"` in the tabbed view. */
    val messages: Flow<String> get() = combine(
        RemoteAppBridge.route,
        RemoteViewAll.rows,
        FolderDetailRepository.uiState,
    ) { route, viewAll, state ->
        buildJsonObject {
            put("k", "folder")
            if (route != "folder") return@buildJsonObject
            put("title", state.folder?.title ?: state.collectionTitle)
            if (state.viewMode == FolderViewMode.TABBED_GRID) {
                val items = state.selectedTab?.items.orEmpty()
                put("tab", state.selectedTabIndex)
                putJsonArray("tabs") { state.tabs.forEach { add(it.label) } }
                put("loading", state.selectedTab?.isLoading == true || state.selectedTabIsLoadingMore)
                put("more", state.selectedTabCanLoadMore)
                put("shape", shapeOf(items))
                putJsonArray("items") { items.forEach { poster(it, items.isLandscape()) } }
            } else {
                val keys = viewAll["folder"].orEmpty()
                putJsonArray("rows") {
                    FolderDetailRepository.getRowSlots(state).forEach { slot ->
                        addJsonObject {
                            val section = slot.section?.takeIf { it.items.isNotEmpty() }
                            if (section != null) {
                                catalogRow(section, key = slot.key, title = slot.tab.label, viewAll = slot.key in keys)
                            } else {
                                put("key", slot.key)
                                put("title", slot.tab.label)
                                put("loading", slot.tab.isLoading)
                                put("error", slot.tab.error)
                                putJsonArray("items") {}
                            }
                        }
                    }
                }
            }
        }.toString()
    }.distinctUntilChanged()

    /** `folderTab` (`index`) or `folderMore` (the grid's next page). */
    fun handle(cmd: String, json: JsonObject) {
        if (RemoteAppBridge.route.value != "folder") return
        when (cmd) {
            "folderTab" -> json["index"]?.jsonPrimitive?.intOrNull?.let { index ->
                SwingUtilities.invokeLater { FolderDetailRepository.selectTab(index) }
            }
            "folderMore" -> SwingUtilities.invokeLater { FolderDetailRepository.loadMoreSelectedTab() }
        }
    }

    /** A poster on the folder page: in a row by its `row` key, or in the grid (no row). */
    fun posterAt(row: String?, item: Int): MetaPreview? {
        if (RemoteAppBridge.route.value != "folder") return null
        val state = FolderDetailRepository.uiState.value
        val items = if (row == null) state.selectedTab?.items
        else FolderDetailRepository.getRowSlots(state).firstOrNull { it.key == row }?.section?.items
        return items?.getOrNull(item)
    }
}
