package com.nuvio.app.features.p2p

import com.nuvio.engine.internal.NuvioEngineLibrary
import kotlinx.coroutines.flow.StateFlow

actual object P2pStreamingEngine {
    val nuvioEngineAvailable: Boolean
        get() = NuvioEngineLibrary.isAvailable

    actual val state: StateFlow<P2pStreamingState>
        get() = NuvioEngineP2pBackend.state

    actual val cacheState: StateFlow<P2pCacheUiState>
        get() = NuvioEngineP2pBackend.cacheState

    actual suspend fun startStream(request: P2pStreamRequest): String =
        NuvioEngineP2pBackend.startStream(request)

    actual suspend fun clearCache(): P2pCacheClearResult =
        NuvioEngineP2pBackend.clearCache()

    actual fun stopStream() {
        NuvioEngineP2pBackend.stopStream()
    }

    actual fun shutdown() {
        NuvioEngineP2pBackend.shutdown()
    }
}
