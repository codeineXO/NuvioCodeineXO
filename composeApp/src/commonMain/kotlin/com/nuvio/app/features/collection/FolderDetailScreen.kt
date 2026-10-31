package com.nuvio.app.features.collection

import com.nuvio.app.core.ui.desktopBackButtonTopPadding

import com.nuvio.app.core.ui.remoteNavigationGroup
import com.nuvio.app.core.ui.BindPosterNavigationPage
import com.nuvio.app.core.ui.PosterNavigationRow
import com.nuvio.app.core.ui.RemotePosterRow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import com.nuvio.app.core.ui.NuvioLazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.nuvio.app.core.ui.NuvioLazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalRippleConfiguration
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.PosterGlowOutset
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.aspectRatio
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.PosterLandscapeAspectRatio
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.core.ui.navigationGlassSource
import com.nuvio.app.core.ui.catalogPosterBaseWidthDp
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.LocalDesktopSidebarContentInset
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.posterGridColumnCountForViewport
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.canOpenCatalog
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomePosterHoverPreview
import com.nuvio.app.features.home.components.homeCatalogPreviewLimitForWidth
import com.nuvio.app.features.home.components.homePosterGutterPaddingForWidth
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.isDesktop
import com.nuvio.app.navigation.LocalUseNativeNavigation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.home.components.HomeInteractiveGradientBackground
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.collections_folder_empty_items
import nuvio.composeapp.generated.resources.collections_folder_not_found
import nuvio.composeapp.generated.resources.collections_tab_all
import org.jetbrains.compose.resources.stringResource

private val FolderCoverHeight = 176.dp

