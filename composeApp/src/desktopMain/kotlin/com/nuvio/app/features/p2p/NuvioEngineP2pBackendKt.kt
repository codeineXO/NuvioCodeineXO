package com.nuvio.app.features.p2p

import com.nuvio.app.features.player.DesktopBufferPreset
import com.nuvio.engine.NuvioEngineConfig
import com.nuvio.engine.NuvioTorrentProfile
import com.nuvio.engine.NuvioUploadMode
import java.io.File

internal const val UNKNOWN_TORRENT_ERROR = "Unknown torrent error"
internal const val STREAMING_SAMPLE_INTERVAL_MS = 5000L
internal const val STARTUP_SAMPLE_INTERVAL_MS = 1000L
const val METADATA_DEADLINE_MS = 60000L
const val METADATA_STALL_MIN_WAIT_MS = 20000L
const val METADATA_STALL_MS = 15000L

fun nuvioEngineWindowBytes(preset: DesktopBufferPreset = DesktopBufferPreset.Balanced): Long = when (preset) {
    DesktopBufferPreset.Metered -> 64L * 1024L * 1024L
    DesktopBufferPreset.LowData -> 128L * 1024L * 1024L
    DesktopBufferPreset.Balanced -> 256L * 1024L * 1024L
    DesktopBufferPreset.Resilient -> 512L * 1024L * 1024L
}

fun buildNuvioEngineConfig(
    stateDirectory: File,
    cacheDirectory: File,
    uploadEnabled: Boolean,
    torrentProfile: P2pTorrentProfile,
    diskCacheCapacityBytes: Long,
    windowBytes: Long = nuvioEngineWindowBytes(DesktopBufferPreset.Balanced),
): NuvioEngineConfig {
    val nativeProfile = when (torrentProfile) {
        P2pTorrentProfile.SOFT -> NuvioTorrentProfile.Soft
        P2pTorrentProfile.BALANCED -> NuvioTorrentProfile.Balanced
        P2pTorrentProfile.FAST -> NuvioTorrentProfile.Fast
    }
    return NuvioEngineConfig(
        dataDirectory = stateDirectory,
        cacheDirectory = cacheDirectory,
        memoryCacheCapacityBytes = windowBytes,
        diskCacheCapacityBytes = diskCacheCapacityBytes,
        torrentProfile = nativeProfile,
        uploadMode = if (uploadEnabled) NuvioUploadMode.Unlimited else NuvioUploadMode.Disabled,
        streamInactivityTimeoutMilliseconds = 0,
    )
}

fun unexpectedStreamStopError(
    requestId: Long,
    eventStreamId: String?,
    currentStreamId: String?,
    message: String?,
    fallbackMessage: String,
): P2pStreamingState.Error? {
    if (requestId != 0L || currentStreamId == null || eventStreamId != currentStreamId) {
        return null
    }
    val resolvedMessage = message?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackMessage
    return P2pStreamingState.Error(resolvedMessage)
}

fun unexpectedTorrentError(
    requestId: Long,
    eventTorrentId: String?,
    currentTorrentId: String?,
    message: String?,
    fallbackMessage: String,
): P2pStreamingState.Error? {
    if (requestId != 0L || eventTorrentId == null || currentTorrentId == null || eventTorrentId != currentTorrentId) {
        return null
    }
    val resolvedMessage = message?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackMessage
    return P2pStreamingState.Error(resolvedMessage)
}

fun metadataWaitVerdict(waitedMs: Long, stalledForMs: Long, knownPeers: Long): String? {
    return when {
        waitedMs >= METADATA_DEADLINE_MS ->
            "Torrent metadata did not arrive within 60s"
        waitedMs >= METADATA_STALL_MIN_WAIT_MS && stalledForMs >= METADATA_STALL_MS -> {
            if (knownPeers == 0L) {
                "No peers found for this torrent"
            } else {
                "No reachable peers for this torrent ($knownPeers found, none answered)"
            }
        }
        else -> null
    }
}

data class TorrentCacheEntry(
    val torrentId: String,
    val directory: File,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)

