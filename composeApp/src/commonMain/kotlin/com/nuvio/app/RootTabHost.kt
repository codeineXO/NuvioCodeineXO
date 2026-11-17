package com.nuvio.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.nuvio.app.core.ui.LocalRetainGlassSources
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.LocalRemoteRoute
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.navigation.pageFadeMillis
import com.nuvio.app.navigation.TabsRoute
import com.nuvio.app.navigation.RandomPickerRoute

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
        val displayedTabs = remember(selectedKey) { (visitedTabs + selectedKey).toList() }
        SideEffect { visitedTabs += selectedKey }
        // The previous tab fades out beneath the incoming one; tabs draw over a shared backdrop, so it must not linger.
        var settledKey by remember { mutableStateOf(selectedKey) }
        val fade = remember(selectedKey) { Animatable(if (selectedKey == settledKey) 1f else 0f) }
        LaunchedEffect(selectedKey) {
            fade.animateTo(1f, tween(pageFadeMillis(NuvioTokens.Motion.sheetEnterMillis), easing = LinearEasing))
            settledKey = selectedKey
        }
        val fromKey = settledKey.takeIf { it != selectedKey && it in displayedTabs }
        // Every pane keeps one graphics layer for life and only its alpha changes: recreating a pane's layer
        // at the switch frame resets the frosted glass inside it, which flashes its opaque fallback.
        val fadeState = rememberUpdatedState(fade)
        val selectedState = rememberUpdatedState(selectedKey)
        val fromState = rememberUpdatedState(fromKey)

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
                            selected = tab == selectedKey,
                            stateHolder = tabStateHolder,
                            modifier = Modifier.graphicsLayer {
                                val progress = fadeState.value.value
                                alpha = when (tab) {
                                    selectedState.value -> NuvioTokens.Motion.decelerate.transform(progress)
                                    fromState.value -> 1f - NuvioTokens.Motion.standard.transform(progress)
                                    else -> 1f
                                }
                            },
                        ) {
                            if (tab == "Shuffle") shuffleContent?.invoke()
                            else content(AppScreenTab.fromName(tab))
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val placeable = measurables[displayedTabs.indexOf(selectedKey)].measure(constraints)
            val below = fromKey?.let { measurables[displayedTabs.indexOf(it)].measure(constraints) }
            layout(placeable.width, placeable.height) {
                below?.placeRelative(0, 0)
                placeable.placeRelative(0, 0)
            }
        }
    }
}

@Composable
private fun RootTabPane(
    tab: String,
    active: Boolean,
    selected: Boolean,
    stateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lifecycleOwner = rememberLifecycleOwner(
        maxLifecycle = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED,
    )
    CompositionLocalProvider(
        LocalLifecycleOwner provides lifecycleOwner,
        LocalScreenActive provides active,
        // Only the selected pane of a page that is fading out keeps feeding glass; hidden tabs never do.
        LocalRetainGlassSources provides (selected && LocalRetainGlassSources.current),
        LocalRemoteRoute provides if (tab == "Shuffle") RandomPickerRoute else TabsRoute to AppScreenTab.fromName(tab),
    ) {
        stateHolder.SaveableStateProvider(tab) {
            Box(
                modifier.fillMaxSize()
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
