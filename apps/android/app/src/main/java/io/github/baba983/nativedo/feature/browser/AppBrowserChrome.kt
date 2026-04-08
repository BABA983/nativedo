package io.github.baba983.nativedo.feature.browser

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.AppTopBarHorizontalPadding
import io.github.baba983.nativedo.ui.AppTopBarRowMinHeight
import io.github.baba983.nativedo.ui.AppTopBarVerticalPadding
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal data class AppBrowserChromeConfig(
    val title: String,
    val subtitle: String?,
    val isHttps: Boolean,
    val progress: Float,
    val canGoForward: Boolean,
    val backHistoryEntries: List<AppBrowserHistoryEntry>,
    val browserHistoryEntries: List<AppBrowserHistoryEntry>,
    val panel: AppBrowserPanel,
    val alpha: Float,
    val translationX: Float,
    val onBack: () -> Unit,
    val onClose: () -> Unit,
    val onShowHistory: () -> Unit,
    val onShowSettings: () -> Unit,
    val onHistoryEntryClick: (AppBrowserHistoryEntry) -> Unit,
    val onForward: () -> Unit,
    val onRefresh: () -> Unit,
    val onShare: () -> Unit,
    val onOpenInBrowser: () -> Unit,
    val onCopyLink: () -> Unit,
)

internal enum class AppBrowserMenuAction {
    History,
    Forward,
    Refresh,
    Share,
    OpenInBrowser,
    CopyLink,
    Settings,
    Close,
}

internal sealed interface AppBrowserMenuEntry {
    data class Item(val item: AppBrowserMenuItem) : AppBrowserMenuEntry
    data object Divider : AppBrowserMenuEntry
}

internal data class AppBrowserMenuItem(
    val action: AppBrowserMenuAction,
    val enabled: Boolean = true,
)

internal data class AppBrowserHistoryEntry(
    val title: String,
    val url: String,
    val offset: Int?,
    val host: String,
    val visitedAtMillis: Long,
)

private enum class AppBrowserHistoryPopupAnchor {
    BackButton,
}

private data class AppBrowserPopupPalette(
    val background: Color,
    val sectionGap: Color,
    val text: Color,
    val secondaryText: Color,
    val icon: Color,
    val pressed: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppBrowserTopBar(
    config: AppBrowserChromeConfig?,
) {
    val colors = NativeDoThemeExtras.colors
    val hasBackHistoryEntries = config?.backHistoryEntries?.isNotEmpty() == true
    val hasBrowserHistoryEntries = config?.browserHistoryEntries?.isNotEmpty() == true
    val progress = config?.progress?.coerceIn(0f, 1f) ?: 0f
    val showProgress = progress > 0f && progress < 1f
    val topBarAlpha = config?.alpha ?: 0f
    val topBarTranslationX = config?.translationX ?: 0f
    var menuExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }

    val menuEntries = remember(config?.canGoForward, hasBrowserHistoryEntries) {
        buildAppBrowserMenuEntries(
            canGoForward = config?.canGoForward == true,
            hasBrowserHistory = hasBrowserHistoryEntries,
        )
    }

    LaunchedEffect(config) {
        if (config == null) {
            menuExpanded = false
            historyExpanded = false
        } else if (config.backHistoryEntries.isEmpty()) {
            historyExpanded = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .graphicsLayer {
                alpha = topBarAlpha
                translationX = topBarTranslationX
            },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppTopBarRowMinHeight)
                    .padding(
                        horizontal = AppTopBarHorizontalPadding,
                        vertical = AppTopBarVerticalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box {
                    AppBrowserChromeButton(
                        onClick = { config?.onBack?.invoke() },
                        onLongClick = if (hasBackHistoryEntries) {
                            {
                                menuExpanded = false
                                historyExpanded = true
                            }
                        } else {
                            null
                        },
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.app_browser_close),
                    )
                    AppBrowserHistoryPopup(
                        expanded = historyExpanded &&
                            config?.backHistoryEntries?.isNotEmpty() == true,
                        entries = config?.backHistoryEntries.orEmpty(),
                        onDismissRequest = { historyExpanded = false },
                        onEntryClick = { entry ->
                            historyExpanded = false
                            config?.onHistoryEntryClick?.invoke(entry)
                        },
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AppBrowserTitlePill(
                        title = config?.title.orEmpty(),
                        subtitle = config?.subtitle,
                        isHttps = config?.isHttps == true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Box {
                    AppBrowserChromeButton(
                        onClick = {
                            if (config != null) {
                                historyExpanded = false
                                menuExpanded = true
                            }
                        },
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.app_browser_more_actions),
                    )
                    AppBrowserActionPopup(
                        expanded = menuExpanded && config != null,
                        entries = menuEntries,
                        onDismissRequest = { menuExpanded = false },
                        onActionClick = { action ->
                            menuExpanded = false
                            when (action) {
                                AppBrowserMenuAction.History -> config?.onShowHistory?.invoke()
                                AppBrowserMenuAction.Forward -> config?.onForward?.invoke()
                                AppBrowserMenuAction.Refresh -> config?.onRefresh?.invoke()
                                AppBrowserMenuAction.Share -> config?.onShare?.invoke()
                                AppBrowserMenuAction.OpenInBrowser -> config?.onOpenInBrowser?.invoke()
                                AppBrowserMenuAction.CopyLink -> config?.onCopyLink?.invoke()
                                AppBrowserMenuAction.Settings -> config?.onShowSettings?.invoke()
                                AppBrowserMenuAction.Close -> config?.onClose?.invoke()
                            }
                        },
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                color = colors.accent,
                trackColor = colors.glassBackgroundStrong.copy(alpha = 0.24f),
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = AppTopBarHorizontalPadding)
                    .height(appBrowserProgressHeight)
                    .clip(NativeDoPillShape)
                    .graphicsLayer {
                        alpha = if (showProgress) 1f else 0f
                        scaleX = if (showProgress) 1f else 0.92f
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(Color.Transparent, NativeDoPillShape),
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
        )
    }
}

@Composable
internal fun AppBrowserChromeButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = if (pressed) {
            colors.glassBackgroundStrong.copy(alpha = 0.94f)
        } else {
            colors.glassBackgroundStrong.copy(alpha = 0.82f)
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = colors.textPrimary,
            )
        }
    }
}

