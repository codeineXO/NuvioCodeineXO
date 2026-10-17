package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import nuvio.composeapp.generated.resources.Res

/** Draws the original IMDb SVG paths without the SVG painter's intermediate bitmap cache. */
@Composable
internal fun rememberImdbLogoPainter(): Painter {
    val paths by produceState<List<ImdbLogoPath>>(emptyList()) {
        val svg = Res.readBytes("drawable/rating_imdb.svg").decodeToString()
        // This bundled asset contains only solid, untransformed paths in a 575 x 290 viewport.
        value = Regex("""<path\s+fill="(#[0-9A-Fa-f]{6})"\s+d="([^"]+)"""")
            .findAll(svg)
            .map { match ->
                ImdbLogoPath(
                    path = PathParser().parsePathString(match.groupValues[2]).toPath(),
                    color = Color(0xFF000000L or match.groupValues[1].drop(1).toLong(16)),
                )
            }
            .toList()
            .also { require(it.size == 5) { "Unexpected IMDb SVG path structure" } }
    }
    return remember(paths) { ImdbLogoPainter(paths) }
}

private data class ImdbLogoPath(val path: Path, val color: Color)

private class ImdbLogoPainter(private val paths: List<ImdbLogoPath>) : Painter() {
    override val intrinsicSize: Size = Size(575f, 290f)

    override fun DrawScope.onDraw() {
        // Image applies ContentScale.Fit and alignment before this draw; retain its exact size.
        withTransform({ scale(size.width / 575f, size.height / 290f, Offset.Zero) }) {
            paths.forEach { drawPath(path = it.path, color = it.color) }
        }
    }
}
