package io.github.baba983.nativedo.core.navigation

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface LinkOpenTarget {
    data class NativeTopic(val topicId: Long) : LinkOpenTarget
    data class NativeUser(val username: String) : LinkOpenTarget
    data class ExternalApp(val url: String) : LinkOpenTarget
    data class AppBrowser(val url: String) : LinkOpenTarget
    data class SystemBrowser(val url: String) : LinkOpenTarget
}

class LinkRouter(private val baseUrl: String) {

    fun resolve(url: String): LinkOpenTarget {
        val scheme = url.substringBefore(":", missingDelimiterValue = "").lowercase()
        if (scheme in EXTERNAL_APP_SCHEMES) {
            return LinkOpenTarget.ExternalApp(url)
        }

        val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return LinkOpenTarget.AppBrowser(url)
        val resolvedUrl = baseHttpUrl.resolve(url) ?: url.toHttpUrlOrNull()
            ?: return LinkOpenTarget.AppBrowser(url)

        if (!resolvedUrl.host.equals(baseHttpUrl.host, ignoreCase = true)) {
            return LinkOpenTarget.AppBrowser(resolvedUrl.toString())
        }

        val segments = resolvedUrl.pathSegments.filter { it.isNotBlank() }

        return when (segments.firstOrNull()) {
            "t" -> parseTopicTarget(segments) ?: LinkOpenTarget.AppBrowser(resolvedUrl.toString())
            "u" -> segments.getOrNull(1)?.let { LinkOpenTarget.NativeUser(it) }
                ?: LinkOpenTarget.AppBrowser(resolvedUrl.toString())
            else -> LinkOpenTarget.AppBrowser(resolvedUrl.toString())
        }
    }

    private fun parseTopicTarget(segments: List<String>): LinkOpenTarget.NativeTopic? {
        val directId = segments.getOrNull(1)?.toLongOrNull()
        if (directId != null) return LinkOpenTarget.NativeTopic(directId)

        val sluggedId = segments.getOrNull(2)?.toLongOrNull()
        if (sluggedId != null) return LinkOpenTarget.NativeTopic(sluggedId)

        return null
    }

    private companion object {
        val EXTERNAL_APP_SCHEMES = setOf("mailto", "tel", "intent")
    }
}
