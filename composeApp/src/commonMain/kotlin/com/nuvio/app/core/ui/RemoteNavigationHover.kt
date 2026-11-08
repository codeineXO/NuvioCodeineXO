@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuvio.app.core.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** Each subscriber receives balanced hover pairs; focus and press interactions pass intact. */
internal class KeyboardHoverInteractionSource(
    private val source: InteractionSource,
    private val session: RemoteNavigationSession,
) : InteractionSource {
    override val interactions: Flow<Interaction> = channelFlow {
        val active = mutableSetOf<HoverInteraction.Enter>()
        var keyboard = session.keyboard || !session.pointerHoverReady
        launch(start = CoroutineStart.UNDISPATCHED) {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is HoverInteraction.Enter -> { active += interaction; if (!keyboard) send(interaction) }
                    is HoverInteraction.Exit -> { active -= interaction.enter; if (!keyboard) send(interaction) }
                    else -> send(interaction)
                }
            }
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            snapshotFlow { session.keyboard || !session.pointerHoverReady }.collect { next ->
                if (keyboard != next) {
                    keyboard = next
                    active.forEach { send(if (next) HoverInteraction.Exit(it) else it) }
                }
            }
        }
    }
}

@Composable
internal fun rememberRemoteInteractionSource(): MutableInteractionSource {
    val raw = remember { MutableInteractionSource() }
    val session = LocalRemoteNavigation.current
    return remember(raw, session) {
        if (session == null) raw else object : MutableInteractionSource {
            override val interactions = KeyboardHoverInteractionSource(raw, session).interactions
            override suspend fun emit(interaction: Interaction) = raw.emit(interaction)
            override fun tryEmit(interaction: Interaction) = raw.tryEmit(interaction)
        }
    }
}

private class KeyboardHoverIndication(
    private val delegate: IndicationNodeFactory,
    private val session: RemoteNavigationSession,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        KeyboardHoverIndicationNode(delegate, interactionSource, session)
    override fun equals(other: Any?) = other is KeyboardHoverIndication && delegate == other.delegate && session === other.session
    override fun hashCode() = 31 * delegate.hashCode() + session.hashCode()
}

private class KeyboardHoverIndicationNode(
    factory: IndicationNodeFactory,
    private val source: InteractionSource,
    private val session: RemoteNavigationSession,
) : DelegatingNode() {
    private val filtered = MutableInteractionSource()
    init { delegate(factory.create(filtered)) }
    override fun onAttach() {
        // Subscribe during attachment, before a lazy clickable emits its first Enter.
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            KeyboardHoverInteractionSource(source, session).interactions.collect { filtered.emit(it) }
        }
    }
}

@Composable
internal fun RemoteNavigationHoverPolicy(content: @Composable () -> Unit) {
    val session = LocalRemoteNavigation.current
    val original = LocalIndication.current
    val indication = remember(original, session) {
        if (session != null && original is IndicationNodeFactory) KeyboardHoverIndication(original, session) else original
    }
    // Material controls create ripple nodes directly instead of using LocalIndication.
    val ripple = LocalRippleConfiguration.current
    val alpha = ripple?.rippleAlpha ?: RippleDefaults.RippleAlpha
    val filteredRipple = if (session?.keyboard == true && ripple != null) RippleConfiguration(
        color = ripple.color,
        rippleAlpha = RippleAlpha(
            draggedAlpha = alpha.draggedAlpha,
            focusedAlpha = alpha.focusedAlpha,
            hoveredAlpha = 0f,
            pressedAlpha = alpha.pressedAlpha,
        ),
    ) else ripple
    // Resume hover only after native ripple consumers receive pointer-mode opacity.
    SideEffect { session?.pointerHoverReady = session?.keyboard != true }
    CompositionLocalProvider(LocalIndication provides indication, LocalRippleConfiguration provides filteredRipple, content = content)
}
