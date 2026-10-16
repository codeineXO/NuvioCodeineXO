package com.nuvio.app.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal actual object SvpRuntimeInstaller {
    actual val isSupported: Boolean = false
    actual val isInstalled: Boolean = false
    actual val state: StateFlow<SvpRuntimeDownloadState> = MutableStateFlow(SvpRuntimeDownloadState.Idle)

    actual fun startDownload() = Unit
    actual fun cancelDownload() = Unit
}
