package io.github.baba983.nativedo.feature.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.navigation.LinkNavigationDecision
import io.github.baba983.nativedo.core.navigation.LinkOpenPolicy
import io.github.baba983.nativedo.core.navigation.openInExternalApp
import io.github.baba983.nativedo.core.navigation.openInSystemBrowser
import io.github.baba983.nativedo.core.ui.BulletinMessage
import io.github.baba983.nativedo.core.ui.BulletinTone
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun AppBrowserRoute(
    url: String,
    onOpenLink: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val linkOpenPolicy = remember(appContainer) { LinkOpenPolicy(appContainer.baseUrl) }
    val clipboardManager = LocalClipboardManager.current
    val bulletinHostState = LocalBulletinHostState.current
    val linkCopiedText = stringResource(R.string.app_browser_link_copied)
    val colors = NativeDoThemeExtras.colors
    val browserHistory by appContainer.appBrowserHistoryStore.history.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val webViewController = rememberAppBrowserWebViewController()
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }.coerceAtLeast(1f)
    }
    val browserViewModel: AppBrowserViewModel = viewModel(
        key = "app-browser-$url",
        factory = remember(url, appContainer.baseUrl) {
            AppBrowserViewModelFactory(
                initialUrl = url,
                baseUrl = appContainer.baseUrl,
            )
        },
    )
    val browserUiState by browserViewModel.uiState.collectAsStateWithLifecycle()

    var webViewRef by remember(url) { mutableStateOf<AppBrowserWebViewView?>(null) }
    var currentUrl by remember(url) { mutableStateOf(url) }
    var currentPageTitle by remember(url) { mutableStateOf<String?>(null) }
    var scrollProgress by remember(url) { mutableStateOf(0f) }
    var loadProgressState by remember(url) { mutableStateOf(AppBrowserLoadProgressState()) }
    var canGoBack by remember(url) { mutableStateOf(false) }
    var canGoForward by remember(url) { mutableStateOf(false) }
    var isClosing by remember(url) { mutableStateOf(false) }
    var isSwipeGestureActive by remember(url) { mutableStateOf(false) }
    var swipeOffsetPx by remember(url) { mutableStateOf(0f) }
    var entrancePlayed by remember(url) { mutableStateOf(false) }
    val historyVisitMarks = remember(url) { mutableStateListOf<AppBrowserHistoryVisit>() }
    val historyFavicons = remember(url) { mutableStateMapOf<String, Bitmap>() }
    var lastRecordedUrl by remember(url) { mutableStateOf<String?>(null) }
    var lastRecordedAtMillis by remember(url) { mutableLongStateOf(0L) }
    var cookiesSizeBytes by remember { mutableLongStateOf(0L) }
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var pendingFileCallback by remember(url) { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingSslRequest by remember(url) { mutableStateOf<PendingSslRequest?>(null) }
    val routeOffsetPx = remember(url, screenWidthPx) { Animatable(screenWidthPx) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(
            result.resultCode,
            result.data,
        )
        pendingFileCallback?.onReceiveValue(uris)
        pendingFileCallback = null
    }

    LaunchedEffect(loadProgressState.isLoading, loadProgressState.displayedProgress) {
        if (!loadProgressState.isLoading || loadProgressState.displayedProgress >= 0.7f) {
            return@LaunchedEffect
        }
        delay(appBrowserFakeProgressTickDelayMillis)
        loadProgressState = loadProgressState.advanceFakeTick()
    }

    LaunchedEffect(loadProgressState.actualProgress, loadProgressState.isVisible, loadProgressState.isLoading) {
        if (loadProgressState.isLoading || !loadProgressState.isVisible || loadProgressState.actualProgress < 1f) {
            return@LaunchedEffect
        }
        delay(appBrowserProgressDismissDelayMillis)
        loadProgressState = loadProgressState.dismiss()
    }

    LaunchedEffect(screenWidthPx) {
        if (!entrancePlayed) {
            entrancePlayed = true
            routeOffsetPx.snapTo(screenWidthPx)
            routeOffsetPx.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = appBrowserRouteEnterDurationMillis,
                    easing = AppBrowserEaseOutQuint,
                ),
            )
        }
    }

    val currentRouteOffsetPx = if (isSwipeGestureActive) {
        swipeOffsetPx
    } else {
        routeOffsetPx.value
    }
    val routePresentationState = remember(currentRouteOffsetPx, screenWidthPx) {
        buildAppBrowserRoutePresentationState(
            offsetPx = currentRouteOffsetPx,
            screenWidthPx = screenWidthPx,
        )
    }

    val domain = remember(currentUrl) {
        currentUrl.toHttpUrlOrNull()?.host ?: currentUrl
    }
    val isHttps = remember(currentUrl) {
        currentUrl.toHttpUrlOrNull()?.isHttps == true
    }
    val titleOverride = currentPageTitle
        ?.trim()
        ?.takeIf { title ->
            title.isNotBlank() && !title.equals(domain, ignoreCase = true)
        }
    val titleText = titleOverride ?: domain
    val subtitleText = domain.takeIf { titleOverride != null }
    val panel = when (browserUiState.sheet) {
        AppBrowserSheet.None -> AppBrowserPanel.Page
        AppBrowserSheet.History -> AppBrowserPanel.History
        AppBrowserSheet.Settings -> AppBrowserPanel.Settings
    }
    val isSearching = browserUiState.search.isActive
    val searchQuery = browserUiState.search.query
    val searchActiveMatchOrdinal = browserUiState.search.activeMatchOrdinal
    val searchNumberOfMatches = browserUiState.search.numberOfMatches
    val historyPopupExpanded = browserUiState.isHistoryPopupVisible
    val chromeTitle = when (panel) {
        AppBrowserPanel.Page -> titleText
        AppBrowserPanel.History -> context.getString(R.string.app_browser_history)
        AppBrowserPanel.Settings -> context.getString(R.string.app_browser_settings)
    }
    val chromeSubtitle = if (panel == AppBrowserPanel.Page) subtitleText else null
    val backHistoryEntries = remember(
        webViewRef,
        currentUrl,
        currentPageTitle,
        canGoBack,
        historyVisitMarks.size,
        lastRecordedAtMillis,
    ) {
        readAppBrowserHistoryEntries(
            webView = webViewRef,
            visits = historyVisitMarks,
            favicons = historyFavicons,
        )
    }
    val browserHistoryEntries = remember(backHistoryEntries, browserHistory) {
        mergeAppBrowserHistoryEntries(
            backHistoryEntries = backHistoryEntries,
            browserHistoryEntries = browserHistory,
        )
    }
    val searchPanelState = remember(
        isSearching,
        searchQuery,
        searchActiveMatchOrdinal,
        searchNumberOfMatches,
    ) {
        buildAppBrowserSearchPanelState(
            isSearching = isSearching,
            query = searchQuery,
            activeMatchOrdinal = searchActiveMatchOrdinal,
            numberOfMatches = searchNumberOfMatches,
        )
    }
    val historyScrimAlpha by animateFloatAsState(
        targetValue = if (historyPopupExpanded) 0.48f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "appBrowserHistoryScrimAlpha",
    )
    val chromeModel = remember(
        chromeTitle,
        chromeSubtitle,
        currentUrl,
        isHttps,
        historyFavicons[currentUrl],
        canGoBack,
        canGoForward,
        loadProgressState.displayedProgress,
        scrollProgress,
        loadProgressState.isVisible,
        backHistoryEntries,
        browserHistoryEntries,
        panel,
        historyPopupExpanded,
    ) {
        AppBrowserChromeModel(
            title = chromeTitle,
            subtitle = chromeSubtitle,
            currentUrl = currentUrl,
            isHttps = isHttps,
            favicon = historyFavicons[currentUrl],
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            progress = loadProgressState.displayedProgress,
            scrollProgress = scrollProgress,
            isLoadingProgressVisible = loadProgressState.isVisible,
            backHistoryEntries = backHistoryEntries,
            browserHistoryEntries = browserHistoryEntries,
            isInSubPanel = panel != AppBrowserPanel.Page,
            alpha = 1f,
            translationX = 0f,
            isHistoryPopupVisible = historyPopupExpanded,
        )
    }

    LaunchedEffect(chromeModel) {
        browserViewModel.setChrome(chromeModel)
    }

    suspend fun refreshSettingsStorageUsage() {
        val appContext = context.applicationContext
        val usage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            readAppBrowserStorageUsage(
                appDataDir = appContext.dataDir,
                appCacheDir = appContext.cacheDir,
            )
        }
        cookiesSizeBytes = usage.cookiesSizeBytes
        cacheSizeBytes = usage.cacheSizeBytes
    }

    fun requestClose(durationMillis: Int = appBrowserRouteExitDurationMillis) {
        if (isClosing) {
            return
        }
        isClosing = true
        coroutineScope.launch {
            if (isSwipeGestureActive) {
                routeOffsetPx.snapTo(swipeOffsetPx)
                isSwipeGestureActive = false
            }
            routeOffsetPx.animateTo(
                targetValue = screenWidthPx,
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = AppBrowserEaseOutQuint,
                ),
            )
            onClose()
        }
    }

    fun navigateBack() {
        if (browserUiState.chrome.canGoBack) {
            webViewController.goBack()
            return
        }
        requestClose()
    }

    fun navigateHistory(entry: AppBrowserHistoryEntry) {
        val offset = entry.offset
        if (offset != null) {
            webViewRef
                ?.takeIf { webView -> webView.canGoBackOrForward(offset) }
                ?.goBackOrForward(offset)
                ?.also { return }
        }
        if (entry.url != currentUrl) {
            webViewController.loadUrl(entry.url)
        }
    }

    fun startSearch() {
        browserViewModel.onAction(AppBrowserAction.StartSearch)
    }

    fun updateSearchQuery(query: String) {
        browserViewModel.onAction(AppBrowserAction.UpdateSearchQuery(query))
        if (query.isBlank()) {
            browserViewModel.onAction(
                AppBrowserAction.UpdateSearchMatches(
                    activeMatchOrdinal = 0,
                    numberOfMatches = 0,
                ),
            )
            webViewController.clearMatches()
        } else {
            webViewController.findAllAsync(query)
        }
    }

    fun searchNext() {
        webViewController.findNext(true)
    }

    fun searchPrevious() {
        webViewController.findNext(false)
    }

    fun closeSearch() {
        browserViewModel.onAction(AppBrowserAction.CloseSearch)
        webViewController.clearMatches()
    }

    val handleNavigation: (String) -> Boolean = { requestUrl ->
        when (val decision = linkOpenPolicy.resolve(requestUrl)) {
            is LinkNavigationDecision.NativeTopic,
            is LinkNavigationDecision.NativeUser -> {
                onOpenLink(requestUrl)
                true
            }

            is LinkNavigationDecision.ExternalApp -> {
                val openedInExternalApp = context.openInExternalApp(decision.url)
                when {
                    openedInExternalApp -> true
                    decision.url.toHttpUrlOrNull() != null -> false
                    else -> true
                }
            }

            is LinkNavigationDecision.AppBrowser -> false

            is LinkNavigationDecision.SystemBrowser -> context.openInSystemBrowser(decision.url)
        }
    }

    fun handleBrowserAction(action: AppBrowserAction) {
        when (action) {
            AppBrowserAction.NavigateBack -> {
                when {
                    browserUiState.address.isEditing -> browserViewModel.onAction(AppBrowserAction.CancelAddressEditing)
                    historyPopupExpanded -> browserViewModel.onAction(AppBrowserAction.SetHistoryPopupVisible(false))
                    isSearching -> closeSearch()
                    panel != AppBrowserPanel.Page -> browserViewModel.onAction(AppBrowserAction.CloseSheet)
                    else -> navigateBack()
                }
            }

            AppBrowserAction.CloseBrowser -> requestClose()
            AppBrowserAction.StartAddressEditing,
            AppBrowserAction.OpenAddressSheet -> {
                browserViewModel.startAddressEditing(
                    currentUrl = currentUrl,
                    recentEntries = browserHistory,
                )
            }

            AppBrowserAction.OpenHistory,
            AppBrowserAction.OpenSettings,
            AppBrowserAction.CancelAddressEditing,
            AppBrowserAction.DismissSheet,
            AppBrowserAction.CloseSheet,
            AppBrowserAction.StartSearch,
            is AppBrowserAction.UpdateSearchMatches,
            is AppBrowserAction.UpdateAddressDraft,
            is AppBrowserAction.SetHistoryPopupVisible -> browserViewModel.onAction(action)

            AppBrowserAction.CloseSearch -> closeSearch()
            is AppBrowserAction.SubmitAddress -> {
                browserViewModel.onAction(action)
                browserViewModel.lastSubmittedAddress?.let(webViewController::loadUrl)
            }
            is AppBrowserAction.UpdateSearchQuery -> updateSearchQuery(action.query)
            is AppBrowserAction.OpenHistoryEntry -> {
                browserViewModel.onAction(AppBrowserAction.CloseSheet)
                navigateHistory(action.entry)
            }

            AppBrowserAction.GoForward -> webViewController.goForward()
            AppBrowserAction.Refresh -> webViewController.reload()
            AppBrowserAction.Share -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                }
                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.app_browser_share),
                    ),
                )
            }

            AppBrowserAction.OpenInBrowser -> context.openInSystemBrowser(currentUrl)
            AppBrowserAction.CopyLink -> {
                clipboardManager.setText(AnnotatedString(currentUrl))
                bulletinHostState?.show(
                    BulletinMessage(
                        title = linkCopiedText,
                        tone = BulletinTone.Success,
                    ),
                )
            }
        }
    }

    BackHandler {
        handleBrowserAction(AppBrowserAction.NavigateBack)
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            pendingSslRequest?.handler?.cancel()
            pendingSslRequest = null
            appContainer.cookieSyncBridge.syncFromWebView(appContainer.baseUrl)
        }
    }
    LaunchedEffect(panel) {
        if (panel == AppBrowserPanel.Settings) {
            refreshSettingsStorageUsage()
        }
    }
    LaunchedEffect(panel, isSearching, historyPopupExpanded) {
        if ((panel != AppBrowserPanel.Page || isSearching) && historyPopupExpanded) {
            browserViewModel.onAction(AppBrowserAction.SetHistoryPopupVisible(false))
        }
    }

    AppBrowserScreen(
        uiState = browserUiState,
        onAction = ::handleBrowserAction,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundBase)
            .graphicsLayer {
                alpha = routePresentationState.alpha
                translationX = currentRouteOffsetPx
            },
        sheetContent = {
            AnimatedContent(
                targetState = panel,
                transitionSpec = {
                    val isReturningToPage = targetState == AppBrowserPanel.Page
                    (
                        slideInHorizontally(
                            animationSpec = tween(
                                durationMillis = 260,
                                easing = LinearOutSlowInEasing,
                            ),
                            initialOffsetX = { fullWidth ->
                                if (isReturningToPage) -fullWidth / 10 else fullWidth / 7
                            },
                        ) + fadeIn(
                            animationSpec = tween(
                                durationMillis = 180,
                                easing = LinearOutSlowInEasing,
                            ),
                        )
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = 180,
                                easing = LinearOutSlowInEasing,
                            ),
                            targetOffsetX = { fullWidth ->
                                if (isReturningToPage) fullWidth / 10 else -fullWidth / 9
                            },
                        ) + fadeOut(
                            animationSpec = tween(
                                durationMillis = 120,
                                easing = LinearOutSlowInEasing,
                            ),
                        ),
                    )
                },
                label = "appBrowserSubPanel",
                modifier = Modifier.fillMaxSize(),
            ) { currentPanel ->
                when (currentPanel) {
                    AppBrowserPanel.History -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.backgroundBase.copy(alpha = 0.98f)),
                        ) {
                            AppBrowserHistoryPanel(
                                entries = browserHistoryEntries,
                                onEntryClick = { entry ->
                                    browserViewModel.onAction(AppBrowserAction.CloseSheet)
                                    navigateHistory(entry)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    AppBrowserPanel.Settings -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.backgroundBase.copy(alpha = 0.98f)),
                        ) {
                            AppBrowserSettingsPanel(
                                historyCount = browserHistory.size,
                                cookiesSizeBytes = cookiesSizeBytes,
                                cacheSizeBytes = cacheSizeBytes,
                                onOpenHistoryClick = { browserViewModel.onAction(AppBrowserAction.OpenHistory) },
                                onClearHistoryClick = {
                                    appContainer.appBrowserHistoryStore.clearImmediate()
                                    browserViewModel.onAction(AppBrowserAction.OpenHistory)
                                },
                                onClearCookiesClick = {
                                    coroutineScope.launch {
                                        appContainer.cookieSyncBridge.clear()
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            clearAppBrowserCookieFiles(context.applicationContext.dataDir)
                                        }
                                        webViewController.reload()
                                        refreshSettingsStorageUsage()
                                    }
                                },
                                onClearCacheClick = {
                                    coroutineScope.launch {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            clearAppBrowserCacheFiles(
                                                appDataDir = context.applicationContext.dataDir,
                                                appCacheDir = context.applicationContext.cacheDir,
                                            )
                                        }
                                        webViewRef?.clearCache(true)
                                        refreshSettingsStorageUsage()
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    AppBrowserPanel.Page -> Box(modifier = Modifier.fillMaxSize())
                }
            }
        },
    ) {
        AppBrowserWebView(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            initialUrl = url,
            userAgent = appContainer.userAgent,
            backgroundColor = colors.backgroundBase,
            controller = webViewController,
            onWebViewCreated = { webView ->
                webViewRef = webView
            },
            onWebViewReleased = {
                webViewRef = null
            },
            onNavigationRequest = handleNavigation,
            onPageStarted = { view, pageUrl, favicon ->
                pageUrl?.let { currentUrl = it }
                currentPageTitle = null
                scrollProgress = 0f
                loadProgressState = AppBrowserLoadProgressState().start()
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                if (favicon != null && !pageUrl.isNullOrBlank()) {
                    historyFavicons.rememberBest(pageUrl, favicon)
                }
                recordAppBrowserVisit(
                    visits = historyVisitMarks,
                    url = pageUrl,
                    lastRecordedUrl = lastRecordedUrl,
                    onRecorded = { recordedUrl, recordedAt ->
                        lastRecordedUrl = recordedUrl
                        lastRecordedAtMillis = recordedAt
                    },
                )
            },
            onPageFinished = { view, pageUrl ->
                loadProgressState = loadProgressState.finish()
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                val finishedUrl = pageUrl.orEmpty()
                appContainer.appBrowserHistoryStore.recordVisit(
                    url = finishedUrl,
                    title = currentPageTitle,
                    favicon = historyFavicons[finishedUrl] ?: view?.favicon,
                )
                if (finishedUrl.contains("linux.do")) {
                    appContainer.cookieSyncBridge.syncFromWebView(finishedUrl)
                }
            },
            onProgressChanged = { newProgress ->
                loadProgressState = loadProgressState.onActualProgressChanged(newProgress / 100f)
            },
            onTitleChanged = { title ->
                currentPageTitle = title
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            },
            onIconChanged = { iconUrl, icon ->
                if (icon != null && iconUrl.isNotBlank()) {
                    historyFavicons.rememberBest(iconUrl, icon)
                }
            },
            onFindResultChanged = { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                if (isDoneCounting) {
                    browserViewModel.onAction(
                        AppBrowserAction.UpdateSearchMatches(
                            activeMatchOrdinal = activeMatchOrdinal,
                            numberOfMatches = numberOfMatches,
                        ),
                    )
                }
            },
            onScrollProgressChanged = { value ->
                scrollProgress = value
            },
            onCanGoBackChanged = { value ->
                canGoBack = value
            },
            onCanGoForwardChanged = { value ->
                canGoForward = value
            },
            onShowFileChooser = { filePathCallback, fileChooserParams ->
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback
                val chooserIntent = fileChooserParams?.createIntent()
                if (chooserIntent == null) {
                    pendingFileCallback?.onReceiveValue(null)
                    pendingFileCallback = null
                    return@AppBrowserWebView
                }
                fileChooserLauncher.launch(chooserIntent)
            },
            onSslError = { handler, error ->
                val safeHandler = handler ?: return@AppBrowserWebView
                pendingSslRequest?.handler?.cancel()
                pendingSslRequest = PendingSslRequest(
                    handler = safeHandler,
                    error = error,
                )
            },
            onSyncCookiesToWebView = {
                appContainer.cookieSyncBridge.syncToWebView(appContainer.baseUrl)
            },
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = routePresentationState.backdropAlpha }
                .background(Color.Black),
        )

        if (historyScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = historyScrimAlpha }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            browserViewModel.onAction(AppBrowserAction.SetHistoryPopupVisible(false))
                        },
                ),
            )
        }

        pendingSslRequest?.let {
            AlertDialog(
                onDismissRequest = {
                    it.handler.cancel()
                    pendingSslRequest = null
                },
                title = {
                    Text(text = stringResource(R.string.app_browser_ssl_error_title))
                },
                text = {
                    Text(
                        text = buildString {
                            append(stringResource(R.string.app_browser_ssl_error_message))
                            it.error?.url?.toString()?.takeIf(String::isNotBlank)?.let { url ->
                                append("\n\n")
                                append(url)
                            }
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            it.handler.proceed()
                            pendingSslRequest = null
                        },
                    ) {
                        Text(text = stringResource(R.string.app_browser_ssl_error_continue))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            it.handler.cancel()
                            pendingSslRequest = null
                        },
                    ) {
                        Text(text = stringResource(R.string.app_browser_ssl_error_cancel))
                    }
                },
            )
        }

        val gestureEnabled = !isClosing &&
            panel == AppBrowserPanel.Page &&
            !browserUiState.address.isEditing &&
            !isSearching &&
            !historyPopupExpanded
        if (gestureEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(appBrowserSwipeEdgeWidth)
                    .align(Alignment.CenterStart)
                    .pointerInput(screenWidthPx, gestureEnabled) {
                        var velocityTracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(0L, offset)
                                swipeOffsetPx = currentRouteOffsetPx
                                isSwipeGestureActive = true
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                swipeOffsetPx = (swipeOffsetPx + dragAmount).coerceIn(0f, screenWidthPx)
                            },
                            onDragCancel = {
                                val settle = buildAppBrowserSwipeSettle(
                                    currentOffsetPx = swipeOffsetPx,
                                    velocityPxPerSecond = 0f,
                                    screenWidthPx = screenWidthPx,
                                )
                                coroutineScope.launch {
                                    routeOffsetPx.snapTo(swipeOffsetPx)
                                    isSwipeGestureActive = false
                                    routeOffsetPx.animateTo(
                                        targetValue = settle.targetOffsetPx,
                                        animationSpec = tween(
                                            durationMillis = settle.durationMillis,
                                            easing = AppBrowserEaseOutQuint,
                                        ),
                                    )
                                }
                            },
                            onDragEnd = {
                                val settle = buildAppBrowserSwipeSettle(
                                    currentOffsetPx = swipeOffsetPx,
                                    velocityPxPerSecond = velocityTracker.calculateVelocity().x,
                                    screenWidthPx = screenWidthPx,
                                )
                                if (settle.shouldDismiss) {
                                    requestClose(durationMillis = settle.durationMillis)
                                } else {
                                    coroutineScope.launch {
                                        routeOffsetPx.snapTo(swipeOffsetPx)
                                        isSwipeGestureActive = false
                                        routeOffsetPx.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = settle.durationMillis,
                                                easing = AppBrowserEaseOutQuint,
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    },
            )
        }

        AnimatedVisibility(
            visible = searchPanelState.visible,
            enter = slideInVertically(
                animationSpec = tween(
                    durationMillis = 320,
                    easing = LinearOutSlowInEasing,
                ),
                initialOffsetY = { it },
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 220,
                    easing = LinearOutSlowInEasing,
                ),
            ),
            exit = slideOutVertically(
                animationSpec = tween(
                    durationMillis = 180,
                    easing = LinearOutSlowInEasing,
                ),
                targetOffsetY = { it },
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 120,
                    easing = LinearOutSlowInEasing,
                ),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            AppBrowserSearchPanel(
                state = searchPanelState,
                onPrevious = ::searchPrevious,
                onNext = ::searchNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
            )
        }
    }
}

