package com.nuvio.app.features.details.components

/** Pixels/second, with zero slope where the edge zone meets the stationary center. */
internal fun seasonRailEdgeVelocity(pointer: Float, start: Float, end: Float, edge: Float, maximumSpeed: Float): Float {
    if (edge <= 0f || end <= start || maximumSpeed <= 0f) return 0f
    val inset = edge.coerceAtMost((end - start) / 2f)
    val penetration = when {
        pointer < start + inset -> -((start + inset - pointer) / inset).coerceIn(0f, 1f)
        pointer > end - inset -> ((pointer - end + inset) / inset).coerceIn(0f, 1f)
        else -> return 0f
    }
    val amount = kotlin.math.abs(penetration)
    val eased = amount * amount * (3f - 2f * amount)
    return maximumSpeed * eased * if (penetration < 0f) -1f else 1f
}
