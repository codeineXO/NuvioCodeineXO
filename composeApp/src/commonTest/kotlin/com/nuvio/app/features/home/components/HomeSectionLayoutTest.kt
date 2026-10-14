package com.nuvio.app.features.home.components

import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeSectionLayoutTest {

    @Test
    fun `gutter padding adds one poster width plus spacing reduced by thirty percent on wide screens`() {
        val screenWidth = 1920f
        val posterWidth = 148.dp
        val itemSpacing = 10.dp
        val basePadding = desktopPageHorizontalPaddingForWidth(screenWidth)

        val gutterPadding = homePosterGutterPaddingForWidth(
            maxWidthDp = screenWidth,
            posterWidthDp = posterWidth,
            itemSpacing = itemSpacing,
        )

        assertEquals((basePadding + posterWidth + itemSpacing) * 0.70f, gutterPadding)
    }

    @Test
    fun `gutter padding is constrained on narrow screens`() {
        val screenWidth = 400f
        val posterWidth = 148.dp

        val gutterPadding = homePosterGutterPaddingForWidth(
            maxWidthDp = screenWidth,
            posterWidthDp = posterWidth,
        )

        // Must leave at least 1.5 * posterWidth for content
        val remainingContentWidth = screenWidth - (gutterPadding.value * 2f)
        assertTrue(remainingContentWidth >= posterWidth.value * 1.5f)
    }
}
