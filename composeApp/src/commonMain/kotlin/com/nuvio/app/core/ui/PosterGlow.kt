package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop
import com.kmpalette.extensions.painter.rememberPainterDominantColorState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.ceil

// Accessed on the UI dispatcher; keep only colors, never decoded images or painters.
private val posterGlowColors = linkedMapOf<String, Color>()
private const val PosterGlowCacheSize = 128
internal val PosterGlowOutset = 20.dp

internal class PosterGlowLayer {
    var shadow by mutableStateOf<Shadow?>(null)
    var shape by mutableStateOf<Shape?>(null)
    var bounds by mutableStateOf<Rect?>(null)
}

internal val LocalPosterGlowLayer = staticCompositionLocalOf<PosterGlowLayer?> { null }
internal val LocalPosterGlowImageState = staticCompositionLocalOf<PosterGlowState?> { null }

/** A drawing-only sibling: poster clipping and pointer hit regions stay unchanged. */
@Composable
internal fun BoxScope.PosterGlowBackdrop(layer: PosterGlowLayer, origin: Offset) {
    val shadow = layer.shadow ?: return
    val shape = layer.shape ?: return
    val bounds = layer.bounds ?: return
    Box(
        Modifier.matchParentSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(
                    bounds.width.roundToInt().coerceAtLeast(1),
                    bounds.height.roundToInt().coerceAtLeast(1),
                ))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (bounds.left - origin.x).roundToInt(),
                        (bounds.top - origin.y).roundToInt(),
                    )
                }
            }
            .softPosterGlow(shape, shadow),
    )
}

internal class PosterGlowState(val artworkKey: String) {
    var painter by mutableStateOf<Painter?>(null)
    var focused by mutableStateOf(false)
    var active by mutableStateOf(false)
}

@Composable
internal fun rememberPosterGlowState(artworkKey: String): PosterGlowState =
    remember(artworkKey) { PosterGlowState(artworkKey) }

@Composable
internal fun rememberArtworkGlowState(imageUrl: String?, fallbackUrl: String? = null): PosterGlowState? =
    if (isDesktop && rememberPosterCardStyleUiState().glowEnabled && !imageUrl.isNullOrBlank()) {
        rememberPosterGlowState("$imageUrl\n$fallbackUrl")
    } else null

@Composable
internal fun Modifier.posterGlow(state: PosterGlowState?, shape: Shape): Modifier =
    posterGlow(state, state?.artworkKey.orEmpty(), shape)

@Composable
internal fun Modifier.posterGlow(
    state: PosterGlowState?,
    artworkKey: String,
    shape: Shape,
): Modifier {
    val layer = LocalPosterGlowLayer.current
    if (state == null) return this
    DisposableEffect(state, layer) {
        onDispose {
            layer?.shadow = null
            layer?.bounds = null
        }
    }

    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsRemoteHoveredAsState()
    val active = hovered || (state.focused && isRemoteKeyboardNavigationActive)
    var color by remember(artworkKey) { mutableStateOf(posterGlowColors[artworkKey]) }
    val dominant = key(artworkKey) {
        rememberPainterDominantColorState(
            defaultColor = Color.Transparent,
            defaultOnColor = Color.White,
        )
    }
    LaunchedEffect(artworkKey, active, state.painter) {
        val painter = state.painter ?: return@LaunchedEffect
        if (!active || color != null) return@LaunchedEffect
        // Skip transient cards during rapid pointer/key navigation.
        delay(80)
        try {
            dominant.updateFrom(painter)
            color = dominant.color.takeIf { it.alpha > 0f }
            color?.let { extracted ->
                posterGlowColors[artworkKey] = extracted
                while (posterGlowColors.size > PosterGlowCacheSize) {
                    posterGlowColors.remove(posterGlowColors.keys.first())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Unsupported/missing artwork simply has no glow.
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (active && color != null) 0.95f else 0f,
        animationSpec = tween(180),
        label = "poster_glow_alpha",
    )
    val vividColor = remember(color) {
        color?.let {
            val hsv = HsvColor.fromRgb(it.toArgb())
            Color.hsv(
                hue = hsv.hue,
                saturation = (hsv.saturation * 1.4f).coerceAtMost(1f),
                value = (hsv.brightness * 1.2f).coerceIn(0.72f, 1f),
            )
        } ?: Color.Transparent
    }
    SideEffect { state.active = active || alpha > 0f }
    val shadow = if (alpha > 0f) Shadow(
        radius = 10.dp,
        spread = 1.dp,
        color = vividColor.copy(alpha = alpha),
    ) else null
    SideEffect {
        layer?.shape = shape
        layer?.shadow = shadow
    }
    return this
        .hoverable(interactions)
        .onFocusChanged { state.focused = it.hasFocus }
        .then(
            if (layer != null) Modifier.onGloballyPositioned { coordinates ->
                layer.bounds = Rect(
                    coordinates.localToRoot(Offset.Zero),
                    coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())),
                )
            } else if (shadow != null) Modifier.softPosterGlow(shape, shadow) else Modifier,
        )
}

/** Keep the entire Gaussian tail; dropShadow's radius + spread bitmap crops it. */
internal fun Modifier.softPosterGlow(shape: Shape, shadow: Shadow): Modifier = drawWithCache {
    val radius = shadow.radius.toPx()
    val spread = shadow.spread.toPx().coerceAtLeast(0f)
    // Match the shadow blur's radius-to-sigma conversion, with room for three sigma.
    val sigma = if (radius > 0f) radius * 0.57735f + 0.5f else 0f
    val padding = ceil(sigma * 3f + spread).toInt()
    val outline = shape.createOutline(size, layoutDirection, this)
    val glow = obtainGraphicsLayer()
    glow.record(size = IntSize(
        ceil(size.width).toInt() + padding * 2,
        ceil(size.height).toInt() + padding * 2,
    )) {
        translate(padding.toFloat(), padding.toFloat()) {
            drawOutline(outline, shadow.color)
            if (spread > 0f) drawOutline(outline, shadow.color, style = Stroke(spread * 2f))
        }
    }
    glow.renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
    glow.alpha = shadow.alpha
    val offsetX = shadow.offset.x.toPx() - padding
    val offsetY = shadow.offset.y.toPx() - padding
    onDrawWithContent {
        translate(offsetX, offsetY) { drawLayer(glow) }
        drawContent()
    }
}
