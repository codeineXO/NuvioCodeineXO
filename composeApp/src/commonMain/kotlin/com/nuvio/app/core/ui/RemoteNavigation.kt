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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.gestures.animateScrollBy
import com.nuvio.app.isDesktop

internal class RemoteNavigationSession {
    var activeShelf: PosterShelfController? = null
    var keyboard by mutableStateOf(false)
    var enterHeld = false
    var leftHeld = false
    var leftMovedDuringHold = false
    var arrowHeldKey: Key? = null
    var streamSourceMovedDuringHold = false
    val rememberedFocus = mutableMapOf<Any, Int>()
    var lastFocused: FocusRequester? = null
    val targets = mutableStateListOf<RemoteFocusTarget>()
    var explicitFocusTransfers = 0
    val explicitFocusTransfer get() = explicitFocusTransfers > 0
    val primaryRevealers = mutableStateMapOf<Any, suspend () -> Unit>()
    var focusedTarget by mutableStateOf<RemoteFocusTarget?>(null)
    val rememberedBounds = mutableMapOf<Any, Rect>()
    val posterPages = mutableMapOf<String, PosterNavigationPage>()
    var posterMoveJob: Job? = null
    var posterMoveDirection: FocusDirection? = null
    var posterMovePage: String? = null
    var pendingPosterMove: PosterMoveCommand? = null
    var posterPressedDirection: FocusDirection? = null
    var posterRepeatingDirection: FocusDirection? = null
    val fastPosterScroll get() = posterMoveDirection != null && posterRepeatingDirection == posterMoveDirection
    var posterMoveGeneration = 0
    var navigationScope: CoroutineScope? = null
    val contentViewports = mutableListOf<RemoteContentViewport>()
    val pendingViewportRoutes = mutableSetOf<Any>()
    var hostBounds: Rect? = null
    var modalDepth = 0
    var focusTransferReason by mutableStateOf(RemoteFocusTransferReason.Navigation)

    fun canEnterSidebar(): Boolean = !leftMovedDuringHold

    fun resetHoldState() {
        activeShelf?.stop()
        activeShelf = null
        cancelPosterNavigation()
        leftHeld = false
        leftMovedDuringHold = false
        arrowHeldKey = null
        streamSourceMovedDuringHold = false
    }

    fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown && event.key != Key.DirectionLeft && event.key != Key.DirectionRight) {
            activeShelf?.stop()
            activeShelf = null
        }
        if (event.type == KeyEventType.KeyUp) remoteDirection(event.key)?.let { releasePosterDirection(it); activeShelf?.release(it) }
        if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && !RemoteTextEditing.isTextInputFocused) {
            if (event.type == KeyEventType.KeyDown) {
                if (enterHeld) return true
                enterHeld = true
            } else enterHeld = false
        }
        val isArrowKey = event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
            event.key == Key.DirectionLeft || event.key == Key.DirectionRight
        if (isArrowKey && !RemoteTextEditing.isTextInputFocused) {
            if (event.type == KeyEventType.KeyDown) {
                if (arrowHeldKey != event.key) {
                    arrowHeldKey = event.key
                    streamSourceMovedDuringHold = false
                }
            } else if (event.type == KeyEventType.KeyUp) {
                if (arrowHeldKey == event.key) {
                    arrowHeldKey = null
                    streamSourceMovedDuringHold = false
                }
            }
        } else if (event.type == KeyEventType.KeyDown && !RemoteTextEditing.isTextInputFocused) {
            arrowHeldKey = null
            streamSourceMovedDuringHold = false
        }
        if (event.key == Key.DirectionLeft && !RemoteTextEditing.isTextInputFocused) {
            if (event.type == KeyEventType.KeyDown) {
                if (!leftHeld) {
                    leftHeld = true
                    leftMovedDuringHold = false
                }
            } else if (event.type == KeyEventType.KeyUp) {
                leftHeld = false
                leftMovedDuringHold = false
            }
        } else if (event.type == KeyEventType.KeyDown && event.key != Key.DirectionLeft && !RemoteTextEditing.isTextInputFocused) {
            leftHeld = false
            leftMovedDuringHold = false
        }
        return false
    }

    fun restoreFocus(route: Any, manager: FocusManager): RemoteFocusRestoration {
        if (posterRestorationPending(route)) return RemoteFocusRestoration.Pending
        val bounds = rememberedBounds[route]
        val candidates = targets.filter {
            it.route == route && it.manager === manager && (
                it.initialFocus ||
                it.identity == rememberedFocus[route] ||
                it.bounds?.let { b -> b.width > 0 && b.height > 0 } == true
            )
        }
        val restored = candidates.sortedBy {
            when {
                it.identity == rememberedFocus[route] -> -1f
                rememberedFocus[route] == null && it.initialFocus -> -0.5f
                bounds != null && it.bounds != null -> (it.bounds!!.center - bounds.center).getDistanceSquared()
                else -> candidates.indexOf(it).toFloat()
            }
        }.any { it.requester.requestFocus() }
        return if (restored) RemoteFocusRestoration.Restored else RemoteFocusRestoration.Unavailable
    }

    fun moveFocus(route: Any, direction: FocusDirection, manager: FocusManager, sourceOverride: RemoteFocusTarget? = null): Boolean {
        val source = sourceOverride ?: focusedTarget ?: targets.firstOrNull { it.route == route && (it.initialFocus || it.identity == rememberedFocus[route]) }
        if (source?.pageBoundary != null) return movePageFocus(source, direction)
        val isSourceSidebar = source?.role == RemoteNavigationRole.Sidebar
        val isSourceStreamSource = source?.role == RemoteNavigationRole.StreamSource
        val sidebarEntry = targets.firstOrNull {
            it.route == route && it.manager === manager && it.role == RemoteNavigationRole.Sidebar && it.sidebarEntry
        }
        val moved = manager.moveFocus(direction)
        if (moved && focusedTarget != null && focusedTarget !== source) {
            val destination = focusedTarget
            val isDestinationSidebar = destination?.role == RemoteNavigationRole.Sidebar
            val isDestinationStreamSource = destination?.role == RemoteNavigationRole.StreamSource
            if (!isSourceSidebar && isDestinationSidebar) {
                if (direction != FocusDirection.Left || !canEnterSidebar()) {
                    source?.requester?.requestFocus()
                    return false
                }
                if (sidebarEntry != null && destination !== sidebarEntry) {
                    return sidebarEntry.requester.requestFocus()
                }
            }
            if (isSourceStreamSource && !isDestinationStreamSource) {
                if (streamSourceMovedDuringHold) {
                    source?.requester?.requestFocus()
                    return false
                }
            }
            if (!isSourceSidebar && !isDestinationSidebar && direction == FocusDirection.Left) {
                leftMovedDuringHold = true
            }
            if (isSourceStreamSource && isDestinationStreamSource) {
                streamSourceMovedDuringHold = true
            }
            return true
        }
        val origin = source?.bounds ?: return moved
        val candidates = targets.filter {
            it.route == route && it.manager === manager && it !== source && it.bounds?.let { b -> b.width > 0 && b.height > 0 } == true &&
                (!isSourceStreamSource || !streamSourceMovedDuringHold || it.role == RemoteNavigationRole.StreamSource)
        }
        val fallback = when {
            isSourceSidebar -> when (direction) {
                FocusDirection.Up -> candidates.filter { it.role == RemoteNavigationRole.Sidebar && it.bounds!!.center.y < origin.center.y }
                FocusDirection.Down -> candidates.filter { it.role == RemoteNavigationRole.Sidebar && it.bounds!!.center.y > origin.bottom }
                FocusDirection.Right -> candidates.filter { it.role != RemoteNavigationRole.Sidebar }
                else -> emptyList()
            }
            direction == FocusDirection.Up -> candidates.filter {
                it.role != RemoteNavigationRole.Sidebar && it.bounds!!.center.y < origin.center.y && (
                    it.role == RemoteNavigationRole.Back ||
                    isSourceStreamSource ||
                    (it.bounds!!.right > origin.left - origin.width && it.bounds!!.left < origin.right + origin.width)
                )
            }
            direction == FocusDirection.Left -> candidates.filter {
                it.bounds!!.center.x < origin.center.x && (
                    (it.role == RemoteNavigationRole.Sidebar && !isSourceSidebar && canEnterSidebar()) ||
                    (it.role != RemoteNavigationRole.Sidebar && kotlin.math.abs(it.bounds!!.center.y - origin.center.y) < maxOf(origin.height, it.bounds!!.height) * 2.5f)
                )
            }
            direction == FocusDirection.Right -> candidates.filter {
                it.role != RemoteNavigationRole.Sidebar && it.bounds!!.center.x > origin.center.x &&
                    kotlin.math.abs(it.bounds!!.center.y - origin.center.y) < maxOf(origin.height, it.bounds!!.height) * 2.5f
            }
            source?.role == RemoteNavigationRole.Back && (direction == FocusDirection.Down || direction == FocusDirection.Right) ->
                candidates.filter { it.role != RemoteNavigationRole.Back && it.role != RemoteNavigationRole.Sidebar }
            direction == FocusDirection.Down -> candidates.filter {
                it.role != RemoteNavigationRole.Sidebar && it.bounds!!.center.y > origin.center.y && it.bounds!!.bottom > origin.bottom
            }
            else -> emptyList()
        }
        return fallback.sortedBy { candidate ->
            if (source?.role == RemoteNavigationRole.Back && candidate.initialFocus) -1f
            else {
                val b = candidate.bounds!!
                when (direction) {
                    FocusDirection.Up -> {
                        val dy = (origin.top - b.bottom).coerceAtLeast(0f)
                        val dx = kotlin.math.abs(b.center.x - origin.center.x)
                        dy * 1000f + dx
                    }
                    FocusDirection.Down -> {
                        val dy = (b.top - origin.bottom).coerceAtLeast(0f)
                        val dx = kotlin.math.abs(b.center.x - origin.center.x)
                        dy * 1000f + dx
                    }
                    FocusDirection.Left -> {
                        if (candidate.role == RemoteNavigationRole.Sidebar) {
                            500000f + kotlin.math.abs(b.center.y - origin.center.y)
                        } else {
                            val dx = (origin.left - b.right).coerceAtLeast(0f)
                            val dy = kotlin.math.abs(b.center.y - origin.center.y)
                            dx * 1000f + dy
                        }
                    }
                    FocusDirection.Right -> {
                        val dx = (b.left - origin.right).coerceAtLeast(0f)
                        val dy = kotlin.math.abs(b.center.y - origin.center.y)
                        dx * 1000f + dy
                    }
                    else -> (b.center - origin.center).getDistanceSquared()
                }
            }
        }.any { spatialCandidate ->
            val candidate = if (!isSourceSidebar && spatialCandidate.role == RemoteNavigationRole.Sidebar) {
                sidebarEntry ?: spatialCandidate
            } else spatialCandidate
            val requested = candidate.requester.requestFocus()
            if (requested) {
                if (!isSourceSidebar && candidate.role != RemoteNavigationRole.Sidebar && direction == FocusDirection.Left) {
                    leftMovedDuringHold = true
                }
                if (isSourceStreamSource && candidate.role == RemoteNavigationRole.StreamSource) {
                    streamSourceMovedDuringHold = true
                }
            }
            requested
        } || (moved && focusedTarget != null && focusedTarget !== source)
    }
}

