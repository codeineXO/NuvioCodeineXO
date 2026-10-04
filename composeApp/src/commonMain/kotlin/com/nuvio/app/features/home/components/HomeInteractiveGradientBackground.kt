package com.nuvio.app.features.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.ui.nuvio
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A dynamic, colorful chromatic gradient background for the home page.
 * Gently floating chromatic aurora orbs move seamlessly across the screen.
 */
@Composable
fun HomeInteractiveGradientBackground(
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
) {
    val tokens = MaterialTheme.nuvio
    val baseBackground = tokens.colors.background

    val infiniteTransition = rememberInfiniteTransition(label = "HomeAmbientGradientTransition")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "HomeAmbientPhase1",
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "HomeAmbientPhase2",
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "HomeAmbientPhase3",
    )

    val phase4 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "HomeAmbientPhase4",
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
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
        modifier = modifier.fillMaxSize(),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Base dark background
            drawRect(color = baseBackground)

            // 2. Ambient Floating Aurora Orbs
            // Orb 1: Vibrant Indigo / Violet (Top-Left quadrant)
            val orb1Center = Offset(
                x = (w * 0.20f) + (cos(phase1) * (w * 0.10f)),
                y = (h * 0.22f) + (sin(phase1) * 70f) - (scrollShift * 0.5f),
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
                x = (w * 0.82f) - (sin(phase2) * (w * 0.08f)),
                y = (h * 0.36f) + (cos(phase2) * 60f) - (scrollShift * 0.6f),
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
                x = (w * 0.48f) + (cos(phase3) * (w * 0.12f)),
                y = (h * 0.68f) + (sin(phase3) * 80f) - (scrollShift * 0.7f),
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
                x = (w * 0.85f) + (sin(phase4) * (w * 0.06f)),
                y = (h * 0.85f) - (cos(phase4) * 50f) - (scrollShift * 0.4f),
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

            // 3. Subtle Vignette Scrim for content contrast and depth
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
