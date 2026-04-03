package io.github.baba983.nativedo.core.network

import org.jsoup.Jsoup

data class ParsedBootstrap(
    val currentUsername: String?,
    val csrfToken: String,
)

object BootstrapHtmlParser {
    private val escapedCurrentUserUsernamePattern = Regex(
        """"currentUser"\s*:\s*"\{(?:[^"\\]|\\.)*?\\"username\\":\\"([^"\\]+)\\"""",
    )
    private val unescapedCurrentUserUsernamePattern = Regex(
        """"currentUser"\s*:\s*"\{.*?"username":"([^"]+)"""",
    )

    fun parse(html: String): ParsedBootstrap {
        val document = Jsoup.parse(html)
        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content").orEmpty()
        val preloadedElement = document.selectFirst("#data-preloaded")
        val preloaded = buildString {
            append(preloadedElement?.attr("data-preloaded").orEmpty())
            append(preloadedElement?.html().orEmpty())
            append(document.selectFirst("body")?.attr("data-preloaded").orEmpty())
        }
        val currentUsername = document
            .selectFirst("meta[name=current-username]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: escapedCurrentUserUsernamePattern.find(preloaded)?.groupValues?.getOrNull(1)
            ?: unescapedCurrentUserUsernamePattern.find(preloaded)?.groupValues?.getOrNull(1)

        return ParsedBootstrap(
            currentUsername = currentUsername,
            csrfToken = csrfToken,
        )
    }
}