internal enum class RemoteNavigationRole { Default, Back, Sidebar, StreamSource, PageSwitch }

internal class RemoteFocusTarget(val route: Any, val identity: Int, val requester: FocusRequester, val initialFocus: Boolean, val role: RemoteNavigationRole, val manager: FocusManager, val sidebarEntry: Boolean = false) {
    var pageBoundary: RemoteNavigationPageBoundary? = null
    var shelfItem: PosterShelfItem? = null
    var poster: PosterNavigationItem? = null
    var bounds by mutableStateOf<Rect?>(null)
    var visibleBounds: Rect? = null
    var focused by mutableStateOf(false)
    var reveal: suspend () -> Unit = {}
}

internal val LocalRemoteNavigationRole = staticCompositionLocalOf { RemoteNavigationRole.Default }
internal val LocalRemoteFocusOutlineColor = staticCompositionLocalOf<Color?> { null }
internal val LocalRemoteNavigation = staticCompositionLocalOf<RemoteNavigationSession?> { null }
internal val LocalRemoteRoute = staticCompositionLocalOf<Any> { Unit }
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
    private data class Registration(val recover: (Boolean) -> Boolean, val mouseMoved: () -> Unit, val cancelHeld: () -> Unit)
    private val handlers = mutableListOf<Registration>()
    fun register(handler: (Boolean) -> Boolean, mouseMoved: () -> Unit, cancelHeld: () -> Unit) {
        handlers += Registration(handler, mouseMoved, cancelHeld)
    }
    fun unregister(handler: (Boolean) -> Boolean) { handlers.removeAll { it.recover === handler } }
    fun recoverFocus(force: Boolean = false): Boolean = handlers.lastOrNull()?.recover?.invoke(force) ?: false
    fun mouseMoved() { handlers.lastOrNull()?.mouseMoved?.invoke() }
    fun focusLost() { handlers.lastOrNull()?.cancelHeld?.invoke() }
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
    if (isDesktop) BindPlatformBackInput()
    val keyboardNavigationEnabled by com.nuvio.app.features.settings.KeyboardNavigationSettings.enabled.collectAsState()
    val navigationEnabled = enabled && isDesktop && keyboardNavigationEnabled
    val session = remember { RemoteNavigationSession() }
    val navigationScope = rememberCoroutineScope()
    SideEffect { session.navigationScope = navigationScope }
    val focusManager = LocalFocusManager.current
    val rootFocus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    var hostHasFocus by remember { mutableStateOf(false) }
    val platformFocus = LocalRemotePlatformFocus.current
    val recoverFocus by rememberUpdatedState<(Boolean) -> Boolean> { force ->
        if (session.modalDepth > 0 || RemoteTextEditing.isTextInputFocused) false
        else if (session.interruptPosterRestoration(restorationKey, focusManager)) true
        else if (session.enterVisibleViewport(restorationKey, focusManager)) true
        else if (hostHasFocus && !force && session.focusedTarget != null) false else {
            session.keyboard = true
            rootFocus.requestFocus()
            if (session.restoreFocus(restorationKey, focusManager) == RemoteFocusRestoration.Unavailable) focusManager.moveFocus(FocusDirection.Next)
            true
        }
    }
    DisposableEffect(navigationEnabled) {
        // Preserve input mode while playback temporarily disables this host. Mouse motion
        // resets it below; returning from keyboard-launched playback restores selection.
        session.resetHoldState()
        session.enterHeld = false
        val handler: (Boolean) -> Boolean = { recoverFocus(it) }
        if (navigationEnabled) RemoteNavigationDispatcher.register(handler, mouseMoved = {
            session.keyboard = false
            session.resetHoldState()
        }, cancelHeld = session::resetHoldState)
        onDispose { session.cancelPosterNavigation(); RemoteNavigationDispatcher.unregister(handler) }
    }
    LaunchedEffect(navigationEnabled, restorationKey) {
        if (navigationEnabled) {
            // Tab disposal and native player teardown can clear focus after composition.
            withFrameNanos { }
            platformFocus()
            rootFocus.requestFocus()
            if (session.keyboard) session.restoreFocus(restorationKey, focusManager)
        }
    }
    CompositionLocalProvider(
        LocalRemoteNavigation provides session.takeIf { navigationEnabled },
        LocalRemoteRoute provides restorationKey,
    ) {
        RemoteNavigationRevealPolicy {
            Box(
                modifier = modifier.fillMaxSize().then(
                    if (navigationEnabled) Modifier
                        .onGloballyPositioned { session.hostBounds = it.boundsInRoot() }
                        .remoteNavigationPointerIcon()
                        .pointerInput(session, restorationKey) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) {
                                        session.keyboard = false
                                        session.resetHoldState()
                                        if (event.type == PointerEventType.Scroll && session.modalDepth == 0) session.manualScroll(restorationKey)
                                        if (event.type == PointerEventType.Press && session.modalDepth == 0) {
                                            session.focusTransferReason = RemoteFocusTransferReason.Navigation
                                            session.posterPages.values.filter { it.route == restorationKey && it.active }
                                                .forEach { it.cancelRestoration(discardBookmark = true) }
                                        }
                                    }
                                }
                            }
                        }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown &&
                                (remoteDirection(it.key) != null || it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Tab) &&
                                session.modalDepth == 0 && !RemoteTextEditing.isTextInputFocused) {
                                if (remoteDirection(it.key) != null &&
                                    (session.interruptPosterRestoration(restorationKey, focusManager) ||
                                        session.enterVisibleViewport(restorationKey, focusManager))) return@onPreviewKeyEvent true
                                session.focusTransferReason = RemoteFocusTransferReason.Navigation
                            }
                            if (session.onPreviewKeyEvent(it)) return@onPreviewKeyEvent true
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
                                    if (!rootFocused || session.restoreFocus(restorationKey, focusManager) == RemoteFocusRestoration.Unavailable) {
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
}

