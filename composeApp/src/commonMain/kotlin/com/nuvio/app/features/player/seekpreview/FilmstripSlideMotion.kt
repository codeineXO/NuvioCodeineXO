package com.nuvio.app.features.player.seekpreview

import androidx.compose.animation.core.Animatable
import com.nuvio.app.core.ui.softScrollAnimationSpec
import kotlin.math.abs

/** Rebase overlapping thumbnails without moving them on screen when a new cue arrives. */
internal class FilmstripSlideMotion {
    val slide = Animatable(0f)
    private var retainedVelocity = 0f

    suspend fun moveBy(steps: Int, sideFrames: Int) {
        if (steps == 0 || abs(steps.toLong()) > sideFrames || sideFrames <= 0) {
            retainedVelocity = 0f
            slide.snapTo(0f)
            return
        }
        val velocity = retainedVelocity
        slide.snapTo(slide.value + steps)
        slide.animateTo(0f, softScrollAnimationSpec(), initialVelocity = velocity) {
            retainedVelocity = this.velocity
        }
        retainedVelocity = 0f
    }
}
