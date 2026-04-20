package io.github.baba983.nativedo.feature.browser

import android.content.Context
import android.webkit.WebView

internal class BrowserWebViewView(
    context: Context,
) : WebView(context) {
    val verticalScrollRange: Int
        get() = computeVerticalScrollRange()

    val verticalScrollExtent: Int
        get() = computeVerticalScrollExtent()
}
