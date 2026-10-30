package com.nuvio.app.features.player

internal data class ImportedSubtitleFile(
    val path: String,
    val name: String,
)

internal object SubtitleImportBridge {
    var onImport: ((ImportedSubtitleFile) -> Unit)? = null
}

internal expect object SubtitleImportPlatform {
    fun pickSubtitleFile(): ImportedSubtitleFile?
}
