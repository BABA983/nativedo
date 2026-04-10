package io.github.baba983.nativedo.feature.browser

import android.graphics.Bitmap
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.WindowInsets
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
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

internal data class AppBrowserChromeConfig(
    val title: String,
    val subtitle: String?,
    val isHttps: Boolean,
    val progress: Float,
    val scrollProgress: Float = 0f,
    val isLoadingProgressVisible: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val backHistoryEntries: List<AppBrowserHistoryEntry>,
    val browserHistoryEntries: List<AppBrowserHistoryEntry>,
    val panel: AppBrowserPanel,
    val alpha: Float,
    val translationX: Float,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
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
    val isHistoryPopupVisible: Boolean = false,
    val onHistoryPopupVisibilityChanged: (Boolean) -> Unit = {},
    val onStartSearch: () -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onSearchClose: () -> Unit = {},
)

internal enum class AppBrowserMenuAction {
    Search,
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
    val favicon: Bitmap? = null,
)

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
    val scrollProgress = config?.scrollProgress?.coerceIn(0f, 1f) ?: 0f
    val showProgress = config?.isLoadingProgressVisible == true
    val scrollChromeState = remember(scrollProgress, showProgress) {
        buildAppBrowserScrollChromeBackgroundState(
            scrollProgress = scrollProgress,
            isLoadingVisible = showProgress,
        )
    }
    val topBarGlassColor = colors.glassBackgroundStrong.copy(alpha = 0.84f)
    val scrollProgressFillColor = colors.accent.copy(alpha = scrollChromeState.fillAlpha)
    val topBarAlpha = config?.alpha ?: 0f
    val topBarTranslationX = config?.translationX ?: 0f
    val isSearching = config?.isSearching == true
    val isInSubPanel = config?.panel?.let { panel -> panel != AppBrowserPanel.Page } == true
    val showMenuButton = !isSearching && !isInSubPanel
    var searchFocusToken by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    val historyExpanded = config?.isHistoryPopupVisible == true
    val showBackButton = isSearching || isInSubPanel || historyExpanded
    val showCloseButton = !showBackButton
    val searchTransition = updateTransition(
        targetState = isSearching,
        label = "appBrowserSearchMode",
    )
    val searchingProgress by searchTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) 320 else 220,
                easing = LinearOutSlowInEasing,
            )
        },
        label = "appBrowserSearchingProgress",
    ) { searching ->
        if (searching) 1f else 0f
    }
    val titleScale by searchTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) 320 else 180,
                easing = LinearOutSlowInEasing,
            )
        },
        label = "appBrowserSearchTitleScale",
    ) { searching ->
        if (searching) 0.985f else 1f
    }
    val searchScale by searchTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) 320 else 180,
                easing = LinearOutSlowInEasing,
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
                durationMillis = 320,
                easing = LinearOutSlowInEasing,
            )
        },
        label = "appBrowserBackCloseProgress",
    ) { closeVisible ->
        if (closeVisible) 1f else 0f
    }
    val searchClearVisible = config?.searchQuery?.isNotEmpty() == true
    val searchClearProgress by animateFloatAsState(
        targetValue = if (searchClearVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (searchClearVisible) 140 else 100,
            easing = LinearOutSlowInEasing,
        ),
        label = "appBrowserSearchClearProgress",
    )

    val menuEntries = remember(config?.canGoForward, hasBrowserHistoryEntries) {
        buildAppBrowserMenuEntries(
            canGoForward = config?.canGoForward == true,
            hasBrowserHistory = hasBrowserHistoryEntries,
        )
    }

    LaunchedEffect(config) {
        if (config == null) {
            menuExpanded = false
        } else if (config.backHistoryEntries.isEmpty()) {
            config.onHistoryPopupVisibilityChanged(false)
        }
    }

    LaunchedEffect(isSearching, isInSubPanel) {
        if (isSearching || isInSubPanel) {
            menuExpanded = false
        }
        if (isSearching) {
            config?.onHistoryPopupVisibilityChanged?.invoke(false)
            delay(appBrowserSearchEnterDurationMillis.toLong())
            searchFocusToken += 1
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
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    drawRect(color = topBarGlassColor)
                    if (scrollChromeState.fillFraction > 0f) {
                        drawRect(
                            color = scrollProgressFillColor,
                            size = Size(
                                width = size.width * scrollChromeState.fillFraction,
                                height = size.height,
                            ),
                        )
                    }
                    drawContent()
                },
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(
                modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppTopBarRowMinHeight)
                    .padding(
                        horizontal = AppTopBarHorizontalPadding,
                        vertical = AppTopBarVerticalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    AppBrowserHistoryAnchorHighlight(
                        visible = historyExpanded,
                    )
                    AppBrowserChromeButton(
                        onClick = {
                            if (historyExpanded) {
                                config?.onHistoryPopupVisibilityChanged?.invoke(false)
                            } else if (showCloseButton) {
                                config?.onClose?.invoke()
                            } else {
                                config?.onBack?.invoke()
                            }
                        },
                        onLongClick = if (hasBackHistoryEntries && !isSearching && !isInSubPanel) {
                            {
                                menuExpanded = false
                                config?.onHistoryPopupVisibilityChanged?.invoke(true)
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
                    AppBrowserHistoryPopup(
                        expanded = historyExpanded &&
                            config?.backHistoryEntries?.isNotEmpty() == true,
                        entries = config?.backHistoryEntries.orEmpty(),
                        onDismissRequest = { config?.onHistoryPopupVisibilityChanged?.invoke(false) },
                        onEntryClick = { entry ->
                            config?.onHistoryPopupVisibilityChanged?.invoke(false)
                            config?.onHistoryEntryClick?.invoke(entry)
                        },
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AppBrowserTitlePill(
                        title = config?.title.orEmpty(),
                        subtitle = config?.subtitle,
                        isHttps = config?.isHttps == true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = 1f - searchingProgress
                                scaleX = titleScale
                                scaleY = titleScale
                            },
                    )
                    if (config != null) {
                        AppBrowserSearchBar(
                            query = config.searchQuery,
                            onQueryChange = config.onSearchQueryChange,
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

                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier.width(44.dp),
                ) {
                    if (showMenuButton) {
                        AppBrowserChromeButton(
                            onClick = {
                                if (config != null) {
                                    config.onHistoryPopupVisibilityChanged(false)
                                    menuExpanded = true
                                }
                            },
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.app_browser_more_actions),
                            emphasized = menuExpanded,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        AppBrowserActionPopup(
                            expanded = menuExpanded && config != null,
                            entries = menuEntries,
                            onDismissRequest = { menuExpanded = false },
                            onActionClick = { action ->
                                menuExpanded = false
                                when (action) {
                                    AppBrowserMenuAction.Search -> config?.onStartSearch?.invoke()
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
                    } else if (isSearching) {
                        AppBrowserChromeButton(
                            onClick = { config?.onSearchQueryChange?.invoke("") },
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
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = scrollChromeState.dividerAlpha),
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            color = colors.accent,
            trackColor = colors.glassBackgroundStrong.copy(alpha = 0.18f),
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = AppTopBarHorizontalPadding)
                .height(appBrowserProgressHeight)
                .graphicsLayer {
                    alpha = if (showProgress) 1f else 0f
                    scaleX = if (showProgress) 1f else 0.92f
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(Color.Transparent),
            )
    }
}

@Composable
internal fun AppBrowserChromeButton(
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
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val contentTint = colors.textPrimary.copy(
        alpha = when {
            !enabled -> 0.38f
            !emphasized -> 0.62f
            else -> 1f
        },
    )
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = containerColorOverride ?: if (pressed) {
            colors.glassBackgroundStrong.copy(alpha = 0.94f)
        } else if (!emphasized) {
            colors.glassBackgroundStrong.copy(alpha = 0.22f)
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
                    enabled = enabled,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                animatedBackCloseProgress != null -> AppBrowserBackCloseGlyph(
                    progress = animatedBackCloseProgress,
                    tint = contentTint,
                    contentDescription = contentDescription,
                )

                imageVector != null -> Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = contentTint,
                )
            }
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
internal fun AppBrowserSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusToken: Int,
    isSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val onQueryChangeState = rememberUpdatedState(onQueryChange)
    val searchHint = stringResource(R.string.app_browser_search_placeholder)
    val isSearchingState = rememberUpdatedState(isSearching)

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
                .height(44.dp)
                .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth(),
                    factory = { context ->
                        AppBrowserSearchEditText(context).apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                            setSingleLine(true)
                            hint = searchHint
                            background = null
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, 0, 0, 0)
                            setTextColor(colors.textPrimary.toArgb())
                            setHintTextColor(colors.textSecondary.toArgb())
                            inputType = inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_SEARCH
                            setTextIsSelectable(false)
                            setOnEditorActionListener { _, actionId, event ->
                                if (
                                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                                    (event != null && (
                                        event.action == KeyEvent.ACTION_UP &&
                                            event.keyCode == KeyEvent.KEYCODE_SEARCH ||
                                            event.action == KeyEvent.ACTION_DOWN &&
                                            event.keyCode == KeyEvent.KEYCODE_ENTER
                                        ))
                                ) {
                                    hideSoftKeyboard(this)
                                }
                                false
                            }
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
                                        onQueryChangeState.value(s?.toString().orEmpty())
                                    }
                                },
                            )
                            if (query.isNotEmpty()) {
                                setText(query)
                                setSelection(text?.length ?: 0)
                            }
                        }
                    },
                    update = { editText ->
                        editText.setTextColor(colors.textPrimary.toArgb())
                        editText.setHintTextColor(colors.textSecondary.toArgb())
                        editText.visibility = if (isSearchingState.value) View.VISIBLE else View.INVISIBLE
                        if (!isSearchingState.value) {
                            editText.cancelPendingKeyboardOpen()
                        }
                        if (editText.text?.toString().orEmpty() != query) {
                            editText.setText(query)
                            editText.setSelection(editText.text?.length ?: 0)
                        }
                        if (isSearchingState.value && editText.appliedFocusToken != focusToken) {
                            editText.scheduleKeyboardOpen(focusToken)
                        }
                    },
                )
            }
        }
    }
}

