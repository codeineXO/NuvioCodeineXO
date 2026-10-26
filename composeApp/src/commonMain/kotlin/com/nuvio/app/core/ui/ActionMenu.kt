package com.nuvio.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A fresh instance identifies each opening, including repeated openings on the same card. */
internal class ActionMenuInvocation(
    val cardBounds: Rect? = null,
    val pointerPosition: Offset? = null,
    val sourceAvailable: () -> Boolean = { true },
    val restoreFocus: (() -> Unit)? = null,
)

/** Lets a temporary hover popup refer back to its persistent shelf card. */
internal class ActionMenuSource {
    var capture: (() -> ActionMenuInvocation)? = null
}

internal val LocalActionMenuSource = staticCompositionLocalOf<ActionMenuSource?> { null }

internal object ActionMenuInvocationHolder {
    private var pending: ActionMenuInvocation? = null

    fun stash(invocation: ActionMenuInvocation) { pending = invocation }

    fun consume(): ActionMenuInvocation = (pending ?: ActionMenuInvocation()).also {
        pending = null
        ActionMenuCoordinator.pendingRestore = null
        ActionMenuCoordinator.panelBounds = null
        ActionMenuCoordinator.active = it
    }
}

/** Also suppresses hover previews while an action menu owns focus. */
internal object ActionMenuCoordinator {
    var active by mutableStateOf<ActionMenuInvocation?>(null)
    var panelBounds: Rect? = null
    var dismiss: ((restoreFocus: Boolean) -> Unit)? = null
    var pendingRestore: (() -> Unit)? = null
}

internal class ActionMenuAction(
    val icon: ImageVector,
    val label: String,
    val isDestructive: Boolean = false,
    val onSelected: () -> Unit,
)

/** Observe on the page ancestor, rather than placing a hit-blocking scrim above cards. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.actionMenuInput(): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(ActionMenuCoordinator.active) {
        if (ActionMenuCoordinator.active == null) {
            withFrameNanos { }
            if (ActionMenuCoordinator.active == null) {
                val restore = ActionMenuCoordinator.pendingRestore
                ActionMenuCoordinator.pendingRestore = null
                runCatching { restore?.invoke() }
            }
        }
    }
    return onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (ActionMenuCoordinator.active == null) continue
                val position = event.changes.firstOrNull()?.position?.plus(origin) ?: continue
                if (ActionMenuCoordinator.panelBounds?.contains(position) == true) continue
                if (event.type == PointerEventType.Scroll) {
                    ActionMenuCoordinator.dismiss?.invoke(false)
                } else if (event.type == PointerEventType.Press) {
                    ActionMenuCoordinator.dismiss?.invoke(true)
                    // Secondary presses must reach the new card in this same event dispatch.
                    if (event.button != PointerButton.Secondary) event.changes.forEach { it.consume() }
                }
            }
        }
    }
}

/** Used by hover cards, whose popup can disappear as soon as the menu opens. */
@Composable
internal fun Modifier.actionMenuSecondaryClick(onClick: (() -> Unit)?, source: ActionMenuSource? = null): Modifier {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    return onGloballyPositioned { bounds = it.boundsInRoot() }
        .secondaryClickAt(onClick?.let { callback -> { local ->
            val original = source?.capture?.invoke()
            ActionMenuInvocationHolder.stash(ActionMenuInvocation(
                cardBounds = original?.cardBounds ?: bounds,
                pointerPosition = bounds?.topLeft?.plus(local),
                sourceAvailable = original?.sourceAvailable ?: { true },
                restoreFocus = original?.restoreFocus,
            ))
            callback()
        } })
}

