package com.nuvio.app.core.ui

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import okio.use

private const val MAX_FRAME_DIMENSION = 512

class SkiaGifDecoder(
    private val source: ImageSource,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val bytes = withContext(Dispatchers.IO) {
            source.source().use { okioSource ->
                okioSource.readByteArray()
            }
        }
        if (bytes.isEmpty()) return null

        return Data.makeFromBytes(bytes).use { data ->
            Codec.makeFromData(data).use { codec ->
                if (codec.frameCount <= 0 || codec.width <= 0 || codec.height <= 0) return null
                val w = codec.width
                val h = codec.height
                val scale = minOf(1f, MAX_FRAME_DIMENSION.toFloat() / w, MAX_FRAME_DIMENSION.toFloat() / h)
                val tw = (w * scale).toInt().coerceAtLeast(1)
                val th = (h * scale).toInt().coerceAtLeast(1)
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
                                        SamplingMode.LINEAR,
                                        null,
                                        true,
                                    )
                                }
                            }
                        }
                    } else {
                        codec.readPixels(bitmap, 0)
                    }
                    // Coil owns the returned bitmap; only temporary Skia resources are closed here.
                    DecodeResult(image = bitmap.asImage(), isSampled = needsScale)
                } catch (error: Throwable) {
                    bitmap.close()
                    throw error
                }
            }
        }
    }
    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val mimeType = result.mimeType
            val isGif = mimeType?.equals("image/gif", ignoreCase = true) == true ||
                result.source.fileOrNull()?.name?.endsWith(".gif", ignoreCase = true) == true
            if (!isGif) return null
            return SkiaGifDecoder(result.source)
        }
    }
}