@Composable
internal fun AppBrowserTitlePill(
    title: String,
    subtitle: String?,
    isHttps: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val statusTint = if (isHttps) colors.accent else colors.textSecondary
    val resolvedTitle = title.ifBlank { " " }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = colors.glassBackgroundStrong.copy(alpha = 0.84f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = statusTint.copy(alpha = 0.14f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isHttps) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = null,
                    tint = statusTint,
                    modifier = Modifier.size(14.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBrowserHistoryPopup(
    expanded: Boolean,
    entries: List<AppBrowserHistoryEntry>,
    onDismissRequest: () -> Unit,
    onEntryClick: (AppBrowserHistoryEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        return
    }

    val palette = rememberAppBrowserPopupPalette()
    val density = LocalDensity.current
    val popupMargin = with(density) { appBrowserPopupMargin.roundToPx() }
    val popupSpacing = with(density) { appBrowserPopupSpacing.roundToPx() }
    val popupOpenDuration = remember(entries.size) {
        appBrowserPopupOpenDuration(entries.size.coerceAtMost(6))
    }
    val popupCornerRadiusPx = with(density) { appBrowserPopupCornerRadius.toPx() }
    val popupCloseTranslationPx = with(density) { appBrowserPopupCloseTranslation.toPx() }
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(expanded) {
        transitionState.targetState = expanded
    }

    if (!transitionState.currentState && !transitionState.targetState) {
        return
    }

    val popupPositionProvider = remember(popupMargin, popupSpacing) {
        AppBrowserPopupPositionProvider(
            margin = popupMargin,
            spacing = popupSpacing,
        )
    }
    val transition = updateTransition(transitionState, label = "appBrowserHistoryPopupTransition")
    val popupProgress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) {
                    popupOpenDuration
                } else {
                    appBrowserPopupCloseDurationMillis
                },
                easing = LinearOutSlowInEasing,
            )
        },
        label = "appBrowserHistoryPopupProgress",
    ) { visible ->
        if (visible) 1f else 0f
    }
    val containerTranslationY by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = appBrowserPopupCloseDurationMillis,
                easing = LinearOutSlowInEasing,
            )
        },
        label = "appBrowserHistoryPopupTranslationY",
    ) { visible ->
        if (visible) 0f else -popupCloseTranslationPx
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = containerTranslationY
                }
                .width(appBrowserHistoryPopupWidth)
                .drawWithContent {
                    val revealScale = max(popupProgress, 0.001f)
                    val backgroundColor = palette.background.copy(alpha = 0.98f * popupProgress)
                    withTransform({
                        scale(
                            scaleX = 1f,
                            scaleY = revealScale,
                            pivot = Offset(size.width, 0f),
                        )
                    }) {
                        drawRoundRect(
                            color = backgroundColor,
                            cornerRadius = CornerRadius(popupCornerRadiusPx, popupCornerRadiusPx),
                        )
                    }
                    clipRect(bottom = size.height * popupProgress) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(appBrowserHistoryPopupWidth)
                    .heightIn(max = appBrowserHistoryPopupMaxHeight)
                    .padding(vertical = appBrowserPopupVerticalPadding),
            ) {
                itemsIndexed(
                    items = entries,
                    key = { index, entry -> "${entry.url}-${entry.visitedAtMillis}-$index" },
                ) { index, entry ->
                    AppBrowserHistoryPopupItem(
                        palette = palette,
                        entry = entry,
                        index = index,
                        itemCount = entries.size,
                        popupProgress = popupProgress,
                        onClick = { onEntryClick(entry) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppBrowserActionPopup(
    expanded: Boolean,
    entries: List<AppBrowserMenuEntry>,
    onDismissRequest: () -> Unit,
    onActionClick: (AppBrowserMenuAction) -> Unit,
) {
    val palette = rememberAppBrowserPopupPalette()
    val density = LocalDensity.current
    val popupMargin = with(density) { appBrowserPopupMargin.roundToPx() }
    val popupSpacing = with(density) { appBrowserPopupSpacing.roundToPx() }
    val itemCount = remember(entries) {
        entries.count { it is AppBrowserMenuEntry.Item }
    }
    val popupOpenDuration = remember(itemCount) {
        appBrowserPopupOpenDuration(itemCount)
    }
    val popupCornerRadiusPx = with(density) { appBrowserPopupCornerRadius.toPx() }
    val popupCloseTranslationPx = with(density) { appBrowserPopupCloseTranslation.toPx() }
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(expanded) {
        transitionState.targetState = expanded
    }

    if (!transitionState.currentState && !transitionState.targetState) {
        return
    }

    val popupPositionProvider = remember(popupMargin, popupSpacing) {
        AppBrowserPopupPositionProvider(
            margin = popupMargin,
            spacing = popupSpacing,
        )
    }
    val transition = updateTransition(transitionState, label = "appBrowserPopupTransition")
    val popupProgress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) {
                    popupOpenDuration
                } else {
                    appBrowserPopupCloseDurationMillis
                },
                easing = LinearOutSlowInEasing,
            )
        },
        label = "appBrowserPopupProgress",
    ) { visible ->
        if (visible) 1f else 0f
    }
    val containerTranslationY by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = appBrowserPopupCloseDurationMillis,
                easing = LinearOutSlowInEasing,
            )
        },
        label = "appBrowserPopupTranslationY",
    ) { visible ->
        if (visible) 0f else -popupCloseTranslationPx
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = containerTranslationY
                }
                .width(appBrowserPopupWidth)
                .drawWithContent {
                    val revealScale = max(popupProgress, 0.001f)
                    val backgroundColor = palette.background.copy(alpha = 0.98f * popupProgress)
                    withTransform({
                        scale(
                            scaleX = 1f,
                            scaleY = revealScale,
                            pivot = Offset(size.width, 0f),
                        )
                    }) {
                        drawRoundRect(
                            color = backgroundColor,
                            cornerRadius = CornerRadius(popupCornerRadiusPx, popupCornerRadiusPx),
                        )
                    }
                    clipRect(bottom = size.height * popupProgress) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            Column(
                modifier = Modifier.padding(vertical = appBrowserPopupVerticalPadding),
            ) {
                var itemIndex = 0
                entries.forEach { entry ->
                    when (entry) {
                        AppBrowserMenuEntry.Divider -> AppBrowserPopupDivider(palette)
                        is AppBrowserMenuEntry.Item -> {
                            AppBrowserPopupItem(
                                palette = palette,
                                item = entry.item,
                                index = itemIndex,
                                itemCount = itemCount,
                                popupProgress = popupProgress,
                                onClick = { onActionClick(entry.item.action) },
                            )
                            itemIndex += 1
                        }
                    }
                }
            }
        }
    }
}

