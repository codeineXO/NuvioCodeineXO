package com.nuvio.app.core.ui

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Rule

/** Offscreen pixel comparisons, not validation of a live window or display. */
class DesktopImageRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sharedWrapperMatchesDirectHighQualityPainterWithIdenticalDecodedBytes() {
        val bytes = letteringFixture(500, 750)
        Image.makeFromEncoded(bytes).use { image ->
            Bitmap.makeFromImage(image).use { bitmap ->
                val loaded = AtomicBoolean()
                compose.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        Row {
                            NuvioAsyncImage(
                                model = ImageRequest.Builder(PlatformContext.INSTANCE).data(bytes).size(500, 750).build(),
                                contentDescription = null,
                                modifier = Modifier.size(250.dp, 375.dp).testTag("wrapper"),
                                onSuccess = {
                                    kotlin.test.assertEquals(500, it.result.image.width)
                                    kotlin.test.assertEquals(750, it.result.image.height)
                                    loaded.set(true)
                                },
                            )
                            ComposeImage(bitmap.asComposeImageBitmap(), null,
                                Modifier.size(250.dp, 375.dp).testTag("direct"), filterQuality = FilterQuality.High)
                        }
                    }
                }
                compose.waitUntil(8_000) { loaded.get() }
                val actual = compose.onNodeWithTag("wrapper").captureToImage().toPixelMap()
                val expected = compose.onNodeWithTag("direct").captureToImage().toPixelMap()
                kotlin.test.assertEquals(expected.width, actual.width)
                kotlin.test.assertEquals(expected.height, actual.height)
                for (y in 0 until actual.height) for (x in 0 until actual.width) {
                    kotlin.test.assertEquals(expected[x, y], actual[x, y], "Pixel $x,$y")
                }
            }
        }
    }

    @Test
    fun decodedArtworkCoversPhysicalDisplayAndCropWithoutLoadingFullOriginals() = runBlocking {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            val bytes = letteringFixture(3840, 2160)
            for ((physicalWidth, physicalHeight, scale) in listOf(
                Triple(1920, 1080, Scale.FIT), Triple(2560, 1440, Scale.FILL), Triple(500, 750, Scale.FILL),
            )) {
                val request = bytes.withDesktopHighQualitySize(PlatformContext.INSTANCE,
                    coil3.size.SizeResolver(coil3.size.Size(physicalWidth, physicalHeight)), scale) as ImageRequest
                val result = loader.execute(request)
                assertTrue(result is SuccessResult, result.toString())
                val image = result.image
                println("physical=${physicalWidth}x$physicalHeight decoded=${image.width}x${image.height} source=3840x2160 scale=$scale")
                assertTrue(image.width >= physicalWidth && image.height >= physicalHeight)
                assertTrue(image.width < 3840 && image.height < 2160, "Do not decode the full original for these display sizes")
            }
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun gifStillAndAnimationFallbackRespectLargeExplicitRequests() = runBlocking {
        val file = java.nio.file.Files.createTempFile("desktop-image-quality-", ".gif").toFile()
        val fixture = BufferedImage(2400, 1600, BufferedImage.TYPE_INT_RGB)
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        ImageIO.createImageOutputStream(file).use { output ->
            writer.output = output
            writer.prepareWriteSequence(null)
            repeat(2) { writer.writeToSequence(javax.imageio.IIOImage(fixture, null, null), null) }
            writer.endWriteSequence()
        }
        writer.dispose()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).components { add(SkiaGifDecoder.Factory()) }.build()
        try {
            for (animate in listOf(false, true)) {
                val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(file).size(1200, 800)
                    .apply { extras[NuvioAnimatedImageKey] = animate }
                    .memoryCacheKeyExtra("test.animation", animate.toString()).build()
                val result = loader.execute(request)
                assertTrue(result is SuccessResult, result.toString())
                kotlin.test.assertEquals(1200, result.image.width)
                kotlin.test.assertEquals(800, result.image.height)
                assertTrue(result.image !is AnimatedSkiaImage, "An animation above the playback pixel budget uses a correctly sized static fallback")
            }
        } finally {
            loader.shutdown()
            file.delete()
        }
    }

    @Test
    fun identicalDecodedBytesLoseEdgeDetailInLegacyWindowsResampling() {
        var improvedCards = 0
        // Logical card widths at 1080p, 125%, 150%, and 200% desktop scaling.
        for ((width, height) in listOf(200 to 300, 250 to 375, 300 to 450, 400 to 600, 1920 to 1080, 2560 to 1440)) {
            Image.makeFromEncoded(letteringFixture(width * 2, height * 2)).use { source ->
                val direct = renderPainter(source, width, height)
                val animated = render(source, width, height, DesktopArtworkSampling)
                val maxDifference = direct.indices.maxOf { abs((direct[it] and 255) - (animated[it] and 255)) }
                // Skia's Image and Bitmap entry points can round a half-channel value differently.
                assertTrue(maxDifference <= 1, "Static/animated high-quality sampling differs by $maxDifference channel levels")
                val legacy = legacy(source, width, height)
                val directDetail = edgeDetail(direct, width, height)
                val legacyDetail = edgeDetail(legacy, width, height)
                println("physical=${width}x$height decoded=${source.width}x${source.height} direct=$directDetail legacy=$legacyDetail")
                if (width.toLong() * height <= 1_250_000L) {
                    if (directDetail > legacyDetail * 1.01) improvedCards++
                } else {
                    assertTrue(abs(directDetail - legacyDetail) < 0.1, "Large artwork already bypassed the legacy painter")
                }
            }
        }
        assertTrue(improvedCards >= 2, "Direct high-quality rendering must retain more detail at the fractional card sizes")
    }

    private fun renderPainter(source: Image, width: Int, height: Int): IntArray =
        Bitmap.makeFromImage(source).use { bitmap ->
            val painter = BitmapPainter(bitmap.asComposeImageBitmap(), filterQuality = FilterQuality.High)
            val target = androidx.compose.ui.graphics.ImageBitmap(width, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(target), Size(width.toFloat(), height.toFloat())) {
                with(painter) { draw(size) }
            }
            target.asSkiaBitmap().use { result ->
                IntArray(width * height) { result.getColor(it % width, it / width) }
            }
        }

    private fun legacy(source: Image, width: Int, height: Int): IntArray {
        // Exact old painter path, including quantum rounding and its large-image bypass.
        if (width.toLong() * height > 1_250_000L) return render(source, width, height, DesktopArtworkSampling)
        val quantum = when { maxOf(width, height) >= 1200 -> 16; maxOf(width, height) >= 600 -> 8; maxOf(width, height) >= 200 -> 4; else -> 2 }
        val cw = (width + quantum - 1) / quantum * quantum
        val ch = (height + quantum - 1) / quantum * quantum
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(cw, ch)
            source.scalePixels(bitmap.peekPixels()!!, FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), false)
            Image.makeFromBitmap(bitmap).use { scaled ->
                return render(scaled, width, height, if (cw == width && ch == height) SamplingMode.DEFAULT else FilterMipmap(FilterMode.LINEAR, MipmapMode.NEAREST))
            }
        }
    }

    private fun render(source: Image, width: Int, height: Int, sampling: SamplingMode): IntArray =
        Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.drawImageRect(source, Rect.makeWH(source.width.toFloat(), source.height.toFloat()), Rect.makeWH(width.toFloat(), height.toFloat()), sampling, null, true)
            surface.makeImageSnapshot().use { image ->
                Bitmap.makeFromImage(image).use { bitmap -> IntArray(width * height) { bitmap.getColor(it % width, it / width) } }
            }
        }

    private fun edgeDetail(pixels: IntArray, width: Int, height: Int): Double {
        var sum = 0L
        for (y in 1 until height) for (x in 1 until width) {
            val value = pixels[y * width + x] and 255
            sum += abs(value - (pixels[y * width + x - 1] and 255))
            sum += abs(value - (pixels[(y - 1) * width + x] and 255))
        }
        return sum.toDouble() / (width * height)
    }

    private fun letteringFixture(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = Color.BLACK
        // Fixed bitmap lettering avoids host fonts and font rasterizer differences.
        val letters = listOf("111101101101111", "110101110101110", "111100110100111")
        for (y in 0 until height step 29) for (x in 0 until width step 23) {
            val glyph = letters[(x / 23 + y / 29) % letters.size]
            glyph.forEachIndexed { i, bit -> if (bit == '1') g.fillRect(x + i % 3 * 3, y + i / 3 * 3, 3, 3) }
            g.drawLine(x + 12, y, x + 19, y + 21)
        }
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
