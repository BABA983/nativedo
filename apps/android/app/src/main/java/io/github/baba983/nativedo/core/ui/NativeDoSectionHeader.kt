package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.baba983.nativedo.ui.theme.NativeDoSpacing
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
fun NativeDoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val colors = NativeDoThemeExtras.colors
    val resolvedActionLabel = nativeDoSectionHeaderActionLabel(actionLabel)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NativeDoSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(NativeDoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (resolvedActionLabel != null && onActionClick != null) {
            Text(
                text = resolvedActionLabel,
                modifier = Modifier.clickable(onClick = onActionClick),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

internal fun nativeDoSectionHeaderActionLabel(actionLabel: String?): String? = actionLabel?.takeIf(String::isNotBlank)
