package com.nuvio.app.features.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val desktopDownloadHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(60))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

internal actual object DownloadsPlatformDownloader {
    private val transfers = DesktopDownloadTransferRegistry()

    private val downloadsDir: File
        get() = DesktopDownloadLocation.directory().also { it.mkdirs() }

    suspend fun awaitTransfersClosed(fileNames: List<String>) =
        transfers.awaitClosed(fileNames.map { File(downloadsDir, "$it.part") }.toSet())

    actual fun restoreItem(item: DownloadItem): DownloadItem =
        if (item.status == DownloadStatus.Downloading) {
            item.copy(status = DownloadStatus.Paused, errorMessage = null)
        } else {
            item
        }

    actual fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
        onPaused: () -> Unit,
    ): DownloadsTaskHandle {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)

        val destination = File(downloadsDir, request.destinationFileName)
        val tempFile = File(downloadsDir, "${request.destinationFileName}.part")
        val transfer = scope.launch(start = CoroutineStart.LAZY) {

            try {
                var resumeFromBytes = tempFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L
                var attemptedRangeRequest = resumeFromBytes > 0L
                var response = runInterruptible {
                    sendDownloadRequest(request, if (attemptedRangeRequest) resumeFromBytes else null)
                }

                if (attemptedRangeRequest && response.statusCode() == 416) {
                    tempFile.delete()
                    resumeFromBytes = 0L
                    attemptedRangeRequest = false
                    response = runInterruptible { sendDownloadRequest(request, null) }
                }

                if (response.statusCode() !in 200..299) {
                    error("Download failed with HTTP ${response.statusCode()}")
                }

                val isPartialResume = attemptedRangeRequest && response.statusCode() == 206 && resumeFromBytes > 0L
                val appendToTemp = isPartialResume
                val startingBytes = if (appendToTemp) resumeFromBytes else 0L
                if (!appendToTemp && tempFile.exists()) {
                    tempFile.delete()
                }

                val totalBytes = resolveTotalBytes(
                    startingBytes = startingBytes,
                    isPartialResume = isPartialResume,
                    contentRangeHeader = response.headers().firstValue("Content-Range").orElse(null),
                    contentLength = response.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull(),
                )
                var downloadedBytes = startingBytes
                onProgress(downloadedBytes, totalBytes)
                var lastReportedBytes = downloadedBytes
                var lastProgressAtNs = System.nanoTime()

                try {
                    response.body().use { input ->
                        ensureActive()
                        FileOutputStream(tempFile, appendToTemp).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                ensureActive()
                                val read = runInterruptible { input.read(buffer) }
                                ensureActive()
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read.toLong()
                                if (System.nanoTime() - lastProgressAtNs >= 250_000_000L) {
                                    onProgress(downloadedBytes, totalBytes)
                                    lastReportedBytes = downloadedBytes
                                    lastProgressAtNs = System.nanoTime()
                                }
                            }
                            output.flush()
                        }
                    }
                } finally {
                    // Publish pending bytes after closing the streams, including on pause or failure.
                    if (downloadedBytes != lastReportedBytes) {
                        onProgress(downloadedBytes, totalBytes)
                    }
                }

                ensureActive()
                if (destination.exists()) {
                    destination.delete()
                }
                if (!tempFile.renameTo(destination)) {
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                }

                val finalSize = destination.length()
                onSuccess(destination.toURI().toString(), totalBytes ?: finalSize)
            } catch (error: CancellationException) {
                onPaused()
                throw error
            } catch (error: Throwable) {
                onFailure(error.message ?: "Download failed")
            }
        }

        transfers.register(tempFile, transfer)
        transfer.start()
        return DesktopDownloadsTaskHandle(job)
    }

    actual fun removeFile(localFileUri: String?): Boolean {
        if (localFileUri.isNullOrBlank()) return false
        val file = localFileUri.toLocalFileOrNull() ?: return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    actual fun removePartialFile(destinationFileName: String): Boolean {
        val tempFile = File(downloadsDir, "$destinationFileName.part")
        return transfers.removePartialFile(tempFile)
    }

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? {
        localFileUri
            ?.toLocalFileOrNull()
            ?.takeIf { it.exists() }
            ?.let { return it.toURI().toString() }

        val fileName = destinationFileName.trim().takeIf { it.isNotBlank() }
            ?: localFileUri?.toLocalFileOrNull()?.name?.takeIf { it.isNotBlank() }
            ?: return null
        return File(downloadsDir, fileName).takeIf { it.exists() }?.toURI()?.toString()
    }

    actual fun openDownloadsDirectory(): Boolean {
        val directory = downloadsDir
        val desktop = runCatching { Desktop.getDesktop() }.getOrNull()

        if (desktop != null && Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.OPEN)) {
            val opened = runCatching { desktop.open(directory) }.isSuccess
            if (opened) return true
        }

        return openDirectoryWithPlatformCommand(directory)
    }

    private fun sendDownloadRequest(
        request: DownloadPlatformRequest,
        rangeStart: Long?,
    ): HttpResponse<java.io.InputStream> {
        val builder = HttpRequest.newBuilder()
            .uri(URI(request.sourceUrl))
            .timeout(Duration.ofSeconds(60))
            .GET()
        request.sourceHeaders.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                builder.header(key, value)
            }
        }
        if (rangeStart != null && rangeStart > 0L) {
            builder.header("Range", "bytes=$rangeStart-")
        }
        return desktopDownloadHttpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
    }
}

