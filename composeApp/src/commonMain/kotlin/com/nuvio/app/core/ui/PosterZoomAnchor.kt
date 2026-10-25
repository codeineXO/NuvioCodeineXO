package com.nuvio.app.core.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp

/** Shared-element origin used by ordinary poster navigation, independently of action menus. */
data class PosterZoomAnchor(
    val boundsInRoot: Rect,
    val imageUrl: String?,
    val cornerRadius: Dp,
) {
    internal var source: PosterLiftSource? = null
}
