package com.nuvio.app

import androidx.compose.foundation.focusGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.navigationMotionDuration

@Composable
internal fun RootTabHost(
    selectedTab: AppScreenTab,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    profileId: Int? = null,
    shuffleSelected: Boolean = false,
    shuffleContent: (@Composable () -> Unit)? = null,
    content: @Composable (AppScreenTab) -> Unit,
) {
    val parentLifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val hostActive = active && parentLifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val focusManager = LocalFocusManager.current
    var contentHasFocus by remember { mutableStateOf(false) }
    val selectedKey = if (shuffleSelected) "Shuffle" else selectedTab.name
    DisposableEffect(selectedKey, hostActive, profileId) {
        onDispose {
            // The sidebar is outside this host and retains focus across tab activation.
            if (contentHasFocus) focusManager.clearFocus(force = true)
        }
    }

    key(profileId) {
        val tabStateHolder = rememberSaveableStateHolder()
        val visitedTabs = remember { mutableSetOf<String>() }
        val displayedTabs = remember(selectedKey) {
            retainedRootTabs(visitedTabs.toList(), selectedKey, if (isDesktop) 2 else Int.MAX_VALUE)
        }
        SideEffect {
            visitedTabs.clear()
            visitedTabs.addAll(displayedTabs)
        }

        Layout(
            modifier = modifier.fillMaxSize()
                .onFocusChanged { contentHasFocus = it.hasFocus }
                .focusGroup(),
            content = {
                displayedTabs.forEach { tab ->
                    key(tab) {
                        RootTabPane(
                            tab = tab,
                            active = hostActive && tab == selectedKey,
                            stateHolder = tabStateHolder,
                        ) {
                            if (tab == "Shuffle") shuffleContent?.invoke()
                            else content(AppScreenTab.fromName(tab))
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val placeable = measurables[displayedTabs.indexOf(selectedKey)].measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }
}

internal fun <T> retainedRootTabs(
    previous: List<T>,
    selected: T,
    limit: Int,
): List<T> = (previous.filterNot { it == selected } + selected).takeLast(limit)

@Composable
private fun RootTabPane(
    tab: String,
    active: Boolean,
    stateHolder: SaveableStateHolder,
    content: @Composable () -> Unit,
) {
    val lifecycleOwner = rememberLifecycleOwner(
        maxLifecycle = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED,
    )
    CompositionLocalProvider(
        LocalLifecycleOwner provides lifecycleOwner,
        LocalScreenActive provides active,
    ) {
        stateHolder.SaveableStateProvider(tab) {
            Box(
                Modifier.fillMaxSize()
                    .rootTabEntrance(active)
                    // An active pane must not turn a descendant focusGroup into a focus stop.
                    .then(if (active) Modifier else Modifier.focusProperties { canFocus = false })
                    .pointerInput(active) {
                        if (!active) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                    .then(if (active) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                content()
            }
        }
    }
}

/** Shared entrance motion for every root pane, including Shuffle. */
@Composable
internal fun Modifier.rootTabEntrance(active: Boolean): Modifier {
    val entrance = remember { Animatable(if (active) 0f else 1f) }
    LaunchedEffect(active) {
        if (active) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(navigationMotionDuration(NuvioTokens.Motion.normalMillis), easing = FastOutSlowInEasing))
        } else entrance.snapTo(1f)
    }
    return graphicsLayer {
        alpha = 0.92f + 0.08f * entrance.value
        translationY = (1f - entrance.value) * 2.dp.toPx()
    }
}
