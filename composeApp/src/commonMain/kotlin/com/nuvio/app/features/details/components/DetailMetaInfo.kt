package com.nuvio.app.features.details.components

import com.nuvio.app.core.ui.rememberImdbLogoPainter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.nuvioDesktopDragScroll
import com.nuvio.app.core.ui.nuvioHorizontalScrollBleed
import com.nuvio.app.isDesktop
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.details.formatRuntimeForDisplay
import com.nuvio.app.features.details.formatMetaReleaseLineForDetails
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_AUDIENCE
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_IMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_LETTERBOXD
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_METACRITIC
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_MAL
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TOMATOES
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TRAKT
import com.nuvio.app.features.mdblist.RottenTomatoesStatus
import com.nuvio.app.features.mdblist.rottenTomatoesStatus
import nuvio.composeapp.generated.resources.*
import nuvio.composeapp.generated.resources.rating_audience_score
import nuvio.composeapp.generated.resources.rating_imdb
import nuvio.composeapp.generated.resources.rating_letterboxd
import nuvio.composeapp.generated.resources.rating_metacritic
import nuvio.composeapp.generated.resources.rating_rotten_tomatoes
import nuvio.composeapp.generated.resources.rating_tmdb
import nuvio.composeapp.generated.resources.rating_trakt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.runBlocking
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun DetailMetaInfo(
    meta: MetaDetails,
    modifier: Modifier = Modifier,
    horizontalScrollPadding: Dp = 0.dp,
    showOverallRatings: Boolean = true,
    isMdbListActive: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val releaseLine = formatMetaReleaseLineForDetails(meta)
        val runtimeText = formatRuntimeForDisplay(meta.runtime)
        val ageBadge = meta.ageRating?.trim()?.takeIf { it.isNotBlank() }
        val validImdbRating = meta.imdbRating
            ?.takeIf { showOverallRatings && !isMdbListActive }
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?.let(::formatOneDecimal)
        val ratingMedia = remember(meta) { meta.ratingReference() }
        val ratingState by rememberRatingState(ratingMedia)
        val hasMetaRow = releaseLine != null ||
            runtimeText != null ||
            ageBadge != null ||
            validImdbRating != null ||
            ratingState.isAvailable
        if (hasMetaRow) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                releaseLine?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                }
                runtimeText?.let { rt ->
                    Text(
                        text = rt,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ageBadge?.let { badge ->
                    DetailHeroMetaBadge(text = badge)
                }
                if (validImdbRating != null) {
                    val imdbTextStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
                    ClickableRatingBadge(url = formatImdbTitleUrl(meta.imdbId ?: meta.id)) {
                        ImdbRatingSourceLabel(storeTextStyle = imdbTextStyle)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = validImdbRating,
                            style = imdbTextStyle,
                            color = ImdbYellow,
                        )
                    }
                }
                if (ratingState.isAvailable) {
                    DetailRating(
                        media = ratingMedia,
                        title = meta.name,
                        state = ratingState,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isMdbListActive && meta.externalRatings.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DetailRatingsRow(
                ratings = meta.externalRatings,
                imdbId = meta.imdbId ?: meta.id,
                horizontalScrollPadding = horizontalScrollPadding,
            )
        }

        if (meta.director.isNotEmpty()) {
            MetaLabelValueRow(
                label = stringResource(Res.string.details_director),
                value = meta.director.joinToString(", "),
            )
        }

        if (meta.writer.isNotEmpty()) {
            MetaLabelValueRow(
                label = stringResource(Res.string.details_writer),
                value = meta.writer.joinToString(", "),
            )
        }

        if (!meta.description.isNullOrBlank()) {
            ExpandableDescription(
                text = meta.description,
                collapsedMaxLines = 3,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            )
        }
    }
}

