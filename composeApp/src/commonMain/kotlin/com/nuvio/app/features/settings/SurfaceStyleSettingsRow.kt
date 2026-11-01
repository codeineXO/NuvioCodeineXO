package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_surface_style
import nuvio.composeapp.generated.resources.settings_surface_style_description
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SurfaceStyleSettingsRow(
    selectedStyle: SurfaceStyle,
    isTablet: Boolean,
    onSelected: (SurfaceStyle) -> Unit,
) {
    val title = stringResource(Res.string.settings_surface_style)
    SettingsChipRow(
        title = title,
        description = stringResource(Res.string.settings_surface_style_description),
        isTablet = isTablet,
        chips = {
            NuvioDropdownChip(
                title = title,
                label = stringResource(selectedStyle.labelRes),
                selectedKey = selectedStyle.key,
                options = SurfaceStyle.entries.map { NuvioDropdownOption(it.key, stringResource(it.labelRes)) },
                onSelected = { option -> onSelected(SurfaceStyle.entries.first { it.key == option.key }) },
                useGlass = true,
            )
        },
    )
}