private var cachedMeasureExe: File? = null

fun resolveMeasureExe(): File? {
    cachedMeasureExe?.takeIf { it.isFile }?.let { return it }

    val candidates = listOf(
        File("composeApp/build/native/windows/nuvio-disk-measure.exe"),
        File("composeApp/src/desktopMain/resources/native/windows/nuvio-disk-measure.exe"),
        File("build/native/windows/nuvio-disk-measure.exe"),
        File(File(System.getProperty("java.home")).parentFile, "bin/nuvio-disk-measure.exe"),
        File(System.getProperty("java.io.tmpdir"), "nuvio-engine/nuvio-disk-measure.exe")
    )
    for (candidate in candidates) {
        if (candidate.isFile) {
            cachedMeasureExe = candidate
            return candidate
        }
    }

    val stream = NuvioEngineConfig::class.java.getResourceAsStream("/native/windows/nuvio-disk-measure.exe")
        ?: NuvioEngineConfig::class.java.getResourceAsStream("/nuvio-disk-measure.exe")
    if (stream != null) {
        try {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "nuvio-engine")
            tempDir.mkdirs()
            val targetFile = File(tempDir, "nuvio-disk-measure.exe")
            stream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cachedMeasureExe = targetFile
            return targetFile
        } catch (_: Throwable) {
            // continue to fallback
        }
    }

    val csc = File("C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe")
    if (csc.isFile) {
        try {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "nuvio-engine")
            tempDir.mkdirs()
            val targetExe = File(tempDir, "nuvio-disk-measure.exe")
            val csSource = File(tempDir, "nuvio-disk-measure.cs")
            csSource.writeText(
                """
                using System;
                using System.IO;
                using System.Runtime.InteropServices;
                class Program {
                    [DllImport("kernel32.dll", SetLastError = true, EntryPoint = "GetCompressedFileSizeW")]
                    static extern uint GetCompressedFileSizeW([MarshalAs(UnmanagedType.LPWStr)] string path, out uint high);
                    static long GetSize(string path) {
                        uint high;
                        uint low = GetCompressedFileSizeW(path, out high);
                        if (low == 0xFFFFFFFF && Marshal.GetLastWin32Error() != 0) return 0L;
                        return ((long)high << 32) | (long)low;
                    }
                    static void Main(string[] args) {
                        if (args.Length == 0) { Console.WriteLine(0); return; }
                        string target = args[0];
                        try {
                            if (File.Exists(target)) { Console.WriteLine(GetSize(target)); }
                            else if (Directory.Exists(target)) {
                                long total = 0L;
                                foreach (string file in Directory.EnumerateFiles(target, "*", SearchOption.AllDirectories)) {
                                    total += GetSize(file);
                                }
                                Console.WriteLine(total);
                            } else { Console.WriteLine(0); }
                        } catch { Console.WriteLine(0); }
                    }
                }
                """.trimIndent()
            )
            val process = ProcessBuilder(csc.absolutePath, "/nologo", "/optimize", "/target:exe", "/out:${targetExe.absolutePath}", csSource.absolutePath)
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            csSource.delete()
            if (targetExe.isFile) {
                cachedMeasureExe = targetExe
                return targetExe
            }
        } catch (_: Throwable) {
            // continue to fallback
        }
    }

    return null
}

fun measureAllocatedDiskBytes(target: File): Long {
    if (!target.exists()) return 0L
    val isWindows = System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)
    if (isWindows) {
        val exe = resolveMeasureExe()
        if (exe != null && exe.isFile) {
            val allocated = runCatching {
                val process = ProcessBuilder(exe.absolutePath, target.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                process.waitFor()
                output.toLongOrNull()
            }.getOrNull()
            if (allocated != null) return allocated
        }
    }
    return if (target.isDirectory) {
        target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } else {
        target.length()
    }
}

