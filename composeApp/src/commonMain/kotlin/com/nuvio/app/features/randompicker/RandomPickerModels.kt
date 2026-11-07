package com.nuvio.app.features.randompicker

import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.player.MediaClassifier
import kotlinx.serialization.Serializable

@Serializable
internal data class PickerFilters(
    val schemaVersion: Int = 3,
    val contentStyle: String = "",
    val format: String = "",
    val addonUrl: String? = null,
    val catalogKey: String? = null,
    val genre: String? = null,
    val minimumRating: Int? = null,
    /** null = any year; -1 = before 1900; otherwise the first year of a decade. */
    val decade: Int? = null,
    val status: String = "",
    val excludePrevious: Boolean = false,
    val excludeWatched: Boolean = false,
) {
    fun isValid(): Boolean = contentStyle in setOf("", "live_action", "anime") && format in setOf("", "movie", "series") &&
        (minimumRating == null || minimumRating in 1..10) &&
        (decade == null || decade == -1 || decade in 1900..9990 && decade % 10 == 0)
}

internal data class PickerSource(val manifestUrl: String, val addonName: String, val catalog: AddonCatalog) {
    val key: String get() = "$manifestUrl\n${catalog.type}\n${catalog.id}"
    val unsupportedRequired: List<String> get() = catalog.extra.filter {
        it.isRequired && (it.name.lowercase() !in setOf("skip", "genre") || it.name.equals("genre", true) && it.options.isEmpty())
    }.map { it.name }
    fun compatible(filters: PickerFilters): Boolean {
        val type = catalog.type.lowercase()
        if (type == "anime") return filters.contentStyle != "live_action"
        return filters.format.isEmpty() || type == filters.format || filters.format == "series" && type == "tv"
    }
    fun selectedBy(filters: PickerFilters): Boolean = compatible(filters) && unsupportedRequired.isEmpty() &&
        (filters.addonUrl == null || manifestUrl == filters.addonUrl) &&
        (filters.catalogKey == null || key == filters.catalogKey)
}

internal fun pickerSources(addons: List<ManagedAddon>): List<PickerSource> = addons.filter { it.isActive }
    .flatMap { addon -> addon.manifest!!.catalogs.filter { it.type.lowercase() in setOf("movie", "series", "tv", "anime") }
        .map { PickerSource(addon.manifestUrl, addon.displayTitle, it) } }

internal fun PickerFilters.pruneSources(sources: List<PickerSource>): PickerFilters {
    val compatible = sources.filter { it.compatible(this) && it.unsupportedRequired.isEmpty() }
    val addon = addonUrl?.takeIf { url -> compatible.any { it.manifestUrl == url } }
    val catalog = catalogKey?.takeIf { key -> compatible.any { it.key == key && (addon == null || it.manifestUrl == addon) } }
    return copy(addonUrl = addon, catalogKey = catalog)
}

internal fun pickerGenres(sources: List<PickerSource>, filters: PickerFilters, observed: Map<String, Set<String>>): List<String> =
    sources.filter { it.selectedBy(filters) }.flatMap { source ->
        source.catalog.extra.filter { it.name.equals("genre", true) }.flatMap { it.options } + observed[source.key].orEmpty()
    }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sorted()

internal fun pickerDecades(currentYear: Int): List<Int> = ((currentYear / 10 * 10) downTo 1900 step 10).toList() + -1

internal fun MetaPreview.pickerMeta(): MetaDetails = MetaDetails(
    id = id, type = type, name = name, poster = poster, background = banner, logo = logo,
    description = description, releaseInfo = releaseInfo, genres = genres, imdbRating = imdbRating,
    runtime = runtime, status = status, language = language,
    imdbScore = imdbScore, tmdbScore = tmdbScore, externalRatings = externalRatings,
)

/** The primary rating field is shared with the standard hero, including existing provider fallback behavior. */
internal fun MetaDetails.pickerRating(): Double? = imdbRating?.trim()?.toDoubleOrNull()
    ?.takeIf { it.isFinite() && it in 0.0..10.0 }

