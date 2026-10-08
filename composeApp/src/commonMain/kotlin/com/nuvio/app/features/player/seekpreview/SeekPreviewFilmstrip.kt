package com.nuvio.app.features.player.seekpreview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.player.formatPlaybackTime
import kotlin.math.roundToInt

private const val FilmstripSideFrames = 2
private const val FilmstripSize = FilmstripSideFrames * 2 + 1
private val FilmstripGap = 10.dp
private const val CenterScale = 1.08f
private val FrameShape = RoundedCornerShape(8.dp)
private val CenterBorder = 2.dp

internal data class FilmstripSlot(
    val cueIndex: Int,
    val thumbnail: SeekrThumbnail?,
)

@Composable
internal fun SeekPreviewFilmstripOverlay(
    track: SeekrPreviewTrack?,
    scrubPositionMs: Long?,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (track == null || track.isEmpty) return

    var lastScrubPositionMs by remember { mutableStateOf<Long?>(null) }
    if (scrubPositionMs != null) {
        lastScrubPositionMs = scrubPositionMs
    }
    val effectivePositionMs = scrubPositionMs ?: lastScrubPositionMs ?: return

    val currentCueIndex = remember(track, effectivePositionMs) {
        track.cueIndexAt(effectivePositionMs)
    }

    var slots by remember(track) {
        mutableStateOf<List<FilmstripSlot>>(emptyList())
    }
    var previousCenterIndex by remember(track) { mutableStateOf<Int?>(null) }
    val slide = remember { Animatable(0f) }

    LaunchedEffect(track, currentCueIndex) {
        if (currentCueIndex < 0) return@LaunchedEffect

        val prev = previousCenterIndex
        val stepDiff = if (prev != null) currentCueIndex - prev else 0
        previousCenterIndex = currentCueIndex

        val newSlots = List(FilmstripSize) { offset ->
            val cueIdx = currentCueIndex + (offset - FilmstripSideFrames)
            val thumb = if (cueIdx in 0..track.cues.lastIndex) {
                track.thumbnailAtIndex(cueIdx)
            } else {
                null
            }
            FilmstripSlot(cueIndex = cueIdx, thumbnail = thumb)
        }
        slots = newSlots

        if (prev != null && stepDiff != 0) {
            val clampedSteps = stepDiff.coerceIn(-FilmstripSideFrames, FilmstripSideFrames)
            slide.snapTo(clampedSteps.toFloat())
            slide.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
        } else {
            slide.snapTo(0f)
        }
    }

    AnimatedVisibility(
        visible = isVisible && slots.isNotEmpty() && slots.any { it.thumbnail != null },
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val totalWidth = maxWidth
            val baseFrameWidth = (totalWidth * 0.16f).coerceIn(140.dp, 200.dp)
            val baseFrameHeight = baseFrameWidth * (9f / 16f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FilmstripRow(
                    slots = slots,
                    slide = slide,
                    frameWidth = baseFrameWidth,
                    frameHeight = baseFrameHeight,
                )
                Spacer(modifier = Modifier.height(6.dp))
                val centerSlot = slots.getOrNull(FilmstripSideFrames)
                val centerTime = centerSlot?.thumbnail?.cueStartMs ?: effectivePositionMs
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = formatPlaybackTime(centerTime),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilmstripRow(
    slots: List<FilmstripSlot>,
    slide: Animatable<Float, *>,
    frameWidth: Dp,
    frameHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val stepPx = with(LocalDensity.current) { (frameWidth + FilmstripGap).toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(frameHeight * CenterScale),
        contentAlignment = Alignment.Center,
    ) {
        slots.forEachIndexed { index, slot ->
            key(slot.cueIndex) {
                val place = index - FilmstripSideFrames
                val isCenter = place == 0
                val thumbnail = slot.thumbnail

                if (thumbnail != null) {
                    val painter = remember(thumbnail.sheetBitmap, thumbnail.tile) {
                        BitmapPainter(
                            image = thumbnail.sheetBitmap,
                            srcOffset = IntOffset(thumbnail.tile.x, thumbnail.tile.y),
                            srcSize = IntSize(thumbnail.tile.w, thumbnail.tile.h),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset {
                                val x = ((place + slide.value) * stepPx).roundToInt()
                                IntOffset(x, 0)
                            }
                            .size(frameWidth, frameHeight)
                            .then(
                                if (isCenter) {
                                    Modifier.graphicsLayer {
                                        scaleX = CenterScale
                                        scaleY = CenterScale
                                    }
                                } else {
                                    Modifier.graphicsLayer {
                                        alpha = 0.68f
                                    }
                                }
                            )
                            .clip(FrameShape)
                            .background(Color.Black)
                            .then(
                                if (isCenter) {
                                    Modifier.border(CenterBorder, Color.White, FrameShape)
                                } else {
                                    Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), FrameShape)
                                }
                            ),
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
