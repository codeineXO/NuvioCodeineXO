package com.nuvio.app.features.tmdb

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.player.MediaClassifier
import com.nuvio.app.features.player.MediaContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentClassificationEnrichmentTest {
    @Test fun canonicalEvidenceSurvivesLocalizedOrDisabledDisplayReplacement() {
        val original = MetaDetails("tt123", "series", "Title")
        for (replaceDisplay in listOf(false, true)) {
            val result = TmdbMetadataService.applyEnrichment(original, enrichment(), emptyMap(),
                TmdbSettings(enabled = true, useBasicInfo = replaceDisplay, useDetails = replaceDisplay))
            assertEquals(MediaContentType.ANIME, MediaClassifier.classify(result))
            assertEquals(if (replaceDisplay) listOf("アニメーション") else emptyList(), result.genres)
            assertEquals(if (replaceDisplay) "JP" else null, result.country)
        }
        val standalone = TmdbMetadataService.buildStandaloneMeta("series", "tt123", 123, enrichment())
        assertEquals(MediaContentType.ANIME, MediaClassifier.classify(standalone))
    }

    @Test fun disabledIntegrationDoesNotIntroduceCanonicalHints() {
        val result = TmdbMetadataService.applyEnrichment(MetaDetails("tt123", "series", "Title"), enrichment(), emptyMap(),
            TmdbSettings(enabled = false, useBasicInfo = false, useDetails = false))
        assertNull(result.classificationHints)
        assertEquals(MediaContentType.LIVE_ACTION, MediaClassifier.classify(result))
    }

    @Test fun explicitAddonAnimeGenreSurvivesLocalizedReplacement() {
        val result = TmdbMetadataService.applyEnrichment(MetaDetails("tt123", "series", "Title", genres = listOf("Anime")),
            enrichment().copy(countries = listOf("US"), language = "en"), emptyMap(), TmdbSettings(enabled = true))
        assertEquals(MediaContentType.ANIME, MediaClassifier.classify(result))
    }

    private fun enrichment() = TmdbEnrichment(
        localizedTitle = null, description = null, genres = listOf("アニメーション"), backdrop = null, logo = null,
        poster = null, people = emptyList(), director = emptyList(), writer = emptyList(), releaseInfo = null,
        rating = null, runtimeMinutes = null, ageRating = null, status = null, countries = listOf("JP"), language = "ja",
        productionCompanies = emptyList(), networks = emptyList(), isAnimation = true,
    )
}
