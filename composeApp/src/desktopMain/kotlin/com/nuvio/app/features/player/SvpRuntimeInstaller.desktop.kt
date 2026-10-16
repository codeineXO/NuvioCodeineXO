package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

internal actual object SvpRuntimeInstaller {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<SvpRuntimeDownloadState>(
        if (checkInstalled()) SvpRuntimeDownloadState.Installed else SvpRuntimeDownloadState.Idle
    )
    actual val state: StateFlow<SvpRuntimeDownloadState> = _state.asStateFlow()

    actual val isSupported: Boolean
        get() = DesktopHostOs.current == DesktopHostOs.WINDOWS

    actual val isInstalled: Boolean
        get() = checkInstalled()

    private fun checkInstalled(): Boolean {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return false
        val root = DesktopStorage.rootDir.resolve("vapoursynth")
        val vsScript = root.resolve("VSScript.dll")
        val svpPlugin = root.resolve("vs-plugins").resolve("svpflow1_vs.dll")
        if (!Files.exists(vsScript) || !Files.exists(svpPlugin)) return false

        // Self-heal: ensure VSScript.dll is API 4.0-compatible and portable.vs exists
        if (Files.exists(root.resolve("python312.dll"))) {
            val size = runCatching { Files.size(vsScript) }.getOrDefault(0L)
            val portableMarker = root.resolve("portable.vs")
            if (size != 44544L || !Files.exists(portableMarker)) {
                deployBundledComponents(root)
            }
        }
        return true
    }

    private const val RUNTIME_DOWNLOAD_URL =
        "https://github.com/allecsc/Stremio-Kai/releases/download/v4.8.0/Stremio-Kai_v4.8.0.7z"

    actual fun startDownload() {
        if (!isSupported) {
            _state.value = SvpRuntimeDownloadState.Error("SVP automated pack installation is only supported on Windows.")
            return
        }
        if (downloadJob?.isActive == true) return

        downloadJob = scope.launch {
            val archiveFile = DesktopStorage.cacheDir.resolve("stremio-kai-svp-runtime.7z")
            val targetDir = DesktopStorage.rootDir.resolve("vapoursynth")
            val tempExtractDir = DesktopStorage.cacheDir.resolve("svp-extract-temp")

            try {
                _state.value = SvpRuntimeDownloadState.Downloading(0f)
                Files.createDirectories(DesktopStorage.cacheDir)
                Files.createDirectories(targetDir)

                downloadFileWithProgress(RUNTIME_DOWNLOAD_URL, archiveFile)

                _state.value = SvpRuntimeDownloadState.Extracting
                extractArchive(archiveFile, tempExtractDir, targetDir)

                // Refresh bridge availability check
                runCatching { NativePlayerBridge.isSvpAvailable() }
                _state.value = SvpRuntimeDownloadState.Installed
            } catch (e: CancellationException) {
                runCatching { Files.deleteIfExists(archiveFile) }
                _state.value = if (checkInstalled()) SvpRuntimeDownloadState.Installed else SvpRuntimeDownloadState.Idle
            } catch (e: Exception) {
                _state.value = SvpRuntimeDownloadState.Error(e.message ?: "Failed to download and install SVP runtime")
            } finally {
                runCatching { Files.deleteIfExists(archiveFile) }
                cleanDirectory(tempExtractDir)
            }
        }
    }

    actual fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    private fun downloadFileWithProgress(urlStr: String, destination: Path) {
        var currentUrl = urlStr
        var connection: HttpURLConnection? = null
        var redirects = 0
        while (redirects < 5) {
            connection = URI(currentUrl).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "NuvioCodeineXO")

            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val newUrl = connection.getHeaderField("Location") ?: break
                currentUrl = newUrl
                redirects++
                connection.disconnect()
            } else {
                break
            }
        }

        val conn = connection ?: throw IllegalStateException("Could not establish connection to $urlStr")
        val totalBytes = conn.contentLengthLong

        conn.inputStream.use { input ->
            Files.newOutputStream(destination).use { output ->
                val buffer = ByteArray(65536)
                var downloadedBytes = 0L
                var bytesRead: Int
                var lastProgressUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 100 && totalBytes > 0) {
                        val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                        _state.value = SvpRuntimeDownloadState.Downloading(progress)
                        lastProgressUpdate = now
                    }
                }
            }
        }
    }

    private fun extractArchive(archiveFile: Path, tempExtractDir: Path, targetDir: Path) {
        Files.createDirectories(tempExtractDir)

        val has7z = runCatching {
            val p = ProcessBuilder("7z.exe", "--help").start()
            p.waitFor(3, TimeUnit.SECONDS)
        }.getOrDefault(false)

        if (has7z) {
            val process = ProcessBuilder(
                "7z.exe", "x", archiveFile.toAbsolutePath().toString(),
                "-o${tempExtractDir.toAbsolutePath()}",
                "Stremio Kai\\python*",
                "Stremio Kai\\*.dll",
                "Stremio Kai\\*.zip",
                "Stremio Kai\\*.pyd",
                "Stremio Kai\\lib\\site-packages\\*",
                "Stremio Kai\\vs-plugins\\*",
                "Stremio Kai\\vs-coreplugins\\*",
                "-r", "-y"
            ).redirectErrorStream(true).start()
            process.waitFor(180, TimeUnit.SECONDS)
        } else {
            val process = ProcessBuilder(
                "tar.exe", "-xf", archiveFile.toAbsolutePath().toString(),
                "-C", tempExtractDir.toAbsolutePath().toString()
            ).redirectErrorStream(true).start()
            process.waitFor(180, TimeUnit.SECONDS)
        }

        val stremioKaiDir = tempExtractDir.resolve("Stremio Kai")
        val sourceDir = if (Files.exists(stremioKaiDir)) stremioKaiDir else tempExtractDir

        if (Files.exists(sourceDir)) {
            Files.walk(sourceDir).use { stream ->
                stream.forEach { sourcePath ->
                    val rel = sourceDir.relativize(sourcePath)
                    if (rel.toString().isNotEmpty()) {
                        val dest = targetDir.resolve(rel.toString())
                        if (Files.isDirectory(sourcePath)) {
                            Files.createDirectories(dest)
                        } else {
                            Files.createDirectories(dest.parent)
                            Files.copy(sourcePath, dest, StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }
            }
        }

        val pkgVsDll = targetDir.resolve("lib/site-packages/vapoursynth.dll")
        if (Files.exists(pkgVsDll)) {
            Files.copy(pkgVsDll, targetDir.resolve("vapoursynth.dll"), StandardCopyOption.REPLACE_EXISTING)
        }
        deployBundledComponents(targetDir)
    }

    private fun deployBundledComponents(targetDir: Path) {
        val resourceStream = SvpRuntimeInstaller::class.java.getResourceAsStream("/vapoursynth/VSScript.dll")
        if (resourceStream != null) {
            val dest = targetDir.resolve("VSScript.dll")
            resourceStream.use { input ->
                Files.copy(input, dest, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val portableMarker = targetDir.resolve("portable.vs")
        if (!Files.exists(portableMarker)) {
            runCatching { Files.createFile(portableMarker) }
        }
        runCatching { Files.deleteIfExists(targetDir.resolve("VSScriptPython38.dll")) }
    }

    private fun cleanDirectory(dir: Path) {
        if (!Files.exists(dir)) return
        runCatching {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }
}
