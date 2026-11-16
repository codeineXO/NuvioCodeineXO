package com.nuvio.app.features.home.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.AuraGlow
import com.nuvio.app.core.ui.AuraSettings
import com.nuvio.app.core.ui.LocalAuraSettings
import com.nuvio.app.core.ui.navigationGlassSource
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.resolveAuraPalette
import com.nuvio.app.core.ui.themePalette
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

internal fun interface AuroraRenderer {
    fun draw(scope: DrawScope, phase: Float, heroBottomPx: Float, heroFadePx: Float): Boolean
}

internal val LocalAuroraRenderer =
    staticCompositionLocalOf<(@Composable (Color, List<AuraGlow>, Float) -> AuroraRenderer)?> { null }

@Composable
fun HomeInteractiveGradientBackground(
    modifier: Modifier = Modifier,
    settings: AuraSettings = LocalAuraSettings.current,
    heroBottomPx: (() -> Int)? = null,
) {
    val background = MaterialTheme.nuvio.colors.background
    val palette = MaterialTheme.themePalette
    val colors = remember(settings.palette, settings.customColors, palette) {
        resolveAuraPalette(settings, palette)
    }
    val intensity = settings.intensity.multiplier
    val renderer = LocalAuroraRenderer.current?.invoke(background, colors, intensity)
    val frame = if (settings.animated) {
        rememberInfiniteTransition(label = "Aurora").animateValue(
            initialValue = 0,
            targetValue = AuroraFrames,
            typeConverter = Int.VectorConverter,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = AuroraDurationSeconds * 1000, easing = LinearEasing),
            ),
            label = "Aurora flow",
        )
    } else {
        null
    }

    Box(modifier.fillMaxSize().navigationGlassSource()) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val heroFadePx = 96.dp.toPx()
                val fallback by lazy { createMeshAuroraRenderer(size, background, colors, intensity) }
                onDrawBehind {
                    val phase = (frame?.value ?: 0) * ((2 * PI).toFloat() / AuroraFrames)
                    val heroBottom = (heroBottomPx?.invoke()?.toFloat() ?: 0f).coerceIn(0f, size.height)
                    if (renderer?.draw(this, phase, heroBottom, heroFadePx) != true) {
                        fallback(this, phase, heroBottom, heroFadePx)
                    }
                }
            },
        )
    }
}

private const val AuroraDurationSeconds = 28
private const val AuroraFrames = AuroraDurationSeconds * 24
private const val AuroraColumns = 64
private const val AuroraRows = 48

private fun createMeshAuroraRenderer(
    size: Size,
    background: Color,
    colors: List<AuraGlow>,
    intensity: Float,
): DrawScope.(Float, Float, Float) -> Unit {
    val vertices = createAuroraVertices(size, colors, intensity)
    val paint = Paint().apply { color = Color.White }
    val dither = createDitherBrush()
    var previousPhase = Float.NaN
    return { phase, heroBottom, fadePx ->
        if (phase != previousPhase) {
            updateAuroraPositions(vertices, size, phase)
            previousPhase = phase
        }
        drawRect(background)
        drawContext.canvas.drawVertices(vertices, BlendMode.Modulate, paint)
        if (heroBottom > 0f) drawHeroAuraScrim(background, heroBottom, fadePx)
        drawRect(dither, blendMode = BlendMode.Plus)
    }
}

private fun updateAuroraPositions(vertices: Vertices, size: Size, phase: Float) {
    val positions = vertices.positions
    for (column in 0..AuroraColumns) {
        val x = column.toFloat() / AuroraColumns
        val displacement = 0.032f * sin(x * 9f - phase) * (0.65f + 0.35f * sin(x * PI.toFloat()))
        for (row in 1 until AuroraRows) {
            val y = row.toFloat() / AuroraRows
            val index = (column * (AuroraRows + 1) + row) * 2 + 1
            positions[index] = round((y + displacement * sin(y * PI.toFloat())) * size.height)
        }
    }
}

private fun createDitherBrush(): ShaderBrush {
    val tileSize = 32
    val bitmap = ImageBitmap(tileSize, tileSize)
    val noise = Random(0)
    val points = buildList {
        for (y in 0 until tileSize) {
            for (x in 0 until tileSize) {
                if (noise.nextBoolean()) add(Offset(x + 0.5f, y + 0.5f))
            }
        }
    }
    // A stationary one-level stipple also softens contours on the software mesh.
    val paint = Paint().apply {
        color = Color.White.copy(alpha = 1f / 255f)
        strokeWidth = 1f
        isAntiAlias = false
    }
    Canvas(bitmap).drawPoints(PointMode.Points, points, paint)
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

private val auroraIndices = buildList(AuroraColumns * AuroraRows * 6) {
    for (column in 0 until AuroraColumns) {
        for (row in 0 until AuroraRows) {
            val left = column * (AuroraRows + 1) + row
            val right = left + AuroraRows + 1
            add(left)
            add(right)
            add(left + 1)
            add(left + 1)
            add(right)
            add(right + 1)
        }
    }
}

private fun createAuroraVertices(size: Size, colors: List<AuraGlow>, intensity: Float): Vertices {
    val vertexCount = (AuroraColumns + 1) * (AuroraRows + 1)
    val positions = ArrayList<Offset>(vertexCount)
    val vertexColors = ArrayList<Color>(vertexCount)
    for (column in 0..AuroraColumns) {
        val x = column.toFloat() / AuroraColumns
        // Align shared triangle edges to pixels to avoid speckles in small previews.
        val pixelX = round(x * size.width)
        val ridge = 0.55f - 0.19f * (x - 0.5f) + 0.10f * sin(x * 6.3f + 0.6f) +
            0.025f * sin(x * 13f - 0.7f)
        for (row in 0..AuroraRows) {
            val y = row.toFloat() / AuroraRows
            val distance = y - ridge
            // A soft surrounding glow connects the whole field to the brighter curved band.
            val band = auraFalloff(distance / 0.085f)
            val veil = auraFalloff((distance + 0.12f) / 0.34f)
            val fringe = auraFalloff((distance - 0.12f) / 0.25f)
            val opacity = intensity * (0.017f + 0.13f * band + 0.076f * veil + 0.047f * fringe)
            val position = (0.94f * x + 0.18f * y - 0.06f).coerceIn(0f, 1f) * colors.lastIndex
            val start = position.toInt().coerceAtMost(colors.lastIndex - 1)
            val end = start + 1
            val mix = position - start
            val inner = lerp(colors[start].inner, colors[end].inner, mix)
            val outer = lerp(colors[start].outer, colors[end].outer, mix)
            positions.add(Offset(pixelX, round(y * size.height)))
            vertexColors.add(lerp(outer, inner, 0.35f + 0.65f * band).copy(alpha = opacity))
        }
    }
    return Vertices(VertexMode.Triangles, positions, positions, vertexColors, auroraIndices)
}

private fun auraFalloff(distance: Float): Float = exp(-distance * distance)

private fun DrawScope.drawHeroAuraScrim(background: Color, heroBottomPx: Float, fadePx: Float) {
    val heroBottom = heroBottomPx.coerceAtMost(size.height)
    val fadeEnd = heroBottom + fadePx
    val opacity = 0.78f * (heroBottom / fadePx).coerceIn(0f, 1f)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(background.copy(alpha = opacity), background.copy(alpha = 0f)),
            startY = heroBottom,
            endY = fadeEnd,
        ),
        size = Size(size.width, fadeEnd.coerceAtMost(size.height)),
    )
}
