package com.nuvio.app.features.remote

import com.nuvio.app.core.storage.DesktopStorage
import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.net.DatagramSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                // no-store: the phone always gets the current page, never a cached older one.
                get("/") {
                    call.response.header("Cache-Control", "no-store")
                    call.respondText(page(), ContentType.Text.Html)
                }
                get("/cuppa.css") {
                    call.response.header("Cache-Control", "no-store")
                    call.respondText(resource("cuppa.css"), ContentType.Text.CSS)
                }
                webSocket("/ws") {
                    val player = launch {
                        RemotePlayerLink.state
                            .map { RemotePlayerPanels.message(it, RemotePlayerLink.audioTracks()) }
                            .distinctUntilChanged()
                            .collect { send(Frame.Text(it)) }
                    }
                    // Browsing: Home's rows and the search results.
                    val home = launch { RemoteHome.homeMessages.collect { send(Frame.Text(it)) } }
                    val search = launch { RemoteHome.searchMessages.collect { send(Frame.Text(it)) } }
                    // Following the PC into a title, its sources and the player.
                    val pages = launch {
                        launch { RemoteDetails.routeMessages.collect { send(Frame.Text(it)) } }
                        launch { RemoteDetails.detailsMessages.collect { send(Frame.Text(it)) } }
                        launch { RemoteDetails.streamsMessages.collect { send(Frame.Text(it)) } }
                    }
                    // The seek bar: position once a second while the player is open.
                    val progress = launch {
                        while (true) {
                            RemotePlayerLink.progress()?.let { (position, duration) ->
                                send(Frame.Text("""{"k":"time","position":$position,"duration":$duration}"""))
                            }
                            delay(1_000)
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) handle(frame.readText())
                        }
                    } finally {
                        player.cancel()
                        progress.cancel()
                        home.cancel()
                        search.cancel()
                        pages.cancel()
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

    /**
     * One phone message: `{"cmd":"player","type":"selectSource","value":3}` goes to the player like an on-screen
     * control, `{"cmd":"click","target":"sources"}` clicks that on-screen control, `{"cmd":"close"}` closes the PC's
     * open panel, anything else is a key press.
     */
    private fun handle(message: String) {
        val json = runCatching { Json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return
        when (val cmd = json["cmd"]?.jsonPrimitive?.contentOrNull) {
            "tab", "open", "folder", "resume", "search" -> RemoteHome.handle(cmd, json)
            "back", "play", "episode", "saved", "watched", "stream", "reloadStreams", "fromStart" -> RemoteDetails.handle(cmd, json)
            "player" -> {
                val type = json["type"]?.jsonPrimitive?.contentOrNull ?: return
                val value = json["value"]?.jsonPrimitive?.doubleOrNull ?: return
                RemotePlayerLink.send(type, value)
            }
            "click" -> RemotePlayerLink.click(json["target"]?.jsonPrimitive?.contentOrNull ?: return)
            "close" -> RemotePlayerLink.closePanels()
            else -> RemoteKeyInjector.handle(json)
        }
    }

    /**
     * Read on every request, so edits to the page show up on a phone refresh. The player's own icon set is copied
     * in from its controls page, so the phone shows the same icons.
     */
    private fun page(): String {
        val html = resource("index.html")
        val controls = javaClass.getResource("/player-ui/controls.html")?.readText().orEmpty()
        val start = controls.indexOf("<svg width=\"0\"")
        val end = controls.indexOf("</svg>", start)
        val icons = if (start >= 0 && end > start) controls.substring(start, end + "</svg>".length) else ""
        return html.replace("<!-- player icons -->", icons)
    }

    /** A file of the remote page from resources/remote, read on every request so edits show up on a refresh. */
    private fun resource(name: String): String =
        javaClass.getResource("/remote/$name")?.readText() ?: "/* $name missing */"

    /** The address the phone should use: the interface that routes to the internet. */
    private fun lanAddress(): String = runCatching {
        DatagramSocket().use {
            it.connect(InetAddress.getByName("8.8.8.8"), 53)
            it.localAddress.hostAddress
        }
    }.getOrDefault("localhost")
}
