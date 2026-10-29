package com.nuvio.app.features.details

import com.nuvio.app.features.plugins.PluginIdDiagnostics
import com.nuvio.app.features.plugins.PluginIdResolution
import com.nuvio.app.features.tmdb.TmdbEnrichment
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MetadataProvenanceTest {
    @Test fun detectsNamespacesWithoutGuessingNumericOrContentTypePrefixes() {
        mapOf(
            " tt0133093 " to "IMDb", "tt0903747:1:2" to "IMDb", "TMDB/603" to "TMDB",
            "kitsu:7442:1:3" to "Kitsu", "mal:20" to "MyAnimeList", "anilist:1" to "AniList",
            "tvdb:81189" to "TVDB", "anidb:69" to "AniDB", "custom:abc" to "custom",
            "movie:tt0133093" to "IMDb", "series:tmdb:1396" to "TMDB",
        ).forEach { (id, provider) -> assertEquals(provider, metadataIdProvider(id), id) }
        listOf("603", "603:1:2", "movie:603", "unrecognized", "ttinvalid", "").forEach {
            assertNull(metadataIdProvider(it), it)
        }
    }

    @Test fun tmdbEnrichmentPreservesAddonAttributionAndOriginalIdentifiers() {
        val provenance = MetadataProvenance(
            sourceName = "Example addon", sourceId = "example.meta", requestedId = "tmdb:603",
            lookupId = "tt0133093", fetchedAtMs = 1234L, knownIds = mapOf("TVDB" to "123"),
        )
        val original = MetaDetails("tt0133093", "movie", "Original", provenance = provenance)
        val result = TmdbMetadataService.applyEnrichment(original, enrichment(), emptyMap(), TmdbSettings(enabled = true))
        assertEquals(provenance, result.provenance.copy(enrichmentSources = emptySet()))
        assertEquals(setOf("TMDB"), result.provenance.enrichmentSources)
        assertEquals("Localized title", result.name)
        assertSame(original, TmdbMetadataService.applyEnrichment(original, null, emptyMap(), TmdbSettings()))
    }

    @Test fun standaloneTmdbMetadataIdentifiesItsFallbackSource() {
        val result = TmdbMetadataService.buildStandaloneMeta("movie", "tmdb:603", 603, enrichment())
        assertEquals("TMDB", result.provenance.sourceName)
        assertTrue(result.provenance.usedFallback)
        assertEquals("tmdb:603", result.provenance.requestedId)
        assertEquals("603", result.provenance.knownIds["TMDB"])
    }

    @Test fun scraperDiagnosticsKeepDifferentOutcomesAndEpisodesSeparate() {
        val first = PluginIdDiagnostics.key("tt0133093", "series", 1, 1)
        val second = PluginIdDiagnostics.key("tt0133093", "tv", 1, 2)
        try {
            PluginIdDiagnostics.record(first, "a", PluginIdResolution("tt0133093", "603", true, "A"))
            PluginIdDiagnostics.record(first, "b", PluginIdResolution("tt0133093", "tt0133093", false, "B"))
            PluginIdDiagnostics.record(second, "a", PluginIdResolution("tt0133093", scraperName = "A"))
            val firstResults = PluginIdDiagnostics.resolutions.value.filterKeys { it.startsWith("$first|") }.values
            assertEquals(setOf("603", "tt0133093"), firstResults.map { it.passedId }.toSet())
            assertEquals(1, firstResults.count { it.resolved })
            PluginIdDiagnostics.clearRequest(first)
            assertTrue(PluginIdDiagnostics.resolutions.value.keys.none { it.startsWith("$first|") })
            assertTrue(PluginIdDiagnostics.resolutions.value.keys.any { it.startsWith("$second|") })
        } finally {
            PluginIdDiagnostics.clearRequest(first)
            PluginIdDiagnostics.clearRequest(second)
        }
    }

    private fun enrichment() = TmdbEnrichment(
        localizedTitle = "Localized title", description = null, genres = emptyList(), backdrop = null, logo = null,
        poster = null, people = emptyList(), director = emptyList(), writer = emptyList(), releaseInfo = null,
        rating = null, runtimeMinutes = null, ageRating = null, status = null, countries = emptyList(), language = "en",
        productionCompanies = emptyList(), networks = emptyList(), isAnimation = false,
    )
}
