package io.github.baba983.nativedo.ui.topic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.baba983.nativedo.ui.actionbar.Theme
import io.github.baba983.nativedo.ui.actionbar.rememberThemeRevision
import io.github.baba983.nativedo.ui.actionbar.themedColor

@Immutable
data class TopicThemeColors(
    val background: Color,
    val rowBackground: Color,
    val primary: Color,
    val title: Color,
    val body: Color,
    val secondary: Color,
    val divider: Color,
)

@Composable
fun rememberTopicThemeColors(): TopicThemeColors {
    val revision = rememberThemeRevision()
    return remember(revision) {
        TopicThemeColors(
            background = themedColor(Theme.key_windowBackgroundGray),
            rowBackground = themedColor(Theme.key_windowBackgroundWhite),
            primary = themedColor(Theme.key_windowBackgroundWhiteBlueText),
            title = themedColor(Theme.key_windowBackgroundWhiteBlackText),
            body = themedColor(Theme.key_windowBackgroundWhiteBlackText),
            secondary = themedColor(Theme.key_windowBackgroundWhiteGrayText),
            divider = themedColor(Theme.key_divider),
        )
    }
}
