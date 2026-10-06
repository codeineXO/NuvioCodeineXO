package com.nuvio.app.features.p2p

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit

internal object StremioP2pBackend : DesktopP2pBackend {
    private val log = Logger.withTag("StremioP2pBackend")

    private const val STREMIO_HTTP_PORT = 11470
    private const val STREMIO_BASE_URL = "http://127.0.0.1:$STREMIO_HTTP_PORT"

    private val _state = MutableStateFlow<P2pStreamingState>(P2pStreamingState.Idle)
    override val state: StateFlow<P2pStreamingState> = _state.asStateFlow()

    private val _cacheState = MutableStateFlow(P2pCacheUiState())
    override val cacheState: StateFlow<P2pCacheUiState> = _cacheState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    private val lifecycleLock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var engineProcess: Process? = null
    private var statsJob: Job? = null
    private var cleanupJob: Job? = null

    @Volatile
    private var currentInfoHash: String? = null
    @Volatile
    private var currentFileIdx: Int? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking {
                stopStreamNow(shutdownEngine = true)
            }
        }.apply {
            name = "stremio-engine-shutdown"
        })

        scope.launch(Dispatchers.IO) {
            P2pSettingsRepository.uiState.collect {
                measureDiskCache()
            }
        }
    }

    override suspend fun startStream(request: P2pStreamRequest): String = withContext(Dispatchers.IO) {
        startMutex.withLock {
            startStreamLocked(request)
        }
    }

    private suspend fun startStreamLocked(request: P2pStreamRequest): String {
        val canonicalHash = canonicalP2pInfoHash(request.infoHash)
        val fileIdx = request.fileIdx ?: 0

        log.i { "Stremio P2P startup: infoHash=$canonicalHash fileIdx=$fileIdx" }

        _state.value = P2pStreamingState.Connecting(phase = "starting_engine")

        ensureEngineRunning()
        applyEngineSettings()

        synchronized(lifecycleLock) {
            currentInfoHash = canonicalHash
            currentFileIdx = fileIdx
        }

        // Format trackers cleanly as tr query parameters (without synthetic dht: or tracker: prefixes)
        val validTrackers = request.trackers.filter(String::isNotBlank).map { tracker ->
            tracker.removePrefix("tracker:").trim()
        }.filter(String::isNotBlank).distinct()

        val trackerQuery = if (validTrackers.isNotEmpty()) {
            "?" + validTrackers.joinToString("&") { "tr=${it.encodeP2pQueryValue()}" }
        } else {
            ""
        }
        val streamUrl = "$STREMIO_BASE_URL/$canonicalHash/$fileIdx$trackerQuery"

        startStatsPolling(canonicalHash, fileIdx, streamUrl)
        _state.value = P2pStreamingState.Streaming(
            localUrl = streamUrl,
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            peers = 0,
            seeds = 0,
            bufferProgress = 0f,
            totalProgress = 0f,
            downloadedBytes = 0L,
            verifiedBytes = 0L,
            deliveredBytes = 0L,
        )
        measureDiskCache()

        return streamUrl
    }

    override suspend fun clearCache(): P2pCacheClearResult = withContext(Dispatchers.IO) {
        startMutex.withLock {
            check(_state.value !is P2pStreamingState.Streaming && _state.value !is P2pStreamingState.Connecting) {
                "Torrent cache cannot be cleared during active playback"
            }
            _cacheState.value = _cacheState.value.copy(isClearing = true)
            try {
                // Shut down process to release locks on cache files
                stopStreamNow(shutdownEngine = true)

                val cacheDir = getStremioCacheDirectory()
                val diskBefore = if (cacheDir.exists()) measureAllocatedDiskBytes(cacheDir) else 0L

                var reclaimed = 0L
                if (cacheDir.exists()) {
                    cacheDir.listFiles()?.forEach { file ->
                        val len = measureAllocatedDiskBytes(file)
                        if (file.deleteRecursively()) {
                            reclaimed += len
                        }
                    }
                }

                val diskAfter = if (cacheDir.exists()) measureAllocatedDiskBytes(cacheDir) else 0L
                updateCacheState(diskAfter, 0L)

                P2pCacheClearResult(
                    reclaimedBytes = maxOf(diskBefore - diskAfter, reclaimed),
                    remainingBytes = diskAfter,
                    protectedBytes = 0L
                )
            } finally {
                _cacheState.value = _cacheState.value.copy(isClearing = false)
            }
        }
    }

    override fun stopStream() {
        scheduleStop(shutdownEngine = true)
    }

    override fun shutdown() {
        scheduleStop(shutdownEngine = true)
    }

    private fun scheduleStop(shutdownEngine: Boolean) {
        val previousCleanup = cleanupJob
        cleanupJob = scope.launch(Dispatchers.IO) {
            previousCleanup?.join()
            stopStreamNow(shutdownEngine)
        }
    }

    private suspend fun stopStreamNow(shutdownEngine: Boolean) {
        synchronized(lifecycleLock) {
            currentInfoHash = null
            currentFileIdx = null
            statsJob?.cancel()
            statsJob = null
            _state.value = P2pStreamingState.Idle
        }

        // Halts all active downloading and disk caching immediately
        if (shutdownEngine) {
            killEngineProcess()
        }
    }

    private fun ensureEngineRunning() {
        if (engineProcess?.isAlive == true && isPortOpen(STREMIO_HTTP_PORT)) {
            return
        }

        val engine = StremioEngineBinary.resolve()
            ?: throw P2pStreamingException("Official Stremio engine (stremio-runtime.exe / server.js) not found")

        killOrphanEngines(engine.runtime)
        killEngineProcess()

        val cacheDir = getStremioCacheDirectory()
        cacheDir.mkdirs()

        val pb = ProcessBuilder(engine.runtime.absolutePath, engine.script.absolutePath)
        engine.workingDir?.let { pb.directory(it) }
        pb.environment()["BIND_ADDRESS"] = "127.0.0.1"
        pb.environment()["HTTPS_PORT"] = "0"
        pb.environment()["APP_PATH"] = cacheDir.absolutePath
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.to(File(cacheDir, "engine.log")))

        val proc = pb.start()
        engineProcess = proc

        // Wait for port 11470 to become available
        val deadline = System.currentTimeMillis() + 10000L
        var running = false
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                val exit = proc.exitValue()
                throw P2pStreamingException("Stremio engine failed to start (exit code $exit)")
            }
            if (isPortOpen(STREMIO_HTTP_PORT)) {
                running = true
                break
            }
            Thread.sleep(100)
        }

        if (!running) {
            killEngineProcess()
            throw P2pStreamingException("Stremio engine timed out waiting for port $STREMIO_HTTP_PORT")
        }

        log.i { "Official Stremio engine started successfully (PID=${proc.pid()})" }
    }

    private fun applyEngineSettings() {
        runCatching {
            val settings = P2pSettingsRepository.uiState.value
            val profile = settings.stremioProfile
            val maxConn = when (profile) {
                StremioTorrentProfile.SOFT -> 80
                StremioTorrentProfile.DEFAULT -> 400
                StremioTorrentProfile.FAST -> 350
                StremioTorrentProfile.ULTRA_FAST -> 500
            }
            val minPeers = when (profile) {
                StremioTorrentProfile.SOFT -> 3
                StremioTorrentProfile.DEFAULT -> 10
                StremioTorrentProfile.FAST -> 10
                StremioTorrentProfile.ULTRA_FAST -> 15
            }
            val hardLimit = when (profile) {
                StremioTorrentProfile.SOFT -> 3670016L // ~3.5MB/s
                StremioTorrentProfile.DEFAULT -> 78643200L // 75MB/s (Official Stremio default limit)
                StremioTorrentProfile.FAST -> 104857600L // 100MB/s
                StremioTorrentProfile.ULTRA_FAST -> 209715200L // 200MB/s
            }
            val softLimit = when (profile) {
                StremioTorrentProfile.SOFT -> 2621440L // 2.5MB/s
                StremioTorrentProfile.DEFAULT -> 8388608L // 8MB/s (Official Stremio default soft limit)
                StremioTorrentProfile.FAST -> 20971520L // 20MB/s
                StremioTorrentProfile.ULTRA_FAST -> 52428800L // 50MB/s
            }
            val cacheBytes = settings.cacheSize.bytes
            val jsonPayload = """
                {
                    "cacheSize": $cacheBytes,
                    "cacheRoot": "${getStremioCacheDirectory().absolutePath.replace("\\", "\\\\")}",
                    "btProfile": "${profile.id}",
                    "btMaxConnections": $maxConn,
                    "btMinPeersForStable": $minPeers,
                    "btDownloadSpeedHardLimit": $hardLimit,
                    "btDownloadSpeedSoftLimit": $softLimit,
                    "btHandshakeTimeout": 25000,
                    "btRequestTimeout": 6000,
                    "seedingEnabled": ${settings.enableUpload}
                }
            """.trimIndent()

            val conn = URL("$STREMIO_BASE_URL/settings").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(jsonPayload) }
            conn.responseCode
            conn.disconnect()
        }
    }

    private fun killOrphanEngines(binary: File) {
        val target = runCatching { binary.canonicalPath }.getOrDefault(binary.absolutePath)
        val ownPid = engineProcess?.pid()
        runCatching {
            ProcessHandle.allProcesses()
                .filter { handle ->
                    handle.pid() != ownPid && handle.info().command().map { cmd ->
                        runCatching { File(cmd).canonicalPath }.getOrDefault(cmd).equals(target, ignoreCase = true)
                    }.orElse(false)
                }
                .forEach { handle ->
                    log.w { "Killing orphaned Stremio engine (PID=${handle.pid()})" }
                    handle.destroyForcibly()
                    runCatching { handle.onExit().get(2, TimeUnit.SECONDS) }
                }
        }
    }

    private fun killEngineProcess() {
        val proc = engineProcess ?: return
        engineProcess = null
        runCatching {
            if (proc.isAlive) {
                proc.destroy()
                if (!proc.waitFor(1000, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly()
                }
            }
        }
    }

    private fun startStatsPolling(infoHash: String, fileIdx: Int, streamUrl: String) {
        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000L)
                val stats = fetchStats(infoHash)
                if (stats != null) {
                    val peers = stats.peers
                    val speed = stats.downloadSpeed
                    val uploadSpeed = stats.uploadSpeed

                    _state.value = P2pStreamingState.Streaming(
                        localUrl = streamUrl,
                        downloadSpeed = speed,
                        uploadSpeed = uploadSpeed,
                        peers = peers,
                        seeds = stats.seeds,
                        bufferProgress = if (stats.totalLength > 0) {
                            (stats.downloaded.toFloat() / stats.totalLength.toFloat()).coerceIn(0f, 1f)
                        } else 0f,
                        totalProgress = if (stats.totalLength > 0) {
                            (stats.downloaded.toFloat() / stats.totalLength.toFloat()).coerceIn(0f, 1f)
                        } else 0f,
                        downloadedBytes = stats.downloaded,
                        verifiedBytes = stats.downloaded,
                        deliveredBytes = stats.downloaded,
                    )
                }
            }
        }
    }

    private data class StremioStats(
        val peers: Int,
        val seeds: Int,
        val downloadSpeed: Long,
        val uploadSpeed: Long,
        val downloaded: Long,
        val totalLength: Long,
        val filesCount: Int,
    )

    private fun fetchStats(infoHash: String): StremioStats? {
        return runCatching {
            val url = URL("$STREMIO_BASE_URL/stats.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val rootObj = json.parseToJsonElement(text).jsonObject
            val torrentObj = rootObj[infoHash.lowercase()]?.jsonObject
                ?: rootObj[infoHash.uppercase()]?.jsonObject
                ?: rootObj[infoHash]?.jsonObject
                ?: return null

            val peers = torrentObj["peers"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: torrentObj["swarmConnections"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: 0
            val seeds = torrentObj["unchoked"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val dlSpeed = torrentObj["downloadSpeed"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong()
                ?: torrentObj["downloadSpeed"]?.jsonPrimitive?.longOrNull
                ?: 0L
            val ulSpeed = torrentObj["uploadSpeed"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong()
                ?: torrentObj["uploadSpeed"]?.jsonPrimitive?.longOrNull
                ?: 0L
            val downloaded = torrentObj["downloaded"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong()
                ?: torrentObj["downloaded"]?.jsonPrimitive?.longOrNull
                ?: 0L
            val filesArray = torrentObj["files"]?.let { runCatching { it.toString() }.getOrNull() }
            val filesCount = if (filesArray?.contains("length") == true) 1 else 0

            StremioStats(
                peers = peers,
                seeds = seeds,
                downloadSpeed = dlSpeed,
                uploadSpeed = ulSpeed,
                downloaded = downloaded,
                totalLength = 0L,
                filesCount = filesCount,
            )
        }.getOrNull()
    }

    private suspend fun measureDiskCache() {
        val cacheDir = getStremioCacheDirectory()
        val totalBytes = if (cacheDir.exists()) measureAllocatedDiskBytes(cacheDir) else 0L
        updateCacheState(totalBytes, 0L)
    }

    private fun updateCacheState(usedBytes: Long, protectedBytes: Long) {
        _cacheState.value = P2pCacheUiState(
            usedBytes = usedBytes,
            protectedBytes = protectedBytes,
            isClearing = _cacheState.value.isClearing,
            hasMeasurement = true,
        )
    }

    private fun getStremioCacheDirectory(): File {
        // Private app-owned directory: must never share the official Stremio app's data/settings.
        return DesktopStorage.rootDir.resolve("stremio-engine").toFile().apply { mkdirs() }
    }

    private fun isPortOpen(port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 200)
                true
            }
        }.getOrDefault(false)
    }
}
