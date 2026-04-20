package io.github.baba983.nativedo.feature.browser

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Canvas
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.http.SslError
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.navigation.AppBrowserOpenRequest
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderDocument
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationTarget
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderSupport
import io.github.baba983.nativedo.core.navigation.LinkNavigationDecision
import io.github.baba983.nativedo.core.navigation.LinkOpenPolicy
import io.github.baba983.nativedo.core.navigation.openInExternalApp
import io.github.baba983.nativedo.core.navigation.openInSystemBrowser
import io.github.baba983.nativedo.core.storage.AppBrowserExternalOpenMode
import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine
import io.github.baba983.nativedo.core.ui.BulletinMessage
import io.github.baba983.nativedo.core.ui.BulletinTone
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.core.ui.RequestErrorCard
import io.github.baba983.nativedo.core.ui.RequestErrorPresentation
import io.github.baba983.nativedo.core.ui.requestErrorPresentation
import io.github.baba983.nativedo.ui.BrowserHostMinimizedPreview
import io.github.baba983.nativedo.ui.buildBrowserHostBrowserPreview
import io.github.baba983.nativedo.ui.NativeDoPullToRefreshIndicator
import io.github.baba983.nativedo.ui.theme.NativeDoProvideScreenStyle
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import io.github.baba983.nativedo.ui.theme.nativeDoBrowserScreenStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserRoute(
    entryId: Long,
    url: String,
    onOpenLink: (String) -> Unit,
    onOpenReader: (AppBrowserReaderContent, AppBrowserOpenRequest?) -> Unit = { _, _ -> },
    onMinimize: () -> Unit = {},
    onClose: () -> Unit,
    onPreviewChanged: (BrowserHostMinimizedPreview) -> Unit = {},
    modifier: Modifier = Modifier,
    presentationProgress: Float = 1f,
    topInset: Dp = 0.dp,
    isForeground: Boolean = true,
    onSheetFullscreenPreferenceChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val browserPreferences by appContainer.appBrowserPreferenceStore.preferences.collectAsStateWithLifecycle()
    val linkOpenPolicy = remember(appContainer, browserPreferences) {
        LinkOpenPolicy(
            baseUrl = appContainer.baseUrl,
            restrictedDomains = browserPreferences.restrictedDomains,
            defaultExternalOpenMode = browserPreferences.defaultExternalOpenMode,
        )
    }
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val bulletinHostState = LocalBulletinHostState.current
    val linkCopiedText = stringResource(R.string.app_browser_link_copied)
    val bookmarkSavedText = stringResource(R.string.app_browser_bookmark_saved_message)
    val bookmarkRemovedText = stringResource(R.string.app_browser_bookmark_removed_message)
    val colors = NativeDoThemeExtras.colors
    val rootScreenStyle = NativeDoThemeExtras.screenStyle
    val browserBookmarks by appContainer.appBrowserBookmarkStore.bookmarks.collectAsStateWithLifecycle()
    val browserHistory by appContainer.appBrowserHistoryStore.history.collectAsStateWithLifecycle()
    val browserRecentSearches by appContainer.appBrowserRecentSearchStore.recentSearches.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val webViewController = rememberBrowserWebViewController()
    val headerCollapseDistancePx = remember(density) {
        with(density) {
            (BrowserTopBarOverlayHeight - BrowserCollapsedTopBarOverlayHeight).toPx()
        }
    }
    val browserViewModel: BrowserViewModel = viewModel(
        key = "app-browser-$entryId",
        factory = remember(entryId, url, appContainer.baseUrl) {
            BrowserViewModelFactory(
                initialUrl = url,
                baseUrl = appContainer.baseUrl,
                searchEngine = browserPreferences.searchEngine,
            )
        },
    )
    val browserUiState by browserViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(browserPreferences.searchEngine) {
        browserViewModel.updateSearchEngine(browserPreferences.searchEngine)
    }

    var webViewRef by remember(entryId) { mutableStateOf<BrowserWebViewView?>(null) }
    var currentUrl by remember(entryId) { mutableStateOf(url) }
    var currentPageTitle by remember(entryId) { mutableStateOf<String?>(null) }
    val instantViewLoader = remember(entryId) { BrowserInstantViewLoader() }
    val instantViewUiState = instantViewLoader.uiState
    var pageThemeColor by remember(entryId) { mutableStateOf<Color?>(null) }
    var scrollProgress by remember(entryId) { mutableStateOf(0f) }
    var headerCollapseState by remember(entryId) { mutableStateOf(BrowserHeaderCollapseState()) }
    var loadProgressState by remember(entryId) { mutableStateOf(BrowserLoadProgressState()) }
    var canGoBack by remember(entryId) { mutableStateOf(false) }
    var canGoForward by remember(entryId) { mutableStateOf(false) }
    var isClosing by remember(entryId) { mutableStateOf(false) }
    var dispatchedFindQuery by remember(entryId) { mutableStateOf<String?>(null) }
    val historyVisitMarks = remember(entryId) { mutableStateListOf<BrowserHistoryVisit>() }
    val historyFavicons = remember(entryId) { mutableStateMapOf<String, Bitmap>() }
    var lastRecordedUrl by remember(entryId) { mutableStateOf<String?>(null) }
    var lastRecordedAtMillis by remember(entryId) { mutableLongStateOf(0L) }
    var cookiesSizeBytes by remember { mutableLongStateOf(0L) }
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var pendingFileCallback by remember(entryId) { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingSslRequest by remember(entryId) { mutableStateOf<PendingSslRequest?>(null) }
    var pendingWebPermissionRequest by remember(entryId) { mutableStateOf<PendingWebPermissionRequest?>(null) }
    var currentPageLoadError by remember(entryId) { mutableStateOf<BrowserPageLoadError?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()
    var isPullRefreshing by remember(entryId) { mutableStateOf(false) }
    val permissionRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantResults ->
        val pendingRequest = pendingWebPermissionRequest
        if (pendingRequest == null) {
            return@rememberLauncherForActivityResult
        }
        val granted = pendingRequest.requestData.requiredAndroidPermissions.all { permission ->
            grantResults[permission] == true
        }
        if (granted) {
            pendingRequest.permissionRequest.grant(
                pendingRequest.requestData.grantableResources.toTypedArray(),
            )
            bulletinHostState?.show(
                BulletinMessage(
                    title = context.getString(R.string.app_browser_permission_granted),
                    tone = BulletinTone.Success,
                ),
            )
        } else {
            pendingRequest.permissionRequest.deny()
            bulletinHostState?.show(
                BulletinMessage(
                    title = context.getString(R.string.app_browser_permission_denied),
                    tone = BulletinTone.Error,
                ),
            )
        }
        pendingWebPermissionRequest = null
    }
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

    LaunchedEffect(loadProgressState.actualProgress, loadProgressState.isVisible, loadProgressState.isLoading) {
        if (loadProgressState.isLoading || !loadProgressState.isVisible || loadProgressState.actualProgress < 1f) {
            return@LaunchedEffect
        }
        delay(appBrowserProgressDismissDelayMillis)
        loadProgressState = loadProgressState.dismiss()
    }
    val resolvedTopInset = topInset
    val resolvedPresentationProgress = presentationProgress.coerceIn(0f, 1f)

    LaunchedEffect(isPullRefreshing, loadProgressState.isLoading, loadProgressState.actualProgress) {
        if (isPullRefreshing && !loadProgressState.isLoading && loadProgressState.actualProgress >= 1f) {
            isPullRefreshing = false
        }
    }
    val fallbackChromeColor = remember(colors) {
        colors.glassBackgroundStrong.copy(alpha = 0.90f)
    }
    val resolvedBrowserChromeColor = remember(fallbackChromeColor, pageThemeColor) {
        pageThemeColor?.let { themeColor ->
            androidx.compose.ui.graphics.lerp(
                start = fallbackChromeColor,
                stop = themeColor,
                fraction = 0.78f,
            )
        } ?: fallbackChromeColor
    }
    val resolvedBrowserNavigationColor = remember(colors, resolvedBrowserChromeColor) {
        androidx.compose.ui.graphics.lerp(
            start = colors.backgroundBase,
            stop = resolvedBrowserChromeColor,
            fraction = 0.68f,
        ).copy(alpha = 1f)
    }
    val animatedBrowserChromeColor by animateColorAsState(
        targetValue = resolvedBrowserChromeColor,
        animationSpec = tween(durationMillis = 200),
        label = "appBrowserChromeColor",
    )
    val animatedBrowserNavigationColor by animateColorAsState(
        targetValue = resolvedBrowserNavigationColor,
        animationSpec = tween(durationMillis = 200),
        label = "appBrowserNavigationColor",
    )
    val browserScreenStyle = remember(
        rootScreenStyle,
        animatedBrowserChromeColor,
        animatedBrowserNavigationColor,
        resolvedPresentationProgress,
    ) {
        nativeDoBrowserScreenStyle(
            baseStyle = rootScreenStyle,
            browserChromeColor = animatedBrowserChromeColor,
            browserNavigationBarColor = animatedBrowserNavigationColor,
            presentationProgress = resolvedPresentationProgress,
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
    val currentPreviewFavicon = historyFavicons[currentUrl] ?: webViewRef?.favicon
    val panel = browserUiState.panel
    val isSearching = browserUiState.search.isActive
    val searchQuery = browserUiState.search.query
    val searchActiveMatchOrdinal = browserUiState.search.activeMatchOrdinal
    val searchNumberOfMatches = browserUiState.search.numberOfMatches
    val searchIsCounting = browserUiState.search.isCounting
    val historyPopupExpanded = browserUiState.isHistoryPopupVisible
    val overflowMenuExpanded = browserUiState.isOverflowMenuVisible
    val headerCollapseEnabled = false
    val prefersSheetFullscreen = panel != BrowserPanel.None || isSearching || browserUiState.address.isEditing
    val currentBookmarkUrl = remember(currentUrl) {
        normalizeAppBrowserBookmarkUrl(currentUrl)
    }
    val currentBookmark = remember(browserBookmarks, currentBookmarkUrl) {
        browserBookmarks.firstOrNull { bookmark -> bookmark.url == currentBookmarkUrl }
    }
    val chromeTitle = when (panel) {
        BrowserPanel.None -> titleText
        BrowserPanel.History -> context.getString(R.string.app_browser_history)
        BrowserPanel.Bookmarks -> context.getString(R.string.app_browser_bookmarks)
        BrowserPanel.Settings -> context.getString(R.string.app_browser_settings)
    }
    val chromeSubtitle = if (panel == BrowserPanel.None) subtitleText else null
    val backHistoryEntries = remember(
        webViewRef,
        currentUrl,
        currentPageTitle,
        canGoBack,
        historyVisitMarks.size,
        lastRecordedAtMillis,
    ) {
        readBrowserHistoryEntries(
            webView = webViewRef,
            visits = historyVisitMarks,
            favicons = historyFavicons,
        )
    }
    val browserHistoryEntries = remember(backHistoryEntries, browserHistory) {
        mergeBrowserHistoryEntries(
            backHistoryEntries = backHistoryEntries,
            browserHistoryEntries = browserHistory,
        )
    }
    val addressSuggestionSections = remember(
        browserUiState.address.draft,
        currentUrl,
        currentPageTitle,
        browserHistory,
        browserBookmarks,
        browserRecentSearches,
    ) {
        buildBrowserAddressSuggestionSections(
            draft = browserUiState.address.draft,
            currentUrl = currentUrl,
            currentTitle = currentPageTitle,
            historyEntries = browserHistory,
            bookmarks = browserBookmarks,
            recentSearches = browserRecentSearches,
        )
    }
    val searchPanelState = remember(
        isSearching,
        searchQuery,
        searchActiveMatchOrdinal,
        searchNumberOfMatches,
        searchIsCounting,
    ) {
        buildBrowserSearchPanelState(
            isSearching = isSearching,
            query = searchQuery,
            activeMatchOrdinal = searchActiveMatchOrdinal,
            numberOfMatches = searchNumberOfMatches,
            isCounting = searchIsCounting,
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
        instantViewUiState,
        animatedBrowserChromeColor,
        currentBookmark?.url,
        backHistoryEntries,
        browserHistoryEntries,
        panel,
        historyPopupExpanded,
        overflowMenuExpanded,
        browserUiState.chromeScene,
        headerCollapseState.progress,
    ) {
        BrowserChromeModel(
            scene = browserUiState.chromeScene,
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
            instantView = instantViewUiState,
            chromeColor = animatedBrowserChromeColor,
            isBookmarked = currentBookmark != null,
            backHistoryEntries = backHistoryEntries,
            browserHistoryEntries = browserHistoryEntries,
            isInSubPanel = panel != BrowserPanel.None,
            alpha = 1f,
            translationX = 0f,
            headerCollapseProgress = headerCollapseState.progress,
            isHistoryPopupVisible = historyPopupExpanded,
            isOverflowMenuVisible = overflowMenuExpanded,
        )
    }

    LaunchedEffect(chromeModel) {
        browserViewModel.setChrome(chromeModel)
    }

    LaunchedEffect(browserRecentSearches, addressSuggestionSections) {
        browserViewModel.updateAddressSuggestions(
            recentSearches = browserRecentSearches,
            suggestionSections = addressSuggestionSections,
        )
    }

    LaunchedEffect(prefersSheetFullscreen, onSheetFullscreenPreferenceChanged) {
        onSheetFullscreenPreferenceChanged(prefersSheetFullscreen)
    }

    suspend fun refreshSettingsStorageUsage() {
        val appContext = context.applicationContext
        val usage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            readBrowserStorageUsage(
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
        onClose()
    }

    fun navigateBack() {
        if (browserUiState.chrome.canGoBack) {
            webViewController.goBack()
            return
        }
        requestClose()
    }

    fun navigateHistory(entry: BrowserHistoryEntry) {
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

    fun navigateBookmark(entry: AppBrowserBookmarkRecord) {
        browserViewModel.onAction(BrowserAction.ClosePanel)
        if (entry.url != currentUrl) {
            webViewController.loadUrl(entry.url)
        }
    }

    fun scrubBrowserScroll(deltaFraction: Float) {
        val webView = webViewRef ?: return
        val scrollableHeight = (webView.verticalScrollRange - webView.verticalScrollExtent)
            .coerceAtLeast(0)
        if (scrollableHeight == 0) {
            return
        }
        val currentProgress = (webView.scrollY.toFloat() / scrollableHeight.toFloat())
            .coerceIn(0f, 1f)
        val targetProgress = buildAppBrowserScrubbedProgress(
            currentProgress = currentProgress,
            deltaFraction = deltaFraction,
        )
        webView.scrollTo(
            webView.scrollX,
            (scrollableHeight * targetProgress).toInt().coerceIn(0, scrollableHeight),
        )
    }

    fun toggleBookmark() {
        val bookmarkUrl = currentBookmarkUrl ?: return
        val bookmarkTitle = currentPageTitle
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: chromeTitle
        val bookmarkFavicon = historyFavicons[currentUrl] ?: historyFavicons[bookmarkUrl] ?: webViewRef?.favicon
        val saved = appContainer.appBrowserBookmarkStore.toggle(
            url = bookmarkUrl,
            title = bookmarkTitle,
            favicon = bookmarkFavicon,
        )
        bulletinHostState?.show(
            BulletinMessage(
                title = if (saved) bookmarkSavedText else bookmarkRemovedText,
                tone = BulletinTone.Success,
            ),
        )
    }

    fun startSearch() {
        browserViewModel.onAction(BrowserAction.StartSearch)
    }

    fun updateSearchQuery(query: String) {
        browserViewModel.onAction(BrowserAction.UpdateSearchQuery(query))
        if (query.isBlank()) {
            dispatchedFindQuery = null
            webViewController.clearMatches()
        }
    }

    fun searchNext() {
        webViewController.findNext(true)
    }

    fun searchPrevious() {
        webViewController.findNext(false)
    }

    fun closeSearch() {
        dispatchedFindQuery = null
        browserViewModel.onAction(BrowserAction.CloseSearch)
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
                    decision.url.toHttpUrlOrNull() == null -> true
                    else -> false
                }
            }

            is LinkNavigationDecision.AppBrowser -> false

            is LinkNavigationDecision.SystemBrowser -> context.openInSystemBrowser(decision.url)
        }
    }

    fun handlePopupRequest(request: BrowserPopupRequest) {
        val popupUrl = request.resolvedUrl
        if (popupUrl.isNullOrBlank()) {
            bulletinHostState?.show(
                BulletinMessage(
                    title = context.getString(R.string.app_browser_popup_blocked),
                    tone = BulletinTone.Info,
                ),
            )
            return
        }
        when (val decision = linkOpenPolicy.resolve(popupUrl)) {
            is LinkNavigationDecision.NativeTopic,
            is LinkNavigationDecision.NativeUser -> onOpenLink(popupUrl)

            is LinkNavigationDecision.ExternalApp -> {
                val opened = context.openInExternalApp(decision.url)
                if (!opened) {
                    webViewController.loadUrl(decision.url)
                }
            }

            is LinkNavigationDecision.AppBrowser -> webViewController.loadUrl(decision.url)

            is LinkNavigationDecision.SystemBrowser -> {
                context.openInSystemBrowser(decision.url)
            }
        }
    }

    fun handleDownloadRequest(request: BrowserDownloadRequest) {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        if (downloadManager != null) {
            val fileName = URLUtil.guessFileName(
                request.url,
                request.contentDisposition,
                request.mimeType,
            )
            val downloadRequest = DownloadManager.Request(Uri.parse(request.url))
                .setTitle(fileName)
                .setMimeType(request.mimeType)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            buildAppBrowserDownloadHeaders(
                cookie = CookieManager.getInstance().getCookie(request.url),
                userAgent = request.userAgent ?: appContainer.userAgent,
            ).forEach { (name, value) ->
                downloadRequest.addRequestHeader(name, value)
            }
            runCatching {
                downloadManager.enqueue(downloadRequest)
            }.onSuccess {
                bulletinHostState?.show(
                    BulletinMessage(
                        title = context.getString(R.string.app_browser_download_started),
                        tone = BulletinTone.Success,
                    ),
                )
                return
            }
        }
        val openedInBrowser = context.openInSystemBrowser(request.url)
        bulletinHostState?.show(
            BulletinMessage(
                title = context.getString(
                    if (openedInBrowser) {
                        R.string.app_browser_download_opened_in_browser
                    } else {
                        R.string.app_browser_download_failed
                    },
                ),
                tone = if (openedInBrowser) BulletinTone.Info else BulletinTone.Error,
            ),
        )
    }

    fun handleWebPermissionRequest(permissionRequest: PermissionRequest?) {
        val safeRequest = permissionRequest ?: return
        pendingWebPermissionRequest?.permissionRequest?.deny()
        val requestData = buildBrowserWebPermissionRequestData(safeRequest.resources)
        if (requestData == null) {
            safeRequest.deny()
            bulletinHostState?.show(
                BulletinMessage(
                    title = context.getString(R.string.app_browser_permission_unsupported),
                    tone = BulletinTone.Info,
                ),
            )
            return
        }
        val missingPermissions = requestData.requiredAndroidPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            safeRequest.grant(requestData.grantableResources.toTypedArray())
            bulletinHostState?.show(
                BulletinMessage(
                    title = context.getString(R.string.app_browser_permission_granted),
                    tone = BulletinTone.Success,
                ),
            )
            return
        }
        pendingWebPermissionRequest = PendingWebPermissionRequest(
            permissionRequest = safeRequest,
            requestData = requestData,
        )
        permissionRequestLauncher.launch(missingPermissions.toTypedArray())
    }

    fun dismissPageLoadError() {
        currentPageLoadError = null
    }

    fun publishPreview(
        snapshot: Bitmap? = null,
    ) {
        onPreviewChanged(
            buildBrowserHostBrowserPreview(
                url = currentUrl,
                title = titleText,
                subtitle = subtitleText,
                favicon = currentPreviewFavicon,
                snapshot = snapshot,
            ),
        )
    }

    SideEffect {
        instantViewLoader.bind(
            resolveHost = { webViewRef?.asInstantViewResolveHost() },
            buildReaderContent = ::buildAppBrowserReaderContent,
            onOpenReader = { content ->
                onOpenReader(
                    content,
                    AppBrowserOpenRequest(
                        url = currentUrl,
                        preferredTarget = AppBrowserPresentationTarget.InstantView,
                        readerSupport = AppBrowserReaderSupport.Supported,
                    ),
                )
            },
            onUnavailable = {
                bulletinHostState?.show(
                    BulletinMessage(
                        title = context.getString(R.string.app_browser_instant_view_unavailable),
                        tone = BulletinTone.Info,
                    ),
                )
            },
        )
    }

    fun handleBrowserAction(action: BrowserAction) {
        when (action) {
            BrowserAction.NavigateBack -> {
                when {
                    browserUiState.address.isEditing -> browserViewModel.onAction(BrowserAction.CancelAddressEditing)
                    overflowMenuExpanded -> browserViewModel.onAction(BrowserAction.SetOverflowMenuVisible(false))
                    historyPopupExpanded -> browserViewModel.onAction(BrowserAction.SetHistoryPopupVisible(false))
                    isSearching -> closeSearch()
                    panel != BrowserPanel.None -> browserViewModel.onAction(BrowserAction.ClosePanel)
                    else -> navigateBack()
                }
            }

            BrowserAction.CloseBrowser -> requestClose()
            BrowserAction.MinimizeBrowser -> {
                publishPreview()
                onMinimize()
            }
            BrowserAction.StartAddressEditing,
            BrowserAction.OpenAddressSheet -> {
                browserViewModel.startAddressEditing(
                    currentUrl = currentUrl,
                    recentEntries = browserHistory,
                    recentSearches = browserRecentSearches,
                    suggestionSections = addressSuggestionSections,
                )
            }

            BrowserAction.OpenHistory,
            BrowserAction.OpenBookmarks,
            BrowserAction.OpenSettings,
            BrowserAction.CancelAddressEditing,
            BrowserAction.DismissPanel,
            BrowserAction.ClosePanel,
            BrowserAction.StartSearch,
            is BrowserAction.UpdateSearchMatches,
            is BrowserAction.UpdateAddressDraft,
            is BrowserAction.SetHistoryPopupVisible,
            is BrowserAction.SetOverflowMenuVisible -> browserViewModel.onAction(action)

            BrowserAction.CloseSearch -> closeSearch()
            is BrowserAction.SubmitAddress -> {
                val submittedValue = action.value.trim()
                if (submittedValue.isNotBlank()) {
                    val resolvedAddress = resolveBrowserAddressInput(
                        value = submittedValue,
                        baseUrl = appContainer.baseUrl,
                        searchEngine = browserPreferences.searchEngine,
                    )
                    if (resolvedAddress != null && resolvedAddress != submittedValue) {
                        appContainer.appBrowserRecentSearchStore.recordSearch(submittedValue)
                    }
                }
                browserViewModel.onAction(action)
                browserViewModel.lastSubmittedAddress?.let(webViewController::loadUrl)
            }
            is BrowserAction.UpdateSearchQuery -> updateSearchQuery(action.query)
            is BrowserAction.OpenHistoryEntry -> {
                browserViewModel.onAction(BrowserAction.ClosePanel)
                navigateHistory(action.entry)
            }

            is BrowserAction.ApplyAddressSuggestion -> {
                when (
                    val result = applyBrowserAddressSuggestion(
                        suggestion = action.suggestion,
                        currentDraft = browserUiState.address.draft,
                        baseUrl = appContainer.baseUrl,
                        searchEngine = browserPreferences.searchEngine,
                    )
                ) {
                    is BrowserAddressSuggestionResult.SubmitAddress -> {
                        handleBrowserAction(BrowserAction.SubmitAddress(result.value))
                    }
                    is BrowserAddressSuggestionResult.QuickAction -> {
                        when (result.action) {
                            BrowserAddressQuickAction.CopyCurrentLink -> handleBrowserAction(BrowserAction.CopyLink)
                            BrowserAddressQuickAction.ToggleBookmark -> handleBrowserAction(BrowserAction.ToggleBookmark)
                        }
                    }
                }
            }

            BrowserAction.GoForward -> webViewController.goForward()
            BrowserAction.Refresh -> webViewController.reload()
            BrowserAction.Share -> {
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

            BrowserAction.OpenInstantView -> {
                instantViewLoader.onOpenRequested()
            }
            BrowserAction.OpenInBrowser -> context.openInSystemBrowser(currentUrl)
            BrowserAction.ToggleBookmark -> toggleBookmark()
            BrowserAction.CopyLink -> {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText(null,currentUrl))
                bulletinHostState?.show(
                    BulletinMessage(
                        title = linkCopiedText,
                        tone = BulletinTone.Success,
                    ),
                )
            }
        }
    }

    BackHandler(enabled = isForeground) {
        handleBrowserAction(BrowserAction.NavigateBack)
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            pendingSslRequest?.handler?.cancel()
            pendingSslRequest = null
            pendingWebPermissionRequest?.permissionRequest?.deny()
            pendingWebPermissionRequest = null
            appContainer.cookieSyncBridge.syncFromWebView(appContainer.baseUrl)
        }
    }
    LaunchedEffect(panel) {
        if (panel == BrowserPanel.Settings) {
            refreshSettingsStorageUsage()
        }
    }
    LaunchedEffect(panel, isSearching, browserUiState.address.isEditing, historyPopupExpanded, overflowMenuExpanded) {
        if ((panel != BrowserPanel.None || isSearching) && historyPopupExpanded) {
            browserViewModel.onAction(BrowserAction.SetHistoryPopupVisible(false))
        }
        if ((panel != BrowserPanel.None || isSearching || browserUiState.address.isEditing) && overflowMenuExpanded) {
            browserViewModel.onAction(BrowserAction.SetOverflowMenuVisible(false))
        }
    }
    LaunchedEffect(headerCollapseEnabled) {
        if (!headerCollapseEnabled) {
            headerCollapseState = BrowserHeaderCollapseState()
        }
    }
    LaunchedEffect(isSearching, searchQuery, currentUrl, loadProgressState.isLoading) {
        if (!isSearching) {
            return@LaunchedEffect
        }
        if (searchQuery.isBlank()) {
            dispatchedFindQuery = null
            webViewController.clearMatches()
            return@LaunchedEffect
        }
        if (loadProgressState.isLoading) {
            return@LaunchedEffect
        }
        delay(appBrowserFindDispatchDebounceMillis)
        dispatchedFindQuery = searchQuery
        webViewController.findAllAsync(searchQuery)
    }
    LaunchedEffect(
        currentUrl,
        titleText,
        subtitleText,
        currentPreviewFavicon?.generationId,
    ) {
        publishPreview()
    }

    NativeDoProvideScreenStyle(screenStyle = browserScreenStyle) {
        BrowserScreen(
            uiState = browserUiState,
            chrome = chromeModel,
            onAction = ::handleBrowserAction,
            modifier = modifier
                .fillMaxSize()
                .background(colors.backgroundBase),
            sceneModifier = Modifier,
            topInset = resolvedTopInset,
            onScrollScrub = ::scrubBrowserScroll,
            sheetContent = {
                AnimatedContent(
                targetState = panel,
                transitionSpec = {
                    val isReturningToPage = targetState == BrowserPanel.None
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
                    BrowserPanel.History -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.backgroundBase.copy(alpha = 0.98f)),
                        ) {
                            BrowserHistoryPanel(
                                entries = browserHistoryEntries,
                                onEntryClick = { entry ->
                                    browserViewModel.onAction(BrowserAction.ClosePanel)
                                    navigateHistory(entry)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    BrowserPanel.Bookmarks -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.backgroundBase.copy(alpha = 0.98f)),
                        ) {
                            BrowserBookmarksPanel(
                                entries = browserBookmarks,
                                onEntryClick = ::navigateBookmark,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    BrowserPanel.Settings -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.backgroundBase.copy(alpha = 0.98f)),
                        ) {
                            BrowserSettingsPanel(
                                historyCount = browserHistory.size,
                                bookmarkCount = browserBookmarks.size,
                                cookiesSizeBytes = cookiesSizeBytes,
                                cacheSizeBytes = cacheSizeBytes,
                                externalOpenMode = browserPreferences.defaultExternalOpenMode,
                                searchEngine = browserPreferences.searchEngine,
                                restrictedDomainCount = browserPreferences.restrictedDomains.size,
                                onExternalOpenModeClick = {
                                    val nextMode = when (browserPreferences.defaultExternalOpenMode) {
                                        AppBrowserExternalOpenMode.InAppBrowser -> AppBrowserExternalOpenMode.SystemBrowser
                                        AppBrowserExternalOpenMode.SystemBrowser -> AppBrowserExternalOpenMode.InAppBrowser
                                    }
                                    appContainer.appBrowserPreferenceStore.setDefaultExternalOpenMode(nextMode)
                                },
                                onSearchEngineClick = {
                                    val nextEngine = when (browserPreferences.searchEngine) {
                                        AppBrowserSearchEngine.LinuxDo -> AppBrowserSearchEngine.Google
                                        AppBrowserSearchEngine.Google -> AppBrowserSearchEngine.Bing
                                        AppBrowserSearchEngine.Bing -> AppBrowserSearchEngine.LinuxDo
                                    }
                                    appContainer.appBrowserPreferenceStore.setSearchEngine(nextEngine)
                                },
                                onRestrictedDomainsClick = {
                                    val host = currentUrl.toHttpUrlOrNull()?.host
                                    if (host != null) {
                                        val updatedDomains = browserPreferences.restrictedDomains.toMutableSet().apply {
                                            if (!add(host)) {
                                                remove(host)
                                            }
                                        }
                                        appContainer.appBrowserPreferenceStore.updateRestrictedDomains(updatedDomains.toList())
                                    }
                                },
                                onOpenHistoryClick = { browserViewModel.onAction(BrowserAction.OpenHistory) },
                                onOpenBookmarksClick = { browserViewModel.onAction(BrowserAction.OpenBookmarks) },
                                onClearHistoryClick = {
                                    appContainer.appBrowserHistoryStore.clearImmediate()
                                    browserViewModel.onAction(BrowserAction.OpenHistory)
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

                    BrowserPanel.None -> Box(modifier = Modifier.fillMaxSize())
                    }
                }
            },
            overlayContent = {
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
                                browserViewModel.onAction(BrowserAction.SetHistoryPopupVisible(false))
                            },
                        ),
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
                    BrowserSearchPanel(
                    state = searchPanelState,
                    onPrevious = ::searchPrevious,
                    onNext = ::searchNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding(),
                    )
                }
            },
        ) {
            if (panel == BrowserPanel.None) {
                PullToRefreshBox(
                    isRefreshing = isPullRefreshing,
                    onRefresh = {
                        isPullRefreshing = true
                        webViewController.reload()
                    },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        NativeDoPullToRefreshIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isPullRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                ) {
                    BrowserWebView(
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
                            dismissPageLoadError()
                            pageUrl?.let { currentUrl = it }
                            currentPageTitle = null
                            instantViewLoader.onPageStarted(pageUrl)
                            pageThemeColor = null
                            scrollProgress = 0f
                            headerCollapseState = BrowserHeaderCollapseState()
                            dispatchedFindQuery = null
                            loadProgressState = BrowserLoadProgressState().start()
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
                            isPullRefreshing = false
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            val finishedUrl = pageUrl?.takeIf(String::isNotBlank) ?: currentUrl
                            instantViewLoader.onPageFinished(finishedUrl)
                            appContainer.appBrowserHistoryStore.recordVisit(
                                url = finishedUrl,
                                title = currentPageTitle,
                                favicon = historyFavicons[finishedUrl] ?: view?.favicon,
                            )
                            if (finishedUrl.contains("linux.do")) {
                                appContainer.cookieSyncBridge.syncFromWebView(finishedUrl)
                            }
                            resolveAppBrowserThemeColor(
                                webView = view,
                                expectedUrl = finishedUrl,
                            ) { resolvedColor ->
                                if (currentUrl == finishedUrl) {
                                    pageThemeColor = resolvedColor
                                }
                            }
                            publishPreview(
                                snapshot = captureAppBrowserPreviewBitmap(view),
                            )
                        },
                        onProgressChanged = { newProgress ->
                            val normalizedProgress = newProgress / 100f
                            loadProgressState = loadProgressState.onActualProgressChanged(normalizedProgress)
                            instantViewLoader.onPageSnapshotChanged(
                                pageUrl = currentUrl,
                                pageProgress = normalizedProgress,
                            )
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
                            if (
                                isDoneCounting &&
                                shouldApplyAppBrowserFindResult(
                                    isSearching = isSearching,
                                    visibleQuery = searchQuery,
                                    dispatchedQuery = dispatchedFindQuery,
                                )
                            ) {
                                browserViewModel.onAction(
                                    BrowserAction.UpdateSearchMatches(
                                        activeMatchOrdinal = activeMatchOrdinal,
                                        numberOfMatches = numberOfMatches,
                                    ),
                                )
                            }
                        },
                        onScrollSnapshotChanged = { snapshot ->
                            scrollProgress = snapshot.progress
                            headerCollapseState = buildBrowserHeaderCollapseState(
                                currentOffsetPx = headerCollapseState.offsetPx,
                                scrollY = snapshot.scrollY,
                                deltaY = snapshot.deltaY,
                                maxCollapseOffsetPx = headerCollapseDistancePx,
                                isEnabled = headerCollapseEnabled,
                            )
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
                                return@BrowserWebView
                            }
                            fileChooserLauncher.launch(chooserIntent)
                        },
                        onPopupRequest = ::handlePopupRequest,
                        onDownloadRequest = ::handleDownloadRequest,
                        onWebPermissionRequest = ::handleWebPermissionRequest,
                        onPageLoadError = { error ->
                            currentPageLoadError = error
                            isPullRefreshing = false
                        },
                        onSslError = { handler, error ->
                            val safeHandler = handler ?: return@BrowserWebView
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
                }
            } else {
                BrowserWebView(
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
                dismissPageLoadError()
                pageUrl?.let { currentUrl = it }
                currentPageTitle = null
                instantViewLoader.onPageStarted(pageUrl)
                pageThemeColor = null
                scrollProgress = 0f
                headerCollapseState = BrowserHeaderCollapseState()
                dispatchedFindQuery = null
                loadProgressState = BrowserLoadProgressState().start()
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
                isPullRefreshing = false
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                val finishedUrl = pageUrl?.takeIf(String::isNotBlank) ?: currentUrl
                instantViewLoader.onPageFinished(finishedUrl)
                appContainer.appBrowserHistoryStore.recordVisit(
                    url = finishedUrl,
                    title = currentPageTitle,
                    favicon = historyFavicons[finishedUrl] ?: view?.favicon,
                )
                if (finishedUrl.contains("linux.do")) {
                    appContainer.cookieSyncBridge.syncFromWebView(finishedUrl)
                }
                resolveAppBrowserThemeColor(
                    webView = view,
                    expectedUrl = finishedUrl,
                ) { resolvedColor ->
                    if (currentUrl == finishedUrl) {
                        pageThemeColor = resolvedColor
                    }
                }
                publishPreview(
                    snapshot = captureAppBrowserPreviewBitmap(view),
                )
            },
            onProgressChanged = { newProgress ->
                val normalizedProgress = newProgress / 100f
                loadProgressState = loadProgressState.onActualProgressChanged(normalizedProgress)
                instantViewLoader.onPageSnapshotChanged(
                    pageUrl = currentUrl,
                    pageProgress = normalizedProgress,
                )
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
                if (
                    isDoneCounting &&
                    shouldApplyAppBrowserFindResult(
                        isSearching = isSearching,
                        visibleQuery = searchQuery,
                        dispatchedQuery = dispatchedFindQuery,
                    )
                ) {
                    browserViewModel.onAction(
                        BrowserAction.UpdateSearchMatches(
                            activeMatchOrdinal = activeMatchOrdinal,
                            numberOfMatches = numberOfMatches,
                        ),
                    )
                }
            },
            onScrollSnapshotChanged = { snapshot ->
                scrollProgress = snapshot.progress
                headerCollapseState = buildBrowserHeaderCollapseState(
                    currentOffsetPx = headerCollapseState.offsetPx,
                    scrollY = snapshot.scrollY,
                    deltaY = snapshot.deltaY,
                    maxCollapseOffsetPx = headerCollapseDistancePx,
                    isEnabled = headerCollapseEnabled,
                )
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
                    return@BrowserWebView
                }
                fileChooserLauncher.launch(chooserIntent)
            },
            onPopupRequest = ::handlePopupRequest,
            onDownloadRequest = ::handleDownloadRequest,
            onWebPermissionRequest = ::handleWebPermissionRequest,
            onPageLoadError = { error ->
                currentPageLoadError = error
                isPullRefreshing = false
            },
            onSslError = { handler, error ->
                val safeHandler = handler ?: return@BrowserWebView
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
            }
        }

        currentPageLoadError?.let { pageLoadError ->
            val errorPresentation: RequestErrorPresentation = remember(pageLoadError) {
                requestErrorPresentation(
                    rawMessage = pageLoadError.rawMessage,
                    genericTitle = if (pageLoadError.kind == BrowserPageLoadErrorKind.Http) {
                        "Page couldn't load"
                    } else {
                        "Couldn't open page"
                    },
                    genericDescription = if (pageLoadError.kind == BrowserPageLoadErrorKind.Http) {
                        buildString {
                            append("The server returned an error")
                            pageLoadError.statusCode?.let { code ->
                                append(" (")
                                append(code)
                                pageLoadError.reasonPhrase?.takeIf(String::isNotBlank)?.let { reason ->
                                    append(" ")
                                    append(reason)
                                }
                                append(")")
                            }
                            append(". Try again or open it externally.")
                        }
                    } else {
                        "The page failed to load. Try again or open it externally."
                    },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.backgroundBase.copy(alpha = 0.96f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RequestErrorCard(
                        presentation = errorPresentation,
                        onRetry = {
                            dismissPageLoadError()
                            webViewController.reload()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.foundation.layout.Row {
                        TextButton(
                            onClick = {
                                clipboardManager?.setPrimaryClip(ClipData.newPlainText(null,pageLoadError.url))
                                bulletinHostState?.show(
                                    BulletinMessage(
                                        title = linkCopiedText,
                                        tone = BulletinTone.Success,
                                    ),
                                )
                            },
                        ) {
                            Text(text = stringResource(R.string.app_browser_copy_link))
                        }
                        TextButton(
                            onClick = {
                                context.openInSystemBrowser(pageLoadError.url)
                            },
                        ) {
                            Text(text = stringResource(R.string.app_browser_open_in_browser))
                        }
                    }
                }
            }
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
    }
}

private fun MutableMap<String, Bitmap>.rememberBest(url: String, icon: Bitmap) {
    val current = this[url]
    if (current == null || (icon.width * icon.height) > (current.width * current.height)) {
        this[url] = icon
    }
}

private fun readBrowserHistoryEntries(
    webView: android.webkit.WebView?,
    visits: List<BrowserHistoryVisit>,
    favicons: Map<String, Bitmap>,
): List<BrowserHistoryEntry> {
    val historyList = webView?.let(android.webkit.WebView::copyBackForwardList) ?: return emptyList()
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
                BrowserHistoryEntry(
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
    visits: MutableList<BrowserHistoryVisit>,
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
        BrowserHistoryVisit(
            url = resolvedUrl,
            visitedAtMillis = visitedAtMillis,
        ),
    )
    onRecorded(resolvedUrl, visitedAtMillis)
}

private data class BrowserHistoryVisit(
    val url: String,
    val visitedAtMillis: Long,
)

private data class PendingSslRequest(
    val handler: SslErrorHandler,
    val error: SslError?,
)

private data class PendingWebPermissionRequest(
    val permissionRequest: PermissionRequest,
    val requestData: BrowserWebPermissionRequestData,
)

private fun resolveAppBrowserThemeColor(
    webView: BrowserWebViewView?,
    expectedUrl: String,
    onResolved: (Color?) -> Unit,
) {
    if (webView == null || expectedUrl.isBlank()) {
        onResolved(null)
        return
    }
    webView.evaluateJavascript(appBrowserThemeColorJavascript) { rawValue ->
        if (webView.url != expectedUrl) {
            return@evaluateJavascript
        }
        onResolved(rawValue.parseAppBrowserThemeColor())
    }
}

private fun String?.parseAppBrowserThemeColor(): Color? {
    val candidate = this
        ?.trim()
        ?.takeUnless { value -> value == "null" }
        ?.removeSurrounding("\"")
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null

    val normalizedCandidate = candidate.lowercase()
    if (normalizedCandidate.startsWith("rgb(") || normalizedCandidate.startsWith("rgba(")) {
        val values = normalizedCandidate
            .substringAfter('(')
            .substringBeforeLast(')')
            .split(',')
            .map(String::trim)
        if (values.size !in 3..4) {
            return null
        }
        val red = values.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 255) ?: return null
        val green = values.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 255) ?: return null
        val blue = values.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 255) ?: return null
        val alpha = values.getOrNull(3)
            ?.toFloatOrNull()
            ?.coerceIn(0f, 1f)
            ?.let { (it * 255f).toInt() }
            ?: 255
        return Color(
            android.graphics.Color.argb(alpha, red, green, blue),
        )
    }

    return runCatching {
        Color(AndroidColor.parseColor(candidate))
    }.getOrNull()
}

private fun captureAppBrowserPreviewBitmap(
    webView: BrowserWebViewView?,
): Bitmap? = captureScaledViewBitmap(
    view = webView,
    maxWidthPx = 720,
    maxHeightPx = 1280,
)

private fun captureScaledViewBitmap(
    view: View?,
    maxWidthPx: Int,
    maxHeightPx: Int,
): Bitmap? {
    val safeView = view ?: return null
    val sourceWidth = safeView.width.takeIf { it > 0 } ?: safeView.measuredWidth
    val sourceHeight = safeView.height.takeIf { it > 0 } ?: safeView.measuredHeight
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        return null
    }
    val scale = minOf(
        1f,
        maxWidthPx.toFloat() / sourceWidth.toFloat(),
        maxHeightPx.toFloat() / sourceHeight.toFloat(),
    )
    val targetWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(
        targetWidth,
        targetHeight,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    safeView.draw(canvas)
    return bitmap
}

private const val appBrowserRouteExitDurationMillis = 250
private const val appBrowserFindDispatchDebounceMillis = 400L
private const val appBrowserProgressDismissDelayMillis = 180L
private const val appBrowserThemeColorJavascript =
    "(function(){var meta=document.querySelector('meta[name=\"theme-color\" i]');return meta?meta.content:null;})()"

private class BrowserViewModelFactory(
    private val initialUrl: String,
    private val baseUrl: String,
    private val searchEngine: AppBrowserSearchEngine,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(BrowserViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return BrowserViewModel(
            initialUrl = initialUrl,
            baseUrl = baseUrl,
            initialSearchEngine = searchEngine,
        ) as T
    }
}
