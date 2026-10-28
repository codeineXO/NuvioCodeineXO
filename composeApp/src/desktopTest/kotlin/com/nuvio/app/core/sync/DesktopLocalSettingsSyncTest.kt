package com.nuvio.app.core.sync

import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.ui.PosterCardStyleUiState
import com.nuvio.app.features.details.MetaEpisodeCardStyle
import com.nuvio.app.features.details.MetaScreenSettingsUiState
import com.nuvio.app.features.player.*
import com.nuvio.app.features.settings.ThemeSettingsStorage
import java.nio.file.Files
import kotlinx.serialization.json.*
import kotlin.test.*

class DesktopLocalSettingsSyncTest {
    private val playerPreferences = mapOf(
        "player_ui_mode" to "official",
        "anime_upscaler_enabled" to "true",
        "anime_upscaler_option" to "always_on",
        "anime_upscaler_mode" to "sharp_detail",
        "svp_motion_option" to "global",
        "playback_buffer_preset" to "large",
        "audio_night_mode_enabled" to "true",
        "show_seekbar_while_seeking" to "false",
        "show_clock_in_player" to "false",
        "show_remaining_time" to "true",
        "seek_preview_enabled" to "false",
        "seek_preview_while_dragging" to "false",
        "seek_preview_while_hovering" to "false",
        "seek_preview_while_keyboard_seeking" to "false",
        "seek_preview_thumbnail_count" to "1",
        "seekr_api_key" to "local-test-key",
        "subtitle_font" to "Consolas",
        "subtitle_outline_effect" to "soft_glow",
    )
    private val themePreferences = mapOf(
        "aura_background_enabled" to "false",
        "aura_settings" to """{"version":1,"palette":"sunset"}""",
        "frosted_glass_enabled" to "false",
    )

    private fun conflictingPayload(preferences: Map<String, String>) = buildJsonObject {
        preferences.forEach { (key, value) ->
            put(key, when (value) {
                "true", "false" -> encodeSyncBoolean(value != "true")
                "1" -> encodeSyncInt(5)
                else -> encodeSyncString("remote-value")
            })
        }
    }