/** Attach before an existing clickable/focusable modifier; never adds a duplicate focus stop. */
internal fun Modifier.remoteNavigationTarget(
    textField: Boolean = false,
    adjustable: Boolean = false,
    shape: Shape = RoundedCornerShape(NuvioTokens.Radius.md),
    initialFocus: Boolean = false,
    sidebarEntry: Boolean = false,
    role: RemoteNavigationRole? = null,
    focusOutlineColor: Color? = null,
    onEditingChange: ((Boolean) -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onContext: (() -> Unit)? = null,
): Modifier = composed {
    val session = LocalRemoteNavigation.current ?: return@composed this
    if (!LocalScreenActive.current) return@composed this.focusProperties { canFocus = false }
    val route = LocalRemoteRoute.current
    val identity = currentCompositeKeyHash
    val focusManager = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val effectiveRole = role ?: LocalRemoteNavigationRole.current
    val target = remember(route, identity, initialFocus, effectiveRole, focusManager, sidebarEntry) { RemoteFocusTarget(route, identity, requester, initialFocus, effectiveRole, focusManager, sidebarEntry) }
    val shelfItem = LocalPosterShelfItem.current
    val poster = LocalPosterNavigationItem.current
    val pageBoundary = LocalRemoteNavigationPageBoundary.current
    SideEffect { target.poster = poster; target.pageBoundary = pageBoundary; target.shelfItem = shelfItem }
    val bringIntoView = remember { BringIntoViewRequester() }
    SideEffect { target.reveal = { bringIntoView.bringIntoView() } }
    val scope = rememberCoroutineScope()
    val margin = with(LocalDensity.current) { 160.dp.toPx() }
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val select by rememberUpdatedState(onSelect)
    val context by rememberUpdatedState(onContext)
    val editingCallback by rememberUpdatedState(onEditingChange)
    var enterJob by remember { mutableStateOf<Job?>(null) }
    var held by remember { mutableStateOf(false) }
    var longSelected by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { enterJob?.cancel() } }
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var enteringEdit by remember { mutableStateOf(false) }
    val accent = MaterialTheme.nuvio.colors.accent
    val outlineColor = focusOutlineColor ?: LocalRemoteFocusOutlineColor.current ?: accent
    val exitEditing = remember {
        {
            requester.freeFocus()
            editing = false
            editingCallback?.invoke(false)
            session.keyboard = true
        }
    }
    DisposableEffect(focused && editing) {
        if (focused && editing) RemoteTextEditing.register(exitEditing)
        onDispose { RemoteTextEditing.unregister(exitEditing) }
    }
    if (textField) PlatformBackHandler(enabled = focused && editing, onBack = exitEditing)
    DisposableEffect(focused, editing, session.keyboard, textField) {
        if (textField && focused && (editing || !session.keyboard)) {
            RemoteTextEditing.focusField(target)
        }
        onDispose {
            if (textField) RemoteTextEditing.blurField(target)
        }
    }
    DisposableEffect(target) {
        session.targets += target
        onDispose {
            session.targets -= target
            if (session.focusedTarget === target) session.focusedTarget = null
        }
    }
    LaunchedEffect(route) {
        if (session.keyboard && !session.explicitFocusTransfer && !session.posterRestorationPending(route) && session.rememberedFocus[route] == identity) requester.requestFocus()
    }
    LaunchedEffect(focused, session.keyboard, session.focusTransferReason) {
        if (focused && session.keyboard && session.focusTransferReason != RemoteFocusTransferReason.ViewportEntry &&
            !session.explicitFocusTransfer && !initialFocus && effectiveRole != RemoteNavigationRole.Back && effectiveRole != RemoteNavigationRole.Sidebar) {
            if (shelfItem != null) {
                // Horizontal reveal belongs to the shelf; retain page-level vertical reveal.
                if (poster == null) {
                    bringIntoView.bringIntoView(target.bounds?.let { Rect(0f, -margin, it.width, it.height + margin) })
                }
                session.posterPages[poster?.page]?.let { page ->
                    page.rows.firstOrNull { it.key == poster?.row }?.let { session.revealPosterRow(poster!!.page, it) }
                }
            } else if (poster?.role == PosterTargetRole.Poster && (poster.page == "home" || poster.page == "collection")) {
                // Let native focus scrolling retain its velocity across key repeats.
                // Correct the padded edge only after that reveal completes.
                bringIntoView.bringIntoView()
                session.revealHorizontallyFocusedPoster(poster)
            } else {
                bringIntoView.bringIntoView(target.bounds?.let { Rect(0f, -margin, it.width, it.height + margin) })
            }
        }
    }
    this
        .remoteNavigationPointerIcon()
        .focusRequester(requester)
        .bringIntoViewRequester(bringIntoView)
        .onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            target.bounds = Rect(position.x, position.y, position.x + coordinates.size.width, position.y + coordinates.size.height)
            target.visibleBounds = coordinates.boundsInRoot()
            if (focused) session.rememberedBounds[route] = target.bounds!!
        }
        .onFocusChanged {
            val hasChildTargetFocused = it.hasFocus && session.targets.any { other ->
                other !== target && other.route == route && (other.focused || other.requester === session.lastFocused)
            }
            val isTargetFocused = it.isFocused || (it.hasFocus && !hasChildTargetFocused)
            focused = isTargetFocused
            target.focused = isTargetFocused
            if (isTargetFocused) {
                session.targets.filter { other ->
                    other !== target && other.route == route && other.focused &&
                        other.bounds?.let { ob -> target.bounds?.let { tb -> ob.contains(tb.center) } } == true
                }.forEach { parent ->
                    parent.focused = false
                }
                session.focusedTarget = target
                session.rememberedFocus[route] = identity
                session.lastFocused = requester
                target.bounds?.let { session.rememberedBounds[route] = it }
            }
            else {
                if (session.focusedTarget === target) session.focusedTarget = null
                enterJob?.cancel()
                held = false
                if (editing) {
                    editing = false
                    editingCallback?.invoke(false)
                }
                enteringEdit = false
            }
        }
        .pointerInput(textField) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) {
                        session.keyboard = false
                        session.resetHoldState()
                    }
                    if (textField && event.type == PointerEventType.Press) {
                        editing = true
                        editingCallback?.invoke(true)
                    }
                }
            }
        }
        .onPreviewKeyEvent { event ->
            session.onPreviewKeyEvent(event)
            val direction = remoteDirection(event.key)
            if (poster != null && event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                session.cancelPosterNavigation(poster.page)
            }
            if (!editing && event.type == KeyEventType.KeyDown &&
                (direction != null || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Tab)) session.keyboard = true
            if (textField && focused && session.focusedTarget === target && editing &&
                event.type == KeyEventType.KeyDown && (direction == FocusDirection.Up || direction == FocusDirection.Down)) {
                exitEditing()
            }
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
                textField && (!focused || session.focusedTarget !== target) -> false
                textField && event.key == Key.Escape && editing -> {
                    if (event.type == KeyEventType.KeyDown) exitEditing()
                    true
                }
                pageBoundary != null && event.key == Key.Tab && !editing -> {
                    if (event.type == KeyEventType.KeyDown) {
                        if (!session.movePageTabFocus(target, event.isShiftPressed)) {
                            session.revealPageFocus(target, if (event.isShiftPressed) FocusDirection.Up else FocusDirection.Down)
                        }
                    }
                    true
                }
                textField && event.key == Key.Tab -> {
                    if (editing) {
                        editing = false
                        editingCallback?.invoke(false)
                    }
                    false
                }
                textField && (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                    if (event.type == KeyEventType.KeyDown && !enteringEdit) {
                        if (editing) exitEditing() else {
                            editing = true
                            editingCallback?.invoke(true)
                        }
                        session.enterHeld = true
                        enteringEdit = true
                    }
                    else if (event.type == KeyEventType.KeyUp) enteringEdit = false
                    true
                }
                textField && editing -> false
                textField && direction == FocusDirection.Right && session.targets.any {
                    it !== target && it.route == route && it.manager === focusManager &&
                        it.bounds?.let { child -> target.bounds?.contains(child.center) } == true
                } -> {
                    if (event.type == KeyEventType.KeyDown) session.targets.firstOrNull {
                        it !== target && it.route == route && it.manager === focusManager &&
                            it.bounds?.let { child -> target.bounds?.contains(child.center) } == true
                    }?.requester?.requestFocus()
                    true
                }
                adjustable && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> false
                shelfItem != null && (direction == FocusDirection.Left || direction == FocusDirection.Right) -> {
                    if (event.type == KeyEventType.KeyDown) shelfItem.controller.press(target, direction)
                    true
                }
                (direction == FocusDirection.Left || direction == FocusDirection.Right) &&
                    poster != null && (poster.page == "home" || poster.page == "collection") -> {
                    if (event.type == KeyEventType.KeyDown) session.movePosterHorizontally(target, direction)
                    true
                }
                direction != null && (session.handlesPosterDirection(target.poster, direction) ||
                    (poster != null && session.posterMovePage == poster.page && session.posterMoveJob?.isActive == true)) -> {
                    if (event.type == KeyEventType.KeyDown) session.enqueuePosterMove(target, direction)
                    true
                }
                direction != null -> {
                    session.keyboard = true
                    if (event.type == KeyEventType.KeyDown && !session.moveFocus(route, direction, focusManager) &&
                        (direction == FocusDirection.Down || direction == FocusDirection.Up)) {
                        if (pageBoundary != null) session.revealPageFocus(target, direction)
                        else scope.launch {
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
                        drawOutline(shape.createOutline(this.size, layoutDirection, this), outlineColor, style = Stroke(width))
                    }
                }
            }
        }
}

