package io.github.baba983.nativedo.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.baba983.nativedo.core.session.SessionDiagnostics
import io.github.baba983.nativedo.core.session.SharedCookieJar
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Re-mints `cf_clearance` through the one network stack Cloudflare trusts.
 *
 * Native OkHttp and the WebView share one cookie store but not one TLS
 * fingerprint. Once the clearance expires (or the site is reached through a
 * different egress IP), the next native request is challenged - even though the
 * user's browser session is perfectly valid. Opening a hidden same-origin page
 * lets Chromium solve a silent challenge and write a new clearance into the
 * shared jar, which is exactly where the next native request reads it.
 *
 * Deliberately lazy: it runs only after a native challenge, never on a schedule
 * and never while logged out. If the challenge needs a human, the page times
 * out and the caller falls back to the visible recovery flow.
 */
class BrowserClearanceRefresher(
    private val appContext: Context,
    private val baseUrl: String,
    private val userAgent: String,
    private val cookieJar: SharedCookieJar,
) {
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val siteUrl = baseUrl.toHttpUrl()
    private var lastAttemptAt = 0L
    private var lastSuccessAt = 0L

    /**
     * @return true when the shared jar now holds a clearance the caller can
     * retry with. A false result means "do not replay"; the visible recovery
     * flow still gets the same cookies it had before.
     */
    suspend fun refresh(): Boolean {
        if (!cookieJar.hasAuthenticatedCookies()) return false
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val incumbent = cookieJar.cfClearance(siteUrl)
            if (lastSuccessAt != 0L && now - lastSuccessAt < SUCCESS_QUIET_MILLIS &&
                incumbent != null
            ) {
                return@withLock true
            }
            if (lastAttemptAt != 0L && now - lastAttemptAt < ATTEMPT_COOLDOWN_MILLIS) {
                return@withLock false
            }
            lastAttemptAt = now
            SessionDiagnostics.record("clearance_refresh_started", cookieJar.revision())
            val loaded = loadHomePage(incumbent)
            if (loaded) cookieJar.flush()
            val refreshed = cookieJar.cfClearance(siteUrl)
            val usable = loaded && refreshed != null
            if (usable) {
                lastSuccessAt = System.currentTimeMillis()
                SessionDiagnostics.record("clearance_refresh_succeeded", cookieJar.revision(), accepted = true)
            } else {
                SessionDiagnostics.record("clearance_refresh_failed", cookieJar.revision(), accepted = false)
            }
            usable
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadHomePage(previousClearance: String?): Boolean =
        try {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    var completed = false
                    var challengeSeen = false
                    var settleJob: Runnable? = null
                    var pollCount = 0
                    val webView = WebView(appContext)
                    lateinit var timeout: Runnable

                    fun finish(loaded: Boolean) {
                        if (completed) return
                        completed = true
                        mainHandler.removeCallbacks(timeout)
                        settleJob?.let(mainHandler::removeCallbacks)
                        runCatching {
                            webView.stopLoading()
                            webView.loadUrl("about:blank")
                            webView.destroy()
                        }
                        if (continuation.isActive) continuation.resume(loaded)
                    }

                    timeout = Runnable { finish(false) }
                    mainHandler.postDelayed(timeout, LOAD_TIMEOUT_MILLIS)
                    continuation.invokeOnCancellation {
                        mainHandler.removeCallbacks(timeout)
                        settleJob?.let(mainHandler::removeCallbacks)
                        runCatching {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }

                    webView.visibility = View.GONE
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = this@BrowserClearanceRefresher.userAgent
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                    fun schedulePoll(delayMillis: Long, job: Runnable) {
                        if (completed) return
                        settleJob?.let(mainHandler::removeCallbacks)
                        settleJob = job
                        mainHandler.postDelayed(job, delayMillis)
                    }

                    fun pollForClearance() {
                        if (completed) return
                        pollCount += 1
                        val minted = cookieJar.cfClearance(siteUrl)
                            ?.let { it != previousClearance } == true
                        if (minted) {
                            finish(true)
                            return
                        }
                        if (!challengeSeen) {
                            finish(true)
                            return
                        }
                        if (pollCount >= MAX_POLL_ATTEMPTS) {
                            finish(false)
                            return
                        }
                        schedulePoll(COOKIE_SETTLE_MILLIS, Runnable { pollForClearance() })
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (completed || !isDiscourseSameOrigin(url, baseUrl)) return
                            // Do not settle on the challenge page itself; wait
                            // for the silent challenge to mint a new clearance
                            // or for Cloudflare to navigate to the real page.
                            if (isChallengePath(url)) return
                            schedulePoll(0, Runnable { pollForClearance() })
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) challengeSeen = true
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            if (request.isForMainFrame &&
                                errorResponse.statusCode in setOf(403, 429, 503)
                            ) {
                                challengeSeen = true
                            }
                        }
                    }

                    // Start checking before the page finishes: the challenge
                    // can mint a clearance while resources are still loading.
                    schedulePoll(INITIAL_POLL_MILLIS, Runnable { pollForClearance() })
                    webView.loadUrl(baseUrl.trimEnd('/') + "/")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // A device with no usable WebView provider must not turn the
            // original challenge into a different request failure.
            false
        }

    private fun isChallengePath(url: String): Boolean {
        val path = runCatching { android.net.Uri.parse(url).path.orEmpty() }.getOrDefault("")
        return path.startsWith("/cdn-cgi/") || path == "/challenge" || path.startsWith("/challenge/")
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 12_000L
        const val INITIAL_POLL_MILLIS = 800L
        const val COOKIE_SETTLE_MILLIS = 400L
        const val MAX_POLL_ATTEMPTS = 20
        const val ATTEMPT_COOLDOWN_MILLIS = 15_000L
        const val SUCCESS_QUIET_MILLIS = 60_000L
    }
}