internal fun PickerFilters.matches(meta: MetaDetails): Boolean {
    if (!isValid()) return false
    val type = if (meta.type.equals("tv", true)) "series" else meta.type.lowercase()
    val formatType = if (type == "anime") "series" else type
    if (format.isNotEmpty() && formatType != format) return false
    val classification = MediaClassifier.classify(meta)
    if (contentStyle == "anime" && !classification.isAnime) return false
    if (contentStyle == "live_action" && !classification.isLiveAction) return false
    if (minimumRating != null && (meta.pickerRating()?.let { it >= minimumRating } != true)) return false
    if (decade != null) {
        val year = Regex("(?<![0-9])[0-9]{4}(?![0-9])").find(meta.releaseInfo.orEmpty())?.value?.toIntOrNull() ?: return false
        if (if (decade == -1) year >= 1900 else year !in decade..(decade + 9)) return false
    }
    if (genre != null && meta.genres.none { it.trim().equals(genre.trim(), true) }) return false
    if (status.isNotBlank()) {
        val normalized = when (meta.status?.trim()?.lowercase()) {
            "returning series", "ongoing", "continuing", "in production" -> "ongoing"
            "ended", "completed", "canceled", "cancelled" -> "completed"
            else -> null
        }
        if (type !in setOf("series", "anime") || normalized != status) return false
    }
    return true
}

internal fun pickerAliases(meta: MetaDetails): Set<String> {
    val type = if (meta.type.equals("tv", true)) "series" else meta.type.lowercase()
    fun normalize(id: String): String {
        val clean = id.trim()
        return when {
            clean.startsWith("imdb:", true) -> clean.substringAfter(':').lowercase()
            Regex("tt[0-9]+", RegexOption.IGNORE_CASE).matches(clean) -> clean.lowercase()
            ':' in clean -> clean.substringBefore(':').lowercase() + ":" + clean.substringAfter(':')
            else -> clean
        }
    }
    return buildSet {
        fun addId(id: String) {
            val normalized = normalize(id)
            add("$type:$normalized")
            // IMDb and anime database IDs identify titles globally; TMDB numbers require the media type.
            if (Regex("tt[0-9]+").matches(normalized) || normalized.substringBefore(':') in setOf("kitsu", "mal", "anidb", "anilist")) {
                add("global:$normalized")
            }
        }
        addId(meta.id)
        meta.imdbId?.let(::addId)
        meta.provenance.knownIds.forEach { (provider, id) ->
            addId(if (provider.equals("imdb", true)) id else "${provider.lowercase()}:$id")
        }
    }
}

internal fun pickerIsWatched(meta: MetaDetails, watchedKeys: Set<String>, fullyWatchedSeriesKeys: Set<String>): Boolean {
    val movie = meta.type.equals("movie", true)
    val aliases = pickerAliases(meta.copy(type = if (movie) "movie" else "series"))
    val keys = if (movie) watchedKeys else fullyWatchedSeriesKeys
    return keys.any { key ->
        if (!key.endsWith(":-1:-1")) return@any false
        val title = key.removeSuffix(":-1:-1")
        val type = title.substringBefore(':').lowercase()
        if (movie && type !in setOf("movie", "film")) return@any false
        if (!movie && type !in setOf("series", "tv", "show", "tvshow", "anime")) return@any false
        pickerAliases(MetaDetails(title.substringAfter(':'), if (movie) "movie" else "series", ""))
            .any(aliases::contains)
    }
}

internal data class PickerArtwork(
    val poster: coil3.request.ImageRequest? = null,
    val background: coil3.request.ImageRequest? = null,
    val logo: coil3.request.ImageRequest? = null,
)
internal data class PickerResult(val preview: MetaPreview, val meta: MetaDetails, val source: PickerSource, val artwork: PickerArtwork? = null, val presentationId: Long = 0)
internal enum class PickerPhase { Idle, LoadingSources, Sampling, Enriching, Preparing, Retrying, Empty, Failed }
internal data class PickerState(
    val filters: PickerFilters = PickerFilters(),
    val sources: List<PickerSource> = emptyList(),
    val genres: List<String> = emptyList(),
    val phase: PickerPhase = PickerPhase.LoadingSources,
    val result: PickerResult? = null,
    val resultPopupOpen: Boolean = false,
    val failedSources: Int = 0,
    val metadataFailures: Int = 0,
    val timedOut: Boolean = false,
    val persistenceFailed: Boolean = false,
) {
    val picking: Boolean get() = phase == PickerPhase.Sampling || phase == PickerPhase.Enriching || phase == PickerPhase.Preparing || phase == PickerPhase.Retrying
}
