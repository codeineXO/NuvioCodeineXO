package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.mdblist.MdbListMetadataService
import com.nuvio.app.features.mdblist.MdbListSettings
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** Owned by the mounted popup, so closing it cancels its pending work. */
private class HomePosterPreviewViewModel(item: MetaPreview, settings: MdbListSettings) : ViewModel() {
    private val mutableItem = MutableStateFlow(item.copy(externalRatings = emptyList()))
    val state = mutableItem.asStateFlow()

    init {
        viewModelScope.launch {
            HomePosterPreviewRepository.load(item, settings) { update -> mutableItem.update(update) }
        }
    }
}

private object HomePosterPreviewRepository {
    suspend fun load(
        item: MetaPreview,
        settings: MdbListSettings,
        publish: ((MetaPreview) -> MetaPreview) -> Unit,
    ) = coroutineScope {
        // Ratings and optional detail enrichment can complete independently.
        launch {
            val ratings = safely {
                withTimeoutOrNull(5_000L) {
                    MdbListMetadataService.getRatings(item.id, item.type, settings)
                }
            }.orEmpty()
            if (ratings.isNotEmpty()) publish { it.copy(externalRatings = ratings) }
        }
        if (item.description.isNullOrBlank() || item.runtime.isNullOrBlank() ||
            item.ageRating.isNullOrBlank() || item.genres.isEmpty() ||
            item.releaseInfo.isNullOrBlank() || item.logo.isNullOrBlank() ||
            (item.banner.isNullOrBlank() && item.landscapePoster.isNullOrBlank()) ||
            (settings.isActive && needsDetailIdentifier(item.id))
        ) {
            val details = safely {
                withTimeoutOrNull(5_000L) { MetaDetailsRepository.fetch(type = item.type, id = item.id) }
            }
            if (details != null) {
                publish { current ->
                    current.copy(
                        banner = current.banner.nonBlankOr(details.background),
                        logo = current.logo.nonBlankOr(details.logo),
                        description = current.description.nonBlankOr(details.description),
                        releaseInfo = current.releaseInfo.nonBlankOr(details.releaseInfo),
                        runtime = current.runtime.nonBlankOr(details.runtime),
                        ageRating = current.ageRating.nonBlankOr(details.ageRating),
                        imdbRating = current.imdbRating.nonBlankOr(details.imdbRating),
                        genres = current.genres.ifEmpty { details.genres },
                    )
                }
                // Addon IDs may expose their IMDb identifier only in the detail response.
                val detailImdbId = MdbListMetadataService.extractImdbId(details.imdbId)
                    ?: MdbListMetadataService.extractImdbId(details.id)
                if (settings.isActive && detailImdbId != null &&
                    MdbListMetadataService.extractImdbId(item.id) == null) {
                    val ratings = safely {
                        withTimeoutOrNull(5_000L) {
                            MdbListMetadataService.getRatings(detailImdbId, item.type, settings)
                        }
                    }.orEmpty()
                    if (ratings.isNotEmpty()) publish { it.copy(externalRatings = ratings) }
                }
            }
        }
    }

    private fun needsDetailIdentifier(id: String): Boolean =
        MdbListMetadataService.extractImdbId(id) == null &&
            !id.startsWith("tmdb:", ignoreCase = true) && !id.all(Char::isDigit)

    private fun String?.nonBlankOr(fallback: String?): String? = takeUnless { it.isNullOrBlank() } ?: fallback

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        null
    }
}

internal data class HomePosterPreviewState(val item: MetaPreview, val ratingSources: List<String>)

@Composable
internal fun rememberHomePosterPreviewState(item: MetaPreview): HomePosterPreviewState {
    val settings by remember {
        MdbListSettingsRepository.ensureLoaded()
        MdbListSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val profile by ProfileRepository.state.collectAsStateWithLifecycle()
    val owner = remember(item, settings, profile.activeProfile) {
        ViewModelStore().also { it.put("preview", HomePosterPreviewViewModel(item, settings)) }
    }
    DisposableEffect(owner) { onDispose { owner.clear() } }
    val model = owner["preview"] as HomePosterPreviewViewModel
    return key(owner) {
        val resolved by model.state.collectAsStateWithLifecycle()
        // Include IMDb's fallback slot even when external ratings are unavailable.
        val sources = if (settings.isActive) settings.enabledProvidersInPriorityOrder() else emptyList()
        HomePosterPreviewState(resolved, (sources + MdbListMetadataService.PROVIDER_IMDB).distinct())
    }
}
