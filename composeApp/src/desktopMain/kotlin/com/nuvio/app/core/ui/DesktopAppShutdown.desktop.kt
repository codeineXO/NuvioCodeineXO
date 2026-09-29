package com.nuvio.app.core.ui

import com.nuvio.app.core.diagnostics.SentryInitializer
import com.nuvio.app.features.discordrpc.DiscordPresenceManager
import com.nuvio.app.features.p2p.P2pStreamingEngine
import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Guaranteed process exit for window close / WM_CLOSE (MSI WixCloseApplications).
 *
 * exitApplication() only ends the Compose application scope; the JVM stays alive
 * while non-daemon threads or loaded native libs (player_bridge, libmpv,
 * WebView2, nuvio_engine) are alive. That leaves a windowless process in
 * Task Manager holding [INSTALLDIR]/app files locked, breaking MSI upgrades.
 *
 * Strategy: dispose the window immediately (so the installer sees success),
 * then bounded graceful cleanup on a daemon thread with a best-effort clean
 * exitProcess(0) (runs shutdown hooks), plus an unconditional Runtime.halt(0)
 * watchdog. halt() is required because exitProcess() blocks until shutdown
 * hooks finish, and a hook stuck in native code (engine teardown, WebView2)
 * would otherwise keep the windowless JVM alive forever.
 *
 * Total time to death is ~3s, inside the installer's close-wait window.
 */
object DesktopAppShutdown {
    @Volatile
    private var exitRequested = false

    private const val GRACEFUL_TIMEOUT_MS = 2000L
    private const val HALT_TIMEOUT_MS = 3000L

    fun requestExit(exitApplication: () -> Unit) {
        if (!markRequested()) {
            return
        }
        // 1. Close the window fast so WM_CLOSE succeeds.
        runCatching { exitApplication() }

        // 2. Bounded graceful cleanup, then hard exit.
        thread(name = "nuvio-app-exit", isDaemon = true) {
            runCatching {
                runBlocking {
                    withTimeoutOrNull(GRACEFUL_TIMEOUT_MS) {
                        runCatching { P2pStreamingEngine.shutdown() }
                        runCatching { DiscordPresenceManager.shutdown() }
                        runCatching { SentryInitializer.close() }
                        if (DesktopHostOs.current == DesktopHostOs.WINDOWS ||
                            DesktopHostOs.current == DesktopHostOs.LINUX
                        ) {
                            runCatching { NativePlayerBridge.shutdownWebView2Warmup() }
                        }
                    }
                }
            }
            exitProcess(0)
        }

        // 3. Watchdog: unconditional halt even if exitProcess() is stuck in a
        // shutdown hook. Never leave a windowless JVM behind.
        thread(name = "nuvio-app-exit-watchdog", isDaemon = true) {
            Thread.sleep(HALT_TIMEOUT_MS)
            Runtime.getRuntime().halt(0)
        }
    }

    @Synchronized
    private fun markRequested(): Boolean {
        if (exitRequested) return false
        exitRequested = true
        return true
    }
}
