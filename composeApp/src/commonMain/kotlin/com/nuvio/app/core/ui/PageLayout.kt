package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/** Desktop top bar layout metrics for vertical alignment and responsive height. */
internal val desktopTopBarTopPadding = 10.dp

internal fun desktopTopBarHeight(windowWidth: Dp): Dp = when {
    windowWidth < 800.dp -> 46.dp
    windowWidth > 1600.dp -> 50.dp
    else -> 48.dp
}

internal fun desktopTopBarActionIconSize(windowWidth: Dp): Dp = when {
    windowWidth < 800.dp -> 22.dp
    windowWidth > 1600.dp -> 26.dp
    else -> 24.dp
}

/**
 * Shared geometry of the desktop top-left edge button: the top bar's power button and every page's back
 * button. All of them sit in the same slot, centred on the top bar row, so navigating never moves it.
 */
private val desktopEdgeButtonInset = 10.dp
internal val desktopBackButtonStartPadding = 16.dp
internal val desktopBackButtonTopPadding = desktopTopBarTopPadding + desktopEdgeButtonInset / 2
internal val desktopBackButtonContainerColor = Color.Black.copy(alpha = 0.34f)

internal fun desktopBackButtonSize(windowWidth: Dp): Dp = desktopTopBarHeight(windowWidth) - desktopEdgeButtonInset

internal fun desktopBackButtonIconSize(windowWidth: Dp): Dp = desktopTopBarActionIconSize(windowWidth) - 4.dp

/** Window width in the current density, for sizing the edge button identically on every screen. */
@Composable
internal fun desktopWindowWidth(): Dp {
    val widthPx = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { widthPx.toDp() }
}

/**
 * Horizontal inset for top-level desktop page content.
 *
 * Full-bleed artwork stays outside this inset; headers, controls, shelves, and
 * grids use it so their leading and trailing edges remain aligned.
 */
internal fun desktopPageHorizontalPaddingForWidth(maxWidthDp: Float): Dp =
    when {
        maxWidthDp >= 1440f -> 32.dp
        maxWidthDp >= 1024f -> 28.dp
        maxWidthDp >= 768f -> 24.dp
        else -> 16.dp
    }
