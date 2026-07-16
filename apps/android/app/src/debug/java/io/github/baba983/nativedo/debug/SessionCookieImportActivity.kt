package io.github.baba983.nativedo.debug

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.baba983.nativedo.MainActivity
import io.github.baba983.nativedo.NativeDoApp
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Debug-only bridge for importing a browser Cookie header into both stores. */
class SessionCookieImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val result = runCatching { importCookies() }
            result.onFailure { error ->
                Log.e(TAG, "Cookie import failed", error)
                Toast.makeText(this@SessionCookieImportActivity, "Cookie import failed", Toast.LENGTH_LONG).show()
            }
            result.onSuccess { authenticatedCookieImported ->
                Log.i(TAG, "Cookie import completed; authenticatedCookieImported=$authenticatedCookieImported")
                Toast.makeText(
                    this@SessionCookieImportActivity,
                    if (authenticatedCookieImported) "Login cookies imported" else "No authenticated cookie imported",
                    Toast.LENGTH_LONG,
                ).show()
            }
            startActivity(
                Intent(this@SessionCookieImportActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            finish()
        }
    }

    private suspend fun importCookies(): Boolean {
        val cookieFile = cacheDir.resolve(COOKIE_FILE_NAME)
        val cookieHeader = try {
            cookieFile.readText().trim()
        } finally {
            cookieFile.delete()
        }
        check(cookieHeader.isNotEmpty()) { "Missing cookie header in ${cookieFile.name}" }
        val cookieSegments = authenticatedCookieSegments(cookieHeader)
        check(cookieSegments.any { segment -> segment.substringBefore('=') == AUTH_COOKIE }) {
            "Missing authenticated cookie"
        }

        val app = applicationContext as NativeDoApp
        val container = app.appContainer
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val writesSucceeded = withTimeout(COOKIE_OPERATION_TIMEOUT_MILLIS) {
            cookieManager.clearAsync()
            cookieSegments
                .map { segment ->
                    cookieManager.setCookieAsync(
                        container.baseUrl,
                        "$segment; Domain=linux.do; Path=/; Secure; SameSite=Lax",
                    )
                }
                .all { succeeded -> succeeded }
        }

        cookieManager.flush()
        val authenticatedCookieImported = container.cookieSyncBridge.syncFromWebView()
        container.sessionCoordinator.start()
        return writesSucceeded && authenticatedCookieImported
    }

    private suspend fun CookieManager.clearAsync() {
        suspendCancellableCoroutine { continuation ->
            removeAllCookies {
                flush()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private suspend fun CookieManager.setCookieAsync(url: String, cookie: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            setCookie(url, cookie) { succeeded ->
                if (continuation.isActive) continuation.resume(succeeded)
            }
        }
    }

    private companion object {
        private const val TAG = "SessionCookieImport"
        private const val COOKIE_FILE_NAME = "session-cookie-import.txt"
        private const val AUTH_COOKIE = "_t"
        private const val COOKIE_OPERATION_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun authenticatedCookieSegments(cookieHeader: String): List<String> {
    return cookieHeader
        .split(';')
        .map(String::trim)
        .filter { segment ->
            val separator = segment.indexOf('=')
            separator > 0 &&
                segment.substring(0, separator).trim() in AUTHENTICATION_COOKIE_NAMES &&
                segment.substring(separator + 1).isNotBlank()
        }
}

private val AUTHENTICATION_COOKIE_NAMES = setOf("_t", "_forum_session", "cf_clearance")
