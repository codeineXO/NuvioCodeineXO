package com.nuvio.app.core.ui

import com.nuvio.app.AppScreenTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object AppPresenceState {
    private val _playerSnapshot = MutableStateFlow<PresenceSnapshot.Player?>(null)
    private val _navigationSnapshot = MutableStateFlow<PresenceSnapshot?>(null)
    private val _current = MutableStateFlow<PresenceSnapshot?>(null)
    val current: StateFlow<PresenceSnapshot?> = _current.asStateFlow()

    fun publish(snapshot: PresenceSnapshot?) {
        if (snapshot is PresenceSnapshot.Player) {
            _playerSnapshot.value = snapshot
        } else {
            _navigationSnapshot.value = snapshot
        }
        _current.value = _playerSnapshot.value ?: _navigationSnapshot.value
    }

    fun clearPlayer() {
        _playerSnapshot.value = null
        _current.value = _navigationSnapshot.value
    }

    fun reset() {
        _playerSnapshot.value = null
        _navigationSnapshot.value = null
        _current.value = null
    }
}

internal sealed interface PresenceSnapshot {
    data class Tab(val tab: AppScreenTab) : PresenceSnapshot

    data class Details(
        val title: String,
        val posterUrl: String? = null,
        val imdbId: String? = null,
    ) : PresenceSnapshot

    data class StreamSelection(
        val title: String,
        val posterUrl: String? = null,
        val episodeLabel: String? = null,
        val imdbId: String? = null,
    ) : PresenceSnapshot

    data class Player(
        val title: String,
        val episodeLabel: String?,
        val posterUrl: String?,
        val episodeThumbnailUrl: String? = null,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val imdbId: String? = null,
        val isSeries: Boolean = false,
    ) : PresenceSnapshot
}
