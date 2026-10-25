package com.nuvio.app.core.ui


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import com.nuvio.app.isDesktop

internal val LocalPosterClickAnchor = staticCompositionLocalOf<((PosterZoomAnchor) -> Unit)?> { null }

internal class PosterLiftSource(private val graphicsContext: GraphicsContext) : RememberObserver {
    val layer = graphicsContext.createGraphicsLayer()
    var bounds: Rect? = null
    var isLifted by mutableStateOf(false)
        private set
    private var rememberCount by mutableStateOf(0)

    val canLift: Boolean
        get() = rememberCount > 0 && !layer.isReleased && layer.size.width > 0 && layer.size.height > 0

    fun lift(): Boolean {
        if (!canLift) return false
        isLifted = true
        return true
    }

    fun land() {
        isLifted = false
        if (rememberCount == 0) release()
    }

    override fun onRemembered() {
        rememberCount++
    }

    override fun onForgotten() {
        rememberCount--
        if (rememberCount == 0 && !isLifted) release()
    }

    override fun onAbandoned() {
        if (rememberCount == 0 && !isLifted) release()
    }

    private fun release() {
        if (!layer.isReleased) graphicsContext.releaseGraphicsLayer(layer)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.posterCardClickable(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    zoomImageUrl: String? = null,
    zoomCornerRadius: Dp = NuvioTokens.Radius.poster,
    hoverScaleEnabled: Boolean = true,
): Modifier {
    val graphicsContext = LocalGraphicsContext.current
    val source = remember(graphicsContext, zoomImageUrl) { PosterLiftSource(graphicsContext) }
    val actionOpenerFocus = remember { FocusRequester() }
    val menuSource = LocalActionMenuSource.current
    SideEffect {
        menuSource?.capture = {
            ActionMenuInvocation(
                cardBounds = source.bounds,
                sourceAvailable = { source.canLift },
                restoreFocus = { actionOpenerFocus.requestFocus() },
            )
        }
    }
    val onPosterClickAnchor = LocalPosterClickAnchor.current
    val navigation = LocalRemoteNavigation.current
    val navigationItem = LocalPosterNavigationItem.current
    val posterModifier = Modifier
        .drawWithContent {
            source.layer.record { this@drawWithContent.drawContent() }
            if (!source.isLifted) drawLayer(source.layer)
        }
        .onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            source.bounds = Rect(
                left = position.x,
                top = position.y,
                right = position.x + coordinates.size.width,
                bottom = position.y + coordinates.size.height,
            )
        }
        .then(this)
    if (onClick == null && onLongClick == null) return posterModifier
    val interactionSource = remember { MutableInteractionSource() }
    fun invokeActions(pointer: Offset? = null) {
        ActionMenuInvocationHolder.stash(
            ActionMenuInvocation(
                source.bounds, pointer,
                sourceAvailable = { source.canLift },
                restoreFocus = { actionOpenerFocus.requestFocus() },
            ),
        )
        onLongClick?.invoke()
    }
    val handleLongClick = onLongClick?.let { { invokeActions() } }
    val handleClick: () -> Unit = {
                if (onClick != null) {
                    navigation?.capturePosterOrigin(navigationItem)
                    source.bounds?.let { bounds ->
                        onPosterClickAnchor?.invoke(
                            PosterZoomAnchor(bounds, zoomImageUrl, zoomCornerRadius).also { it.source = source },
                        )
                    }
                    onClick()
                }
            }
    return Modifier
        .desktopPosterHoverScale(
            enabled = hoverScaleEnabled,
            interactionSource = interactionSource,
        )
        .then(posterModifier)
        .focusRequester(actionOpenerFocus)
        .remoteNavigationTarget(shape = androidx.compose.foundation.shape.RoundedCornerShape(zoomCornerRadius), onSelect = handleClick, onContext = handleLongClick).combinedClickable(
            interactionSource = interactionSource,
            indication = if (!isDesktop && onPosterClickAnchor == null) LocalIndication.current else null,
            onClick = handleClick,
            onLongClick = handleLongClick,
        )
        .secondaryClickAt(onLongClick?.let { { local -> invokeActions(source.bounds?.topLeft?.plus(local)) } })
}

internal fun DrawScope.drawLiftedPoster(source: PosterLiftSource) {
    val layer = source.layer
    if (layer.isReleased || layer.size.width <= 0 || layer.size.height <= 0) return
    scale(
        scaleX = size.width / layer.size.width,
        scaleY = size.height / layer.size.height,
        pivot = Offset.Zero,
    ) {
        drawLayer(layer)
    }
}
