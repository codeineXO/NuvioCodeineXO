package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Rule

class PosterFocusRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun focusCoversDecorativeCornersAndPreservesSoftGlow() {
        val radius = mutableStateOf(12)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    CompositionLocalProvider(LocalRemoteFocusOutlineColor provides Color.Blue) {
                        val shape = RoundedCornerShape(radius.value.dp)
                        Box(Modifier.testTag("scene").background(Color.Black).padding(20.dp)) {
                            Box(Modifier.size(100.dp, 140.dp)
                                .softPosterGlow(shape, Shadow(radius = 10.dp, spread = 1.dp, color = Color.Green))
                                .clip(shape)
                                .background(Color.Red)
                                .cardDepthVisual(shape, edgeStrength = 100f, sheenStrength = 100f)
                                .posterCardClickable(onClick = {}, onLongClick = null,
                                    zoomCornerRadius = radius.value.dp, hoverScaleEnabled = false)
                                .testTag("poster"))
                        }
                    }
                }
            }
        }
        listOf(0, 4, 12, 24, 50, 100).forEach { corner ->
            compose.runOnIdle { radius.value = corner }
            // Pointer input hides keyboard focus without changing the shadow.
            compose.onNodeWithTag("poster").performMouseInput { click() }
            val before = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            compose.onNodeWithTag("poster").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithTag("poster").performKeyInput { pressKey(Key.DirectionUp) }
            val after = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            val scale = after.width / 140f
            val padding = 20f * scale
            val effectiveRadius = corner.coerceAtMost(50) * scale
            val diagonal = effectiveRadius * (1 - 1 / sqrt(2f))
            // Pixel centers sit half a pixel past their integer coordinates.
            val x = if (corner == 0) padding.roundToInt() else (padding + diagonal - 0.5f).roundToInt()
            val y = x
            val color = after[x, y]
            assertTrue(color.blue > 0.8f && color.red < 0.15f && color.green < 0.15f,
                "Decorative edge/artwork escapes focus at radius $corner: $color")

            var glowPixels = 0
            for (py in 0 until after.height) for (px in 0 until after.width) {
                if (px < padding - 1 || px > padding + 100 * scale + 1 ||
                    py < padding - 1 || py > padding + 140 * scale + 1) {
                    val a = before[px, py]
                    val b = after[px, py]
                    if (a.green > 0.02f) glowPixels++
                    assertTrue(abs(a.green - b.green) < 0.02f,
                        "Focus clips glow at radius $corner ($px, $py)")
                }
            }
            assertTrue(glowPixels > 20, "Missing soft glow outside poster bounds at radius $corner")
        }
    }
}
