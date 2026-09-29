package com.nuvio.app.features.settings

import com.nuvio.app.core.build.AppFeaturePolicy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.firstEnabledManifestError
import com.nuvio.app.features.addons.hasPendingEnabledManifests
import com.nuvio.app.features.addons.isWaitingForFirstEnabledManifest
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.buildAddonCatalogRefreshSignature
import com.nuvio.app.features.home.components.HomeInteractiveGradientBackground
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.compose_settings_page_addons
import nuvio.composeapp.generated.resources.compose_settings_page_continue_watching
import nuvio.composeapp.generated.resources.compose_settings_page_homescreen
import nuvio.composeapp.generated.resources.compose_settings_page_meta_screen
import nuvio.composeapp.generated.resources.compose_settings_page_plugins
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomescreenSettingsScreen(
    onBack: () -> Unit,
) {
    val auraBackgroundEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val homescreenCatalogRefreshKey = remember(addonsUiState.addons) {
        buildAddonCatalogRefreshSignature(addonsUiState.addons)
    }
    val addonManifestsLoading = addonsUiState.addons.hasPendingEnabledManifests()
    val addonManifestErrorMessage = addonsUiState.addons.firstEnabledManifestError()
    val homescreenSettingsUiState by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val collections by CollectionRepository.collections.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        CollectionRepository.initialize()
    }

    LaunchedEffect(homescreenCatalogRefreshKey) {
        val enabledAddons = addonsUiState.addons.enabledAddons()
        if (!enabledAddons.isWaitingForFirstEnabledManifest()) {
            HomeCatalogSettingsRepository.syncCatalogs(enabledAddons)
        }
    }

    LaunchedEffect(collections) {
        HomeCatalogSettingsRepository.syncCollections(collections)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (auraBackgroundEnabled) {
            HomeInteractiveGradientBackground(modifier = Modifier.fillMaxSize())
        }
        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            backgroundColor = if (auraBackgroundEnabled) Color.Transparent else MaterialTheme.nuvio.colors.background,
        ) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.compose_settings_page_homescreen),
                    onBack = onBack,
                )
            }
            homescreenSettingsContent(
                isTablet = false,
                heroEnabled = homescreenSettingsUiState.heroEnabled,
                showCatalogType = homescreenSettingsUiState.showCatalogType,
                hideUnreleasedContent = homescreenSettingsUiState.hideUnreleasedContent,
                items = homescreenSettingsUiState.items,
                isCatalogLoading = addonManifestsLoading,
                catalogErrorMessage = addonManifestErrorMessage,
            )
        }
    }
}

@Composable
fun MetaScreenSettingsScreen(
    onBack: () -> Unit,
) {
    val auraBackgroundEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    val metaScreenSettingsUiState by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (auraBackgroundEnabled) {
            HomeInteractiveGradientBackground(modifier = Modifier.fillMaxSize())
        }
        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            backgroundColor = if (auraBackgroundEnabled) Color.Transparent else MaterialTheme.nuvio.colors.background,
        ) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.compose_settings_page_meta_screen),
                    onBack = onBack,
                )
            }
            metaScreenSettingsContent(
                isTablet = false,
                uiState = metaScreenSettingsUiState,
            )
        }
    }
}

@Composable
fun ContinueWatchingSettingsScreen(
    onBack: () -> Unit,
) {
    val auraBackgroundEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    val continueWatchingPreferencesUiState by remember {
        ContinueWatchingPreferencesRepository.ensureLoaded()
        ContinueWatchingPreferencesRepository.uiState
    }.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (auraBackgroundEnabled) {
            HomeInteractiveGradientBackground(modifier = Modifier.fillMaxSize())
        }
        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            backgroundColor = if (auraBackgroundEnabled) Color.Transparent else MaterialTheme.nuvio.colors.background,
        ) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.compose_settings_page_continue_watching),
                    onBack = onBack,
                )
            }
            continueWatchingSettingsContent(
                isTablet = false,
                isVisible = continueWatchingPreferencesUiState.isVisible,
                style = continueWatchingPreferencesUiState.style,
                upNextFromFurthestEpisode = continueWatchingPreferencesUiState.upNextFromFurthestEpisode,
                useEpisodeThumbnails = continueWatchingPreferencesUiState.useEpisodeThumbnails,
                showUnairedNextUp = continueWatchingPreferencesUiState.showUnairedNextUp,
                blurNextUp = continueWatchingPreferencesUiState.blurNextUp,
                showResumePromptOnLaunch = continueWatchingPreferencesUiState.showResumePromptOnLaunch,
                sortMode = continueWatchingPreferencesUiState.sortMode,
            )
        }
    }
}

@Composable
fun AddonsSettingsScreen(
    onBack: () -> Unit,
) {
    val auraBackgroundEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        AddonRepository.initialize()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (auraBackgroundEnabled) {
            HomeInteractiveGradientBackground(modifier = Modifier.fillMaxSize())
        }
        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            backgroundColor = if (auraBackgroundEnabled) Color.Transparent else MaterialTheme.nuvio.colors.background,
        ) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.compose_settings_page_addons),
                    onBack = onBack,
                )
            }
            addonsSettingsContent()
        }
    }
}

@Composable
fun PluginsSettingsScreen(
    onBack: () -> Unit,
) {
    if (!AppFeaturePolicy.pluginsEnabled) {
        AddonsSettingsScreen(onBack = onBack)
        return
    }

    val auraBackgroundEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        PluginRepository.initialize()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (auraBackgroundEnabled) {
            HomeInteractiveGradientBackground(modifier = Modifier.fillMaxSize())
        }
        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            backgroundColor = if (auraBackgroundEnabled) Color.Transparent else MaterialTheme.nuvio.colors.background,
        ) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.compose_settings_page_plugins),
                    onBack = onBack,
                )
            }
            pluginsSettingsContent()
        }
    }
}

@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
) {
    val auraBackgroundEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        if (auraBackgroundEnabled) {
            HomeInteractiveGradientBackground(modifier = Modifier.fillMaxSize())
        }
        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            backgroundColor = if (auraBackgroundEnabled) Color.Transparent else MaterialTheme.nuvio.colors.background,
        ) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.compose_settings_page_account),
                    onBack = onBack,
                )
            }
            accountSettingsContent(
                isTablet = false,
            )
        }
    }
}

