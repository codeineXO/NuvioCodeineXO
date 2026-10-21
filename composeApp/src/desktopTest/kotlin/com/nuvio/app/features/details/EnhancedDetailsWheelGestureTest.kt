package com.nuvio.app.features.details

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnhancedDetailsWheelGestureTest {
    private val gesture = EnhancedDetailsWheelGesture()
    private fun scroll(time: Long, delta: Float = 1f, second: Boolean = false,
        backward: Boolean = false, forward: Boolean = false, horizontal: Boolean = false, x: Float = 0f,
    ) = gesture.onScroll(time, x, delta, second, backward, forward, horizontal)

    @Test fun reachingBoundaryNeedsFreshGesture() {
        assertEquals(EnhancedDetailsWheelAction.Scroll, scroll(0, forward = true))
        assertEquals(EnhancedDetailsWheelAction.Scroll, scroll(50))
        assertEquals(EnhancedDetailsWheelAction.Scroll, scroll(200))
        assertEquals(EnhancedDetailsWheelAction.NextPage, scroll(450))
    }

    @Test fun momentumIsSuppressedAcrossPageReplacement() {
        assertEquals(EnhancedDetailsWheelAction.NextPage, scroll(0))
        assertTrue(gesture.isSuppressing(30))
        assertEquals(EnhancedDetailsWheelAction.Consume, scroll(30, -1f, second = true))
        assertEquals(EnhancedDetailsWheelAction.Consume, scroll(100, second = true))
        assertEquals(EnhancedDetailsWheelAction.PreviousPage, scroll(350, -1f, second = true))
    }

    @Test fun secondPageScrollsNormallyUntilItsTop() {
        assertEquals(EnhancedDetailsWheelAction.Scroll, scroll(0, -1f, second = true, backward = true))
        assertEquals(EnhancedDetailsWheelAction.Scroll, scroll(30, -1f, second = true))
        assertEquals(EnhancedDetailsWheelAction.PreviousPage, scroll(280, -1f, second = true))
    }

    @Test fun horizontalRailsAndTrackpadAxisDoNotSwitchPages() {
        assertEquals(EnhancedDetailsWheelAction.Scroll, scroll(0, horizontal = true))
        assertEquals(EnhancedDetailsWheelAction.Scroll, scroll(300, x = 2f))
        assertEquals(EnhancedDetailsWheelAction.Scroll, scroll(600, delta = 0f))
        assertEquals(EnhancedDetailsWheelAction.NextPage, scroll(900))
    }
}
