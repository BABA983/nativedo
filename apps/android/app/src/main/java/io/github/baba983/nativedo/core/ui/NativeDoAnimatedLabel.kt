package io.github.baba983.nativedo.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.github.baba983.nativedo.ui.theme.NativeDoMotionTheme
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Immutable
private data class NativeDoAnimatedLabelState(
    val title: String,
    val subtitle: String?,
)

@Composable
fun NativeDoAnimatedLabel(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    subtitleStyle: TextStyle = MaterialTheme.typography.labelSmall,
    titleColor: Color = NativeDoThemeExtras.colors.textPrimary,
    subtitleColor: Color = NativeDoThemeExtras.colors.textSecondary,
    titleMaxLines: Int = 1,
    subtitleMaxLines: Int = 1,
    verticalSpacing: Dp = 2.dp,
) {
    val labelMotion = NativeDoMotionTheme.scheme.label
    val targetState = NativeDoAnimatedLabelState(
        title = title.ifBlank { " " },
        subtitle = subtitle?.takeIf { it.isNotBlank() },
    )

    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(animationSpec = labelMotion.enter.tweenSpec()) +
                    slideInVertically(
                        animationSpec = labelMotion.enter.tweenSpec(),
                        initialOffsetY = { it / 4 },
                    )
                ) togetherWith (
                fadeOut(animationSpec = labelMotion.exit.tweenSpec()) +
                    slideOutVertically(
                        animationSpec = labelMotion.exit.tweenSpec(),
                        targetOffsetY = { -it / 4 },
                    )
                )
        },
        label = "native_do_animated_label",
    ) { labelState ->
        Column(
            verticalArrangement = Arrangement.spacedBy(
                space = if (labelState.subtitle != null) verticalSpacing else 0.dp,
            ),
        ) {
            Text(
                text = labelState.title,
                style = titleStyle,
                color = titleColor,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            labelState.subtitle?.let { resolvedSubtitle ->
                Text(
                    text = resolvedSubtitle,
                    style = subtitleStyle,
                    color = subtitleColor,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
