package com.nuvio.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.DisintegratingContainer
import com.nuvio.app.core.ui.DisintegrationRequest
import com.nuvio.app.core.ui.LocalDesktopSidebarContentInset
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.NuvioLazyVerticalGrid
import com.nuvio.app.core.ui.ScopedDisintegrationTracker
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.navigationGlassSource
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.remoteNavigationGroup
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.CatalogHeader
import com.nuvio.app.features.home.components.ContinueWatchingTile
import com.nuvio.app.features.home.components.HomeInteractiveGradientBackground
import com.nuvio.app.features.home.components.continueWatchingLandscapeCardWidth
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.features.home.components.rememberContinueWatchingLayout
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.tracking.WatchProgressSource
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.continueWatchingItemKey
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_playback_no_items_available
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ContinueWatchingScreen(
    title: String,
    profileId: Int,
    shuffleVisit: Long,
    onBack: () -> Unit,
    onItemClick: (ContinueWatchingItem) -> Unit,
    onItemLongPress: (ContinueWatchingItem) -> Unit,
    disintegrationRequest: DisintegrationRequest<String>?,
) {
    val addons by AddonRepository.uiState.collectAsStateWithLifecycle()
    val enabledAddons = remember(addons.addons) { addons.addons.enabledAddons() }
    val preferences by ContinueWatchingPreferencesRepository.uiState.collectAsStateWithLifecycle()
    val progress by WatchProgressRepository.uiState.collectAsStateWithLifecycle()
    val watched by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val network by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    val auraBackgroundEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    val (items, _) = rememberHomeContinueWatchingItems(
        activeProfileId = profileId,
        enabledAddons = enabledAddons,
        preferences = preferences,
        watchedUiState = watched,
        watchProgressUiState = progress,
        networkCondition = network.condition,
        shuffleVisit = shuffleVisit,
    )
    val posterStyle = rememberPosterCardStyleUiState()
    val sidebarInset = LocalDesktopSidebarContentInset.current

    key(profileId, progress.source) {
        val gridState = rememberLazyGridState()
        val disintegration = remember {
            ScopedDisintegrationTracker<WatchProgressSource, String, ContinueWatchingItem>(::continueWatchingItemKey)
        }
        val displayEntries = disintegration.sync(progress.source, items, disintegrationRequest)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentWidth = (maxWidth - sidebarInset).coerceAtLeast(0.dp)
            val pagePadding = homeSectionHorizontalPaddingForWidth(contentWidth.value)
            val layout = rememberContinueWatchingLayout(contentWidth.value, posterStyle)
            val tileWidth = when (preferences.style) {
                ContinueWatchingSectionStyle.Card ->
                    continueWatchingLandscapeCardWidth(desktopCatalogShelfPosterBaseWidthDp(posterStyle.widthDp))
                ContinueWatchingSectionStyle.Wide -> layout.wideCardWidth
                ContinueWatchingSectionStyle.Poster -> layout.posterCardWidth
            }

            if (auraBackgroundEnabled) {
                HomeInteractiveGradientBackground(modifier = Modifier.fillMaxSize(), gridState = gridState)
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .navigationGlassSource(zIndex = -1f)
                        .background(MaterialTheme.nuvio.colors.background),
                )
            }
            Column(Modifier.fillMaxSize()) {
                CatalogHeader(
                    title = title,
                    subtitle = "",
                    pageHorizontalPadding = pagePadding,
                    sidebarInset = sidebarInset,
                    onBack = onBack,
                )
                Box(Modifier.weight(1f).fillMaxSize()) {
                    if (displayEntries.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_playback_no_items_available),
                            modifier = Modifier.align(Alignment.Center).padding(start = sidebarInset),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        NuvioLazyVerticalGrid(
                            columns = GridCells.Adaptive(tileWidth),
                            state = gridState,
                            modifier = Modifier.fillMaxSize().remoteNavigationGroup(),
                            contentPadding = PaddingValues(
                                start = sidebarInset + pagePadding,
                                end = pagePadding,
                                top = layout.itemGap,
                                bottom = nuvioSafeBottomPadding(layout.itemGap),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(layout.itemGap),
                            verticalArrangement = Arrangement.spacedBy(layout.itemGap),
                        ) {
                            items(displayEntries, key = { it.key }) { entry ->
                                DisintegratingContainer(
                                    disintegrating = entry.exiting,
                                    onDisintegrated = { disintegration.onDisintegrated(entry.key) },
                                ) {
                                    ContinueWatchingTile(
                                        item = entry.item,
                                        style = preferences.style,
                                        layout = layout,
                                        useEpisodeThumbnails = preferences.useEpisodeThumbnails,
                                        blurNextUp = preferences.blurNextUp,
                                        onClick = if (entry.exiting) null else { { onItemClick(entry.item) } },
                                        onLongClick = if (entry.exiting) null else { { onItemLongPress(entry.item) } },
                                    )
                                }
                            }
                        }
                        NuvioDesktopVerticalScrollbar(
                            state = gridState,
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
