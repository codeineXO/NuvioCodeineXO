package com.nuvio.app.features.home.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.catalogPosterBaseWidthDp
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.isDesktop
import kotlin.math.ceil

private const val HomeCatalogBaselinePreviewLimit = 18
private const val HomeCatalogItemSpacingDp = 10f

internal fun homeSectionHorizontalPaddingForWidth(maxWidthDp: Float): Dp =
    desktopPageHorizontalPaddingForWidth(maxWidthDp)

internal fun homePosterGutterPaddingForWidth(
    maxWidthDp: Float,
    posterWidthDp: Dp,
    itemSpacing: Dp = 10.dp,
): Dp {
    val basePadding = desktopPageHorizontalPaddingForWidth(maxWidthDp)
    val gutterPadding = (basePadding + posterWidthDp + itemSpacing) * 0.70f
    val minContentWidth = posterWidthDp.value * 1.5f
    val maxAllowedPadding = ((maxWidthDp - minContentWidth) / 2f).coerceAtLeast(basePadding.value).dp
    return gutterPadding.coerceIn(basePadding, maxAllowedPadding)
}

internal fun homeCatalogPreviewLimitForWidth(
    maxWidthDp: Float,
    sectionPadding: Dp,
    basePosterWidthDp: Int,
    useLandscapeMode: Boolean,
    useDesktopSizing: Boolean = isDesktop,
): Int {
    val posterWidthDp = catalogPosterBaseWidthDp(basePosterWidthDp, useDesktopSizing).let {
        if (useLandscapeMode) landscapePosterWidth(it).value else it.toFloat()
    }
    val availableWidthDp = (maxWidthDp - sectionPadding.value * 2f).coerceAtLeast(0f)
    val visibleItems = ceil(
        (availableWidthDp + HomeCatalogItemSpacingDp) /
            (posterWidthDp + HomeCatalogItemSpacingDp),
    ).toInt()
    return maxOf(HomeCatalogBaselinePreviewLimit, visibleItems)
}