@Composable
internal fun DetailRatingsRow(
    ratings: List<MetaExternalRating>,
    modifier: Modifier = Modifier,
    imdbId: String? = null,
    horizontalScrollPadding: Dp = 0.dp,
) {
    val scrollState = rememberScrollState()

    RatingsRow(
        ratings = ratings,
        imdbId = imdbId,
        modifier = modifier
            .nuvioHorizontalScrollBleed(horizontalScrollPadding)
            .fillMaxWidth()
            .nuvioDesktopDragScroll(scrollState)
            .horizontalScroll(scrollState)
            .padding(horizontal = horizontalScrollPadding),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RatingsRow(
    ratings: List<MetaExternalRating>,
    modifier: Modifier = Modifier,
    imdbId: String? = null,
    maxItems: Int = Int.MAX_VALUE,
    wrap: Boolean = false,
    compact: Boolean = false,
) {
    val orderedRatings = remember(ratings, maxItems) {
        val bySource = ratings.associateBy { it.source }
        ratingVisuals.mapNotNull { visuals ->
            bySource[visuals.source]?.let { rating -> visuals to rating }
        }.take(maxItems)
    }

    if (orderedRatings.isEmpty()) return
    val ratingScale = if (compact) 0.85f else 1f

    val content: @Composable () -> Unit = {
        orderedRatings.forEach { (visuals, rating) ->
            val ratingTextStyle = MaterialTheme.typography.titleSmall.copy(
                fontSize = MaterialTheme.typography.titleSmall.fontSize * ratingScale,
                lineHeight = MaterialTheme.typography.titleSmall.lineHeight * ratingScale,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
            val targetUrl = rating.url
                ?: if (visuals.source == PROVIDER_IMDB) formatImdbTitleUrl(imdbId) else null
            ClickableRatingBadge(
                url = targetUrl,
                modifier = if (wrap) Modifier.heightIn(min = 24.dp * ratingScale) else Modifier,
            ) {
                if (visuals.source == PROVIDER_IMDB && !AppFeaturePolicy.imdbRatingLogoEnabled) {
                    ImdbRatingSourceLabel(storeTextStyle = ratingTextStyle)
                } else {
                    val logoHeight = when (rating.rottenTomatoesStatus) {
                        RottenTomatoesStatus.CERTIFIED_FRESH, RottenTomatoesStatus.VERIFIED_HOT -> 24.dp
                        else -> 16.dp
                    } * ratingScale
                    Image(
                        painter = if (visuals.source == PROVIDER_IMDB) rememberImdbLogoPainter() else painterResource(visuals.logoFor(rating)),
                        contentDescription = visuals.displayName,
                        modifier = Modifier.size(
                            width = maxOf(visuals.logoWidth * ratingScale, if (wrap) 24.dp * ratingScale else logoHeight),
                            height = logoHeight,
                        ),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp * ratingScale))
                Text(
                    text = visuals.format(rating.value),
                    style = ratingTextStyle,
                    color = visuals.valueColor,
                )
            }
        }
    }
    if (wrap) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp * ratingScale),
            verticalArrangement = Arrangement.spacedBy(6.dp * ratingScale),
        ) { content() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp * ratingScale),
        ) { content() }
    }
}

internal fun formatImdbTitleUrl(imdbId: String?): String? {
    val clean = imdbId?.substringBefore(":")?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val id = when {
        clean.startsWith("tt", ignoreCase = true) && clean.length > 2 -> clean
        clean.all { it.isDigit() } -> "tt$clean"
        else -> null
    } ?: return null
    return "https://www.imdb.com/title/$id/"
}

