package io.github.baba983.nativedo.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.baba983.nativedo.core.session.CookieSyncBridge
import io.github.baba983.nativedo.core.storage.SvgSpriteStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class DiscourseSvgSpriteLoader(
    private val appContext: Context,
    private val baseUrl: String,
    private val userAgent: String,
    private val cookieSyncBridge: CookieSyncBridge,
    private val svgSpriteStore: SvgSpriteStore,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var inFlight: CompletableDeferred<Boolean>? = null

    fun ensureLoadedIfNeeded() {
        val cached = svgSpriteStore.sprite.value
        if (looksLikeDiscourseSvgSpriteMarkup(cached)) {
            svgSpriteDebugLog("ensureLoadedIfNeeded: skip, cache ${summarizeDiscourseSvgSpriteMarkup(cached)}")
            return
        }
        svgSpriteDebugLog("ensureLoadedIfNeeded: schedule hidden WebView load")
        scope.launch {
            val ok = runCatching { ensureLoaded() }.getOrDefault(false)
            svgSpriteDebugLog("ensureLoadedIfNeeded: finished ok=$ok cache=${summarizeDiscourseSvgSpriteMarkup(svgSpriteStore.sprite.value)}")
        }
    }

    suspend fun ensureLoaded(): Boolean = mutex.withLock {
        if (looksLikeDiscourseSvgSpriteMarkup(svgSpriteStore.sprite.value)) {
            return true
        }
        val existing = inFlight
        if (existing != null) {
            svgSpriteDebugLog("ensureLoaded: await in-flight")
            return existing.await()
        }
        val deferred = CompletableDeferred<Boolean>()
        inFlight = deferred
        try {
            svgSpriteDebugLog("ensureLoaded: start hidden WebView")
            val success = withContext(Dispatchers.Main.immediate) {
                withTimeout(LOAD_TIMEOUT_MILLIS) {
                    loadSpriteViaHiddenWebView()
                }
            }
            deferred.complete(success)
            success
        } catch (error: CancellationException) {
            deferred.complete(false)
            throw error
        } catch (error: Throwable) {
            svgSpriteDebugLog("ensureLoaded: failed ${error.javaClass.simpleName}: ${error.message}")
            deferred.complete(false)
            false
        } finally {
            if (inFlight === deferred) {
                inFlight = null
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadSpriteViaHiddenWebView(): Boolean = suspendCancellableCoroutine { continuation ->
        cookieSyncBridge.syncToWebView()
        val webView = WebView(appContext)
        var finished = false
        var captureAttempts = 0
        lateinit var timeoutRunnable: Runnable

        fun complete(success: Boolean, reason: String) {
            if (finished) {
                return
            }
            finished = true
            mainHandler.removeCallbacks(timeoutRunnable)
            svgSpriteDebugLog("loadSprite: complete success=$success reason=$reason attempts=$captureAttempts")
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
            if (continuation.isActive) {
                continuation.resume(success)
            }
        }

        fun tryCaptureFromPage(view: WebView, url: String, trigger: String) {
            if (finished) {
                return
            }
            if (!isDiscourseSameOrigin(currentUrl = url, baseUrl = baseUrl)) {
                svgSpriteDebugLog("tryCapture: skip wrong origin url=$url trigger=$trigger")
                return
            }
            captureAttempts += 1
            svgSpriteDebugLog("tryCapture: #$captureAttempts url=$url trigger=$trigger")
            view.evaluateJavascript(DISCOURSE_ASSETS_ICONS_SCRIPT) { rawValue ->
                val markup = decodeJavascriptString(rawValue)
                svgSpriteDebugLog("tryCapture: js result ${summarizeDiscourseSvgSpriteMarkup(markup)}")
                val saved = saveDiscourseSvgSpriteMarkup(svgSpriteStore, markup)
                if (saved) {
                    complete(true, "saved")
                }
            }
        }

        timeoutRunnable = Runnable { complete(false, "timeout") }
        mainHandler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MILLIS)

        continuation.invokeOnCancellation {
            mainHandler.removeCallbacks(timeoutRunnable)
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }

        webView.visibility = View.GONE
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = userAgent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                tryCaptureFromPage(view, url, "onPageFinished")
                if (!finished) {
                    RETRY_DELAYS_MILLIS.forEach { delayMs ->
                        mainHandler.postDelayed({
                            tryCaptureFromPage(view, url, "retry@${delayMs}ms")
                        }, delayMs)
                    }
                }
            }
        }

        val loadUrl = baseUrl.trimEnd('/') + "/"
        svgSpriteDebugLog("loadSprite: loadUrl=$loadUrl")
        webView.loadUrl(loadUrl)
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 20_000L
        val RETRY_DELAYS_MILLIS = longArrayOf(1_000L, 3_000L, 6_000L)
    }
}