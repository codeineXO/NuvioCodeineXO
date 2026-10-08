package com.nuvio.app.features.player.seekpreview

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface SeekrContent {
    data class Movie(
        val imdbId: String? = null,
        val tmdbId: Int? = null,
    ) : SeekrContent

    data class Episode(
        val showImdbId: String? = null,
        val showTmdbId: Int? = null,
        val season: Int,
        val episode: Int,
    ) : SeekrContent
}

data class SeekrTile(
    val sheetUrl: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

data class SeekrCue(
    val startMs: Long,
    val endMs: Long,
    val tile: SeekrTile,
)

data class SeekrThumbnail(
    val sheetBitmap: ImageBitmap,
    val tile: SeekrTile,
    val cueStartMs: Long,
    val cueEndMs: Long,
)

@Serializable
internal data class SpriteLookup(
    @SerialName("vtt_url") val vttUrl: String,
    @SerialName("scale") val scale: Double = 1.0,
    @SerialName("source_duration_ms") val sourceDurationMs: Long = 0,
)

@Serializable
internal data class KeyValidation(
    @SerialName("valid") val valid: Boolean = false,
)
