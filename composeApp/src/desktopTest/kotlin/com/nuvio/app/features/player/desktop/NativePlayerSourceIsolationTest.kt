package com.nuvio.app.features.player.desktop

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativePlayerSourceIsolationTest {
    @Test
    fun outgoingHandleIsNotReadyForTheNextSource() {
        val controller = controller()
        val headers = mapOf("Authorization" to "test")
        attach(controller, "episode-1", headers)
        val source = field("pendingSource").get(controller)
        field("attachedSource").set(controller, source)
        field("handle").setLong(controller, 41L)
        assertTrue(controller.isAttachedTo("episode-1", headers))
        assertFalse(controller.isAttachedTo("episode-2", headers))
        assertFalse(controller.isAttachedTo("episode-1", emptyMap()))

        attach(controller, "episode-2", headers)
        assertFalse(controller.isAttachedTo("episode-1", headers))
        assertFalse(controller.isAttachedTo("episode-2", headers))
        field("attachedSource").set(controller, field("pendingSource").get(controller))
        field("handle").setLong(controller, 42L)
        assertTrue(controller.isAttachedTo("episode-2", headers))
        controller.dispose()
    }

    @Test
    fun queuedCommandsCannotSwitchToAReplacementHandleOrSource() = runTest {
        val controller = controller()
        field("playerScope").set(controller, backgroundScope)
        field("handle").setLong(controller, 41L)
        val calls = mutableListOf<Long>()
        val command = NativePlayerController::class.java.getDeclaredMethod(
            "executePlayerCommand", kotlin.jvm.functions.Function1::class.java,
        ).apply { isAccessible = true }
        val action: (Long) -> Unit = { calls += it }

        command.invoke(controller, action)
        field("handle").setLong(controller, 42L)
        runCurrent()
        assertTrue(calls.isEmpty())

        command.invoke(controller, action)
        attach(controller, "episode-2", emptyMap())
        runCurrent()
        assertTrue(calls.isEmpty())

        command.invoke(controller, action)
        runCurrent()
        assertEquals(listOf(42L), calls)
        controller.dispose()
    }

    private fun controller() = NativePlayerController(
        host = NativePlayerHost(),
        nativeCreate = { _, _, _, _, _, _, _, _, _ -> error("No native player in unit tests") },
        nativeDispose = {},
        isHostDisplayable = { false },
    )

    private fun attach(controller: NativePlayerController, url: String, headers: Map<String, String>) {
        controller.attach(url, headers, true, 0L, 0, false, {})
    }

    private fun field(name: String) = NativePlayerController::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }
}
