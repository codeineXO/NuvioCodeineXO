package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

internal fun resolveDesktopAppLocale(
    languageCode: String,
    deviceLocale: Locale,
): Locale = if (languageCode.equals(AppLanguage.DEVICE.code, ignoreCase = true)) {
    deviceLocale
} else {
    Locale.forLanguageTag(languageCode)
}

internal actual object ThemeSettingsStorage : AuraSettingsStore {
    private const val selectedThemeKey = "selected_theme"
    private const val customThemeColorsKey = "custom_theme_colors"
    private const val frostedGlassEnabledKey = "frosted_glass_enabled"
    private const val amoledEnabledKey = "amoled_enabled"
    private const val liquidGlassNativeTabBarEnabledKey = "liquid_glass_native_tab_bar_enabled"
    private const val desktopNavigationLayoutKey = "desktop_navigation_layout"
    private const val selectedAppLanguageKey = "selected_app_language"
    private const val navBarStyleKey = "nav_bar_style"
    private const val navBarGlowEnabledKey = "nav_bar_glow_enabled"
    private const val auraSettingsKey = "aura_settings"
    private const val auraBackgroundEnabledKey = "aura_background_enabled"
    // Aura and frosted glass stay local to each profile and are never replaced by account sync.
    private val deviceLocale = Locale.getDefault()
    private val store = DesktopStorage.store("nuvio_theme_settings")

    actual fun loadSelectedTheme(): String? =
        store.getString(ProfileScopedKey.of(selectedThemeKey))

    actual fun saveSelectedTheme(themeName: String) {
        store.putString(ProfileScopedKey.of(selectedThemeKey), themeName)
    }

    actual fun loadCustomThemeColors(): String? =
        store.getString(ProfileScopedKey.of(customThemeColorsKey))

    actual fun saveCustomThemeColors(colors: String) {
        store.putString(ProfileScopedKey.of(customThemeColorsKey), colors)
    }

    private val frostedGlassStorage = DesktopFrostedGlassSettingsStorage(store) {
        ProfileScopedKey.of(frostedGlassEnabledKey)
    }
    override fun loadFrostedGlassEnabled(): Boolean? = frostedGlassStorage.loadFrostedGlassEnabled()
    override fun saveFrostedGlassEnabled(enabled: Boolean) = frostedGlassStorage.saveFrostedGlassEnabled(enabled)

    actual fun loadAmoledEnabled(): Boolean? =
        store.getBoolean(ProfileScopedKey.of(amoledEnabledKey))

    actual fun saveAmoledEnabled(enabled: Boolean) {
        store.putBoolean(ProfileScopedKey.of(amoledEnabledKey), enabled)
    }

    actual fun loadLiquidGlassNativeTabBarEnabled(): Boolean? =
        store.getBoolean(ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey))

    actual fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean) {
        store.putBoolean(ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey), enabled)
    }

    actual fun loadDesktopNavigationLayout(): String? =
        store.getString(ProfileScopedKey.of(desktopNavigationLayoutKey))

    actual fun saveDesktopNavigationLayout(layoutName: String) {
        store.putString(ProfileScopedKey.of(desktopNavigationLayoutKey), layoutName)
    }

    actual fun loadSelectedAppLanguage(): String? =
        store.getString(selectedAppLanguageKey)
            ?: Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() }

    actual fun saveSelectedAppLanguage(languageCode: String) {
        store.putString(selectedAppLanguageKey, languageCode)
    }

    actual fun applySelectedAppLanguage(languageCode: String) {
        Locale.setDefault(resolveDesktopAppLocale(languageCode, deviceLocale))
    }

    actual fun loadNavBarStyle(): String? =
        store.getString(ProfileScopedKey.of(navBarStyleKey))

    actual fun saveNavBarStyle(styleKey: String) {
        store.putString(ProfileScopedKey.of(navBarStyleKey), styleKey)
    }

    actual fun loadNavBarGlowEnabled(): Boolean? =
        store.getBoolean(ProfileScopedKey.of(navBarGlowEnabledKey))

    actual fun saveNavBarGlowEnabled(enabled: Boolean) {
        store.putBoolean(ProfileScopedKey.of(navBarGlowEnabledKey), enabled)
    }

    actual fun loadAuraBackgroundEnabled(): Boolean? =
        store.getBoolean(ProfileScopedKey.of(auraBackgroundEnabledKey))

    actual fun saveAuraBackgroundEnabled(enabled: Boolean) {
        store.putBoolean(ProfileScopedKey.of(auraBackgroundEnabledKey), enabled)
    }

    private val auraStorage = DesktopAuraSettingsStorage(store) { ProfileScopedKey.of(auraSettingsKey) }
    override fun loadAuraSettings(): String? = auraStorage.loadAuraSettings()
    override fun saveAuraSettings(value: String) = auraStorage.saveAuraSettings(value)

    private val syncStorage by lazy { SyncStorage(store) }

    actual fun exportToSyncPayload(): JsonObject = syncStorage.exportToSyncPayload()

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        syncStorage.replaceFromSyncPayload(payload)
        applySelectedAppLanguage(loadSelectedAppLanguage() ?: AppLanguage.ENGLISH.code)
    }

    /** Account sync only touches shared keys; local preferences use the same store unchanged. */
    internal class SyncStorage(
        private val store: DesktopStorage.Store,
        private val profileKey: (String) -> String = ProfileScopedKey::of,
    ) {
        private fun scoped(key: String): String = profileKey(key)

        private val profileScopedSyncKeys = listOf(
            selectedThemeKey,
            customThemeColorsKey,
            amoledEnabledKey,
            liquidGlassNativeTabBarEnabledKey,
            desktopNavigationLayoutKey,
            navBarStyleKey,
            navBarGlowEnabledKey,
        )

        fun exportToSyncPayload(): JsonObject = buildJsonObject {
            store.getString(scoped(selectedThemeKey))?.let { put(selectedThemeKey, encodeSyncString(it)) }
            store.getString(scoped(customThemeColorsKey))?.let { put(customThemeColorsKey, encodeSyncString(it)) }
            store.getBoolean(scoped(amoledEnabledKey))?.let { put(amoledEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(liquidGlassNativeTabBarEnabledKey))?.let { put(liquidGlassNativeTabBarEnabledKey, encodeSyncBoolean(it)) }
            store.getString(scoped(desktopNavigationLayoutKey))?.let { put(desktopNavigationLayoutKey, encodeSyncString(it)) }
            store.getString(scoped(navBarStyleKey))?.let { put(navBarStyleKey, encodeSyncString(it)) }
            store.getBoolean(scoped(navBarGlowEnabledKey))?.let { put(navBarGlowEnabledKey, encodeSyncBoolean(it)) }
        }

        fun replaceFromSyncPayload(payload: JsonObject) {
            store.removeAll(profileScopedSyncKeys.map(::scoped))
            payload.decodeSyncString(selectedThemeKey)?.let { store.putString(scoped(selectedThemeKey), it) }
            payload.decodeSyncString(customThemeColorsKey)?.let { store.putString(scoped(customThemeColorsKey), it) }
            payload.decodeSyncBoolean(amoledEnabledKey)?.let { store.putBoolean(scoped(amoledEnabledKey), it) }
            payload.decodeSyncBoolean(liquidGlassNativeTabBarEnabledKey)?.let { store.putBoolean(scoped(liquidGlassNativeTabBarEnabledKey), it) }
            payload.decodeSyncString(desktopNavigationLayoutKey)?.let { store.putString(scoped(desktopNavigationLayoutKey), it) }
            payload.decodeSyncString(navBarStyleKey)?.let { store.putString(scoped(navBarStyleKey), it) }
            payload.decodeSyncBoolean(navBarGlowEnabledKey)?.let { store.putBoolean(scoped(navBarGlowEnabledKey), it) }
        }
    }
}

internal class DesktopAuraSettingsStorage(
    private val store: DesktopStorage.Store,
    private val scopedKey: () -> String,
) : AuraSettingsStore {
    override fun loadAuraSettings(): String? = store.getString(scopedKey())
    override fun saveAuraSettings(value: String) { store.putString(scopedKey(), value) }
}

internal class DesktopFrostedGlassSettingsStorage(
    private val store: DesktopStorage.Store,
    private val scopedKey: () -> String,
) : FrostedGlassSettingsStore {
    override fun loadFrostedGlassEnabled(): Boolean? = store.getBoolean(scopedKey())
    override fun saveFrostedGlassEnabled(enabled: Boolean) = store.putBoolean(scopedKey(), enabled)
}
