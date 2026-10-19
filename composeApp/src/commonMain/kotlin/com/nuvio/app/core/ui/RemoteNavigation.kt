package com.nuvio.app.core.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nuvio.app.isDesktop

internal class RemoteNavigationSession {
    var keyboard by mutableStateOf(false)
    var enterHeld = false
    val rememberedFocus = mutableMapOf<Any, Int>()
    var lastFocused: FocusRequester? = null
    val targets = mutableListOf<RemoteFocusTarget>()
    var focusedTarget by mutableStateOf<RemoteFocusTarget?>(null)
    val rememberedBounds = mutableMapOf<Any, Rect>()

    fun restoreFocus(route: Any, manager: FocusManager): Boolean {
        val bounds = rememberedBounds[route]
        val candidates = targets.filter { it.route == route && it.manager === manager && it.bounds?.let { bounds -> bounds.width > 0 && bounds.height > 0 } == true }
        return candidates.sortedBy {
            when {
                it.identity == rememberedFocus[route] -> -1f
                rememberedFocus[route] == null && it.initialFocus -> -0.5f
                bounds != null -> (it.bounds!!.center - bounds.center).getDistanceSquared()
                else -> candidates.indexOf(it).toFloat()
            }
        }.any { it.requester.requestFocus() }
    }

    fun moveFocus(route: Any, direction: FocusDirection, manager: FocusManager): Boolean {
        val source = focusedTarget
        val moved = manager.moveFocus(direction)
        if (moved && focusedTarget != null && focusedTarget !== source) return true
        val origin = source?.bounds ?: return moved
        val candidates = targets.filter {
            it.route == route && it.manager === manager && it !== source && it.bounds?.let { b -> b.width > 0 && b.height > 0 } == true
        }
        val fallback = when {
            direction == FocusDirection.Up -> candidates.filter { it.role == RemoteNavigationRole.Back && it.bounds!!.center.y < origin.center.y }
            direction == FocusDirection.Left -> candidates.filter { it.role == RemoteNavigationRole.Back && it.bounds!!.center.x < origin.center.x }
            source.role == RemoteNavigationRole.Back && (direction == FocusDirection.Down || direction == FocusDirection.Right) ->
                candidates.filter { it.role != RemoteNavigationRole.Back }
            direction == FocusDirection.Down -> candidates.filter { it.bounds!!.center.y > origin.bottom }
            else -> emptyList()
        }
        return fallback.sortedBy {
            if (source.role == RemoteNavigationRole.Back && it.initialFocus) -1f
            else (it.bounds!!.center - origin.center).getDistanceSquared()
        }.any { it.requester.requestFocus() } || moved
    }
}

internal enum class RemoteNavigationRole { Default, Back }

internal class RemoteFocusTarget(val route: Any, val identity: Int, val requester: FocusRequester, val initialFocus: Boolean, val role: RemoteNavigationRole, val manager: FocusManager) {
    var bounds: Rect? = null
}

private val LocalRemoteNavigation = staticCompositionLocalOf<RemoteNavigationSession?> { null }
private val LocalRemoteRoute = staticCompositionLocalOf<Any> { Unit }
internal val LocalRemotePlatformFocus = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalRemoteHiddenPointerIcon = staticCompositionLocalOf<PointerIcon?> { null }

internal val isRemoteKeyboardNavigationActive: Boolean
    @Composable get() = LocalRemoteNavigation.current?.keyboard == true

/** Hover styling stays inactive until real mouse motion restores mouse mode. */
@Composable
internal fun InteractionSource.collectIsRemoteHoveredAsState(): State<Boolean> {
    val hovered = collectIsHoveredAsState()
    val session = LocalRemoteNavigation.current
    return remember(hovered, session) { derivedStateOf { hovered.value && session?.keyboard != true } }
}

private fun Modifier.remoteNavigationPointerIcon(): Modifier = composed {
    val icon = LocalRemoteHiddenPointerIcon.current
    val session = LocalRemoteNavigation.current
    if (session != null && icon != null) {
        // Keep the hover node attached so a stationary cursor updates immediately on key input.
        this.pointerHoverIcon(if (session.keyboard) icon else PointerIcon.Default, overrideDescendants = session.keyboard)
    } else this
}

internal fun Modifier.excludeFromRemoteNavigation(): Modifier = composed {
    if (LocalRemoteNavigation.current == null) this else this.focusProperties { canFocus = false }
}

