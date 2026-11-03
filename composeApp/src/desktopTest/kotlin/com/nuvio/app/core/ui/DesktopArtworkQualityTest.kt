package com.nuvio.app.core.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.maxBitmapSize
import coil3.size.Scale
import coil3.size.Size
import coil3.size.SizeResolver
import coil3.toBitmap
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

class DesktopArtworkQualityTest {
    @Test
    fun fineDetailIsFilteredDuringDecodeRatherThanAliased() = runBlocking {
        val bytes = artworkPng(256, 128)
        val loader = artworkLoader()
        try {
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(ByteBuffer.wrap(bytes)).size(64, 32).build()) as SuccessResult
            val bitmap = result.image.toBitmap()
            assertTrue(result.isSampled)
            assertEquals(64, bitmap.width)
            val filtered = (0 until bitmap.width).map { bitmap.getColor(it, 16) and 255 }
            assertTrue(filtered.any { it in 16..239 }, "Fine stripes should be blended, not selected with nearest sampling")
            Bitmap().use { nearest ->
                nearest.allocPixels(ImageInfo.makeN32Premul(64, 32))
                Image.makeFromEncoded(bytes).use { image ->
                    Canvas(nearest).use { it.drawImageRect(image, Rect.makeWH(256f, 128f), Rect.makeWH(64f, 32f), SamplingMode.DEFAULT, null, true) }
                }
                assertTrue((0 until 64).any { nearest.getColor(it, 16) != bitmap.getColor(it, 16) })
            }
        } finally { loader.shutdown() }
    }

    @Test
    fun decodedSizeRespectsCropFitSourceAndBitmapLimit() {
        assertEquals(androidx.compose.ui.unit.IntSize(200, 100), desktopRasterDecodeSize(800, 400, Size(100, 100), Scale.FILL, Size(4096, 4096)))
        assertEquals(androidx.compose.ui.unit.IntSize(100, 50), desktopRasterDecodeSize(800, 400, Size(100, 100), Scale.FIT, Size(4096, 4096)))
        assertEquals(androidx.compose.ui.unit.IntSize(80, 40), desktopRasterDecodeSize(80, 40, Size(1000, 1000), Scale.FILL, Size(4096, 4096)))
        assertEquals(androidx.compose.ui.unit.IntSize(128, 64), desktopRasterDecodeSize(800, 400, Size.ORIGINAL, Scale.FILL, Size(128, 96)))
    }

    @Test
    fun undersizedSourcesAreNotUpscaledAndLimitsAreEnforcedByLoader() = runBlocking {
        val loader = artworkLoader()
        try {
            val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(ByteBuffer.wrap(artworkPng(256, 128)))
            val full = loader.execute(request.size(1000, 1000).build()) as SuccessResult
            assertEquals(256, full.image.width)
            assertFalse(full.isSampled)
            val limited = loader.execute(request.size(Size.ORIGINAL).maxBitmapSize(Size(64, 48)).build()) as SuccessResult
            assertEquals(64, limited.image.width)
            assertEquals(32, limited.image.height)
            assertTrue(limited.isSampled)
        } finally { loader.shutdown() }
    }

    @Test
    fun thumbnailCannotSatisfyHeroButHeroCanSatisfyThumbnail() = runBlocking {
        val loader = artworkLoader()
        val encoded = artworkPng(1024, 576)
        suspend fun load(size: Size) = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
            .data(ByteBuffer.wrap(encoded)).memoryCacheKey("shared-artwork")
            .size(size).precision(coil3.size.Precision.INEXACT).build()) as SuccessResult
        try {
            val thumbnail = load(Size(128, 72))
            val hero = load(Size(768, 432))
            assertNotEquals(DataSource.MEMORY_CACHE, hero.dataSource)
            assertTrue(hero.image.width > thumbnail.image.width)
            val returnedThumbnail = load(Size(128, 72))
            assertEquals(DataSource.MEMORY_CACHE, returnedThumbnail.dataSource)
            assertSame(hero.image, returnedThumbnail.image)
            assertEquals("mitchell-v1", hero.memoryCacheKey?.extras?.get("nuvio.artwork.quality"))
        } finally { loader.shutdown() }
    }

    @Test
    fun explicitRequestsKeepTheirSizeAndFallbackMetadata() = runBlocking {
        val fallback = CustomPosterFallbackInterceptor.FALLBACK_URL_KEY
        val request = ImageRequest.Builder(PlatformContext.INSTANCE).data("https://provider.test/poster?signature=secret")
            .size(400, 600).memoryCacheKeyExtra(fallback, "https://provider.test/original").build()
        val sized = request.withDesktopHighQualitySize(PlatformContext.INSTANCE, SizeResolver(Size(1000, 1000))) as ImageRequest
        assertSame(request, sized)
        assertEquals(Size(400, 600), sized.sizeResolver.size())
        assertEquals(request.memoryCacheKeyExtras, sized.memoryCacheKeyExtras)
    }

    @Test
    fun densityAndWindowGrowthUpgradeBucketsWithoutShrinking() {
        val normal = desktopDecodeSize(Size(200, 300))
        assertEquals(Size(256, 384), normal)
        val dense = desktopDecodeSize(Size(400, 600))
        assertEquals(Size(512, 768), dense)
        assertEquals(dense, desktopArtworkUpgradeSize(normal, dense))
        assertEquals(dense, desktopArtworkUpgradeSize(dense, normal))
        assertTrue(heroArtworkHeadroom(600f, 1f) >= 1.3 * (800.0 / 600) * (800.0 / 600))
    }

    @Test
    fun tmdbUsesAdequateSourceWithoutDowngradingOrChangingCustomUrls() {
        fun select(path: String, role: ArtworkRole, size: Size, scale: Scale = Scale.FILL) =
            desktopArtworkSourceUrl("https://image.tmdb.org/t/p/$path", role, size, scale)
        assertEquals("https://image.tmdb.org/t/p/w780/p.jpg", select("w500/p.jpg", ArtworkRole.Poster, Size(600, 900)))
        assertEquals("https://image.tmdb.org/t/p/original/p.jpg", select("w500/p.jpg", ArtworkRole.Poster, Size(900, 1350)))
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", select("w780/b.jpg", ArtworkRole.Backdrop, Size(1000, 562)))
        assertEquals("https://image.tmdb.org/t/p/original/b.jpg", select("w1280/b.jpg", ArtworkRole.Backdrop, Size(1920, 1080)))
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", select("w1280/b.jpg", ArtworkRole.Backdrop, Size(100, 56)))
        assertEquals("https://image.tmdb.org/t/p/original/b.jpg", select("original/b.jpg", ArtworkRole.Backdrop, Size(100, 56)))
        assertEquals("https://image.tmdb.org/t/p/w780/p.jpg?sig=a%2Fb#x", select("w500/p.jpg?sig=a%2Fb#x", ArtworkRole.Poster, Size(600, 900)))
        for (url in listOf("https://provider.test/w500/p.jpg?key=a%2Fb", "https://image.tmdb.org.evil.test/t/p/w500/p.jpg")) {
            assertEquals(url, desktopArtworkSourceUrl(url, ArtworkRole.Poster, Size(4000, 6000), Scale.FILL))
        }
    }
}

internal fun artworkLoader(): ImageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
    .components { addPlatformDecoders() }
    .configurePlatformImageLoader()
    .build()

internal fun artworkPng(width: Int, height: Int): ByteArray = Bitmap().use { bitmap ->
    bitmap.allocPixels(ImageInfo.makeN32Premul(width, height))
    Canvas(bitmap).use { canvas ->
        canvas.clear(0xFFFFFFFF.toInt())
        Paint().use { paint ->
            paint.color = 0xFF000000.toInt()
            for (x in 0 until width step 2) canvas.drawRect(Rect.makeXYWH(x.toFloat(), 0f, 1f, height.toFloat()), paint)
        }
    }
    Image.makeFromBitmap(bitmap).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
    }
}
