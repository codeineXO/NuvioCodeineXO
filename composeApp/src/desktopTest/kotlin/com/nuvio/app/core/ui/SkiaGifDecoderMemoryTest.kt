package com.nuvio.app.core.ui

import coil3.BitmapImage
import coil3.decode.ImageSource
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkiaGifDecoderMemoryTest {
    @Test
    fun returnedPixelsSurviveClosingDecoderResources() = runBlocking {
        for ((width, requestedSize, expectedWidth) in listOf(
            Triple(4, Size.ORIGINAL, 4),
            Triple(1024, Size.ORIGINAL, 1024),
            Triple(1024, Size(512, 256), 512),
        )) {
            val input = BufferedImage(width, width / 2, BufferedImage.TYPE_INT_RGB)
            input.createGraphics().apply {
                color = java.awt.Color.RED
                fillRect(0, 0, input.width, input.height)
                dispose()
            }
            val bytes = ByteArrayOutputStream().also { assertTrue(ImageIO.write(input, "gif", it)) }.toByteArray()
            ImageSource(Buffer().write(bytes), FileSystem.SYSTEM).use { source ->
                val result = assertNotNull(SkiaGifDecoder(source, requestedSize = requestedSize).decode())
                val bitmap = (result.image as BitmapImage).bitmap
                try {
                    assertEquals(expectedWidth, bitmap.width)
                    assertEquals(expectedWidth / 2, bitmap.height)
                    assertEquals(0xffff0000.toInt(), bitmap.getColor(0, 0))
                    assertEquals(expectedWidth != width, result.isSampled)
                } finally {
                    bitmap.close()
                }
            }
            input.flush()
        }
    }

    @Test
    fun emptyAndInvalidInputsReleaseSource(): Unit = runBlocking {
        ImageSource(Buffer(), FileSystem.SYSTEM).use { assertNull(SkiaGifDecoder(it).decode()) }
        ImageSource(Buffer().writeUtf8("invalid GIF"), FileSystem.SYSTEM).use {
            assertFails { SkiaGifDecoder(it).decode() }
        }
    }
}