internal fun Modifier.remoteNavigationGroup(): Modifier = composed {
    if (LocalRemoteNavigation.current == null) this else this.focusRestorer().focusGroup()
}

/** An explicit destination can reveal lazy content before requesting its focus. */
class RemoteNavigationFocusDestination internal constructor() {
    internal val requester = FocusRequester()
    internal var reveal: (suspend () -> Unit)? = null
    internal var revealAttached: (suspend () -> Unit)? = null
    internal var attached by mutableStateOf(false)
    internal var session: RemoteNavigationSession? = null
    internal var attachmentOwner: Any? = null

    internal suspend fun requestFocus(): Boolean {
        val owner = session
        if (owner != null) owner.explicitFocusTransfers++
        return try {
            reveal?.invoke()
            if (withTimeoutOrNull(750) { snapshotFlow { attached }.first { it } } != true) return false
            revealAttached?.invoke()
            withFrameNanos { }
            runCatching { requester.requestFocus() }.getOrDefault(false).also {
                withFrameNanos { }
                withFrameNanos { }
            }
        } finally {
            if (owner != null) owner.explicitFocusTransfers--
        }
    }
}

@Composable
internal fun BindRemoteNavigationFocusDestination(
    destination: RemoteNavigationFocusDestination?,
    reveal: suspend () -> Unit,
) {
    val currentReveal by rememberUpdatedState(reveal)
    val session = LocalRemoteNavigation.current
    DisposableEffect(destination) {
        val boundReveal: suspend () -> Unit = { currentReveal() }
        destination?.reveal = boundReveal
        destination?.session = session
        onDispose { if (destination != null && destination.reveal === boundReveal) destination.reveal = null }
    }
}

