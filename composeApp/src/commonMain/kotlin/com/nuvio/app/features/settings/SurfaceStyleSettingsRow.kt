package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.remoteNavigationTarget
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
    val tokens = MaterialTheme.nuvio
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val verticalPadding = if (isTablet) 16.dp else 14.dp
    val title = stringResource(Res.string.settings_surface_style)
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            // Match adjacent settings rows so vertical focus search cannot skip the trailing chip.
            .remoteNavigationTarget(shape = tokens.shapes.card)
            .clickable { expanded = true }
            .padding(
                horizontal = (horizontalPadding - 6.dp).coerceAtLeast(0.dp),
                vertical = (verticalPadding - 2.dp).coerceAtLeast(0.dp),
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .widthIn(max = if (isTablet) 560.dp else Dp.Unspecified),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(Res.string.settings_surface_style_description),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )
        }
        NuvioDropdownChip(
            title = title,
            label = stringResource(selectedStyle.labelRes),
            selectedKey = selectedStyle.key,
            options = SurfaceStyle.entries.map { NuvioDropdownOption(it.key, stringResource(it.labelRes)) },
            onSelected = { option -> onSelected(SurfaceStyle.entries.first { it.key == option.key }) },
            useGlass = true,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            keyboardNavigationEnabled = false,
        )
    }
}
