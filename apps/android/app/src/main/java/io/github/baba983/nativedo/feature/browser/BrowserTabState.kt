package io.github.baba983.nativedo.feature.browser

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.graphics.Color

internal data class BrowserTabState(
    val id: Long,
    val url: String,
    val title: String,
    val subtitle: String?,
    val isHttps: Boolean,
    val favicon: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val webViewBundle: Bundle? = null,
    val scrollPosition: Int = 0,
    val actionBarColor: Color = Color.Unspecified,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isErrorPage: Boolean = false,
)
