package com.nuvio.app.core.ui

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import kotlin.math.ceil

/** Exact 2x2 box average when halving; trilinear mipmaps for shrinking at draw time. */
private val HalvingSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
private val MipmapSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

/**
 * A cubic filter only reads a 4x4 neighbourhood, so shrinking by more than 2x skips source pixels and
 * aliases (small badges and logos look pixelated). Halve with a box filter until one cubic pass is
 * within 2x of [targetWidth] x [targetHeight]. Returns [image] itself when no halving is needed;
 * otherwise the caller owns and must close the returned image.
 */
internal fun downsampleForCubic(image: Image, targetWidth: Float, targetHeight: Float): Image {
    var current = image
    while (current.width / 2f >= targetWidth && current.height / 2f >= targetHeight &&
        current.width >= 2 && current.height >= 2
    ) {
        val w = ceil(current.width / 2f).toInt()
        val h = ceil(current.height / 2f).toInt()
        val half = Bitmap()
        try {
            check(half.allocPixels(ImageInfo.makeN32Premul(w, h)))
            Canvas(half).use { canvas ->
                canvas.drawImageRect(
                    current,
                    Rect.makeWH(current.width.toFloat(), current.height.toFloat()),
                    Rect.makeWH(w.toFloat(), h.toFloat()),
                    HalvingSampling,
                    null,
                    true,
                )
            }
            val next = Image.makeFromBitmap(half)
            if (current !== image) current.close()
            current = next
        } finally {
            half.close()
        }
    }
    return current
}

/** Draws [image] into [dst] with the cubic artwork filter, box-downsampling first when shrinking past 2x. */
internal fun Canvas.drawArtworkRect(image: Image, src: Rect, dst: Rect, paint: Paint? = null) {
    val scaleX = dst.width / src.width
    val scaleY = dst.height / src.height
    if (scaleX >= 0.5f && scaleY >= 0.5f) {
        drawImageRect(image, src, dst, DesktopArtworkSampling, paint, true)
        return
    }
    val full = src.left == 0f && src.top == 0f &&
        src.width == image.width.toFloat() && src.height == image.height.toFloat()
    if (!full) {
        drawImageRect(image, src, dst, MipmapSampling, paint, true)
        return
    }
    val reduced = downsampleForCubic(image, dst.width, dst.height)
    try {
        drawImageRect(
            reduced,
            Rect.makeWH(reduced.width.toFloat(), reduced.height.toFloat()),
            dst,
            DesktopArtworkSampling,
            paint,
            true,
        )
    } finally {
        if (reduced !== image) reduced.close()
    }
}

/** Per-frame draw: mipmapped sampling instead of cubic when shrinking past 2x, so nothing aliases. */
internal fun artworkDrawSampling(srcWidth: Int, srcHeight: Int, dstWidth: Float, dstHeight: Float): SamplingMode =
    if (dstWidth / srcWidth < 0.5f || dstHeight / srcHeight < 0.5f) MipmapSampling else DesktopArtworkSampling
