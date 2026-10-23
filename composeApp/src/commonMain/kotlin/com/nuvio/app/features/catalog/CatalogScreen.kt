package com.nuvio.app.features.catalog

import com.nuvio.app.core.ui.desktopBackButtonSize
import com.nuvio.app.core.ui.desktopBackButtonContainerColor
import com.nuvio.app.core.ui.desktopBackButtonIconSize
import com.nuvio.app.core.ui.desktopBackButtonTopPadding

import com.nuvio.app.core.ui.excludeFromRemoteNavigation
import com.nuvio.app.core.ui.remoteNavigationGroup
import com.nuvio.app.core.ui.remoteNavigationTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nuvio.app.core.ui.navigationGlass
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.NuvioPosterWatchedOverlay
import com.nuvio.app.core.ui.PosterLandscapeAspectRatio
import com.nuvio.app.core.ui.catalogPosterBaseWidthDp
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.desktopPosterHoverScale
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.posterGridColumnCountForViewport
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.SkeletonPoster
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.isDesktop
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.components.HomePosterHoverPreview
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.navigation.LocalUseNativeNavigation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun CatalogScreen(
    title: String,
    subtitle: String,
    target: CatalogTarget,
    onBack: () -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by CatalogRepository.uiState.collectAsStateWithLifecycle()
    val homeCatalogSettingsUiState by HomeCatalogSettingsRepository.uiState.collectAsStateWithLifecycle()
    val posterCardStyle = rememberPosterCardStyleUiState()
    val networkStatusUiState by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val initialScrollPosition = remember(
        target,
        homeCatalogSettingsUiState.hideUnreleasedContent,
    ) {
        CatalogRepository.scrollPosition(
            target = target,
        )
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialScrollPosition.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialScrollPosition.firstVisibleItemScrollOffset,
    )
    var observedOfflineState by remember { mutableStateOf(false) }

    LaunchedEffect(target, homeCatalogSettingsUiState.hideUnreleasedContent) {
        CatalogRepository.load(
            target = target,
        )
    }

    LaunchedEffect(gridState, target, homeCatalogSettingsUiState.hideUnreleasedContent) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                CatalogRepository.saveScrollPosition(
                    target = target,
                    firstVisibleItemIndex = index,
                    firstVisibleItemScrollOffset = offset,
                )
            }
    }

    LaunchedEffect(gridState, uiState.canLoadMore, uiState.isLoading) {
        snapshotFlow { gridState.layoutInfo }
            .map { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= layoutInfo.totalItemsCount - 6
            }
            .distinctUntilChanged()
            .filter { it && uiState.canLoadMore && !uiState.isLoading }
            .collect {
                CatalogRepository.loadMore()
            }
    }

    LaunchedEffect(networkStatusUiState.condition, target) {
        when (networkStatusUiState.condition) {
            NetworkCondition.NoInternet,
            NetworkCondition.ServersUnreachable,
            -> {
                observedOfflineState = true
            }

            NetworkCondition.Online -> {
                if (!observedOfflineState) return@LaunchedEffect
                observedOfflineState = false
                CatalogRepository.load(
                    target = target,
                    force = true,
                )
            }

            NetworkCondition.Unknown,
            NetworkCondition.Checking,
            -> Unit
        }
    }

    val auraBackgroundEnabled by com.nuvio.app.features.settings.ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (auraBackgroundEnabled) Modifier else Modifier.background(MaterialTheme.colorScheme.background)
            ),
    ) {
        val pageHorizontalPadding = if (isDesktop) {
            desktopPageHorizontalPaddingForWidth(maxWidth.value)
        } else {
            16.dp
        }
        val basePosterWidthDp = catalogPosterBaseWidthDp(posterCardStyle.widthDp)
        val targetPosterWidthDp = if (posterCardStyle.catalogLandscapeModeEnabled) {
            landscapePosterWidth(basePosterWidthDp)
        } else {
            basePosterWidthDp.dp
        }
        val columns = remember(maxWidth, maxHeight, targetPosterWidthDp, pageHorizontalPadding, isDesktop) {
            if (isDesktop) {
                val availableWidth = (maxWidth - pageHorizontalPadding * 2).coerceAtLeast(0.dp)
                val spacing = 12.dp
                ((availableWidth + spacing) / (targetPosterWidthDp + spacing)).toInt().coerceAtLeast(1)
            } else {
                catalogGridColumnsForWidth(maxWidth)
            }
        }
        val gridCells = if (isDesktop) {
            GridCells.Adaptive(minSize = targetPosterWidthDp)
        } else {
            GridCells.Fixed(columns)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (auraBackgroundEnabled) {
                com.nuvio.app.features.home.components.HomeInteractiveGradientBackground(
                    modifier = Modifier.fillMaxSize(),
                    gridState = gridState,
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
                CatalogHeader(
                    title = title,
                    subtitle = subtitle,
                    pageHorizontalPadding = pageHorizontalPadding,
                    onBack = onBack,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyVerticalGrid(
                        columns = gridCells,
                        state = gridState,
                        modifier = Modifier.remoteNavigationGroup().then(Modifier.fillMaxSize()),
                        contentPadding = PaddingValues(
                            start = pageHorizontalPadding,
                            end = pageHorizontalPadding,
                            bottom = nuvioSafeBottomPadding(28.dp),
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (uiState.items.isEmpty() && uiState.isLoading) {
                            items(columns * 3) {
                                if (isDesktop) {
                                    CatalogSkeletonTile(
                                        cornerRadiusDp = posterCardStyle.cornerRadiusDp,
                                        aspectRatio = if (posterCardStyle.catalogLandscapeModeEnabled) {
                                            PosterLandscapeAspectRatio
                                        } else {
                                            0.68f
                                        },
                                    )
                                } else {
                                    SkeletonPoster(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = posterCardStyle.cornerRadiusDp.dp,
                                        showLabels = !posterCardStyle.hideLabelsEnabled,
                                    )
                                }
                            }
                        } else if (uiState.items.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CatalogEmptyState(
                                    errorMessage = uiState.errorMessage,
                                    networkCondition = networkStatusUiState.condition,
                                    onRetry = {
                                        NetworkStatusRepository.requestRefresh(force = true)
                                        CatalogRepository.load(
                                            target = target,
                                            force = true,
                                        )
                                    },
                                )
                            }
                        } else {
                            items(
                                items = uiState.items.withDuplicateSafeLazyKeys { item -> item.stableKey() },
                                key = { item -> item.lazyKey },
                            ) { keyedItem ->
                                val item = keyedItem.value
                                val isWatched = WatchingState.isPosterWatched(
                                    watchedKeys = watchedUiState.watchedKeys,
                                    item = item,
                                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                                )
                                if (isDesktop) {
                                    HomePosterCard(
                                        item = item,
                                        useLandscapeBackdropMode = posterCardStyle.catalogLandscapeModeEnabled,
                                        isWatched = isWatched,
                                        fillMaxWidth = true,
                                        onClick = onPosterClick?.let { { it(item) } },
                                        onLongClick = onPosterLongClick?.let { { it(item) } },
                                    )
                                } else {
                                    CatalogPosterTile(
                                        item = item,
                                        cornerRadiusDp = posterCardStyle.cornerRadiusDp,
                                        hideLabels = posterCardStyle.hideLabelsEnabled,
                                        isWatched = isWatched,
                                        onClick = onPosterClick?.let { { it(item) } },
                                        onLongClick = onPosterLongClick?.let { { it(item) } },
                                    )
                                }
                            }
                            if (uiState.isLoading) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    CatalogLoadingFooter()
                                }
                            }
                        }
                    }
                    NuvioDesktopVerticalScrollbar(
                        state = gridState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogHeader(
    title: String,
    subtitle: String,
    pageHorizontalPadding: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalUseNativeNavigation.current) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(44.dp),
        )
        return
    }

    val tokens = MaterialTheme.nuvio
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val effectiveTopPadding = statusBarTop + if (isDesktop) desktopBackButtonTopPadding else tokens.spacing.screenTop

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .nuvioConsumePointerEvents()
            .padding(horizontal = pageHorizontalPadding)
            .padding(top = effectiveTopPadding, bottom = NuvioTokens.Space.s4),
        verticalAlignment = if (isDesktop) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        IconButton(onClick = onBack, modifier = (if (isDesktop) Modifier.size(desktopBackButtonSize) else Modifier).navigationGlass().excludeFromRemoteNavigation()) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.action_back),
                tint = if (isDesktop) MaterialTheme.colorScheme.onBackground else tokens.colors.textPrimary,
                modifier = if (isDesktop) Modifier.size(desktopBackButtonIconSize) else Modifier,
            )
        }
        var headerTitle by remember { mutableStateOf("") }
        var headerSubtitle by remember { mutableStateOf("") }

        LaunchedEffect(title, subtitle) {
            headerTitle = title
            headerSubtitle = subtitle
        }

        AnimatedContent(
            targetState = headerTitle to headerSubtitle,
            transitionSpec = {
                (fadeIn(tween(NuvioTokens.Motion.normalMillis)) +
                    expandHorizontally(
                        animationSpec = tween(NuvioTokens.Motion.normalMillis),
                        expandFrom = Alignment.Start,
                    )) togetherWith
                (fadeOut(tween(NuvioTokens.Motion.fastMillis)) +
                    shrinkHorizontally(
                        animationSpec = tween(NuvioTokens.Motion.fastMillis),
                        shrinkTowards = Alignment.Start,
                    ))
            },
            contentAlignment = Alignment.CenterStart,
            label = "screen_header_title",
            modifier = Modifier.weight(1f, fill = false),
        ) { (currentTitle, currentSubtitle) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            ) {
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.displayLarge,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (currentSubtitle.isNotBlank()) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = tokens.colors.textMuted,
                    )
                    Text(
                        text = currentSubtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogPosterTile(
    item: MetaPreview,
    cornerRadiusDp: Int,
    hideLabels: Boolean,
    isWatched: Boolean,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    HomePosterHoverPreview(
        item = item,
        isWatched = isWatched,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .desktopPosterHoverScale()
                .then(it),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(item.posterShape.catalogAspectRatio())
                    .clip(RoundedCornerShape(cornerRadiusDp.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .nuvioCardDepth(
                        shape = RoundedCornerShape(cornerRadiusDp.dp),
                        surface = NuvioCardDepthSurface.Posters,
                    )
                    .posterCardClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        zoomImageUrl = item.poster,
                        zoomCornerRadius = cornerRadiusDp.dp,
                        hoverScaleEnabled = false,
                    ),
            ) {
                if (item.poster != null) {
                    val platformContext = coil3.compose.LocalPlatformContext.current
                    val hasFallback = !item.rawPosterUrl.isNullOrBlank() && item.rawPosterUrl != item.poster
                    val imageModel = remember(item.poster, item.rawPosterUrl, platformContext) {
                        if (hasFallback) {
                            coil3.request.ImageRequest.Builder(platformContext)
                                .data(item.poster)
                                .memoryCacheKeyExtras(
                                    mapOf(com.nuvio.app.core.poster.CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to item.rawPosterUrl!!)
                                )
                                .build()
                        } else {
                            item.poster
                        }
                    }
                    AsyncImage(
                        model = imageModel,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                NuvioPosterWatchedOverlay(isWatched = isWatched)
            }
            if (!hideLabels) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = item.releaseInfo?.let { formatReleaseDateForDisplay(it) }
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun CatalogSkeletonTile(cornerRadiusDp: Int) {
    CatalogSkeletonTile(cornerRadiusDp = cornerRadiusDp, aspectRatio = 0.68f)
}

@Composable
private fun CatalogSkeletonTile(
    cornerRadiusDp: Int,
    aspectRatio: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(MaterialTheme.colorScheme.surface),
    )
}

private fun PosterShape.catalogAspectRatio(): Float =
    when (this) {
        PosterShape.Poster -> 0.68f
        PosterShape.Square -> 1f
        PosterShape.Landscape -> 1.78f
    }

private fun catalogGridColumnsForWidth(screenWidth: Dp): Int =
    when {
        screenWidth >= 1400.dp -> 7
        screenWidth >= 1200.dp -> 6
        screenWidth >= 1000.dp -> 5
        screenWidth >= 840.dp -> 4
        else -> 3
    }

@Composable
private fun CatalogEmptyState(
    errorMessage: String?,
    networkCondition: NetworkCondition,
    onRetry: (() -> Unit)? = null,
) {
    if (
        !errorMessage.isNullOrBlank() &&
        (networkCondition == NetworkCondition.NoInternet || networkCondition == NetworkCondition.ServersUnreachable)
    ) {
        NuvioNetworkOfflineCard(
            condition = networkCondition,
            onRetry = onRetry,
        )
        return
    }

    val loadFailed = !errorMessage.isNullOrBlank()
    HomeEmptyStateCard(
        title = stringResource(
            if (loadFailed) Res.string.catalog_load_failed_title else Res.string.catalog_empty_title,
        ),
        message = errorMessage ?: stringResource(Res.string.catalog_empty_message),
        actionLabel = if (loadFailed) stringResource(Res.string.action_retry) else null,
        onActionClick = if (loadFailed) onRetry else null,
    )
}

@Composable
private fun CatalogLoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        NuvioLoadingIndicator(
            modifier = Modifier.size(22.dp),
        )
    }
}
