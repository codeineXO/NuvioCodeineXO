package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule

class DesktopArtworkResizeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun windowAndDensityGrowthReloadButShrinkingKeepsAdequateArtwork() {
        val loader = artworkLoader()
        val model = ByteBuffer.wrap(artworkPng(1024, 1024))
        val successes = CopyOnWriteArrayList<SuccessResult>()
        val retainedDuringLoading = CopyOnWriteArrayList<Boolean>()
        var edge by mutableStateOf(64.dp)
        var density by mutableStateOf(1f)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    NuvioAsyncImage(
                        model = rememberArtworkRequest(model, ArtworkRole.Poster),
                        contentDescription = null,
                        modifier = Modifier.size(edge),
                        contentScale = ContentScale.Crop,
                        imageLoader = loader,
                        onLoading = { if (successes.isNotEmpty()) retainedDuringLoading.add(it.painter != null) },
                        onSuccess = { successes.add(it.result) },
                    )
                }
            }
            compose.waitUntil(10_000) { successes.isNotEmpty() }
            val initialWidth = successes.last().image.width
            compose.runOnIdle { edge = 160.dp }
            compose.waitUntil(10_000) { successes.last().image.width > initialWidth }
            val enlargedWidth = successes.last().image.width
            compose.runOnIdle { density = 1.5f }
            compose.waitUntil(10_000) { successes.last().image.width > enlargedWidth }
            val fractionalDensityWidth = successes.last().image.width
            compose.runOnIdle { density = 2f }
            compose.waitUntil(10_000) { successes.last().image.width > fractionalDensityWidth }
            assertTrue(retainedDuringLoading.isNotEmpty())
            assertTrue(retainedDuringLoading.all { it }, "Resize loads must retain the previous painter")
            val count = successes.size
            compose.runOnIdle { edge = 32.dp }
            compose.waitForIdle()
            Thread.sleep(250)
            assertEquals(count, successes.size)
        } finally { loader.shutdown() }
    }

    @Test
    fun explicitlySizedRequestDoesNotReloadOnWindowGrowth() {
        val loader = artworkLoader()
        val model = ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
            .data(ByteBuffer.wrap(artworkPng(512, 512))).size(128, 128).build()
        val successes = CopyOnWriteArrayList<SuccessResult>()
        var edge by mutableStateOf(64.dp)
        try {
            compose.setContent {
                NuvioAsyncImage(model = model, contentDescription = null, modifier = Modifier.size(edge),
                    imageLoader = loader, onSuccess = { successes.add(it.result) })
            }
            compose.waitUntil(10_000) { successes.isNotEmpty() }
            assertEquals(128, successes.single().image.width)
            compose.runOnIdle { edge = 256.dp }
            compose.waitForIdle()
            Thread.sleep(250)
            assertEquals(1, successes.size)
        } finally { loader.shutdown() }
    }
}
