// Compose exposes cursor updates only through hover nodes; popup initialization
// needs the scene's internal service until a public cursor override is available.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.nuvio.app.core.ui

import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalPointerIconService
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
    val session = LocalRemoteNavigation.current
    val hiddenIcon = LocalRemoteHiddenPointerIcon.current
    val pointerIcons = LocalPointerIconService.current
    val keyboard = session?.keyboard == true
    // A popup has its own pointer-icon service. Hover modifiers cannot initialize
    // it when the stationary mouse is outside the popup's bounds.
    DisposableEffect(pointerIcons, keyboard, hiddenIcon) {
        if (keyboard && hiddenIcon != null) pointerIcons?.setIcon(hiddenIcon)
        onDispose {
            if (keyboard && hiddenIcon != null) {
                pointerIcons?.setIcon(if (session?.keyboard == true) hiddenIcon else PointerIcon.Default)
            }
        }
    }
    LaunchedEffect(pointerIcons, keyboard, hiddenIcon) {
        if (keyboard && hiddenIcon != null) {
            // Scene attachment can reset the platform cursor after composition.
            withFrameNanos { }
            pointerIcons?.setIcon(hiddenIcon)
        }
    }
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, properties = properties, modifier = modifier) {
        AppOverlayInput { SettingsOverlaySurfaceLayer(content) }
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
