package com.nuvio.app.core.diagnostics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.storage.DesktopStorage
import java.awt.Desktop
import java.io.BufferedWriter
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Writes the opt-in debug log to %APPDATA%\NuvioCodeineXO\logs: one file per session
 * (rolled at [MAX_FILE_BYTES]), newest [MAX_FILES] kept. Lines are queued and written on a
 * daemon thread so logging never blocks the UI or the player.
 */
object DesktopDebugLog {
    private const val MAX_FILE_BYTES = 10L * 1024 * 1024
    private const val MAX_FILES = 10
    private const val QUEUE_CAPACITY = 50_000
    private const val ENABLED_KEY = "enabled"
    private const val REDACT_KEY = "redact_secrets"

    private val store = DesktopStorage.store("nuvio_debug_logging")
    private val timestamp = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val queue = LinkedBlockingQueue<Entry>(QUEUE_CAPACITY)
    private val dropped = AtomicLong(0)
    private val lock = Any()

    @Volatile
    private var active = false
    private var writerThread: Thread? = null
    private var writer: BufferedWriter? = null
    private var sessionStamp: String = ""
    private var part = 1
    private var bytesWritten = 0L

    val logDir: Path by lazy {
        val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        (appData?.let(Paths::get) ?: Paths.get(System.getProperty("user.home"), "AppData", "Roaming"))
            .resolve("NuvioCodeineXO")
            .resolve("logs")
    }

    fun install() {
        DebugLogSettings.initialize(
            enabled = store.getBoolean(ENABLED_KEY) ?: false,
            redactSecrets = store.getBoolean(REDACT_KEY) ?: true,
            logFolderPath = logDir.toString(),
            saveEnabled = { store.putBoolean(ENABLED_KEY, it) },
            saveRedactSecrets = { store.putBoolean(REDACT_KEY, it) },
            openLogFolder = ::openLogFolder,
        )
        Logger.addLogWriter(FileLogWriter)
        installCrashHandler()
        if (DebugLogSettings.enabled.value) start()
        scope.launch {
            DebugLogSettings.enabled.drop(1).collect { enabled ->
                if (enabled) start() else stop()
            }
        }
    }

    fun isActive(): Boolean = active

    /** Raw message (no timestamp) from any source, e.g. the mpv bridge. */
    fun write(tag: String, level: String, message: String) {
        if (!active) return
        enqueue(level, tag, message)
    }

    // Callers only capture the time; redaction and formatting run on the writer thread so the
    // UI thread and the mpv event loop never pay for the regex passes.
    private class Entry(val time: LocalDateTime, val level: String, val tag: String, val message: String)

    private fun render(entry: Entry): String {
        val text = if (DebugLogSettings.redactSecrets.value) LogRedactor.redact(entry.message) else entry.message
        return "${entry.time.format(timestamp)} ${entry.level} [${entry.tag}] $text"
    }

    private fun enqueue(level: String, tag: String, message: String) {
        if (!queue.offer(Entry(LocalDateTime.now(), level, tag, message))) dropped.incrementAndGet()
    }