@Composable
internal fun NuvioActionMenu(
    invocation: ActionMenuInvocation,
    actions: List<ActionMenuAction>,
    hazeState: HazeState,
    onDismissed: () -> Unit,
) {
    key(invocation) {
        val frozenActions = remember { actions }
        val dismissOwner by rememberUpdatedState(onDismissed)
        val alpha = remember { Animatable(0f) }
        var closing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val firstAction = remember { FocusRequester() }
        val tokens = MaterialTheme.nuvio
        val shape = RoundedCornerShape(NuvioTokens.Radius.md)
        var rootOrigin by remember { mutableStateOf(Offset.Zero) }

        fun close(action: (() -> Unit)? = null, restoreFocus: Boolean = true) {
            if (closing) return
            closing = true
            scope.launch {
                alpha.animateTo(0f, tween(120))
                if (ActionMenuCoordinator.active === invocation) {
                    // Restoring an offscreen focus target would undo the user's wheel scroll.
                    ActionMenuCoordinator.pendingRestore = invocation.restoreFocus.takeIf { restoreFocus }
                    ActionMenuCoordinator.panelBounds = null
                    ActionMenuCoordinator.dismiss = null
                    ActionMenuCoordinator.active = null
                }
                dismissOwner()
            }
            action?.invoke()
        }

        DisposableEffect(invocation) {
            ActionMenuCoordinator.active = invocation
            ActionMenuCoordinator.dismiss = { restoreFocus -> close(restoreFocus = restoreFocus) }
            onDispose {
                if (ActionMenuCoordinator.active === invocation) {
                    ActionMenuCoordinator.active = null
                    ActionMenuCoordinator.panelBounds = null
                    ActionMenuCoordinator.dismiss = null
                }
            }
        }
        LaunchedEffect(invocation) {
            withFrameNanos { }
            if (closing) return@LaunchedEffect
            firstAction.requestFocus()
            alpha.animateTo(1f, tween(120))
        }
        LaunchedEffect(ActionMenuCoordinator.active) {
            if (ActionMenuCoordinator.active !== invocation) dismissOwner()
        }
        LaunchedEffect(invocation.sourceAvailable()) {
            if (!invocation.sourceAvailable()) close()
        }
        PlatformBackHandler(enabled = true) { close() }

        BoxWithConstraints(
            Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.positionInRoot() },
        ) {
            val density = LocalDensity.current
            val margin = with(density) { 8.dp.roundToPx() }
            val availableWidth = (maxWidth - 16.dp).coerceAtLeast(1.dp)
            val availableHeight = (maxHeight - 16.dp).coerceAtLeast(1.dp)
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    AppOverlayInput {
                        Column(
                            Modifier
                                .widthIn(min = minOf(244.dp, availableWidth), max = minOf(320.dp, availableWidth))
                                .width(IntrinsicSize.Max)
                                .heightIn(max = availableHeight)
                                .graphicsLayer { this.alpha = alpha.value }
                                .navigationGlass(shape = shape, panel = true, hazeState = hazeState)
                                .border(tokens.borders.hairline, tokens.colors.borderSubtle, shape)
                                .remoteNavigationModal()
                                .onPreviewKeyEvent {
                                    if (it.key == Key.Escape || it.key == Key.Back) {
                                        if (it.type == KeyEventType.KeyDown) close()
                                        true
                                    } else false
                                }
                                .onGloballyPositioned {
                                    if (ActionMenuCoordinator.active === invocation) {
                                        ActionMenuCoordinator.panelBounds = it.boundsInRoot()
                                    }
                                }
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                        ) {
                            frozenActions.forEachIndexed { index, action ->
                                if (index > 0) Box(Modifier.fillMaxWidth().height(tokens.borders.hairline)
                                    .background(tokens.colors.borderSubtle))
                                ActionMenuRow(
                                    action = action,
                                    shape = actionMenuRowShape(index, frozenActions.size),
                                    enabled = !closing,
                                    onSelected = { close(action.onSelected) },
                                    modifier = if (index == 0) Modifier.focusRequester(firstAction) else Modifier,
                                )
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                val panel = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
                val bounds = invocation.cardBounds?.translate(-rootOrigin)
                val pointer = invocation.pointerPosition?.minus(rootOrigin)
                val position = actionMenuPosition(
                    IntSize(constraints.maxWidth, constraints.maxHeight),
                    IntSize(panel.width, panel.height), margin, pointer, bounds,
                )
                layout(constraints.maxWidth, constraints.maxHeight) { panel.place(position) }
            }
        }
    }
}

internal fun actionMenuRowShape(index: Int, count: Int): Shape = RoundedCornerShape(
    topStart = if (index == 0) NuvioTokens.Radius.md else 0.dp,
    topEnd = if (index == 0) NuvioTokens.Radius.md else 0.dp,
    bottomStart = if (index == count - 1) NuvioTokens.Radius.md else 0.dp,
    bottomEnd = if (index == count - 1) NuvioTokens.Radius.md else 0.dp,
)

internal fun actionMenuPosition(
    window: IntSize, panel: IntSize, margin: Int, pointer: Offset?, card: Rect?,
): IntOffset {
    fun clamp(value: Float, extent: Int, viewport: Int) = value.roundToInt()
        .coerceIn(margin, (viewport - extent - margin).coerceAtLeast(margin))
    val x = when {
        pointer != null -> if (pointer.x + panel.width <= window.width - margin) pointer.x else pointer.x - panel.width
        card != null -> if (card.right + margin + panel.width <= window.width - margin) card.right + margin
            else card.left - margin - panel.width
        else -> (window.width - panel.width) / 2f
    }
    val y = when {
        pointer != null -> if (pointer.y + panel.height <= window.height - margin) pointer.y else pointer.y - panel.height
        card != null -> card.top
        else -> (window.height - panel.height) / 2f
    }
    return IntOffset(clamp(x, panel.width, window.width), clamp(y, panel.height, window.height))
}

@Composable
internal fun ActionMenuRow(
    action: ActionMenuAction,
    shape: Shape,
    enabled: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val color = if (action.isDestructive) tokens.colors.danger else tokens.colors.textPrimary
    Row(
        modifier.fillMaxWidth()
            .hoverable(interaction)
            .navigationGlassFeedback(interaction, shape, showFocus = isRemoteKeyboardNavigationActive)
            .remoteNavigationTarget(shape = shape, insetFocusOutline = true)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onSelected)
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(action.label, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
    }
}