fun parseTorrentNameFromResume(resumeFile: File): String? {
    if (!resumeFile.exists() || !resumeFile.isFile) return null
    return runCatching {
        val bytes = resumeFile.readBytes()
        val marker = "4:name".toByteArray(Charsets.ISO_8859_1)
        val idx = indexOfSubarray(bytes, marker)
        if (idx < 0) return null
        var cursor = idx + marker.size
        val lenStart = cursor
        while (cursor < bytes.size && bytes[cursor] in '0'.code.toByte()..'9'.code.toByte()) {
            cursor++
        }
        if (cursor == lenStart || cursor >= bytes.size || bytes[cursor] != ':'.code.toByte()) {
            return null
        }
        val lengthStr = String(bytes, lenStart, cursor - lenStart, Charsets.ISO_8859_1)
        val nameLength = lengthStr.toIntOrNull() ?: return null
        val nameStart = cursor + 1
        if (nameStart + nameLength > bytes.size) return null
        String(bytes, nameStart, nameLength, Charsets.UTF_8)
    }.getOrNull()
}

internal fun indexOfSubarray(source: ByteArray, target: ByteArray): Int {
    if (target.isEmpty() || source.size < target.size) return -1
    val limit = source.size - target.size
    for (i in 0..limit) {
        var match = true
        for (j in target.indices) {
            if (source[i + j] != target[j]) {
                match = false
                break
            }
        }
        if (match) return i
    }
    return -1
}

fun scanTorrentCacheEntries(
    cacheDirectory: File,
    resumeDirectory: File? = null,
): List<TorrentCacheEntry> {
    if (!cacheDirectory.exists()) return emptyList()

    val nameToHash = mutableMapOf<String, String>()
    if (resumeDirectory != null && resumeDirectory.isDirectory) {
        resumeDirectory.listFiles()?.filter { it.isFile && it.name.endsWith(".resume") }?.forEach { resumeFile ->
            val infoHash = resumeFile.name.removeSuffix(".resume").lowercase()
            val torrentName = parseTorrentNameFromResume(resumeFile)
            if (torrentName != null) {
                nameToHash[torrentName.lowercase()] = infoHash
            }
        }
    }

    val candidateTargets = mutableListOf<File>()
    val nestedPayload = File(cacheDirectory, "payload")
    if (nestedPayload.isDirectory) {
        nestedPayload.listFiles()?.let { candidateTargets.addAll(it) }
    }
    cacheDirectory.listFiles()?.filter { it.name != "payload" }?.let {
        candidateTargets.addAll(it)
    }

    return candidateTargets.map { target ->
        val resolvedTorrentId = nameToHash[target.name.lowercase()]
            ?: if (target.name.matches(Regex("^[0-9a-fA-F]{40}$"))) {
                target.name.lowercase()
            } else {
                target.name.lowercase()
            }
        val size = measureAllocatedDiskBytes(target)
        val lastModified = if (target.isDirectory) {
            target.walkTopDown().map { it.lastModified() }.maxOrNull() ?: target.lastModified()
        } else {
            target.lastModified()
        }
        TorrentCacheEntry(
            torrentId = resolvedTorrentId,
            directory = target,
            sizeBytes = size,
            lastModifiedMs = lastModified,
        )
    }
}

fun selectTorrentsToPrune(
    entries: List<TorrentCacheEntry>,
    currentTotalBytes: Long,
    capacityBytes: Long,
    activeTorrentId: String? = null,
): List<TorrentCacheEntry> {
    if (capacityBytes > 0L && currentTotalBytes <= capacityBytes) {
        return emptyList()
    }
    val activeCanonical = activeTorrentId?.lowercase()
    val protectedTorrentId = if (capacityBytes > 0L) {
        activeCanonical ?: entries.maxByOrNull { it.lastModifiedMs }?.torrentId
    } else {
        null
    }
    val eligible = entries
        .filter { entry -> protectedTorrentId == null || entry.torrentId != protectedTorrentId }
        .sortedBy { it.lastModifiedMs }

    val toPrune = mutableListOf<TorrentCacheEntry>()
    var remainingTotal = currentTotalBytes
    for (entry in eligible) {
        if (capacityBytes > 0L && remainingTotal <= capacityBytes) {
            break
        }
        toPrune.add(entry)
        remainingTotal -= entry.sizeBytes
    }
    return toPrune
}

