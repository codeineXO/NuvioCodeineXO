package com.nuvio.app.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object InAppPlayerManager {
    private val _activeLaunch = MutableStateFlow<PlayerLaunch?>(null)
    val activeLaunch: StateFlow<PlayerLaunch?> = _activeLaunch.asStateFlow()

    private val _activeLaunchId = MutableStateFlow<Long?>(null)
    val activeLaunchId: StateFlow<Long?> = _activeLaunchId.asStateFlow()

    private val _isMiniPlayer = MutableStateFlow(false)
    val isMiniPlayer: StateFlow<Boolean> = _isMiniPlayer.asStateFlow()

    private val _offsetX = MutableStateFlow(0f)
    val offsetX: StateFlow<Float> = _offsetX.asStateFlow()

    private val _offsetY = MutableStateFlow(0f)
    val offsetY: StateFlow<Float> = _offsetY.asStateFlow()

    fun dragBy(dx: Float, dy: Float) {
        _offsetX.value += dx
        _offsetY.value += dy
    }

    fun setOffset(x: Float, y: Float) {
        _offsetX.value = x
        _offsetY.value = y
    }

    fun resetOffset() {
        _offsetX.value = 0f
        _offsetY.value = 0f
    }

    fun play(launchId: Long, launch: PlayerLaunch) {
        resetOffset()
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
        resetOffset()
    }

    fun getActiveLaunchId(): Long? = _activeLaunchId.value
}
