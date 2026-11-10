package com.nuvio.app.features.remote

import com.nuvio.app.features.player.AnimeUpscalerMode
import com.nuvio.app.features.player.AudioTrack
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.SubtitleColorSwatches
import com.nuvio.app.features.player.SubtitleOutlineColorSwatches
import com.nuvio.app.features.player.SubtitleOutlineEffect
import com.nuvio.app.features.player.backgroundBoxColor
import com.nuvio.app.features.player.toStorageHexString
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The data behind the player's bar and panels, for the phone to draw the same panels. Only what those panels show:
 * the controls state also holds things like API keys, which never leave the PC.
 */
internal object RemotePlayerPanels {
    /** `{"k":"player","active":false}`, or the player's bar and panels while it is open. */
    fun message(state: PlayerControlsState?, audioTracks: List<AudioTrack>): String = buildJsonObject {
        put("k", "player")
        put("active", state != null)
        if (state == null) return@buildJsonObject
        put("title", state.title)
        // The artwork, logo and synopsis the player shows on its loading screen and pause overlay.
        put("artwork", state.openingArtwork.orEmpty())
        put("logo", (state.pauseOverlayLogo ?: state.openingLogo).orEmpty())
        put("episodeInfo", state.pauseOverlayEpisodeInfo)
        put("episodeTitle", state.pauseOverlayEpisodeTitle)
        put("description", state.pauseOverlayDescription)
        put("playing", state.isPlaying)
        put("volume", state.volumeLevel ?: 1f)
        put("speed", state.playbackSpeedLabel)
        put("aspect", state.resizeModeLabel)
        put("eyeFilter", state.eyeFilterEnabled)
        sources(state)
        subtitles(state)
        audio(state, audioTracks)
        enhancements(state)
        putJsonArray("chapters") {
            state.chapters.forEach { addJsonObject { put("title", it.title); put("startMs", it.startMs) } }
        }
    }.toString()

    private fun JsonObjectBuilder.sources(state: PlayerControlsState) = putJsonObject("sources") {
        put("loading", state.sourceIsLoading)
        putJsonArray("items") {
            state.sourceItems.forEach { item ->
                addJsonObject {
                    put("index", item.index)
                    put("label", item.label)
                    put("subtitle", item.subtitle)
                    put("current", item.isCurrent)
                    put("enabled", item.isEnabled)
                    put("size", item.formattedSize)
                    putJsonArray("badges") { item.badges.filter { it.imageURL.isNotBlank() }.forEach { add(it.imageURL) } }
                }
            }
        }
    }

    private fun JsonObjectBuilder.subtitles(state: PlayerControlsState) = putJsonObject("subtitles") {
        put("language", state.selectedSubtitleLanguageKey)
        put("selected", state.selectedSubtitleOptionId)
        putJsonArray("languages") {
            state.subtitleLanguageItems.forEach {
                addJsonObject { put("key", it.key); put("label", it.label); put("count", it.count) }
            }
        }
        customization(state)
        putJsonArray("options") {
            state.subtitleOptionItems.forEach {
                addJsonObject {
                    put("id", it.id)
                    put("language", it.languageKey)
                    put("kind", it.kind)
                    put("index", it.index)
                    put("source", it.sourceLabel)
                    put("title", it.title)
                    put("metadata", it.metadata)
                    put("selected", it.isSelected)
                }
            }
        }
    }

    /** The Subtitles panel's Customization tab. Colors are `#AARRGGBB`, like the player sends them. */
    private fun JsonObjectBuilder.customization(state: PlayerControlsState) = putJsonObject("style") {
        val style = state.subtitleStyle
        val boxed = style.outlineEffect == SubtitleOutlineEffect.BACKGROUND_BOX
        put("delayMs", state.subtitleDelayMs)
        put("custom", state.customSubtitleStylingEnabled)
        put("fontSize", style.fontSizeSp)
        put("bottomOffset", style.bottomOffset)
        put("font", state.fontPicker.items.firstOrNull { it.id == style.importedFontId }?.label ?: style.fontName)
        put("bold", style.bold)
        put("textColor", style.textColor.toStorageHexString())
        put("effect", style.outlineEffect.id)
        put("effectLabel", style.outlineEffect.label)
        put("effectColor", (if (boxed) style.backgroundBoxColor else style.outlineColor).toStorageHexString())
        put("outlineWidth", style.outlineWidth)
        putJsonArray("textSwatches") { SubtitleColorSwatches.forEach { add(it.toStorageHexString()) } }
        putJsonArray("outlineSwatches") { SubtitleOutlineColorSwatches.forEach { add(it.toStorageHexString()) } }
        putJsonObject("autoSync") {
            put("enabled", state.hasSelectedAddonSubtitle)
            put("loading", state.subtitleAutoSyncIsLoading)
            put("error", state.subtitleAutoSyncErrorMessage)
            putJsonArray("cues") {
                state.subtitleAutoSyncCues.forEach {
                    addJsonObject { put("index", it.index); put("time", it.timeLabel); put("text", it.text) }
                }
            }
        }
    }

    private fun JsonObjectBuilder.audio(state: PlayerControlsState, tracks: List<AudioTrack>) = putJsonObject("audio") {
        put("nightMode", state.audioNightModeEnabled)
        putJsonArray("tracks") {
            tracks.forEach {
                addJsonObject {
                    put("id", it.id.toIntOrNull() ?: -1)
                    put("index", it.index)
                    put("label", it.label)
                    put("language", it.language)
                    put("selected", it.isSelected)
                }
            }
        }
    }

    private fun JsonObjectBuilder.enhancements(state: PlayerControlsState) = putJsonObject("enhancements") {
        put("upscaler", state.animeUpscalerOption.storageKey)
        put("mode", state.animeUpscalerModeIndex)
        putJsonArray("modes") {
            AnimeUpscalerMode.entries.forEach {
                addJsonObject { put("index", it.index); put("label", it.label); put("description", it.description) }
            }
        }
        put("svp", state.svpMode)
        put("svpAvailable", state.svpAvailable)
        put("content", state.mediaContentType.name.lowercase())
    }
}
