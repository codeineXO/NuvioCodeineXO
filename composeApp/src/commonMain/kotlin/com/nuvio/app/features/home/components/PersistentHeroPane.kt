package com.nuvio.app.features.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.nuvio.app.core.ui.ActionMenuCoordinator
import com.nuvio.app.core.ui.DesktopBackdropVerticalBias
import com.nuvio.app.core.ui.LocalSnapVerticalRows
import com.nuvio.app.core.ui.LocalVerticalWheelOwner
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.fullscreenActionHorizontalInsetForWidth
import com.nuvio.app.core.ui.nuvioVerticalMouseWheelScroll
import com.nuvio.app.core.ui.navigationGlassSource
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.collectIsRemoteHoveredAsState
import com.nuvio.app.core.ui.isRemoteKeyboardNavigationActive
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.home.HomeHeroMeta
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.fetchHomeHeroDetails
import com.nuvio.app.features.home.fetchHomeHeroRatings
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.home.withHeroMeta
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.isDesktop
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal val LocalPersistentHeroSelection = staticCompositionLocalOf<((MetaPreview) -> Unit)?> { null }

internal class PersistentHeroSelection<T> {
    class Request(val item: MetaPreview)

    var requested by mutableStateOf<Request?>(null)
        private set
    var displayed by mutableStateOf<T?>(null)
        private set

    fun select(item: MetaPreview) {
        if (requested?.item?.stableKey() != item.stableKey()) requested = Request(item)
    }

    fun publish(request: Request, value: T) {
        if (requested === request) displayed = value
    }

    fun clear() {
        requested = null
        displayed = null
    }
}

private data class LoadedHero(val item: MetaPreview, val backdrop: Painter?, val logo: Painter?)

@Composable
internal fun PersistentHeroPane(
    enabled: Boolean,
    page: Any,
    seed: MetaPreview?,
    loading: Boolean,
    rowScrollState: ScrollableState,
    rowAnchors: () -> List<Int>,
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
    val selection = remember(page, profile.activeProfile?.profileIndex, mdbList, tmdbSettings) { PersistentHeroSelection<LoadedHero>() }
    val active = LocalScreenActive.current
    val submit = remember(selection, active) {
        { item: MetaPreview -> if (active) selection.select(item) }
    }
    LaunchedEffect(active, selection, seed) {
        if (!active) selection.clear()
        else if (selection.requested == null && seed != null) selection.select(seed)
    }

    val currentRowAnchors by rememberUpdatedState(rowAnchors)
    val anchors = remember { { currentRowAnchors() } }
    BoxWithConstraints(modifier.fillMaxSize().nuvioVerticalMouseWheelScroll(rowScrollState, rowAnchors = anchors)) {
        val heroHeight = persistentHeroHeight(maxHeight, topInset, hasHeader = header != null)
        val railEndInset = fullscreenActionHorizontalInsetForWidth(maxWidth.value)
        val context = LocalPlatformContext.current
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val showMdbListRatings = mdbList.isActive && mdbList.showOnHero
        val request = selection.requested
        LaunchedEffect(active, selection, request, context, widthPx, heightPx) {
            if (!active || request == null) return@LaunchedEffect
            val preview = request.item
            val hero = coroutineScope {
                val details = async { withTimeoutOrNull(2_500) { fetchHomeHeroDetails(preview) } }
                val ratings = async {
                    if (showMdbListRatings) {
                        withTimeoutOrNull(2_500) { fetchHomeHeroRatings(preview, mdbList) { details.await() } }.orEmpty()
                    } else emptyList()
                }
                val meta = details.await()
                val item = preview.withHeroMeta(HomeHeroMeta(meta)).copy(
                    banner = preview.banner.nonBlank() ?: meta?.background.nonBlank() ?: preview.landscapePoster.nonBlank(),
                    poster = preview.poster.nonBlank() ?: meta?.poster.nonBlank(),
                    logo = preview.logo.nonBlank() ?: meta?.logo.nonBlank(),
                    description = preview.description.nonBlank() ?: meta?.description.nonBlank(),
                )
                suspend fun artwork(url: String?, width: Int, height: Int): Painter? {
                    if (url == null) return null
                    val imageRequest = ImageRequest.Builder(context).data(url).size(width, height).build()
                    return withTimeoutOrNull(1_750) {
                        (SingletonImageLoader.get(context).execute(imageRequest) as? SuccessResult)
                            ?.image?.asPainter(context)
                    }
                }
                val backdrop = async {
                    artwork(item.banner, widthPx, heightPx)
                        ?: artwork(item.poster?.takeUnless { it == item.banner }, widthPx, heightPx)
                        ?: artwork(item.rawPosterUrl?.takeUnless { it == item.poster || it == item.banner }, widthPx, heightPx)
                }
                val logo = async { artwork(item.logo, (widthPx / 2).coerceAtLeast(1), heightPx) }
                val loadedLogo = logo.await()
                LoadedHero(
                    item.copy(
                        logo = item.logo.takeIf { loadedLogo != null },
                        externalRatings = ratings.await().ifEmpty { preview.externalRatings },
                    ),
                    backdrop.await(),
                    loadedLogo,
                )
            }
            selection.publish(request, hero)
        }

        val hero = selection.displayed
        PersistentHeroBackdrop(hero?.backdrop, backdropModifier)
        Column(Modifier.fillMaxSize().padding(top = topInset)) {
            header?.invoke()
            val showSkeleton = loading || request != null || seed != null
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                Crossfade(
                    targetState = hero,
                    modifier = Modifier.fillMaxSize().padding(start = startInset),
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    label = "Persistent hero details",
                ) { displayed ->
                    if (displayed != null) {
                        PersistentHomeHero(
                            item = displayed.item,
                            logo = displayed.logo,
                            showImdbRating = metaSettings.showOverallRatings,
                            showMdbListRatings = showMdbListRatings,
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

internal fun persistentHeroHeight(viewportHeight: Dp, topInset: Dp, hasHeader: Boolean = false): Dp {
    val fraction = if (hasHeader) 0.44f else 0.56f
    return minOf(440.dp, (viewportHeight - topInset).coerceAtLeast(0.dp) * fraction)
}

@Composable
private fun PersistentHeroBackdrop(artwork: Painter?, modifier: Modifier) {
    val background = MaterialTheme.nuvio.colors.background
    Box(
        modifier.fillMaxSize()
            .navigationGlassSource(zIndex = -1f)
            .background(background),
    ) {
        AnimatedContent(
            targetState = artwork,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(if (initialState == null) 320 else 240, easing = FastOutSlowInEasing)) togetherWith
                    ExitTransition.KeepUntilTransitionsFinished).apply { targetContentZIndex = 1f }
            },
            label = "Persistent hero artwork",
        ) { painter ->
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, DesktopBackdropVerticalBias),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(background))
            }
        }
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

private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

@Composable
internal fun persistentHeroTarget(item: MetaPreview): Modifier {
    val select = LocalPersistentHeroSelection.current ?: return Modifier
    val active = LocalScreenActive.current
    val keyboard = isRemoteKeyboardNavigationActive
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsRemoteHoveredAsState()
    val menuOpen = ActionMenuCoordinator.active != null
    val selectCurrent by rememberUpdatedState {
        if (active && !menuOpen) select(item)
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
