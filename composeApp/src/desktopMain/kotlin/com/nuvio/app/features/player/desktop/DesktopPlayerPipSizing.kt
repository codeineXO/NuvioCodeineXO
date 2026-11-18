package com.nuvio.app.features.player.desktop

import androidx.compose.ui.unit.IntSize
import com.nuvio.app.features.player.PlayerResizeMode
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.ceil
import kotlin.math.roundToInt

internal fun desktopMpvVideoSize(property: (String) -> String): IntSize {
    fun dimensions(prefix: String): IntSize? {
        val width = property("$prefix/dw").toIntOrNull() ?: return null
        val height = property("$prefix/dh").toIntOrNull() ?: return null
        return if (width > 0 && height > 0) IntSize(width, height) else null
    }
    return dimensions("video-out-params") ?: dimensions("video-params") ?: IntSize.Zero
}

internal fun desktopEffectiveResizeMode(mode: PlayerResizeMode, inPictureInPicture: Boolean): PlayerResizeMode =
    if (inPictureInPicture) PlayerResizeMode.Fit else mode

/**
 * Places a docked mini player inside [area] (the main window's content bounds), keeping [offsetFromBottomRight]
 * between its bottom-right corner and the area's, but never closer than [margin] to any edge.
 */
internal fun desktopDockedPipBounds(
    area: Rectangle,
    size: Dimension,
    offsetFromBottomRight: Point,
    margin: Int,
): Rectangle {
    val minX = area.x + margin
    val minY = area.y + margin
    val maxX = maxOf(minX, area.x + area.width - margin - size.width)
    val maxY = maxOf(minY, area.y + area.height - margin - size.height)
    val x = (area.x + area.width - offsetFromBottomRight.x - size.width).coerceIn(minX, maxX)
    val y = (area.y + area.height - offsetFromBottomRight.y - size.height).coerceIn(minY, maxY)
    return Rectangle(x, y, size.width, size.height)
}

internal fun desktopPipBoundsForAspectRatio(bounds: Rectangle, ratio: Float): Rectangle {
    require(ratio.isFinite() && ratio > 0f)
    val width = maxOf(bounds.width, 320, ceil(180.0 * ratio).toInt())
    val height = (width / ratio).roundToInt().coerceAtLeast(180)
    return Rectangle(bounds.x + bounds.width - width, bounds.y, width, height)
}
