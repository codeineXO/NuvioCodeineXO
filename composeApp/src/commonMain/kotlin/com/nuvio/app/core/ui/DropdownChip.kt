package com.nuvio.app.core.ui


import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop
import kotlinx.coroutines.launch

data class NuvioDropdownOption(
    val key: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuvioDropdownChip(
    title: String,
    label: String,
    selectedKey: String?,
    options: List<NuvioDropdownOption>,
    enabled: Boolean = true,
    onSelected: (NuvioDropdownOption) -> Unit,
    modifier: Modifier = Modifier,
    useGlass: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    var isSheetVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .then(if (enabled) Modifier.remoteNavigationTarget(shape = tokens.shapes.compactCard) else Modifier)
                .then(
                    if (useGlass) {
                        Modifier.navigationGlass(shape = tokens.shapes.compactCard, backgroundOnly = true)
                            .navigationGlassFeedback(interactionSource, tokens.shapes.compactCard)
                    } else {
                        Modifier.clip(tokens.shapes.compactCard).background(tokens.colors.surface)
                    },
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    onClick = { isSheetVisible = true },
                )
                .padding(horizontal = NuvioTokens.Space.s12, vertical = tokens.components.chipVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) tokens.colors.textPrimary else tokens.colors.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
                tint = if (enabled) tokens.colors.textMuted else tokens.colors.borderDefault,
            )
        }

        if (isDesktop) {
            PlatformBackHandler(enabled = isSheetVisible, onBack = { isSheetVisible = false })
            DropdownMenu(
                expanded = isSheetVisible,
                onDismissRequest = { isSheetVisible = false },
                modifier = Modifier.widthIn(min = 180.dp, max = 360.dp)
                    .then(
                        if (useGlass) Modifier.navigationGlass(
                            shape = tokens.shapes.compactCard,
                            panel = true,
                            backgroundOnly = true,
                        ) else Modifier,
                    ),
                containerColor = if (useGlass) Color.Transparent else tokens.colors.surfacePopover,
                shape = tokens.shapes.compactCard,
                tonalElevation = if (useGlass) tokens.elevation.flat else tokens.elevation.raised,
                shadowElevation = if (useGlass) tokens.elevation.flat else tokens.elevation.overlay,
                border = if (useGlass) BorderStroke(tokens.borders.thin, tokens.colors.borderDefault) else null,
            ) {
                options.forEach { option ->
                    key(option.key) {
                        NuvioDropdownMenuOption(
                            option = option,
                            isSelected = option.key == selectedKey,
                            useGlass = useGlass,
                            onClick = {
                                onSelected(option)
                                isSheetVisible = false
                            },
                        )
                    }
                }
            }
        }
    }

    if (isSheetVisible && !isDesktop) {
        NuvioDropdownOptionsSheet(
            title = title,
            options = options,
            selectedKey = selectedKey,
            sheetState = sheetState,
            onDismiss = {
                coroutineScope.launch {
                    dismissNuvioBottomSheet(
                        sheetState = sheetState,
                        onDismiss = { isSheetVisible = false },
                    )
                }
            },
            onSelected = { option ->
                onSelected(option)
                coroutineScope.launch {
                    dismissNuvioBottomSheet(
                        sheetState = sheetState,
                        onDismiss = { isSheetVisible = false },
                    )
                }
            },
        )
    }
}

@Composable
private fun NuvioDropdownMenuOption(
    option: NuvioDropdownOption,
    isSelected: Boolean,
    useGlass: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val shape = tokens.shapes.compactCard
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // Keep the popup's native arrow/Tab navigation and draw its focus ring locally.
    // Page navigation targets would inherit the underlying page's focus boundaries.
    DropdownMenuItem(
        modifier = Modifier
            .padding(horizontal = NuvioTokens.Space.s8)
            .semantics { selected = isSelected }
            .then(if (focused) Modifier.border(2.dp, tokens.colors.focusRing, shape) else Modifier)
            .clip(shape)
            .background(
                if (isSelected) tokens.colors.accent.copy(alpha = tokens.opacity.selected)
                else Color.Transparent,
            )
            .then(if (useGlass) Modifier.navigationGlassFeedback(interactionSource, shape) else Modifier),
        interactionSource = interactionSource,
        text = {
            Text(
                text = option.label,
                color = tokens.colors.textPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(tokens.icons.md),
                )
            }
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuvioDropdownOptionsSheet(
    title: String,
    options: List<NuvioDropdownOption>,
    selectedKey: String?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelected: (NuvioDropdownOption) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = nuvioSafeBottomPadding(tokens.spacing.screenHorizontal)),
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal, vertical = NuvioTokens.Space.s14),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary,
            )
            NuvioBottomSheetDivider()
            LazyColumn(
                modifier = Modifier.remoteNavigationGroup().then(Modifier
                    .fillMaxWidth()
                    .heightIn(max = tokens.breakpoints.largePhone)),
            ) {
                items(options) { option ->
                    NuvioBottomSheetActionRow(
                        title = option.label,
                        onClick = { onSelected(option) },
                        selected = option.key == selectedKey,
                        trailingContent = {
                            if (option.key == selectedKey) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = tokens.colors.accent,
                                    modifier = Modifier.size(tokens.icons.md),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
