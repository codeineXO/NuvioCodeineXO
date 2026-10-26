package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

@Composable
expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)

/** Custom desktop inputs join Compose's dispatcher; they never maintain a second handler stack. */
internal object PlatformBackDispatcher {
    private val inputs = mutableListOf<() -> Unit>()

    fun attach(input: () -> Unit): () -> Unit {
        inputs += input
        return { inputs.remove(input) }
    }

    fun dispatch(): Boolean {
        val input = inputs.lastOrNull() ?: return false
        input()
        return true
    }
}

/** App shortcuts remain available without arrow-key navigation or a focused Compose target. */
internal object AppBackspaceNavigation {
    private val scopes = mutableSetOf<Any>()
    val isEnabled: Boolean get() = scopes.isNotEmpty()

    fun attach(): () -> Unit {
        val scope = Any()
        scopes += scope
        return { scopes -= scope }
    }
}

@Composable
internal fun BindAppBackspaceNavigation(enabled: Boolean) {
    DisposableEffect(enabled) {
        val detach = if (enabled) AppBackspaceNavigation.attach() else null
        onDispose { detach?.invoke() }
    }
}

@Composable
internal fun BindPlatformBackInput() {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val input = remember { DirectNavigationEventInput() }
    DisposableEffect(dispatcher) {
        dispatcher?.addInput(input)
        val detach = if (dispatcher != null) PlatformBackDispatcher.attach { input.backCompleted() } else null
        onDispose {
            detach?.invoke()
            dispatcher?.removeInput(input)
        }
    }
}
