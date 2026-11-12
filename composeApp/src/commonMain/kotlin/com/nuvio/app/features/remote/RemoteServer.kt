package com.nuvio.app.features.remote

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface RemoteServerPlatform {
    fun loadEnabled(): Boolean
    fun loadPort(): Int?
    fun save(enabled: Boolean, port: Int)

    /** Starts serving on [port] and returns the LAN URL when available. */
    fun start(port: Int): String?
    fun stop()
    fun qrCode(url: String): ImageBitmap
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

    data class Connection(val url: String? = null, val error: String? = null)

    private val mutableConnection = MutableStateFlow(Connection())
    val connection: StateFlow<Connection> = mutableConnection.asStateFlow()

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
        mutableConnection.value = if (mutableEnabled.value) {
            val port = mutablePort.value
            runCatching { Connection(url = backend.start(port)) }
                .getOrElse { Connection(error = "Couldn't start on port $port: ${it.message}") }
        } else {
            Connection()
        }
    }
}
