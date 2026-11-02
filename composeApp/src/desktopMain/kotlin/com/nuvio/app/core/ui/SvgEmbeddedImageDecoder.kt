package com.nuvio.app.core.ui

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import kotlin.math.ceil
import coil3.toBitmap
import java.net.URLDecoder
import java.util.Base64
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGDOM

private val imageTag = Regex("<image\\b[^>]*>", RegexOption.IGNORE_CASE)
private val rootTag = Regex("<svg\\b[^>]*>", RegexOption.IGNORE_CASE)
private val separators = Regex("[\\s,]+")
private val attributePatterns = listOf("viewBox", "width", "height", "href", "xlink:href", "x", "y", "preserveAspectRatio")
    .associateWith { name -> Regex("(?<![\\w:-])${Regex.escape(name)}\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')") }

private class EmbeddedImage(
    val href: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val preserveAspectRatio: String,
)

internal suspend fun renderSvgWithImages(
    svg: String,
    requestedSize: Size = Size.ORIGINAL,
    requestedScale: Scale = Scale.FIT,
    loadImage: suspend (String) -> Image?,
): Bitmap? = renderSvgWithSizedImages(svg, requestedSize, requestedScale) { href, _, _ -> loadImage(href) }

private data class EmbeddedRequest(val width: Int, val height: Int, val scale: Scale)

internal suspend fun renderSvgWithSizedImages(
    svg: String,
    requestedSize: Size = Size.ORIGINAL,
    requestedScale: Scale = Scale.FIT,
    loadImage: suspend (String, Size, Scale) -> Image?,
): Bitmap? {
    val tags = imageTag.findAll(svg).map { it.value }.toList()
    val stripped = svg.replace(imageTag, "").replace("</image>", "", ignoreCase = true)
    val root = rootTag.find(stripped)?.value ?: return null

    val viewBox = attr(root, "viewBox")
        ?.trim()
        ?.split(separators)
        ?.mapNotNull { it.toFloatOrNull() }
        ?.takeIf { it.size == 4 }
    val width = length(attr(root, "width")) ?: viewBox?.get(2) ?: return null
    val height = length(attr(root, "height")) ?: viewBox?.get(3) ?: return null
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
    val box = viewBox ?: listOf(0f, 0f, width, height)
    if (box[2] <= 0f || box[3] <= 0f) return null
    val sx = width / box[2]
    val sy = height / box[3]

    val images = tags.mapNotNull { tag ->
        val href = (attr(tag, "href") ?: attr(tag, "xlink:href"))?.replace("&amp;", "&") ?: return@mapNotNull null
        EmbeddedImage(
            href = href,
            x = length(attr(tag, "x")) ?: 0f,
            y = length(attr(tag, "y")) ?: 0f,
            width = length(attr(tag, "width")) ?: return@mapNotNull null,
            height = length(attr(tag, "height")) ?: return@mapNotNull null,
            preserveAspectRatio = attr(tag, "preserveAspectRatio").orEmpty(),
        )
    }
    val ratios = listOfNotNull(
        (requestedSize.width as? Dimension.Pixels)?.px?.div(width),
        (requestedSize.height as? Dimension.Pixels)?.px?.div(height),
    )
    val scale = (if (requestedScale == Scale.FILL) ratios.maxOrNull() else ratios.minOrNull()) ?: 1f
    if (!scale.isFinite() || scale <= 0f) return null
    val requests = linkedMapOf<String, EmbeddedRequest>()
    for (image in images) {
        val requestedWidth = ceil(image.width * sx * scale).toInt().coerceAtLeast(1)
        val requestedHeight = ceil(image.height * sy * scale).toInt().coerceAtLeast(1)
        val imageScale = if (image.preserveAspectRatio.contains("slice") || image.preserveAspectRatio.trim() == "none") Scale.FILL else Scale.FIT
        val previous = requests[image.href]
        requests[image.href] = EmbeddedRequest(
            maxOf(requestedWidth, previous?.width ?: 0),
            maxOf(requestedHeight, previous?.height ?: 0),
            if (previous?.scale == Scale.FILL) Scale.FILL else imageScale,
        )
    }
    val loaded = ConcurrentHashMap<String, Image>()
    try {
        coroutineScope {
            requests.map { (href, request) -> async {
                loadImage(href, Size(request.width, request.height), request.scale)?.let { loaded[href] = it }
            } }.forEach { it.await() }
        }
        val bitmap = Bitmap().apply {
            allocPixels(ImageInfo.makeN32Premul(ceil(width * scale).toInt().coerceAtLeast(1), ceil(height * scale).toInt().coerceAtLeast(1)))
        }
        try {
            Data.makeFromBytes(stripped.encodeToByteArray()).use { data ->
                SVGDOM(data).use { dom ->
                    Canvas(bitmap).use { canvas ->
                        dom.setContainerSize(width, height)
                        canvas.scale(scale, scale)
                        dom.render(canvas)
                        images.forEach { embedded ->
                            val image = loaded[embedded.href] ?: return@forEach
                            val dx = (embedded.x - box[0]) * sx
                            val dy = (embedded.y - box[1]) * sy
                            val dw = embedded.width * sx
                            val dh = embedded.height * sy
                            val dst = fit(image.width.toFloat(), image.height.toFloat(), dx, dy, dw, dh, embedded.preserveAspectRatio)
                            canvas.drawImageRect(
                                image,
                                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                                dst,
                                DesktopArtworkSampling,
                                null,
                                true,
                            )
                        }
                    }
                }
            }
            return bitmap
        } catch (error: Throwable) {
            bitmap.close()
            throw error
        }
    } finally {
        loaded.values.forEach { it.close() }
    }
}