private fun MutableMap<String, Bitmap>.rememberBest(url: String, icon: Bitmap) {
    val current = this[url]
    if (current == null || (icon.width * icon.height) > (current.width * current.height)) {
        this[url] = icon
    }
}

private fun readAppBrowserHistoryEntries(
    webView: WebView?,
    visits: List<AppBrowserHistoryVisit>,
    favicons: Map<String, Bitmap>,
): List<AppBrowserHistoryEntry> {
    val historyList = webView?.copyBackForwardList() ?: return emptyList()
    val currentIndex = historyList.currentIndex
    if (currentIndex <= 0) {
        return emptyList()
    }
    return buildList {
        for (index in currentIndex - 1 downTo 0) {
            val item = historyList.getItemAtIndex(index)
            val itemUrl = item.url.orEmpty()
            if (itemUrl.isBlank()) {
                continue
            }
            add(
                AppBrowserHistoryEntry(
                    title = item.title
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?: (itemUrl.toHttpUrlOrNull()?.host ?: itemUrl),
                    url = itemUrl,
                    offset = index - currentIndex,
                    host = itemUrl.toHttpUrlOrNull()?.host ?: itemUrl,
                    visitedAtMillis = visits
                        .lastOrNull { visit -> visit.url == itemUrl }
                        ?.visitedAtMillis
                        ?: 0L,
                    favicon = favicons[itemUrl] ?: item.favicon,
                ),
            )
        }
    }
}

