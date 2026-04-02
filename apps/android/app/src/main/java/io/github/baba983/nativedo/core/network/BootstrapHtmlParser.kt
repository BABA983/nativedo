package io.github.baba983.nativedo.core.network

import org.jsoup.Jsoup

data class ParsedBootstrap(
    val currentUsername: String?,
    val csrfToken: String,
)

object BootstrapHtmlParser {
    fun parse(html: String): ParsedBootstrap {
        val document = Jsoup.parse(html)
        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content").orEmpty()
        val preloaded = buildString {
            append(document.selectFirst("#data-preloaded")?.html().orEmpty())
            append(document.selectFirst("body")?.attr("data-preloaded").orEmpty())
        }
        val currentUsername = document
            .selectFirst("meta[name=current-username]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: Regex("\"username\":\"([^\"]+)\"")
                .find(preloaded)
                ?.groupValues
                ?.getOrNull(1)

        return ParsedBootstrap(
            currentUsername = currentUsername,
            csrfToken = csrfToken,
        )
    }
}