private class AppBrowserPopupPositionProvider(
    margin: Int,
    spacing: Int,
) : PopupPositionProvider {
    private val margin = margin
    private val spacing = spacing

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val preferredX = anchorBounds.right - popupContentSize.width
        val preferredY = anchorBounds.bottom + spacing
        return IntOffset(
            x = preferredX.coerceIn(margin, maxX),
            y = preferredY.coerceIn(margin, maxY),
        )
    }
}

private fun appBrowserPopupOpenDuration(
    itemCount: Int,
): Int = 150 + 16 * itemCount.coerceAtLeast(0)

@Composable
private fun rememberAppBrowserPopupPalette(): AppBrowserPopupPalette {
    val colors = NativeDoThemeExtras.colors
    val isLightTheme = colors.backgroundBase.luminance() > 0.5f
    return remember(colors, isLightTheme) {
        if (isLightTheme) {
            AppBrowserPopupPalette(
                background = Color(0xFFF7F4ED),
                sectionGap = Color(0xFFE9E2D6),
                text = colors.textPrimary.copy(alpha = 0.97f),
                secondaryText = colors.textSecondary.copy(alpha = 0.92f),
                icon = colors.textPrimary.copy(alpha = 0.90f),
                pressed = Color(0x12000000),
            )
        } else {
            AppBrowserPopupPalette(
                background = Color(0xFF1B211F),
                sectionGap = Color(0xFF121715),
                text = colors.textPrimary.copy(alpha = 0.98f),
                secondaryText = colors.textSecondary.copy(alpha = 0.92f),
                icon = colors.textPrimary.copy(alpha = 0.90f),
                pressed = Color(0x18FFFFFF),
            )
        }
    }
}

