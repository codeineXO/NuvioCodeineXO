package com.nuvio.app.features.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.nuvio.app.core.ui.ActionMenuAction
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.isDesktop
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

internal object RemoteCardMenus {
    sealed interface Target {
        data class Poster(val preview: MetaPreview) : Target
        data class ContinueWatching(val item: ContinueWatchingItem) : Target
        data class Episode(val detailsKey: String, val episode: MetaVideo) : Target
        data class Source(val streamsKey: String, val stream: StreamItem) : Target
    }

    private class Request(val target: Target) {
        val response = CompletableDeferred<List<ActionMenuAction>>()
    }

    private val requests = MutableStateFlow<List<Request>>(emptyList())

    suspend fun actions(target: Target): List<ActionMenuAction> {
        val request = Request(target)
        requests.update { it + request }
        return try {
            withTimeoutOrNull(2_000) { request.response.await() }.orEmpty()
        } finally {
            requests.update { it - request }
        }
    }

    @Composable
    fun Provide(actions: @Composable (Target) -> List<ActionMenuAction>?) {
        if (!isDesktop) return
        val pending by requests.collectAsState()
        pending.forEach { request ->
            key(request) {
                val items = actions(request.target)
                if (items != null) SideEffect { request.response.complete(items) }
            }
        }
    }
}
