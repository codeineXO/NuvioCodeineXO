package com.nuvio.app.core.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp

internal object PosterOpenMotion {
    val DurationMillis: Int get() = navigationMotionDuration(550)
    val ContentDelayMillis: Int get() = navigationMotionDuration(260)
    private val expansion = NuvioTokens.Motion.standard
    private val backgroundRecede = NuvioTokens.Motion.standard
    private val dissolve = CubicBezierEasing(0.146f, 0f, 0.538f, 1f)
    private val backgroundDissolve = CubicBezierEasing(0.061f, 0f, 0.435f, 1f)
    private val blur = CubicBezierEasing(0.718f, 0.392f, 0.569f, 1f)

    fun bounds(start: Rect, end: Rect, elapsedMillis: Float): Rect =
        lerp(start, end, expansionProgress(elapsedMillis))

    fun backgroundScale(elapsedMillis: Float): Float =
        1f - 0.1f * backgroundRecede.transform((elapsedMillis / DurationMillis).coerceIn(0f, 1f))

    fun artworkAlpha(elapsedMillis: Float): Float =
        1f - dissolve.transform(((elapsedMillis - ContentDelayMillis) / (DurationMillis - ContentDelayMillis)).coerceIn(0f, 1f))

    fun backgroundAlpha(elapsedMillis: Float): Float =
        1f - backgroundDissolve.transform((elapsedMillis / navigationMotionDuration(300)).coerceIn(0f, 1f))

    fun artworkBlurFraction(elapsedMillis: Float): Float =
        0.14f * blur.transform((elapsedMillis / navigationMotionDuration(200)).coerceIn(0f, 1f))

    fun artworkBlurRadius(artwork: Size, bounds: Size, elapsedMillis: Float, maximumScreenSigmaPx: Float = 16f): Size {
        if (artwork.width <= 0f || artwork.height <= 0f || bounds.width <= 0f || bounds.height <= 0f) return Size.Zero
        val fraction = artworkBlurFraction(elapsedMillis)
        val maximum = maximumScreenSigmaPx.coerceAtLeast(0f)
        return Size(
            blurRadiusForSigma(minOf(artwork.width * fraction, maximum * artwork.width / bounds.width)),
            blurRadiusForSigma(minOf(artwork.height * bounds.width / bounds.height * fraction, maximum * artwork.height / bounds.height)),
        )
    }

    fun backgroundBlurSigmaDp(elapsedMillis: Float): Float {
        val progress = (elapsedMillis / navigationMotionDuration(200)).coerceIn(0f, 1f)
        return 16f * progress * progress
    }

    fun blurRadiusForSigma(sigma: Float): Float =
        ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0f)

    private fun expansionProgress(elapsedMillis: Float): Float =
        expansion.transform((elapsedMillis / DurationMillis).coerceIn(0f, 1f))
}
