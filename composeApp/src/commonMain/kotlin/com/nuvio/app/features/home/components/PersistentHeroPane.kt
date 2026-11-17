package com.nuvio.app.features.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.nuvio.app.core.ui.ActionMenuCoordinator
import com.nuvio.app.core.ui.DesktopBackdropVerticalBias
import com.nuvio.app.core.ui.LocalSnapVerticalRows
import com.nuvio.app.core.ui.LocalVerticalWheelOwner
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PosterGlowOutset
import com.nuvio.app.core.ui.aspectRatio
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.landscapePosterHeightForWidth
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.fullscreenActionHorizontalInsetForWidth
import com.nuvio.app.core.ui.nuvioVerticalMouseWheelScroll
import com.nuvio.app.core.ui.navigationGlassSource
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.LocalPosterNavigationItem
import com.nuvio.app.core.ui.collectIsRemoteHoveredAsState
import com.nuvio.app.core.ui.isRemoteKeyboardNavigationActive
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.LocalPersistentHeroDetailsSource
import com.nuvio.app.features.details.PersistentHeroDetails
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.mdblist.MdbListSettings
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.remote.RemoteAppBridge
import com.nuvio.app.features.tmdb.TmdbSettings
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.isDesktop
import com.nuvio.app.navigation.DetailRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal val LocalPersistentHeroSelection = staticCompositionLocalOf<((PersistentHeroTarget) -> Unit)?> { null }
internal val LocalPersistentHeroPageState = staticCompositionLocalOf<PersistentHeroPageState?> { null }

internal class PersistentHeroPageState(val contentGeneration: Int = 0) {
    private data class PageKey(
        val page: Any,
        val profileIndex: Int?,
        val mdbList: MdbListSettings,
        val tmdbSettings: TmdbSettings,
    )

    private var key: PageKey? = null
    private var selection = PersistentHeroSelection()

    fun selectionFor(page: Any, profileIndex: Int?, mdbList: MdbListSettings, tmdbSettings: TmdbSettings): PersistentHeroSelection {
        val pageKey = PageKey(page, profileIndex, mdbList, tmdbSettings)
        if (key != pageKey) {
            key = pageKey
            selection = PersistentHeroSelection()
        }
        return selection
    }
}

internal class PersistentHeroSelection {
    class Request(val item: MetaPreview)

    var requested by mutableStateOf<Request?>(null)
        private set
    var displayed by mutableStateOf<LoadedHero?>(null)
        private set

    var target by mutableStateOf<PersistentHeroTarget?>(null)
        private set
    var previous: PersistentHeroTarget? = null
        private set

    fun select(next: PersistentHeroTarget) {
        if (target == next) return
        previous = target
        target = next
        if (requested?.item != next.item) {
            requested = Request(next.item)
        }
    }

    fun publish(request: Request, hero: LoadedHero) {
        if (requested === request) displayed = hero
    }
}

internal data class LoadedHero(
    val item: MetaPreview,
    val backdrop: Painter?,
    val logo: Painter?,
    val metadataReady: Boolean = false,
) {
    val showMetadataBadge: Boolean
        get() = metadataReady || (!item.ageRating.isNullOrBlank() && !item.status.isNullOrBlank())
}

private const val HeroContentGraceMs = 200L
private const val HeroTransitionMs = 240

