package com.nuvio.app.core.ui


import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import com.nuvio.app.core.ui.NuvioLazyColumn
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
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
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
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    keyboardNavigationEnabled: Boolean = true,
    wrapFilterLabels: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    var internalExpanded by remember { mutableStateOf(false) }
    val isSheetVisible = expanded ?: internalExpanded
    fun setExpanded(value: Boolean) {
        internalExpanded = value
        onExpandedChange?.invoke(value)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .then(if (wrapFilterLabels) Modifier.fillMaxWidth() else Modifier)
                .then(if (!keyboardNavigationEnabled) Modifier.excludeFromRemoteNavigation()
                    else if (enabled) Modifier.remoteNavigationTarget(shape = tokens.shapes.compactCard) else Modifier)
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
                    onClick = { setExpanded(true) },
                )
                .padding(horizontal = NuvioTokens.Space.s12, vertical = tokens.components.chipVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    !enabled -> tokens.colors.textDisabled
                    wrapFilterLabels && isSheetVisible -> tokens.colors.accent
                    else -> tokens.colors.textPrimary
                },
                modifier = if (wrapFilterLabels) Modifier.weight(1f) else Modifier,
                maxLines = if (wrapFilterLabels) Int.MAX_VALUE else 1,
                overflow = if (wrapFilterLabels) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
                tint = if (enabled) tokens.colors.textMuted else tokens.colors.borderDefault,
            )
        }

        if (isDesktop) {
            val scrollState = rememberScrollState()
            PlatformBackHandler(enabled = isSheetVisible, onBack = { setExpanded(false) })
            DropdownMenu(
                expanded = isSheetVisible,
                onDismissRequest = { setExpanded(false) },
                scrollState = if (wrapFilterLabels) rememberScrollState() else scrollState,
                modifier = Modifier.then(if (wrapFilterLabels) Modifier else Modifier.nuvioDesktopDragScroll(scrollState, Orientation.Vertical))
                    .widthIn(min = 180.dp, max = 360.dp)
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
                AppOverlayInput {
                    // Fix the viewport width before DropdownMenu asks for intrinsic sizes.
                    // Measuring the entire list intrinsically can overflow packed Constraints.
                    Box(Modifier.then(if (wrapFilterLabels) Modifier.then(FilterDropdownViewport)
                        .width(320.dp).heightIn(max = 360.dp) else Modifier)) {
                        Column(Modifier.then(if (wrapFilterLabels) Modifier.verticalScroll(scrollState)
                            .nuvioDesktopDragScroll(scrollState, Orientation.Vertical).padding(end = 12.dp) else Modifier)) {
                            options.forEach { option ->
                                key(option.key) {
                                    NuvioDropdownMenuOption(
                                        option = option,
                                        isSelected = option.key == selectedKey,
                                        useGlass = useGlass,
                                        wrapLabel = wrapFilterLabels,
                                        onClick = {
                                            onSelected(option)
                                            setExpanded(false)
                                        },
                                    )
                                }
                            }
                        }
                        // A fixed upper bound also keeps intrinsic measurement away from the
                        // desktop scrollbar's unbounded-height measure policy.
                        if (wrapFilterLabels && scrollState.maxValue > 0) Box(Modifier.matchParentSize()) {
                            NuvioDesktopVerticalScrollbar(scrollState,
                                Modifier.align(Alignment.CenterEnd).height(360.dp).padding(vertical = 4.dp))
                        }
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
                        onDismiss = { setExpanded(false) },
                    )
                }
            },
            onSelected = { option ->
                onSelected(option)
                coroutineScope.launch {
                    dismissNuvioBottomSheet(
                        sheetState = sheetState,
                        onDismiss = { setExpanded(false) },
                    )
                }
            },
        )
    }
}

/** Menu intrinsics describe the viewport, never the full height of the scrollable list. */
private object FilterDropdownViewport : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = 320.dp.roundToPx()
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = 320.dp.roundToPx()
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = 360.dp.roundToPx()
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = 360.dp.roundToPx()
}

@Composable
private fun NuvioDropdownMenuOption(
    option: NuvioDropdownOption,
    isSelected: Boolean,
    useGlass: Boolean,
    wrapLabel: Boolean = false,
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
                color = if (wrapLabel && isSelected) tokens.colors.accent else tokens.colors.textPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = if (wrapLabel) Int.MAX_VALUE else 1,
                overflow = if (wrapLabel) TextOverflow.Clip else TextOverflow.Ellipsis,
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
            NuvioLazyColumn(
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