    @Test
    fun productionSyncPreservesEveryLocalKeyAcrossProfilesAndDiskReload() {
        val file = Files.createTempFile("local-only-settings", ".properties")
        val store = DesktopStorage.Store(file)
        var profile = 1
        val key: (String) -> String = { ProfileScopedKey.of(it, profile) }
        val player = PlayerSettingsStorage.SyncStorage(store, key)
        val theme = ThemeSettingsStorage.SyncStorage(store, key)
        val local = playerPreferences + themePreferences
        try {
            for (index in 1..2) {
                profile = index
                val chosen = if (index == 1) local else local.mapValues { (name, value) ->
                    when (name) {
                        "player_ui_mode" -> "codeine_xo"
                        "anime_upscaler_option" -> "auto"
                        "anime_upscaler_mode" -> "fast"
                        "svp_motion_option" -> "off"
                        "playback_buffer_preset" -> "low"
                        "seekr_api_key" -> "second-local-test-key"
                        "subtitle_font" -> "Arial"
                        "subtitle_outline_effect" -> "outline"
                        "aura_settings" -> """{"version":1,"palette":"arctic"}"""
                        else -> when (value) { "true" -> "false"; "false" -> "true"; else -> "3" }
                    }
                }
                chosen.forEach { (name, value) -> store.putString(key(name), value) }
                store.putBoolean(key("show_loading_overlay"), false)
                store.putString(key("selected_theme"), "WHITE")
                assertTrue(player.exportToSyncPayload().keys.intersect(playerPreferences.keys).isEmpty())
                assertTrue(theme.exportToSyncPayload().keys.intersect(themePreferences.keys).isEmpty())
                assertEquals(false, player.exportToSyncPayload().decodeSyncBoolean("show_loading_overlay"))
                assertEquals("WHITE", theme.exportToSyncPayload().decodeSyncString("selected_theme"))

                player.replaceFromSyncPayload(JsonObject(conflictingPayload(playerPreferences) + mapOf(
                    "show_loading_overlay" to encodeSyncBoolean(true),
                    "subtitle_text_color" to encodeSyncString("#FFEEDD"),
                )))
                theme.replaceFromSyncPayload(JsonObject(conflictingPayload(themePreferences) + mapOf(
                    "selected_theme" to encodeSyncString("GOLD"),
                )))
                assertEquals(true, store.getBoolean(key("show_loading_overlay")))
                assertEquals("#FFEEDD", store.getString(key("subtitle_text_color")))
                assertEquals("GOLD", store.getString(key("selected_theme")))
                chosen.forEach { (name, value) -> assertEquals(value, store.getString(key(name)), name) }
                player.replaceFromSyncPayload(JsonObject(emptyMap()))
                theme.replaceFromSyncPayload(JsonObject(emptyMap()))
                player.replaceFromSyncPayload(conflictingPayload(playerPreferences))
                theme.replaceFromSyncPayload(conflictingPayload(themePreferences))
                chosen.forEach { (name, value) -> assertEquals(value, store.getString(key(name)), name) }
            }
            profile = 3
            player.replaceFromSyncPayload(conflictingPayload(playerPreferences))
            theme.replaceFromSyncPayload(conflictingPayload(themePreferences))
            local.keys.forEach { assertNull(store.getString(key(it)), it) }
            store.flushSync()
            val reloaded = DesktopStorage.Store(file)
            for (index in 1..2) {
                profile = index
                local.keys.forEach { assertEquals(store.getString(key(it)), reloaded.getString(key(it)), it) }
            }
            profile = 1
            local.forEach { (name, value) -> assertEquals(value, reloaded.getString(key(name)), name) }
        } finally {
            store.flushSync()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun embeddedPreferencesSurviveReplacementAndReloadWhileSharedFieldsSync() {
        val file = Files.createTempFile("local-payload-settings", ".properties")
        val store = DesktopStorage.Store(file)
        try {
            for ((field, initial) in listOf("glowEnabled" to "false", "episodeCardStyle" to "\"enhanced\"")) {
                val local = """{"$field":$initial,"shared":1}"""
                val remote = """{"$field":null,"shared":2,"futureField":true}"""
                val upload = if (field == "glowEnabled") posterSettingsForSync(local) else metaSettingsForSync(local)
                assertFalse(Json.parseToJsonElement(upload).jsonObject.containsKey(field))
                assertEquals(1, Json.parseToJsonElement(upload).jsonObject["shared"]?.jsonPrimitive?.intOrNull)
                val restore: (String, String?) -> String = if (field == "glowEnabled") {
                    { incoming, existing -> restorePosterSettingsFromSync(incoming, existing) }
                } else {
                    { incoming, existing -> restoreMetaSettingsFromSync(incoming, existing) }
                }
                for (profile in 1..2) {
                    val key = ProfileScopedKey.of(field, profile)
                    val second = if (field == "glowEnabled") "true" else "\"list\""
                    store.putString(key, if (profile == 1) local else """{"$field":$second}""")
                    val expected = Json.parseToJsonElement(store.getString(key)!!).jsonObject[field]
                    store.putString(key, restore(remote, store.getString(key)))
                    val merged = Json.parseToJsonElement(store.getString(key)!!).jsonObject
                    assertEquals(expected, merged[field])
                    assertEquals(2, merged["shared"]?.jsonPrimitive?.intOrNull)
                    assertEquals(true, merged["futureField"]?.jsonPrimitive?.booleanOrNull)
                    for (incoming in listOf("", "{}", "bad json", "[]", "null")) {
                        store.putString(key, restore(incoming, store.getString(key)))
                        assertEquals(expected, Json.parseToJsonElement(store.getString(key)!!).jsonObject[field])
                    }
                }
                assertFalse(Json.parseToJsonElement(restore(remote, null)).jsonObject.containsKey(field))
                assertEquals("", restore("", null))
                store.flushSync()
                val reloaded = DesktopStorage.Store(file)
                for (profile in 1..2) {
                    val key = ProfileScopedKey.of(field, profile)
                    assertEquals(store.getString(key), reloaded.getString(key))
                }
            }
        } finally {
            store.flushSync()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun signaturesIgnoreLocalChangesButDetectSharedChanges() {
        val player = PlayerSettingsUiState()
        val variants = listOf(
            player.copy(playerUiMode = PlayerUiMode.OFFICIAL),
            player.copy(animeUpscalerEnabled = true),
            player.copy(animeUpscalerOption = AnimeUpscalerOption.ALWAYS_ON),
            player.copy(animeUpscalerMode = AnimeUpscalerMode.SHARP_DETAIL),
            player.copy(svpMotionOption = SvpMotionOption.GLOBAL),
            player.copy(playbackBufferPreset = PlaybackBufferPreset.LARGE),
            player.copy(audioNightModeEnabled = true),
            player.copy(showSeekbarWhileSeeking = false),
            player.copy(showClockInPlayer = false),
            player.copy(showRemainingTime = true),
            player.copy(seekPreviewEnabled = false),
            player.copy(seekPreviewWhileDragging = false),
            player.copy(seekPreviewWhileHovering = false),
            player.copy(seekPreviewWhileKeyboardSeeking = false),
            player.copy(seekPreviewThumbnailCount = 1),
            player.copy(seekrApiKey = "test-local-key"),
            player.copy(subtitleStyle = player.subtitleStyle.copy(fontName = "Consolas")),
            player.copy(subtitleStyle = player.subtitleStyle.copy(outlineEffect = SubtitleOutlineEffect.SOFT_GLOW)),
        )
        variants.forEach { assertEquals(playerSettingsSyncState(player), playerSettingsSyncState(it)) }
        assertNotEquals(playerSettingsSyncState(player), playerSettingsSyncState(player.copy(showLoadingOverlay = false)))
        assertNotEquals(playerSettingsSyncState(player), playerSettingsSyncState(player.copy(
            subtitleStyle = player.subtitleStyle.copy(textColor = Color.Red),
        )))
        val poster = PosterCardStyleUiState()
        assertEquals(posterSettingsSyncState(poster), posterSettingsSyncState(poster.copy(glowEnabled = false)))
        assertNotEquals(posterSettingsSyncState(poster), posterSettingsSyncState(poster.copy(widthDp = 200)))
        val meta = MetaScreenSettingsUiState()
        MetaEpisodeCardStyle.entries.forEach {
            assertEquals(metaSettingsSyncState(meta), metaSettingsSyncState(meta.copy(episodeCardStyle = it)))
        }
        assertNotEquals(metaSettingsSyncState(meta), metaSettingsSyncState(meta.copy(showOverallRatings = false)))
    }

    @Test
    fun otherPlatformsKeepPayloadsAndStateUntouched() {
        val payload = """{"glowEnabled":false,"episodeCardStyle":"list"}"""
        assertEquals(payload, posterSettingsForSync(payload, desktop = false))
        assertEquals(payload, metaSettingsForSync(payload, desktop = false))
        assertEquals(payload, restorePosterSettingsFromSync(payload, "{}", desktop = false))
        assertEquals(payload, restoreMetaSettingsFromSync(payload, "{}", desktop = false))
        val player = PlayerSettingsUiState(seekrApiKey = "test-key")
        assertEquals(player, playerSettingsSyncState(player, desktop = false))
        val poster = PosterCardStyleUiState(glowEnabled = false)
        assertEquals(poster, posterSettingsSyncState(poster, desktop = false))
        val meta = MetaScreenSettingsUiState(episodeCardStyle = MetaEpisodeCardStyle.Enhanced)
        assertEquals(meta, metaSettingsSyncState(meta, desktop = false))
    }
}