/** The desktop key entry point can recover even when no Compose target owns focus. */
internal object RemoteNavigationDispatcher {
    private data class Registration(val recover: (Boolean) -> Boolean, val mouseMoved: () -> Unit)
    private val handlers = mutableListOf<Registration>()
    fun register(handler: (Boolean) -> Boolean, mouseMoved: () -> Unit) { handlers += Registration(handler, mouseMoved) }
    fun unregister(handler: (Boolean) -> Boolean) { handlers.removeAll { it.recover === handler } }
    fun recoverFocus(force: Boolean = false): Boolean = handlers.lastOrNull()?.recover?.invoke(force) ?: false
    fun mouseMoved() { handlers.lastOrNull()?.mouseMoved?.invoke() }
    val isEnabled: Boolean get() = handlers.isNotEmpty()
    fun dispatchBackspace(): Boolean = isEnabled && !RemoteTextEditing.isTextInputFocused && PlatformBackDispatcher.dispatch()
}

/** Retained outgoing entries must neither claim focus nor write focus memory for the incoming route. */
@Composable
internal fun RemoteNavigationEntry(routeKey: Any, active: Boolean, content: @Composable () -> Unit) {
    if (!isDesktop) { content(); return }
    CompositionLocalProvider(
        LocalRemoteRoute provides routeKey,
        LocalScreenActive provides (active && LocalScreenActive.current),
    ) {
        Box(Modifier.fillMaxSize().then(if (active) Modifier else Modifier.focusProperties { canFocus = false })) {
            content()
        }
    }
}

@Composable
internal fun RemoteNavigationDisabled(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRemoteNavigation provides null, content = content)
}

/** Editing gets first refusal before the desktop AWT back dispatcher pops a route. */
internal object RemoteTextEditing {
    private val editors = mutableListOf<() -> Unit>()
    private val focusedFields = mutableSetOf<Any>()
    val isEditing: Boolean get() = editors.isNotEmpty()
    val isTextInputFocused: Boolean get() = focusedFields.isNotEmpty()
    fun focusField(owner: Any) { focusedFields += owner }
    fun blurField(owner: Any) { focusedFields -= owner }
    fun register(exit: () -> Unit) { editors += exit }
    fun unregister(exit: () -> Unit) { editors -= exit }
    fun dispatchBack(): Boolean {
        val exit = editors.lastOrNull() ?: return false
        exit()
        return true
    }
}

/** Also used by custom PIN inputs, whose Backspace handling must take precedence over navigation. */
internal fun Modifier.remoteTextInputGuard(): Modifier = composed {
    val owner = remember { Any() }
    DisposableEffect(Unit) { onDispose { RemoteTextEditing.blurField(owner) } }
    this.onFocusChanged {
        if (it.isFocused) RemoteTextEditing.focusField(owner) else RemoteTextEditing.blurField(owner)
    }
}

internal fun remoteDirection(key: Key): FocusDirection? = when (key) {
    Key.DirectionLeft -> FocusDirection.Left
    Key.DirectionRight -> FocusDirection.Right
    Key.DirectionUp -> FocusDirection.Up
    Key.DirectionDown -> FocusDirection.Down
    else -> null
}

