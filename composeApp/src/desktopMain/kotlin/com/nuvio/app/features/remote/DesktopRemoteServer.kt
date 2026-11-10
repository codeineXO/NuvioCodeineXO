package com.nuvio.app.features.remote

import com.nuvio.app.core.storage.DesktopStorage
import com.sun.net.httpserver.HttpServer
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/** Serves the phone remote page on the LAN with the JDK's built-in HTTP server. */
internal object DesktopRemoteServer : RemoteServerPlatform {
    private const val ENABLED_KEY = "enabled"
    private const val PORT_KEY = "port"
    private val store = DesktopStorage.store("nuvio_remote_settings")
    private var server: HttpServer? = null

    fun install() = RemoteServer.install(this)

    override fun loadEnabled(): Boolean = store.getBoolean(ENABLED_KEY) ?: false

    override fun loadPort(): Int? = store.getInt(PORT_KEY)

    override fun save(enabled: Boolean, port: Int) {
        store.putBoolean(ENABLED_KEY, enabled)
        store.putInt(PORT_KEY, port)
    }

    @Synchronized
    override fun start(port: Int): String {
        val http = HttpServer.create(InetSocketAddress(port), 0)
        http.createContext("/") { exchange ->
            val body = PAGE.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        http.start()
        server = http
        return "http://${lanAddress()}:$port"
    }

    @Synchronized
    override fun stop() {
        server?.stop(0)
        server = null
    }

    /** The address the phone should use: the interface that routes to the internet. */
    private fun lanAddress(): String = runCatching {
        DatagramSocket().use {
            it.connect(InetAddress.getByName("8.8.8.8"), 53)
            it.localAddress.hostAddress
        }
    }.getOrDefault("localhost")

    private val PAGE = """
        <!doctype html>
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Nuvio Remote</title></head>
        <body style="font-family:system-ui;display:grid;place-items:center;min-height:100vh;margin:0;background:#111;color:#eee">
        <h1>Hello world</h1>
        </body></html>
    """.trimIndent()
}