@Composable
internal fun ClickableRatingBadge(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val isClickable = !url.isNullOrBlank()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = if (isClickable && isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "rating_badge_hover",
    )

    val badgeModifier = if (isClickable) {
        modifier
            .clip(shape)
            .background(containerColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = { runCatching { uriHandler.openUri(url) } },
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    } else {
        modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    }

    Row(
        modifier = badgeModifier,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun ImdbRatingSourceLabel(
    storeTextStyle: TextStyle,
    storeTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (AppFeaturePolicy.imdbRatingLogoEnabled) {
        Image(
            painter = if (isDesktop) rememberImdbLogoPainter() else painterResource(Res.drawable.rating_imdb),
            contentDescription = stringResource(Res.string.source_imdb),
            modifier = Modifier.size(width = 30.dp, height = 16.dp),
        )
    } else {
        Text(
            text = stringResource(Res.string.source_imdb),
            style = storeTextStyle,
            color = storeTextColor,
            maxLines = 1,
        )
    }
}

@Composable
internal fun MetaLabelValueRow(
    label: String,
    value: String,
) {
    Row {
        Text(
            text = "$label:  ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun DetailHeroMetaBadge(
    text: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    secondaryText: String? = null,
) {
    val borderColor = contentColor.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .border(BorderStroke(1.dp, borderColor), MetaBadgeShape)
            .padding(MetaBadgePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetaBadgeText(text = text, color = contentColor)
        secondaryText?.let { secondary ->
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(borderColor),
            )
            MetaBadgeText(text = secondary, color = contentColor)
        }
    }
}

internal val MetaBadgeShape = RoundedCornerShape(6.dp)
internal val MetaBadgePadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)

@Composable
private fun MetaBadgeText(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal val ImdbYellow = Color(0xFFF5C518)

private data class RatingVisuals(
    val source: String,
    val displayName: String,
    val logo: DrawableResource,
    val logoWidth: androidx.compose.ui.unit.Dp,
    val valueColor: Color,
    val format: (Double) -> String,
)

private fun RatingVisuals.logoFor(rating: MetaExternalRating): DrawableResource =
    when (rating.rottenTomatoesStatus) {
        RottenTomatoesStatus.FRESH -> Res.drawable.rating_rotten_tomatoes
        RottenTomatoesStatus.ROTTEN -> Res.drawable.rating_rotten_tomatoes_rotten
        RottenTomatoesStatus.CERTIFIED_FRESH -> Res.drawable.rating_rotten_tomatoes_certified
        RottenTomatoesStatus.HOT -> Res.drawable.rating_audience_score
        RottenTomatoesStatus.STALE -> Res.drawable.rating_audience_stale
        RottenTomatoesStatus.VERIFIED_HOT -> Res.drawable.rating_audience_verified_hot
        null -> logo
    }

private val ratingVisuals = listOf(
    RatingVisuals(
        source = PROVIDER_IMDB,
        displayName = "IMDb",
        logo = Res.drawable.rating_imdb,
        logoWidth = 30.dp,
        valueColor = Color(0xFFF5C518),
        format = ::formatOneDecimal,
    ),
    RatingVisuals(
        source = PROVIDER_TMDB,
        displayName = "TMDB",
        logo = Res.drawable.rating_tmdb,
        logoWidth = 22.dp,
        valueColor = Color(0xFF01B4E4),
        format = ::formatWhole,
    ),
    RatingVisuals(
        source = PROVIDER_TRAKT,
        displayName = "Trakt",
        logo = Res.drawable.rating_trakt,
        logoWidth = 16.dp,
        valueColor = Color(0xFFED1C24),
        format = ::formatWhole,
    ),
    RatingVisuals(
        source = PROVIDER_LETTERBOXD,
        displayName = "Letterboxd",
        logo = Res.drawable.rating_letterboxd,
        logoWidth = 16.dp,
        valueColor = Color(0xFF00E054),
        format = ::formatOneDecimal,
    ),
    RatingVisuals(
        source = PROVIDER_MAL,
        displayName = "MyAnimeList",
        logo = Res.drawable.rating_mal,
        logoWidth = 16.dp,
        valueColor = Color(0xFF2E51A2),
        format = ::formatOneDecimal,
    ),
    RatingVisuals(
        source = PROVIDER_TOMATOES,
        displayName = "Rotten Tomatoes",
        logo = Res.drawable.rating_rotten_tomatoes,
        logoWidth = 16.dp,
        valueColor = Color(0xFFFA320A),
        format = ::formatPercent,
    ),
    RatingVisuals(
        source = PROVIDER_AUDIENCE,
        displayName = runBlocking { getString(Res.string.rating_audience_score) },
        logo = Res.drawable.rating_audience_score,
        logoWidth = 16.dp,
        valueColor = Color(0xFFFA320A),
        format = ::formatPercent,
    ),
    RatingVisuals(
        source = PROVIDER_METACRITIC,
        displayName = "Metacritic",
        logo = Res.drawable.rating_metacritic,
        logoWidth = 16.dp,
        valueColor = Color(0xFFFFCC33),
        format = ::formatWhole,
    ),
)

internal fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt()
    val whole = rounded / 10
    val decimal = (rounded % 10).absoluteValue
    return "$whole.$decimal"
}

private fun formatWhole(value: Double): String = value.roundToInt().toString()

private fun formatPercent(value: Double): String = "${value.roundToInt()}%"
