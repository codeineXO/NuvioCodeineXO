package com.nuvio.app.features.p2p

import com.nuvio.engine.internal.NuvioEngineLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

actual object P2pStreamingEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val nuvioEngineAvailable: Boolean
        get() = NuvioEngineLibrary.isAvailable

    val stremioEngineAvailable: Boolean
        get() = StremioEngineBinary.isAvailable

    private val _state = MutableStateFlow<P2pStreamingState>(P2pStreamingState.Idle)
    actual val state: StateFlow<P2pStreamingState> = _state.asStateFlow()

    private val _cacheState = MutableStateFlow(P2pCacheUiState())
    actual val cacheState: StateFlow<P2pCacheUiState> = _cacheState.asStateFlow()

    private fun activeBackend(): DesktopP2pBackend {
        P2pSettingsRepository.ensureLoaded()
        return when (P2pSettingsRepository.uiState.value.engineBackend) {
            P2pEngineBackend.STREMIO_ENGINE -> StremioP2pBackend
            P2pEngineBackend.NUVIO_ENGINE -> NuvioEngineP2pBackend
        }
    }

    init {
        scope.launch {
            P2pSettingsRepository.uiState.collect { settings ->
                when (settings.engineBackend) {
                    P2pEngineBackend.STREMIO_ENGINE -> {
                        _state.value = StremioP2pBackend.state.value
                        _cacheState.value = StremioP2pBackend.cacheState.value
                    }
                    P2pEngineBackend.NUVIO_ENGINE -> {
                        _state.value = NuvioEngineP2pBackend.state.value
                        _cacheState.value = NuvioEngineP2pBackend.cacheState.value
                    }
                }
            }
        }

        scope.launch {
            NuvioEngineP2pBackend.state.collect { s ->
                if (P2pSettingsRepository.uiState.value.engineBackend == P2pEngineBackend.NUVIO_ENGINE) {
                    _state.value = s
                }
            }
        }

        scope.launch {
            NuvioEngineP2pBackend.cacheState.collect { c ->
                if (P2pSettingsRepository.uiState.value.engineBackend == P2pEngineBackend.NUVIO_ENGINE) {
                    _cacheState.value = c
                }
            }
        }

        scope.launch {
            StremioP2pBackend.state.collect { s ->
                if (P2pSettingsRepository.uiState.value.engineBackend == P2pEngineBackend.STREMIO_ENGINE) {
                    _state.value = s
                }
            }
        }

        scope.launch {
            StremioP2pBackend.cacheState.collect { c ->
                if (P2pSettingsRepository.uiState.value.engineBackend == P2pEngineBackend.STREMIO_ENGINE) {
                    _cacheState.value = c
                }
            }
        }
    }

    actual suspend fun startStream(request: P2pStreamRequest): String =
        activeBackend().startStream(request)

    actual suspend fun clearCache(): P2pCacheClearResult =
        activeBackend().clearCache()

    actual fun stopStream() {
        NuvioEngineP2pBackend.stopStream()
        StremioP2pBackend.stopStream()
    }

    actual fun shutdown() {
        NuvioEngineP2pBackend.shutdown()
        StremioP2pBackend.shutdown()
    }
}
