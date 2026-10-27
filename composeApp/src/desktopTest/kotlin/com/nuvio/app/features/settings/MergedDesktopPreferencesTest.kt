package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.features.mdblist.DesktopHeroRatingPreference
import com.nuvio.app.features.mdblist.MdbListSettings
import com.nuvio.app.features.player.DesktopSubtitleRendererPreference
import com.nuvio.app.features.player.PlayerSettingsUiState
import com.nuvio.app.features.streams.DesktopPlayerBadgePreferences
import com.nuvio.app.features.streams.StreamBadgeFilter
import com.nuvio.app.features.streams.StreamBadgeImport
import com.nuvio.app.features.streams.StreamBadgeRules
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsUiState
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MergedDesktopPreferencesTest {
    @Test
    fun desktopDefaultsAndBadgeResetPreserveTheChosenBehavior() {
        assertFalse(StreamBadgeSettingsUiState().showInPlayer)
        assertTrue(StreamBadgeSettingsUiState().showFileSizeBadges)
        assertFalse(StreamBadgeSettingsUiState().showAddonLogo)
        StreamBadgeSettingsRepository.clearLocalState()
        assertFalse(StreamBadgeSettingsRepository.uiState.value.showInPlayer)
        assertEquals("", StreamBadgeSettingsRepository.uiState.value.playerBadgeSource)
        assertTrue(MdbListSettings().showOnHero)
        assertFalse(PlayerSettingsUiState().useLibass)
        assertTrue(PlayerSettingsUiState().androidExoChaptersEnabled)
    }

    @Test
    fun playerBadgeChoicesSurviveDiskReloadProfilesAndSync() {
        val file = Files.createTempFile("merged-player-badges", ".properties")
        val store = DesktopStorage.Store(file)
        var profile = 1
        val key: (String) -> String = { ProfileScopedKey.of(it, profile) }
        val preferences = DesktopPlayerBadgePreferences(store, key)
        try {
            assertNull(preferences.loadEnabled())
            preferences.saveEnabled(true)
            preferences.saveSource("https://example.test/player-badges.json")
            val enabled = preferences.exportToSyncPayload()
            assertEquals(true, enabled.decodeSyncBoolean("show_badges_in_player"))
            assertEquals("https://example.test/player-badges.json", enabled.decodeSyncString("player_badge_source"))

            profile = 2
            assertNull(preferences.loadEnabled())
            assertNull(preferences.loadSource())
            preferences.saveEnabled(false)
            preferences.saveSource("")
            val disabled = preferences.exportToSyncPayload()

            profile = 1
            preferences.replaceFromSyncPayload(disabled)
            assertEquals(false, preferences.loadEnabled())
            assertEquals("", preferences.loadSource())
            preferences.replaceFromSyncPayload(enabled)
            store.flushSync()
            val reloaded = DesktopPlayerBadgePreferences(DesktopStorage.Store(file), key)
            assertEquals(true, reloaded.loadEnabled())
            assertEquals("https://example.test/player-badges.json", reloaded.loadSource())

            profile = 2
            assertEquals(false, reloaded.loadEnabled())
            preferences.replaceFromSyncPayload(JsonObject(emptyMap()))
            assertNull(preferences.loadEnabled())
            assertNull(preferences.loadSource())
            profile = 1
            assertEquals(true, preferences.loadEnabled())
        } finally {
            store.flushSync()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun heroOptOutAndSubtitleOptInRemainExplicitProfilePreferences() {
        val file = Files.createTempFile("merged-rendering-preferences", ".properties")
        val store = DesktopStorage.Store(file)
        var profile = 1
        val key: (String) -> String = { ProfileScopedKey.of(it, profile) }
        val hero = DesktopHeroRatingPreference(store, key)
        val subtitles = DesktopSubtitleRendererPreference(store, key)
        try {
            assertNull(hero.load())
            assertNull(subtitles.load())
            hero.save(false)
            subtitles.save(true)
            profile = 2
            assertNull(hero.load())
            assertNull(subtitles.load())
            hero.save(true)
            subtitles.save(false)
            store.flushSync()

            val reloadedStore = DesktopStorage.Store(file)
            val reloadedHero = DesktopHeroRatingPreference(reloadedStore, key)
            val reloadedSubtitles = DesktopSubtitleRendererPreference(reloadedStore, key)
            assertEquals(true, reloadedHero.load())
            assertEquals(false, reloadedSubtitles.load())
            profile = 1
            assertEquals(false, reloadedHero.load())
            assertEquals(true, reloadedSubtitles.load())
        } finally {
            store.flushSync()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun playerPackSelectionDoesNotChangeTheStreamPackAndMissingPacksFallBack() {
        val streamPack = StreamBadgeImport(
            sourceUrl = "https://example.test/streams.json",
            filters = listOf(StreamBadgeFilter(name = "HDR", pattern = "HDR")),
        )
        val playerPack = streamPack.copy(sourceUrl = "https://example.test/player.json", isActive = false)
        val rules = StreamBadgeRules(imports = listOf(streamPack, playerPack)).normalized()
        assertEquals(streamPack.sourceUrl, rules.forPlayer("").imports.single { it.isActive }.sourceUrl)
        assertEquals(playerPack.sourceUrl, rules.forPlayer(playerPack.sourceUrl).imports.single { it.isActive }.sourceUrl)
        assertEquals(streamPack.sourceUrl, rules.imports.single { it.isActive }.sourceUrl)
        val withoutPlayerPack = rules.removeSource(playerPack.sourceUrl)
        assertEquals(streamPack.sourceUrl, withoutPlayerPack.forPlayer(playerPack.sourceUrl).imports.single { it.isActive }.sourceUrl)
    }
}
