package com.nuvio.app.features.plugins

import com.nuvio.app.core.storage.DesktopStorage
import java.util.Locale
import kotlinx.serialization.json.Json

internal object PluginStorage {
    private const val pluginsStateKey = "plugins_state"
    private val store = DesktopStorage.store("nuvio_plugins")

    fun loadState(profileId: Int): String? =
        store.getString("${pluginsStateKey}_$profileId")

    fun saveState(profileId: Int, payload: String) {
        store.putString("${pluginsStateKey}_$profileId", payload)
        removeOrphanedScraperCode(profileId, payload)
    }

    /**
     * Scraper code is stored per scraper and never removed with its scraper or repository.
     * The saved state is what the next launch restores, and the repository writes each
     * scraper's code before saving state, so code for any scraper missing from it is dead.
     */
    private fun removeOrphanedScraperCode(profileId: Int, payload: String) {
        val state = runCatching { stateJson.decodeFromString<StoredPluginsState>(payload) }.getOrNull() ?: return
        val keep = state.scrapers.mapTo(HashSet()) { scraper -> scraperCodeKey(profileId, scraper.id) }
        val orphaned = store.keysWithPrefix("scraper_code_${profileId}_").filterNot(keep::contains)
        if (orphaned.isNotEmpty()) store.removeAll(orphaned)
    }

    private val stateJson = Json { ignoreUnknownKeys = true }

    fun hasScraperCode(profileId: Int, scraperId: String): Boolean =
        store.contains(scraperCodeKey(profileId, scraperId))

    fun loadScraperCode(profileId: Int, scraperId: String): String? =
        store.getString(scraperCodeKey(profileId, scraperId))

    fun saveScraperCode(
        profileId: Int,
        scraperId: String,
        code: String,
        overwrite: Boolean,
    ): Boolean {
        val key = scraperCodeKey(profileId, scraperId)
        if (!overwrite && store.contains(key)) return true
        return runCatching { store.putString(key, code) }.isSuccess
    }

    fun loadScraperSettings(scraperId: String): String? =
        store.getString("settings_${scraperId}")

    fun saveScraperSettings(scraperId: String, payload: String) {
        store.putString("settings_${scraperId}", payload)
    }

    private fun scraperCodeKey(profileId: Int, scraperId: String): String =
        "scraper_code_${profileId}_${pluginDigestHex("SHA256", scraperId)}"
}

internal fun currentPluginPlatform(): String = "desktop"

internal fun currentPluginPlatformTags(): Set<String> {
    val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    val osTag = when {
        osName.contains("mac") || osName.contains("darwin") -> "macos"
        osName.contains("win") -> "windows"
        osName.contains("linux") -> "linux"
        else -> null
    }
    return buildSet {
        add(currentPluginPlatform())
        add("jvm")
        osTag?.let(::add)
    }
}

internal fun currentEpochMillis(): Long = System.currentTimeMillis()
