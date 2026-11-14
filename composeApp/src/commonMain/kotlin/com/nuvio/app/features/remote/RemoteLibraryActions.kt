package com.nuvio.app.features.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The phone's Library buttons that need the Library screen: Downloads, and playing a cloud file. */
internal object RemoteLibraryActions {
    sealed interface Action {
        data object Downloads : Action
        data class PlayCloud(val itemKey: String, val fileKey: String) : Action
    }

    private val _actions = MutableSharedFlow<Action>(extraBufferCapacity = 1)
    val actions: SharedFlow<Action> = _actions.asSharedFlow()

    fun send(action: Action) {
        _actions.tryEmit(action)
    }
}