private fun appBrowserPopupCascadeProgress(
    fullAnimationT: Float,
    position: Float,
    count: Float,
    waveLength: Float,
): Float {
    if (count <= 0f) {
        return fullAnimationT
    }
    val waveDuration = (1f / count) * min(waveLength, count)
    val waveOffset = (position / count) * (1f - waveDuration)
    return ((fullAnimationT - waveOffset) / waveDuration).coerceIn(0f, 1f)
}

@Composable
private fun AppBrowserPopupDivider(
    palette: AppBrowserPopupPalette,
) {
    Spacer(modifier = Modifier.height(appBrowserPopupDividerSpacing))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(appBrowserPopupSectionGapHeight)
            .background(palette.sectionGap),
    )
    Spacer(modifier = Modifier.height(appBrowserPopupDividerSpacing))
}

@Composable
private fun AppBrowserPopupItem(
    palette: AppBrowserPopupPalette,
    item: AppBrowserMenuItem,
    index: Int,
    itemCount: Int,
    popupProgress: Float,
    onClick: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val startTranslationPx = with(density) { appBrowserPopupChildStartTranslation.toPx() }
    val rowProgress = remember(index, itemCount, popupProgress) {
        appBrowserPopupCascadeProgress(
            fullAnimationT = popupProgress,
            position = index.toFloat(),
            count = itemCount.toFloat(),
            waveLength = appBrowserPopupCascadeWaveLength,
        )
    }
    val rowAlpha = rowProgress * if (item.enabled) 1f else 0.42f
    val rowTranslationY = (1f - rowProgress) * -startTranslationPx

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(appBrowserPopupRowHeight)
            .graphicsLayer {
                alpha = rowAlpha
                translationY = rowTranslationY
            }
            .background(
                color = if (pressed && item.enabled) {
                    palette.pressed
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = item.enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.action.icon(),
            contentDescription = null,
            tint = palette.icon,
            modifier = Modifier.size(19.dp),
        )
        Spacer(modifier = Modifier.width(11.dp))
        Text(
            text = item.action.label(),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.text,
        )
    }
}

