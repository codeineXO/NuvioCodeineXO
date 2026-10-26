package com.nuvio.app.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Fades only row content, revealing the actual background without changing hit targets. */
internal fun Modifier.nuvioPosterRowEdgeFade(
    state: LazyListState,
    horizontalInset: Dp = 0.dp,
): Modifier = drawWithCache {
    val inset = horizontalInset.toPx().coerceIn(0f, size.width / 2f)
    val left = inset
    val right = size.width - inset
    val width = NuvioTokens.Space.s32.toPx().coerceAtMost((right - left) / 2f)
    // Match the existing gutter clip's allowance for hover scale and poster glow.
    val overflow = NuvioTokens.Space.s48.toPx()
    val bounds = Rect(-overflow, -overflow, size.width + overflow, size.height + overflow)
    val paint = Paint()
    val leftMask = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, Color.Black),
        startX = left,
        endX = left + width,
    )
    val rightMask = Brush.horizontalGradient(
        colors = listOf(Color.Black, Color.Transparent),
        startX = right - width,
        endX = right,
    )

    onDrawWithContent {
        val layout = state.layoutInfo
        val measured = layout.totalItemsCount > 0 && layout.visibleItemsInfo.isNotEmpty()
        val backwardOnLeft = (layoutDirection == LayoutDirection.Ltr) != layout.reverseLayout
        val fadeLeft = measured && if (backwardOnLeft) state.canScrollBackward else state.canScrollForward
        val fadeRight = measured && if (backwardOnLeft) state.canScrollForward else state.canScrollBackward
        if (width <= 0f || (!fadeLeft && !fadeRight)) {
            drawContent()
        } else {
            val canvas = drawContext.canvas
            canvas.saveLayer(bounds, paint)
            try {
                drawContent()
                if (fadeLeft) {
                    drawRect(
                        brush = leftMask,
                        topLeft = Offset(bounds.left, bounds.top),
                        size = Size(left + width - bounds.left, bounds.height),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (fadeRight) {
                    drawRect(
                        brush = rightMask,
                        topLeft = Offset(right - width, bounds.top),
                        size = Size(bounds.right - right + width, bounds.height),
                        blendMode = BlendMode.DstIn,
                    )
                }
            } finally {
                canvas.restore()
            }
        }
    }
}
