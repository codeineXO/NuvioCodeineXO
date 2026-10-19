package com.nuvio.app.core.ui

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import coil3.size.SizeResolver
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
    fun artworkAndUnboundedRequestsStayWithinSourceLimit() {
        assertEquals(Size(Dimension.Pixels(1536), Dimension.Pixels(1000)),
            desktopDecodeSize(Size(Dimension.Pixels(2400), Dimension.Pixels(800))))
        assertEquals(Size(Dimension.Pixels(1536), Dimension.Pixels(1536)), desktopDecodeSize(Size.ORIGINAL))
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
}