/** A desktop-only boundary; excluded screens inherit a null session. */
@Composable
internal fun RemoteNavigationHost(
    enabled: Boolean,
    restorationKey: Any = Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val session = remember { RemoteNavigationSession() }
    val focusManager = LocalFocusManager.current
    val rootFocus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    var hostHasFocus by remember { mutableStateOf(false) }
    val platformFocus = LocalRemotePlatformFocus.current
    val recoverFocus by rememberUpdatedState<(Boolean) -> Boolean> { force ->
        if (hostHasFocus && !force && (session.focusedTarget != null || RemoteTextEditing.isTextInputFocused)) false else {
            session.keyboard = true
            rootFocus.requestFocus()
            if (!session.restoreFocus(restorationKey, focusManager)) focusManager.moveFocus(FocusDirection.Next)
            true
        }
    }
    DisposableEffect(enabled) {
        session.enterHeld = false
        val handler: (Boolean) -> Boolean = { recoverFocus(it) }
        if (enabled && isDesktop) RemoteNavigationDispatcher.register(handler) { session.keyboard = false }
        onDispose { RemoteNavigationDispatcher.unregister(handler) }
    }
    LaunchedEffect(enabled, restorationKey) {
        if (enabled && isDesktop) {
            // Tab disposal and native player teardown can clear focus after composition.
            withFrameNanos { }
            platformFocus()
            rootFocus.requestFocus()
            if (session.keyboard) session.restoreFocus(restorationKey, focusManager)
        }
    }
    CompositionLocalProvider(
        LocalRemoteNavigation provides session.takeIf { enabled && isDesktop },
        LocalRemoteRoute provides restorationKey,
    ) {
        Box(
            modifier = modifier.fillMaxSize().then(
                if (enabled && isDesktop) Modifier
                    .remoteNavigationPointerIcon()
                    .pointerInput(session) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) {
                                    session.keyboard = false
                                }
                            }
                        }
                    }
                    .onPreviewKeyEvent {
                        if ((it.key == Key.Enter || it.key == Key.NumPadEnter) && !RemoteTextEditing.isTextInputFocused) {
                            if (it.type == KeyEventType.KeyDown) {
                                if (session.enterHeld) return@onPreviewKeyEvent true
                                session.enterHeld = true
                            } else session.enterHeld = false
                        }
                        if (it.type == KeyEventType.KeyDown && !RemoteTextEditing.isEditing &&
                            (remoteDirection(it.key) != null || it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Tab)) session.keyboard = true
                        false
                    }
                    .onKeyEvent {
                        if (it.key == Key.Backspace && !RemoteTextEditing.isTextInputFocused) {
                            if (it.type == KeyEventType.KeyDown) RemoteNavigationDispatcher.dispatchBackspace()
                            return@onKeyEvent true
                        }
                        val direction = remoteDirection(it.key)
                        if (direction != null) {
                            if (it.type == KeyEventType.KeyDown) {
                                session.keyboard = true
                                if (!rootFocused || !session.restoreFocus(restorationKey, focusManager)) {
                                    focusManager.moveFocus(if (rootFocused) FocusDirection.Next else direction)
                                }
                            }
                            true
                        } else false
                    }
                    .focusRequester(rootFocus)
                    .onFocusChanged { rootFocused = it.isFocused; hostHasFocus = it.hasFocus }
                    .focusable()
                else Modifier,
            ),
        ) { content() }
    }
}

