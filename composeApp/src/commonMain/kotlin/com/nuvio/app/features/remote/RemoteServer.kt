package com.nuvio.app.features.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface RemoteServerPlatform {
    fun loadEnabled(): Boolean
    fun loadPort(): Int?
    fun save(enabled: Boolean, port: Int)

    /** Starts serving on [port]; returns the URL to show, or throws if the port can't be used. */
    fun start(port: Int): String
    fun stop()
}

/** Phone remote web server. Desktop installs the platform; other targets show no Remote settings. */
internal object RemoteServer {
    const val DEFAULT_PORT = 8090
    val PORTS = 1024..65535

    var platform: RemoteServerPlatform? = null
        private set

    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    private val mutablePort = MutableStateFlow(DEFAULT_PORT)
    val port: StateFlow<Int> = mutablePort.asStateFlow()

    /** URL while running, or the error from the last start attempt. */
    private val mutableStatus = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = mutableStatus.asStateFlow()

    fun install(backend: RemoteServerPlatform) {
        platform = backend
        mutablePort.value = backend.loadPort() ?: DEFAULT_PORT
        mutableEnabled.value = backend.loadEnabled()
        if (mutableEnabled.value) restart()
    }

    fun setEnabled(enabled: Boolean) {
        mutableEnabled.value = enabled
        platform?.save(enabled, mutablePort.value)
        restart()
    }

    fun setPort(port: Int) {
        if (port !in PORTS || port == mutablePort.value) return
        mutablePort.value = port
        platform?.save(mutableEnabled.value, port)
        if (mutableEnabled.value) restart()
    }

    private fun restart() {
        val backend = platform ?: return
        backend.stop()
        mutableStatus.value = if (mutableEnabled.value) {
            runCatching { backend.start(mutablePort.value) }
                .getOrElse { "Couldn't start on port ${mutablePort.value}: ${it.message}" }
        } else {
            null
        }
    }
}