internal class DesktopDownloadTransferRegistry {
    private class Transfers {
        val jobs = mutableSetOf<Job>()
        var removePartialFile = false
    }

    private val lock = Any()
    private val active = mutableMapOf<File, Transfers>()

    suspend fun awaitClosed(files: Set<File>) {
        val jobs = synchronized(lock) { active.filterKeys { it in files }.values.flatMap { it.jobs }.toList() }
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
    }

    fun register(tempFile: File, job: Job) = synchronized(lock) {
        val transfers = active.getOrPut(tempFile) { Transfers() }
        transfers.jobs.add(job)
        job.invokeOnCompletion {
            synchronized(lock) {
                transfers.jobs.remove(job)
                if (transfers.jobs.isEmpty()) {
                    active.remove(tempFile)
                    if (transfers.removePartialFile) {
                        tempFile.delete()
                    }
                }
            }
        }
    }

    fun removePartialFile(tempFile: File): Boolean = synchronized(lock) {
        val transfers = active[tempFile]
        if (transfers != null) {
            // Pause only cancels the job. Explicit removal also waits for every writer to close.
            transfers.removePartialFile = true
            transfers.jobs.toList().forEach { it.cancel() }
            true
        } else {
            !tempFile.exists() || tempFile.delete()
        }
    }
}

private fun openDirectoryWithPlatformCommand(directory: File): Boolean {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val command = when {
        osName.contains("mac") -> listOf("open", directory.absolutePath)
        osName.contains("win") -> listOf("explorer", directory.absolutePath)
        else -> listOf("xdg-open", directory.absolutePath)
    }
    return runCatching { ProcessBuilder(command).start() }.isSuccess
}

private class DesktopDownloadsTaskHandle(
    private val job: Job,
) : DownloadsTaskHandle {
    override fun cancel() {
        job.cancel()
    }
}

private fun String.toLocalFileOrNull(): File? =
    runCatching {
        if (startsWith("file:")) {
            File(URI(this))
        } else {
            File(this)
        }
    }.getOrNull()

private fun resolveTotalBytes(
    startingBytes: Long,
    isPartialResume: Boolean,
    contentRangeHeader: String?,
    contentLength: Long?,
): Long? {
    parseContentRangeTotal(contentRangeHeader)?.let { return it }
    val normalizedLength = contentLength?.takeIf { it > 0L } ?: return null
    return if (isPartialResume && startingBytes > 0L) {
        startingBytes + normalizedLength
    } else {
        normalizedLength
    }
}

private fun parseContentRangeTotal(headerValue: String?): Long? {
    val value = headerValue?.trim().orEmpty()
    if (value.isBlank()) return null
    val slashIndex = value.lastIndexOf('/')
    if (slashIndex == -1 || slashIndex == value.lastIndex) return null
    val totalPart = value.substring(slashIndex + 1).trim()
    if (totalPart == "*") return null
    return totalPart.toLongOrNull()?.takeIf { it > 0L }
}
