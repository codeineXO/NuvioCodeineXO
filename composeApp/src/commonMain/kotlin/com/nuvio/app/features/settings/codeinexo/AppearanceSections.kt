package com.nuvio.app.features.settings.codeinexo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.home.AppInterface
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.settings.AppInterfaceSettingsRow
import com.nuvio.app.features.settings.AuraCustomizationRow
import com.nuvio.app.features.settings.KeyboardNavigationSettingsRow
import com.nuvio.app.features.settings.MetaEpisodeCardStyleSelector
import com.nuvio.app.features.settings.MetadataInfoSettings
import com.nuvio.app.features.settings.NavigationFeelSettingsRow
import com.nuvio.app.features.settings.PosterGutterSizeSettingsRow
import com.nuvio.app.features.settings.SettingsDropdownRow
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import com.nuvio.app.features.settings.SurfaceStyleSettingsRow
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.settings.uiScaleDropdownOptions
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppearanceSection(isTablet: Boolean) {
    val auraBackgroundEnabled by remember {
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.auraBackgroundEnabled
    }.collectAsStateWithLifecycle()
    val surfaceStyle by ThemeSettingsRepository.surfaceStyle.collectAsStateWithLifecycle()
    val posterCardStyle by remember {
        PosterCardStyleRepository.ensureLoaded()
        PosterCardStyleRepository.uiState
    }.collectAsStateWithLifecycle()

    SettingsSection(title = stringResource(CodeineXOSection.Appearance.titleRes), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            if (isDesktop) {
                val appScale by ThemeSettingsRepository.appScale.collectAsStateWithLifecycle()
                SettingsDropdownRow(
                    title = stringResource(Res.string.settings_appearance_app_scale),
                    description = stringResource(Res.string.settings_appearance_app_scale_description),
                    selectedKey = appScale.toString(),
                    selectedLabel = "${appScale}%",
                    options = uiScaleDropdownOptions,
                    isTablet = isTablet,
                    onSelected = { option -> option.key.toIntOrNull()?.let(ThemeSettingsRepository::setAppScale) },
                )
                SettingsGroupDivider(isTablet = isTablet)
            }
            SurfaceStyleSettingsRow(
                selectedStyle = surfaceStyle,
                isTablet = isTablet,
                onSelected = ThemeSettingsRepository::setSurfaceStyle,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.xo_aura_title),
                description = stringResource(Res.string.xo_aura_description),
                checked = auraBackgroundEnabled,
                isTablet = isTablet,
                onCheckedChange = ThemeSettingsRepository::setAuraBackgroundEnabled,
            )
            SettingsGroupDivider(isTablet = isTablet)
            AuraCustomizationRow(isTablet = isTablet)
            if (isDesktop) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_poster_glow),
                    description = stringResource(Res.string.settings_poster_glow_description),
                    checked = posterCardStyle.glowEnabled,
                    isTablet = isTablet,
                    onCheckedChange = PosterCardStyleRepository::setGlowEnabled,
                )
            }
        }
    }
}

@Composable
internal fun NavigationSection(isTablet: Boolean) {
    SettingsSection(title = stringResource(CodeineXOSection.Navigation.titleRes), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            NavigationFeelSettingsRow(isTablet)
            SettingsGroupDivider(isTablet = isTablet)
            KeyboardNavigationSettingsRow(isTablet)
        }
    }
}

@Composable
internal fun HomePostersSection(isTablet: Boolean) {
    val homeCatalogSettings by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val posterCardStyle by remember {
        PosterCardStyleRepository.ensureLoaded()
        PosterCardStyleRepository.uiState
    }.collectAsStateWithLifecycle()

    SettingsSection(title = stringResource(CodeineXOSection.HomePosters.titleRes), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            AppInterfaceSettingsRow(value = homeCatalogSettings.appInterface, isTablet = isTablet)
            if (isDesktop && homeCatalogSettings.appInterface == AppInterface.ScrollPosters) {
                SettingsGroupDivider(isTablet = isTablet)
                PosterGutterSizeSettingsRow(size = homeCatalogSettings.posterGutterSize, isTablet = isTablet)
            }
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_poster_hide_labels),
                description = stringResource(Res.string.settings_poster_hide_labels_description),
                checked = posterCardStyle.hideLabelsEnabled,
                isTablet = isTablet,
                onCheckedChange = PosterCardStyleRepository::setHideLabelsEnabled,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_poster_hide_year),
                description = stringResource(Res.string.settings_poster_hide_year_description),
                checked = posterCardStyle.hideYearEnabled,
                isTablet = isTablet,
                onCheckedChange = PosterCardStyleRepository::setHideYearEnabled,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_homescreen_always_animate_collections),
                description = stringResource(Res.string.settings_homescreen_always_animate_collections_description),
                checked = homeCatalogSettings.alwaysAnimateCollectionCards,
                isTablet = isTablet,
                onCheckedChange = HomeCatalogSettingsRepository::setAlwaysAnimateCollectionCards,
            )
        }
    }
}

@Composable
internal fun DetailsPageSection(isTablet: Boolean) {
    val metaScreenSettings by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    SettingsSection(title = stringResource(CodeineXOSection.DetailsPage.titleRes), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            MetaEpisodeCardStyleSelector(
                isTablet = isTablet,
                selectedStyle = metaScreenSettings.episodeCardStyle,
                onStyleSelected = MetaScreenSettingsRepository::setEpisodeCardStyle,
            )
            SettingsGroupDivider(isTablet = isTablet)
            MetadataInfoSettings(isTablet = isTablet, checked = metaScreenSettings.showMetadataInfo)
        }
    }
}
