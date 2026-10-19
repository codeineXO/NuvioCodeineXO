package com.nuvio.app.core.ui

import coil3.BitmapImage
import coil3.decode.ImageSource
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
        for (width in listOf(4, 1024)) {
            val input = BufferedImage(width, width / 2, BufferedImage.TYPE_INT_RGB)
            input.createGraphics().apply {
                color = java.awt.Color.RED
                fillRect(0, 0, input.width, input.height)
                dispose()
            }
            val bytes = ByteArrayOutputStream().also { assertTrue(ImageIO.write(input, "gif", it)) }.toByteArray()
            ImageSource(Buffer().write(bytes), FileSystem.SYSTEM).use { source ->
                val result = assertNotNull(SkiaGifDecoder(source).decode())
                val bitmap = (result.image as BitmapImage).bitmap
                try {
                    assertEquals(minOf(width, 512), bitmap.width)
                    assertEquals(0xffff0000.toInt(), bitmap.getColor(0, 0))
                    assertEquals(width > 512, result.isSampled)
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
