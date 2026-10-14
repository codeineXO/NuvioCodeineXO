package com.nuvio.app.features.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FillerBadge(
    modifier: Modifier = Modifier,
    isMixed: Boolean = false,
) {
    if (isMixed) {
        MixedBadge(modifier = modifier)
    } else {
        AnimeEpisodeBadge(
            text = "FILLER",
            dotColor = Color(0xFFF59E0B),
            dotBorderColor = Color(0xFFFBBF24),
            badgeBorderColor = Color(0xFFF59E0B).copy(alpha = 0.55f),
            textColor = Color(0xFFFDE68A),
            modifier = modifier,
        )
    }
}

@Composable
fun MixedBadge(
    modifier: Modifier = Modifier,
) {
    AnimeEpisodeBadge(
        text = "MIXED",
        dotColor = Color(0xFF0EA5E9),
        dotBorderColor = Color(0xFF38BDF8),
        badgeBorderColor = Color(0xFF0EA5E9).copy(alpha = 0.55f),
        textColor = Color(0xFFBAE6FD),
        modifier = modifier,
    )
}

@Composable
private fun AnimeEpisodeBadge(
    text: String,
    dotColor: Color,
    dotBorderColor: Color,
    badgeBorderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .border(
                width = 1.dp,
                color = badgeBorderColor,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(width = 0.75.dp, color = dotBorderColor, shape = CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.7.sp,
            ),
            color = textColor,
            maxLines = 1,
        )
    }
}