private fun fit(iw: Float, ih: Float, x: Float, y: Float, w: Float, h: Float, preserveAspectRatio: String): Rect {
    val parts = preserveAspectRatio.trim().split(separators).filter { it.isNotEmpty() && it != "defer" }
    val align = parts.firstOrNull() ?: "xMidYMid"
    if (align == "none") return Rect.makeXYWH(x, y, w, h)
    val slice = parts.getOrNull(1) == "slice"
    val ratioW = w / iw
    val ratioH = h / ih
    val s = if (slice) maxOf(ratioW, ratioH) else minOf(ratioW, ratioH)
    val fw = iw * s
    val fh = ih * s
    val ox = when {
        align.startsWith("xMin") -> 0f
        align.startsWith("xMax") -> w - fw
        else -> (w - fw) / 2f
    }
    val oy = when {
        align.endsWith("YMin") -> 0f
        align.endsWith("YMax") -> h - fh
        else -> (h - fh) / 2f
    }
    return Rect.makeXYWH(x + ox, y + oy, fw, fh)
}

private fun attr(tag: String, name: String): String? {
    val match = attributePatterns.getValue(name).find(tag) ?: return null
    return match.groupValues[1].ifEmpty { match.groupValues[2] }
}

private fun length(value: String?): Float? =
    value?.trim()?.removeSuffix("px")?.toFloatOrNull()

private fun decodeDataUri(uri: String): ByteArray? {
    val comma = uri.indexOf(',')
    if (comma < 0) return null
    val payload = uri.substring(comma + 1)
    val bytes = if (uri.substring(0, comma).contains("base64", ignoreCase = true)) {
        Base64.getMimeDecoder().decode(payload)
    } else {
        URLDecoder.decode(payload, "UTF-8").encodeToByteArray()
    }
    return bytes
}

class SvgEmbeddedImageDecoder(
    private val svg: String,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val bitmap = renderSvgWithSizedImages(svg, options.size, options.scale, ::load) ?: return null
        return DecodeResult(image = bitmap.asImage(), isSampled = true)
    }

    private suspend fun load(href: String, size: Size, scale: Scale): Image? {
        return try {
            val data = if (href.startsWith("data:", ignoreCase = true)) {
                decodeDataUri(href)?.let(ByteBuffer::wrap) ?: return null
            } else href
            val request = ImageRequest.Builder(options.context)
                .data(data)
                .size(size)
                .scale(scale)
                .build()
            val result = imageLoader.execute(request) as? SuccessResult ?: return null
            Image.makeFromBitmap(result.image.toBitmap())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val isSvg = result.mimeType?.contains("svg", ignoreCase = true) == true ||
                result.source.fileOrNull()?.name?.endsWith(".svg", ignoreCase = true) == true
            if (!isSvg) return null
            val svg = runCatching { result.source.source().peek().readUtf8() }.getOrNull() ?: return null
            if (!imageTag.containsMatchIn(svg)) return null
            return SvgEmbeddedImageDecoder(svg, options, imageLoader)
        }
    }
}