internal fun Modifier.remoteNavigationFocusDestination(destination: RemoteNavigationFocusDestination?): Modifier = composed {
    if (destination == null) return@composed this
    val session = LocalRemoteNavigation.current
    val bring = remember { BringIntoViewRequester() }
    val owner = remember { Any() }
    DisposableEffect(destination) {
        destination.attachmentOwner = owner
        destination.session = session
        destination.revealAttached = { bring.bringIntoView() }
        onDispose {
            if (destination.attachmentOwner === owner) {
                destination.attached = false
                destination.revealAttached = null
                destination.attachmentOwner = null
            }
        }
    }
    this.focusRequester(destination.requester).bringIntoViewRequester(bring).onGloballyPositioned {
        if (destination.attachmentOwner === owner)
            destination.attached = it.isAttached && it.size.width > 0 && it.size.height > 0
    }
}

/** Reveal only the obscured part; visible destinations must not jump to the top. */
internal suspend fun LazyListState.revealNavigationItem(key: Any, keys: List<Any>, stickyHeader: Boolean = false) {
    val index = keys.indexOf(key)
    if (index < 0) return
    val info = layoutInfo
    val headerBottom = if (stickyHeader) info.visibleItemsInfo.firstOrNull { it.index == 0 }
        ?.let { it.offset + it.size } ?: info.viewportStartOffset else info.viewportStartOffset
    val item = info.visibleItemsInfo.firstOrNull { it.key == key }
    if (item == null) {
        animateScrollToItem(index, -headerBottom.coerceAtLeast(0))
    } else {
        val delta = when {
            item.offset < headerBottom -> item.offset - headerBottom
            item.offset + item.size > info.viewportEndOffset ->
                minOf(item.offset - headerBottom, item.offset + item.size - info.viewportEndOffset)
            else -> 0
        }
        if (delta != 0) animateScrollBy(delta.toFloat())
    }
}

