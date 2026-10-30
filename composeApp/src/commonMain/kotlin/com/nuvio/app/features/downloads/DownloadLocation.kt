package com.nuvio.app.features.downloads

import com.nuvio.app.core.ui.NuvioToastController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

internal interface DownloadLocationPlatform {
    fun currentPath(): String
    fun isCurrentPath(path: String?): Boolean
    suspend fun pickFolder(): String?
    suspend fun migrate(path: String?, items: List<DownloadItem>, activeIds: List<String>): List<DownloadItem>
}

/** Desktop installs this bridge; unsupported targets expose no folder controls. */
internal object DownloadLocation {
    var platform: DownloadLocationPlatform? = null
    val operationMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutablePath = MutableStateFlow("")
    val path = mutablePath.asStateFlow()
    var recoveredActiveIds = emptyList<String>()
    var recoveryRequired = false
    val operationsBlocked get() = busy.value || recoveryRequired

    fun resumeRecovered() {
        if (operationsBlocked) return
        val ids = recoveredActiveIds
        recoveredActiveIds = emptyList()
        ids.forEach(DownloadsRepository::resumeDownload)
    }

    fun refresh() {
        mutablePath.value = platform?.currentPath().orEmpty()
    }

    fun chooseFolder(restoreDefault: Boolean = false) {
        if (mutableBusy.value) return
        // Lock before opening the picker too: its owning profile must remain stable.
        scope.launch {
            if (!operationMutex.tryLock()) return@launch
            var activeIds = emptyList<String>()
            var transfersPaused = false
            try {
                val backend = platform ?: return@launch
                mutableBusy.value = true
                if (recoveryRequired) DownloadsRepository.onProfileChanged()
                val target = if (restoreDefault) null else backend.pickFolder() ?: return@launch
                if (backend.isCurrentPath(target)) return@launch
                DownloadsRepository.ensureLoaded()
                activeIds = DownloadsRepository.uiState.value.activeItems
                    .filter { it.status == DownloadStatus.Downloading }.map { it.id }
                transfersPaused = true
                DownloadsRepository.pauseForLocationChange()
                val moved = backend.migrate(target, DownloadsRepository.uiState.value.items, activeIds)
                DownloadsRepository.applyLocationChange(moved)
                refresh()
                NuvioToastController.show(getString(Res.string.downloads_location_saved))
            } catch (error: Exception) {
                // A durable commit may have succeeded before a settings write failed.
                // Reload through journal recovery before allowing transfers to resume.
                if (transfersPaused || recoveryRequired) {
                    runCatching { DownloadsRepository.onProfileChanged() }.onFailure {
                        recoveryRequired = true
                        activeIds = emptyList()
                        error.addSuppressed(it)
                    }
                }
                NuvioToastController.show(getString(Res.string.downloads_location_failed, error.message.orEmpty()))
            } finally {
                mutableBusy.value = false
                activeIds = (activeIds + recoveredActiveIds).distinct()
                recoveredActiveIds = emptyList()
                try {
                    activeIds.forEach { id ->
                        runCatching { DownloadsRepository.resumeDownload(id) }.onFailure {
                            NuvioToastController.show(getString(Res.string.downloads_location_failed, it.message.orEmpty()))
                        }
                    }
                } finally {
                    operationMutex.unlock()
                }
            }
        }
    }
}
