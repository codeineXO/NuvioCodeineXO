package com.nuvio.app.features.settings

import kotlinx.serialization.json.JsonObject

/** Optional local surface preferences, including the legacy player appearance setting. */
internal interface FrostedGlassSettingsStore {
    fun loadSurfaceStyle(): String? = null
    fun saveSurfaceStyle(value: String) {}
    fun loadFrostedGlassEnabled(): Boolean? = null
    fun saveFrostedGlassEnabled(enabled: Boolean) {}
}

internal interface AuraSettingsStore : FrostedGlassSettingsStore {
    fun loadAuraSettings(): String? = null
    fun saveAuraSettings(value: String) {}
}

internal expect object ThemeSettingsStorage : AuraSettingsStore {
    fun loadSelectedTheme(): String?
    fun saveSelectedTheme(themeName: String)
    fun loadCustomThemeColors(): String?
    fun saveCustomThemeColors(colors: String)
    fun loadAmoledEnabled(): Boolean?
    fun saveAmoledEnabled(enabled: Boolean)
    fun loadLiquidGlassNativeTabBarEnabled(): Boolean?
    fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean)
    fun loadDesktopNavigationLayout(): String?
    fun saveDesktopNavigationLayout(layoutName: String)
    fun loadSelectedAppLanguage(): String?
    fun saveSelectedAppLanguage(languageCode: String)
    fun applySelectedAppLanguage(languageCode: String)
    fun loadNavBarStyle(): String?
    fun saveNavBarStyle(styleKey: String)
    fun loadNavBarGlowEnabled(): Boolean?
    fun saveNavBarGlowEnabled(enabled: Boolean)
    fun loadAuraBackgroundEnabled(): Boolean?
    fun saveAuraBackgroundEnabled(enabled: Boolean)
    fun loadAppScale(): Int?
    fun saveAppScale(scale: Int)
    fun exportToSyncPayload(): JsonObject
    fun replaceFromSyncPayload(payload: JsonObject)
}
