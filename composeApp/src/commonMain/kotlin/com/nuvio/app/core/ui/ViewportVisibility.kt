package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** Uses clipped bounds so retained lazy items stop playback outside either viewport axis. */
internal fun Modifier.onViewportVisibilityChanged(onVisible: (Boolean) -> Unit): Modifier =
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        onVisible(coordinates.isAttached && bounds.width > 0f && bounds.height > 0f)
    }
