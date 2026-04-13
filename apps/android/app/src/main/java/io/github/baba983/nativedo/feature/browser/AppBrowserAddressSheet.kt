package io.github.baba983.nativedo.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.theme.NativeDoSpacing
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
internal fun AppBrowserAddressSheet(
    state: AppBrowserAddressUiState,
    onAction: (AppBrowserAction) -> Unit,
) {
    if (!state.isEditing) return

    val colors = NativeDoThemeExtras.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onAction(AppBrowserAction.CancelAddressEditing) },
            ),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .appBrowserTopBarOffset()
                .padding(horizontal = NativeDoSpacing.lg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(28.dp),
            color = colors.glassBackgroundStrong.copy(alpha = 0.92f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NativeDoSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(NativeDoSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.app_browser_address_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )

                TextField(
                    value = state.draft,
                    onValueChange = { value ->
                        onAction(AppBrowserAction.UpdateAddressDraft(value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.app_browser_address_placeholder),
                            color = colors.textSecondary,
                        )
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                onAction(AppBrowserAction.SubmitAddress(state.draft))
                            },
                        ) {
                            Text(text = stringResource(R.string.app_browser_address_submit))
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            onAction(AppBrowserAction.SubmitAddress(state.draft))
                        },
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.backgroundBase.copy(alpha = 0.72f),
                        unfocusedContainerColor = colors.backgroundBase.copy(alpha = 0.56f),
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedPlaceholderColor = colors.textSecondary,
                        unfocusedPlaceholderColor = colors.textSecondary,
                    ),
                    shape = RoundedCornerShape(20.dp),
                )

                if (state.recentEntries.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.app_browser_recent_links),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                    ) {
                        items(
                            items = state.recentEntries.take(6),
                            key = { entry -> entry.url },
                        ) { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAction(AppBrowserAction.SubmitAddress(entry.url))
                                    }
                                    .padding(vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = entry.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
                            )
                        }
                    }
                }
            }
        }
    }
}
