package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
fun NativeDoSettingsRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingValue: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NativeDoChromeShape,
        color = colors.glassBackgroundStrong.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        NativeDoListRow(
            title = title,
            subtitle = nativeDoSettingsRowSubtitleText(subtitle),
            onClick = onClick,
            leading = leading,
            trailing = nativeDoSettingsRowTrailingValueText(trailingValue)?.let { resolvedValue ->
                {
                    NativeDoSettingsRowTrailingValue(resolvedValue)
                }
            },
        )
    }
}

@Composable
private fun RowScope.NativeDoSettingsRowTrailingValue(
    value: String,
) {
    val colors = NativeDoThemeExtras.colors
    Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun nativeDoSettingsRowSubtitleText(subtitle: String?): String? = subtitle?.takeIf(String::isNotBlank)

internal fun nativeDoSettingsRowTrailingValueText(value: String?): String? = value?.takeIf(String::isNotBlank)
