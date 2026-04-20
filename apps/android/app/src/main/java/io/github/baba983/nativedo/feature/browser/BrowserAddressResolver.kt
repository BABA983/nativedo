package io.github.baba983.nativedo.feature.browser

import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun resolveBrowserAddressInput(
    value: String,
    baseUrl: String,
    searchEngine: AppBrowserSearchEngine = AppBrowserSearchEngine.LinuxDo,
): String? {
    val trimmedValue = value.trim()
    if (trimmedValue.isBlank()) {
        return null
    }

    trimmedValue.toHttpUrlOrNull()?.let { return it.toString() }

    val baseHttpUrl = baseUrl.toHttpUrlOrNull()
    if (trimmedValue.startsWith("/")) {
        return baseHttpUrl?.resolve(trimmedValue)?.toString() ?: trimmedValue
    }

    if (!trimmedValue.contains(' ')) {
        val guessedUrl = "https://$trimmedValue".toHttpUrlOrNull()
        if (guessedUrl != null) {
            return guessedUrl.toString()
        }
    }

    return when (searchEngine) {
        AppBrowserSearchEngine.LinuxDo -> {
            baseHttpUrl
                ?.newBuilder()
                ?.encodedPath("/search")
                ?.query(null)
                ?.addQueryParameter("q", trimmedValue)
                ?.build()
                ?.toString()
                ?: trimmedValue
        }
        AppBrowserSearchEngine.Google -> {
            "https://www.google.com/search".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("q", trimmedValue)
                ?.build()
                ?.toString()
                ?: trimmedValue
        }
        AppBrowserSearchEngine.Bing -> {
            "https://www.bing.com/search".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("q", trimmedValue)
                ?.build()
                ?.toString()
                ?: trimmedValue
        }
    }
}
