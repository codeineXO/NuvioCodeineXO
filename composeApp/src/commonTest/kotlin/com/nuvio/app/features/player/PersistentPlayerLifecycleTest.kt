package com.nuvio.app.features.player

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentPlayerLifecycleTest {

    private fun testLaunch(title: String = "Test Stream") = PlayerLaunch(
        profileId = 1,
        title = title,
        sourceUrl = "https://example.com/stream.mp4",
        streamTitle = "Stream",
        providerName = "Provider",
        parentMetaId = "tt1234567",
        parentMetaType = "movie",
    )

    @BeforeTest
    @AfterTest
    fun cleanup() {
        InAppPlayerManager.close()
    }

    @Test
    fun initialManagerIsInactive() {
        assertNull(InAppPlayerManager.activeLaunch.value)
        assertNull(InAppPlayerManager.activeLaunchId.value)
        assertFalse(InAppPlayerManager.isMiniPlayer.value)
        assertEquals(0f, InAppPlayerManager.offsetX.value)
        assertEquals(0f, InAppPlayerManager.offsetY.value)
    }

    @Test
    fun playActivatesLaunchAndStartsExpanded() {
        val launch = testLaunch("Test Stream")
        InAppPlayerManager.play(100L, launch)

        assertEquals(100L, InAppPlayerManager.activeLaunchId.value)
        assertEquals(launch, InAppPlayerManager.activeLaunch.value)
        assertFalse(InAppPlayerManager.isMiniPlayer.value)
        assertFalse(InAppPlayerManager.isMinimized(100L))
    }

    @Test
    fun minimizeAndExpandTransitions() {
        val launch = testLaunch("Test Stream")
        InAppPlayerManager.play(200L, launch)

        InAppPlayerManager.minimize()
        assertTrue(InAppPlayerManager.isMiniPlayer.value)
        assertTrue(InAppPlayerManager.isMinimized(200L))
        assertFalse(InAppPlayerManager.isMinimized(999L))

        InAppPlayerManager.expand()
        assertFalse(InAppPlayerManager.isMiniPlayer.value)
        assertFalse(InAppPlayerManager.isMinimized(200L))
    }

    @Test
    fun playSameLaunchRestoresFromMinimized() {
        val launch = testLaunch("Test Stream")
        InAppPlayerManager.play(300L, launch)
        InAppPlayerManager.minimize()
        assertTrue(InAppPlayerManager.isMiniPlayer.value)

        InAppPlayerManager.play(300L, launch)
        assertFalse(InAppPlayerManager.isMiniPlayer.value)
    }

    @Test
    fun dragAndOffsetManagement() {
        InAppPlayerManager.dragBy(15f, -25f)
        assertEquals(15f, InAppPlayerManager.offsetX.value)
        assertEquals(-25f, InAppPlayerManager.offsetY.value)

        InAppPlayerManager.setOffset(50f, 100f)
        assertEquals(50f, InAppPlayerManager.offsetX.value)
        assertEquals(100f, InAppPlayerManager.offsetY.value)

        InAppPlayerManager.resetOffset()
        assertEquals(0f, InAppPlayerManager.offsetX.value)
        assertEquals(0f, InAppPlayerManager.offsetY.value)
    }

    @Test
    fun closeResetsAllStateAndClearsStore() {
        val launch = testLaunch("Test Stream")
        val launchId = PlayerLaunchStore.put(launch)
        InAppPlayerManager.play(launchId, launch)
        InAppPlayerManager.minimize()
        InAppPlayerManager.setOffset(10f, 20f)

        InAppPlayerManager.close()

        assertNull(InAppPlayerManager.activeLaunch.value)
        assertNull(InAppPlayerManager.activeLaunchId.value)
        assertFalse(InAppPlayerManager.isMiniPlayer.value)
        assertEquals(0f, InAppPlayerManager.offsetX.value)
        assertEquals(0f, InAppPlayerManager.offsetY.value)
        assertNull(PlayerLaunchStore.get(launchId))
    }
}