@Composable
fun FolderDetailScreen(
    onBack: () -> Unit,
    onCatalogClick: (HomeCatalogSection) -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val uiState by FolderDetailRepository.uiState.collectAsState()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsState()
    val homeCatalogSettings by HomeCatalogSettingsRepository.settings.collectAsState()
    val folder = uiState.folder
    val useNativeNavigation = LocalUseNativeNavigation.current
    val coverImageUrl = folder?.coverImageUrl?.takeIf { it.isNotBlank() }
    val auraBackgroundEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()

    if (!isDesktop) {
        MobileFolderDetailContent(
            uiState = uiState,
            watchedKeys = watchedUiState.watchedKeys,
            homeCatalogSettings = homeCatalogSettings,
            coverImageUrl = coverImageUrl,
            auraBackgroundEnabled = auraBackgroundEnabled,
            useNativeNavigation = useNativeNavigation,
            onBack = onBack,
            onCatalogClick = onCatalogClick,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (auraBackgroundEnabled || coverImageUrl != null) Modifier else Modifier.background(MaterialTheme.colorScheme.background)
            ),
    ) {
        if (coverImageUrl != null) {
            Box(Modifier.matchParentSize().navigationGlassSource()) {
                AsyncImage(
                    model = coverImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.06f
                            scaleY = 1.06f
                        }
                        .blur(28.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.85f,
                )
            }
        } else if (auraBackgroundEnabled) {
            HomeInteractiveGradientBackground(Modifier.fillMaxSize())
        } else {
            Box(Modifier.matchParentSize().navigationGlassSource(zIndex = -1f).background(MaterialTheme.colorScheme.background))
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val desktopPagePadding = desktopPageHorizontalPaddingForWidth(maxWidth.value)
            val sidebarInset = LocalDesktopSidebarContentInset.current
            Column(modifier = Modifier.fillMaxSize().padding(start = sidebarInset)) {
                if (!useNativeNavigation) {
                    NuvioScreenHeader(
                        title = folder?.title ?: uiState.collectionTitle,
                        modifier = Modifier,
                        backgroundColor = Color.Transparent,
                        includeStatusBarPadding = false,
                        topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + desktopBackButtonTopPadding,
                        onBack = onBack,
                        backButtonKeyboardNavigationEnabled = false,
                    )
                }

                if (folder == null && !uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.collections_folder_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    return@Column
                }

                when (uiState.viewMode) {
                    FolderViewMode.TABBED_GRID -> TabbedGridContent(
                        uiState = uiState,
                        watchedKeys = watchedUiState.watchedKeys,
                        pageHorizontalPadding = desktopPagePadding,
                        modifier = Modifier.weight(1f),
                        onTabSelected = { FolderDetailRepository.selectTab(it) },
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                    )
                    FolderViewMode.ROWS -> RowsContent(
                        uiState = uiState,
                        watchedKeys = watchedUiState.watchedKeys,
                        homeCatalogSettings = homeCatalogSettings,
                        modifier = Modifier.weight(1f),
                        onCatalogClick = onCatalogClick,
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                    )
                    FolderViewMode.FOLLOW_LAYOUT -> RowsContent(
                        uiState = uiState,
                        watchedKeys = watchedUiState.watchedKeys,
                        homeCatalogSettings = homeCatalogSettings,
                        modifier = Modifier.weight(1f),
                        onCatalogClick = onCatalogClick,
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileFolderDetailContent(
    uiState: FolderDetailUiState,
    watchedKeys: Set<String>,
    homeCatalogSettings: HomeCatalogSettingsUiState,
    coverImageUrl: String?,
    auraBackgroundEnabled: Boolean,
    useNativeNavigation: Boolean,
    onBack: () -> Unit,
    onCatalogClick: (HomeCatalogSection) -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val folder = uiState.folder
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxHeroHeightPx = with(density) { FolderCoverHeight.toPx() }
    var heroHeightPx by remember(coverImageUrl, maxHeroHeightPx) {
        mutableFloatStateOf(if (coverImageUrl != null) maxHeroHeightPx else 0f)
    }
    val heroScrollConnection = remember(coverImageUrl, maxHeroHeightPx) {
        object : NestedScrollConnection {
            fun consumeHeroDelta(deltaY: Float): Float {
                if (coverImageUrl == null || deltaY == 0f) return 0f
                val previousHeight = heroHeightPx
                val nextHeight = (previousHeight + deltaY).coerceIn(0f, maxHeroHeightPx)
                heroHeightPx = nextHeight
                return nextHeight - previousHeight
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                return Offset(x = 0f, y = consumeHeroDelta(available.y))
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                return Offset(x = 0f, y = consumeHeroDelta(available.y))
            }
        }
    }
    val heroHeight = with(density) { heroHeightPx.toDp() }
    val heroCollapseFraction = if (coverImageUrl == null || maxHeroHeightPx == 0f) {
        1f
    } else {
        1f - (heroHeightPx / maxHeroHeightPx)
    }
    val contentModifier = if (coverImageUrl != null) {
        Modifier.nestedScroll(heroScrollConnection)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (auraBackgroundEnabled || coverImageUrl != null) Modifier else Modifier.background(MaterialTheme.colorScheme.background)
            ),
    ) {
        if (coverImageUrl == null && auraBackgroundEnabled) {
            HomeInteractiveGradientBackground(Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (coverImageUrl != null && heroHeight > 0.dp) {
                FolderCoverImage(
                    imageUrl = coverImageUrl,
                    title = folder?.title.orEmpty(),
                    modifier = Modifier.height(heroHeight),
                )
            }

            if (!useNativeNavigation) {
                NuvioScreenHeader(
                    title = folder?.title ?: uiState.collectionTitle,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    includeStatusBarPadding = coverImageUrl == null,
                    topPadding = if (coverImageUrl != null) statusBarTop * heroCollapseFraction else null,
                    onBack = onBack,
                )
            }

            if (folder == null && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.collections_folder_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Box
            }

            when (uiState.viewMode) {
                FolderViewMode.TABBED_GRID -> TabbedGridContent(
                    uiState = uiState,
                    watchedKeys = watchedKeys,
                    modifier = Modifier.weight(1f).then(contentModifier),
                    onTabSelected = { FolderDetailRepository.selectTab(it) },
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
                FolderViewMode.ROWS,
                FolderViewMode.FOLLOW_LAYOUT,
                -> RowsContent(
                    uiState = uiState,
                    watchedKeys = watchedKeys,
                    homeCatalogSettings = homeCatalogSettings,
                    modifier = Modifier.weight(1f).then(contentModifier),
                    onCatalogClick = onCatalogClick,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            }
        }
    }
}

@Composable
private fun FolderCoverImage(
    imageUrl: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = title,
        modifier = modifier
            .fillMaxWidth()
            .height(FolderCoverHeight),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun TabbedGridContent(
    uiState: FolderDetailUiState,
    watchedKeys: Set<String>,
    pageHorizontalPadding: Dp = 16.dp,
    modifier: Modifier = Modifier,
    onTabSelected: (Int) -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, uiState.selectedTabIndex, uiState.selectedTabCanLoadMore, uiState.selectedTabIsLoadingMore) {
        snapshotFlow { gridState.layoutInfo }
            .map { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= layoutInfo.totalItemsCount - 6
            }
            .distinctUntilChanged()
            .filter { it && uiState.selectedTabCanLoadMore && !uiState.selectedTabIsLoadingMore }
            .collect {
                FolderDetailRepository.loadMoreSelectedTab()
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.tabs.size > 1) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTabIndex,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = pageHorizontalPadding,
                    containerColor = if (isDesktop) Color.Transparent else MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    divider = {},
                ) {
                    uiState.tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == uiState.selectedTabIndex,
                            onClick = { onTabSelected(index) },
                            text = {
                                Text(
                                    text = if (tab.isAllTab) {
                                        stringResource(Res.string.collections_tab_all)
                                    } else {
                                        tab.label
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val selectedTab = uiState.tabs.getOrNull(uiState.selectedTabIndex)
        if (selectedTab == null) return

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val posterCardStyle = rememberPosterCardStyleUiState()
            val columns = remember(maxWidth, maxHeight, posterCardStyle.widthDp, isDesktop) {
                if (isDesktop) {
                    posterGridColumnCountForViewport(maxWidth, maxHeight, posterCardStyle.widthDp)
                } else {
                    folderDetailGridColumnsForWidth(maxWidth)
                }
            }
            val basePosterWidthDp = catalogPosterBaseWidthDp(posterCardStyle.widthDp)
            val gridCells = if (isDesktop) {
                GridCells.Adaptive(basePosterWidthDp.dp)
            } else {
                GridCells.Fixed(columns)
            }

            when {
                selectedTab.isLoading && selectedTab.items.isEmpty() -> LoadingIndicator()
                selectedTab.error != null && selectedTab.items.isEmpty() -> ErrorMessage(selectedTab.error)
                selectedTab.items.isEmpty() -> EmptyMessage()
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NuvioLazyVerticalGrid(
                            columns = gridCells,
                            state = gridState,
                            modifier = Modifier.remoteNavigationGroup().then(Modifier.fillMaxSize()),
                            contentPadding = PaddingValues(
                                start = maxOf(pageHorizontalPadding, if (isDesktop && posterCardStyle.glowEnabled) PosterGlowOutset else 0.dp),
                                end = maxOf(pageHorizontalPadding, if (isDesktop && posterCardStyle.glowEnabled) PosterGlowOutset else 0.dp),
                                top = if (isDesktop && posterCardStyle.glowEnabled) PosterGlowOutset else if (isDesktop) 8.dp else 4.dp,
                                bottom = nuvioSafeBottomPadding(if (isDesktop && posterCardStyle.glowEnabled) PosterGlowOutset else 18.dp),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(if (isDesktop) 12.dp else 10.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isDesktop) 18.dp else 14.dp),
                        ) {
                            items(
                                items = selectedTab.items.withDuplicateSafeLazyKeys { item -> item.stableKey() },
                                key = { item -> item.lazyKey },
                            ) { keyedItem ->
                                val item = keyedItem.value
                                val isWatched = WatchingState.isPosterWatched(
                                    watchedKeys = watchedKeys,
                                    item = item,
                                )
                                HomePosterHoverPreview(
                                    item = item,
                                    isWatched = isWatched,
                                    onClick = { onPosterClick(item) },
                                    onLongClick = onPosterLongClick?.let { { it(item) } },
                                    modifier = if (isDesktop) Modifier.fillMaxWidth() else Modifier,
                                ) { cardModifier ->
                                    NuvioPosterCard(
                                        title = item.name,
                                        imageUrl = item.poster,
                                        modifier = cardModifier,
                                        basePosterWidthDp = if (isDesktop) basePosterWidthDp else null,
                                        fillMaxWidth = isDesktop,
                                        fallbackImageUrl = item.rawPosterUrl,
                                        shape = NuvioPosterShape.Poster,
                                        detailLine = item.releaseInfo,
                                        isWatched = isWatched,
                                        onClick = { onPosterClick(item) },
                                        onLongClick = onPosterLongClick?.let { { it(item) } },
                                    )
                                }
                            }

                            if (uiState.selectedTabIsLoadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    PaginationLoadingFooter()
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
}

@Composable
internal fun RowsContent(
    uiState: FolderDetailUiState,
    watchedKeys: Set<String>,
    modifier: Modifier = Modifier,
    homeCatalogSettings: HomeCatalogSettingsUiState = HomeCatalogSettingsRepository.settings.collectAsState().value,
    onCatalogClick: (HomeCatalogSection) -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    slots: List<FolderRowSlot> = FolderDetailRepository.getRowSlots(uiState),
    listState: LazyListState = rememberLazyListState(),
) {
    if (uiState.isLoading && slots.isEmpty()) {
        LoadingIndicator()
        return
    }

    if (slots.isEmpty() && !uiState.isLoading) {
        EmptyMessage()
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val homeSectionPadding = if (isDesktop) homeSectionHorizontalPaddingForWidth(maxWidth.value) else null
        val posterCardStyle = rememberPosterCardStyleUiState()
        val useGutterLayout = homeCatalogSettings.scrollPostersLayoutEnabled && isDesktop
        val basePosterWidthDp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp)
        val effectivePosterWidth = if (posterCardStyle.catalogLandscapeModeEnabled) {
            landscapePosterWidth(basePosterWidthDp)
        } else {
            basePosterWidthDp.dp
        }
        val effectivePosterSectionPadding = if (useGutterLayout) {
            homePosterGutterPaddingForWidth(maxWidth.value, effectivePosterWidth)
        } else {
            homeSectionPadding
        }
        val sectionBottomPadding = if (useGutterLayout) 24.dp else 12.dp
        val catalogPreviewLimit = if (isDesktop) {
            homeCatalogPreviewLimitForWidth(
                maxWidthDp = maxWidth.value,
                sectionPadding = effectivePosterSectionPadding ?: 0.dp,
                basePosterWidthDp = posterCardStyle.widthDp,
                useLandscapeMode = posterCardStyle.catalogLandscapeModeEnabled,
            )
        } else {
            18
        }
        // Use the same reserved geometry for loading, populated, empty and failed rows.
        // In particular, loading a row above the viewport must not move the visible content.
        val density = LocalDensity.current
        val typography = MaterialTheme.typography
        val labelsHeight = with(density) {
            if (posterCardStyle.hideLabelsEnabled) NuvioTokens.Space.s6 else
                typography.bodyMedium.lineHeight.toDp() + typography.labelSmall.lineHeight.toDp() + NuvioTokens.Space.s6 * 2
        }
        val imageHeight = effectivePosterWidth / if (posterCardStyle.catalogLandscapeModeEnabled) {
            PosterLandscapeAspectRatio
        } else NuvioPosterShape.Poster.aspectRatio
        val headerHeight = with(density) { maxOf(48.dp, typography.titleLarge.lineHeight.toDp()) }
        val slotHeight = headerHeight + MaterialTheme.nuvio.spacing.controlGap + NuvioTokens.Space.s2 +
            imageHeight + labelsHeight + sectionBottomPadding
        BindPosterNavigationPage(
            "collection", uiState.collectionTitle to uiState.folder?.id,
            slots.mapIndexed { index, slot ->
                PosterNavigationRow(slot.key, slot.section?.items.orEmpty().map { it.stableKey() }, index)
            },
            listState, uiState.isLoading,
        )
        NuvioLazyColumn(
            state = listState,
            modifier = Modifier.remoteNavigationGroup().then(Modifier.fillMaxSize()),
            contentPadding = PaddingValues(
                bottom = nuvioSafeBottomPadding(18.dp),
            ),
            verticalArrangement = if (useGutterLayout) Arrangement.Top else Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = slots,
                key = { it.key },
            ) { slot ->
                val section = slot.section
                Box(Modifier.fillMaxWidth().height(slotHeight)) {
                    if (section == null || section.items.isEmpty()) {
                        if (slot.tab.isLoading) {
                            HomeSkeletonRow(horizontalPadding = effectivePosterSectionPadding)
                        } else {
                            Column(Modifier.fillMaxSize().padding(horizontal = effectivePosterSectionPadding ?: 16.dp)) {
                                Box(Modifier.fillMaxWidth().height(headerHeight), contentAlignment = Alignment.CenterStart) {
                                    Text(slot.tab.label, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                Box(Modifier.fillMaxWidth().weight(1f)) {
                                    if (slot.tab.error != null) ErrorMessage(slot.tab.error) else EmptyMessage()
                                }
                            }
                        }
                    } else RemotePosterRow("collection", slot.key) {
                        HomeCatalogRowSection(
                            section = section,
                            entries = if (isDesktop) {
                                // Keep every fetched poster available for horizontal scrolling.
                                section.items
                            } else {
                                section.items.take(catalogPreviewLimit)
                            },
                            modifier = Modifier.padding(bottom = sectionBottomPadding),
                            sectionPadding = effectivePosterSectionPadding,
                            useGutterLayout = useGutterLayout,
                            onViewAllClick = if (section.canOpenCatalog(catalogPreviewLimit)) {
                                { onCatalogClick(section) }
                            } else {
                                null
                            },
                            watchedKeys = watchedKeys,
                            onPosterClick = { onPosterClick(it) },
                            onPosterLongClick = onPosterLongClick,
                        )
                    }
                }
            }
        }
        NuvioDesktopVerticalScrollbar(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
}

private fun folderDetailGridColumnsForWidth(screenWidth: Dp): Int =
    when {
        screenWidth >= 1400.dp -> 7
        screenWidth >= 1200.dp -> 6
        screenWidth >= 1000.dp -> 5
        screenWidth >= 840.dp -> 4
        else -> 3
    }

@Composable
private fun PaginationLoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        NuvioLoadingIndicator(
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        NuvioLoadingIndicator(
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun ErrorMessage(error: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyMessage() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.collections_folder_empty_items),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun PosterShape.toNuvioPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }
