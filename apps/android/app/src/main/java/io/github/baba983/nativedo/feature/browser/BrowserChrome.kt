package io.github.baba983.nativedo.feature.browser

import android.graphics.Bitmap
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.ui.NativeDoAnimatedLabel
import io.github.baba983.nativedo.core.ui.NativeDoAnchoredPopup
import io.github.baba983.nativedo.core.ui.NativeDoBackIcon
import io.github.baba983.nativedo.core.ui.NativeDoChromeIconButton
import io.github.baba983.nativedo.core.ui.NativeDoGlassBar
import io.github.baba983.nativedo.core.ui.NativeDoSearchField
import io.github.baba983.nativedo.core.ui.NativeDoPopupPalette
import io.github.baba983.nativedo.core.ui.NativeDoPopupAnchorHighlight
import io.github.baba983.nativedo.core.ui.NativeDoPopupMenu
import io.github.baba983.nativedo.core.ui.NativeDoPopupMenuDivider
import io.github.baba983.nativedo.core.ui.NativeDoPopupMenuItem
import io.github.baba983.nativedo.core.ui.nativeDoChromeIconButtonContainerColor
import io.github.baba983.nativedo.core.ui.nativeDoChromeIconButtonContentAlpha
import io.github.baba983.nativedo.core.ui.nativeDoPopupCascadeProgress
import io.github.baba983.nativedo.core.ui.rememberNativeDoPopupPalette
import io.github.baba983.nativedo.ui.AppTopBarHorizontalPadding
import io.github.baba983.nativedo.ui.AppTopBarRowMinHeight
import io.github.baba983.nativedo.ui.AppTopBarVerticalPadding
import io.github.baba983.nativedo.ui.NativeDoGlassSurface
import io.github.baba983.nativedo.ui.theme.NativeDoDuration
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.delay

internal enum class BrowserMenuAction {
    AddBookmark,
    RemoveBookmark,
    InstantView,
    Search,
    History,
    Forward,
    Refresh,
    Share,
    OpenInBrowser,
    CopyLink,
    Settings,
    Minimize,
    Close,
}

internal sealed interface BrowserMenuEntry {
    data class Item(val item: BrowserMenuItem) : BrowserMenuEntry
    data object Divider : BrowserMenuEntry
}

internal data class BrowserMenuItem(
    val action: BrowserMenuAction,
    val enabled: Boolean = true,
)

internal data class BrowserPopupDividerMetrics(
    val spacing: androidx.compose.ui.unit.Dp,
    val gapHeight: androidx.compose.ui.unit.Dp,
)

internal data class BrowserTopBarCollapseMetrics(
    val menuScale: Float,
    val menuTranslationProgress: Float,
    val titleScale: Float,
    val titleTranslationProgress: Float,
)

internal fun browserPopupDividerMetrics(): BrowserPopupDividerMetrics = BrowserPopupDividerMetrics(
    spacing = 2.dp,
    gapHeight = 6.dp,
)

internal fun buildBrowserTopBarCollapseMetrics(
    headerCollapseProgress: Float,
    scrollProgress: Float,
): BrowserTopBarCollapseMetrics {
    val resolvedHeaderProgress = headerCollapseProgress.coerceIn(0f, 1f)
    val resolvedScrollProgress = scrollProgress.coerceIn(0f, 1f)
    val expandedHeight = BrowserTopBarOverlayHeight.value.coerceAtLeast(1f)
    val collapsedHeight = BrowserCollapsedTopBarOverlayHeight.value.coerceIn(0f, expandedHeight)
    val currentHeight = expandedHeight - ((expandedHeight - collapsedHeight) * resolvedHeaderProgress)
    val menuScale = sqrt((currentHeight / expandedHeight).coerceIn(0f, 1f).toDouble()).toFloat()
    return BrowserTopBarCollapseMetrics(
        menuScale = menuScale,
        menuTranslationProgress = (1f - menuScale).coerceIn(0f, 1f),
        titleScale = menuScale * (1f - (0.03f * resolvedScrollProgress)),
        titleTranslationProgress = resolvedHeaderProgress,
    )
}

internal fun buildAppBrowserScrubbedProgress(
    currentProgress: Float,
    deltaFraction: Float,
): Float = (currentProgress + deltaFraction).coerceIn(0f, 1f)

internal data class BrowserHistoryEntry(
    val title: String,
    val url: String,
    val offset: Int?,
    val host: String,
    val visitedAtMillis: Long,
    val favicon: Bitmap? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserTopBar(
    chrome: BrowserChromeModel,
    search: BrowserSearchUiState,
    address: BrowserAddressUiState,
    topInset: Dp = 0.dp,
    onScrollScrub: (Float) -> Unit = {},
    onAction: (BrowserAction) -> Unit,
) {
    val colors = NativeDoThemeExtras.colors
    val view = LocalView.current
    val hasBackHistoryEntries = chrome.backHistoryEntries.isNotEmpty()
    val hasBrowserHistoryEntries = chrome.browserHistoryEntries.isNotEmpty()
    val chromeScene = chrome.scene
    val progress = chrome.progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = if (progress >= 1f) 180 else 220,
            easing = NativeDoEasing.Standard,
        ),
        label = "appBrowserLoadingProgress",
    )
    val isSearching = chromeScene == BrowserChromeScene.Search
    val scrollProgress = if (isSearching) {
        0f
    } else {
        chrome.scrollProgress.coerceIn(0f, 1f)
    }
    val showProgress = chrome.isLoadingProgressVisible
    val scrollChromeState = remember(scrollProgress, showProgress) {
        buildBrowserScrollChromeBackgroundState(
            scrollProgress = scrollProgress,
            isLoadingVisible = showProgress,
        )
    }
    val topBarGlassColor by animateColorAsState(
        targetValue = if (chrome.chromeColor != Color.Unspecified) {
            chrome.chromeColor
        } else {
            colors.glassBackgroundStrong.copy(alpha = 0.84f)
        },
        animationSpec = tween(
            durationMillis = 200,
            easing = NativeDoEasing.Standard,
        ),
        label = "appBrowserTopBarGlassColor",
    )
    val scrollProgressFillColor = colors.accent.copy(alpha = scrollChromeState.fillAlpha)
    val topBarAlpha = chrome.alpha
    val topBarTranslationX = chrome.translationX
    val headerCollapseProgress = chrome.headerCollapseProgress.coerceIn(0f, 1f)
    val topBarOverlayHeight = lerp(
        start = BrowserTopBarOverlayHeight,
        stop = BrowserCollapsedTopBarOverlayHeight,
        fraction = headerCollapseProgress,
    )
    val topBarVerticalPadding = lerp(
        start = AppTopBarVerticalPadding,
        stop = BrowserCollapsedTopBarVerticalPadding,
        fraction = headerCollapseProgress,
    )
    val isInSubPanel = chromeScene == BrowserChromeScene.Subpanel
    val isAddressEditing = chromeScene == BrowserChromeScene.AddressEditing
    val historyExpanded = chromeScene == BrowserChromeScene.HistoryPopup
    val menuExpanded = chromeScene == BrowserChromeScene.OverflowMenu
    val showMenuButton = chromeScene == BrowserChromeScene.Browse ||
        chromeScene == BrowserChromeScene.HistoryPopup ||
        chromeScene == BrowserChromeScene.OverflowMenu
    var searchFocusToken by remember { mutableIntStateOf(0) }
    var addressFocusToken by remember { mutableIntStateOf(0) }
    val showBackButton = chromeScene != BrowserChromeScene.Browse &&
        chromeScene != BrowserChromeScene.OverflowMenu
    val showCloseButton = !showBackButton
    val searchTransition = updateTransition(
        targetState = isSearching,
        label = "appBrowserSearchMode",
    )
    val searchingProgress by searchTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) NativeDoDuration.BrowserSearchEnter else NativeDoDuration.BrowserSearchExit,
                easing = NativeDoEasing.Standard,
            )
        },
        label = "appBrowserSearchingProgress",
    ) { searching ->
        if (searching) 1f else 0f
    }
    val searchTitleScale by searchTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) NativeDoDuration.BrowserSearchEnter else NativeDoDuration.ContentExit,
                easing = NativeDoEasing.Standard,
            )
        },
        label = "appBrowserSearchTitleScale",
    ) { searching ->
        if (searching) 0.985f else 1f
    }
    val searchScale by searchTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) NativeDoDuration.BrowserSearchEnter else NativeDoDuration.ContentExit,
                easing = NativeDoEasing.Standard,
            )
        },
        label = "appBrowserSearchFieldScale",
    ) { searching ->
        if (searching) 1f else 0.985f
    }
    val backCloseTransition = updateTransition(
        targetState = showCloseButton,
        label = "appBrowserBackCloseState",
    )
    val backCloseProgress by backCloseTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = NativeDoDuration.BrowserOpen,
                easing = NativeDoEasing.Standard,
            )
        },
        label = "appBrowserBackCloseProgress",
    ) { closeVisible ->
        if (closeVisible) 1f else 0f
    }
    val searchClearVisible = search.query.isNotEmpty()
    val searchClearProgress by animateFloatAsState(
        targetValue = if (searchClearVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (searchClearVisible) NativeDoDuration.AnchorReveal else 100,
            easing = NativeDoEasing.Standard,
        ),
        label = "appBrowserSearchClearProgress",
    )
    val addressTransition = updateTransition(
        targetState = isAddressEditing,
        label = "appBrowserAddressMode",
    )
    val addressingProgress by addressTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) NativeDoDuration.BrowserAddressEnter else NativeDoDuration.BrowserAddressExit,
                easing = if (targetState) {
                    NativeDoEasing.Standard
                } else {
                    NativeDoEasing.BrowserDismiss
                },
            )
        },
        label = "appBrowserAddressingProgress",
    ) { editing ->
        if (editing) 1f else 0f
    }
    val topBarRevealProgress = 1f - addressingProgress
    val topBarExitOffset = with(LocalDensity.current) { 14.dp.toPx() }
    val addressSceneExitOffset = with(LocalDensity.current) { 10.dp.toPx() }
    val collapseMetrics = remember(headerCollapseProgress, scrollProgress) {
        buildBrowserTopBarCollapseMetrics(
            headerCollapseProgress = headerCollapseProgress,
            scrollProgress = scrollProgress,
        )
    }
    val menuTranslationX = with(LocalDensity.current) { 42.dp.toPx() * collapseMetrics.menuTranslationProgress }
    val menuTranslationY = with(LocalDensity.current) { (-12).dp.toPx() * collapseMetrics.menuTranslationProgress }
    val titleTranslationY = with(LocalDensity.current) { (-4).dp.toPx() * collapseMetrics.titleTranslationProgress }

    val menuEntries = remember(
        chrome.canGoForward,
        hasBrowserHistoryEntries,
        chrome.isBookmarked,
        chrome.instantView,
    ) {
        buildBrowserMenuEntries(
            canGoForward = chrome.canGoForward,
            hasBrowserHistory = hasBrowserHistoryEntries,
            isBookmarked = chrome.isBookmarked,
            instantViewActionAvailability = chrome.instantView.actionAvailability,
        )
    }

    LaunchedEffect(chrome.backHistoryEntries) {
        if (chrome.backHistoryEntries.isEmpty()) {
            onAction(BrowserAction.SetHistoryPopupVisible(false))
        }
    }

    LaunchedEffect(isSearching, isInSubPanel, isAddressEditing) {
        if (isSearching || isInSubPanel || isAddressEditing) {
            onAction(BrowserAction.SetOverflowMenuVisible(false))
        }
        if (isSearching) {
            onAction(BrowserAction.SetHistoryPopupVisible(false))
            delay(appBrowserSearchEnterDurationMillis.toLong())
            searchFocusToken += 1
        }
        if (isAddressEditing) {
            onAction(BrowserAction.SetHistoryPopupVisible(false))
            addressFocusToken += 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = topBarAlpha
                translationX = topBarTranslationX
            },
    ) {
        if (isAddressEditing || addressingProgress > 0f) {
            BrowserAddressScene(
                chrome = chrome,
                historyExpanded = historyExpanded,
                hasBackHistoryEntries = hasBackHistoryEntries,
                address = address,
                focusToken = addressFocusToken,
                topInset = topInset,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = addressingProgress
                        translationY = addressSceneExitOffset * topBarRevealProgress
                    },
            )
        }

        if (!isAddressEditing || topBarRevealProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = topBarRevealProgress
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = topBarOverlayHeight)
                        .drawWithContent {
                            // 绘制背景色
                            drawRect(color = topBarGlassColor)
                            // 绘制滚动进度填充
                            if (scrollChromeState.fillFraction > 0f) {
                                drawRect(
                                    color = scrollProgressFillColor,
                                    size = Size(
                                        width = size.width * scrollChromeState.fillFraction,
                                        height = size.height,
                                    ),
                                )
                            }
                            // 绘制内容
                            drawContent()
                            // 绘制底部细阴影线 (对齐 Telegram: max(0.66dp, 1px))
                            val shadowAlpha = scrollChromeState.dividerAlpha
                            if (shadowAlpha > 0f) {
                                val shadowHeight = maxOf(0.66f.dp.toPx(), 1f)
                                drawRect(
                                    color = Color.Black.copy(alpha = shadowAlpha),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        0f,
                                        size.height - shadowHeight,
                                    ),
                                    size = Size(size.width, shadowHeight),
                                )
                            }
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = topBarExitOffset * addressingProgress
                            },
                    ) {
                        Spacer(modifier = Modifier.height(topInset))
                        NativeDoGlassBar(
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = BrowserTopBarRowMinHeight,
                            contentPadding = PaddingValues(
                                horizontal = AppTopBarHorizontalPadding,
                                vertical = topBarVerticalPadding,
                            ),
                            sectionSpacing = 12.dp,
                            leading = {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = collapseMetrics.menuScale
                                        scaleY = collapseMetrics.menuScale
                                        translationX = menuTranslationX
                                        translationY = menuTranslationY
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    },
                                ) {
                                    BrowserHistoryAnchorHighlight(
                                        visible = historyExpanded,
                                    )
                                    BrowserChromeButton(
                                        onClick = {
                                            if (historyExpanded) {
                                                onAction(BrowserAction.SetHistoryPopupVisible(false))
                                            } else if (showCloseButton) {
                                                onAction(BrowserAction.CloseBrowser)
                                            } else {
                                                onAction(BrowserAction.NavigateBack)
                                            }
                                        },
                                        onLongClick = if (hasBackHistoryEntries && !isSearching && !isInSubPanel) {
                                            {
                                                onAction(BrowserAction.SetOverflowMenuVisible(false))
                                                onAction(BrowserAction.SetHistoryPopupVisible(true))
                                            }
                                        } else {
                                            null
                                        },
                                        animatedBackCloseProgress = backCloseProgress,
                                        containerColorOverride = if (historyExpanded) Color.Transparent else null,
                                        contentDescription = stringResource(
                                            if (showCloseButton) {
                                                R.string.app_browser_close
                                            } else if (isSearching) {
                                                R.string.app_browser_search_back
                                            } else {
                                                R.string.app_browser_back
                                            },
                                        ),
                                    )
                                    BrowserHistoryPopup(
                                        expanded = historyExpanded && hasBackHistoryEntries,
                                        entries = chrome.backHistoryEntries,
                                        onDismissRequest = {
                                            onAction(BrowserAction.SetHistoryPopupVisible(false))
                                        },
                                        onEntryClick = { entry ->
                                            onAction(BrowserAction.SetHistoryPopupVisible(false))
                                            onAction(BrowserAction.OpenHistoryEntry(entry))
                                        },
                                    )
                                }
                            },
                            title = {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!isSearching || searchingProgress < 1f) {
                                        BrowserTitlePill(
                                            title = chrome.title,
                                            subtitle = chrome.subtitle,
                                            isHttps = chrome.isHttps,
                                            onProgressScrub = if (!isSearching && !isInSubPanel) {
                                                { deltaFraction ->
                                                    onScrollScrub(deltaFraction)
                                                }
                                            } else {
                                                null
                                            },
                                            onClick = if (!isSearching && !isInSubPanel) {
                                                {
                                                    onAction(BrowserAction.StartAddressEditing)
                                                }
                                            } else {
                                                null
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    alpha = 1f - searchingProgress
                                                    scaleX = searchTitleScale * collapseMetrics.titleScale
                                                    scaleY = searchTitleScale * collapseMetrics.titleScale
                                                    translationY = titleTranslationY
                                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                                },
                                        )
                                    }
                                    if (isSearching || searchingProgress > 0f) {
                                        BrowserSearchBar(
                                            query = search.query,
                                            onQueryChange = { query ->
                                                onAction(BrowserAction.UpdateSearchQuery(query))
                                            },
                                            focusToken = searchFocusToken,
                                            isSearching = isSearching,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    alpha = searchingProgress
                                                    scaleX = searchScale
                                                    scaleY = searchScale
                                                },
                                        )
                                    }
                                }
                            },
                            trailing = {
                                Box(
                                    modifier = Modifier
                                        .width(if (showMenuButton) 92.dp else 44.dp)
                                        .graphicsLayer {
                                            scaleX = collapseMetrics.menuScale
                                            scaleY = collapseMetrics.menuScale
                                            translationX = -menuTranslationX
                                            translationY = menuTranslationY
                                            transformOrigin = TransformOrigin(1f, 0f)
                                        },
                                ) {
                                    if (showMenuButton) {
                                        Row(
                                            modifier = Modifier.align(Alignment.CenterEnd),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            BrowserChromeButton(
                                                onClick = { onAction(BrowserAction.MinimizeBrowser) },
                                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                                contentDescription = stringResource(R.string.app_browser_minimize),
                                            )
                                            Box {
                                                BrowserChromeButton(
                                                    onClick = {
                                                        onAction(BrowserAction.SetHistoryPopupVisible(false))
                                                        onAction(BrowserAction.SetOverflowMenuVisible(!menuExpanded))
                                                    },
                                                    imageVector = Icons.Outlined.MoreVert,
                                                    contentDescription = stringResource(R.string.app_browser_more_actions),
                                                    modifier = Modifier.align(Alignment.Center),
                                                )
                                            }
                                        }
                                        BrowserActionPopup(
                                            expanded = menuExpanded,
                                            entries = menuEntries,
                                            onDismissRequest = {
                                                onAction(BrowserAction.SetOverflowMenuVisible(false))
                                            },
                                            onActionClick = { action ->
                                                onAction(BrowserAction.SetOverflowMenuVisible(false))
                                                when (action) {
                                                    BrowserMenuAction.AddBookmark,
                                                    BrowserMenuAction.RemoveBookmark -> onAction(BrowserAction.ToggleBookmark)
                                                    BrowserMenuAction.InstantView -> onAction(BrowserAction.OpenInstantView)
                                                    BrowserMenuAction.Search -> onAction(BrowserAction.StartSearch)
                                                    BrowserMenuAction.History -> onAction(BrowserAction.OpenHistory)
                                                    BrowserMenuAction.Forward -> onAction(BrowserAction.GoForward)
                                                    BrowserMenuAction.Refresh -> onAction(BrowserAction.Refresh)
                                                    BrowserMenuAction.Share -> onAction(BrowserAction.Share)
                                                    BrowserMenuAction.OpenInBrowser -> onAction(BrowserAction.OpenInBrowser)
                                                    BrowserMenuAction.CopyLink -> onAction(BrowserAction.CopyLink)
                                                    BrowserMenuAction.Settings -> onAction(BrowserAction.OpenSettings)
                                                    BrowserMenuAction.Minimize -> onAction(BrowserAction.MinimizeBrowser)
                                                    BrowserMenuAction.Close -> onAction(BrowserAction.CloseBrowser)
                                                }
                                            },
                                        )
                                    } else if (isSearching) {
                                        BrowserChromeButton(
                                            onClick = {
                                                onAction(BrowserAction.UpdateSearchQuery(""))
                                            },
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.app_browser_search_clear),
                                            enabled = searchClearVisible,
                                            emphasized = searchClearVisible,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .graphicsLayer {
                                                    alpha = searchClearProgress
                                                    scaleX = 0.9f + (searchClearProgress * 0.1f)
                                                    scaleY = 0.9f + (searchClearProgress * 0.1f)
                                                },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                // 移除原来的 HorizontalDivider,改用上面的渐变阴影

                val progressMetrics = remember { browserProgressMetrics() }
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = colors.accent,
                    trackColor = colors.glassBackgroundStrong.copy(alpha = progressMetrics.trackAlpha),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = AppTopBarHorizontalPadding)
                        .height(progressMetrics.height)
                        .graphicsLayer {
                            alpha = if (showProgress) 1f else 0f
                            scaleX = if (showProgress) 1f else 0.92f
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .background(Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun BrowserAddressScene(
    chrome: BrowserChromeModel,
    historyExpanded: Boolean,
    hasBackHistoryEntries: Boolean,
    address: BrowserAddressUiState,
    focusToken: Int,
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    onAction: (BrowserAction) -> Unit,
) {
    val colors = NativeDoThemeExtras.colors
    val previewTitle = chrome.title.ifBlank {
        chrome.currentUrl.ifBlank { chrome.subtitle.orEmpty() }
    }
    val previewUrl = chrome.currentUrl.ifBlank { chrome.subtitle ?: chrome.title }

    Column(
        modifier = modifier
            .background(colors.backgroundBase)
            .padding(
                horizontal = AppTopBarHorizontalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(modifier = Modifier.height(topInset + AppTopBarVerticalPadding))
        NativeDoGlassBar(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppTopBarRowMinHeight),
            sectionSpacing = 12.dp,
            leading = {
                Box {
                    BrowserHistoryAnchorHighlight(
                        visible = historyExpanded,
                    )
                    BrowserChromeButton(
                        onClick = {
                            if (historyExpanded) {
                                onAction(BrowserAction.SetHistoryPopupVisible(false))
                            } else {
                                onAction(BrowserAction.CancelAddressEditing)
                            }
                        },
                        onLongClick = if (hasBackHistoryEntries) {
                            {
                                onAction(BrowserAction.SetHistoryPopupVisible(true))
                            }
                        } else {
                            null
                        },
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        containerColorOverride = if (historyExpanded) Color.Transparent else null,
                        contentDescription = stringResource(R.string.app_browser_back),
                    )
                    BrowserHistoryPopup(
                        expanded = historyExpanded && hasBackHistoryEntries,
                        entries = chrome.backHistoryEntries,
                        onDismissRequest = {
                            onAction(BrowserAction.SetHistoryPopupVisible(false))
                        },
                        onEntryClick = { entry ->
                            onAction(BrowserAction.SetHistoryPopupVisible(false))
                            onAction(BrowserAction.OpenHistoryEntry(entry))
                        },
                    )
                }
            },
            title = {
                BrowserAddressInput(
                    value = address.draft,
                    isEditing = address.isEditing,
                    focusToken = focusToken,
                    onValueChange = { value -> onAction(BrowserAction.UpdateAddressDraft(value)) },
                    onSubmit = { onAction(BrowserAction.SubmitAddress(address.draft)) },
                    modifier = Modifier.weight(1f),
                )
            },
        )
        BrowserAddressPreviewCard(
            title = previewTitle,
            url = previewUrl,
            favicon = chrome.favicon,
            onCopyLink = { onAction(BrowserAction.CopyLink) },
        )
        BrowserBookmarkPlaceholderCard(
            isBookmarked = chrome.isBookmarked,
            onClick = { onAction(BrowserAction.ToggleBookmark) },
        )
        if (address.suggestionSections.isNotEmpty()) {
            BrowserAddressSuggestionSections(
                sections = address.suggestionSections,
                onSuggestionClick = { suggestion ->
                    onAction(BrowserAction.ApplyAddressSuggestion(suggestion))
                },
            )
        }
    }
}

@Composable
private fun BrowserAddressSuggestionSections(
    sections: List<BrowserAddressSuggestionSection>,
    onSuggestionClick: (BrowserAddressSuggestion) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            BrowserAddressSuggestionSection(
                section = section,
                onSuggestionClick = onSuggestionClick,
            )
        }
    }
}

@Composable
private fun BrowserAddressInput(
    value: String,
    isEditing: Boolean,
    focusToken: Int,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    NativeDoGlassSurface(
        modifier = modifier,
        shape = NativeDoPillShape,
        glassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Field),
    ) {
        NativeDoGlassBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 10.dp),
            title = {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context ->
                            BrowserAddressEditText(context).apply {
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                                setSingleLine(true)
                                hint = "https://linux.do 或搜索词"
                                background = null
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(0, 0, 0, 0)
                                setTextColor(colors.textPrimary.toArgb())
                                setHintTextColor(colors.textSecondary.toArgb())
                                inputType = inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_GO
                                addTextChangedListener(
                                    object : TextWatcher {
                                        override fun beforeTextChanged(
                                            s: CharSequence?,
                                            start: Int,
                                            count: Int,
                                            after: Int,
                                        ) = Unit

                                        override fun onTextChanged(
                                            s: CharSequence?,
                                            start: Int,
                                            before: Int,
                                            count: Int,
                                        ) = Unit

                                        override fun afterTextChanged(s: Editable?) {
                                            onValueChange(s?.toString().orEmpty())
                                        }
                                    },
                                )
                                setOnEditorActionListener { _, actionId, event ->
                                    if (
                                        actionId == EditorInfo.IME_ACTION_GO ||
                                        (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)
                                    ) {
                                        onSubmit()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                if (value.isNotEmpty()) {
                                    setText(value)
                                    setSelection(text?.length ?: 0)
                                }
                            }
                        },
                        update = { editText ->
                            editText.setTextColor(colors.textPrimary.toArgb())
                            editText.setHintTextColor(colors.textSecondary.toArgb())
                            if (editText.text?.toString().orEmpty() != value) {
                                editText.setText(value)
                                editText.setSelection(editText.text?.length ?: 0)
                            }
                            if (!isEditing) {
                                editText.cancelPendingKeyboardOpen()
                            } else if (editText.appliedFocusToken != focusToken) {
                                editText.scheduleKeyboardOpen(focusToken)
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun BrowserAddressPreviewCard(
    title: String,
    url: String,
    favicon: Bitmap?,
    onCopyLink: () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.glassBackgroundStrong.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrowserAddressPreview(
                title = title,
                url = url,
                favicon = favicon,
                modifier = Modifier.weight(1f),
            )
            BrowserAddressQuickActionChip(
                icon = Icons.Outlined.ContentCopy,
                label = stringResource(R.string.app_browser_copy),
                onClick = onCopyLink,
            )
        }
    }
}

@Composable
private fun BrowserBookmarkPlaceholderCard(
    isBookmarked: Boolean,
    onClick: () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors
    val titleRes = if (isBookmarked) {
        R.string.app_browser_bookmark_saved_title
    } else {
        R.string.app_browser_bookmark_unsaved_title
    }
    val subtitleRes = if (isBookmarked) {
        R.string.app_browser_bookmark_saved_subtitle
    } else {
        R.string.app_browser_bookmark_unsaved_subtitle
    }
    val bookmarkIcon = if (isBookmarked) {
        Icons.Filled.Star
    } else {
        Icons.Outlined.StarBorder
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.glassBackgroundStrong.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = colors.glassBackground.copy(alpha = 0.58f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = bookmarkIcon,
                    contentDescription = null,
                    tint = if (isBookmarked) colors.accent else colors.textPrimary.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            BrowserAddressQuickActionChip(
                icon = bookmarkIcon,
                label = stringResource(
                    if (isBookmarked) {
                        R.string.app_browser_remove_bookmark
                    } else {
                        R.string.app_browser_add_bookmark
                    },
                ),
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun BrowserAddressPreview(
    title: String,
    url: String,
    favicon: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = colors.glassBackground.copy(alpha = 0.58f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (favicon != null) {
                Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = if (url.startsWith("https://")) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = null,
                    tint = colors.textPrimary.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrowserAddressSuggestionSection(
    section: BrowserAddressSuggestionSection,
    onSuggestionClick: (BrowserAddressSuggestion) -> Unit,
) {
    val colors = NativeDoThemeExtras.colors

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = browserAddressSuggestionSectionIcon(section.kind),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = browserAddressSuggestionSectionTitle(section.kind),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.glassBackgroundStrong.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                section.items.forEachIndexed { index, item ->
                    BrowserAddressSuggestionRow(
                        suggestion = item,
                        onClick = { onSuggestionClick(item) },
                    )
                    if (index != section.items.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserAddressSuggestionRow(
    suggestion: BrowserAddressSuggestion,
    onClick: () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = browserAddressSuggestionTitle(suggestion),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        browserAddressSuggestionSubtitle(suggestion)?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun browserAddressSuggestionSectionTitle(kind: BrowserAddressSuggestionSectionKind): String = when (kind) {
    BrowserAddressSuggestionSectionKind.QuickActions -> stringResource(R.string.app_browser_address_quick_actions)
    BrowserAddressSuggestionSectionKind.RecentSearches -> stringResource(R.string.app_browser_recent_searches)
    BrowserAddressSuggestionSectionKind.Bookmarks -> stringResource(R.string.app_browser_bookmarks)
    BrowserAddressSuggestionSectionKind.RecentLinks -> stringResource(R.string.app_browser_recent_links)
}

private fun browserAddressSuggestionSectionIcon(kind: BrowserAddressSuggestionSectionKind): ImageVector = when (kind) {
    BrowserAddressSuggestionSectionKind.QuickActions -> Icons.Outlined.MoreVert
    BrowserAddressSuggestionSectionKind.RecentSearches -> Icons.Outlined.Search
    BrowserAddressSuggestionSectionKind.Bookmarks -> Icons.Outlined.StarBorder
    BrowserAddressSuggestionSectionKind.RecentLinks -> Icons.Outlined.History
}

private fun browserAddressSuggestionTitle(suggestion: BrowserAddressSuggestion): String = when (suggestion) {
    is BrowserAddressSuggestion.RecentLink -> suggestion.title
    is BrowserAddressSuggestion.Bookmark -> suggestion.title
    is BrowserAddressSuggestion.RecentSearch -> suggestion.query
    is BrowserAddressSuggestion.QuickAction -> when (suggestion.action) {
        BrowserAddressQuickAction.CopyCurrentLink -> "Copy current link"
        BrowserAddressQuickAction.ToggleBookmark -> "Toggle bookmark"
    }
}

private fun browserAddressSuggestionSubtitle(suggestion: BrowserAddressSuggestion): String? = when (suggestion) {
    is BrowserAddressSuggestion.RecentLink -> suggestion.url
    is BrowserAddressSuggestion.Bookmark -> suggestion.url
    is BrowserAddressSuggestion.RecentSearch -> null
    is BrowserAddressSuggestion.QuickAction -> null
}

@Composable
private fun BrowserAddressQuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = when {
            !enabled -> colors.glassBackground.copy(alpha = 0.36f)
            pressed -> colors.glassBackground.copy(alpha = 0.82f)
            else -> colors.glassBackground.copy(alpha = 0.58f)
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textPrimary.copy(alpha = if (enabled) 1f else 0.42f),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary.copy(alpha = if (enabled) 1f else 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private class BrowserAddressEditText(
    context: android.content.Context,
) : AppCompatEditText(context) {
    var appliedFocusToken: Int = Int.MIN_VALUE

    fun scheduleKeyboardOpen(focusToken: Int) {
        appliedFocusToken = focusToken
        requestFocus()
        setSelection(text?.length ?: 0)
        showSoftKeyboard(this)
    }

    fun cancelPendingKeyboardOpen() {
        clearFocus()
        hideSoftKeyboard(this)
        appliedFocusToken = Int.MIN_VALUE
    }
}

@Composable
internal fun BrowserChromeButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    imageVector: ImageVector? = null,
    animatedBackCloseProgress: Float? = null,
    contentDescription: String,
    enabled: Boolean = true,
    emphasized: Boolean = true,
    containerColorOverride: Color? = null,
    modifier: Modifier = Modifier,
) {
    if (animatedBackCloseProgress != null) {
        val colors = NativeDoThemeExtras.colors
        val glassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome)
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val contentTint = colors.textPrimary.copy(
            alpha = nativeDoChromeIconButtonContentAlpha(
                enabled = enabled,
                emphasized = emphasized,
            ),
        )
        Surface(
            modifier = modifier.size(44.dp),
            shape = CircleShape,
            color = nativeDoChromeIconButtonContainerColor(
                glassSpec = glassSpec,
                enabled = enabled,
                emphasized = emphasized,
                pressed = pressed,
                containerColorOverride = containerColorOverride,
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        enabled = enabled,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                NativeDoBackIcon(
                    progress = animatedBackCloseProgress,
                    tint = contentTint,
                    contentDescription = contentDescription,
                )
            }
        }
    } else if (imageVector != null) {
        NativeDoChromeIconButton(
            imageVector = imageVector,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            onLongClick = onLongClick,
            emphasized = emphasized,
            containerColorOverride = containerColorOverride,
        )
    }
}

@Composable
internal fun BrowserTitlePill(
    title: String,
    subtitle: String?,
    isHttps: Boolean,
    onClick: (() -> Unit)? = null,
    onProgressScrub: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val view = LocalView.current
    val statusTint = if (isHttps) colors.accent else colors.textSecondary
    val resolvedTitle = title.ifBlank { " " }
    val interactionSource = remember { MutableInteractionSource() }

    NativeDoGlassSurface(
        modifier = modifier
            .pointerInput(onProgressScrub) {
                if (onProgressScrub == null) {
                    return@pointerInput
                }
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (size.width > 0) {
                            onProgressScrub(dragAmount.x / (0.8f * size.width))
                        }
                    },
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = NativeDoPillShape,
        glassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome),
    ) {
        NativeDoGlassBar(
            modifier = Modifier.fillMaxWidth(),
            minHeight = 0.dp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
            sectionSpacing = 8.dp,  // 从 10dp 减小到 8dp
            leading = {
                Box(
                    modifier = Modifier
                        .size(22.dp)
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
                        modifier = Modifier.size(11.dp),
                    )
                }
            },
            title = {
                NativeDoAnimatedLabel(
                    title = resolvedTitle,
                    subtitle = subtitle,
                    modifier = Modifier.weight(1f),
                    titleStyle = MaterialTheme.typography.labelLarge,
                    subtitleStyle = MaterialTheme.typography.labelSmall,
                    titleColor = colors.textPrimary,
                    subtitleColor = colors.textSecondary,
                )
            },
        )
    }
}

@Composable
internal fun BrowserSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusToken: Int,
    isSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    val searchHint = stringResource(R.string.app_browser_search_placeholder)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSearching, focusToken) {
        if (isSearching) {
            delay(appBrowserSearchKeyboardDelayMillis)
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    NativeDoSearchField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = searchHint,
        enabled = isSearching,
        focusRequester = focusRequester,
        onSubmit = {
            keyboardController?.hide()
        },
        glassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Field),
    )
}

private fun showSoftKeyboard(view: AppCompatEditText): Boolean {
    val inputMethodManager = view.context.getSystemService(InputMethodManager::class.java)
    return inputMethodManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) == true
}

private fun hideSoftKeyboard(view: AppCompatEditText) {
    val inputMethodManager = view.context.getSystemService(InputMethodManager::class.java)
    inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
}

private const val appBrowserSearchEnterDurationMillis = NativeDoDuration.BrowserSearchEnter
private const val appBrowserSearchKeyboardDelayMillis = 48L

@Composable
internal fun BrowserSearchPanel(
    state: BrowserSearchPanelState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
        color = colors.backgroundElevated.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            HorizontalDivider(
                color = colors.glassStrokeStrong.copy(alpha = 0.32f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.showsNoResults) {
                        stringResource(R.string.app_browser_search_no_results)
                    } else {
                        state.matchText
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (state.canNavigate) colors.textPrimary else colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                BrowserSearchActionButton(
                    onClick = onPrevious,
                    enabled = state.canNavigate,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.app_browser_search_previous),
                        tint = colors.textPrimary.copy(alpha = if (state.canNavigate) 1f else 0.38f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                BrowserSearchActionButton(
                    onClick = onNext,
                    enabled = state.canNavigate,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.app_browser_search_next),
                        tint = colors.textPrimary.copy(alpha = if (state.canNavigate) 1f else 0.38f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserSearchActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Surface(
        shape = CircleShape,
        color = when {
            !enabled -> Color.Transparent
            pressed -> colors.glassBackgroundStrong.copy(alpha = 0.96f)
            else -> Color.Transparent
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    enabled = enabled,
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun BrowserHistoryPopup(
    expanded: Boolean,
    entries: List<BrowserHistoryEntry>,
    onDismissRequest: () -> Unit,
    onEntryClick: (BrowserHistoryEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        return
    }

    val popupGlass = rememberBrowserPopupGlass()
    val palette = rememberNativeDoPopupPalette(glassSpec = popupGlass)
    NativeDoAnchoredPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        width = appBrowserHistoryPopupWidth,
        itemCount = entries.size.coerceAtMost(6),
        margin = appBrowserPopupMargin,
        spacing = appBrowserPopupSpacing,
        closeTranslationY = appBrowserPopupCloseTranslation,
        shape = RoundedCornerShape(appBrowserPopupCornerRadius),
        contentPadding = PaddingValues(vertical = 0.dp),
        glassSpec = popupGlass,
    ) { popupProgress ->
        Box(modifier = Modifier.width(appBrowserHistoryPopupWidth)) {
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
                    BrowserHistoryPopupItem(
                        palette = palette,
                        entry = entry,
                        index = index,
                        itemCount = entries.size,
                        revealProgress = popupProgress,
                        onClick = { onEntryClick(entry) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun BrowserActionPopup(
    expanded: Boolean,
    entries: List<BrowserMenuEntry>,
    onDismissRequest: () -> Unit,
    onActionClick: (BrowserMenuAction) -> Unit,
) {
    val itemCount = remember(entries) {
        entries.count { it is BrowserMenuEntry.Item }
    }
    val dividerMetrics = remember { browserPopupDividerMetrics() }
    val popupGlass = rememberBrowserPopupGlass()
    val palette = rememberNativeDoPopupPalette(glassSpec = popupGlass)
    NativeDoAnchoredPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        width = appBrowserPopupWidth,
        itemCount = itemCount,
        margin = appBrowserPopupMargin,
        spacing = appBrowserPopupSpacing,
        closeTranslationY = appBrowserPopupCloseTranslation,
        shape = RoundedCornerShape(appBrowserPopupCornerRadius),
        contentPadding = PaddingValues(vertical = 0.dp),
        glassSpec = popupGlass,
    ) { popupProgress ->
        Box(modifier = Modifier.width(appBrowserPopupWidth)) {
            Column(
                modifier = Modifier.padding(vertical = appBrowserPopupVerticalPadding),
            ) {
                var itemIndex = 0
                entries.forEach { entry ->
                    when (entry) {
                        BrowserMenuEntry.Divider -> NativeDoPopupMenuDivider(
                            spacing = dividerMetrics.spacing,
                            gapHeight = dividerMetrics.gapHeight,
                            color = browserPopupDividerColor(popupGlass),
                        )
                        is BrowserMenuEntry.Item -> {
                            NativeDoPopupMenuItem(
                                label = entry.item.action.label(),
                                icon = entry.item.action.icon(),
                                enabled = entry.item.enabled,
                                index = itemIndex,
                                itemCount = itemCount.coerceAtLeast(1),
                                popupProgress = popupProgress,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                                glassSpec = popupGlass,
                                paletteOverride = palette,
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

@Composable
private fun BrowserHistoryAnchorHighlight(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    NativeDoPopupAnchorHighlight(
        visible = visible,
        modifier = modifier,
    )
}

@Composable
private fun rememberBrowserPopupGlass(): io.github.baba983.nativedo.ui.theme.NativeDoGlassSpec {
    val basePopup = NativeDoThemeExtras.glass(NativeDoGlassRole.Popup)
    val chromeGlass = NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome)
    return remember(basePopup, chromeGlass) {
        val useLightPopup = chromeGlass.containerColor.luminance() > 0.72f
        if (useLightPopup) {
            basePopup.copy(
                containerColor = Color.White.copy(alpha = 0.985f),
                borderColor = Color.Black.copy(alpha = 0.08f),
            )
        } else {
            basePopup.copy(
                containerColor = Color(0xFF181819).copy(alpha = 0.985f),
                borderColor = Color.White.copy(alpha = 0.08f),
            )
        }
    }
}
private fun browserPopupDividerColor(
    glassSpec: io.github.baba983.nativedo.ui.theme.NativeDoGlassSpec,
): Color = if (glassSpec.containerColor.luminance() > 0.72f) {
    Color.Black.copy(alpha = 0.06f)
} else {
    Color.White.copy(alpha = 0.08f)
}

@Composable
private fun BrowserHistoryPopupItem(
    palette: NativeDoPopupPalette,
    entry: BrowserHistoryEntry,
    index: Int,
    itemCount: Int,
    revealProgress: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val startTranslationPx = with(density) { appBrowserPopupChildStartTranslation.toPx() }
    val rowProgress = remember(index, itemCount, revealProgress) {
        nativeDoPopupCascadeProgress(
            fullAnimationT = revealProgress,
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
        BrowserHistoryFavicon(
            favicon = entry.favicon,
            fallbackLabel = entry.title.ifBlank { entry.host },
            palette = palette,
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
                    text = formatBrowserHistoryVisitedAt(entry.visitedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BrowserHistoryFavicon(
    favicon: Bitmap?,
    fallbackLabel: String,
    palette: NativeDoPopupPalette,
) {
    val fallbackText = remember(fallbackLabel) {
        fallbackLabel.trim().firstOrNull()?.uppercase() ?: ""
    }
    Box(
        modifier = Modifier
            .size(17.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                color = if (favicon == null) {
                    palette.iconContainer
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(4.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (favicon != null) {
            Image(
                bitmap = favicon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
        } else {
            Text(
                text = fallbackText,
                style = MaterialTheme.typography.labelSmall,
                color = palette.icon,
                maxLines = 1,
            )
        }
    }
}

private fun formatBrowserHistoryVisitedAt(
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
private fun BrowserMenuAction.label(): String = when (this) {
    BrowserMenuAction.AddBookmark -> androidx.compose.ui.res.stringResource(R.string.app_browser_add_bookmark)
    BrowserMenuAction.RemoveBookmark -> androidx.compose.ui.res.stringResource(R.string.app_browser_remove_bookmark)
    BrowserMenuAction.InstantView -> androidx.compose.ui.res.stringResource(R.string.app_browser_instant_view)
    BrowserMenuAction.Search -> androidx.compose.ui.res.stringResource(R.string.app_browser_search)
    BrowserMenuAction.History -> androidx.compose.ui.res.stringResource(R.string.app_browser_history)
    BrowserMenuAction.Forward -> androidx.compose.ui.res.stringResource(R.string.app_browser_forward)
    BrowserMenuAction.Refresh -> androidx.compose.ui.res.stringResource(R.string.app_browser_refresh)
    BrowserMenuAction.Share -> androidx.compose.ui.res.stringResource(R.string.app_browser_share)
    BrowserMenuAction.OpenInBrowser -> androidx.compose.ui.res.stringResource(R.string.app_browser_open_in_browser)
    BrowserMenuAction.CopyLink -> androidx.compose.ui.res.stringResource(R.string.app_browser_copy_link)
    BrowserMenuAction.Settings -> androidx.compose.ui.res.stringResource(R.string.app_browser_settings)
    BrowserMenuAction.Minimize -> androidx.compose.ui.res.stringResource(R.string.app_browser_minimize)
    BrowserMenuAction.Close -> androidx.compose.ui.res.stringResource(R.string.app_browser_close)
}

private fun BrowserMenuAction.icon(): ImageVector = when (this) {
    BrowserMenuAction.AddBookmark -> Icons.Outlined.StarBorder
    BrowserMenuAction.RemoveBookmark -> Icons.Filled.Star
    BrowserMenuAction.InstantView -> Icons.Outlined.Article
    BrowserMenuAction.Search -> Icons.Outlined.Search
    BrowserMenuAction.History -> Icons.Outlined.History
    BrowserMenuAction.Forward -> Icons.AutoMirrored.Outlined.ArrowForward
    BrowserMenuAction.Refresh -> Icons.Outlined.Refresh
    BrowserMenuAction.Share -> Icons.Outlined.Share
    BrowserMenuAction.OpenInBrowser -> Icons.Outlined.OpenInBrowser
    BrowserMenuAction.CopyLink -> Icons.Outlined.ContentCopy
    BrowserMenuAction.Settings -> Icons.Outlined.Settings
    BrowserMenuAction.Minimize -> Icons.Outlined.KeyboardArrowDown
    BrowserMenuAction.Close -> Icons.Outlined.Close
}

private val appBrowserPopupWidth = 204.dp
private val appBrowserHistoryPopupWidth = 252.dp
private val appBrowserHistoryPopupMaxHeight = 320.dp
private val appBrowserHistoryPopupRowMinHeight = 54.dp
private val appBrowserPopupVerticalPadding = 2.dp
private val appBrowserPopupMargin = 12.dp
private val appBrowserPopupSpacing = 2.dp
private val appBrowserPopupCornerRadius = 18.dp
private val appBrowserPopupChildStartTranslation = 5.dp
private val appBrowserPopupCloseTranslation = 0.dp
internal data class BrowserProgressMetrics(
    val height: androidx.compose.ui.unit.Dp,
    val trackAlpha: Float,
)

internal fun browserProgressMetrics(): BrowserProgressMetrics = BrowserProgressMetrics(
    height = 2.dp,
    trackAlpha = 0.12f,
)

private const val appBrowserPopupCascadeWaveLength = 4f
