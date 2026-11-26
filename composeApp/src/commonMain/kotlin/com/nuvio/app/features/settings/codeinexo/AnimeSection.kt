package com.nuvio.app.features.settings.codeinexo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.anime.AnimeFillerSettingsRepository
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AnimeSection(isTablet: Boolean) {
    val animeFillerEnabled by remember {
        AnimeFillerSettingsRepository.ensureLoaded()
        AnimeFillerSettingsRepository.enabled
    }.collectAsStateWithLifecycle()
    val animeHideFillers by remember {
        AnimeFillerSettingsRepository.ensureLoaded()
        AnimeFillerSettingsRepository.hideFillers
    }.collectAsStateWithLifecycle()

    SettingsSection(title = stringResource(CodeineXOSection.Anime.titleRes), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.xo_anime_filler_detection_title),
                description = stringResource(Res.string.xo_anime_filler_detection_description),
                checked = animeFillerEnabled,
                isTablet = isTablet,
                onCheckedChange = AnimeFillerSettingsRepository::setEnabled,
            )
            if (animeFillerEnabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.xo_hide_anime_filler_title),
                    description = stringResource(Res.string.xo_hide_anime_filler_description),
                    checked = animeHideFillers,
                    isTablet = isTablet,
                    onCheckedChange = AnimeFillerSettingsRepository::setHideFillers,
                )
            }
        }
    }
}