/** Scoped to a Details visit: late-loaded Play gets the initial focus exactly once. */
internal fun Modifier.remoteNavigationDetailsEntry(): Modifier = composed {
    var initialized by rememberSaveable { mutableStateOf(false) }
    val session = LocalRemoteNavigation.current ?: return@composed this
    val route = LocalRemoteRoute.current
    val active = LocalScreenActive.current
    val manager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(active, session.keyboard) {
        if (active && session.keyboard && !initialized) {
            snapshotFlow {
                session.primaryRevealers[route] != null ||
                    session.targets.any { it.route == route && it.initialFocus && it.bounds != null }
            }.first { it }
            if (!session.requestPrimaryFocus(route)) session.restoreFocus(route, manager)
            initialized = true
        }
    }
    this.onPreviewKeyEvent { event ->
        if (active && event.key == Key.DirectionDown && session.focusedTarget?.role == RemoteNavigationRole.Back) {
            if (event.type == KeyEventType.KeyDown) {
                scope.launch {
                    if (!session.requestPrimaryFocus(route)) session.moveFocus(route, FocusDirection.Down, manager)
                }
            }
            return@onPreviewKeyEvent true
        }
        false
    }
}

@Composable
internal fun rememberRemotePrimaryFocus(): suspend () -> Unit {
    val session = LocalRemoteNavigation.current
    val route = LocalRemoteRoute.current
    return {
        // A page replacement can dispose the initiating target's coroutine scope.
        // Finish the transfer in the host scope that owns both pages.
        val owner = session
        if (owner?.navigationScope != null) {
            owner.navigationScope?.launch { owner.requestPrimaryFocus(route) }?.join()
        } else {
            owner?.requestPrimaryFocus(route)
        }
        Unit
    }
}

private suspend fun RemoteNavigationSession.requestPrimaryFocus(route: Any): Boolean {
    primaryRevealers[route]?.invoke()
    val play = withTimeoutOrNull(750) {
        snapshotFlow { targets.firstOrNull { it.route == route && it.initialFocus && it.bounds != null } }
            .first { it != null }
    } ?: return false
    play.reveal()
    return play.requester.requestFocus()
}

/** The owning list can compose Play even when user-configured sections precede it. */
@Composable
internal fun BindRemotePrimaryFocusReveal(
    state: LazyListState,
    hasPlayAction: Boolean,
    beforeReveal: suspend () -> Unit = {},
) {
    val prepareReveal by rememberUpdatedState(beforeReveal)
    val session = LocalRemoteNavigation.current ?: return
    val route = LocalRemoteRoute.current
    val active = LocalScreenActive.current
    DisposableEffect(session, route, state, active, hasPlayAction) {
        val reveal: suspend () -> Unit = {
            prepareReveal()
            snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > 0 }
            if (session.targets.none { it.route == route && it.initialFocus && it.bounds != null }) {
                for (index in 0 until state.layoutInfo.totalItemsCount) {
                    state.scrollToItem(index)
                    withFrameNanos { }
                    if (session.targets.any { it.route == route && it.initialFocus && it.bounds != null }) break
                }
            }
        }
        if (active && hasPlayAction) session.primaryRevealers[route] = reveal
        onDispose { if (session.primaryRevealers[route] === reveal) session.primaryRevealers.remove(route) }
    }
}

