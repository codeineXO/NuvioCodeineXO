package com.nuvio.app.core.ui

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.ConstraintsSizeResolver
import coil3.compose.LocalPlatformContext
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.NullRequestDataException
import coil3.size.Dimension
import coil3.size.SizeResolver
import coil3.size.Scale
import kotlin.math.ceil
import kotlinx.coroutines.delay
import coil3.size.Size as CoilSize

// ConstraintsSizeResolver already reports physical pixels, including fractional display density.
// Posters reserve hover headroom; heroes supply their larger motion allowance through request extras.
private const val DesktopDecodeHeadroom = 1.25

private val AlwaysPlaying: () -> Boolean = { true }
private val NeverPlaying: () -> Boolean = { false }
private val NeverHovered: State<Boolean> = mutableStateOf(false)

@Composable
internal actual fun NuvioAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier,
    placeholder: Painter?,
    error: Painter?,
    fallback: Painter?,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)?,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)?,
    onError: ((AsyncImagePainter.State.Error) -> Unit)?,
    alignment: Alignment,
    contentScale: ContentScale,
    alpha: Float,
    colorFilter: ColorFilter?,
    filterQuality: FilterQuality?,
    clipToBounds: Boolean,
    desktopImageScaling: NuvioDesktopImageScaling,
    imageLoader: ImageLoader?,
    desktopAnimation: NuvioDesktopAnimation,
) {
    // Hover tracking only exists for On hover, so the common case pays nothing for it.
    val hoverSource = remember { MutableInteractionSource() }
    val hovered: State<Boolean> =
        if (desktopAnimation == NuvioDesktopAnimation.OnHover) hoverSource.collectIsRemoteHoveredAsState() else NeverHovered
    val playing: () -> Boolean = when (desktopAnimation) {
        NuvioDesktopAnimation.Always -> AlwaysPlaying
        NuvioDesktopAnimation.OnHover -> remember(hovered) { { hovered.value } }
        NuvioDesktopAnimation.Off -> NeverPlaying
    }
    val imageModifier =
        if (desktopAnimation == NuvioDesktopAnimation.OnHover) modifier.hoverable(hoverSource) else modifier
    val animated = desktopAnimation != NuvioDesktopAnimation.Off

    val context = LocalPlatformContext.current
    val constraintsSizeResolver = remember { ConstraintsSizeResolver() }
    val automaticSize = desktopImageScaling != NuvioDesktopImageScaling.Disabled &&
        (model !is ImageRequest || model.defined.sizeResolver == null)
    val headroom = (model as? ImageRequest)?.extras?.get(ArtworkHeadroomKey) ?: DesktopDecodeHeadroom
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    var initialSize by remember(model, headroom) { mutableStateOf<CoilSize?>(null) }
    var requestedSize by remember(model, headroom) { mutableStateOf<CoilSize?>(null) }
    LaunchedEffect(measuredSize, model, automaticSize, headroom) {
        if (!automaticSize || measuredSize.width <= 0 || measuredSize.height <= 0) return@LaunchedEffect
        val target = desktopDecodeSize(CoilSize(measuredSize.width, measuredSize.height), headroom)
        if (initialSize == null) {
            // The initial constraint-aware request already covers this size. Do not cancel its fetch.
            initialSize = target
            return@LaunchedEffect
        }
        val current = requestedSize ?: initialSize
        val upgraded = desktopArtworkUpgradeSize(current, target)
        if (upgraded != current) {
            delay(150)
            requestedSize = upgraded
        }
    }
    val requestModel = remember(context, model, desktopImageScaling, constraintsSizeResolver, animated, contentScale, requestedSize, headroom) {
        model.withDesktopImageOptions(
            context = context,
            highQualitySize = desktopImageScaling != NuvioDesktopImageScaling.Disabled,
            scale = if (contentScale == ContentScale.Fit || contentScale == ContentScale.Inside) Scale.FIT else Scale.FILL,
            resolver = constraintsSizeResolver,
            animated = animated,
            requestedSize = requestedSize,
            headroom = headroom,
        )
    }
    val transform: (AsyncImagePainter.State) -> AsyncImagePainter.State = remember(
        placeholder,
        error,
        fallback,
        playing,
    ) {
        transform@{ state ->
            when (state) {
                is AsyncImagePainter.State.Loading -> {
                    placeholder?.let { state.copy(painter = it) } ?: state
                }
                is AsyncImagePainter.State.Success -> {
                    val image = state.result.image
                    if (image is AnimatedSkiaImage) {
                        return@transform state.copy(painter = image.painter(playing))
                    }
                    state
                }
                is AsyncImagePainter.State.Error -> {
                    val fallbackPainter = if (state.result.throwable is NullRequestDataException) {
                        fallback ?: error
                    } else {
                        error
                    }
                    fallbackPainter?.let { state.copy(painter = it) } ?: state
                }
                AsyncImagePainter.State.Empty -> state
            }
        }
    }
    val onState: ((AsyncImagePainter.State) -> Unit)? = remember(onLoading, onSuccess, onError) {
        if (onLoading == null && onSuccess == null && onError == null) {
            null
        } else {
            { state ->
                when (state) {
                    is AsyncImagePainter.State.Loading -> onLoading?.invoke(state)
                    is AsyncImagePainter.State.Success -> onSuccess?.invoke(state)
                    is AsyncImagePainter.State.Error -> onError?.invoke(state)
                    AsyncImagePainter.State.Empty -> Unit
                }
            }
        }
    }

    AsyncImage(
        model = requestModel,
        imageLoader = imageLoader ?: SingletonImageLoader.get(context),
        contentDescription = contentDescription,
        modifier = imageModifier.then(constraintsSizeResolver).onSizeChanged { measuredSize = it },
        transform = transform,
        onState = onState,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality ?: FilterQuality.High,
        clipToBounds = clipToBounds,
    )
}

