package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoCardShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

internal enum class RequestErrorKind {
    Connectivity,
    Generic,
}

internal data class RequestErrorPresentation(
    val kind: RequestErrorKind,
    val title: String,
    val description: String,
    val recovery: String? = null,
    val rawMessage: String? = null,
)

internal fun requestErrorPresentation(
    rawMessage: String?,
    genericTitle: String,
    genericDescription: String,
): RequestErrorPresentation {
    val message = rawMessage.orEmpty().trim()
    return if (message.isNotBlank() && looksLikeConnectivityIssue(message)) {
        RequestErrorPresentation(
            kind = RequestErrorKind.Connectivity,
            title = "Can't reach Linux.do",
            description = connectivityDescription(message),
            recovery = "Check your network and try again.",
            rawMessage = message,
        )
    } else {
        RequestErrorPresentation(
            kind = RequestErrorKind.Generic,
            title = genericTitle,
            description = genericDescription,
            rawMessage = message.ifBlank { null },
        )
    }
}

@Composable
internal fun RequestErrorCard(
    presentation: RequestErrorPresentation,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = NativeDoThemeExtras.colors
    val bulletinHostState = LocalBulletinHostState.current
    val clipboardManager = LocalClipboardManager.current
    val iconSize = if (compact) 18.dp else 22.dp
    val iconContainerSize = if (compact) 34.dp else 44.dp
    val contentPadding = if (compact) 16.dp else 20.dp
    val spacing = if (compact) 8.dp else 12.dp
    val containerColor = lerp(
        start = colors.backgroundBase,
        stop = colors.backgroundElevated,
        fraction = 0.22f,
    ).copy(alpha = 0.9f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NativeDoCardShape,
        color = containerColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = borderColor, shape = NativeDoCardShape)
                .padding(horizontal = contentPadding, vertical = contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(iconContainerSize)
                    .clip(CircleShape)
                    .background(colors.backgroundElevated.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = requestErrorIcon(presentation.kind),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = if (presentation.kind == RequestErrorKind.Connectivity) {
                        colors.textSecondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Text(
                text = presentation.title,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Text(
                text = presentation.description,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            presentation.recovery?.let { recovery ->
                Text(
                    text = recovery,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                )
            }

            presentation.rawMessage?.let { rawMessage ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.backgroundBase.copy(alpha = 0.72f))
                        .border(
                            width = 1.dp,
                            color = borderColor.copy(alpha = 0.86f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "Request error",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                    SelectionContainer {
                        Text(
                            text = rawMessage,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.textPrimary,
                        )
                    }
                }
            }

            if (onRetry != null || presentation.rawMessage != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onRetry != null) {
                        TextButton(onClick = onRetry) {
                            Text(text = "Retry")
                        }
                    }
                    presentation.rawMessage?.let { rawMessage ->
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(rawMessage))
                                bulletinHostState?.show(
                                    BulletinMessage(
                                        title = "Error copied",
                                        subtitle = "You can send it to the developer.",
                                        tone = BulletinTone.Success,
                                    ),
                                )
                            },
                        ) {
                            Text(text = "Copy error")
                        }
                    }
                }
            }
        }
    }
}

private fun looksLikeConnectivityIssue(message: String): Boolean {
    val normalized = message.lowercase()
    return normalized.contains("failed to connect") ||
        normalized.contains("unable to resolve host") ||
        normalized.contains("timeout") ||
        normalized.contains("timed out") ||
        normalized.contains("connection reset") ||
        normalized.contains("network is unreachable") ||
        normalized.contains("unexpected end of stream")
}

private fun connectivityDescription(message: String): String {
    val normalized = message.lowercase()
    return when {
        normalized.contains("unable to resolve host") ->
            "DNS lookup failed while trying to resolve Linux.do."

        normalized.contains("timeout") || normalized.contains("timed out") ->
            "The request timed out before Linux.do responded."

        normalized.contains("connection reset") ->
            "The connection was reset while talking to Linux.do."

        normalized.contains("network is unreachable") ->
            "The network is currently unreachable from this device."

        normalized.contains("unexpected end of stream") ->
            "The connection closed unexpectedly before Linux.do finished responding."

        else ->
            "The connection to Linux.do failed before a response was received."
    }
}

private fun requestErrorIcon(kind: RequestErrorKind): ImageVector = when (kind) {
    RequestErrorKind.Connectivity -> Icons.Outlined.CloudOff
    RequestErrorKind.Generic -> Icons.Outlined.ErrorOutline
}
