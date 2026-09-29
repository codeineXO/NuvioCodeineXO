package com.nuvio.app.features.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.nuvio.app.core.ui.nuvio
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An interactive, dynamic colorful gradient background for the home page.
 * Combines gently floating chromatic aurora orbs with a smooth, cursor/touch-reactive
 * glowing spotlight that tracks user pointer motion across the screen.
 */
@Composable
fun HomeInteractiveGradientBackground(
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
) {
    val tokens = MaterialTheme.nuvio
    val baseBackground = tokens.colors.background

    var pointerPosition by remember { mutableStateOf(Offset.Unspecified) }

    val animatedPointerOffset by animateOffsetAsState(
        targetValue = if (pointerPosition != Offset.Unspecified) pointerPosition else Offset(400f, 300f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "HomePointerGlowOffset",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "HomeAmbientGradientTransition")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "HomeAmbientGradientPhase",
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "HomeAmbientGradientPulse",
    )

    val scrollShift by remember(listState, gridState) {
        derivedStateOf {
            when {
                listState != null -> (listState.firstVisibleItemIndex * 400f + listState.firstVisibleItemScrollOffset) * 0.15f
                gridState != null -> (gridState.firstVisibleItemIndex * 250f + gridState.firstVisibleItemScrollOffset) * 0.15f
                else -> 0f
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull()
                        if (change != null) {
                            pointerPosition = change.position
                        }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Base dark background
            drawRect(color = baseBackground)

            // 2. Ambient Floating Aurora Orbs
            // Orb 1: Vibrant Indigo / Violet (Top-Left quadrant)
            val orb1Center = Offset(
                x = (w * 0.20f) + (cos(phase) * (w * 0.10f)),
                y = (h * 0.22f) + (sin(phase) * 70f) - (scrollShift * 0.5f),
            )
            val orb1Radius = (w * 0.46f * pulse).coerceAtLeast(180f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.26f),
                        Color(0xFF4338CA).copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    center = orb1Center,
                    radius = orb1Radius,
                ),
                center = orb1Center,
                radius = orb1Radius,
            )

            // Orb 2: Electric Cyan / Turquoise (Top-Right quadrant)
            val orb2Center = Offset(
                x = (w * 0.82f) - (sin(phase * 0.85f) * (w * 0.08f)),
                y = (h * 0.36f) + (cos(phase * 0.85f) * 60f) - (scrollShift * 0.6f),
            )
            val orb2Radius = (w * 0.42f * pulse).coerceAtLeast(160f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF06B6D4).copy(alpha = 0.22f),
                        Color(0xFF0284C7).copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = orb2Center,
                    radius = orb2Radius,
                ),
                center = orb2Center,
                radius = orb2Radius,
            )

            // Orb 3: Magenta / Fuchsia (Mid-Bottom Center)
            val orb3Center = Offset(
                x = (w * 0.48f) + (cos(phase * 1.15f) * (w * 0.12f)),
                y = (h * 0.68f) + (sin(phase * 1.15f) * 80f) - (scrollShift * 0.7f),
            )
            val orb3Radius = (w * 0.50f * pulse).coerceAtLeast(200f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD946EF).copy(alpha = 0.18f),
                        Color(0xFF9333EA).copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = orb3Center,
                    radius = orb3Radius,
                ),
                center = orb3Center,
                radius = orb3Radius,
            )

            // Orb 4: Rose / Coral Sunset (Bottom-Right quadrant)
            val orb4Center = Offset(
                x = (w * 0.85f) + (sin(phase * 0.7f) * (w * 0.06f)),
                y = (h * 0.85f) - (cos(phase * 0.7f) * 50f) - (scrollShift * 0.4f),
            )
            val orb4Radius = (w * 0.38f * pulse).coerceAtLeast(150f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF43F5E).copy(alpha = 0.15f),
                        Color(0xFFE11D48).copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                    center = orb4Center,
                    radius = orb4Radius,
                ),
                center = orb4Center,
                radius = orb4Radius,
            )

            // 3. Interactive Cursor / Touch Glowing Spotlight
            if (pointerPosition != Offset.Unspecified) {
                val spotlightRadius = (w * 0.32f).coerceIn(280f, 600f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA78BFA).copy(alpha = 0.22f),
                            Color(0xFF38BDF8).copy(alpha = 0.12f),
                            Color(0xFF818CF8).copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                        center = animatedPointerOffset,
                        radius = spotlightRadius,
                    ),
                    center = animatedPointerOffset,
                    radius = spotlightRadius,
                )
            }

            // 4. Subtle Vignette Scrim for content contrast and depth
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.15f),
                        Color.Black.copy(alpha = 0.45f),
                        Color.Black.copy(alpha = 0.70f),
                    ),
                    startY = 0f,
                    endY = h,
                ),
            )
        }
    }
}
