package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusDirection
import kotlin.math.abs

internal data class RemoteControlRow(val page: String, val index: Int, val verticalEntry: Boolean, val rememberPosition: Boolean = false,
    val allowUpToNavigation: Boolean = false)
internal val LocalRemoteControlRow = staticCompositionLocalOf<RemoteControlRow?> { null }

/** Logical control rows keep directional navigation independent of header geometry. */
@Composable
internal fun RemoteNavigationControls(page: String, row: Int, verticalEntry: Boolean = true, rememberPosition: Boolean = false,
    allowUpToNavigation: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRemoteControlRow provides RemoteControlRow(page, row, verticalEntry, rememberPosition, allowUpToNavigation), content = content)
}

/** Null leaves ordinary spatial navigation in charge of directions outside these rows. */
internal fun RemoteNavigationSession.moveControlFocus(source: RemoteFocusTarget, direction: FocusDirection): Boolean? {
    val group = source.controlRow
    val pageId = group?.page ?: source.poster?.page ?: return null
    val controls = targets.filter {
        it.route == source.route && it.manager === source.manager && it.controlRow?.page == pageId &&
            it.bounds?.let { bounds -> bounds.width > 0 && bounds.height > 0 } == true
    }
    if (controls.isEmpty()) return null
    val origin = source.bounds ?: return null
    if (group?.rememberPosition == true) {
        // Use rendered rows so wrapped filters retain the same arrow-key behavior.
        val rows = mutableListOf<MutableList<RemoteFocusTarget>>()
        controls.sortedWith(compareBy<RemoteFocusTarget> { it.bounds!!.top }.thenBy { it.bounds!!.left }).forEach { target ->
            val row = rows.lastOrNull()
            if (row != null && row.all { it.bounds!!.bottom > target.bounds!!.top && it.bounds!!.top < target.bounds!!.bottom }) row += target
            else rows += mutableListOf(target)
        }
        rows.forEach { it.sortBy { target -> target.bounds!!.left } }
        val rowIndex = rows.indexOfFirst { source in it }
        if (rowIndex < 0) return true
        val row = rows[rowIndex]
        controlRowMemories[source.route to row.first().identity] = source.identity
        val horizontal = direction == FocusDirection.Left || direction == FocusDirection.Right
        val destination = when (direction) {
            FocusDirection.Left -> row.getOrNull(row.indexOf(source) - 1)
            FocusDirection.Right -> row.getOrNull(row.indexOf(source) + 1)
            FocusDirection.Up, FocusDirection.Down -> {
                val next = rows.getOrNull(rowIndex + if (direction == FocusDirection.Up) -1 else 1)
                next?.firstOrNull { it.identity == controlRowMemories[source.route to next.first().identity] } ?: next?.firstOrNull()
            }
            else -> return null
        }
        if (destination != null) {
            if (destination.requester.requestFocus() && direction == FocusDirection.Left) leftMovedDuringHold = true
        } else if (direction == FocusDirection.Up && group.allowUpToNavigation) {
            val topBar = navigationTarget(source.route, source.manager, RemoteNavigationRole.TopBar)
            if (topBar?.requester?.requestFocus() == true) return true
            return null
        } else if (horizontal && direction == FocusDirection.Left && canEnterSidebar()) {
            val sidebar = navigationTarget(source.route, source.manager)
            if (sidebar?.requester?.requestFocus() == true) return true
            return null
        }
        return true
    }
    fun focus(target: RemoteFocusTarget?): Boolean {
        if (target?.requester?.requestFocus() == true && direction == FocusDirection.Left && leftHeld) {
            leftMovedDuringHold = true
        }
        return true
    }
    if (group == null) {
        // Only Up from the first poster row leaves the poster navigation system.
        val poster = source.poster ?: return null
        if (direction != FocusDirection.Up || poster.role != PosterTargetRole.Poster ||
            posterPages[pageId]?.rows?.firstOrNull { it.items.isNotEmpty() }?.key != poster.row) return null
        val lastRow = controls.maxOf { it.controlRow!!.index }
        return focus(controls.filter { it.controlRow!!.index == lastRow }
            .minByOrNull { abs(it.bounds!!.center.x - origin.center.x) })
    }
    if (direction == FocusDirection.Left || direction == FocusDirection.Right) {
        val row = controls.filter { it.controlRow!!.index == group.index }.sortedBy { it.bounds!!.center.x }
        val index = row.indexOf(source)
        val next = row.getOrNull(index + if (direction == FocusDirection.Right) 1 else -1)
        if (next != null) return focus(next)
        if (direction == FocusDirection.Left && canEnterSidebar()) {
            val sidebar = navigationTarget(source.route, source.manager)
            if (sidebar?.requester?.requestFocus() == true) return true
            return null
        }
        return true
    }
    val nextRow = when (direction) {
        FocusDirection.Up -> controls.map { it.controlRow!!.index }.filter { it < group.index }.maxOrNull()
        FocusDirection.Down -> controls.map { it.controlRow!!.index }.filter { it > group.index }.minOrNull()
        else -> return null
    }
    if (nextRow == null) return if (direction == FocusDirection.Up) true else null
    val candidates = controls.filter { it.controlRow!!.index == nextRow }
    return focus(candidates.filter { it.controlRow!!.verticalEntry }.ifEmpty { candidates }
        .minByOrNull { abs(it.bounds!!.center.x - origin.center.x) })
}
