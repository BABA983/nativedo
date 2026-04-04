package io.github.baba983.nativedo.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.ui.theme.NativeDoCardShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
fun AuthRestoreGate(
    snapshot: SessionSnapshot?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val subtitle = snapshot?.chromeTitle ?: "正在同步 Linux.do 会话"
    val containerColor = lerp(
        start = colors.backgroundBase,
        stop = colors.backgroundElevated,
        fraction = 0.18f,
    ).copy(alpha = 0.9f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .background(colors.backgroundBase.copy(alpha = 0.26f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = NativeDoCardShape,
            color = containerColor,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = NativeDoCardShape,
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(
                    text = "正在恢复登录态",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

internal fun shouldShowAuthRestoreGate(sessionState: AppSessionState): Boolean {
    return sessionState == AppSessionState.Unknown || sessionState is AppSessionState.Restoring
}
