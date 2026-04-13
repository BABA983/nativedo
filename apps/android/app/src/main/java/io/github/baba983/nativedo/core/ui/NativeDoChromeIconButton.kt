package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoExtendedColors
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import androidx.compose.foundation.shape.CircleShape

@Composable
fun NativeDoChromeIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    emphasized: Boolean = true,
    containerColorOverride: Color? = null,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val contentTint = colors.textPrimary.copy(
        alpha = nativeDoChromeIconButtonContentAlpha(
            enabled = enabled,
            emphasized = emphasized,
        ),
    )

    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = nativeDoChromeIconButtonContainerColor(
            colors = colors,
            enabled = enabled,
            emphasized = emphasized,
            pressed = pressed,
            containerColorOverride = containerColorOverride,
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    enabled = enabled,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = contentTint,
            )
        }
    }
}

fun nativeDoChromeIconButtonContentAlpha(
    enabled: Boolean,
    emphasized: Boolean,
): Float = when {
    !enabled -> 0.38f
    !emphasized -> 0.62f
    else -> 1f
}

fun nativeDoChromeIconButtonContainerColor(
    colors: NativeDoExtendedColors,
    enabled: Boolean,
    emphasized: Boolean,
    pressed: Boolean,
    containerColorOverride: Color? = null,
): Color {
    if (containerColorOverride != null) {
        return containerColorOverride
    }
    return when {
        pressed && enabled -> colors.glassBackgroundStrong.copy(alpha = 0.94f)
        !emphasized -> colors.glassBackgroundStrong.copy(alpha = 0.22f)
        else -> colors.glassBackgroundStrong.copy(alpha = 0.82f)
    }
}
