package io.github.baba983.nativedo.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoGlassSpec
import io.github.baba983.nativedo.ui.theme.NativeDoMotionTheme
import io.github.baba983.nativedo.ui.theme.NativeDoSpacing
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

private val NativeDoSearchFieldHeight = 48.dp
private val NativeDoSearchFieldIconSize = 22.dp

@Composable
fun NativeDoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    readOnly: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    glassSpec: NativeDoGlassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Field),
    onClick: (() -> Unit)? = null,
    onSubmit: (() -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val colors = NativeDoThemeExtras.colors
    val labelMotion = NativeDoMotionTheme.scheme.label
    val containerColor by animateColorAsState(
        targetValue = glassSpec.containerColor,
        animationSpec = labelMotion.enter.tweenSpec(),
        label = "native_do_search_field_container",
    )
    val borderColor by animateColorAsState(
        targetValue = glassSpec.borderColor,
        animationSpec = labelMotion.enter.tweenSpec(),
        label = "native_do_search_field_border",
    )
    val placeholderVisible = shouldShowNativeDoSearchFieldPlaceholder(value)
    val readOnlyText = nativeDoSearchFieldReadOnlyText(value)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(NativeDoSearchFieldHeight)
            .then(
                if (readOnly && onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        shape = NativeDoChromeShape,
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NativeDoSpacing.md + NativeDoSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(NativeDoSearchFieldIconSize),
                tint = colors.textSecondary,
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = placeholderVisible,
                    enter = fadeIn(animationSpec = labelMotion.enter.tweenSpec()),
                    exit = fadeOut(animationSpec = labelMotion.exit.tweenSpec()),
                    label = "native_do_search_field_placeholder",
                ) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                }

                if (readOnly) {
                    readOnlyText?.let { resolvedReadOnlyText ->
                        Text(
                            text = resolvedReadOnlyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                    }
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (focusRequester != null) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                }
                            ),
                        enabled = enabled,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = colors.textPrimary,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            imeAction = nativeDoSearchFieldImeAction(hasSubmitAction = onSubmit != null),
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { onSubmit?.invoke() },
                        ),
                    )
                }
            }

            trailingContent()
        }
    }
}

internal fun shouldShowNativeDoSearchFieldPlaceholder(value: String): Boolean = value.isBlank()

internal fun nativeDoSearchFieldReadOnlyText(value: String): String? = value.takeIf(String::isNotBlank)

internal fun nativeDoSearchFieldImeAction(hasSubmitAction: Boolean): ImeAction = if (hasSubmitAction) {
    ImeAction.Search
} else {
    ImeAction.Default
}
