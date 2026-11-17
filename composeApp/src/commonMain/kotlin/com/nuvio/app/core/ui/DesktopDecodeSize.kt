package com.nuvio.app.core.ui

import kotlin.math.ceil

/** Desktop decodes artwork slightly larger than it is drawn so hover and motion stay sharp. */
internal const val DesktopDecodeHeadroom = 1.25

/**
 * One edge of a desktop decode size: the drawn size plus [headroom], rounded up to a multiple of 64 so small
 * layout changes reuse the same decode. Shared by the image loader and by prefetches that must hit its cache.
 */
internal fun desktopDecodeEdge(px: Int, headroom: Double = DesktopDecodeHeadroom): Int =
    ((ceil(px * headroom).toLong().coerceAtLeast(1) + 63) / 64 * 64)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