private class AppBrowserSearchEditText(
    context: android.content.Context,
) : AppCompatEditText(context) {
    var appliedFocusToken: Int = Int.MIN_VALUE
    private var pendingKeyboardToken: Int = Int.MIN_VALUE
    private val showKeyboardRunnable = Runnable {
        if (pendingKeyboardToken == Int.MIN_VALUE || visibility != View.VISIBLE) {
            return@Runnable
        }
        requestFocus()
        setSelection(text?.length ?: 0)
        showSoftKeyboard(this)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && !showSoftKeyboard(this)) {
            clearFocus()
            requestFocus()
        }
        return super.onTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && pendingKeyboardToken != Int.MIN_VALUE) {
            removeCallbacks(showKeyboardRunnable)
            postDelayed(showKeyboardRunnable, appBrowserSearchKeyboardDelayMillis)
        }
    }

    fun scheduleKeyboardOpen(focusToken: Int) {
        appliedFocusToken = focusToken
        pendingKeyboardToken = focusToken
        removeCallbacks(showKeyboardRunnable)
        if (hasWindowFocus()) {
            postDelayed(showKeyboardRunnable, appBrowserSearchKeyboardDelayMillis)
        }
    }

    fun cancelPendingKeyboardOpen() {
        pendingKeyboardToken = Int.MIN_VALUE
        removeCallbacks(showKeyboardRunnable)
        clearFocus()
        hideSoftKeyboard(this)
        appliedFocusToken = Int.MIN_VALUE
    }
}

