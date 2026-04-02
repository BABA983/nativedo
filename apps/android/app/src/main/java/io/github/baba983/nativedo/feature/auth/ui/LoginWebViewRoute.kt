package io.github.baba983.nativedo.feature.auth.ui

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.core.session.AppSessionState
import kotlinx.coroutines.launch

@Composable
fun LoginWebViewRoute(onLoginFinished: () -> Unit) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val hasFinished = remember { mutableStateOf(false) }
    val isRestoringSession = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setBackgroundColor(Color.Transparent.toArgb())
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        val currentUrl = url.orEmpty()
                        if (
                            hasFinished.value ||
                            isRestoringSession.value ||
                            !currentUrl.contains("linux.do") ||
                            currentUrl.contains("/login")
                        ) {
                            return
                        }

                        coroutineScope.launch {
                            isRestoringSession.value = true
                            appContainer.sessionCoordinator.restoreSession()
                            if (appContainer.sessionCoordinator.sessionState.value is AppSessionState.Authenticated) {
                                hasFinished.value = true
                                onLoginFinished()
                            } else {
                                isRestoringSession.value = false
                            }
                        }
                    }
                }
                loadUrl("https://linux.do/login")
            }
        },
    )
}
