package io.github.baba983.nativedo.feature.auth.ui

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LoginWebViewRoute(onLoginFinished: () -> Unit) {
    val hasFinished = remember { mutableStateOf(false) }

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
                webViewClient = object : WebViewClient() {}
                loadUrl("https://linux.do/login")
            }
        },
        update = { webView ->
            val currentUrl = webView.url.orEmpty()
            if (
                !hasFinished.value &&
                currentUrl.contains("linux.do") &&
                !currentUrl.contains("/login")
            ) {
                hasFinished.value = true
                onLoginFinished()
            }
        },
    )
}
