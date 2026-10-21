package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ShelfHoverClippingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstAndLastPostersCanExpandIntoBothGutters() {
        lateinit var state: LazyListState
        lateinit var scope: CoroutineScope
        compose.setContent {
            NuvioTheme {
                state = rememberLazyListState()
                scope = rememberCoroutineScope()
                Box(Modifier.size(500.dp, 240.dp).background(Color.Black).testTag("viewport")) {
                    NuvioShelfSection(
                        title = "",
                        entries = (0..5).toList(),
                        state = state,
                        key = { it },
                        useGutterLayout = true,
                        headerHorizontalPadding = 24.dp,
                    ) { index ->
                        Box(Modifier.desktopPosterHoverScale().size(200.dp, 120.dp)
                            .background(Color.Red).testTag("poster$index"))
                    }
                }
            }
        }
        fun assertExpanded(index: Int, left: Boolean) {
            val viewport = compose.onNodeWithTag("viewport")
            val poster = compose.onNodeWithTag("poster$index")
            val bounds = poster.fetchSemanticsNode().boundsInRoot
            val root = viewport.fetchSemanticsNode().boundsInRoot
            poster.performMouseInput { enter(center) }
            compose.mainClock.advanceTimeBy(1500)
            val pixels = viewport.captureToImage().toPixelMap()
            val pxPerDp = pixels.width / 500f
            val edge = if (left) bounds.left - root.left else bounds.right - root.left
            val x = (edge + if (left) -3 * pxPerDp else 3 * pxPerDp).toInt()
            val y = (bounds.center.y - root.top).toInt()
            val pixel = pixels[x, y]
            assertTrue(pixel.red > 0.9f && pixel.green < 0.1f,
                "Hovered poster $index is clipped at its ${if (left) "left" else "right"} edge: $pixel")
            poster.performMouseInput { exit() }
            compose.mainClock.advanceTimeBy(1500)
        }
        assertExpanded(0, left = true)
        compose.runOnIdle { scope.launch { state.scrollToItem(5) } }
        compose.waitForIdle()
        assertExpanded(5, left = false)
    }
}
