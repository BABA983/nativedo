package io.github.baba983.nativedo.core.session

import android.content.Context
import io.github.baba983.nativedo.core.storage.CookieStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.telegram.messenger.browser.Browser

/**
 * Opening a linux.do page in upstream's in-app browser, with the app's session in it.
 *
 * <p>The native store is the single source of truth; the WebView jar is derived from it. Before the
 * browser is asked for a linux.do URL, the native cookies are seeded into the WebView jar, so the
 * page is the reader's own session instead of the anonymous one the login-time jar would long ago
 * have gone stale into. Pages on any other host are opened untouched - a linux.do cookie is never
 * written under another domain.</p>
 *
 * <p>Two jars that disagree on `_t` cannot be ordered from the outside: either the native jar's
 * token is the newer rotation, or the browser's is. Writing the wrong one over the right one can
 * cost the reader the only live token, so a disagreement is settled by asking the server which of
 * the two it still accepts ([SessionProbeResult]) before anything is written. The answers:</p>
 *
 * <ul>
 *   <li>`Accepted` - the native token is current (or the previous one, good for one request), so
 *   the native values replace the browser's;</li>
 *   <li>`Rejected` - the native token is stale and the browser jar holds the rotation; adopt it
 *   into the native store and leave the browser's cookies as they are;</li>
 *   <li>`Unavailable` - nothing is written: an unanswered question is not a licence to overwrite.
 *   The page then shows whatever the browser itself is, and the normal native flows still decide
 *   the session's fate.</li>
 * </ul>
 */
class InAppBrowserSession(
    private val cookieStore: CookieStore,
    private val bridge: CookieSyncBridge,
    private val probeSession: suspend () -> SessionProbeResult,
    private val openInAppBrowser: (Context, String) -> Unit = { context, url ->
        Browser.openUrl(context, url)
    },
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Seeds the jar for [url] when it is the site's own, then opens it in upstream's browser. */
    fun open(context: Context?, url: String) {
        if (context == null || url.isBlank()) return
        scope.launch(Dispatchers.Main.immediate) {
            prepare(url)
            openInAppBrowser(context, url)
        }
    }

    /**
     * Makes the browser jar able to show [url] as the signed-in reader when [url] is the site's
     * own host. Safe to call for any URL; nothing happens for the others.
     */
    suspend fun prepare(url: String) {
        if (!bridge.isSessionHost(url)) return
        val nativeToken = cookieStore.authenticatedToken() ?: return
        val browserToken = try {
            bridge.authTokenInWebView(url)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A conflicting or unreadable browser header is not a licence to overwrite it.
            return
        }
        when {
            browserToken == null -> {
                // There is nothing to clobber; the reader's session is only absent.
                val seeded = bridge.seedBrowserFromNative(
                    url = url,
                    overwriteNames = CookieSyncBridge.AUTH_COOKIE_NAMES,
                    timeoutMillis = BROWSER_WRITE_TIMEOUT_MILLIS,
                )
                SessionDiagnostics.record("browser_bridge_seed", cookieStore.revision(), accepted = seeded)
            }
            browserToken == nativeToken -> {
                bridge.seedBrowserFromNative(url = url, timeoutMillis = BROWSER_WRITE_TIMEOUT_MILLIS)
            }
            else -> when (probeWithTimeout()) {
                SessionProbeResult.Accepted -> {
                    val seeded = bridge.seedBrowserFromNative(
                        url = url,
                        overwriteNames = CookieSyncBridge.AUTH_COOKIE_NAMES,
                        timeoutMillis = BROWSER_WRITE_TIMEOUT_MILLIS,
                    )
                    SessionDiagnostics.record("browser_bridge_seed", cookieStore.revision(), accepted = seeded)
                }
                SessionProbeResult.Rejected -> {
                    // The browser's token is the rotation the native jar missed. Adopt it; the
                    // browser jar itself needs no seed (its auth values are the newer ones).
                    runCatching {
                        bridge.syncFromWebViewByName(url, CookieSyncBridge.AUTH_COOKIE_NAMES)
                    }
                    SessionDiagnostics.record("browser_bridge_adopt", cookieStore.revision())
                }
                SessionProbeResult.Unavailable -> {
                    SessionDiagnostics.record(
                        "browser_bridge_unavailable",
                        cookieStore.revision(),
                        accepted = false,
                    )
                }
            }
        }
    }

    private suspend fun probeWithTimeout(): SessionProbeResult =
        withTimeoutOrNull(SESSION_PROBE_TIMEOUT_MILLIS) { probeSession() }
            ?: SessionProbeResult.Unavailable

    private companion object {
        /**
         * A probe is one small GET; past this the page opens on the browser's own cookies rather
         * than keeping the reader waiting on an answer that is not needed for public pages.
         */
        const val SESSION_PROBE_TIMEOUT_MILLIS = 2_500L

        /** Long enough for Chromium's cookie store to confirm a handful of writes. */
        const val BROWSER_WRITE_TIMEOUT_MILLIS = 2_000L
    }
}
