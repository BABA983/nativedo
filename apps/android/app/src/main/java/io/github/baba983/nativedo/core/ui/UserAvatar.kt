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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

private val AVATAR_PALETTE = listOf(
    Color(0xFFE5766D), // red
    Color(0xFFF0A15B), // orange
    Color(0xFF9B7FCF), // violet
    Color(0xFF6BBF6B), // green
    Color(0xFF5CC0D0), // cyan
    Color(0xFF6B9FE8), // blue
    Color(0xFFE878A0), // pink
)

internal fun avatarColorForLabel(label: String): Color {
    if (label.isBlank()) return AVATAR_PALETTE[5]
    val hash = label.hashCode().toLong().and(0xFFFFFFFFL)
    val index = (hash % AVATAR_PALETTE.size).toInt()
    return AVATAR_PALETTE[index]
}

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
    val avatarBackground = remember(label) { avatarColorForLabel(label) }
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
            .background(avatarBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (shouldRenderFallback) {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.92f),
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
