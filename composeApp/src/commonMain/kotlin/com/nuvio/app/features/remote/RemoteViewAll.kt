package com.nuvio.app.features.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * "View All" for the phone remote: the rows that show it on the PC (on Home and on a collection folder), the
 * phone's taps on it for those screens to open like their own buttons, and the page it opened.
 */
internal object RemoteViewAll {
    /** The key of Home's Continue Watching row; catalog rows use their section key, collection rows their id. */
    const val CONTINUE_WATCHING = "continue"

    data class Page(val title: String, val collectionId: String? = null)

    private val _page = MutableStateFlow<Page?>(null)

    /** The page a "View All" opened on the PC (a catalog, a collection's folders, Continue Watching), or null. */
    val page: StateFlow<Page?> = _page.asStateFlow()

    fun publishPage(page: Page?) {
        _page.value = page
    }

    private val _rows = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** The keys of the rows that show "View All" on the PC, by screen ("home", "folder"). */
    val rows: StateFlow<Map<String, Set<String>>> = _rows.asStateFlow()

    fun publishRows(screen: String, keys: Set<String>) {
        _rows.update { it + (screen to keys) }
    }

    private val _requests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Row keys tapped on the phone; each screen opens the ones it shows. */
    val requests: SharedFlow<String> = _requests.asSharedFlow()

    fun open(rowKey: String) {
        if (_rows.value.values.any { rowKey in it }) _requests.tryEmit(rowKey)
    }
}
