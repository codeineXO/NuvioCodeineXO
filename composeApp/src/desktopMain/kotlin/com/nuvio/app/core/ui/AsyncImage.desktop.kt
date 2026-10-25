package com.nuvio.app.core.ui

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.ConstraintsSizeResolver
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.NullRequestDataException
import coil3.size.Dimension
import coil3.size.SizeResolver
import coil3.size.Scale
import kotlin.math.ceil
import coil3.size.Size as CoilSize

// ConstraintsSizeResolver already reports physical pixels, including fractional display density.
// Covers the existing hover enlargement and the hero's 1.14x background scale.
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
        if (desktopAnimation == NuvioDesktopAnimation.OnHover) hoverSource.collectIsHoveredAsState() else NeverHovered
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
    val requestModel = remember(context, model, desktopImageScaling, constraintsSizeResolver, animated, contentScale) {
        model.withDesktopImageOptions(
            context = context,
            highQualitySize = desktopImageScaling != NuvioDesktopImageScaling.Disabled,
            scale = if (contentScale == ContentScale.Fit || contentScale == ContentScale.Inside) Scale.FIT else Scale.FILL,
            resolver = constraintsSizeResolver,
            animated = animated,
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
        modifier = imageModifier.then(constraintsSizeResolver),
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

internal fun Any?.withDesktopHighQualitySize(context: PlatformContext, resolver: SizeResolver, scale: Scale = Scale.FIT): Any? {
    if (this == null) return null
    if (this is ImageRequest && defined.sizeResolver != null) return this

    val builder = if (this is ImageRequest) newBuilder() else ImageRequest.Builder(context).data(this)
    if (this !is ImageRequest || defined.scale == null) builder.scale(scale)
    return builder.size(object : SizeResolver {
        override suspend fun size(): CoilSize = desktopDecodeSize(resolver.size())
    }).build()
}

internal fun desktopDecodeSize(size: CoilSize): CoilSize {
    fun Dimension.withHeadroom(): Dimension = when (this) {
        is Dimension.Pixels -> Dimension.Pixels(ceil(px * DesktopDecodeHeadroom).toLong().coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
        else -> this
    }
    return CoilSize(size.width.withHeadroom(), size.height.withHeadroom())
}

/** Keeps constraint-aware sizing and separates animated requests in Coil's memory cache. */
private fun Any?.withDesktopImageOptions(
    context: PlatformContext,
    highQualitySize: Boolean,
    scale: Scale,
    resolver: SizeResolver,
    animated: Boolean,
): Any? {
    val sizedModel = if (highQualitySize) withDesktopHighQualitySize(context, resolver, scale) else this
    if (sizedModel == null || !animated) return sizedModel
    val builder = if (sizedModel is ImageRequest) sizedModel.newBuilder() else ImageRequest.Builder(context).data(sizedModel)
    return builder.apply {
        extras[NuvioAnimatedImageKey] = true
        memoryCacheKeyExtra("nuvio.animated", "true")
    }.build()
}