/** Enter the first available control below the enhanced hero, skipping text-only sections. */
@Composable
internal fun rememberRemoteNextSectionFocus(state: LazyListState, firstItemIndex: Int = 1, fromEnd: Boolean = false): suspend () -> Unit {
    val session = LocalRemoteNavigation.current
    val route = LocalRemoteRoute.current
    return action@{
        if (session == null) return@action
        val source = session.focusedTarget
        val originalIndex = state.firstVisibleItemIndex
        val originalOffset = state.firstVisibleItemScrollOffset
        val indices = (firstItemIndex until state.layoutInfo.totalItemsCount).let { if (fromEnd) it.reversed() else it }
        for (index in indices) {
            state.animateScrollToItem(index)
            withFrameNanos { }
            val candidates = session.targets.filter {
                val viewport = it.pageBoundary?.viewportBounds
                it.route == route && it !== source && !it.initialFocus &&
                    it.role == RemoteNavigationRole.Default &&
                    (source?.pageBoundary == null || it.pageBoundary === source.pageBoundary) &&
                    it.bounds?.let { bounds ->
                        if (viewport != null) bounds.bottom > viewport.top && bounds.top < viewport.bottom
                        else bounds.top >= 0 && bounds.bottom <= state.layoutInfo.viewportEndOffset
                    } == true
            }
            val candidate = if (fromEnd) candidates.maxByOrNull { it.bounds!!.bottom }
                else candidates.minByOrNull { it.bounds!!.top }
            if (candidate != null && candidate.requester.requestFocus()) return@action
        }
        // A text-only secondary page should stay at its opening position.
        if (firstItemIndex == 0) state.scrollToItem(originalIndex, originalOffset)
    }
}

/** Consume both key phases so a missing destination cannot fall through to spatial navigation. */
internal fun Modifier.remoteNavigationDown(onDown: (suspend () -> Unit)?): Modifier = composed {
    this.remoteNavigationDirection(Key.DirectionDown, onDown)
}

internal fun Modifier.remoteNavigationUp(onUp: (suspend () -> Unit)?): Modifier =
    remoteNavigationDirection(Key.DirectionUp, onUp)

private fun Modifier.remoteNavigationDirection(key: Key, onDown: (suspend () -> Unit)?): Modifier = composed {
    val session = LocalRemoteNavigation.current
    val active = LocalScreenActive.current
    val scope = rememberCoroutineScope()
    val handler by rememberUpdatedState(onDown)
    var moveJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(active, onDown != null) { onDispose { moveJob?.cancel() } }
    if (session == null || !active || onDown == null) this else onPreviewKeyEvent { event ->
        if (event.key != key) false
        else {
            if (event.type == KeyEventType.KeyDown && RemoteTextEditing.isEditing) RemoteTextEditing.dispatchBack()
            session.keyboard = true
            session.onPreviewKeyEvent(event)
            if (event.type == KeyEventType.KeyDown && moveJob?.isActive != true) {
                moveJob = scope.launch { handler?.invoke() }
            }
            true
        }
    }
}

/** Restore artwork above the hero's focused controls. */
internal fun Modifier.remoteNavigationScrollToTop(state: LazyListState): Modifier = composed {
    val session = LocalRemoteNavigation.current ?: return@composed this
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus, session.keyboard, session.focusedTarget, session.focusTransferReason) {
        if (hasFocus && session.keyboard && session.focusTransferReason != RemoteFocusTransferReason.ViewportEntry) {
            // Let child focus reveal finish before aligning the complete hero.
            delay(300)
            state.animateScrollToItem(0)
        }
    }
    this.onFocusChanged { hasFocus = it.hasFocus }.focusGroup()
}

/** Keep directional movement inside an overlay and return to its opener on dismissal. */
internal fun Modifier.remoteNavigationModal(onDismissRequest: (() -> Unit)? = null): Modifier = composed {
    val session = LocalRemoteNavigation.current ?: return@composed this
    val requester = remember { FocusRequester() }
    val opener = remember { session.lastFocused }
    val dismiss by rememberUpdatedState(onDismissRequest)
    var consumeOpeningEnter by remember { mutableStateOf(session.enterHeld) }
    LaunchedEffect(Unit) { requester.requestFocus() }
    DisposableEffect(Unit) {
        session.modalDepth++
        session.posterPages.values.filter { it.active }.forEach { it.cancelRestoration(discardBookmark = true) }
        onDispose {
            session.modalDepth--
            if (session.keyboard) runCatching { opener?.requestFocus() }
        }
    }
    this.remoteNavigationPointerIcon().focusRequester(requester)
        .onPreviewKeyEvent { event ->
            // A modal can receive repeats in a separate window after its opener loses focus.
            // Keep the opening press out of its actions, including the final key-up.
            if (consumeOpeningEnter && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                if (event.type == KeyEventType.KeyUp) {
                    consumeOpeningEnter = false
                    session.enterHeld = false
                }
                true
            } else false
        }
        .onKeyEvent { event ->
            // Popup windows do not bubble Backspace to the screen's navigation host.
            if (event.key == Key.Backspace && !RemoteTextEditing.isTextInputFocused && dismiss != null) {
                if (event.type == KeyEventType.KeyDown) dismiss?.invoke()
                true
            } else false
        }
        .focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()
}
