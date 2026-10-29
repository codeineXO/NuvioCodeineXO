package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MetadataInfoSettings(isTablet: Boolean, checked: Boolean) {
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_show_metadata_info),
        description = stringResource(Res.string.settings_show_metadata_info_description),
        checked = checked,
        isTablet = isTablet,
        onCheckedChange = MetaScreenSettingsRepository::setShowMetadataInfo,
    )
}
