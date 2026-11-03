package com.nuvio.app.core.ui

import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import kotlin.coroutines.coroutineContext

/** Static raster fallback. Specialized SVG/GIF/animated WebP decoders retain precedence. */
internal class DesktopRasterDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = withContext(Dispatchers.IO) { source.source().readByteArray() }
        coroutineContext.ensureActive()
        Image.makeFromEncoded(bytes).use { image ->
            val target = desktopRasterDecodeSize(image.width, image.height, options.size, options.scale, options.maxBitmapSize)
            val bitmap = Bitmap()
            try {
                check(bitmap.allocPixels(ImageInfo.makeN32Premul(target.width, target.height)))
                Canvas(bitmap).use { canvas ->
                    canvas.drawImageRect(image,
                        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                        Rect.makeWH(target.width.toFloat(), target.height.toFloat()),
                        DesktopArtworkSampling, null, true)
                }
                coroutineContext.ensureActive()
                bitmap.setImmutable()
                return DecodeResult(bitmap.asImage(), target.width < image.width || target.height < image.height)
            } catch (error: Throwable) {
                bitmap.close()
                throw error
            }
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val source = result.source.source().peek()
            val header = source.readByteArray(minOf(32L, if (source.request(32)) 32L else source.buffer.size))
            val raster = header.size >= 2 && (
                (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) ||
                    header.take(4) == listOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()) ||
                    header.decodeToString().let {
                        it.startsWith("GIF8") || it.startsWith("BM") ||
                            (it.length >= 12 && it.startsWith("RIFF") && it.substring(8, 12) == "WEBP") ||
                            (it.length >= 12 && it.substring(4, 8) == "ftyp" &&
                                it.substring(8, 12) in listOf("avif", "avis", "heic", "heix", "mif1"))
                    })
            return if (raster) DesktopRasterDecoder(result.source, options) else null
        }
    }
}

internal fun desktopRasterDecodeSize(width: Int, height: Int, size: Size, scale: Scale, maxSize: Size): IntSize {
    val target = desktopSourceDecodeSize(width, height, size, scale)
    val limit = minOf(1.0,
        (maxSize.width as? Dimension.Pixels)?.px?.toDouble()?.div(target.width) ?: 1.0,
        (maxSize.height as? Dimension.Pixels)?.px?.toDouble()?.div(target.height) ?: 1.0)
    return IntSize((target.width * limit).toInt().coerceAtLeast(1), (target.height * limit).toInt().coerceAtLeast(1))
}
