package com.nuvio.app.features.home.components
import com.nuvio.app.core.ui.navigationMotionDuration

import com.nuvio.app.core.ui.remoteNavigationTarget

import com.nuvio.app.core.ui.NuvioDesktopAnimation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.nuvio.app.core.ui.collectIsRemoteHoveredAsState
import com.nuvio.app.core.ui.isRemoteKeyboardNavigationActive
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalWindowInfo
import com.nuvio.app.features.details.components.RatingsRow
import com.nuvio.app.features.details.formatRuntimeForDisplay
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.nuvio.app.core.ui.LocalPosterGlowLayer
import com.nuvio.app.core.ui.PosterGlowOutset
import com.nuvio.app.core.ui.posterGlow
import com.nuvio.app.core.ui.rememberArtworkGlowState
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.mdblist.MdbListMetadataService
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.format.extractReleaseYearForDisplay
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioPosterWatchedOverlay
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.ActionMenuCoordinator
import com.nuvio.app.core.ui.ActionMenuSource
import com.nuvio.app.core.ui.LocalActionMenuSource
import com.nuvio.app.core.ui.actionMenuSecondaryClick
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import com.nuvio.app.isDesktop
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.poster_logo_content_description
import org.jetbrains.compose.resources.stringResource

private const val HoverPreviewCloseDelayMillis = 120L
private val HoverPreviewEnterDurationMillis get() = navigationMotionDuration(NuvioTokens.Motion.sheetEnterMillis)
private val HoverPreviewExitDurationMillis get() = navigationMotionDuration(170)
private const val HoverTrailerExtractionDebounceMillis = 1_000L
private val HoverPreviewWidth = 600.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomePosterHoverPreview(
    item: MetaPreview,
    isWatched: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (!isDesktop) {
        content(modifier)
        return
    }

    val posterCardStyle = rememberPosterCardStyleUiState()
    val keyboardNavigation = isRemoteKeyboardNavigationActive
    val trailerPlaybackEnabled = AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP &&
        posterCardStyle.hoverPreviewTrailerEnabled
    if (!posterCardStyle.hoverPreviewEnabled) {
        content(modifier)
        return
    }

    val actionMenuOpen = ActionMenuCoordinator.active != null
    val actionMenuSource = remember(item.type, item.id) { ActionMenuSource() }

    val anchorInteractionSource = remember { MutableInteractionSource() }
    val previewInteractionSource = remember { MutableInteractionSource() }
    val anchorHovered by anchorInteractionSource.collectIsRemoteHoveredAsState()
    val previewHovered by previewInteractionSource.collectIsRemoteHoveredAsState()
    var anchorFocused by remember(item.type, item.id) { mutableStateOf(false) }
    var previewVisible by remember(item.type, item.id) { mutableStateOf(false) }
    var popupMounted by remember(item.type, item.id) { mutableStateOf(false) }
    var previewDismissedByScroll by remember(item.type, item.id) { mutableStateOf(false) }
    var trailerRequestId by remember(item.type, item.id) { mutableIntStateOf(0) }
    var trailerResolutionPending by remember(item.type, item.id) { mutableStateOf(false) }
    var trailerPlaybackSource by remember(item.type, item.id) {
        mutableStateOf<TrailerPlaybackSource?>(null)
    }

    DisposableEffect(item.type, item.id) {
        onDispose {
            anchorFocused = false
            previewVisible = false
            popupMounted = false
            trailerResolutionPending = false
            trailerPlaybackSource = null
        }
    }

    val density = LocalDensity.current
    val positionProvider = remember(density) {
        HomePosterPreviewPositionProvider(
            edgeMarginPx = with(density) { NuvioTokens.Space.s16.roundToPx() },
        )
    }

    val isKeyboardTarget = keyboardNavigation && anchorFocused
    val isHoverTarget = !keyboardNavigation && (anchorHovered || previewHovered)
    val isPreviewActive = isKeyboardTarget || isHoverTarget

    LaunchedEffect(
        isPreviewActive,
        actionMenuOpen,
        keyboardNavigation,
        posterCardStyle.hoverPreviewOpenDelayMillis,
        previewDismissedByScroll,
    ) {
        if (actionMenuOpen) {
            previewVisible = false
            popupMounted = false
            return@LaunchedEffect
        }
        if (previewDismissedByScroll) {
            if (!isPreviewActive) {
                previewDismissedByScroll = false
            }
            return@LaunchedEffect
        }
        if (isPreviewActive) {
            if (!popupMounted) {
                delay(posterCardStyle.hoverPreviewOpenDelayMillis.toLong())
                popupMounted = true
                withFrameNanos { }
            }
            previewVisible = true
        } else if (popupMounted) {
            if (keyboardNavigation) {
                previewVisible = false
                popupMounted = false
            } else {
                delay(HoverPreviewCloseDelayMillis)
                previewVisible = false
                delay(HoverPreviewExitDurationMillis.toLong())
                popupMounted = false
            }
        }
    }

    val trailerActive = trailerPlaybackEnabled && (if (keyboardNavigation) anchorFocused else anchorHovered)

    LaunchedEffect(trailerActive, trailerPlaybackEnabled, keyboardNavigation) {
        if (
            trailerActive &&
            trailerPlaybackSource == null &&
            !trailerResolutionPending
        ) {
            delay(HoverTrailerExtractionDebounceMillis)
            trailerResolutionPending = true
            trailerRequestId += 1
        }
    }

    LaunchedEffect(
        trailerRequestId,
        trailerPlaybackEnabled,
        trailerActive,
        item.type,
        item.id,
    ) {
        if (!trailerActive) {
            trailerRequestId = 0
            trailerPlaybackSource = null
            trailerResolutionPending = false
            return@LaunchedEffect
        }
        if (trailerRequestId == 0) return@LaunchedEffect
        try {
            trailerPlaybackSource = resolveHomePosterHoverTrailerPlaybackSource(item)
        } finally {
            trailerResolutionPending = false
        }
    }

    Box(modifier = modifier) {
        CompositionLocalProvider(LocalActionMenuSource provides actionMenuSource) {
            content(
                Modifier
                    .onFocusChanged { anchorFocused = it.hasFocus }
                    .hoverable(anchorInteractionSource, enabled = !keyboardNavigation)
            )
        }

        if (popupMounted && !actionMenuOpen) {
            val previewState = rememberHomePosterPreviewState(item)
            val glowOutset = if (posterCardStyle.glowEnabled) PosterGlowOutset else 0.dp
            val windowSize = LocalWindowInfo.current.containerSize
            val availableWidth = with(density) { windowSize.width.toDp() } - (NuvioTokens.Space.s16 + glowOutset) * 2
            val availableHeight = with(density) { windowSize.height.toDp() } - (NuvioTokens.Space.s16 + glowOutset) * 2
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(focusable = false),
            ) {
                // A popup owns its glow rather than writing into the shelf behind it.
                CompositionLocalProvider(LocalPosterGlowLayer provides null) {
                    AnimatedVisibility(
                        visible = previewVisible,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = HoverPreviewEnterDurationMillis,
                                easing = NuvioTokens.Motion.standard,
                            ),
                        ) + scaleIn(
                            animationSpec = tween(
                                durationMillis = HoverPreviewEnterDurationMillis,
                                easing = NuvioTokens.Motion.emphasized,
                            ),
                            initialScale = 0.96f,
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = HoverPreviewEnterDurationMillis,
                                easing = NuvioTokens.Motion.standard,
                            ),
                            initialOffsetY = { height -> height / 18 },
                        ),
                        exit = fadeOut(
                            animationSpec = tween(HoverPreviewExitDurationMillis),
                        ) + scaleOut(
                            animationSpec = tween(HoverPreviewExitDurationMillis),
                            targetScale = 0.97f,
                        ) + slideOutVertically(
                            animationSpec = tween(HoverPreviewExitDurationMillis),
                            targetOffsetY = { height -> height / 32 },
                        ),
                    ) {
                        HomePosterPreviewCard(
                            actionMenuSource = actionMenuSource,
                            item = previewState.item,
                            ratingSources = previewState.ratingSources,
                            previewWidth = minOf(HoverPreviewWidth, availableWidth.coerceAtLeast(1.dp)),
                            previewMaxHeight = availableHeight.coerceAtLeast(1.dp),
                            isWatched = isWatched,
                            trailerEnabled = trailerPlaybackEnabled,
                            trailerSoundEnabled = posterCardStyle.hoverPreviewTrailerSoundEnabled,
                            trailerStartSeconds = posterCardStyle.hoverPreviewTrailerStartSeconds,
                            trailerPlaybackSource = trailerPlaybackSource,
                            modifier = Modifier
                                .padding(glowOutset)
                                .hoverable(previewInteractionSource)
                                .onPointerEvent(PointerEventType.Scroll) {
                                    previewDismissedByScroll = true
                                    previewVisible = false
                                    popupMounted = false
                                },
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun HomePosterPreviewCard(
    actionMenuSource: ActionMenuSource,
    item: MetaPreview,
    isWatched: Boolean,
    ratingSources: List<String>,
    previewWidth: androidx.compose.ui.unit.Dp,
    previewMaxHeight: androidx.compose.ui.unit.Dp,
    trailerEnabled: Boolean,
    trailerSoundEnabled: Boolean,
    trailerStartSeconds: Int,
    trailerPlaybackSource: TrailerPlaybackSource?,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(NuvioTokens.Radius.xl)
    val previewBackground = tokens.colors.background
    val clickInteractionSource = remember { MutableInteractionSource() }
    var logoLoadError by remember(item.type, item.id, item.logo) { mutableStateOf(false) }
    val logoUrl = item.logo?.takeIf { it.isNotBlank() && !logoLoadError }
    val artworkUrl = listOf(item.banner, item.landscapePoster, item.poster)
        .firstOrNull { !it.isNullOrBlank() }
    val glow = rememberArtworkGlowState(artworkUrl)

    Box(
        modifier = modifier
            .width(previewWidth)
            .sizeIn(maxHeight = previewMaxHeight)
            .posterGlow(glow, shape, forceActive = true)
            .shadow(tokens.elevation.overlay, shape)
            .clip(shape)
            .background(previewBackground)
            .border(tokens.borders.hairline, tokens.colors.borderStrong, shape)
            .remoteNavigationTarget(shape = shape)
            .clickable(
                interactionSource = clickInteractionSource,
                indication = null,
                enabled = onClick != null,
            ) { onClick?.invoke() }
            .actionMenuSecondaryClick(onLongClick, actionMenuSource),
    ) {
        if (artworkUrl != null) {
            NuvioAsyncImage(
                model = artworkUrl,
                contentDescription = item.name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                desktopAnimation = NuvioDesktopAnimation.Always,
                onSuccess = { result -> glow?.painter = result.painter },
            )
        }
        if (trailerEnabled) {
            HomePosterHoverTrailer(
                playbackSource = trailerPlaybackSource,
                soundEnabled = trailerSoundEnabled,
                startPositionSeconds = trailerStartSeconds,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to Color.Black.copy(alpha = 0.15f),
                    0.7f to Color.Black.copy(alpha = 0.78f),
                    1f to Color.Black.copy(alpha = 0.98f),
                ),
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minOf(previewWidth * 9f / 16f, previewMaxHeight))
                .verticalScroll(rememberScrollState())
                .padding(NuvioTokens.Space.s18),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Keep a clear artwork area even when wrapping ratings increase the card height.
            Spacer(Modifier.height(previewWidth * 0.25f))
            if (logoUrl != null) {
                NuvioAsyncImage(
                    model = logoUrl,
                    contentDescription = stringResource(Res.string.poster_logo_content_description, item.name),
                    modifier = Modifier.width(220.dp).height(56.dp),
                    alignment = Alignment.CenterStart,
                    contentScale = ContentScale.Fit,
                    clipToBounds = false,
                    onError = { logoLoadError = true },
                )
            } else {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                PreviewMetadataRatings(item, ratingSources)
                item.genres.take(3).joinToString(" • ").takeIf { it.isNotBlank() }?.let { genres ->
                    Text(
                        text = genres,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        NuvioPosterWatchedOverlay(isWatched = isWatched, padding = NuvioTokens.Space.s10)
    }
}

/** Reserve enabled source slots before the request completes, including wrap rows. */
@Composable
private fun PreviewMetadataRatings(item: MetaPreview, ratingSources: List<String>) {
    val reservedRatings = remember(ratingSources) {
        ratingSources.map { source ->
            val maximum = when (source) {
                MdbListMetadataService.PROVIDER_IMDB,
                MdbListMetadataService.PROVIDER_LETTERBOXD,
                MdbListMetadataService.PROVIDER_MAL -> 10.0
                else -> 100.0
            }
            MetaExternalRating(source, maximum)
        }
    }
    val ratings = item.externalRatings.ifEmpty {
        item.imdbRating?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?.let { listOf(MetaExternalRating(MdbListMetadataService.PROVIDER_IMDB, it)) }
            .orEmpty()
    }
    val metadata = item.previewMetadataLine()
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Box(Modifier.fillMaxWidth()) {
            RatingsRow(
                ratings = reservedRatings,
                modifier = Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics {},
                wrap = true,
                compact = true,
            )
            RatingsRow(
                ratings = ratings,
                modifier = Modifier.fillMaxWidth(),
                wrap = true,
                compact = true,
            )
        }
    }
}

private fun MetaPreview.previewMetadataLine(): String = buildList {
    releaseInfo?.takeIf { it.isNotBlank() }?.let { extractReleaseYearForDisplay(it)?.toString() ?: it }
        ?.takeIf { it.isNotBlank() }?.let(::add)
    formatRuntimeForDisplay(runtime)?.takeIf { it.isNotBlank() }?.let(::add)
    ageRating?.takeIf { it.isNotBlank() }?.let(::add)
}.joinToString(" • ")

private class HomePosterPreviewPositionProvider(
    private val edgeMarginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx)
            .coerceAtLeast(edgeMarginPx)
        val maxY = (windowSize.height - popupContentSize.height - edgeMarginPx)
            .coerceAtLeast(edgeMarginPx)
        val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val centeredY = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2

        return IntOffset(
            x = centeredX.coerceIn(edgeMarginPx, maxX),
            y = centeredY.coerceIn(edgeMarginPx, maxY),
        )
    }
}
