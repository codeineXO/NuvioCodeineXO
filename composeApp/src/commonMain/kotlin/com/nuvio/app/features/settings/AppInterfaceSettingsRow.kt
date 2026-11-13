package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.features.home.AppInterface
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_app_interface
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppInterfaceSettingsRow(
    value: AppInterface,
    isTablet: Boolean,
    onSelected: (AppInterface) -> Unit = HomeCatalogSettingsRepository::setAppInterface,
) {
    SettingsDropdownRow(
        title = stringResource(Res.string.settings_app_interface),
        selectedKey = value.key,
        selectedLabel = stringResource(value.label),
        options = AppInterface.entries.map { NuvioDropdownOption(it.key, stringResource(it.label)) },
        isTablet = isTablet,
        onSelected = { option -> onSelected(AppInterface.entries.first { it.key == option.key }) },
    )
}
