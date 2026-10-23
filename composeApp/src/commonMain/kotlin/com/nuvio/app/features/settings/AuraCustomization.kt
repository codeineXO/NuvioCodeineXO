package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.home.components.HomeInteractiveGradientBackground
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val AuraPalette.label: StringResource get() = when (this) {
    AuraPalette.ORIGINAL -> Res.string.aura_original
    AuraPalette.ARCTIC -> Res.string.aura_arctic
    AuraPalette.NORTHERN_LIGHTS -> Res.string.aura_northern_lights
    AuraPalette.SUNSET -> Res.string.aura_sunset
    AuraPalette.ROSE_QUARTZ -> Res.string.aura_rose_quartz
    AuraPalette.GOLDEN_HOUR -> Res.string.aura_golden_hour
    AuraPalette.DEEP_SPACE -> Res.string.aura_deep_space
    AuraPalette.MATCH_THEME -> Res.string.aura_match_theme
    AuraPalette.CUSTOM -> Res.string.aura_custom
}
private val AuraIntensity.label: StringResource get() = when (this) {
    AuraIntensity.SUBTLE -> Res.string.aura_subtle
    AuraIntensity.BALANCED -> Res.string.aura_balanced
    AuraIntensity.VIVID -> Res.string.aura_vivid
}

@Composable
internal fun AuraCustomizationRow(isTablet: Boolean) {
    val settings by ThemeSettingsRepository.auraSettings.collectAsStateWithLifecycle()
    val enabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val profileId = profileState.activeProfile?.profileIndex
    var showSheet by rememberSaveable(profileId) { mutableStateOf(false) }
    SettingsNavigationRow(
        title = stringResource(Res.string.aura_customize),
        description = stringResource(if (enabled) settings.palette.label else Res.string.aura_enable_hint),
        isTablet = isTablet,
        onClick = { showSheet = true },
    )
    if (showSheet) key(profileId) { AuraCustomizationSheet(
        settings = settings,
        enabled = enabled,
        onChange = ThemeSettingsRepository::setAuraSettings,
        onDismiss = { showSheet = false },
    ) }
}

@Composable
private fun AuraPreview(settings: AuraSettings) {
    Box(Modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.nuvio.shapes.compactCard)) {
        CompositionLocalProvider(LocalNavigationGlassState provides null) {
            HomeInteractiveGradientBackground(settings = settings)
        }
        Text(stringResource(Res.string.aura_preview), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AuraCustomizationSheet(
    settings: AuraSettings,
    enabled: Boolean,
    onChange: (AuraSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingCustom by rememberSaveable { mutableStateOf(false) }
    if (editingCustom && enabled) {
        CustomThemeEditor(
            initialColors = settings.customColors,
            allowGradient = true,
            forceThreeColors = true,
            title = stringResource(Res.string.aura_custom_title),
            subtitle = stringResource(Res.string.aura_custom_subtitle),
            saveLabel = stringResource(Res.string.aura_custom_save),
            preview = { AuraPreview(settings.copy(palette = AuraPalette.CUSTOM, customColors = it)) },
            onSave = { onChange(settings.copy(palette = AuraPalette.CUSTOM, customColors = it)) },
            onDismiss = { editingCustom = false },
        )
        return
    }
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.aura_customize), style = MaterialTheme.typography.titleLarge)
            if (!enabled) Text(stringResource(Res.string.aura_enable_hint))
            AuraPreview(if (enabled) settings else settings.copy(animated = false))
            Text(stringResource(Res.string.aura_palette), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuraPalette.entries.forEach { palette ->
                    val colors = resolveAuraPalette(settings.copy(palette = palette), MaterialTheme.themePalette).map { it.inner }
                    FilterChip(
                        selected = settings.palette == palette,
                        enabled = enabled,
                        onClick = { if (palette == AuraPalette.CUSTOM) editingCustom = true else onChange(settings.copy(palette = palette)) },
                        label = { Text(stringResource(palette.label)) },
                        leadingIcon = { Box(Modifier.size(20.dp).clip(MaterialTheme.shapes.small).background(Brush.linearGradient(colors))) },
                        modifier = Modifier.remoteNavigationTarget(),
                    )
                }
            }
            Text(stringResource(Res.string.aura_intensity), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuraIntensity.entries.forEach { intensity ->
                    FilterChip(
                        selected = settings.intensity == intensity,
                        enabled = enabled,
                        onClick = { onChange(settings.copy(intensity = intensity)) },
                        label = { Text(stringResource(intensity.label)) },
                        modifier = Modifier.remoteNavigationTarget(),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.aura_animation))
                    Text(stringResource(Res.string.aura_animation_description), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.animated, enabled = enabled, onCheckedChange = { onChange(settings.copy(animated = it)) })
            }
        }
    }
}
