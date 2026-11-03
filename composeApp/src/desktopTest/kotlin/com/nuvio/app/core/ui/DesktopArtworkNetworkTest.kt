package com.nuvio.app.core.ui

import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.svg.SvgDecoder
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopArtworkNetworkTest {
    @Test
    fun customFallbackAndSvgImagesKeepNetworkFetchingAndHighQualityDecoding() = runBlocking {
        val png = artworkPng(1024, 576)
        val received = CopyOnWriteArrayList<String>()
        val samples = CopyOnWriteArrayList<ArtworkTiming>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val root = "http://127.0.0.1:${server.address.port}"
        server.createContext("/") { exchange ->
            received.add(exchange.requestURI.toString())
            val path = exchange.requestURI.path
            val body = when (path) {
                "/missing" -> byteArrayOf()
                "/poster.svg" -> """<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="576"><image href="$root/source.png" width="1024" height="576"/></svg>""".encodeToByteArray()
                else -> png
            }
            exchange.responseHeaders.add("Content-Type", if (path.endsWith(".svg")) "image/svg+xml" else "image/png")
            exchange.responseHeaders.add("Cache-Control", "public, max-age=3600")
            exchange.sendResponseHeaders(if (path == "/missing") 404 else 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(CustomPosterFallbackInterceptor())
                addPlatformDecoders()
                add(SvgDecoder.Factory())
                add(coil3.network.ktor3.KtorNetworkFetcherFactory(
                    cacheStrategy = { coil3.network.cachecontrol.CacheControlCacheStrategy() }))
            }
            .eventListenerFactory { TimingListener(samples) }
            .configurePlatformImageLoader()
            .build()
        try {
            assertEquals(256L * 1024 * 1024, loader.memoryCache!!.maxSize)
            val fallback = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                .data("$root/missing?signature=a%2Fb")
                .memoryCacheKeyExtra(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY, "$root/source.png?signature=c%2Fd")
                .size(128, 72).build()) as SuccessResult
            assertEquals(listOf("/missing?signature=a%2Fb", "/source.png?signature=c%2Fd"), received.toList())
            assertEquals(128, fallback.image.width)
            assertTrue(fallback.isSampled)
            val svg = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                .data("$root/poster.svg").size(128, 72).build()) as SuccessResult
            assertEquals(128, svg.image.width)
            assertTrue(received.contains("/source.png"))
            assertTrue(samples.any { it.decoder == "DesktopRasterDecoder" })
            assertTrue(samples.any { it.decoder == "SvgEmbeddedImageDecoder" })

            suspend fun load(size: Size) = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                .data("$root/source.png").size(size).precision(coil3.size.Precision.INEXACT).build()) as SuccessResult
            val hero = load(Size(768, 432))
            assertNotEquals(DataSource.MEMORY_CACHE, hero.dataSource)
            val beforeWarmRequests = received.size
            val retainedBytes = loader.memoryCache!!.size
            repeat(20) {
                assertEquals(DataSource.MEMORY_CACHE, load(Size(128, 72)).dataSource)
                assertEquals(DataSource.MEMORY_CACHE, load(Size(768, 432)).dataSource)
            }
            assertEquals(beforeWarmRequests, received.size)
            assertEquals(retainedBytes, loader.memoryCache!!.size)
            assertTrue(loader.memoryCache!!.size <= loader.memoryCache!!.maxSize)
            assertTrue(samples.any { it.fetchNanos > 0 && it.decodeNanos > 0 })
            assertTrue(samples.filter { it.origin == DataSource.MEMORY_CACHE }
                .all { it.fetchNanos == 0L && it.decodeNanos == 0L })
        } finally {
            loader.shutdown()
            server.stop(0)
        }
    }
}

private data class ArtworkTiming(
    val requested: Size?, val width: Int, val height: Int, val sampled: Boolean,
    val origin: DataSource, val decoder: String?, val fetchNanos: Long, val decodeNanos: Long,
)

private class TimingListener(private val samples: MutableList<ArtworkTiming>) : EventListener() {
    private var requested: Size? = null
    private var fetchStarted = 0L
    private var decodeStarted = 0L
    private var fetchNanos = 0L
    private var decodeNanos = 0L
    private var decoderName: String? = null
    override fun resolveSizeEnd(request: ImageRequest, size: Size) { requested = size }
    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) { fetchStarted = System.nanoTime() }
    override fun fetchEnd(request: ImageRequest, fetcher: Fetcher, options: Options, result: FetchResult?) {
        fetchNanos = System.nanoTime() - fetchStarted
    }
    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        decodeStarted = System.nanoTime()
        decoderName = decoder.javaClass.simpleName
    }
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) {
        decodeNanos = System.nanoTime() - decodeStarted
    }
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        samples.add(ArtworkTiming(requested, result.image.width, result.image.height, result.isSampled,
            result.dataSource, decoderName, fetchNanos, decodeNanos))
    }
}