@Composable
internal fun PersistentHeroPane(
    enabled: Boolean,
    page: Any,
    seed: MetaPreview?,
    loading: Boolean,
    rowScrollState: ScrollableState,
    rowAnchors: () -> List<Int>,
    posterRows: Map<Any, List<MetaPreview>>,
    onHeroClick: ((MetaPreview) -> Unit)?,
    backdropModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    startInset: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
    rail: (@Composable (maxHeight: Dp) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Column(modifier.fillMaxSize()) {
            header?.invoke()
            Box(Modifier.weight(1f)) { content() }
        }
        return
    }

    val profile by ProfileRepository.state.collectAsStateWithLifecycle()
    val mdbList by remember {
        MdbListSettingsRepository.ensureLoaded()
        MdbListSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val metaSettings by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val tmdbSettings by remember {
        TmdbSettingsRepository.ensureLoaded()
        TmdbSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val pageState = LocalPersistentHeroPageState.current ?: remember { PersistentHeroPageState() }
    val selection = pageState.selectionFor(page, profile.activeProfile?.profileIndex, mdbList, tmdbSettings)
    val active = LocalScreenActive.current
    val submit = remember(selection, active) {
        { target: PersistentHeroTarget -> if (active) selection.select(target) }
    }
    LaunchedEffect(active, selection, seed) {
        if (active && selection.requested == null && seed != null) selection.select(PersistentHeroTarget(seed))
    }
    val detailsSource = LocalPersistentHeroDetailsSource.current
    DisposableEffect(active, selection, detailsSource) {
        val capture: (DetailRoute) -> PersistentHeroDetails? = { route ->
            val key = "${route.type}:${route.id}"
            val hero = selection.displayed?.takeIf { it.item.stableKey() == key }
                ?: selection.requested?.item?.takeIf { it.stableKey() == key }?.let { LoadedHero(it, null, null) }
            hero?.let { PersistentHeroDetails(it.item, it.backdrop, it.logo, it.showMetadataBadge) }
        }
        if (active) detailsSource?.capture = capture
        onDispose {
            if (detailsSource?.capture === capture) detailsSource.capture = null
        }
    }

    // Keep a title opened away from Home queued until its hero can load it.
    if (page == "home") {
        LaunchedEffect(active, selection, RemoteAppBridge.heroPicks) {
            if (active) RemoteAppBridge.heroPicks.collect { pick ->
                if (pick != null && RemoteAppBridge.consumeHeroPick(pick)) {
                    selection.select(PersistentHeroTarget(pick.item, pick.row, pick.index))
                }
            }
        }
    }

    val currentRowAnchors by rememberUpdatedState(rowAnchors)
    val anchors = remember { { currentRowAnchors() } }
    BoxWithConstraints(modifier.fillMaxSize().nuvioVerticalMouseWheelScroll(rowScrollState, rowAnchors = anchors)) {
        var headerHeight by remember { mutableStateOf(0.dp) }
        val heroHeight = persistentHeroHeight(
            maxHeight,
            topInset,
            hasHeader = header != null,
            rowReserve = persistentHeroRowReserve(),
            headerHeight = headerHeight,
        )
        val railEndInset = fullscreenActionHorizontalInsetForWidth(maxWidth.value)
        val context = LocalPlatformContext.current
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val logoHeightPx = with(density) { DesktopHeroMaxLogoHeight.roundToPx().coerceAtLeast(1) }
        val showMdbListRatings = mdbList.isActive && mdbList.showOnHero && mdbList.enabledProvidersInPriorityOrder().isNotEmpty()
        val request = selection.requested
        val scope = rememberCoroutineScope()
        val loader = remember(selection, context, widthPx, heightPx, logoHeightPx) {
            PersistentHeroLoader(
                scope, context, SingletonImageLoader.get(context), widthPx, heightPx, logoHeightPx,
                mdbList.takeIf { showMdbListRatings },
            )
        }
        DisposableEffect(loader) { onDispose { loader.retain(emptySet()) } }

        LaunchedEffect(active, selection, request, loader) {
            if (!active || request == null) return@LaunchedEffect
            val preview = request.item
            val previous = selection.displayed?.takeIf { it.item.stableKey() == preview.stableKey() }
            var item = previous?.item ?: preview
            var logo = previous?.logo
            var backdrop = previous?.backdrop
            var metadataReady = previous?.metadataReady == true
            var revealed = false
            fun publish() = selection.publish(request, LoadedHero(item, backdrop, logo, metadataReady))

            val artwork = async { loader.backdrop(preview, foreground = true) }
            val metadata = launch {
                item = loader.enrich(preview, foreground = true).copy(
                    logo = item.logo,
                    externalRatings = item.externalRatings,
                )
                metadataReady = true
                if (revealed) publish()
            }
            val titleLogo = launch {
                val currentLogo = previous?.logo?.let { painter ->
                    previous.item.logo.nonBlank()?.let { PersistentHeroLoader.Logo(it, painter) }
                }
                loader.logo(preview, foreground = true, currentLogo = currentLogo)?.let { (url, painter) ->
                    item = item.copy(logo = url)
                    logo = painter
                    if (revealed) publish()
                }
            }
            val ratings = launch {
                loader.ratings(preview, foreground = true)?.let {
                    item = item.copy(externalRatings = it)
                    if (revealed) publish()
                }
            }
            backdrop = artwork.await() ?: backdrop
            // Commit one title/backdrop pair even when some optional assets miss the reveal window.
            withTimeoutOrNull(HeroContentGraceMs) { joinAll(metadata, titleLogo, ratings) }
            revealed = true
            publish()
        }

        val neighbors = remember(posterRows, selection.target) {
            selection.target?.let { persistentHeroNeighbors(posterRows, it, selection.previous) }.orEmpty()
        }
        LaunchedEffect(active, loader, request, neighbors) {
            if (!active || request == null) return@LaunchedEffect
            val keep = neighbors.mapTo(mutableSetOf(request.item.stableKey()), MetaPreview::stableKey)
            loader.retain(keep)
            delay(100)
            neighbors.forEach { item -> launch { loader.preload(item) } }
        }
        val hero = selection.displayed
        val heroTransition = updateTransition(hero, label = "Persistent hero")
        PersistentHeroBackdropFrame(backdropModifier) {
            heroTransition.AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                contentKey = { it?.item?.stableKey() },
                transitionSpec = {
                    (fadeIn(tween(HeroTransitionMs, easing = FastOutSlowInEasing)) togetherWith
                        ExitTransition.KeepUntilTransitionsFinished).apply { targetContentZIndex = 1f }
                },
            ) { displayed ->
                PersistentHeroBackdropImage(displayed?.backdrop)
            }
        }
        Column(Modifier.fillMaxSize().padding(top = topInset)) {
            if (header != null) {
                Box(Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) { header() }
            }
            val showSkeleton = loading || request != null || seed != null
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                heroTransition.AnimatedContent(
                    modifier = Modifier.fillMaxSize().padding(start = startInset),
                    transitionSpec = {
                        fadeIn(tween(HeroTransitionMs, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(tween(HeroTransitionMs, easing = FastOutSlowInEasing))
                    },
                    contentKey = { it?.item?.stableKey() },
                ) { displayed ->
                    if (displayed != null) {
                        PersistentHomeHero(
                            item = displayed.item,
                            onDetailsClick = onHeroClick.takeIf {
                                active && displayed.item.stableKey() == hero?.item?.stableKey()
                            },
                            logo = displayed.logo,
                            showImdbRating = metaSettings.showOverallRatings,
                            showMdbListRatings = showMdbListRatings,
                            showMetadataBadge = displayed.showMetadataBadge,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (showSkeleton) {
                        HomeSkeletonHero(modifier = Modifier.fillMaxSize(), containerMode = true)
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
                if (isDesktop && rail != null) {
                    val railGap = NuvioTokens.Space.s16
                    Box(
                        Modifier.align(Alignment.CenterEnd).padding(end = railEndInset),
                    ) {
                        rail((heroHeight - railGap * 2).coerceAtLeast(0.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                CompositionLocalProvider(
                    LocalPersistentHeroSelection provides submit,
                    LocalSnapVerticalRows provides true,
                    LocalVerticalWheelOwner provides rowScrollState,
                ) {
                    content()
                }
            }
        }
    }
}

private val PersistentHeroMinHeight = 180.dp

/** Shrinks the hero before it covers the first full poster row (labels included), down to a floor. */
internal fun persistentHeroHeight(
    viewportHeight: Dp,
    topInset: Dp,
    hasHeader: Boolean = false,
    rowReserve: Dp = 0.dp,
    headerHeight: Dp = 0.dp,
): Dp {
    val available = (viewportHeight - topInset).coerceAtLeast(0.dp)
    val fraction = if (hasHeader) 0.44f else 0.56f
    val target = minOf(440.dp, available * fraction)
    if (rowReserve <= 0.dp) return target
    val fitted = (available - headerHeight - rowReserve).coerceAtLeast(PersistentHeroMinHeight)
    return minOf(target, fitted)
}

/** Height of one poster shelf: header, poster, title and year lines, plus glow/hover overdraw. */
@Composable
internal fun persistentHeroRowReserve(): Dp {
    val style = rememberPosterCardStyleUiState()
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val base = desktopCatalogShelfPosterBaseWidthDp(style.widthDp)
    val posterHeight = if (style.catalogLandscapeModeEnabled) {
        landscapePosterHeightForWidth(landscapePosterWidth(base))
    } else {
        (base / NuvioPosterShape.Poster.aspectRatio).dp
    }
    val labels = with(density) {
        if (style.hideLabelsEnabled) NuvioTokens.Space.s6
        else NuvioTokens.Space.s6 * 2 + typography.bodyMedium.lineHeight.toDp() +
            (if (style.hideYearEnabled) 0.dp else typography.labelSmall.lineHeight.toDp())
    }
    val overdraw = if (isDesktop && style.glowEnabled) PosterGlowOutset * 2 else NuvioTokens.Space.s8
    val header = NuvioTokens.Space.s40 + NuvioTokens.Space.s8 + NuvioTokens.Space.s2
    return header + posterHeight + labels + overdraw + NuvioTokens.Space.s12
}

@Composable
internal fun PersistentHeroBackdrop(artwork: Painter?, modifier: Modifier) {
    PersistentHeroBackdropFrame(modifier) {
        AnimatedContent(
            targetState = artwork,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(if (initialState == null) 320 else 240, easing = FastOutSlowInEasing)) togetherWith
                    ExitTransition.KeepUntilTransitionsFinished).apply { targetContentZIndex = 1f }
            },
            label = "Persistent hero artwork",
        ) { painter ->
            PersistentHeroBackdropImage(painter)
        }
    }
}

@Composable
private fun PersistentHeroBackdropImage(painter: Painter?) {
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, DesktopBackdropVerticalBias),
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.nuvio.colors.background))
    }
}

@Composable
private fun PersistentHeroBackdropFrame(modifier: Modifier, artwork: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize()
            .navigationGlassSource(zIndex = -1f)
            .background(MaterialTheme.nuvio.colors.background),
    ) {
        artwork()
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.72f),
                    0.55f to Color.Black.copy(alpha = 0.32f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.20f),
                    0.22f to Color.Transparent,
                    0.51f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.34f),
                ),
            ),
        )
    }
}

@Composable
internal fun persistentHeroTarget(item: MetaPreview): Modifier {
    val select = LocalPersistentHeroSelection.current ?: return Modifier
    val active = LocalScreenActive.current
    val keyboard = isRemoteKeyboardNavigationActive
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsRemoteHoveredAsState()
    val menuOpen = ActionMenuCoordinator.active != null
    val position = LocalPosterNavigationItem.current
    val selectCurrent by rememberUpdatedState {
        if (active && !menuOpen) select(PersistentHeroTarget(item, position?.row, position?.index))
    }
    LaunchedEffect(hovered, active, menuOpen) {
        if (!hovered || !active || menuOpen) return@LaunchedEffect
        delay(140)
        selectCurrent()
    }
    return Modifier
        .onFocusChanged { if (it.hasFocus) selectCurrent() }
        .hoverable(source, enabled = active && !keyboard)
}
