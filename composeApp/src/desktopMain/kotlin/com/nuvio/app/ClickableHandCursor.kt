package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.nuvio.app.core.ui.NoHandCursorKey
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * Shows the hand cursor while the mouse is over a button-like clickable in [window], and hides the
 * pointer on key input until the mouse really moves again.
 *
 * Compose never switches the cursor for clickable content on its own, and the app has hundreds of click
 * targets, so this looks up what is under the pointer in the semantics tree instead of touching each one.
 * Posters, cards and context-menu rows keep the normal arrow (see [NoHandCursorKey]).
 */
@Composable
internal fun ClickableHandCursor(window: ComposeWindow) {
    val controller = remember(window) { HandCursorController(window) }
    // The phone remote hides the pointer on the window; drop a hand left on a child component.
    LaunchedEffect(controller) {
        snapshotFlow { DesktopRemoteCursorController.isHidden.value }.collect { hidden ->
            if (hidden) controller.release()
        }
    }
    DisposableEffect(controller) {
        Toolkit.getDefaultToolkit().addAWTEventListener(controller, HandCursorEventMask)
        onDispose {
            Toolkit.getDefaultToolkit().removeAWTEventListener(controller)
            controller.release()
            controller.showPointer()
        }
    }
}

private const val HandCursorEventMask =
    AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK or
        AWTEvent.KEY_EVENT_MASK

private class HandCursorController(private val window: ComposeWindow) : AWTEventListener {
    private val handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    private var overridden: Component? = null
    private var cursorBeforeOverride: Cursor? = null
    private var pending: MouseEvent? = null

    /** The component under the pointer, from the last mouse event. */
    private var hovered: Component? = null
    private var hiddenOn: Component? = null
    private var cursorBeforeHide: Cursor? = null
    private var hiddenAt: Point? = null

    override fun eventDispatched(event: AWTEvent) {
        val source = (event.source as? Component) ?: return
        if (source !== window && !SwingUtilities.isDescendingFrom(source, window)) return
        when (event) {
            is KeyEvent -> if (event.id == KeyEvent.KEY_PRESSED && !isModifierKey(event.keyCode)) hidePointer()
            is MouseEvent -> {
                if (hiddenOn != null) {
                    // Changing the cursor can make the OS report a move at the same spot; ignore it.
                    if (event.id == MouseEvent.MOUSE_MOVED && event.locationOnScreen == hiddenAt) return
                    showPointer()
                }
                hovered = event.component
                val alreadyScheduled = pending != null
                pending = event
                // Runs after Compose has handled the event, so its own cursor choice is already applied.
                if (!alreadyScheduled) SwingUtilities.invokeLater(::update)
            }
        }
    }

    private fun update() {
        val event = pending ?: return
        pending = null
        val source = event.component ?: return
        val wantsHand = hiddenOn == null &&
            !DesktopRemoteCursorController.isHidden.value &&
            event.id != MouseEvent.MOUSE_EXITED &&
            event.modifiersEx and AnyButtonDownMask == 0 &&
            isOverHandTarget(source, event)
        if (wantsHand) apply(source) else release()
    }

    private fun apply(component: Component) {
        if (overridden === component && component.cursor === handCursor) return
        // Leave cursors that a component chose on purpose (text caret, resize, autoscroll) alone.
        val current = component.cursor
        if (current !== handCursor && current.type != Cursor.DEFAULT_CURSOR) {
            overridden = null
            return
        }
        release()
        cursorBeforeOverride = if (component.isCursorSet) current else null
        overridden = component
        component.cursor = handCursor
    }

    fun release() {
        val component = overridden ?: return
        overridden = null
        // Only undo our own change; if Compose picked another cursor meanwhile, keep that one.
        if (component.cursor === handCursor) component.cursor = cursorBeforeOverride
        cursorBeforeOverride = null
    }

    /** Key input means keyboard navigation: hide the pointer where it rests until the mouse moves. */
    private fun hidePointer() {
        if (hiddenOn != null) return
        val component = hovered ?: return
        val pointer = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return
        val bounds = runCatching { java.awt.Rectangle(component.locationOnScreen, component.size) }.getOrNull() ?: return
        if (!bounds.contains(pointer)) return
        release()
        cursorBeforeHide = if (component.isCursorSet) component.cursor else null
        hiddenOn = component
        hiddenAt = pointer
        component.cursor = desktopHiddenCursor
    }

    fun showPointer() {
        val component = hiddenOn ?: return
        hiddenOn = null
        hiddenAt = null
        if (component.cursor === desktopHiddenCursor) component.cursor = cursorBeforeHide
        cursorBeforeHide = null
    }

    @OptIn(ComposeToolingApi::class)
    private fun isOverHandTarget(source: Component, event: MouseEvent): Boolean {
        val pointInContent = SwingUtilities.convertPoint(source, event.point, window.contentPane)
        val scale = window.graphicsConfiguration?.defaultTransform?.scaleX?.toFloat() ?: 1f
        val position = Offset(pointInContent.x * scale, pointInContent.y * scale)
        // Later owners are layers drawn above earlier ones (popups, dialogs): the topmost one under the
        // pointer decides, so a dialog never lets clicks targets behind it show the hand.
        val owner = window.semanticsOwners.lastOrNull { owner ->
            owner.rootSemanticsNode.boundsInWindow.contains(position)
        } ?: return false
        val target = findClickable(owner.rootSemanticsNode, position) ?: return false
        val config = target.config
        return !config.contains(SemanticsProperties.Disabled) &&
            !config.contains(SemanticsProperties.EditableText) &&
            !config.contains(SemanticsActions.SetText) &&
            // Long-pressable targets are cards (posters, continue watching, streams, episodes).
            !config.contains(SemanticsActions.OnLongClick) &&
            !target.hasNoHandMarker()
    }

    private fun SemanticsNode.hasNoHandMarker(): Boolean {
        var node: SemanticsNode? = this
        while (node != null) {
            if (node.config.contains(NoHandCursorKey)) return true
            node = node.parent
        }
        return false
    }

    /** The deepest clickable node under [position], preferring children drawn last (on top). */
    private fun findClickable(node: SemanticsNode, position: Offset): SemanticsNode? {
        val children = node.children
        for (index in children.indices.reversed()) {
            val child = children[index]
            if (child.boundsInWindow.contains(position)) {
                findClickable(child, position)?.let { return it }
            }
        }
        return node.takeIf { it.config.contains(SemanticsActions.OnClick) }
    }

    private fun isModifierKey(code: Int): Boolean = code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL ||
        code == KeyEvent.VK_ALT || code == KeyEvent.VK_META || code == KeyEvent.VK_WINDOWS ||
        code == KeyEvent.VK_ALT_GRAPH || code == KeyEvent.VK_CAPS_LOCK

    private companion object {
        const val AnyButtonDownMask =
            MouseEvent.BUTTON1_DOWN_MASK or MouseEvent.BUTTON2_DOWN_MASK or MouseEvent.BUTTON3_DOWN_MASK
    }
}
