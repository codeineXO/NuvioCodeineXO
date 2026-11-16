package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.rememberResourceEnvironment

@Composable
internal fun AppBrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    icon: AppIconOption? = null,
) {
    val resource = rememberWordmarkIcon(icon)
    val environment = rememberResourceEnvironment()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        key(resource, environment) {
            val image by produceState<Result<ImageBitmap>?>(null) {
                value = loadWordmarkIcon(resource, environment)
            }
            val bitmap = image?.getOrNull()
            val painter = when {
                bitmap != null -> remember(bitmap) { BitmapPainter(bitmap) }
                image?.isFailure == true -> painterResource(resource)
                else -> remember { ColorPainter(Color.Transparent) }
            }
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "NuvioCodeineXO",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = (-0.3).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun PreloadAppBrandWordmark() {
    val resource = rememberWordmarkIcon(icon = null)
    val environment = rememberResourceEnvironment()
    LaunchedEffect(resource, environment) { loadWordmarkIcon(resource, environment) }
}

@Composable
private fun rememberWordmarkIcon(icon: AppIconOption?): DrawableResource {
    val state by remember {
        AppIconRepository.ensureLoaded()
        AppIconRepository.state
    }.collectAsStateWithLifecycle()
    return (icon ?: state.selected).transparentPreviewResource
}

private val wordmarkIconLock = Mutex()
private val wordmarkIcons = mutableMapOf<DrawableResource, ImageBitmap>()

private suspend fun loadWordmarkIcon(
    resource: DrawableResource,
    environment: ResourceEnvironment,
): Result<ImageBitmap> = wordmarkIconLock.withLock {
    wordmarkIcons[resource]?.let { return@withLock Result.success(it) }
    withContext(Dispatchers.Default) {
        val bytes = getDrawableResourceBytes(environment, resource)
        runCatching { bytes.decodeToImageBitmap() }
    }.onSuccess { wordmarkIcons[resource] = it }
}
