package com.nuvio.app.features.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nuvio.app.core.ui.*
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import java.nio.file.Files
import kotlin.test.*

class AuraSettingsTest {
    @Test fun settingsRoundTripAndFallbacks() {
        val settings = AuraSettings(AuraPalette.CUSTOM, AuraIntensity.VIVID, false, CustomThemeColors(0x123456, 0xABCDEF, 0x987654))
        assertEquals(settings, AuraSettings.decode(settings.encode()))
        listOf(null, "", "bad json", "[]", "{\"version\":2}").forEach {
            assertEquals(AuraSettings(), AuraSettings.decode(it))
        }
        val invalid = AuraSettings.decode("""{"version":1,"palette":"future","intensity":"unknown","animated":false,"colors":["bad"]}""")
        assertEquals(AuraSettings(animated = false), invalid)
        val badColors = AuraSettings.decode("""{"version":1,"palette":"arctic","colors":["#112233","oops","#445566"]}""")
        assertEquals(AuraPalette.ARCTIC, badColors.palette)
        assertEquals(CustomThemeColors.Default, badColors.customColors)
    }

    @Test fun originalAndPresetColors() {
        val original = resolveAuraPalette(AuraSettings(), ThemeColors.White)
        assertEquals(Color(0xFF6366F1), original[0].inner)
        assertEquals(listOf(0xFF4338CA, 0xFF0284C7, 0xFF9333EA, 0xFFE11D48).map { Color(it) }, original.map { it.outer })
        val presets = mapOf(
            AuraPalette.ARCTIC to listOf(0x38BDF8,0x22D3EE,0x3B82F6,0x818CF8),
            AuraPalette.NORTHERN_LIGHTS to listOf(0x22C55E,0x14B8A6,0x8B5CF6,0x06B6D4),
            AuraPalette.SUNSET to listOf(0xFB7185,0xFB923C,0xD946EF,0xF43F5E),
            AuraPalette.ROSE_QUARTZ to listOf(0xF9A8D4,0xC084FC,0xA78BFA,0xFDA4AF),
            AuraPalette.GOLDEN_HOUR to listOf(0xFBBF24,0xF59E0B,0xD97706,0xFB923C),
            AuraPalette.DEEP_SPACE to listOf(0x1D4ED8,0x4338CA,0x7C3AED,0x6366F1),
        )
        presets.forEach { (palette, colors) ->
            val resolved = resolveAuraPalette(AuraSettings(palette), ThemeColors.White)
            assertEquals(colors.map { Color(it or 0xFF000000.toInt()) }, resolved.map { it.inner })
            assertTrue(resolved.all { it.inner == it.outer })
        }
    }

    @Test fun themeSamplingAndCustomMapping() {
        val settings = AuraSettings(AuraPalette.MATCH_THEME)
        assertTrue(resolveAuraPalette(settings, ThemeColors.White).all { it.inner == ThemeColors.White.secondary })
        val theme = ThemeColors.White.copy(accentGradient = listOf(Color.Black, Color.White))
        val sampled = resolveAuraPalette(settings, theme)
        assertEquals(List(4) { lerp(Color.Black, Color.White, it / 3f) }, sampled.map { it.inner })
        val gold = resolveAuraPalette(settings, ThemeColors.Gold)
        assertEquals(ThemeColors.Gold.accentGradient.first(), gold.first().inner)
        assertEquals(ThemeColors.Gold.accentGradient.last(), gold.last().inner)
        val custom = resolveAuraPalette(AuraSettings(AuraPalette.CUSTOM), theme)
        assertEquals(Color(0xFFB75AFF), custom[0].inner)
        assertEquals(Color(0xFFEC70A9), custom[1].inner)
        assertEquals(Color(0xFFFFB37A), custom[2].inner)
        assertEquals(lerp(custom[0].inner, custom[2].inner, 0.5f), custom[3].inner)
        assertEquals(listOf(0.55f, 1f, 1.4f), AuraIntensity.entries.map { it.multiplier })
    }

    @Test fun storagePersistsLocallyAcrossProfilesAndReload() {
        val file = Files.createTempFile("aura-settings", ".properties")
        val store = DesktopStorage.Store(file)
        var profile = 1
        val storage = DesktopAuraSettingsStorage(store) { ProfileScopedKey.of("aura_settings", profile) }
        try {
            assertNull(storage.loadAuraSettings())
            val first = AuraSettings(AuraPalette.SUNSET).encode()
            storage.saveAuraSettings(first)
            profile = 2
            assertNull(storage.loadAuraSettings())
            val second = AuraSettings(AuraPalette.ARCTIC, animated = false).encode()
            storage.saveAuraSettings(second)
            profile = 1
            assertEquals(first, storage.loadAuraSettings())
            store.flushSync()
            val reloaded = DesktopAuraSettingsStorage(DesktopStorage.Store(file)) { ProfileScopedKey.of("aura_settings", 2) }
            assertEquals(second, reloaded.loadAuraSettings())
        } finally {
            store.flushSync()
            Files.deleteIfExists(file)
        }
    }
}