internal fun Any?.withDesktopHighQualitySize(
    context: PlatformContext,
    resolver: SizeResolver,
    scale: Scale = Scale.FIT,
    requestedSize: CoilSize? = null,
    headroom: Double = DesktopDecodeHeadroom,
): Any? {
    if (this == null) return null
    if (this is ImageRequest && defined.sizeResolver != null) return this

    val builder = if (this is ImageRequest) newBuilder() else ImageRequest.Builder(context).data(this)
    if (this !is ImageRequest || defined.scale == null) builder.scale(scale)
    return builder.apply {
        if (requestedSize != null) {
            size(requestedSize)
            useExistingImageAsPlaceholder(true)
        } else {
            size(object : SizeResolver {
                override suspend fun size(): CoilSize = desktopDecodeSize(resolver.size(), headroom)
            })
        }
    }.build()
}

internal fun desktopDecodeSize(size: CoilSize, headroom: Double = DesktopDecodeHeadroom): CoilSize {
    fun Dimension.withHeadroom(): Dimension = when (this) {
        is Dimension.Pixels -> Dimension.Pixels(
            ((ceil(px * headroom).toLong().coerceAtLeast(1) + 63) / 64 * 64)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        else -> this
    }
    return CoilSize(size.width.withHeadroom(), size.height.withHeadroom())
}

/** Retain adequate results when shrinking; upgrade each axis when the display needs more detail. */
internal fun desktopArtworkUpgradeSize(current: CoilSize?, target: CoilSize): CoilSize {
    if (current == null) return target
    fun larger(a: Dimension, b: Dimension): Dimension =
        if (a is Dimension.Pixels && b is Dimension.Pixels) Dimension.Pixels(maxOf(a.px, b.px)) else b
    return CoilSize(larger(current.width, target.width), larger(current.height, target.height))
}

/** Keeps constraint-aware sizing and separates animated requests in Coil's memory cache. */
private fun Any?.withDesktopImageOptions(
    context: PlatformContext,
    highQualitySize: Boolean,
    scale: Scale,
    resolver: SizeResolver,
    animated: Boolean,
    requestedSize: CoilSize?,
    headroom: Double,
): Any? {
    val sizedModel = if (highQualitySize) withDesktopHighQualitySize(context, resolver, scale, requestedSize, headroom) else this
    if (sizedModel == null || !animated) return sizedModel
    val builder = if (sizedModel is ImageRequest) sizedModel.newBuilder() else ImageRequest.Builder(context).data(sizedModel)
    return builder.apply {
        extras[NuvioAnimatedImageKey] = true
        memoryCacheKeyExtra("nuvio.animated", "true")
    }.build()
}