@Composable
private fun AppBrowserBackCloseGlyph(
    progress: Float,
    tint: Color,
    contentDescription: String,
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - progress
                rotationZ = -135f * progress
                scaleX = 1f - (progress * 0.08f)
                scaleY = 1f - (progress * 0.08f)
            },
        )
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                alpha = progress
                rotationZ = 90f * (1f - progress)
                scaleX = 0.92f + (progress * 0.08f)
                scaleY = 0.92f + (progress * 0.08f)
            },
        )
    }
}

private fun showSoftKeyboard(view: AppCompatEditText): Boolean {
    val inputMethodManager = view.context.getSystemService(InputMethodManager::class.java)
    return inputMethodManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) == true
}

private fun hideSoftKeyboard(view: AppCompatEditText) {
    val inputMethodManager = view.context.getSystemService(InputMethodManager::class.java)
    inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
}

private const val appBrowserSearchEnterDurationMillis = 320
private const val appBrowserSearchKeyboardDelayMillis = 48L

@Composable
internal fun AppBrowserSearchPanel(
    state: AppBrowserSearchPanelState,
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
                AppBrowserSearchActionButton(
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
                AppBrowserSearchActionButton(
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
private fun AppBrowserSearchActionButton(
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
    val backgroundRevealProgress by transition.animateFloat(
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
        label = "appBrowserHistoryPopupBackgroundReveal",
    ) { visible ->
        if (visible) 1f else 0.001f
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
                    alpha = popupProgress
                    translationY = containerTranslationY
                }
                .width(appBrowserHistoryPopupWidth)
                .drawWithContent {
                    val backgroundColor = palette.background.copy(alpha = 0.985f)
                    clipRect(
                        right = size.width,
                        bottom = size.height * max(backgroundRevealProgress, 0.001f),
                    ) {
                        drawRoundRect(
                            color = backgroundColor,
                            cornerRadius = CornerRadius(popupCornerRadiusPx, popupCornerRadiusPx),
                        )
                    }
                    clipRect(bottom = size.height * backgroundRevealProgress) {
                        this@drawWithContent.drawContent()
                    }
                }
                .clip(RoundedCornerShape(appBrowserPopupCornerRadius)),
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
                        revealProgress = backgroundRevealProgress,
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
    val backgroundRevealProgress by transition.animateFloat(
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
        label = "appBrowserPopupBackgroundReveal",
    ) { visible ->
        if (visible) 1f else 0.001f
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
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    alpha = popupProgress
                    translationY = containerTranslationY
                    scaleY = backgroundRevealProgress
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .width(appBrowserPopupWidth)
                .clip(RoundedCornerShape(appBrowserPopupCornerRadius)),
            shape = RoundedCornerShape(appBrowserPopupCornerRadius),
            color = palette.background.copy(alpha = 0.985f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
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
private fun AppBrowserHistoryAnchorHighlight(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visible) 140 else 100,
            easing = LinearOutSlowInEasing,
        ),
        label = "appBrowserHistoryAnchorAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(
            durationMillis = if (visible) 180 else 120,
            easing = LinearOutSlowInEasing,
        ),
        label = "appBrowserHistoryAnchorScale",
    )
    if (alpha <= 0f) {
        return
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                color = colors.glassBackgroundStrong.copy(alpha = 0.94f),
                shape = CircleShape,
            ),
    )
}

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
    revealProgress: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val startTranslationPx = with(density) { appBrowserPopupChildStartTranslation.toPx() }
    val rowProgress = remember(index, itemCount, revealProgress) {
        appBrowserPopupCascadeProgress(
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
        AppBrowserHistoryFavicon(
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
                    text = formatAppBrowserHistoryVisitedAt(entry.visitedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AppBrowserHistoryFavicon(
    favicon: Bitmap?,
    fallbackLabel: String,
    palette: AppBrowserPopupPalette,
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
                    palette.icon.copy(alpha = 0.12f)
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
    AppBrowserMenuAction.Search -> androidx.compose.ui.res.stringResource(R.string.app_browser_search)
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
    AppBrowserMenuAction.Search -> Icons.Outlined.Search
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
private val appBrowserPopupCloseTranslation = 6.dp
private val appBrowserPopupDividerSpacing = 4.dp
private val appBrowserPopupSectionGapHeight = 10.dp
private val appBrowserProgressHeight = 3.dp
private const val appBrowserPopupCascadeWaveLength = 4f
private const val appBrowserPopupCloseDurationMillis = 150
