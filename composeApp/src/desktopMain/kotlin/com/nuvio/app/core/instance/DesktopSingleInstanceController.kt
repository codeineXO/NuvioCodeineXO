package com.nuvio.app.core.instance

import androidx.compose.ui.window.WindowState
import com.nuvio.app.core.deeplink.handleAppUrl
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.desktop.DesktopHostOs
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object DesktopSingleInstanceController {
    private const val LOCK_FILE_NAME = "app.lock"
    private const val PORT_FILE_NAME = "app.port"
    private const val LOOPBACK_HOST = "127.0.0.1"

    private val isReleased = AtomicBoolean(false)
    private val pendingActivation = AtomicBoolean(false)
    private val shutdownHookRegistered = AtomicBoolean(false)

    @Volatile
    private var lockRaf: RandomAccessFile? = null
    @Volatile
    private var lockFileChannel: FileChannel? = null
    @Volatile
    private var fileLock: FileLock? = null
    @Volatile
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var authToken: String? = null

    @Volatile
    private var activeLockDir: Path? = null

    @Volatile
    private var window: Window? = null
    @Volatile
    private var windowState: WindowState? = null

    internal var onPingReceivedForTest: ((List<String>) -> Unit)? = null

    /**
     * Checks if another instance is already running.
     * If this is the primary instance, acquires the lock and starts listening on loopback.
     * If an instance is already running, forwards [args] to it and invokes [onExit].
     *
     * @return true if this process is the primary instance and should proceed with startup.
     */
    fun startOrForward(
        args: Array<String>,
        lockDir: Path = DesktopStorage.rootDir,
        onExit: (Int) -> Unit = ::exitProcess,
    ): Boolean {
        if (System.getProperty("nuvio.singleinstance.disabled", "false").toBoolean()) {
            return true
        }

        activeLockDir = lockDir
        val lockFile = lockDir.resolve(LOCK_FILE_NAME).toFile()
        val portFile = lockDir.resolve(PORT_FILE_NAME).toFile()

        // 1. First attempt to acquire lock directly
        if (tryAcquireLock(lockFile, portFile, lockDir)) {
            return true
        }

        // 2. Lock is held by another instance. Retry loop to connect & forward or re-acquire
        val maxAttempts = 15
        for (attempt in 1..maxAttempts) {
            if (tryForward(portFile, args)) {
                onExit(0)
                return false
            }

            // In case the existing instance was shutting down and just released the lock
            if (tryAcquireLock(lockFile, portFile, lockDir)) {
                return true
            }

            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                break
            }
        }

        // Lock remains held but IPC was unreachable; terminate secondary instance immediately
        onExit(0)
        return false
    }

    private fun tryAcquireLock(lockFile: File, portFile: File, lockDir: Path): Boolean {
        var raf: RandomAccessFile? = null
        var channel: FileChannel? = null
        var lock: FileLock? = null
        var server: ServerSocket? = null

        return try {
            Files.createDirectories(lockDir)
            raf = RandomAccessFile(lockFile, "rw")
            channel = raf.channel
            lock = channel.tryLock()

            if (lock == null) {
                runCatching { channel.close() }
                runCatching { raf.close() }
                return false
            }

            // Bind local loopback server socket on an ephemeral port
            server = ServerSocket()
            server.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), 0), 50)
            val port = server.localPort
            val token = UUID.randomUUID().toString()
            val pid = ProcessHandle.current().pid()

            val portContent = "$port\n$token\n$pid\n"
            Files.writeString(
                portFile.toPath(),
                portContent,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )

            synchronized(this) {
                this.lockRaf = raf
                this.lockFileChannel = channel
                this.fileLock = lock
                this.serverSocket = server
                this.authToken = token
                this.isReleased.set(false)
            }

            startListener(server, token)
            registerShutdownHookOnce()
            true
        } catch (_: Exception) {
            runCatching { server?.close() }
            runCatching { lock?.release() }
            runCatching { channel?.close() }
            runCatching { raf?.close() }
            false
        }
    }

    private fun tryForward(portFile: File, args: Array<String>): Boolean {
        if (!portFile.exists()) return false

        val (port, token) = readPortAndToken(portFile) ?: return false

        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), port), 1000)
                socket.soTimeout = 2000
                val writer = socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8)
                val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)

                writer.write(token)
                writer.newLine()

                writer.write(args.size.toString())
                writer.newLine()

                for (arg in args) {
                    val encoded = Base64.getUrlEncoder().encodeToString(arg.toByteArray(StandardCharsets.UTF_8))
                    writer.write(encoded)
                    writer.newLine()
                }
                writer.flush()

                val ack = reader.readLine()
                ack == "OK"
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readPortAndToken(portFile: File): Pair<Int, String>? {
        return try {
            val lines = Files.readAllLines(portFile.toPath(), StandardCharsets.UTF_8)
            if (lines.size < 2) return null
            val port = lines[0].trim().toIntOrNull() ?: return null
            val token = lines[1].trim()
            if (token.isEmpty()) return null
            Pair(port, token)
        } catch (_: Exception) {
            null
        }
    }

    private fun startListener(server: ServerSocket, expectedToken: String) {
        thread(name = "nuvio-single-instance-listener", isDaemon = true) {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                thread(name = "nuvio-single-instance-handler", isDaemon = true) {
                    handleClient(client, expectedToken)
                }
            }
        }
    }

    private fun handleClient(client: Socket, expectedToken: String) {
        client.use { socket ->
            try {
                socket.soTimeout = 3000
                val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                val writer = socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8)

                val token = reader.readLine()
                if (token != expectedToken) {
                    writer.write("ERR_AUTH")
                    writer.newLine()
                    writer.flush()
                    return
                }

                val countStr = reader.readLine()
                val count = countStr?.toIntOrNull() ?: 0
                val receivedArgs = ArrayList<String>(count)
                for (i in 0 until count) {
                    val line = reader.readLine() ?: break
                    val decoded = runCatching {
                        String(Base64.getUrlDecoder().decode(line), StandardCharsets.UTF_8)
                    }.getOrDefault(line)
                    receivedArgs.add(decoded)
                }

                writer.write("OK")
                writer.newLine()
                writer.flush()

                onSecondaryPing(receivedArgs)
            } catch (_: Exception) {
                // Client connection reset or timed out
            }
        }
    }

    private fun onSecondaryPing(receivedArgs: List<String>) {
        // 1. Dispatch received deep-link arguments through handleAppUrl
        for (arg in receivedArgs) {
            val cleaned = cleanArgument(arg)
            if (isDesktopAppUrl(cleaned)) {
                runCatching {
                    handleAppUrl(cleaned)
                }
            }
        }

        // 2. Restore and focus the window
        restoreAndFocusWindow()

        // 3. Test hook
        runCatching {
            onPingReceivedForTest?.invoke(receivedArgs)
        }
    }

    /**
     * Connects Compose window and state to the single instance controller.
     */
    fun attachWindow(window: Window, windowState: WindowState) {
        this.window = window
        this.windowState = windowState
        if (pendingActivation.compareAndSet(true, false)) {
            restoreAndFocusWindow()
        }
    }

    fun detachWindow() {
        this.window = null
        this.windowState = null
    }

    /**
     * Unminimizes, restores, and requests focus for the application window.
     */
    fun restoreAndFocusWindow() {
        SwingUtilities.invokeLater {
            val state = windowState
            val win = window
            if (win == null && state == null) {
                pendingActivation.set(true)
                return@invokeLater
            }

            runCatching {
                state?.isMinimized = false
            }

            if (win is Frame) {
                runCatching {
                    if ((win.extendedState and Frame.ICONIFIED) != 0) {
                        win.extendedState = win.extendedState and Frame.ICONIFIED.inv()
                    }
                }
            }

            if (win != null) {
                runCatching {
                    win.isVisible = true
                    if (DesktopHostOs.current == DesktopHostOs.WINDOWS) {
                        win.isAlwaysOnTop = true
                        win.toFront()
                        win.requestFocus()
                        win.isAlwaysOnTop = false
                    } else {
                        win.toFront()
                        win.requestFocus()
                    }
                }
            }
        }
    }

    /**
     * Releases lock and server socket resources gracefully.
     */
    @Synchronized
    fun release() {
        if (isReleased.getAndSet(true)) return

        val dir = activeLockDir ?: DesktopStorage.rootDir

        // 1. Close server socket
        runCatching {
            serverSocket?.close()
        }
        serverSocket = null

        // 2. Delete port file
        runCatching {
            Files.deleteIfExists(dir.resolve(PORT_FILE_NAME))
        }

        // 3. Release lock and channel
        runCatching {
            fileLock?.release()
        }
        fileLock = null

        runCatching {
            lockFileChannel?.close()
        }
        lockFileChannel = null

        runCatching {
            lockRaf?.close()
        }
        lockRaf = null

        // 4. Delete lock file
        runCatching {
            Files.deleteIfExists(dir.resolve(LOCK_FILE_NAME))
        }
    }

    private fun registerShutdownHookOnce() {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread({
                release()
            }, "nuvio-single-instance-cleanup"))
        }
    }

    fun cleanArgument(value: String): String =
        value.trim().removeSurrounding("\"").removeSurrounding("'").trim()

    fun isDesktopAppUrl(value: String): Boolean {
        val cleaned = cleanArgument(value)
        return cleaned.startsWith("nuvio://", ignoreCase = true) ||
            cleaned.startsWith("stremio://", ignoreCase = true)
    }

    internal fun resetForTest() {
        release()
        isReleased.set(false)
        pendingActivation.set(false)
        window = null
        windowState = null
        onPingReceivedForTest = null
        activeLockDir = null
    }
}
