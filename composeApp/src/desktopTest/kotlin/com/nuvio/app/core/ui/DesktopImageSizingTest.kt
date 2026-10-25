package com.nuvio.app.core.ui

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import coil3.size.SizeResolver
import coil3.size.Scale
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DesktopImageSizingTest {
    @Test
    fun cardRequestsUsePixelConstraintsWithHeadroom() {
        assertEquals(Size(Dimension.Pixels(250), Dimension.Pixels(375)),
            desktopDecodeSize(Size(Dimension.Pixels(200), Dimension.Pixels(300))))
    }

    @Test
    fun largeArtworkIsBoundedByDisplayRequirementsRatherThanAFixedCeiling() {
        assertEquals(Size(Dimension.Pixels(3000), Dimension.Pixels(1000)),
            desktopDecodeSize(Size(Dimension.Pixels(2400), Dimension.Pixels(800))))
        assertEquals(Size.ORIGINAL, desktopDecodeSize(Size.ORIGINAL))
        assertEquals(Size(Dimension.Pixels(500), Dimension.Undefined),
            desktopDecodeSize(Size(Dimension.Pixels(400), Dimension.Undefined)))
    }

    @Test
    fun fractionalPhysicalConstraintsRoundUpAndIncludeHoverHeadroom() {
        // 201 x 301 dp at 125% and 150%, rounded to physical layout pixels by Compose.
        assertEquals(Size(315, 472), desktopDecodeSize(Size(252, 377)))
        assertEquals(Size(378, 565), desktopDecodeSize(Size(302, 452)))
        assertEquals(Size(Int.MAX_VALUE, Int.MAX_VALUE), desktopDecodeSize(Size(Int.MAX_VALUE, Int.MAX_VALUE)))
    }

    @Test
    fun cropKeepsEnoughSourcePixelsInBothAxes() {
        assertEquals(IntSize(1125, 750), desktopSourceDecodeSize(3000, 2000, Size(500, 750), Scale.FILL))
        assertEquals(IntSize(500, 334), desktopSourceDecodeSize(3000, 2000, Size(500, 750), Scale.FIT))
        assertEquals(IntSize(1000, 1500), desktopSourceDecodeSize(2000, 3000, Size(1000, 500), Scale.FILL))
    }

    @Test
    fun animationFallbackUsesRequestedPhysicalSizeWithoutUpscalingSource() {
        assertEquals(IntSize(1920, 1080), desktopSourceDecodeSize(3840, 2160, Size(1920, 1080), Scale.FIT))
        assertEquals(IntSize(2560, 1440), desktopSourceDecodeSize(3840, 2160, Size(2560, 1440), Scale.FILL))
        assertEquals(IntSize(320, 180), desktopSourceDecodeSize(320, 180, Size(2560, 1440), Scale.FILL))
        assertEquals(IntSize(800, 450), desktopSourceDecodeSize(3840, 2160, Size(Dimension.Pixels(800), Dimension.Undefined), Scale.FILL))
    }

    @Test
    fun explicitCallerSizeIsPreserved() {
        val request = ImageRequest.Builder(PlatformContext.INSTANCE).data("poster").size(320, 480).build()
        assertSame(request, request.withDesktopHighQualitySize(PlatformContext.INSTANCE, SizeResolver.ORIGINAL))
    }

    @Test
    fun defaultRequestResolvesActualConstraints() = runBlocking {
        val resolver = object : SizeResolver {
            override suspend fun size() = Size(Dimension.Pixels(400), Dimension.Pixels(600))
        }
        val request = "poster".withDesktopHighQualitySize(PlatformContext.INSTANCE, resolver) as ImageRequest
        assertEquals(Size(Dimension.Pixels(500), Dimension.Pixels(750)), request.sizeResolver.size())
    }

    @Test
    fun cropRequestUsesFillAndPreservesExplicitScale() {
        val context = PlatformContext.INSTANCE
        val crop = "poster".withDesktopHighQualitySize(context, SizeResolver.ORIGINAL, Scale.FILL) as ImageRequest
        assertEquals(Scale.FILL, crop.scale)
        val explicit = ImageRequest.Builder(context).data("poster").scale(Scale.FIT).build()
        val sized = explicit.withDesktopHighQualitySize(context, SizeResolver.ORIGINAL, Scale.FILL) as ImageRequest
        assertEquals(Scale.FIT, sized.scale)
    }
}
