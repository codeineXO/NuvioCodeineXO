package com.nuvio.app.features.player

import com.nuvio.app.core.ui.AppPresenceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the active player alive across navigation, and whether it is minimized to the docked mini player. */
internal object InAppPlayerManager {
    private val _activeLaunch = MutableStateFlow<PlayerLaunch?>(null)
    val activeLaunch: StateFlow<PlayerLaunch?> = _activeLaunch.asStateFlow()

    private val _activeLaunchId = MutableStateFlow<Long?>(null)
    val activeLaunchId: StateFlow<Long?> = _activeLaunchId.asStateFlow()

    private val _isMiniPlayer = MutableStateFlow(false)
    val isMiniPlayer: StateFlow<Boolean> = _isMiniPlayer.asStateFlow()

    fun play(launchId: Long, launch: PlayerLaunch) {
        if (_activeLaunchId.value == launchId && _activeLaunch.value != null) {
            _isMiniPlayer.value = false
            return
        }
        val previousLaunchId = _activeLaunchId.value
        if (previousLaunchId != null && previousLaunchId != launchId) {
            PlayerLaunchStore.remove(previousLaunchId)
        }
        _activeLaunchId.value = launchId
        _activeLaunch.value = launch
        _isMiniPlayer.value = false
    }

    fun isMinimized(launchId: Long): Boolean {
        return _isMiniPlayer.value && _activeLaunchId.value == launchId
    }

    fun minimize() {
        if (_activeLaunch.value != null) {
            _isMiniPlayer.value = true
        }
    }

    fun expand() {
        if (_activeLaunch.value != null) {
            _isMiniPlayer.value = false
        }
    }

    fun close() {
        val launchId = _activeLaunchId.value
        if (launchId != null) {
            PlayerLaunchStore.remove(launchId)
        }
        _activeLaunchId.value = null
        _activeLaunch.value = null
        _isMiniPlayer.value = false
        AppPresenceState.clearPlayer()
    }

    fun getActiveLaunchId(): Long? = _activeLaunchId.value
}
