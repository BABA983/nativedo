package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NativeDoDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
    alpha: Float = 0.75f,
    thickness: Dp = 1.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(nativeDoDividerResolvedThickness(thickness))
            .background(
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = nativeDoDividerResolvedAlpha(alpha),
                ),
            ),
    )
}

fun nativeDoDividerResolvedAlpha(alpha: Float): Float = alpha.coerceIn(0f, 1f)

fun nativeDoDividerResolvedThickness(thickness: Dp): Dp = if (thickness <= 0.dp) 1.dp else thickness
