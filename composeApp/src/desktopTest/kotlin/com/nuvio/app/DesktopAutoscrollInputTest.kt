package com.nuvio.app

import kotlin.test.*

class DesktopAutoscrollInputTest {
    @Test
    fun earlierKeyListenerCancelsScrollExactlyOnce() {
        var stops = 0
        val detach = DesktopAutoscrollInput.attach { stops++ }
        try {
            assertTrue(DesktopAutoscrollInput.cancel())
            assertFalse(DesktopAutoscrollInput.cancel())
            assertEquals(1, stops)
        } finally { detach() }
    }

    @Test
    fun disposingOldScrollDoesNotDisconnectNewScroll() {
        val detachOld = DesktopAutoscrollInput.attach { error("Stale scroll") }
        var stopped = false
        val detachNew = DesktopAutoscrollInput.attach { stopped = true }
        try {
            detachOld()
            assertTrue(DesktopAutoscrollInput.cancel())
            assertTrue(stopped)
        } finally { detachNew() }
    }
}
