package com.nuvio.app.features.player

import kotlinx.coroutines.flow.StateFlow

sealed interface SvpRuntimeDownloadState {
    data object Idle : SvpRuntimeDownloadState
    data class Downloading(val progress: Float) : SvpRuntimeDownloadState
    data object Extracting : SvpRuntimeDownloadState
    data object Installed : SvpRuntimeDownloadState
    data class Error(val message: String) : SvpRuntimeDownloadState
}

internal expect object SvpRuntimeInstaller {
    val isSupported: Boolean
    val isInstalled: Boolean
    val state: StateFlow<SvpRuntimeDownloadState>

    fun startDownload()
    fun cancelDownload()
}
