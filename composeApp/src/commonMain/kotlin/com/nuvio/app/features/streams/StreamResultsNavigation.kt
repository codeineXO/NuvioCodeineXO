package com.nuvio.app.features.streams

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import com.nuvio.app.core.ui.LocalRemoteNavigation

internal data class StreamResultsFocusKey(val isTab: Boolean, val id: String?)
internal data class StreamResultsFocusRequest(val filter: String?)

/** Owned by the desktop panel; identities match provider IDs and duplicate-safe lazy keys. */
internal class StreamResultsNavigation {
    val requesters = mutableStateMapOf<StreamResultsFocusKey, FocusRequester>()
    var focused: StreamResultsFocusKey? = null
    var firstResult: String? = null
    var pending by mutableStateOf<StreamResultsFocusRequest?>(null)

    fun focusTab(filter: String?) {
        requesters[StreamResultsFocusKey(true, filter)]?.requestFocus()
    }

    fun enterResults(filter: String?) {
        focusTab(filter)
        pending = StreamResultsFocusRequest(filter)
    }
}

internal val LocalStreamResultsNavigation = staticCompositionLocalOf<StreamResultsNavigation?> { null }

internal fun Modifier.streamResultsFocus(isTab: Boolean, id: String?): Modifier = composed {
    val navigation = LocalStreamResultsNavigation.current ?: return@composed this
    val key = remember(isTab, id) { StreamResultsFocusKey(isTab, id) }
    val requester = remember(key) { FocusRequester() }
    DisposableEffect(navigation, key) {
        navigation.requesters[key] = requester
        onDispose {
            navigation.requesters.remove(key)
            if (navigation.focused == key) navigation.focused = null
        }
    }
    this.focusRequester(requester).onFocusChanged {
        if (it.isFocused) {
            navigation.focused = key
            navigation.pending?.let { pending ->
                if (key != StreamResultsFocusKey(true, pending.filter)) navigation.pending = null
            }
        } else if (navigation.focused == key) navigation.focused = null
    }
}

internal fun Modifier.streamResultsNavigation(
    navigation: StreamResultsNavigation,
    tabs: List<String?>,
    selectedFilter: String?,
    onFilterSelected: (String?) -> Unit,
): Modifier = composed {
    val session = LocalRemoteNavigation.current
    this.onFocusChanged { if (!it.hasFocus) navigation.pending = null }
        .pointerInput(navigation) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) {
                        navigation.pending = null
                    }
                }
            }
        }
        .onPreviewKeyEvent { event ->
            val focused = navigation.focused
            val resultFocused = focused != null && !focused.isTab
            val horizontal = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
            val toTab = resultFocused && focused?.id == navigation.firstResult && event.key == Key.DirectionUp
            val toResults = focused == StreamResultsFocusKey(true, selectedFilter) && event.key == Key.DirectionDown
            if (session == null || !(resultFocused && horizontal || toTab || toResults)) {
                if (event.type == KeyEventType.KeyDown) navigation.pending = null
                false
            } else {
                session.keyboard = true
                session.onPreviewKeyEvent(event)
                if (event.type == KeyEventType.KeyDown) {
                    navigation.pending = null
                    when {
                        toTab -> navigation.focusTab(selectedFilter)
                        toResults -> navigation.enterResults(selectedFilter)
                        else -> {
                            val index = tabs.indexOf(selectedFilter)
                            val next = index + if (event.key == Key.DirectionLeft) -1 else 1
                            if (index >= 0 && next in tabs.indices) {
                                val filter = tabs[next]
                                navigation.enterResults(filter)
                                onFilterSelected(filter)
                            }
                        }
                    }
                }
                true
            }
        }.focusGroup()
}
