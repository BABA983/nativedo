package io.github.baba983.nativedo.core.network

import android.content.Context
import android.os.LocaleList
import android.webkit.WebSettings
import java.util.Locale

internal const val FALLBACK_USER_AGENT: String =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

internal const val FALLBACK_ACCEPT_LANGUAGE: String = "zh-CN,zh;q=0.9,en;q=0.8"

internal fun resolveNativeDoUserAgent(context: Context?): String {
    val appContext = context?.applicationContext ?: return FALLBACK_USER_AGENT
    return runCatching {
        WebSettings.getDefaultUserAgent(appContext)
    }.getOrDefault(FALLBACK_USER_AGENT)
}

/**
 * Build the same `Accept-Language` header the WebView sends, from the device
 * locale list (Chromium expands each language with its base tag and assigns
 * descending q-values). A native client claiming the WebView's user agent but
 * sending a different language list is an avoidable fingerprint mismatch.
 */
internal fun resolveAcceptLanguage(): String {
    val locales = runCatching { LocaleList.getDefault() }.getOrNull()
        ?: return FALLBACK_ACCEPT_LANGUAGE
    if (locales.isEmpty) return FALLBACK_ACCEPT_LANGUAGE
    val tags = LinkedHashSet<String>()
    for (index in 0 until locales.size()) {
        val locale = locales[index]
        val language = locale.language.lowercase(Locale.ROOT)
        if (language.isBlank() || language == "und") continue
        val tag = locale.toLanguageTag().takeIf { it.isNotBlank() && it != "und" } ?: language
        tags.add(tag)
        val nextLanguage = if (index + 1 < locales.size()) {
            locales[index + 1].language.lowercase(Locale.ROOT)
        } else {
            null
        }
        if (nextLanguage != language) tags.add(language)
    }
    if (tags.isEmpty()) return FALLBACK_ACCEPT_LANGUAGE
    val ordered = tags.take(10)
    val builder = StringBuilder(ordered.first())
    var q = 9
    for (tag in ordered.drop(1)) {
        builder.append(',').append(tag)
        if (q > 0) {
            builder.append(";q=0.").append(q)
            q -= 1
        }
    }
    return builder.toString()
}
