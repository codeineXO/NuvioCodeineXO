package com.nuvio.app.core.ui

import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.isDesktop

/** In-window Compose layers retain their background scene's text-field focus. */
internal object AppOverlayInputLayers {
    private val layers = mutableListOf<Any>()
    val current: Any? get() = layers.lastOrNull()
    fun attach(layer: Any): () -> Unit {
        layers += layer
        return { layers.remove(layer) }
    }
}

internal val LocalAppOverlayInputLayer = staticCompositionLocalOf<Any?> { null }

@Composable
internal fun AppOverlayInput(content: @Composable () -> Unit) {
    if (!isDesktop) { content(); return }
    val layer = remember { Any() }
    DisposableEffect(layer) {
        val detach = AppOverlayInputLayers.attach(layer)
        onDispose { detach() }
    }
    CompositionLocalProvider(LocalAppOverlayInputLayer provides layer, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppBasicAlertDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, properties = properties) {
        AppOverlayInput(content)
    }
}

@Composable
internal fun AppPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit,
) {
    Popup(popupPositionProvider = popupPositionProvider, onDismissRequest = onDismissRequest, properties = properties) {
        AppOverlayInput(content)
    }
}

@Composable
internal fun AppPopup(
    alignment: Alignment,
    offset: IntOffset,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit,
) {
    Popup(alignment = alignment, offset = offset, onDismissRequest = onDismissRequest, properties = properties) {
        AppOverlayInput(content)
    }
}
