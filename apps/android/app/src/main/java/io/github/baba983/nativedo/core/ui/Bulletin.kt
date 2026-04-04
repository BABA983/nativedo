package io.github.baba983.nativedo.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest

internal enum class BulletinTone {
    Info,
    Error,
    Success,
}

internal data class BulletinMessage(
    val title: String,
    val subtitle: String? = null,
    val tone: BulletinTone = BulletinTone.Info,
    val durationMillis: Long = 2_800L,
)

internal data class BulletinHostUiState(
    val currentMessage: BulletinMessage? = null,
    val isVisible: Boolean = false,
) {
    fun show(message: BulletinMessage): BulletinHostUiState =
        BulletinHostUiState(currentMessage = message, isVisible = true)

    fun hide(): BulletinHostUiState = copy(isVisible = false)

    fun clear(): BulletinHostUiState = BulletinHostUiState()
}

internal class BulletinHostState {
    private val _messages = MutableSharedFlow<BulletinMessage>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    fun show(message: BulletinMessage) {
        _messages.tryEmit(message)
    }
}

internal val LocalBulletinHostState = staticCompositionLocalOf<BulletinHostState?> { null }

@Composable
internal fun BulletinHost(
    hostState: BulletinHostState,
    modifier: Modifier = Modifier,
) {
    var uiState by remember { mutableStateOf(BulletinHostUiState()) }

    LaunchedEffect(hostState) {
        hostState.messages.collectLatest { nextMessage ->
            uiState = uiState.show(nextMessage)
            delay(nextMessage.durationMillis)
            uiState = uiState.hide()
            delay(BULLETIN_EXIT_DURATION_MILLIS)
            uiState = uiState.clear()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = uiState.isVisible,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 240),
                initialOffsetY = { -it / 2 },
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 200),
                targetOffsetY = { -it / 3 },
            ) + fadeOut(animationSpec = tween(durationMillis = 160)),
        ) {
            val currentMessage = uiState.currentMessage ?: return@AnimatedVisibility
            val colors = NativeDoThemeExtras.colors
            val containerColor = lerp(
                start = colors.backgroundElevated,
                stop = colors.textPrimary,
                fraction = 0.08f,
            ).copy(alpha = 0.98f)

            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 76.dp)
                    .widthIn(max = 420.dp),
                shape = NativeDoChromeShape,
                color = containerColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
                            shape = NativeDoChromeShape,
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = bulletinIconContainerColor(currentMessage.tone),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = bulletinIcon(currentMessage.tone),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = bulletinIconTint(currentMessage.tone),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = currentMessage.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        currentMessage.subtitle?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val BULLETIN_EXIT_DURATION_MILLIS = 220L

@Composable
private fun bulletinIconContainerColor(tone: BulletinTone) = when (tone) {
    BulletinTone.Info -> NativeDoThemeExtras.colors.backgroundBase.copy(alpha = 0.88f)
    BulletinTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    BulletinTone.Success -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
}

@Composable
private fun bulletinIconTint(tone: BulletinTone) = when (tone) {
    BulletinTone.Info -> NativeDoThemeExtras.colors.textSecondary
    BulletinTone.Error -> MaterialTheme.colorScheme.error
    BulletinTone.Success -> MaterialTheme.colorScheme.primary
}

private fun bulletinIcon(tone: BulletinTone): ImageVector = when (tone) {
    BulletinTone.Info -> Icons.Outlined.Info
    BulletinTone.Error -> Icons.Outlined.CloudOff
    BulletinTone.Success -> Icons.Outlined.ContentCopy
}

internal fun requestErrorBulletin(
    rawMessage: String?,
    genericTitle: String,
    genericDescription: String,
): BulletinMessage {
    val presentation = requestErrorPresentation(
        rawMessage = rawMessage,
        genericTitle = genericTitle,
        genericDescription = genericDescription,
    )
    return BulletinMessage(
        title = presentation.title,
        subtitle = presentation.description,
        tone = if (presentation.kind == RequestErrorKind.Connectivity) {
            BulletinTone.Error
        } else {
            BulletinTone.Info
        },
    )
}
