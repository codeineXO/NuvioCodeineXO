package com.nuvio.app.features.autosync

import com.nuvio.app.features.player.PlayerEngineController
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopAutoSyncCoordinatorTest {
    @AfterTest
    fun resetPreferences() {
        AutoSyncPreferencesRepository.installPersistence(load = { false }, save = {})
        AutoSyncSyncedSubtitle.clear()
    }

    @Test
    fun disabledSyncAttachesOriginalWithoutStartingAnalysis() = runTest {
        AutoSyncPreferencesRepository.installPersistence(load = { false }, save = {})
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        val base = recordingController(calls)
        val coordinator = DesktopAutoSyncCoordinator(backgroundScope, "https://example.test/video.mkv",
            emptyMap(), { emptyList() }, { "eng" })
        val controller = coordinator.wrap(base) as AutoSyncPlayerController
        controller.setSubtitleUriWithAutoSync("https://example.test/sub.srt")
        assertEquals<List<Pair<String, List<Any?>>>>(
            listOf("setSubtitleUri" to listOf("https://example.test/sub.srt")), calls)
        assertNull(AutoSyncSyncedSubtitle.url.value)
        coordinator.dispose()
    }

    @Test
    fun localFilesKeepOriginalEvenWhenSyncIsEnabledAndManualDelayClearsMarker() = runTest {
        AutoSyncPreferencesRepository.installPersistence(load = { true }, save = {})
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        val coordinator = DesktopAutoSyncCoordinator(backgroundScope, "file:///movie.mkv",
            emptyMap(), { emptyList() }, { "eng" })
        val wrapped = coordinator.wrap(recordingController(calls))
        val autoSync = wrapped as AutoSyncPlayerController
        autoSync.setSubtitleUriWithSelectedAutoSync("https://example.test/sub.srt")
        assertEquals<List<Pair<String, List<Any?>>>>(
            listOf("setSubtitleUri" to listOf("https://example.test/sub.srt")), calls)
        AutoSyncSyncedSubtitle.mark("https://example.test/sub.srt")
        autoSync.cancelForManualSubtitleDelay()
        assertNull(AutoSyncSyncedSubtitle.url.value)
        wrapped.selectSubtitleTrack(2)
        wrapped.clearExternalSubtitle()
        assertEquals("selectSubtitleTrack" to listOf(2), calls[1])
        assertEquals("clearExternalSubtitle" to emptyList(), calls[2])
        coordinator.dispose()
    }

    private fun recordingController(calls: MutableList<Pair<String, List<Any?>>>): PlayerEngineController =
        Proxy.newProxyInstance(PlayerEngineController::class.java.classLoader,
            arrayOf(PlayerEngineController::class.java)) { _, method, arguments ->
            calls += method.name to arguments.orEmpty().toList()
            null
        } as PlayerEngineController
}
