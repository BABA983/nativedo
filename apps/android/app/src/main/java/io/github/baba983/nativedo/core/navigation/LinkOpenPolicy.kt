package io.github.baba983.nativedo.core.navigation

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface LinkNavigationDecision {
    data class NativeTopic(val topicId: Long) : LinkNavigationDecision
    data class NativeUser(val username: String) : LinkNavigationDecision
    data class ExternalApp(val url: String) : LinkNavigationDecision
    data class AppBrowser(val url: String) : LinkNavigationDecision
    data class SystemBrowser(val url: String) : LinkNavigationDecision
}

internal class LinkOpenPolicy(
    private val baseUrl: String,
) {

    fun resolve(url: String): LinkNavigationDecision {
        val scheme = url.substringBefore(":", missingDelimiterValue = "").lowercase()
        if (scheme in externalAppSchemes) {
            return LinkNavigationDecision.ExternalApp(url)
        }

        val baseHttpUrl = baseUrl.toHttpUrlOrNull()
            ?: return LinkNavigationDecision.AppBrowser(url)
        val resolvedUrl = baseHttpUrl.resolve(url) ?: url.toHttpUrlOrNull()
            ?: return LinkNavigationDecision.AppBrowser(url)

        if (!resolvedUrl.host.equals(baseHttpUrl.host, ignoreCase = true)) {
            return LinkNavigationDecision.ExternalApp(resolvedUrl.toString())
        }

        val segments = resolvedUrl.pathSegments.filter { it.isNotBlank() }

        return when (segments.firstOrNull()) {
            "t" -> parseTopicDecision(segments) ?: LinkNavigationDecision.AppBrowser(resolvedUrl.toString())
            "u" -> segments.getOrNull(1)?.let { LinkNavigationDecision.NativeUser(it) }
                ?: LinkNavigationDecision.AppBrowser(resolvedUrl.toString())
            else -> LinkNavigationDecision.AppBrowser(resolvedUrl.toString())
        }
    }

    private fun parseTopicDecision(segments: List<String>): LinkNavigationDecision.NativeTopic? {
        val directId = segments.getOrNull(1)?.toLongOrNull()
        if (directId != null) return LinkNavigationDecision.NativeTopic(directId)

        val sluggedId = segments.getOrNull(2)?.toLongOrNull()
        if (sluggedId != null) return LinkNavigationDecision.NativeTopic(sluggedId)

        return null
    }

    private companion object {
        val externalAppSchemes = setOf("mailto", "tel", "intent")
    }
}
