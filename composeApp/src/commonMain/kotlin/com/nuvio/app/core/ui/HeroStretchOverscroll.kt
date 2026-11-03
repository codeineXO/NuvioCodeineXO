package com.nuvio.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val HeroStretchMaxDp = 200f
private val HERO_STRETCH_MAX = HeroStretchMaxDp.dp
private const val HERO_STRETCH_DRAG_RESISTANCE = 0.55f
private const val HERO_STRETCH_RELEASE_ABSORB = 0.35f
private const val HERO_STRETCH_FRAME_TO_VELOCITY = 60f
private val HeroStretchReleaseSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 320f,
)
private val HeroStretchBounceSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 110f,
)

internal val ModifierLocalHeroWheelStretch = modifierLocalOf<HeroStretchState?> { null }

/** Advertise wheel overscroll only to the list associated with this hero. */
@Composable
internal fun Modifier.heroStretchOverscroll(state: HeroStretchState): Modifier {
    DisposableEffect(state) { onDispose { state.reset() } }
    return modifierLocalProvider(ModifierLocalHeroWheelStretch) { state }
        .nestedScroll(state.nestedScrollConnection)
}

@Stable
class HeroStretchState internal constructor(
    private val scope: CoroutineScope,
    private val maxStretchPx: Float,
    private val scrollState: ScrollableState? = null,
    private val isAtTop: () -> Boolean,
) {
    private var stretch by mutableFloatStateOf(0f)
    private var settleJob: Job? = null
    private var flingAbsorbed = false

    val stretchPx: Float get() = stretch

    internal fun acceptsWheelOverscroll(state: ScrollableState, delta: Float, reverseScrolling: Boolean): Boolean {
        if (state !== scrollState) return false
        val physicalY = delta * if (reverseScrolling) 1f else -1f
        return if (physicalY > 0f) isAtTop() else physicalY < 0f && stretchPx > 0f
    }

    internal fun reset() {
        settleJob?.cancel()
        stretch = 0f
        flingAbsorbed = false
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
                flingAbsorbed = false
                if (available.y != 0f) settleJob?.cancel()
            }
            val stretch = stretchPx
            if (available.y < 0f && stretch > 0f) {
                val consumedY = available.y.coerceAtLeast(-stretch)
                snapTo(stretch + consumedY)
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (available.y <= 0f || !isAtTop()) return Offset.Zero

            if (source == NestedScrollSource.UserInput) {
                val stretch = stretchPx
                val resistance = HERO_STRETCH_DRAG_RESISTANCE *
                    (1f - stretch / maxStretchPx).coerceIn(0f, 1f)
                snapTo(stretch + available.y * resistance)
            } else if (!flingAbsorbed) {
                flingAbsorbed = true
                if (settleJob?.isActive != true) {
                    settle(available.y * HERO_STRETCH_FRAME_TO_VELOCITY, HeroStretchBounceSpring)
                }
            }
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (stretchPx <= 0f) return Velocity.Zero
            if (settleJob?.isActive != true) {
                settle(available.y * HERO_STRETCH_RELEASE_ABSORB, HeroStretchReleaseSpring)
            }
            return Velocity(0f, available.y)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            flingAbsorbed = false
            if (stretchPx > 0f && settleJob?.isActive != true) {
                settle(0f, HeroStretchReleaseSpring)
            }
            return if (available.y > 0f && isAtTop()) {
                Velocity(0f, available.y)
            } else {
                Velocity.Zero
            }
        }
    }

    private fun snapTo(value: Float) {
        settleJob?.cancel()
        stretch = value.coerceIn(0f, maxStretchPx)
    }

    private fun settle(initialVelocity: Float, spec: SpringSpec<Float>) {
        settleJob?.cancel()
        val animation = Animatable(stretchPx)
        settleJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animation.animateTo(
                targetValue = 0f,
                animationSpec = spec,
                initialVelocity = initialVelocity,
            ) { stretch = value.coerceIn(0f, maxStretchPx) }
            stretch = 0f
        }
    }
}

@Composable
fun rememberHeroStretchState(listState: LazyListState): HeroStretchState {
    val scope = rememberCoroutineScope()
    val maxStretchPx = with(LocalDensity.current) { HERO_STRETCH_MAX.toPx() }
    val state = remember(listState, maxStretchPx) {
        HeroStretchState(scope, maxStretchPx, listState) { !listState.canScrollBackward }
    }
    val active = LocalScreenActive.current
    DisposableEffect(state, active) {
        if (!active) state.reset()
        onDispose { state.reset() }
    }
    return state
}

fun Modifier.heroStretchHeight(baseHeight: Dp, stretchPx: () -> Float): Modifier =
    layout { measurable, constraints ->
        val height = baseHeight.roundToPx() + stretchPx().coerceAtLeast(0f).roundToInt()
        val placeable = measurable.measure(
            constraints.copy(minHeight = height, maxHeight = height),
        )
        layout(placeable.width, height) { placeable.place(0, 0) }
    }

fun Modifier.heroStretchZoom(stretchPx: () -> Float): Modifier = graphicsLayer {
    val zoom = 1f + stretchPx().coerceAtLeast(0f) / size.height.coerceAtLeast(1f)
    transformOrigin = TransformOrigin(0.5f, 0f)
    scaleX = zoom
    scaleY = zoom
}
