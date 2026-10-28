package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The shared download API is retained for compatibility; Windows preparation is entirely offline. */
internal actual object SvpRuntimeInstaller {
    private val log = Logger.withTag("SvpRuntimeInstaller")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var preparationJob: Job? = null
    private val mutableState = MutableStateFlow<SvpRuntimeDownloadState>(SvpRuntimeDownloadState.Idle)
    actual val state: StateFlow<SvpRuntimeDownloadState> = mutableState.asStateFlow()
    actual val isSupported: Boolean get() = DesktopHostOs.current == DesktopHostOs.WINDOWS
    actual val isInstalled: Boolean get() = mutableState.value == SvpRuntimeDownloadState.Installed
    @Volatile
    internal var runtimeDirectory: java.nio.file.Path? = null
        private set

    init {
        if (isSupported) startDownload()
    }

    /** Called after loading the bridge, before its first availability check or player creation. */
    @Synchronized
    internal fun prepareForBridge(register: (String) -> Boolean) {
        if (!isSupported) return
        mutableState.value = SvpRuntimeDownloadState.Extracting
        try {
            val prefix = "/svp-runtime/windows-x64/"
            val manifest = javaClass.getResourceAsStream(prefix + "manifest.sha256")
                ?.bufferedReader()?.use { it.readText() }
                ?: error("Bundled motion smoothing manifest is missing")
            val directory = BundledSvpRuntime(
                DesktopStorage.cacheDir.resolve("svp-runtime/windows-x64"), manifest,
            ) { name -> javaClass.getResourceAsStream(prefix + name) }.install()
            check(register(directory.toAbsolutePath().toString())) {
                "Bundled VapourSynth could not load its VSScript API (4.0)"
            }
            runtimeDirectory = directory
            mutableState.value = SvpRuntimeDownloadState.Installed
        } catch (error: Throwable) {
            runtimeDirectory = null
            log.e(error) { "Unable to prepare bundled motion smoothing runtime" }
            mutableState.value = SvpRuntimeDownloadState.Error(error.message ?: "Motion smoothing preparation failed")
        }
    }

    @Synchronized
    actual fun startDownload() {
        if (!isSupported || preparationJob?.isActive == true) return
        preparationJob = scope.launch {
            try {
                // Bridge initialization prepares and registers the runtime before it exposes playback.
                NativePlayerBridge.ensureLoaded()
                if (!isInstalled) prepareForBridge(NativePlayerBridge::configureSvpRuntime)
            } catch (error: Throwable) {
                log.e(error) { "Motion smoothing bridge initialization failed" }
                mutableState.value = SvpRuntimeDownloadState.Error(error.message ?: "Motion smoothing is unavailable")
            }
        }
    }

    actual fun cancelDownload() {
        preparationJob?.cancel()
    }
}
