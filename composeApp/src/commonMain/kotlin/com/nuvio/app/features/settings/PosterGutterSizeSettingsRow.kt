package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.PosterGutterSize
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PosterGutterSizeSettingsRow(size: PosterGutterSize, isTablet: Boolean) {
    val options = listOf(
        NuvioDropdownOption(PosterGutterSize.Default.key, stringResource(Res.string.settings_poster_gutter_size_default)),
        NuvioDropdownOption(PosterGutterSize.Narrow.key, stringResource(Res.string.settings_poster_gutter_size_narrow)),
        NuvioDropdownOption(PosterGutterSize.Wide.key, stringResource(Res.string.settings_poster_gutter_size_wide)),
    )
    SettingsDropdownRow(
        title = stringResource(Res.string.settings_poster_gutter_size),
        description = stringResource(Res.string.settings_poster_gutter_size_description),
        selectedKey = size.key,
        selectedLabel = options.first { it.key == size.key }.label,
        options = options,
        isTablet = isTablet,
        onSelected = { HomeCatalogSettingsRepository.setPosterGutterSize(PosterGutterSize.fromKey(it.key)) },
    )
}
