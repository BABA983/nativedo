package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor

@Composable
fun NativeDoBackIcon(
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack,
    closeIcon: ImageVector = Icons.Outlined.Close,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = backIcon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - clampedProgress
                rotationZ = -135f * clampedProgress
                scaleX = 1f - (clampedProgress * 0.08f)
                scaleY = 1f - (clampedProgress * 0.08f)
            },
        )
        Icon(
            imageVector = closeIcon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                alpha = clampedProgress
                rotationZ = 90f * (1f - clampedProgress)
                scaleX = 0.92f + (clampedProgress * 0.08f)
                scaleY = 0.92f + (clampedProgress * 0.08f)
            },
        )
    }
}
