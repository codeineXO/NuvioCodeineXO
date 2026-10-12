package com.nuvio.app.features.plugins

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginPersistenceTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun recentRepositoryDoesNotRequireRefresh() {
        val now = 10 * PLUGIN_REPOSITORY_REFRESH_INTERVAL_MS

        assertFalse(
            isPluginRepositoryRefreshDue(
                lastUpdatedEpochMs = now - PLUGIN_REPOSITORY_REFRESH_INTERVAL_MS + 1L,
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun staleOrUninitializedRepositoryRequiresRefresh() {
        val now = 10 * PLUGIN_REPOSITORY_REFRESH_INTERVAL_MS

        assertTrue(
            isPluginRepositoryRefreshDue(
                lastUpdatedEpochMs = now - PLUGIN_REPOSITORY_REFRESH_INTERVAL_MS,
                nowEpochMs = now,
            ),
        )
        assertTrue(isPluginRepositoryRefreshDue(lastUpdatedEpochMs = 0L, nowEpochMs = now))
    }

    @Test
    fun metadataStateDoesNotSerializeScraperSourceCode() {
        val sourceCode = "module.exports = " + "x".repeat(4 * 1024 * 1024)
        val stored = PluginsUiState(
            scrapers = listOf(pluginScraper(sourceCode)),
        ).toStoredPluginsState()

        val encoded = json.encodeToString(stored)

        assertNull(stored.scrapers.single().code)
        assertTrue(encoded.length < 2_048)
        assertFalse(sourceCode in encoded)
    }

    @Test
    fun cachedScraperCodeRestoresOfflineWithoutMigration() {
        val sourceCode = "offline scraper source"
        val stored = pluginScraper(sourceCode).toStoredPluginScraper()

        val restored = stored.restorePluginScraper { scraperId ->
            if (scraperId == stored.id) sourceCode else null
        }

        assertEquals(sourceCode, restored?.scraper?.code)
        assertFalse(restored?.requiresMigration ?: true)
    }

    @Test
    fun legacyEmbeddedScraperCodeIsPreservedForMigration() {
        val sourceCode = "legacy scraper source"
        val stored = pluginScraper(sourceCode)
            .toStoredPluginScraper()
            .copy(code = sourceCode)

        val restored = stored.restorePluginScraper { null }

        assertEquals(sourceCode, restored?.scraper?.code)
        assertTrue(restored?.requiresMigration == true)
    }

    @Test
    fun pluginRepositoryItemDefaultsToEnabled() {
        val repo = PluginRepositoryItem(
            manifestUrl = "https://example.com/manifest.json",
            name = "Test Repo",
        )
        assertTrue(repo.enabled)
    }

    @Test
    fun storedPluginRepositorySerializesAndDeserializesEnabledFlag() {
        val state = PluginsUiState(
            repositories = listOf(
                PluginRepositoryItem(
                    manifestUrl = "https://example.com/manifest.json",
                    name = "Test Repo",
                    enabled = false,
                ),
            ),
        )
        val stored = state.toStoredPluginsState()
        assertFalse(stored.repositories.single().enabled)

        val encoded = json.encodeToString(stored)
        val decoded = json.decodeFromString<StoredPluginsState>(encoded)
        assertFalse(decoded.repositories.single().enabled)
    }

    @Test
    fun legacyStoredPluginRepositoryWithoutEnabledDeserializesAsEnabled() {
        val legacyJson = """{"pluginsEnabled":true,"repositories":[{"manifestUrl":"https://example.com/manifest.json","name":"Legacy Repo"}],"scrapers":[]}"""
        val decoded = json.decodeFromString<StoredPluginsState>(legacyJson)
        assertTrue(decoded.repositories.single().enabled)
    }

    private fun pluginScraper(code: String): PluginScraper = PluginScraper(
        id = "https://plugins.example/manifest.json:scraper",
        repositoryUrl = "https://plugins.example/manifest.json",
        name = "Scraper",
        description = "",
        version = "1.0.0",
        filename = "scraper.js",
        supportedTypes = listOf("movie", "tv"),
        enabled = true,
        manifestEnabled = true,
        code = code,
    )
}
