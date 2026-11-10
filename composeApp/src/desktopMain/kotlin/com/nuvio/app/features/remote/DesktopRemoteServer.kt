package com.nuvio.app.features.remote

import com.nuvio.app.core.storage.DesktopStorage
import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.net.DatagramSocket
import java.net.InetAddress

/** Serves the phone remote page on the LAN and takes button presses over a WebSocket. */
internal object DesktopRemoteServer : RemoteServerPlatform {
    private const val ENABLED_KEY = "enabled"
    private const val PORT_KEY = "port"
    private val store = DesktopStorage.store("nuvio_remote_settings")
    private var server: EmbeddedServer<*, *>? = null

    fun install() = RemoteServer.install(this)

    override fun loadEnabled(): Boolean = store.getBoolean(ENABLED_KEY) ?: false

    override fun loadPort(): Int? = store.getInt(PORT_KEY)

    override fun save(enabled: Boolean, port: Int) {
        store.putBoolean(ENABLED_KEY, enabled)
        store.putInt(PORT_KEY, port)
    }

    @Synchronized
    override fun start(port: Int): String {
        val engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                get("/") { call.respondText(page(), ContentType.Text.Html) }
                webSocket("/ws") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) RemoteKeyInjector.handle(frame.readText())
                    }
                }
            }
        }
        engine.start(wait = false)
        server = engine
        return "http://${lanAddress()}:$port"
    }

    @Synchronized
    override fun stop() {
        server?.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        server = null
    }

    /** Read on every request, so edits to the page show up on a phone refresh. */
    private fun page(): String =
        javaClass.getResource("/remote/index.html")?.readText() ?: "Remote page missing"

    /** The address the phone should use: the interface that routes to the internet. */
    private fun lanAddress(): String = runCatching {
        DatagramSocket().use {
            it.connect(InetAddress.getByName("8.8.8.8"), 53)
            it.localAddress.hostAddress
        }
    }.getOrDefault("localhost")
}
