package io.github.baba983.nativedo.core.network

import android.content.Context
import android.webkit.WebSettings

internal const val FALLBACK_USER_AGENT: String =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

internal fun resolveNativeDoUserAgent(context: Context?): String {
    val appContext = context?.applicationContext ?: return FALLBACK_USER_AGENT
    return runCatching {
        WebSettings.getDefaultUserAgent(appContext)
    }.getOrDefault(FALLBACK_USER_AGENT)
}
