package com.nuvio.app.core.ui

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import kotlin.math.ceil

/** Run before the engine so selected URLs participate in normal disk and memory cache keys. */
internal class DesktopArtworkInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data
        val role = request.extras[ArtworkRoleKey]
        val selected = if (data is String && role != null) {
            desktopArtworkSourceUrl(data, role, chain.size, request.scale)
        } else data
        return chain.withRequest(request.newBuilder()
            .data(selected)
            .memoryCacheKeyExtra("nuvio.artwork.quality", "mitchell-v1")
            .build()).proceed()
    }
}

private val tmdbArtworkUrl = Regex("^(https://image\\.tmdb\\.org/t/p/)(w[0-9]+|original)(/[^?#]+)(.*)$")

internal fun desktopArtworkSourceUrl(url: String, role: ArtworkRole, size: Size, scale: Scale): String {
    val match = tmdbArtworkUrl.matchEntire(url) ?: return url
    val current = match.groupValues[2]
    if (current == "original") return url
    val width = (size.width as? Dimension.Pixels)?.px?.toDouble()
    val heightWidth = (size.height as? Dimension.Pixels)?.px?.times(role.aspectRatio)
    val requirements = listOfNotNull(width, heightWidth)
    if (requirements.isEmpty()) return url
    val required = ceil(if (scale == Scale.FILL) requirements.max() else requirements.min()).toInt()
    val widths = if (role == ArtworkRole.Poster) listOf(92, 154, 185, 342, 500, 780) else listOf(300, 780, 1280)
    val selected = widths.firstOrNull { it >= required }
    val existing = current.removePrefix("w").toIntOrNull() ?: return url
    if (selected != null && selected <= existing) return url
    return match.groupValues[1] + (selected?.let { "w$it" } ?: "original") + match.groupValues[3] + match.groupValues[4]
}