private fun recordAppBrowserVisit(
    visits: MutableList<AppBrowserHistoryVisit>,
    url: String?,
    lastRecordedUrl: String?,
    onRecorded: (String, Long) -> Unit,
) {
    val resolvedUrl = url?.takeIf { it.isNotBlank() } ?: return
    if (resolvedUrl == lastRecordedUrl) {
        return
    }
    val visitedAtMillis = System.currentTimeMillis()
    visits.add(
        AppBrowserHistoryVisit(
            url = resolvedUrl,
            visitedAtMillis = visitedAtMillis,
        ),
    )
    onRecorded(resolvedUrl, visitedAtMillis)
}

private data class AppBrowserHistoryVisit(
    val url: String,
    val visitedAtMillis: Long,
)

private data class PendingSslRequest(
    val handler: SslErrorHandler,
    val error: SslError?,
)

private val AppBrowserEaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val appBrowserSwipeEdgeWidth = 24.dp
private const val appBrowserRouteEnterDurationMillis = 380
private const val appBrowserRouteExitDurationMillis = 300
private const val appBrowserFakeProgressTickDelayMillis = 100L
private const val appBrowserProgressDismissDelayMillis = 180L

private class AppBrowserViewModelFactory(
    private val initialUrl: String,
    private val baseUrl: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(AppBrowserViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return AppBrowserViewModel(
            initialUrl = initialUrl,
            baseUrl = baseUrl,
        ) as T
    }
}
