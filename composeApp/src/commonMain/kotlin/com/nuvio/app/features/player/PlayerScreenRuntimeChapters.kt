package com.nuvio.app.features.player

import kotlinx.coroutines.delay

private const val CHAPTER_RETRIES = 8

internal fun PlayerScreenRuntime.clearChapters() {
    chapters = emptyList()
    chaptersLoadedForSourceUrl = null
    if (showChaptersPanel) controlsVisible = true
    showChaptersPanel = false
}

/**
 * Reads the chapters once per source, when it has loaded: libmpv lists them itself, ExoPlayer
 * reads them from the file (when allowed), so a rebuffer must not read them again.
 */
internal suspend fun PlayerScreenRuntime.loadChaptersIfNeeded() {
    val controller = playerController ?: return
    val sourceUrl = playerControllerSourceUrl ?: return
    if (sourceUrl != activeSourceUrl || !playerSettingsUiState.androidExoChaptersEnabled) return
    if (chaptersLoadedForSourceUrl == sourceUrl) return
    var loaded = PlayerChapters.normalize(controller.getChapters())
    var attempts = 0
    while (loaded.isEmpty() && attempts < CHAPTER_RETRIES) {
        delay(1_000)
        if (playerControllerSourceUrl != sourceUrl || activeSourceUrl != sourceUrl || playerController !== controller) return
        loaded = PlayerChapters.normalize(controller.getChapters())
        attempts++
    }
    if (playerControllerSourceUrl != sourceUrl || activeSourceUrl != sourceUrl || playerController !== controller) return
    chaptersLoadedForSourceUrl = sourceUrl
    chapters = loaded
}

internal fun PlayerScreenRuntime.openChaptersPanel() {
    if (chapters.isEmpty()) return
    showChaptersPanel = true
    showSourcesPanel = false
    showEpisodesPanel = false
    controlsVisible = false
}

internal fun PlayerScreenRuntime.selectChapter(index: Int) {
    val chapter = chapters.getOrNull(index) ?: return
    showChaptersPanel = false
    finishTimelineSeek(chapter.startMs)
    controlsVisible = true
    controlsActivityTick += 1
}
