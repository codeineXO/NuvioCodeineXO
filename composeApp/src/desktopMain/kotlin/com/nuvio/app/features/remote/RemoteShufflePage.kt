package com.nuvio.app.features.remote

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.randompicker.PickerFilters
import com.nuvio.app.features.randompicker.PickerPhase
import com.nuvio.app.features.randompicker.PickerState
import com.nuvio.app.features.randompicker.pickerDecades
import java.time.Year
import javax.swing.SwingUtilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Shuffle on the phone, like the PC: its filters as `fields` (each with the PC's label, value and options), its
 * toggles, actions and progress, and the picked title as a hero. Every change runs the PC's own Shuffle model.
 */
internal object RemoteShufflePage {
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<String> get() = RemoteShuffle.model.flatMapLatest { model ->
        model?.uiState?.map { message(it) } ?: flowOf("""{"k":"shuffle","active":false}""")
    }.distinctUntilChanged()

    private suspend fun message(state: PickerState): String {
        val filters = state.filters
        val compatible = state.sources.filter { it.compatible(filters) && it.unsupportedRequired.isEmpty() }
        val scoped = compatible.filter { filters.addonUrl == null || it.manifestUrl == filters.addonUrl }
        suspend fun text(resource: StringResource) = getString(resource)
        suspend fun choices(vararg options: Pair<String, StringResource>) = options.map { (key, label) -> key to text(label) }
        val fields = listOf(
            Field("style", text(Res.string.picker_style), filters.contentStyle,
                choices("" to Res.string.picker_all, "live_action" to Res.string.picker_live_action, "anime" to Res.string.picker_anime)),
            Field("format", text(Res.string.picker_format), filters.format,
                choices("" to Res.string.picker_all, "movie" to Res.string.picker_movies, "series" to Res.string.picker_series)),
            Field("status", text(Res.string.picker_status), filters.status,
                choices("" to Res.string.picker_all, "ongoing" to Res.string.picker_ongoing, "completed" to Res.string.picker_completed),
                chips = true, disabled = filters.format == "movie"),
            Field("addon", text(Res.string.picker_addon), filters.addonUrl.orEmpty(),
                listOf("" to text(Res.string.picker_all_sources)) + compatible.distinctBy { it.manifestUrl }.map { it.manifestUrl to it.addonName }),
            Field("catalog", text(Res.string.picker_catalog), filters.catalogKey.orEmpty(),
                listOf("" to text(Res.string.picker_all_catalogs)) + scoped.map { source ->
                    val duplicate = scoped.count { it.catalog.name.equals(source.catalog.name, true) } > 1
                    source.key to if (duplicate) "${source.catalog.name} · ${source.addonName} · ${source.catalog.type}" else source.catalog.name
                }),
            Field("genre", text(Res.string.picker_genres), filters.genre.orEmpty(),
                listOf("" to text(Res.string.picker_any_genre)) + state.genres.map { it to it }),
            Field("rating", text(Res.string.picker_rating), filters.minimumRating?.toString().orEmpty(),
                listOf("" to text(Res.string.picker_any_rating)) + (1..9).map { "$it" to "$it+" }),
            Field("year", text(Res.string.picker_year), filters.decade?.toString().orEmpty(),
                listOf("" to text(Res.string.picker_any_year)) + pickerDecades(Year.now().value).map {
                    "$it" to if (it == -1) text(Res.string.picker_before_1900) else "${it}s"
                }),
        )
        val feedback = listOfNotNull(
            when (state.phase) {
                PickerPhase.LoadingSources -> Res.string.picker_loading
                PickerPhase.Sampling -> Res.string.picker_sampling
                PickerPhase.Enriching -> Res.string.picker_enriching
                PickerPhase.Empty -> Res.string.picker_empty
                PickerPhase.NoNewTitles -> Res.string.picker_no_new_titles
                PickerPhase.Retrying -> Res.string.picker_retrying
                PickerPhase.Failed -> if (state.timedOut) Res.string.picker_timeout else Res.string.picker_failed
                else -> null
            },
            Res.string.picker_partial.takeIf { state.failedSources > 0 },
            Res.string.picker_metadata_missing.takeIf { state.metadataFailures > 0 },
        ).map { text(it) }
        val result = state.result?.takeIf { state.resultPopupOpen }
        val resultMeta = result?.meta?.let { meta ->
            MetaPreview(id = meta.id, type = meta.type, name = meta.name, poster = meta.poster, banner = meta.background,
                logo = meta.logo, description = meta.description, releaseInfo = meta.releaseInfo, genres = meta.genres,
                imdbRating = meta.imdbRating, runtime = meta.runtime, status = meta.status, language = meta.language,
                ageRating = meta.ageRating, externalRatings = meta.externalRatings)
        }
        val resultInfo = resultMeta?.let { heroInfo(it) }
        val noDescription = text(Res.string.picker_no_description)
        val labels = listOf(
            "title" to Res.string.picker_title, "subtitle" to Res.string.picker_subtitle, "types" to Res.string.picker_types,
            "excludeHint" to Res.string.picker_exclude_hint, "samplingHint" to Res.string.picker_sampling_hint,
            "pick" to (if (state.result == null) Res.string.picker_pick else Res.string.picker_again),
            "again" to Res.string.picker_again, "reset" to Res.string.picker_reset, "cancel" to Res.string.picker_cancel,
            "details" to Res.string.picker_details, "close" to Res.string.picker_close,
        ).map { (key, resource) -> key to text(resource) }
        val toggles = listOf(
            Triple("excludeWatched", text(Res.string.picker_exclude_watched), filters.excludeWatched),
            Triple("excludePrevious", text(Res.string.picker_exclude), filters.excludePrevious),
        )
        val noSources = if (compatible.isEmpty()) text(Res.string.picker_no_sources) else null
        val canPick = !state.picking && state.phase != PickerPhase.LoadingSources &&
            state.sources.any { it.selectedBy(filters) } && filters.isValid()
        return buildJsonObject {
            put("k", "shuffle")
            put("active", true)
            putJsonObject("labels") { labels.forEach { (key, value) -> put(key, value) } }
            putJsonArray("fields") { fields.forEach { field(it) } }
            putJsonArray("toggles") {
                toggles.forEach { (id, label, value) -> addJsonObject { put("id", id); put("label", label); put("value", value) } }
            }
            put("noSources", noSources)
            put("canPick", canPick)
            put("picking", state.picking)
            put("busy", state.picking || state.phase == PickerPhase.LoadingSources)
            putJsonArray("feedback") { feedback.forEach { add(it) } }
            if (resultMeta != null && resultInfo != null) {
                putJsonObject("result") {
                    hero(resultMeta, resultInfo)
                    put("description", resultMeta.description ?: noDescription)
                }
            }
        }.toString()
    }

    /** `shuffleFilter` (`field`, `value`), `shufflePick`, `shuffleReset`, `shuffleCancel` or `shuffleClose`. */
    fun handle(cmd: String, json: JsonObject) {
        val model = RemoteShuffle.model.value ?: return
        val field = json["field"]?.jsonPrimitive?.contentOrNull
        val value = json["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
        SwingUtilities.invokeLater {
            when (cmd) {
                "shuffleFilter" -> withFilter(model.uiState.value.filters, field, value)?.let(model::update)
                "shufflePick" -> model.pick()
                "shuffleReset" -> model.reset()
                "shuffleCancel" -> model.stopSearch()
                "shuffleClose" -> model.dismissResult()
            }
        }
    }

    /** The picked title, for its View Details (`open` from `shuffle`). */
    fun picked(): MetaPreview? = RemoteShuffle.model.value?.uiState?.value?.result?.preview

    private fun withFilter(filters: PickerFilters, field: String?, value: String): PickerFilters? = when (field) {
        "style" -> filters.copy(contentStyle = value)
        "format" -> filters.copy(format = value, status = if (value == "movie") "" else filters.status)
        "status" -> filters.copy(status = value)
        "addon" -> filters.copy(addonUrl = value.ifEmpty { null })
        "catalog" -> filters.copy(catalogKey = value.ifEmpty { null })
        "genre" -> filters.copy(genre = value.ifEmpty { null })
        "rating" -> filters.copy(minimumRating = value.toIntOrNull())
        "year" -> filters.copy(decade = value.toIntOrNull())
        "excludeWatched" -> filters.copy(excludeWatched = value == "true")
        "excludePrevious" -> filters.copy(excludePrevious = value == "true")
        else -> null
    }

    private class Field(
        val id: String,
        val label: String,
        val value: String,
        val options: List<Pair<String, String>>,
        val chips: Boolean = false,
        val disabled: Boolean = false,
    )

    private fun JsonArrayBuilder.field(field: Field) = addJsonObject {
        put("id", field.id)
        put("label", field.label)
        put("value", field.value)
        put("chips", field.chips)
        put("disabled", field.disabled)
        putJsonArray("options") { field.options.forEach { (key, label) -> addJsonObject { put("key", key); put("label", label) } } }
    }
}