/** Attach before an existing clickable/focusable modifier; never adds a duplicate focus stop. */
internal fun Modifier.remoteNavigationTarget(
    textField: Boolean = false,
    adjustable: Boolean = false,
    shape: Shape = RoundedCornerShape(NuvioTokens.Radius.md),
    initialFocus: Boolean = false,
    role: RemoteNavigationRole = RemoteNavigationRole.Default,
    onSelect: (() -> Unit)? = null,
    onContext: (() -> Unit)? = null,
): Modifier = composed {
    val session = LocalRemoteNavigation.current ?: return@composed this
    if (!LocalScreenActive.current) return@composed this.focusProperties { canFocus = false }
    val route = LocalRemoteRoute.current
    val identity = currentCompositeKeyHash
    val focusManager = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val target = remember(route, identity, initialFocus, role, focusManager) { RemoteFocusTarget(route, identity, requester, initialFocus, role, focusManager) }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val margin = with(LocalDensity.current) { 160.dp.toPx() }
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val select by rememberUpdatedState(onSelect)
    val context by rememberUpdatedState(onContext)
    var enterJob by remember { mutableStateOf<Job?>(null) }
    var held by remember { mutableStateOf(false) }
    var longSelected by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { enterJob?.cancel() } }
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var enteringEdit by remember { mutableStateOf(false) }
    val accent = MaterialTheme.nuvio.colors.accent
    val exitEditing = remember { { requester.freeFocus(); editing = false; session.keyboard = true } }
    DisposableEffect(focused && editing) {
        if (focused && editing) RemoteTextEditing.register(exitEditing)
        onDispose { RemoteTextEditing.unregister(exitEditing) }
    }
    DisposableEffect(target) {
        session.targets += target
        onDispose {
            session.targets -= target
            if (session.focusedTarget === target) session.focusedTarget = null
        }
    }
    LaunchedEffect(route) {
        if (session.keyboard && session.rememberedFocus[route] == identity) requester.requestFocus()
    }
    LaunchedEffect(focused, session.keyboard) {
        if (focused && session.keyboard && !initialFocus && role != RemoteNavigationRole.Back) {
            bringIntoView.bringIntoView(target.bounds?.let { Rect(0f, -margin, it.width, it.height + margin) })
        }
    }
    this
        .remoteNavigationPointerIcon()
        .then(if (textField) Modifier.remoteTextInputGuard() else Modifier)
        .focusRequester(requester)
        .bringIntoViewRequester(bringIntoView)
        .onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            target.bounds = Rect(position.x, position.y, position.x + coordinates.size.width, position.y + coordinates.size.height)
            if (focused) session.rememberedBounds[route] = target.bounds!!
        }
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) {
                session.focusedTarget = target
                session.rememberedFocus[route] = identity
                session.lastFocused = requester
                target.bounds?.let { session.rememberedBounds[route] = it }
            }
            else {
                if (session.focusedTarget === target) session.focusedTarget = null
                enterJob?.cancel()
                held = false
                editing = false
                enteringEdit = false
            }
        }
        .pointerInput(textField) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) session.keyboard = false
                    if (textField && event.type == PointerEventType.Press) editing = true
                }
            }
        }
        .onPreviewKeyEvent { event ->
            val direction = remoteDirection(event.key)
            if (!editing && event.type == KeyEventType.KeyDown &&
                (direction != null || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Tab)) session.keyboard = true
            when {
                !textField && onSelect != null && (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                    if (event.type == KeyEventType.KeyDown && !held) {
                        held = true
                        longSelected = false
                        enterJob = scope.launch {
                            delay(longPressTimeout)
                            longSelected = true
                            context?.invoke()
                        }
                    } else if (event.type == KeyEventType.KeyUp && held) {
                        held = false
                        enterJob?.cancel()
                        if (!longSelected) select?.invoke()
                    }
                    true
                }
                textField && !focused -> false
                textField && event.key == Key.Escape && editing -> {
                    if (event.type == KeyEventType.KeyDown) exitEditing()
                    true
                }
                textField && event.key == Key.Tab -> { editing = false; false }
                textField && (event.key == Key.Enter || event.key == Key.NumPadEnter) && (!editing || enteringEdit) -> {
                    if (event.type == KeyEventType.KeyDown) { editing = true; enteringEdit = true }
                    else enteringEdit = false
                    true
                }
                textField && editing -> false
                adjustable && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> false
                direction != null -> {
                    session.keyboard = true
                    if (event.type == KeyEventType.KeyDown && !session.moveFocus(route, direction, focusManager) &&
                        (direction == FocusDirection.Down || direction == FocusDirection.Up)) {
                        scope.launch {
                            val bounds = target.bounds ?: return@launch
                            val extra = margin * 3
                            bringIntoView.bringIntoView(Rect(0f, if (direction == FocusDirection.Up) -extra else 0f,
                                bounds.width, bounds.height + if (direction == FocusDirection.Down) extra else 0f))
                            withFrameNanos { }
                            session.moveFocus(route, direction, focusManager)
                        }
                    }
                    true
                }
                textField -> event.key != Key.Escape
                else -> false
            }
        }
        .drawWithContent {
            drawContent()
            if (focused && session.keyboard) {
                val width = 2.dp.toPx()
                if (size.width > width && size.height > width) {
                    inset(width / 2) {
                        drawOutline(shape.createOutline(size, layoutDirection, this), accent, style = Stroke(width))
                    }
                }
            }
        }
}

internal fun Modifier.remoteNavigationGroup(): Modifier = composed {
    if (LocalRemoteNavigation.current == null) this else this.focusRestorer().focusGroup()
}

/** Restore artwork above the hero's focused controls. */
internal fun Modifier.remoteNavigationScrollToTop(state: LazyListState): Modifier = composed {
    val session = LocalRemoteNavigation.current ?: return@composed this
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus, session.keyboard, session.focusedTarget) {
        if (hasFocus && session.keyboard) {
            // Let child focus reveal finish before aligning the complete hero.
            delay(300)
            state.animateScrollToItem(0)
        }
    }
    this.onFocusChanged { hasFocus = it.hasFocus }.focusGroup()
}

/** Keep directional movement inside an overlay and return to its opener on dismissal. */
internal fun Modifier.remoteNavigationModal(): Modifier = composed {
    val session = LocalRemoteNavigation.current ?: return@composed this
    val requester = remember { FocusRequester() }
    val opener = remember { session.lastFocused }
    LaunchedEffect(Unit) { requester.requestFocus() }
    DisposableEffect(Unit) {
        onDispose { if (session.keyboard) runCatching { opener?.requestFocus() } }
    }
    this.remoteNavigationPointerIcon().focusRequester(requester)
        .focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()
}
