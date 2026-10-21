package com.nuvio.app.features.details

import kotlin.math.abs

internal enum class EnhancedDetailsWheelAction { Scroll, Consume, NextPage, PreviousPage }

/** A boundary can switch pages only on the first event of a fresh wheel gesture. */
internal class EnhancedDetailsWheelGesture {
    private var lastEventMillis: Long? = null
    private var suppressGesture = false

    fun isSuppressing(timeMillis: Long): Boolean =
        suppressGesture && lastEventMillis?.let { timeMillis - it < 250 } == true

    fun onScroll(
        timeMillis: Long,
        deltaX: Float,
        deltaY: Float,
        secondPage: Boolean,
        canScrollBackward: Boolean,
        canScrollForward: Boolean,
        overHorizontalContent: Boolean,
    ): EnhancedDetailsWheelAction {
        val fresh = lastEventMillis?.let { timeMillis - it >= 250 } ?: true
        lastEventMillis = timeMillis
        if (fresh) suppressGesture = false
        if (suppressGesture) return EnhancedDetailsWheelAction.Consume
        if (!fresh || overHorizontalContent || abs(deltaX) >= abs(deltaY) || abs(deltaY) <= 0.05f) {
            return EnhancedDetailsWheelAction.Scroll
        }
        val action = when {
            !secondPage && deltaY > 0 && !canScrollForward -> EnhancedDetailsWheelAction.NextPage
            secondPage && deltaY < 0 && !canScrollBackward -> EnhancedDetailsWheelAction.PreviousPage
            else -> EnhancedDetailsWheelAction.Scroll
        }
        suppressGesture = action != EnhancedDetailsWheelAction.Scroll
        return action
    }
}
