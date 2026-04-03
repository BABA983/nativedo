package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
fun UserAvatar(
    label: String,
    avatarTemplate: String,
    baseUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val colors = NativeDoThemeExtras.colors
    val avatarSizePx = with(LocalDensity.current) { size.roundToPx() }
    val resolvedUrl = remember(baseUrl, avatarTemplate, avatarSizePx) {
        resolveAvatarUrl(
            baseUrl = baseUrl,
            avatarTemplate = avatarTemplate,
            size = avatarSizePx,
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentMuted),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (resolvedUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
