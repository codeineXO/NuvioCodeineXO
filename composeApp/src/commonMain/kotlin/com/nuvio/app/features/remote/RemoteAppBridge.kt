package com.nuvio.app.features.remote

import com.nuvio.app.AppScreenTab
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the phone remote asks the app to do, handled by the app shell exactly like the matching click (a sidebar
 * tab, a poster, a Continue Watching card). Also carries the few things the remote shows that only live in screens.
 */
object RemoteAppBridge {
    sealed interface Command {
        data class SelectTab(val tab: AppScreenTab) : Command
        data object Shuffle : Command
        data class OpenTitle(val meta: MetaPreview) : Command
        data class OpenFolder(val collectionId: String, val folderId: String, val title: String) : Command
        data class Resume(val item: ContinueWatchingItem) : Command
        data class Search(val query: String) : Command
        data class Back(val fromDpad: Boolean = false) : Command
    }

    /** Which page the PC shows: "tabs", "details", "streams", "player" or "other". The phone follows it. */
    private val _route = MutableStateFlow("tabs")
    val route: StateFlow<String> = _route.asStateFlow()

    fun publishRoute(route: String) {
        _route.value = route
    }

    /** The open details page's own actions: its Play button, an episode's play, watched and library toggles. */
    class DetailsActions(
        val key: String,
        val playLabel: String,
        val isSaved: Boolean,
        val isWatched: Boolean,
        val play: () -> Unit,
        val playEpisode: (com.nuvio.app.features.details.MetaVideo) -> Unit,
        val toggleSaved: () -> Unit,
        val toggleWatched: () -> Unit,
        val preferredSeasonNumber: Int?,
        val preferredEpisodeNumber: Int?,
    )

    private val _details = MutableStateFlow<DetailsActions?>(null)
    val details: StateFlow<DetailsActions?> = _details.asStateFlow()

    fun publishDetails(actions: DetailsActions) {
        _details.value = actions
    }

    fun clearDetails(key: String) {
        if (_details.value?.key == key) _details.value = null
    }

    /** The open sources page: what it is for, and its own "play this source" and reload actions. */
    class StreamsActions(
        val key: String,
        val title: String,
        val logo: String?,
        val background: String?,
        val episode: String?,
        /** Where playback would resume, when there is saved progress; the page offers Resume or Start from beginning. */
        val resumeMs: Long?,
        val startFromBeginning: Boolean,
        val setStartFromBeginning: (Boolean) -> Unit,
        val select: (com.nuvio.app.features.streams.StreamItem) -> Unit,
        val reload: () -> Unit,
    )

    private val _streams = MutableStateFlow<StreamsActions?>(null)
    val streams: StateFlow<StreamsActions?> = _streams.asStateFlow()

    fun publishStreams(actions: StreamsActions) {
        _streams.value = actions
    }

    fun clearStreams(key: String) {
        if (_streams.value?.key == key) _streams.value = null
    }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    fun send(command: Command) {
        _commands.tryEmit(command)
    }

    private val _continueWatching = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())

    /** The Continue Watching row as the Home screen shows it. */
    val continueWatching: StateFlow<List<ContinueWatchingItem>> = _continueWatching.asStateFlow()

    fun publishContinueWatching(items: List<ContinueWatchingItem>) {
        _continueWatching.value = items
    }

    private val _searchQuery = MutableStateFlow<String?>(null)

    /** A query typed on the phone, waiting for the Search screen to take it. */
    val searchQuery: StateFlow<String?> = _searchQuery.asStateFlow()

    fun requestSearch(query: String) {
        _searchQuery.value = query
        send(Command.Search(query))
    }

    /** The Search screen took the query into its field. */
    fun consumeSearchQuery() {
        _searchQuery.value = null
    }
}
