package com.nuvio.app.core.ui

import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import com.nuvio.app.isDesktop
import kotlinx.coroutines.CancellationException
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

/**
 * Platform spline decay sampled on a steadied frame timeline. Raw desktop frame timestamps
 * jitter around vsync, which turns into uneven per-frame travel at speed. The first frame
 * advances one full period so a fling never shows a motionless frame.
 */
private class PreciseFlingBehavior(private val decay: FloatDecayAnimationSpec) : DesktopLeadFling {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float = fling(initialVelocity, null)

    /**
     * Takes over from a drag inside the current frame: the first step plays the time since the
     * release, without waiting for another frame, so release shows neither a short nor a
     * doubled frame.
     */
    override suspend fun ScrollScope.performFling(initialVelocity: Float, lead: DesktopDragLead): Float =
        fling(initialVelocity, lead)

    private suspend fun ScrollScope.fling(initialVelocity: Float, lead: DesktopDragLead?): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        val duration = decay.getDurationNanos(0f, initialVelocity)
        val target = decay.getTargetValue(0f, initialVelocity)
        val timeline = SteadyFrameTimeline()
        var start = 0L
        var started = false
        var previous = 0f
        var velocity = initialVelocity
        try {
            while (true) {
                val time = timeline.advance(if (!started && lead != null) lead.frameNanos else withFrameNanos { it })
                if (!started) {
                    started = true
                    start = time - (lead?.playedNanos ?: timeline.periodNanos)
                }
                val playTime = time - start
                val done = playTime >= duration
                val value = if (done) target else decay.getValueFromNanos(playTime, 0f, initialVelocity)
                velocity = if (done) 0f else decay.getVelocityFromNanos(playTime, 0f, initialVelocity)
                val delta = value - previous
                val consumed = scrollBy(delta)
                previous = value
                if (done || abs(delta - consumed) > 0.5f) break
            }
        } catch (_: CancellationException) {
            // Interrupted: report the speed at the last frame, like the default fling.
        }
        return velocity
    }
}

@Composable
internal fun rememberNuvioFlingBehavior(): FlingBehavior {
    if (!isDesktop) return ScrollableDefaults.flingBehavior()
    val density = LocalDensity.current
    return remember(density.density, density.fontScale) {
        PreciseFlingBehavior(FractionalMillisecondDecay(SplineBasedFloatDecayAnimationSpec(density)))
    }
}
