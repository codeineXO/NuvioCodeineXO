package com.nuvio.app.core.ui

import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import com.nuvio.app.isDesktop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Keep the platform spline's physics, but sample between its whole-millisecond values. */
private class FractionalMillisecondDecay(
    private val delegate: FloatDecayAnimationSpec,
) : FloatDecayAnimationSpec {
    override val absVelocityThreshold: Float get() = delegate.absVelocityThreshold

    override fun getTargetValue(initialValue: Float, initialVelocity: Float): Float =
        delegate.getTargetValue(initialValue, initialVelocity)

    override fun getDurationNanos(initialValue: Float, initialVelocity: Float): Long =
        delegate.getDurationNanos(initialValue, initialVelocity)

    override fun getValueFromNanos(playTimeNanos: Long, initialValue: Float, initialVelocity: Float): Float {
        val remainder = playTimeNanos % NanosPerMillisecond
        val lower = playTimeNanos - remainder
        val before = delegate.getValueFromNanos(lower, initialValue, initialVelocity)
        if (remainder == 0L) return before
        val after = delegate.getValueFromNanos(lower + NanosPerMillisecond, initialValue, initialVelocity)
        return before + (after - before) * (remainder.toFloat() / NanosPerMillisecond)
    }

    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: Float, initialVelocity: Float): Float {
        val remainder = playTimeNanos % NanosPerMillisecond
        val lower = playTimeNanos - remainder
        val before = delegate.getVelocityFromNanos(lower, initialValue, initialVelocity)
        if (remainder == 0L) return before
        val after = delegate.getVelocityFromNanos(lower + NanosPerMillisecond, initialValue, initialVelocity)
        return before + (after - before) * (remainder.toFloat() / NanosPerMillisecond)
    }
}

private const val NanosPerMillisecond = 1_000_000L

// Match the desktop default fling's duration scale independently of UI transition settings.
private val DesktopFlingDurationScale = object : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

/** Same boundary tolerance, cancellation, and decay lifecycle as the desktop default. */
private class PreciseFlingBehavior(private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val scrollScope = this
        return withContext(DesktopFlingDurationScale) {
            if (abs(initialVelocity) <= 1f) return@withContext initialVelocity
            val animation = AnimationState(initialValue = 0f, initialVelocity = initialVelocity)
            var previous = 0f
            var remaining = initialVelocity
            try {
                animation.animateDecay(decay) {
                    val delta = value - previous
                    val consumed = scrollScope.scrollBy(delta)
                    previous = value
                    remaining = velocity
                    if (abs(delta - consumed) > 0.5f) cancelAnimation()
                }
            } catch (_: CancellationException) {
                remaining = animation.velocity
            }
            remaining
        }
    }
}

@Composable
internal fun rememberNuvioFlingBehavior(): FlingBehavior {
    if (!isDesktop) return ScrollableDefaults.flingBehavior()
    val density = LocalDensity.current
    return remember(density.density, density.fontScale) {
        val decay = FractionalMillisecondDecay(SplineBasedFloatDecayAnimationSpec(density))
            .generateDecayAnimationSpec<Float>()
        PreciseFlingBehavior(decay)
    }
}