@Composable
private fun AppBrowserHistoryPopupItem(
    palette: AppBrowserPopupPalette,
    entry: AppBrowserHistoryEntry,
    index: Int,
    itemCount: Int,
    popupProgress: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val startTranslationPx = with(density) { appBrowserPopupChildStartTranslation.toPx() }
    val rowProgress = remember(index, itemCount, popupProgress) {
        appBrowserPopupCascadeProgress(
            fullAnimationT = popupProgress,
            position = index.toFloat(),
            count = itemCount.toFloat(),
            waveLength = appBrowserPopupCascadeWaveLength,
        )
    }
    val rowAlpha = rowProgress
    val rowTranslationY = (1f - rowProgress) * -startTranslationPx

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = appBrowserHistoryPopupRowMinHeight)
            .graphicsLayer {
                alpha = rowAlpha
                translationY = rowTranslationY
            }
            .background(
                color = if (pressed) {
                    palette.pressed
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = palette.icon,
            modifier = Modifier.size(17.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatAppBrowserHistoryVisitedAt(entry.visitedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatAppBrowserHistoryVisitedAt(
    visitedAtMillis: Long,
): String {
    if (visitedAtMillis <= 0L) {
        return ""
    }
    val now = Calendar.getInstance()
    val visitedAt = Calendar.getInstance().apply {
        timeInMillis = visitedAtMillis
    }
    val pattern = if (
        now.get(Calendar.YEAR) == visitedAt.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == visitedAt.get(Calendar.DAY_OF_YEAR)
    ) {
        "HH:mm"
    } else {
        "MM-dd HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(visitedAtMillis))
}

@Composable
private fun AppBrowserMenuAction.label(): String = when (this) {
    AppBrowserMenuAction.History -> androidx.compose.ui.res.stringResource(R.string.app_browser_history)
    AppBrowserMenuAction.Forward -> androidx.compose.ui.res.stringResource(R.string.app_browser_forward)
    AppBrowserMenuAction.Refresh -> androidx.compose.ui.res.stringResource(R.string.app_browser_refresh)
    AppBrowserMenuAction.Share -> androidx.compose.ui.res.stringResource(R.string.app_browser_share)
    AppBrowserMenuAction.OpenInBrowser -> androidx.compose.ui.res.stringResource(R.string.app_browser_open_in_browser)
    AppBrowserMenuAction.CopyLink -> androidx.compose.ui.res.stringResource(R.string.app_browser_copy_link)
    AppBrowserMenuAction.Settings -> androidx.compose.ui.res.stringResource(R.string.app_browser_settings)
    AppBrowserMenuAction.Close -> androidx.compose.ui.res.stringResource(R.string.app_browser_close)
}

private fun AppBrowserMenuAction.icon(): ImageVector = when (this) {
    AppBrowserMenuAction.History -> Icons.Outlined.History
    AppBrowserMenuAction.Forward -> Icons.AutoMirrored.Outlined.ArrowForward
    AppBrowserMenuAction.Refresh -> Icons.Outlined.Refresh
    AppBrowserMenuAction.Share -> Icons.Outlined.Share
    AppBrowserMenuAction.OpenInBrowser -> Icons.Outlined.OpenInBrowser
    AppBrowserMenuAction.CopyLink -> Icons.Outlined.ContentCopy
    AppBrowserMenuAction.Settings -> Icons.Outlined.Settings
    AppBrowserMenuAction.Close -> Icons.Outlined.Close
}

private val appBrowserPopupWidth = 200.dp
private val appBrowserHistoryPopupWidth = 252.dp
private val appBrowserHistoryPopupMaxHeight = 320.dp
private val appBrowserHistoryPopupRowMinHeight = 54.dp
private val appBrowserPopupRowHeight = 46.dp
private val appBrowserPopupVerticalPadding = 5.dp
private val appBrowserPopupMargin = 12.dp
private val appBrowserPopupSpacing = 6.dp
private val appBrowserPopupCornerRadius = 14.dp
private val appBrowserPopupChildStartTranslation = 6.dp
private val appBrowserPopupCloseTranslation = 8.dp
private val appBrowserPopupDividerSpacing = 4.dp
private val appBrowserPopupSectionGapHeight = 10.dp
private val appBrowserProgressHeight = 3.dp
private const val appBrowserPopupCascadeWaveLength = 4f
private const val appBrowserPopupCloseDurationMillis = 150
