package io.github.baba983.nativedo.feature.browser

import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.navigation.LinkOpenTarget
import io.github.baba983.nativedo.core.navigation.LinkRouter
import io.github.baba983.nativedo.core.navigation.openInExternalApp
import io.github.baba983.nativedo.core.navigation.openInSystemBrowser
import io.github.baba983.nativedo.core.navigation.shouldTryOpenInExternalApp
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
    onChromeConfigChanged: (AppBrowserChromeConfig?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val linkRouter = remember(appContainer) { LinkRouter(appContainer.baseUrl) }
    val clipboardManager = LocalClipboardManager.current
    val bulletinHostState = LocalBulletinHostState.current
    val linkCopiedText = stringResource(R.string.app_browser_link_copied)
    val colors = NativeDoThemeExtras.colors
    val browserHistory by appContainer.appBrowserHistoryStore.history.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val exitTranslationPx = remember(density) {
        with(density) { appBrowserRouteExitTranslation.roundToPx().toFloat() }
    }

    var webViewRef by remember(url) { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(url) { mutableStateOf(url) }
    var currentPageTitle by remember(url) { mutableStateOf<String?>(null) }
    var progress by remember(url) { mutableIntStateOf(0) }
    var scrollProgress by remember(url) { mutableStateOf(0f) }
    var loadProgressState by remember(url) { mutableStateOf(AppBrowserLoadProgressState()) }
    var canGoBack by remember(url) { mutableStateOf(false) }
    var canGoForward by remember(url) { mutableStateOf(false) }
    var closeRequested by remember(url) { mutableStateOf(false) }
    var panel by remember(url) { mutableStateOf<AppBrowserPanel>(AppBrowserPanel.Page) }
    var isSearching by remember(url) { mutableStateOf(false) }
    var searchQuery by remember(url) { mutableStateOf("") }
    var searchActiveMatchOrdinal by remember(url) { mutableIntStateOf(0) }
    var searchNumberOfMatches by remember(url) { mutableIntStateOf(0) }
    var historyPopupExpanded by remember(url) { mutableStateOf(false) }
    val historyVisitMarks = remember(url) { mutableStateListOf<AppBrowserHistoryVisit>() }
    val historyFavicons = remember(url) { mutableStateMapOf<String, Bitmap>() }
    var lastRecordedUrl by remember(url) { mutableStateOf<String?>(null) }
    var lastRecordedAtMillis by remember(url) { mutableLongStateOf(0L) }
    var cookiesSizeBytes by remember { mutableLongStateOf(0L) }
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }

    val presentationState = remember { MutableTransitionState(false) }

    LaunchedEffect(Unit) {
        presentationState.targetState = true
    }

    LaunchedEffect(closeRequested) {
        if (closeRequested) {
            presentationState.targetState = false
        }
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

    LaunchedEffect(
        closeRequested,
        presentationState.currentState,
        presentationState.targetState,
    ) {
        if (closeRequested && !presentationState.currentState && !presentationState.targetState) {
            onClose()
        }
    }

    val presentationTransition = updateTransition(
        transitionState = presentationState,
        label = "appBrowserPresentation",
    )
    val routeAlpha by presentationTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) {
                    appBrowserRouteEnterDurationMillis
                } else {
                    appBrowserRouteExitDurationMillis
                },
                easing = LinearOutSlowInEasing,
            )
        },
        label = "appBrowserRouteAlpha",
    ) { visible ->
        if (visible) 1f else 0f
    }
    val routeTranslationX by presentationTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) {
                    appBrowserRouteEnterDurationMillis
                } else {
                    appBrowserRouteExitDurationMillis
                },
                easing = LinearOutSlowInEasing,
            )
        },
        label = "appBrowserRouteTranslationX",
    ) { visible ->
        if (visible) 0f else exitTranslationPx
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

    fun requestClose() {
        if (closeRequested) {
            return
        }
        closeRequested = true
    }

    fun navigateBack() {
        val webView = webViewRef
        if (webView?.canGoBack() == true) {
            webView.goBack()
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
            webViewRef?.loadUrl(entry.url)
        }
    }

    fun startSearch() {
        isSearching = true
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        if (query.isBlank()) {
            searchActiveMatchOrdinal = 0
            searchNumberOfMatches = 0
            webViewRef?.clearMatches()
        } else {
            webViewRef?.findAllAsync(query)
        }
    }

    fun searchNext() {
        webViewRef?.findNext(true)
    }

    fun searchPrevious() {
        webViewRef?.findNext(false)
    }

    fun closeSearch() {
        isSearching = false
        searchQuery = ""
        searchActiveMatchOrdinal = 0
        searchNumberOfMatches = 0
        webViewRef?.clearMatches()
    }

    val handleNavigation: (String) -> Boolean = { requestUrl ->
        when (val target = linkRouter.resolve(requestUrl)) {
            is LinkOpenTarget.NativeTopic,
            is LinkOpenTarget.NativeUser -> {
                onOpenLink(requestUrl)
                true
            }

            is LinkOpenTarget.ExternalApp -> context.openInExternalApp(target.url)

            is LinkOpenTarget.AppBrowser -> {
                shouldTryOpenInExternalApp(
                    baseUrl = appContainer.baseUrl,
                    url = target.url,
                ) && context.openInExternalApp(target.url)
            }

            is LinkOpenTarget.SystemBrowser -> context.openInSystemBrowser(target.url)
        }
    }

    BackHandler {
        when {
            historyPopupExpanded -> historyPopupExpanded = false
            isSearching -> closeSearch()
            panel != AppBrowserPanel.Page -> panel = AppBrowserPanel.Page
            else -> navigateBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onChromeConfigChanged(null)
            appContainer.cookieSyncBridge.syncFromWebView(appContainer.baseUrl)
        }
    }

    LaunchedEffect(
        chromeTitle,
        chromeSubtitle,
        panel,
        isHttps,
        progress,
        scrollProgress,
        loadProgressState,
        canGoBack,
        canGoForward,
        routeAlpha,
        routeTranslationX,
        currentUrl,
        webViewRef,
        backHistoryEntries,
        browserHistoryEntries,
        isSearching,
        searchQuery,
        searchActiveMatchOrdinal,
        searchNumberOfMatches,
        historyPopupExpanded,
    ) {
        onChromeConfigChanged(
            AppBrowserChromeConfig(
                title = chromeTitle,
                subtitle = chromeSubtitle,
                isHttps = isHttps,
                progress = loadProgressState.displayedProgress,
                scrollProgress = scrollProgress,
                isLoadingProgressVisible = loadProgressState.isVisible,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                backHistoryEntries = backHistoryEntries,
                browserHistoryEntries = browserHistoryEntries,
                panel = panel,
                alpha = routeAlpha,
                translationX = routeTranslationX,
                onBack = {
                    if (isSearching) {
                        closeSearch()
                    } else if (panel != AppBrowserPanel.Page) {
                        panel = AppBrowserPanel.Page
                    } else {
                        navigateBack()
                    }
                },
                onClose = ::requestClose,
                onShowHistory = { panel = AppBrowserPanel.History },
                onShowSettings = { panel = AppBrowserPanel.Settings },
                onHistoryEntryClick = { entry ->
                    panel = AppBrowserPanel.Page
                    navigateHistory(entry)
                },
                onForward = { webViewRef?.goForward() },
                onRefresh = { webViewRef?.reload() },
                onShare = {
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
                },
                onOpenInBrowser = {
                    context.openInSystemBrowser(currentUrl)
                },
                onCopyLink = {
                    clipboardManager.setText(AnnotatedString(currentUrl))
                    bulletinHostState?.show(
                        BulletinMessage(
                            title = linkCopiedText,
                            tone = BulletinTone.Success,
                        ),
                    )
                },
                isHistoryPopupVisible = historyPopupExpanded,
                onHistoryPopupVisibilityChanged = { historyPopupExpanded = it },
                isSearching = isSearching,
                searchQuery = searchQuery,
                onStartSearch = ::startSearch,
                onSearchQueryChange = ::updateSearchQuery,
                onSearchClose = ::closeSearch,
            ),
        )
    }
    LaunchedEffect(panel) {
        if (panel == AppBrowserPanel.Settings) {
            refreshSettingsStorageUsage()
        }
    }
    SideEffect {
        if (panel != AppBrowserPanel.Page && historyPopupExpanded) {
            historyPopupExpanded = false
        }
        if (isSearching && historyPopupExpanded) {
            historyPopupExpanded = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundBase)
            .graphicsLayer {
                alpha = routeAlpha
                translationX = routeTranslationX
            },
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = appContainer.userAgent
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    setBackgroundColor(colors.backgroundBase.toArgb())
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val requestUrl = request?.url?.toString() ?: return false
                            return handleNavigation(requestUrl)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            url?.let { currentUrl = it }
                            currentPageTitle = null
                            progress = 0
                            scrollProgress = 0f
                            loadProgressState = AppBrowserLoadProgressState().start()
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            if (favicon != null && !url.isNullOrBlank()) {
                                historyFavicons.rememberBest(url, favicon)
                            }
                            recordAppBrowserVisit(
                                visits = historyVisitMarks,
                                url = url,
                                lastRecordedUrl = lastRecordedUrl,
                                onRecorded = { recordedUrl, recordedAt ->
                                    lastRecordedUrl = recordedUrl
                                    lastRecordedAtMillis = recordedAt
                                },
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            progress = 100
                            loadProgressState = loadProgressState.finish()
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            val finishedUrl = url.orEmpty()
                            appContainer.appBrowserHistoryStore.recordVisit(
                                url = finishedUrl,
                                title = currentPageTitle,
                                favicon = historyFavicons[finishedUrl] ?: view?.favicon,
                            )
                            if (finishedUrl.contains("linux.do")) {
                                appContainer.cookieSyncBridge.syncFromWebView(finishedUrl)
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            loadProgressState = loadProgressState.onActualProgressChanged(newProgress / 100f)
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            currentPageTitle = title
                                ?.trim()
                                ?.takeIf(String::isNotBlank)
                        }

                        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                            super.onReceivedIcon(view, icon)
                            val iconUrl = view?.url.orEmpty()
                            if (icon != null && iconUrl.isNotBlank()) {
                                historyFavicons.rememberBest(iconUrl, icon)
                            }
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?,
                        ): Boolean {
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                                ?: return false
                            val tempWebView = WebView(ctx)
                            tempWebView.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false
                                    if (handleNavigation(targetUrl)) {
                                        tempWebView.destroy()
                                        return true
                                    }
                                    this@apply.loadUrl(targetUrl)
                                    tempWebView.destroy()
                                    return true
                                }
                            }
                            transport.webView = tempWebView
                            resultMsg.sendToTarget()
                            return true
                        }
                    }

                    setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                        if (isDoneCounting) {
                            searchActiveMatchOrdinal = activeMatchOrdinal
                            searchNumberOfMatches = numberOfMatches
                        }
                    }

                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        scrollProgress = buildAppBrowserScrollProgress(
                            scrollY = scrollY,
                            contentHeightPx = (contentHeight * scale).toInt(),
                            viewportHeightPx = height,
                        )
                    }

                    appContainer.cookieSyncBridge.syncToWebView(appContainer.baseUrl)
                    loadUrl(url)
                    webViewRef = this
                }
            },
            update = { view ->
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
                scrollProgress = buildAppBrowserScrollProgress(
                    scrollY = view.scrollY,
                    contentHeightPx = (view.contentHeight * view.scale).toInt(),
                    viewportHeightPx = view.height,
                )
            },
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
                        onClick = { historyPopupExpanded = false },
                    ),
            )
        }

        AnimatedContent(
            targetState = panel,
            transitionSpec = {
                (slideInHorizontally { fullWidth -> fullWidth / 6 } + fadeIn())
                    .togetherWith(slideOutHorizontally { fullWidth -> -fullWidth / 8 } + fadeOut())
            },
            label = "appBrowserPanel",
        ) { currentPanel ->
            when (currentPanel) {
                AppBrowserPanel.History -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.backgroundBase),
                ) {
                    AppBrowserHistoryPanel(
                        entries = browserHistoryEntries,
                        onEntryClick = { entry ->
                            panel = AppBrowserPanel.Page
                            navigateHistory(entry)
                        },
                    )
                }

                AppBrowserPanel.Settings -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.backgroundBase),
                ) {
                    AppBrowserSettingsPanel(
                        historyCount = browserHistory.size,
                        cookiesSizeBytes = cookiesSizeBytes,
                        cacheSizeBytes = cacheSizeBytes,
                        onOpenHistoryClick = { panel = AppBrowserPanel.History },
                        onClearHistoryClick = {
                            appContainer.appBrowserHistoryStore.clearImmediate()
                            panel = AppBrowserPanel.History
                        },
                        onClearCookiesClick = {
                            coroutineScope.launch {
                                appContainer.cookieSyncBridge.clear()
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    clearAppBrowserCookieFiles(context.applicationContext.dataDir)
                                }
                                webViewRef?.reload()
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
                    )
                }

                AppBrowserPanel.Page -> Box(modifier = Modifier.fillMaxSize())
            }
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

private val appBrowserRouteExitTranslation = 24.dp
private const val appBrowserRouteEnterDurationMillis = 220
private const val appBrowserRouteExitDurationMillis = 170
private const val appBrowserFakeProgressTickDelayMillis = 100L
private const val appBrowserProgressDismissDelayMillis = 180L
