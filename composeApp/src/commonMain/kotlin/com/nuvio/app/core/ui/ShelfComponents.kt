package com.nuvio.app.core.ui


import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.nuvio.app.core.ui.collectIsRemoteHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_view_all
import nuvio.composeapp.generated.resources.home_scroll_left
import nuvio.composeapp.generated.resources.home_scroll_right
import nuvio.composeapp.generated.resources.poster_logo_content_description
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class NuvioPosterShape {
    Poster,
    Square,
    Landscape,
}

private val ShelfHeaderControlSize = NuvioTokens.Space.s40

@Composable
fun <T> NuvioShelfSection(
    title: String,
    entries: List<T>,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    headerHorizontalPadding: Dp = 0.dp,
    rowContentPadding: PaddingValues = PaddingValues(0.dp),
    useGutterLayout: Boolean = false,
    itemSpacing: Dp = 10.dp,
    onViewAllClick: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    showRowNavigationControls: Boolean = false,
    key: ((T) -> Any)? = null,
    animatePlacement: Boolean = false,
    state: LazyListState = rememberLazyListState(),
    itemContent: @Composable (T) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val duplicateSafeEntries = remember(entries, key) {
        key?.let { entries.withDuplicateSafeLazyKeys(it) }
    }
    BindPosterShelf(state)
    val shelfController = rememberPosterShelfController(
        state,
        duplicateSafeEntries?.map { it.lazyKey } ?: entries.mapIndexed { index, entry -> key?.invoke(entry) ?: index },
        precedingPosterVisible = true,
    )

    ScreenActivityEffect(state) { active ->
        if (!active) state.stopScroll()
    }
    val density = LocalDensity.current
    val scrollStepPx = remember(density) { with(density) { 80.dp.toPx() } }
    val hoverViewport = remember { PosterRowHoverViewport() }
    val glowEnabled = isDesktop && rememberPosterCardStyleUiState().glowEnabled
    val glowOutset = if (glowEnabled) PosterGlowOutset else 0.dp
    val rowHoverInset = DesktopPosterRowHoverInset
    val horizontalOverflow = if (useGutterLayout) rowHoverInset else 0.dp
    val rowOverflow = glowOutset
    val direction = LocalLayoutDirection.current
    hoverViewport.inset = rowHoverInset

    val effectiveRowModifier = if (useGutterLayout) {
        rowModifier
            .padding(horizontal = headerHorizontalPadding)
            .nuvioShelfHoverOverdraw(rowOverflow, horizontalOverflow)
            .onGloballyPositioned {
                hoverViewport.left = it.positionInRoot().x
                hoverViewport.width = it.size.width
            }
            .nuvioPosterRowGutterClip()
            .nuvioPosterRowMouseWheelScroll(state = state, scrollStepPx = scrollStepPx)
            .nuvioDesktopDragScroll(state)
    } else {
        rowModifier.nuvioShelfHoverOverdraw(rowOverflow, horizontalOverflow).nuvioDesktopDragScroll(state)
    }
    val effectiveRowContentPadding = if (useGutterLayout) {
        PaddingValues(horizontal = rowHoverInset, vertical = rowOverflow)
    } else {
        PaddingValues(
            start = rowContentPadding.calculateStartPadding(direction),
            end = rowContentPadding.calculateEndPadding(direction),
            top = rowContentPadding.calculateTopPadding() + rowOverflow,
            bottom = rowContentPadding.calculateBottomPadding() + rowOverflow,
        )
    }

    val sectionModifier = if (useGutterLayout) {
        Modifier
            .fillMaxWidth()
            .then(modifier)
            // Measure the wheel region inside caller padding so the sidebar
            // inset does not shift the left gutter into the consumed center.
            .nuvioCenterScrollConsumer(headerHorizontalPadding, enabled = true)
    } else {
        modifier.fillMaxWidth()
    }

    Column(
        modifier = sectionModifier,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap + NuvioTokens.Space.s2),
    ) {
        if (title.isNotBlank()) {
            NuvioShelfSectionHeader(
                title = title,
                modifier = Modifier.padding(horizontal = headerHorizontalPadding),
                onViewAllClick = onViewAllClick,
                onTitleClick = onTitleClick,
                showRowNavigationControls = showRowNavigationControls,
                state = state,
                viewportInset = if (useGutterLayout) {
                    rowHoverInset + ShelfPosterViewportSafetyInset
                } else {
                    ShelfPosterViewportSafetyInset
                },
            )
        }
        PosterShelfScrollOwner(shelfController) {
            LazyRow(
                state = state,
                modifier = Modifier.remoteNavigationGroup()
                    .nuvioHorizontalScrollBleed(LocalVerticalBarPadding.current)
                    .then(effectiveRowModifier),
                contentPadding = effectiveRowContentPadding.withVerticalBar(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                if (duplicateSafeEntries != null) {
                    itemsIndexed(
                        items = duplicateSafeEntries,
                        key = { _, entry -> entry.lazyKey },
                        contentType = { _, _ -> "poster" },
                    ) { index, keyedEntry ->
                        PosterRowItem(
                            viewport = if (useGutterLayout) hoverViewport else null,
                            modifier = if (animatePlacement) Modifier.animateItem() else Modifier,
                        ) {
                            CompositionLocalProvider(LocalPosterShelfItem provides shelfController?.let { PosterShelfItem(it, keyedEntry.lazyKey, index) }) {
                                RemotePosterItem(key?.invoke(keyedEntry.value), index) { itemContent(keyedEntry.value) }
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = entries,
                        contentType = { _, _ -> "poster" },
                    ) { index, entry ->
                        PosterRowItem(
                            viewport = if (useGutterLayout) hoverViewport else null,
                            modifier = if (animatePlacement) Modifier.animateItem() else Modifier,
                        ) {
                            CompositionLocalProvider(LocalPosterShelfItem provides shelfController?.let { PosterShelfItem(it, key?.invoke(entry) ?: index, index) }) {
                                RemotePosterItem(key?.invoke(entry), index) { itemContent(entry) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DesktopPosterRowHoverInset = 16.dp
private val ShelfPosterViewportSafetyInset = 2.dp

private class PosterRowHoverViewport {
    var inset: Dp = DesktopPosterRowHoverInset
    var left by mutableFloatStateOf(0f)
    var width by mutableIntStateOf(0)
}

private class PosterRowItemHover {
    var scale: () -> Float = { 1f }
}

private val LocalPosterRowItemHover = staticCompositionLocalOf<PosterRowItemHover?> { null }

@Composable
private fun PosterRowItem(
    viewport: PosterRowHoverViewport?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val hover = remember { PosterRowItemHover() }
    val coordinates = remember { PosterRowHoverViewport() }
    val glowLayer = remember { PosterGlowLayer() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsRemoteHoveredAsState()
    CompositionLocalProvider(LocalPosterRowItemHover provides hover, LocalPosterGlowLayer provides glowLayer) {
        Box(
            modifier
                .zIndex(if (hovered || hover.scale() > 1f || glowLayer.shadow != null) 1f else 0f)
                .hoverable(interactionSource)
                .onGloballyPositioned {
                    origin = it.positionInRoot()
                    coordinates.left = origin.x
                },
        ) {
            PosterGlowBackdrop(glowLayer, origin)
            Box(Modifier.drawWithContent {
                    if (viewport == null || viewport.width == 0) {
                        drawContent()
                    } else {
                        val rowLeft = viewport.left - coordinates.left
                        val inset = viewport.inset.toPx()
                        val expansion = size.width * (hover.scale() - 1f).coerceAtLeast(0f) / 2f
                        // Only the existing hover scale can enter the gutter.
                        // The glow is drawn separately, outside this content clip.
                        clipRect(
                            left = rowLeft + inset - expansion,
                            right = rowLeft + viewport.width - inset + expansion,
                            top = -48.dp.toPx(),
                            bottom = size.height + 48.dp.toPx(),
                        ) { this@drawWithContent.drawContent() }
                    }
            }) { content() }
        }
    }
}

internal fun Modifier.nuvioPosterRowGutterClip(
    verticalOverflow: Dp = 48.dp,
    horizontalOverflow: Dp = 10.dp,
): Modifier = drawWithContent {
    val extraY = verticalOverflow.toPx()
    val extraX = horizontalOverflow.toPx()
    clipRect(
        left = -extraX,
        top = -extraY,
        right = size.width + extraX,
        bottom = size.height + extraY,
    ) {
        this@drawWithContent.drawContent()
    }
}

/** Reports rail-owned wheel events to an optional enclosing page switcher. */
internal class HorizontalWheelEvents {
    var handled = false
}

internal val LocalHorizontalWheelEvents = staticCompositionLocalOf<HorizontalWheelEvents?> { null }

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.nuvioPosterRowMouseWheelScroll(
    state: LazyListState,
    scrollStepPx: Float,
    enabled: Boolean = true,
): Modifier {
    if (!isDesktop || !enabled) return this

    return composed {
        val horizontalEvents = LocalHorizontalWheelEvents.current
        onPointerEvent(PointerEventType.Scroll) { event ->
            val totalDelta = event.changes.fold(0f) { acc, change ->
                acc + change.scrollDelta.y + change.scrollDelta.x
            }
            if (totalDelta != 0f) {
                horizontalEvents?.handled = true
                state.dispatchRawDelta(totalDelta * scrollStepPx)
                event.changes.forEach { it.consume() }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.nuvioCenterScrollConsumer(
    headerHorizontalPadding: Dp,
    enabled: Boolean = true,
): Modifier {
    if (!isDesktop || !enabled) return this

    return composed {
        val horizontalEvents = LocalHorizontalWheelEvents.current
        pointerInput(enabled, headerHorizontalPadding, horizontalEvents) {
            val headerPaddingPx = headerHorizontalPadding.toPx()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.type == PointerEventType.Scroll) {
                        val width = size.width.toFloat()
                        event.changes.forEach { change ->
                            val x = change.position.x
                            if (x >= headerPaddingPx && x <= width - headerPaddingPx) {
                                horizontalEvents?.handled = true
                                change.consume()
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun Modifier.nuvioDesktopDragScroll(
    state: LazyListState,
    momentumEnabled: Boolean = true,
): Modifier {
    if (momentumEnabled) return nuvioDesktopDragScroll(state, androidx.compose.foundation.gestures.Orientation.Horizontal)
    if (!isDesktop) return this

    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                state.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

internal fun Modifier.nuvioDesktopDragScroll(
    state: ScrollState,
    momentumEnabled: Boolean = true,
): Modifier {
    if (momentumEnabled) return nuvioDesktopDragScroll(state, androidx.compose.foundation.gestures.Orientation.Horizontal)
    if (!isDesktop) return this

    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                state.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

@Composable
fun NuvioPosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    basePosterWidthDp: Int? = null,
    fallbackImageUrl: String? = null,
    shape: NuvioPosterShape = NuvioPosterShape.Poster,
    detailLine: String? = null,
    showTitleBelow: Boolean = true,
    bottomLeftLogoUrl: String? = null,
    bottomLeftText: String? = null,
    isWatched: Boolean = false,
    fillMaxWidth: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val tokens = MaterialTheme.nuvio
    val effectiveBasePosterWidthDp = basePosterWidthDp ?: posterCardStyle.widthDp
    val cardWidth = shape.cardWidth(basePosterWidthDp = effectiveBasePosterWidthDp)
    val cardShape = RoundedCornerShape(posterCardStyle.cornerRadiusDp.dp)
    val glowArtworkKey = "$imageUrl\n$fallbackImageUrl"
    val glow = if (isDesktop && posterCardStyle.glowEnabled && imageUrl != null) {
        rememberPosterGlowState(glowArtworkKey)
    } else null
    val catalogLogoOverlaySize = catalogLogoOverlaySize(
        basePosterWidthDp = effectiveBasePosterWidthDp,
        shape = shape,
    )
    val shouldShowTitleBelow = showTitleBelow && !posterCardStyle.hideLabelsEnabled

    Column(
        modifier = Modifier
            .desktopPosterHoverScale()
            .then(modifier)
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier.width(cardWidth)),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(shape.aspectRatio)
                .posterGlow(glow, glowArtworkKey, cardShape)
                .clip(cardShape)
                .background(tokens.colors.surface)
                .nuvioCardDepth(
                    shape = cardShape,
                    surface = NuvioCardDepthSurface.Posters,
                )
                .posterCardClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    zoomImageUrl = imageUrl,
                    zoomCornerRadius = posterCardStyle.cornerRadiusDp.dp,
                    hoverScaleEnabled = false,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                val platformContext = coil3.compose.LocalPlatformContext.current
                val hasFallback = !fallbackImageUrl.isNullOrBlank() && fallbackImageUrl != imageUrl
                val imageModel = remember(imageUrl, fallbackImageUrl, platformContext) {
                    if (hasFallback) {
                        coil3.request.ImageRequest.Builder(platformContext)
                            .data(imageUrl)
                            .memoryCacheKeyExtras(
                                mapOf(com.nuvio.app.core.poster.CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallbackImageUrl!!)
                            )
                            .build()
                    } else {
                        imageUrl
                    }
                }
                NuvioAsyncImage(
                    model = imageModel,
                    contentDescription = title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    desktopAnimation = posterCardStyle.animatedArtworkMode.toDesktopAnimation(),
                    onSuccess = { result -> glow?.painter = result.painter },
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s14),
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!bottomLeftLogoUrl.isNullOrBlank() || !bottomLeftText.isNullOrBlank()) {
                // Gradient scrim for readability — matching NuvioTV Modern Home style
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithCache {
                            val gradient = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.58f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.75f)
                                )
                            )
                            onDrawBehind { drawRect(gradient) }
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s10),
                ) {
                    if (!bottomLeftLogoUrl.isNullOrBlank()) {
                        NuvioAsyncImage(
                            model = bottomLeftLogoUrl,
                            contentDescription = stringResource(Res.string.poster_logo_content_description, title),
                            modifier = Modifier
                                .width(catalogLogoOverlaySize.width)
                                .height(catalogLogoOverlaySize.height),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                        )
                    } else {
                        Text(
                            text = bottomLeftText.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = catalogLogoOverlaySize.textMaxWidth),
                        )
                    }
                }
            }

            NuvioPosterWatchedOverlay(isWatched = isWatched)
        }
        if (shouldShowTitleBelow) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detailLine.isNullOrBlank()) {
                Text(
                    text = detailLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(modifier = Modifier.height(NuvioTokens.Space.none))
            }
        } else {
            Box(modifier = Modifier.height(NuvioTokens.Space.none))
        }
    }
}

@Composable
private fun NuvioShelfSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewAllClick: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    showRowNavigationControls: Boolean = false,
    state: LazyListState,
    viewportInset: Dp,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    modifier = Modifier
                        .then(if (onTitleClick != null) {
                            (if (LocalPosterNavigationRow.current != null) Modifier.excludeFromRemoteNavigation()
                            else Modifier.remoteNavigationTarget()).clickable(onClick = onTitleClick)
                        } else Modifier),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showRowNavigationControls) {
                ShelfRowNavigationButton(
                    state = state,
                    direction = ShelfScrollDirection.Backward,
                    viewportInset = viewportInset,
                )
                ShelfRowNavigationButton(
                    state = state,
                    direction = ShelfScrollDirection.Forward,
                    viewportInset = viewportInset,
                )
            }
            val viewAllPlaceholderModifier = if (onViewAllClick == null) {
                if (showRowNavigationControls) Modifier.size(0.dp)
                else Modifier
                    .alpha(0f)
                    .clearAndSetSemantics { }
            } else {
                Modifier
            }
            RemotePosterItem(null, Int.MAX_VALUE, PosterTargetRole.EndAction) {
            NuvioViewAllPill(
                onClick = onViewAllClick,
                modifier = viewAllPlaceholderModifier,
            )
            }
        }
    }
}

private enum class ShelfScrollDirection { Backward, Forward }

@Composable
private fun ShelfRowNavigationButton(
    state: LazyListState,
    direction: ShelfScrollDirection,
    viewportInset: Dp,
) {
    val scope = rememberCoroutineScope()
    val viewportInsetPx = with(LocalDensity.current) { viewportInset.roundToPx() }
    val enabled = when (direction) {
        ShelfScrollDirection.Backward -> state.canScrollBackward
        ShelfScrollDirection.Forward -> state.canScrollForward
    }
    val contentDescription = stringResource(
        if (direction == ShelfScrollDirection.Backward) Res.string.home_scroll_left
        else Res.string.home_scroll_right,
    )

    // Prevent Material minimum sizing from enlarging the glass background.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides NuvioTokens.Space.none) {
        IconButton(
            onClick = {
                val scrollDistance = shelfViewportScrollDistance(state, direction, viewportInsetPx)
                if (scrollDistance != 0f) {
                    scope.launch {
                        state.animateScrollBy(scrollDistance)
                    }
                }
            },
            enabled = enabled,
            modifier = Modifier
                .size(ShelfHeaderControlSize)
                .navigationGlass(shape = CircleShape, backgroundOnly = true)
                .focusProperties { canFocus = false },
        ) {
            Icon(
                imageVector = if (direction == ShelfScrollDirection.Backward) {
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft
                } else {
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight
                },
                contentDescription = contentDescription,
                tint = MaterialTheme.nuvio.colors.textMuted,
            )
        }
    }
}

private fun shelfViewportScrollDistance(
    state: LazyListState,
    direction: ShelfScrollDirection,
    viewportInsetPx: Int,
): Float {
    val layout = state.layoutInfo
    // LazyListInfo's scroll bounds include content padding. Use the visible row
    // bounds instead, excluding the desktop hover overdraw inset when enabled.
    val viewportStart = viewportInsetPx.toFloat()
    val viewportEnd = (layout.viewportSize.width - viewportInsetPx).toFloat()
    if (viewportEnd <= viewportStart) return 0f

    val visibleItems = layout.visibleItemsInfo
    if (visibleItems.isEmpty()) return 0f

    val clippedAtEdge = when (direction) {
        ShelfScrollDirection.Backward -> visibleItems.firstOrNull { item ->
            item.offset < viewportStart && item.offset + item.size > viewportStart
        }
        ShelfScrollDirection.Forward -> visibleItems.lastOrNull { item ->
            item.offset < viewportEnd && item.offset + item.size > viewportEnd
        }
    }
    if (clippedAtEdge != null) {
        return when (direction) {
            // Bring a left-clipped card fully into view, aligned to the right edge.
            ShelfScrollDirection.Backward -> clippedAtEdge.offset + clippedAtEdge.size - viewportEnd
            // Bring a right-clipped card fully into view, aligned to the left edge.
            ShelfScrollDirection.Forward -> clippedAtEdge.offset - viewportStart
        }
    }

    val spacing = layout.mainAxisItemSpacing
    return when (direction) {
        ShelfScrollDirection.Backward -> {
            val first = visibleItems.firstOrNull { it.offset + it.size > viewportStart } ?: return 0f
            first.offset - spacing - viewportEnd
        }
        ShelfScrollDirection.Forward -> {
            val last = visibleItems.lastOrNull { it.offset < viewportEnd } ?: return 0f
            last.offset + last.size + spacing - viewportStart
        }
    }
}

@Composable
private fun NuvioViewAllPill(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val viewAllText = stringResource(Res.string.home_view_all)
    val pillShape = RoundedCornerShape(NuvioTokens.Radius.full)

    Box(
        modifier = modifier
            .height(ShelfHeaderControlSize)
            .widthIn(min = ShelfHeaderControlSize)
            // Draw the matching focus outline outside the glass clip.
            .then(if (onClick != null) Modifier.remoteNavigationTarget(shape = pillShape) else Modifier)
            .then(if (onClick != null) {
                Modifier.navigationGlass(shape = pillShape, backgroundOnly = true)
            } else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = NuvioTokens.Space.s10),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = viewAllText,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.textMuted,
            maxLines = 1,
        )
    }
}

internal const val NuvioDesktopCatalogShelfPosterScale = 1.4f
private const val DesktopPosterHoverScale = 1.045f

internal fun desktopCatalogShelfPosterBaseWidthDp(
    basePosterWidthDp: Int,
): Int =
    if (isDesktop) {
        (basePosterWidthDp * NuvioDesktopCatalogShelfPosterScale).roundToInt()
    } else {
        basePosterWidthDp
    }

internal fun Modifier.nuvioShelfHoverOverdraw(inset: Dp, horizontal: Dp = inset): Modifier {
    if (inset == 0.dp && horizontal == 0.dp) return this

    // Expand the measured viewport, then place it negatively so edge items keep
    // their visual alignment while hover scale and glow draw into the gutter.
    return layout { measurable, constraints ->
        val insetPx = inset.roundToPx()
        val horizontalPx = horizontal.roundToPx()
        val horizontalInset = horizontalPx * 2
        val verticalInset = insetPx * 2
        val expandedMaxWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth + horizontalInset
        } else {
            constraints.maxWidth
        }
        val expandedMinWidth = if (constraints.hasBoundedWidth) {
            (constraints.minWidth + horizontalInset).coerceAtMost(expandedMaxWidth)
        } else {
            constraints.minWidth
        }
        val expandedMaxHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight + verticalInset
        } else {
            constraints.maxHeight
        }
        val expandedConstraints = constraints.copy(
            minWidth = expandedMinWidth,
            maxWidth = expandedMaxWidth,
            minHeight = 0,
            maxHeight = expandedMaxHeight,
        )
        val placeable = measurable.measure(expandedConstraints)
        val width = (placeable.width - horizontalInset)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable.height - verticalInset)
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            placeable.placeRelative(-horizontalPx, -insetPx)
        }
    }
}

internal val NuvioPosterShape.aspectRatio: Float
    get() = when (this) {
        NuvioPosterShape.Poster -> 0.675f
        NuvioPosterShape.Square -> 1f
        NuvioPosterShape.Landscape -> PosterLandscapeAspectRatio
    }

private data class CatalogLogoOverlaySize(
    val width: Dp,
    val height: Dp,
    val textMaxWidth: Dp,
)

private fun catalogLogoOverlaySize(
    basePosterWidthDp: Int,
    shape: NuvioPosterShape,
): CatalogLogoOverlaySize =
    if (shape == NuvioPosterShape.Landscape) {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 92.dp, height = 24.dp, textMaxWidth = 120.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 104.dp, height = 28.dp, textMaxWidth = 132.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 116.dp, height = 30.dp, textMaxWidth = 144.dp)
            else -> CatalogLogoOverlaySize(width = 128.dp, height = 34.dp, textMaxWidth = 156.dp)
        }
    } else {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 72.dp, height = 18.dp, textMaxWidth = 92.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 80.dp, height = 20.dp, textMaxWidth = 104.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 88.dp, height = 22.dp, textMaxWidth = 112.dp)
            else -> CatalogLogoOverlaySize(width = 96.dp, height = 24.dp, textMaxWidth = 124.dp)
        }
    }

private fun NuvioPosterShape.cardWidth(basePosterWidthDp: Int): Dp =
    when (this) {
        NuvioPosterShape.Poster -> basePosterWidthDp.dp
        NuvioPosterShape.Square -> basePosterWidthDp.dp
        NuvioPosterShape.Landscape -> landscapePosterWidth(basePosterWidthDp)
    }
@Composable
internal fun Modifier.desktopPosterHoverScale(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    if (!enabled || !isDesktop) return this

    val hovered by interactionSource.collectIsRemoteHoveredAsState()
    val keyboardNavigation = isRemoteKeyboardNavigationActive
    val animatedScale = animateFloatAsState(
        targetValue = if (hovered) DesktopPosterHoverScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "desktop_poster_hover_scale",
    )
    val scale = if (keyboardNavigation) 1f else animatedScale.value

    val isScaling = hovered || scale != 1f
    // Read animation state in the parent's draw scope so its clip is invalidated
    // together with the scaled child layer on every animation frame.
    LocalPosterRowItemHover.current?.scale = { if (keyboardNavigation) 1f else animatedScale.value }

    return this
        .then(
            if (isScaling) {
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .zIndex(1f)
            } else {
                Modifier
            },
        )
        .hoverable(interactionSource)
}
