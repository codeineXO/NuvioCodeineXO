package com.nuvio.app

import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import kotlin.test.*
import org.junit.Test

class DesktopBackspaceInputTest {
    private val source = Canvas()
    private fun event(id: Int, modifiers: Int = 0) = KeyEvent(
        source, id, 0L, modifiers,
        if (id == KeyEvent.KEY_TYPED) KeyEvent.VK_UNDEFINED else KeyEvent.VK_BACK_SPACE, '\b',
    )

    @Test fun oneEscapeSequencePerPhysicalPressEvenWhenEligibilityChanges() {
        val input = DesktopBackspaceInput()
        val down = event(KeyEvent.KEY_PRESSED)
        assertFalse(input.process(down, true))
        assertEquals(KeyEvent.VK_ESCAPE, down.keyCode)
        assertTrue(input.process(event(KeyEvent.KEY_TYPED), false))
        assertTrue(input.process(event(KeyEvent.KEY_PRESSED), false))
        val up = event(KeyEvent.KEY_RELEASED)
        assertFalse(input.process(up, false))
        assertEquals(KeyEvent.VK_ESCAPE, up.keyCode)
        val next = event(KeyEvent.KEY_PRESSED)
        assertFalse(input.process(next, true))
        assertEquals(KeyEvent.VK_ESCAPE, next.keyCode)
    }

    @Test fun editingPlayerAndModifiedBackspaceRemainUntouched() {
        for (modifiers in listOf(0, InputEvent.CTRL_DOWN_MASK, InputEvent.ALT_DOWN_MASK, InputEvent.SHIFT_DOWN_MASK, InputEvent.META_DOWN_MASK)) {
            val input = DesktopBackspaceInput()
            repeat(2) {
                val down = event(KeyEvent.KEY_PRESSED, modifiers)
                assertFalse(input.process(down, modifiers != 0))
                assertEquals(KeyEvent.VK_BACK_SPACE, down.keyCode)
                assertFalse(input.process(event(KeyEvent.KEY_TYPED), false))
            }
            val up = event(KeyEvent.KEY_RELEASED, modifiers)
            assertFalse(input.process(up, false))
            assertEquals(KeyEvent.VK_BACK_SPACE, up.keyCode)
        }
    }

    @Test fun losingFocusResetsRepeatSuppression() {
        val input = DesktopBackspaceInput()
        input.process(event(KeyEvent.KEY_PRESSED), true)
        input.reset()
        val down = event(KeyEvent.KEY_PRESSED)
        assertFalse(input.process(down, true))
        assertEquals(KeyEvent.VK_ESCAPE, down.keyCode)
    }
}
