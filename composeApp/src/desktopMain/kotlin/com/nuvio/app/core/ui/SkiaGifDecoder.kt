package com.nuvio.app.core.ui

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect

/**
 * Decodes GIFs (and, on request, animated WebPs) with Skia.
 *
 * By default only the first frame is decoded, downscaled to keep memory low. When the request sets
 * [NuvioAnimatedImageKey] the result is an [AnimatedSkiaImage] that [NuvioAsyncImage] plays back.
 */
class SkiaGifDecoder internal constructor(
    private val source: ImageSource,
    private val animate: Boolean = false,
    private val isGif: Boolean = true,
    private val requestedSize: Size = Size.ORIGINAL,
    private val requestedScale: Scale = Scale.FIT,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        // Peek so a decoder that declines (static WebP) leaves the source intact for the next decoder.
        val bytes = withContext(Dispatchers.IO) {
            source.source().peek().readByteArray()
        }
        if (bytes.isEmpty()) return null

        val data = Data.makeFromBytes(bytes)
        var dataTransferred = false
        try {
            Codec.makeFromData(data).use { codec ->
                val count = codec.frameCount
                if (count <= 0) return null
                // Read before any pixels are decoded: Skia reports a different count for some GIFs afterwards.
                val repetitionCount = codec.repetitionCount

                val w = codec.width
                val h = codec.height
                if (w <= 0 || h <= 0) return null

                val canAnimate = animate && count > 1 && w.toLong() * h <= MaxAnimatedImagePixels
                // Only GIFs are handled when not animating; other formats belong to Coil's default decoder.
                if (!isGif && !canAnimate) return null

                if (canAnimate) {
                    val result = decodeAnimated(codec, data, bytes, count, w, h, repetitionCount)
                    dataTransferred = true
                    return result
                }
                return decodeStill(codec, w, h)
            }
        } finally {
            if (!dataTransferred) data.close()
        }
    }

    private fun decodeAnimated(
        codec: Codec,
        data: Data,
        bytes: ByteArray,
        count: Int,
        w: Int,
        h: Int,
        repetitionCount: Int,
    ): DecodeResult {
        // framesInfo reads every frame in one native call; per-frame getFrameInfo(i) is unreliable in Skiko.
        val frames = codec.framesInfo
        val delays = IntArray(count) { frames[it].duration }
        val required = IntArray(count) { frames[it].requiredFrame }
        val firstFrame = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(w, h)) }
        try {
            codec.readPixels(firstFrame, 0)
            val image = AnimatedSkiaImage(
                data = data,
                width = w,
                height = h,
                frameDelaysMs = delays,
                requiredFrames = required,
                repetitionCount = repetitionCount,
                firstFrame = Image.makeFromBitmap(firstFrame),
                contentKey = contentKeyOf(bytes),
            )
            return DecodeResult(image = image, isSampled = false)
        } finally {
            firstFrame.close()
        }
    }

    private fun decodeStill(codec: Codec, w: Int, h: Int): DecodeResult {
        val target = desktopSourceDecodeSize(w, h, requestedSize, requestedScale)
        val tw = target.width
        val th = target.height
        val needsScale = tw != w || th != h
        val bitmap = Bitmap()
        try {
            bitmap.allocPixels(ImageInfo.makeN32Premul(tw, th))
            if (needsScale) {
                Bitmap().use { fullBitmap ->
                    fullBitmap.allocPixels(ImageInfo.makeN32Premul(w, h))
                    codec.readPixels(fullBitmap, 0)
                    Image.makeFromBitmap(fullBitmap).use { image ->
                        Canvas(bitmap).use { canvas ->
                            canvas.drawImageRect(
                                image,
                                Rect.makeWH(w.toFloat(), h.toFloat()),
                                Rect.makeWH(tw.toFloat(), th.toFloat()),
                                DesktopArtworkSampling,
                                null,
                                true,
                            )
                        }
                    }
                }
            } else {
                codec.readPixels(bitmap, 0)
            }
            // Coil owns the result; all temporary Skia resources are closed above.
            return DecodeResult(image = bitmap.asImage(), isSampled = needsScale)
        } catch (error: Throwable) {
            bitmap.close()
            throw error
        }
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val mimeType = result.mimeType
            val fileName = result.source.fileOrNull()?.name
            val isGif = mimeType?.equals("image/gif", ignoreCase = true) == true ||
                fileName?.endsWith(".gif", ignoreCase = true) == true
            val animate = options.extras[NuvioAnimatedImageKey] == true
            val isWebp = mimeType?.equals("image/webp", ignoreCase = true) == true ||
                fileName?.endsWith(".webp", ignoreCase = true) == true
            if (!isGif) {
                // Only animated WebP is claimed, so the many static WebP posters keep using Coil's decoder
                // without being read or parsed here.
                if (!animate || !isWebp || !result.source.isAnimatedWebp()) return null
            }
            return SkiaGifDecoder(result.source, animate = animate, isGif = isGif,
                requestedSize = options.size, requestedScale = options.scale)
        }
    }
}

/** Bounds decoded pixels by display requirements and real source detail, preserving aspect ratio. */
internal fun desktopSourceDecodeSize(width: Int, height: Int, size: Size, scale: Scale): IntSize {
    val ratios = listOfNotNull(
        (size.width as? Dimension.Pixels)?.px?.toDouble()?.div(width),
        (size.height as? Dimension.Pixels)?.px?.toDouble()?.div(height),
    )
    val ratio = (if (scale == Scale.FILL) ratios.maxOrNull() else ratios.minOrNull())
        ?.coerceAtMost(1.0) ?: 1.0
    return IntSize(ceil(width * ratio).toInt().coerceIn(1, width), ceil(height * ratio).toInt().coerceIn(1, height))
}

/** True when the RIFF/WebP header's VP8X chunk has the animation flag set. Reads 21 bytes without consuming. */
private fun ImageSource.isAnimatedWebp(): Boolean = runCatching {
    val peek = source().peek()
    if (!peek.request(HEADER_BYTES)) return@runCatching false
    val header = peek.readByteArray(HEADER_BYTES)
    fun matches(offset: Int, tag: String): Boolean =
        tag.indices.all { header[offset + it] == tag[it].code.toByte() }
    matches(0, "RIFF") && matches(8, "WEBP") && matches(12, "VP8X") &&
        header[20].toInt() and ANIMATION_FLAG != 0
}.getOrDefault(false)

private const val HEADER_BYTES = 21L
private const val ANIMATION_FLAG = 0x02

private fun contentKeyOf(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
