package com.nuvio.app.features.remote

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nuvio.app.DesktopRemoteCursorController
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.ui.RemoteNavigationDispatcher
import com.nuvio.app.features.anime.AnimeFillerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** Serves the phone remote page on the LAN and takes button presses over a WebSocket. */
internal object DesktopRemoteServer : RemoteServerPlatform {
    private const val ENABLED_KEY = "enabled"
    private const val PORT_KEY = "port"
    private val store = DesktopStorage.store("nuvio_remote_settings")
    private var server: EmbeddedServer<*, *>? = null

    fun install() {
        AnimeFillerSettingsRepository.ensureLoaded()
        RemoteServer.install(this)
    }

    override fun loadEnabled(): Boolean = store.getBoolean(ENABLED_KEY) ?: false

    override fun loadPort(): Int? = store.getInt(PORT_KEY)

    override fun save(enabled: Boolean, port: Int) {
        store.putBoolean(ENABLED_KEY, enabled)
        store.putInt(PORT_KEY, port)
    }

    @Synchronized
    override fun start(port: Int): String? {
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
                    val cardMenus = RemoteCardMenuSession()
                    val player = launch {
                        combine(
                            RemotePlayerLink.state,
                            PlayerSettingsRepository.uiState.map { it.grainFilterEnabled }.distinctUntilChanged(),
                        ) { state, _ ->
                            RemotePlayerPanels.message(state, RemotePlayerLink.audioTracks())
                        }.distinctUntilChanged().collect { send(Frame.Text(it)) }
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
                            if (frame is Frame.Text) handle(frame.readText(), cardMenus) { send(Frame.Text(it)) }
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
        return lanAddress()?.let { "http://$it:$port" }
    }

    @Synchronized
    override fun stop() {
        server?.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        server = null
    }

    override fun qrCode(url: String): ImageBitmap {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 240, 240)
        val rowBytes = matrix.width * 4
        val pixels = ByteArray(rowBytes * matrix.height) { 0xff.toByte() }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    val offset = y * rowBytes + x * 4
                    pixels.fill(0, offset, offset + 3)
                }
            }
        }
        val info = ImageInfo(matrix.width, matrix.height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        return Bitmap().apply { check(installPixels(info, pixels, rowBytes)) }.asComposeImageBitmap()
    }

    /**
     * One phone message: `{"cmd":"player","type":"selectSource","value":3}` goes to the player like an on-screen
     * control, `{"cmd":"click","target":"sources"}` clicks that on-screen control, `{"cmd":"close"}` closes the PC's
     * open panel, anything else is a key press.
     */
    private suspend fun handle(message: String, cardMenus: RemoteCardMenuSession, sendMenu: suspend (String) -> Unit) {
        val json = runCatching { Json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return
        val cmd = json["cmd"]?.jsonPrimitive?.contentOrNull ?: return
        when (cmd) {
            "cardMenu" -> {
                cardMenus.open(json)?.let { sendMenu(it) }
                return
            }
            "cardMenuAction" -> {
                cardMenus.select(json)
                return
            }
        }
        if (cmd == "exitDpad") {
            SwingUtilities.invokeLater { RemoteNavigationDispatcher.exitKeyboardNavigation(discardPosterOrigins = true) }
            return
        }
        DesktopRemoteCursorController.hideCursor()
        when (cmd) {
            "dpadBack" -> RemoteAppBridge.send(RemoteAppBridge.Command.Back(fromDpad = true))
            "okLong" -> SwingUtilities.invokeLater { RemoteNavigationDispatcher.openContextMenu() }
            "tab", "open", "folder", "resume", "search" -> RemoteHome.handle(cmd, json)
            "fillerDetection", "hideFillers" -> RemoteDetails.handle(cmd, json)
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

    private fun lanAddress(): String? {
        val routedAddress = runCatching {
            DatagramSocket().use {
                it.connect(InetAddress.getByName("8.8.8.8"), 53)
                it.localAddress
            }
        }.getOrNull()
        if (routedAddress is Inet4Address && !routedAddress.isAnyLocalAddress && !routedAddress.isLoopbackAddress) {
            return routedAddress.hostAddress
        }
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress && !it.isAnyLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
