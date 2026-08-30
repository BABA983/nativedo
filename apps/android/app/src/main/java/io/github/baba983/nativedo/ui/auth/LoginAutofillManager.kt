package io.github.baba983.nativedo.ui.auth

import android.webkit.JavascriptInterface
import android.webkit.WebView
import io.github.baba983.nativedo.core.session.CredentialStore
import org.json.JSONObject

/**
 * Manages WebView login form auto-fill and credential capture.
 *
 * Responsibilities:
 * 1. Inject JS to auto-fill saved username/password on the Discourse login page.
 * 2. Hook the login button to capture credentials via a JavascriptInterface.
 *
 * Security:
 * - The JavascriptInterface name "NativeDoLoginBridge" is scoped to the login
 *   WebView only and destroyed with it.
 * - Credentials are escaped via [JSONObject.quote] to prevent JS injection.
 */
internal class LoginAutofillManager(
    private val credentialStore: CredentialStore,
) {

    inner class Bridge {
        @JavascriptInterface
        fun captureCredentials(json: String) {
            runCatching {
                val data = JSONObject(json)
                val username = data.getString("username")
                val password = data.getString("password")
                if (username.isNotBlank() && password.isNotBlank()) {
                    credentialStore.save(username, password)
                }
            }
        }
    }

    /** Register the JavascriptInterface. Call before loadUrl. */
    fun attach(webView: WebView) {
        webView.addJavascriptInterface(Bridge(), "NativeDoLoginBridge")
    }

    /**
     * Auto-fill the login form and hook the submit button.
     * Call from onPageFinished when the URL contains "/login".
     */
    fun onPageFinished(webView: WebView, url: String?) {
        val urlStr = url?.toString() ?: return
        // Only inject on Discourse login pages.
        if (!urlStr.contains("/login")) return
        // Don't inject on email-login pages (no username/password form).
        if (urlStr.contains("/session/email-login/")) return

        val credentials = credentialStore.load()
        val escapedUser = credentials.username?.let { JSONObject.quote(it) } ?: "null"
        val escapedPass = credentials.password?.let { JSONObject.quote(it) } ?: "null"

        webView.evaluateJavascript(
            AUTOFILL_SCRIPT.format(escapedUser, escapedPass),
            null,
        )
    }

    companion object {
        private const val AUTOFILL_SCRIPT = """
            (function() {
                var savedUser = %s;
                var savedPass = %s;
                var filled = false;
                var hooked = false;
                var attempts = 0;
                var timer = setInterval(function() {
                    var userInput = document.getElementById('login-account-name');
                    var passInput = document.getElementById('login-account-password');
                    if (userInput && passInput) {
                        if (!filled && savedUser && savedPass) {
                            filled = true;
                            userInput.value = savedUser;
                            passInput.value = savedPass;
                            userInput.dispatchEvent(new Event('input', {bubbles: true}));
                            passInput.dispatchEvent(new Event('input', {bubbles: true}));
                        }
                        if (!hooked) {
                            hooked = true;
                            var loginBtn = document.getElementById('login-button');
                            if (loginBtn) {
                                loginBtn.addEventListener('click', function() {
                                    var u = document.getElementById('login-account-name');
                                    var p = document.getElementById('login-account-password');
                                    if (u && p && u.value && p.value) {
                                        NativeDoLoginBridge.captureCredentials(
                                            JSON.stringify({username: u.value, password: p.value})
                                        );
                                    }
                                }, true);
                            }
                        }
                        clearInterval(timer);
                    }
                    if (++attempts > 30) clearInterval(timer);
                }, 300);
            })();
        """
    }
}
