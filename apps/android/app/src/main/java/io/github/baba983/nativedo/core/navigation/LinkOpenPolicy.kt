package io.github.baba983.nativedo.core.navigation

import io.github.baba983.nativedo.core.storage.AppBrowserExternalOpenMode
import io.github.baba983.nativedo.core.storage.normalizeAppBrowserRestrictedDomain
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
    private val restrictedDomains: Set<String> = emptySet(),
    private val defaultExternalOpenMode: AppBrowserExternalOpenMode = AppBrowserExternalOpenMode.InAppBrowser,
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
            val normalizedHost = normalizeAppBrowserRestrictedDomain(resolvedUrl.host)
            if (normalizedHost != null && normalizedHost in restrictedDomains) {
                return LinkNavigationDecision.SystemBrowser(resolvedUrl.toString())
            }
            return when (defaultExternalOpenMode) {
                AppBrowserExternalOpenMode.InAppBrowser -> LinkNavigationDecision.ExternalApp(resolvedUrl.toString())
                AppBrowserExternalOpenMode.SystemBrowser -> LinkNavigationDecision.SystemBrowser(resolvedUrl.toString())
            }
        }

        val segments = resolvedUrl.pathSegments.filter { it.isNotBlank() }
        val normalizedHost = normalizeAppBrowserRestrictedDomain(resolvedUrl.host)

        return when (segments.firstOrNull()) {
            "t" -> parseTopicDecision(segments) ?: restrictedOrBrowserDecision(
                url = resolvedUrl.toString(),
                normalizedHost = normalizedHost,
            )
            "u" -> segments.getOrNull(1)?.let { LinkNavigationDecision.NativeUser(it) }
                ?: restrictedOrBrowserDecision(
                    url = resolvedUrl.toString(),
                    normalizedHost = normalizedHost,
                )
            else -> restrictedOrBrowserDecision(
                url = resolvedUrl.toString(),
                normalizedHost = normalizedHost,
            )
        }
    }

    private fun parseTopicDecision(segments: List<String>): LinkNavigationDecision.NativeTopic? {
        val directId = segments.getOrNull(1)?.toLongOrNull()
        if (directId != null) return LinkNavigationDecision.NativeTopic(directId)

        val sluggedId = segments.getOrNull(2)?.toLongOrNull()
        if (sluggedId != null) return LinkNavigationDecision.NativeTopic(sluggedId)

        return null
    }

    private fun restrictedOrBrowserDecision(
        url: String,
        normalizedHost: String?,
    ): LinkNavigationDecision {
        if (normalizedHost != null && normalizedHost in restrictedDomains) {
            return LinkNavigationDecision.SystemBrowser(url)
        }
        return LinkNavigationDecision.AppBrowser(url)
    }

    private companion object {
        val externalAppSchemes = setOf("mailto", "tel", "intent")
    }
}
