package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Rule

class PosterGlowRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shelfGlowHasSoftUnclippedFalloff() = assertShelfGlow(useGutterLayout = false)

    @Test
    fun gutterShelfGlowHasSoftUnclippedFalloff() = assertShelfGlow(useGutterLayout = true)

    private fun assertShelfGlow(useGutterLayout: Boolean) {
        val active = mutableStateOf(1)
        compose.setContent {
            NuvioTheme {
                Box(Modifier.size(600.dp, 240.dp).background(Color.Black).testTag("scene")) {
                    NuvioShelfSection(title = "", entries = listOf(0, 1, 2, 3),
                        modifier = Modifier.padding(30.dp), itemSpacing = 30.dp,
                        useGutterLayout = useGutterLayout) { index ->
                        val layer = LocalPosterGlowLayer.current!!
                        val shape = RoundedCornerShape(24.dp)
                        val selected = index == active.value
                        SideEffect {
                            layer.shape = shape
                            layer.shadow = if (selected) Shadow(radius = 10.dp, spread = 1.dp,
                                color = Color.Green) else null
                        }
                        Box(Modifier.size(100.dp, 140.dp)
                            .onGloballyPositioned { coordinates ->
                                layer.bounds = Rect(coordinates.localToRoot(Offset.Zero),
                                    coordinates.localToRoot(Offset(coordinates.size.width.toFloat(),
                                        coordinates.size.height.toFloat())))
                            }
                            .clip(shape).background(Color.DarkGray).testTag("poster$index"))
                    }
                }
            }
        }
        listOf(1, 0).forEach { index ->
            compose.runOnIdle { active.value = index }
            compose.waitForIdle()
            val image = compose.onNodeWithTag("scene").captureToImage()
            val directory = File("build/reports/poster-glow").apply { mkdirs() }
            ImageIO.write(image.toAwtImage(), "png", File(directory, "shelf-$useGutterLayout-$index.png"))
            val pixels = image.toPixelMap()
            val bounds = compose.onNodeWithTag("poster$index").fetchSemanticsNode().boundsInRoot
            val scale = image.width / 600f
            val y = bounds.center.y.toInt()
            val samples = listOf(2, 4, 6, 8, 10, 12, 14).map { distance ->
                val x = if (index == 0) bounds.left - distance * scale else bounds.right + distance * scale
                pixels[x.toInt(), y].green
            }
            assertTrue(samples.first() > 0.1f, "Glow missing immediately outside artwork: $samples")
            assertTrue(samples[5] > 0.005f,
                "Blur tail is cropped to radius + spread instead of fading out: $samples")
            assertTrue(samples.zipWithNext().count { (a, b) -> a - b > 0.005f } >= 4,
                "Glow must fade continuously instead of ending at a hard edge: $samples")
        }
    }
}
