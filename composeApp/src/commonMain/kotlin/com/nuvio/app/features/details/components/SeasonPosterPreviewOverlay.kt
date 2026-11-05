package com.nuvio.app.features.details.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.ActionMenuCoordinator
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.excludeFromRemoteNavigation
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

internal data class SeasonPreviewAnchor(
    val posterUrl: String,
    val hovered: Boolean,
    val bounds: Rect,
    val visibleBounds: Rect,
    val rowState: LazyListState,
)

internal class SeasonPosterPreviewController {
    val anchors = mutableStateMapOf<Any, SeasonPreviewAnchor>()
    var owner by mutableStateOf<Any?>(null)
        private set

    fun update(token: Any, anchor: SeasonPreviewAnchor) {
        val previous = anchors[token]
        if (previous != anchor) anchors[token] = anchor
        if (anchor.hovered && previous?.hovered != true) owner = token
        else if (!anchor.hovered && owner === token) owner = null
    }

    fun unregister(token: Any) {
        anchors.remove(token)
        if (owner === token) owner = null
    }
}

internal val LocalSeasonPosterPreview = staticCompositionLocalOf<SeasonPosterPreviewController?> { null }

@Composable
internal fun Modifier.seasonPosterPreviewAnchor(
    posterUrl: String?,
    hovered: Boolean,
    rowBounds: Rect,
    rowState: LazyListState,
): Modifier {
    val controller = LocalSeasonPosterPreview.current ?: return this
    val token = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var clippedBounds by remember { mutableStateOf(Rect.Zero) }
    DisposableEffect(controller, token) {
        onDispose { controller.unregister(token) }
    }
    // Read measured state during composition so layout-only changes publish a new anchor.
    val anchor = posterUrl?.takeIf { it.isNotBlank() }?.let {
        SeasonPreviewAnchor(it, hovered, bounds, clippedBounds.intersect(rowBounds), rowState)
    }
    SideEffect {
        if (anchor == null) controller.unregister(token)
        else controller.update(token, anchor)
    }
    return onGloballyPositioned {
        bounds = Rect(it.positionInRoot(), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
        clippedBounds = it.boundsInRoot()
    }
}

/** Also suppress one-shot scrollToItem/scrollBy repositioning, which need not animate. */
@Composable
internal fun rememberSeasonPreviewScrollSuppressed(states: List<LazyListState>): Boolean {
    var repositioning by remember(states) { mutableStateOf(false) }
    LaunchedEffect(states) {
        var previous: List<Pair<Int, Int>>? = null
        snapshotFlow {
            states.map { Triple(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset, it.isScrollInProgress) }
        }.collectLatest { positions ->
            val offsets = positions.map { it.first to it.second }
            repositioning = positions.any { it.third } || previous?.let { it != offsets } == true
            previous = offsets
            if (!positions.any { it.third }) {
                withFrameNanos { }
                withFrameNanos { }
                repositioning = false
            }
        }
    }
    return repositioning || states.any { it.isScrollInProgress }
}

internal fun seasonPreviewPosition(anchor: Rect, viewport: IntSize, preview: IntSize): IntOffset? {
    if (viewport.width < preview.width + 16 || viewport.height < preview.height + 16) return null
    return IntOffset(
        ((anchor.left + anchor.right - preview.width) / 2).roundToInt().coerceIn(8, viewport.width - preview.width - 8),
        (anchor.top - preview.height - 10).roundToInt().coerceIn(8, viewport.height - preview.height - 8),
    )
}

@Composable
internal fun SeasonPosterPreviewOverlay(
    controller: SeasonPosterPreviewController,
    suppressed: Boolean,
    modifier: Modifier = Modifier,
    previewContent: @Composable (String) -> Unit = { SeasonPosterPreviewCard(it) },
) {
    var hostBounds by remember { mutableStateOf(Rect.Zero) }
    var outgoingOwner by remember { mutableStateOf<Any?>(null) }
    val owner = controller.owner
    val displayedOwner = owner ?: outgoingOwner
    val anchor = controller.anchors[displayedOwner]
    val rowStates = listOfNotNull(anchor?.rowState)
    val scrolling = rememberSeasonPreviewScrollSuppressed(rowStates)
    val density = LocalDensity.current
    val previewSize = with(density) { IntSize(110.dp.roundToPx(), 165.dp.roundToPx()) }
    val position = anchor?.let {
        seasonPreviewPosition(it.bounds.translate(-hostBounds.topLeft), IntSize(hostBounds.width.roundToInt(), hostBounds.height.roundToInt()), previewSize)
    }
    val blocked = suppressed || scrolling || ActionMenuCoordinator.active != null ||
        anchor == null || anchor.visibleBounds.isEmpty || position == null
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(owner, blocked) {
        if (blocked) {
            outgoingOwner = null
            opacity.snapTo(0f)
        } else if (owner != null) {
            outgoingOwner = owner
            opacity.animateTo(1f, tween(140))
        } else {
            opacity.animateTo(0f, tween(120))
            outgoingOwner = null
        }
    }
    Box(modifier.fillMaxSize().onGloballyPositioned { hostBounds = it.boundsInRoot() }
        .excludeFromRemoteNavigation().clearAndSetSemantics { }) {
        if (!blocked && anchor != null && position != null) {
            Box(Modifier.offset { position }.size(110.dp, 165.dp).graphicsLayer {
                alpha = opacity.value
                scaleX = 0.94f + 0.06f * alpha
                scaleY = scaleX
            }) { previewContent(anchor.posterUrl) }
        }
    }
}

@Composable
private fun SeasonPosterPreviewCard(posterUrl: String) {
    val shape = RoundedCornerShape(rememberPosterCardStyleUiState().cornerRadiusDp.dp)
    Box(Modifier.fillMaxSize().shadow(8.dp, shape)
        .nuvioCardDepth(shape = shape, surface = NuvioCardDepthSurface.Posters)
        .clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, Color.White.copy(alpha = 0.12f), shape)) {
        NuvioAsyncImage(
            model = rememberSeasonPosterHoverRequest(posterUrl),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