    private fun start() {
        synchronized(lock) {
            if (active) return
            runCatching { Files.createDirectories(logDir) }
            sessionStamp = LocalDateTime.now().format(fileStamp)
            part = 1
            if (!openFile()) return
            active = true
            writerThread = Thread(::drainLoop, "nuvio-debug-log").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun stop() {
        val thread: Thread?
        synchronized(lock) {
            if (!active) return
            enqueue("I", "DebugLog", "Debug logging turned off")
            active = false
            thread = writerThread
            writerThread = null
        }
        thread?.interrupt()
        runCatching { thread?.join(2_000) }
        synchronized(lock) {
            drainPendingLocked()
            closeFile()
        }
    }

    private fun drainLoop() {
        // Header runs here: the GPU query spawns a process and must stay off the UI thread.
        val header = writeHeader()
        synchronized(lock) { header.forEach(::writeLine) }
        pruneOldFiles()
        while (active) {
            val entry = try {
                queue.poll(500, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            }
            synchronized(lock) {
                if (entry != null) writeLine(render(entry))
                drainPendingLocked()
                runCatching { writer?.flush() }
            }
        }
    }

    private fun drainPending() = synchronized(lock) { drainPendingLocked() }

    private fun drainPendingLocked() {
        val lost = dropped.getAndSet(0)
        if (lost > 0) writeLine(render(Entry(LocalDateTime.now(), "W", "DebugLog", "$lost log lines dropped (queue full)")))
        while (true) {
            val next = queue.poll() ?: break
            writeLine(render(next))
        }
        runCatching { writer?.flush() }
    }

    private fun writeLine(line: String) {
        val out = writer ?: return
        runCatching {
            out.write(line)
            out.newLine()
            bytesWritten += line.length + 2
        }
        if (bytesWritten >= MAX_FILE_BYTES) {
            closeFile()
            part++
            openFile()
            pruneOldFiles()
        }
    }

    private fun openFile(): Boolean {
        val suffix = if (part > 1) "-$part" else ""
        val file = logDir.resolve("nuvio-$sessionStamp$suffix.log").toFile()
        return runCatching {
            writer = file.bufferedWriter(StandardCharsets.UTF_8)
            bytesWritten = 0
            true
        }.getOrElse { false }
    }

    private fun closeFile() {
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
    }

    private fun pruneOldFiles() {
        runCatching {
            logDir.toFile()
                .listFiles { file: File -> file.isFile && file.name.startsWith("nuvio-") && file.name.endsWith(".log") }
                ?.sortedByDescending(File::lastModified)
                ?.drop(MAX_FILES)
                ?.forEach { it.delete() }
        }
    }

    private fun writeHeader(): List<String> {
        val runtime = Runtime.getRuntime()
        return listOf(
            "===== NuvioCodeineXO debug log =====",
            "Started: ${LocalDateTime.now()}",
            "App: ${AppVersionConfig.DESKTOP_VERSION_NAME} (${AppVersionConfig.DESKTOP_VERSION_CODE})",
            "OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
            "Java: ${System.getProperty("java.vendor")} ${System.getProperty("java.version")}",
            "Memory: max=${runtime.maxMemory() / 1_048_576}MB cpus=${runtime.availableProcessors()}",
            "Renderer: ${rendererName()}",
            "GPU: ${gpuNames()}",
            "Secrets redacted: ${DebugLogSettings.redactSecrets.value}",
            "====================================",
        )
    }

    private fun rendererName(): String {
        val explicit = System.getProperty("skiko.renderApi") ?: System.getenv("SKIKO_RENDER_API")
        return when {
            explicit != null -> explicit
            runCatching { com.nuvio.app.features.settings.DesktopRendererSettings.useOpenGl }.getOrDefault(false) -> "OPENGL"
            System.getProperty("os.name").contains("win", ignoreCase = true) -> "DIRECT3D (Skiko default)"
            else -> "Skiko default"
        }
    }

    private fun gpuNames(): String = runCatching {
        val process = ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-Command",
            "(Get-CimInstance Win32_VideoController | ForEach-Object { \$_.Name + ' / driver ' + \$_.DriverVersion }) -join '; '",
        ).redirectErrorStream(true).start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching "unknown (timeout)"
        }
        process.inputStream.bufferedReader().readText().trim().ifBlank { "unknown" }
    }.getOrDefault("unknown")

    private fun openLogFolder() {
        runCatching { Files.createDirectories(logDir) }
        val dir = logDir.toFile()
        runCatching {
            if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
                ProcessBuilder("explorer.exe", dir.absolutePath).start()
            } else if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir)
            }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (active) {
                enqueue("A", "Crash", "Uncaught exception on thread '${thread.name}'\n${stackTrace(error)}")
                drainPending()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun stackTrace(error: Throwable): String {
        val out = StringWriter()
        error.printStackTrace(PrintWriter(out))
        return out.toString().trimEnd()
    }

    private object FileLogWriter : LogWriter() {
        override fun isLoggable(tag: String, severity: Severity): Boolean = active

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            if (!active) return
            val body = if (throwable != null) "$message\n${stackTrace(throwable)}" else message
            enqueue(severity.name.first().toString(), tag.ifBlank { "App" }, body)
        }
    }
}
