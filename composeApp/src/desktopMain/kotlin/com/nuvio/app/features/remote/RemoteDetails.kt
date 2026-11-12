package com.nuvio.app.features.remote

import com.nuvio.app.features.anime.AnimeFillerSettingsRepository
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.normalizeSeasonNumber
import com.nuvio.app.features.player.MediaClassifier
import com.nuvio.app.features.remote.RemoteAppBridge.Command
import com.nuvio.app.features.streams.StreamsRepository
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The phone follows the PC from Home into a title's page, its sources and the player. The pages' content goes to the
 * phone, and the phone's taps run the pages' own buttons (Play, an episode, a source).
 */
internal object RemoteDetails {
    /** `{"k":"route","route":"tabs|details|streams|player|other"}`: which page the PC shows. */
    val routeMessages: Flow<String> = RemoteAppBridge.route.map { route ->
        buildJsonObject { put("k", "route"); put("route", route) }.toString()
    }.distinctUntilChanged()

    /** `{"k":"details",…}`: the open title, like its page on the PC. */
    val detailsMessages: Flow<String> = combine(
        MetaDetailsRepository.uiState,
        RemoteAppBridge.details,
        AnimeFillerSettingsRepository.enabled,
        AnimeFillerSettingsRepository.hideFillers,
    ) { state, actions, fillerEnabled, hideFillers ->
        val meta = state.meta
        buildJsonObject {
            put("k", "details")
            put("loading", state.isLoading)
            if (meta == null) return@buildJsonObject
            val key = "${meta.type}:${meta.id}"
            val details = actions?.takeIf { it.key == key }
            put("key", key)
            put("preferredSeason", details?.preferredSeasonNumber)
            put("name", meta.name)
            put("logo", meta.logo)
            put("background", meta.background ?: meta.poster)
            put("year", meta.releaseInfo)
            put("runtime", meta.runtime)
            put("ageRating", meta.ageRating)
            put("imdb", meta.imdbRating)
            putJsonArray("ratings") {
                meta.externalRatings.filter { it.value > 0 }.take(5).forEach {
                    addJsonObject { put("source", it.source); put("value", it.value) }
                }
            }
            put("genres", meta.genres.take(4).joinToString(" · "))
            put("description", meta.description)
            put("playLabel", details?.playLabel)
            put("saved", details?.isSaved == true)
            put("watched", details?.isWatched == true)
            val isAnime = MediaClassifier.classify(meta).isAnime || meta.videos.any { it.isFiller || it.isMixed }
            if (isAnime) {
                put("fillerDetection", fillerEnabled)
                put("hideFillers", fillerEnabled && hideFillers)
            }
            val seasons = meta.videos.withIndex()
                .filter { it.value.season != null || it.value.episode != null }
                .groupBy { normalizeSeasonNumber(it.value.season) }
            putJsonArray("seasons") {
                seasons.keys.sortedWith(compareBy({ it == 0 }, { it })).forEach { season ->
                    addJsonObject {
                        put("number", season)
                        put("label", if (season == 0) "Specials" else "Season $season")
                        putJsonArray("episodes") {
                            seasons.getValue(season).sortedBy { it.value.episode ?: 0 }.forEach { (index, video) ->
                                if (isAnime && fillerEnabled && hideFillers && video.isFiller && !video.isMixed) {
                                    return@forEach
                                }
                                addJsonObject {
                                    put("index", index)
                                    put("id", video.id)
                                    if (details != null && season == details.preferredSeasonNumber && video.episode != null &&
                                        video.episode == details.preferredEpisodeNumber
                                    ) put("preferred", true)
                                    put("code", "S$season E${video.episode ?: ""}")
                                    put("title", video.title)
                                    put("overview", video.overview)
                                    put("image", video.thumbnail ?: meta.background)
                                    put("runtime", video.runtime?.let { "${it}m" })
                                    put("rating", video.rating?.takeIf { it > 0 })
                                    put("released", video.released?.take(10))
                                    if (isAnime && fillerEnabled) {
                                        if (video.isMixed) put("fillerLabel", "Mixed")
                                        else if (video.isFiller) put("fillerLabel", "Filler")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }.distinctUntilChanged()

    /** `{"k":"streams",…}`: the sources for what is about to play, grouped by addon, like the PC's list. */
    val streamsMessages: Flow<String> = combine(StreamsRepository.uiState, RemoteAppBridge.streams) { state, page ->
        buildJsonObject {
            put("k", "streams")
            put("loading", state.isAnyLoading)
            if (page != null) {
                put("key", page.key)
                put("title", page.title)
                put("logo", page.logo)
                put("background", page.background)
                put("episode", page.episode)
                put("resumeMs", page.resumeMs)
                put("fromStart", page.startFromBeginning)
            }
            putJsonArray("groups") {
                state.groups.forEach { group ->
                    addJsonObject {
                        put("addon", group.addonName)
                        put("loading", group.isLoading)
                        put("error", group.error)
                        putJsonArray("streams") {
                            group.streams.forEach { stream ->
                                addJsonObject {
                                    put("name", stream.streamLabel)
                                    put("menuId", stream.hashCode())
                                    put("details", stream.description ?: stream.title)
                                    putJsonArray("badges") { stream.badges.forEach { add(it.name) } }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }.distinctUntilChanged()

    /**
     * One message from the phone on these pages: `back`, `play` (the page's Play button), `episode` (`item`, an index
     * in the title's episodes), `saved`, `watched`, `stream` (`group` and `item`) or `reloadStreams`.
     */
    fun handle(cmd: String, json: JsonObject) {
        val item = json["item"]?.jsonPrimitive?.intOrNull ?: -1
        val details = RemoteAppBridge.details.value
        val page = RemoteAppBridge.streams.value
        val action: (() -> Unit)? = when (cmd) {
            "back" -> ({ RemoteAppBridge.send(Command.Back()) })
            "play" -> details?.play
            "saved" -> details?.toggleSaved
            "watched" -> details?.toggleWatched
            "fillerDetection" -> json["value"]?.jsonPrimitive?.booleanOrNull?.let { enabled ->
                { AnimeFillerSettingsRepository.setEnabled(enabled) }
            }
            "hideFillers" -> json["value"]?.jsonPrimitive?.booleanOrNull?.let { hide ->
                {
                    if (hide) AnimeFillerSettingsRepository.setEnabled(true)
                    AnimeFillerSettingsRepository.setHideFillers(hide)
                }
            }
            "episode" -> MetaDetailsRepository.uiState.value.meta?.videos?.getOrNull(item)
                ?.let { video -> details?.let { { it.playEpisode(video) } } }
            "stream" -> {
                val group = json["group"]?.jsonPrimitive?.intOrNull ?: -1
                StreamsRepository.uiState.value.groups.getOrNull(group)?.streams?.getOrNull(item)
                    ?.let { stream -> page?.let { { it.select(stream) } } }
            }
            "reloadStreams" -> page?.reload
            "fromStart" -> json["value"]?.jsonPrimitive?.booleanOrNull
                ?.let { fromStart -> page?.let { { it.setStartFromBeginning(fromStart) } } }
            else -> null
        }
        // The pages' buttons belong to the UI thread.
        action?.let { SwingUtilities.invokeLater(it) }
    }
}
