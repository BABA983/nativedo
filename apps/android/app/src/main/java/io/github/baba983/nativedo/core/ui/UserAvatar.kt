package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

@Composable
fun UserAvatar(
    label: String,
    avatarTemplate: String,
    animatedAvatarUrl: String = "",
    baseUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val colors = NativeDoThemeExtras.colors
    val avatarSizePx = with(LocalDensity.current) { size.roundToPx() }
    val resolvedUrl = remember(baseUrl, avatarTemplate, animatedAvatarUrl, avatarSizePx) {
        resolveAvatarUrl(
            baseUrl = baseUrl,
            avatarTemplate = avatarTemplate,
            animatedAvatarUrl = animatedAvatarUrl,
            size = avatarSizePx,
        )
    }
    val imageRequest = remember(context, resolvedUrl) {
        ImageRequest.Builder(context)
            .data(resolvedUrl)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = imageRequest)
    val painterState by painter.state.collectAsState()
    val shouldRenderFallback = shouldRenderAvatarFallback(
        resolvedUrl = resolvedUrl,
        isImageLoadSuccessful = painterState is AsyncImagePainter.State.Success,
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentMuted),
        contentAlignment = Alignment.Center,
    ) {
        if (shouldRenderFallback) {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (resolvedUrl.isNotBlank()) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun shouldRenderAvatarFallback(
    resolvedUrl: String,
    isImageLoadSuccessful: Boolean,
): Boolean = resolvedUrl.isBlank() || !isImageLoadSuccessful
