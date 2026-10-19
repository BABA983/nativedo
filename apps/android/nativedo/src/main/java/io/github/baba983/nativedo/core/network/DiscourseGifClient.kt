// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.core.network

import java.io.IOException
import retrofit2.HttpException

/**
 * A GIF picker tile that the provider described, with the provider's own dimensions.
 *
 * <p>The dimension pair is the site component's Markdown contract (`![title|WxH](url)`); `0` means
 * the provider did not say, so the caller leaves the size out instead of inventing one.</p>
 */
data class DiscourseGif(
    val title: String,
    val url: String,
    val width: Int,
    val height: Int,
)

/** A provider category: its readable name and the term its search uses. */
data class DiscourseGifCategory(
    val name: String,
    val searchTerm: String,
    val imageUrl: String?,
)

/** One provider page. [next] is the cursor for the following page, or null when this is the last. */
data class DiscourseGifPage(
    val gifs: List<DiscourseGif>,
    val next: String?,
)

/**
 * The site does not serve the GIF routes here: its `ensure_gifs_enabled` answer (404), which is the
 * `enable_gifs` gate being off or the plugin being absent - both are site configuration, not a
 * transient bug.
 *
 * <p>Deliberately not a bare [HttpException]: the picker explains a missing capability instead of
 * drawing an empty wall or a fake "no results". Nothing else is folded into it - a 403 is the
 * site's own refusal (a provider key and a permission are indistinguishable from here), a
 * challenge goes to the WebView recovery, a logout expires the session, and a rate limit, a 5xx or
 * a malformed body stay retryable.</p>
 */
class DiscourseGifUnavailableException(cause: Throwable? = null) : IOException(
    "The site does not serve the GIF routes",
    cause,
)

/**
 * The site-backed GIF reads.
 *
 * <p>GIFs are a signed-in site feature: Discourse's GIF controller runs `requires_login` and the
 * provider key stays server-side. The two reads therefore run through
 * [DiscourseApiClient.runWithSessionBoundary] - the app's one session boundary, with its
 * stale-generation, challenge, logout, CSRF and rate-limit meanings - over the container's shared
 * OkHttp stack. This client adds only what is specific to the GIF routes: the provider's DTO
 * mapping, the absent-route answer as [DiscourseGifUnavailableException], and the delivery fence
 * the panel needs because it draws no session state of its own.</p>
 */
class DiscourseGifClient(
    private val service: DiscourseGifService,
    private val apiClient: DiscourseApiClient,
) {

    /** `GET /gifs/categories.json`, mapped to the site's readable categories. */
    suspend fun fetchCategories(): List<DiscourseGifCategory> =
        gifRead {
            service.categories().tags.mapNotNull { tag ->
                val searchTerm = tag.searchterm?.trim().orEmpty()
                if (searchTerm.isEmpty()) {
                    return@mapNotNull null
                }
                DiscourseGifCategory(
                    name = tag.name?.trim()?.takeIf(String::isNotEmpty) ?: searchTerm,
                    searchTerm = searchTerm,
                    imageUrl = tag.image?.trim()?.takeIf(String::isNotEmpty),
                )
            }.distinctBy { category -> category.searchTerm }
        }

    /**
     * `GET /gifs/search.json?q=&pos=`, mapped to the provider's own formats. The first page passes
     * `"0"` (the site's default); later pages pass the previous page's `next`. A result with no
     * usable format is skipped, not turned into a blank tile.
     */
    suspend fun search(query: String, position: String? = null): DiscourseGifPage {
        // An empty box is not a site read: it answers locally instead of resolving a session.
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return DiscourseGifPage(emptyList(), null)
        }
        return gifRead {
            val page = service.search(
                query = trimmed,
                position = position?.takeIf(String::isNotBlank) ?: "0",
            )
            val next = page.nextPosition()?.takeIf { it != position }
            DiscourseGifPage(
                gifs = page.results.mapNotNull { result -> result.toGif() },
                next = next,
            )
        }
    }

    private fun GifResultDto.toGif(): DiscourseGif? {
        // Klipy/Tenor name the same media in several formats. Prefer the site's webp answer, fall
        // back to gif, then to any provider format that has a URL. The chosen format is both the
        // preview and the inserted picture, exactly as the site's own picker inserts it.
        val format = mediaFormats["webp"]?.takeIf { !it.url.isNullOrBlank() }
            ?: mediaFormats["gif"]?.takeIf { !it.url.isNullOrBlank() }
            ?: mediaFormats.values.firstOrNull { !it.url.isNullOrBlank() }
            ?: return null
        val url = format.url?.trim().orEmpty()
        if (url.isEmpty()) {
            return null
        }
        return DiscourseGif(
            title = title?.trim().orEmpty().ifEmpty { "GIF" },
            url = url,
            width = format.dims?.getOrNull(0) ?: 0,
            height = format.dims?.getOrNull(1) ?: 0,
        )
    }

    /**
     * One read on the app's session boundary.
     *
     * <p>The boundary already classifies every refusal by the site's evidence and applies the
     * session's rules; this wrapper only maps the route's absence and fences delivery. The panel
     * keeps an answer on screen and observes no session state, so an answer whose session moved on
     * while it was in flight belongs to the previous reader and must not become its grid - the same
     * rule [DiscourseApiClient]'s own cached reads apply before handing a value back.</p>
     */
    private suspend fun <T> gifRead(block: suspend () -> T): T {
        val generation = apiClient.sessionGeneration()
        try {
            val value = apiClient.runWithSessionBoundary(requiresAuthentication = true, block = block)
            if (apiClient.sessionGeneration() != generation) {
                throw SessionChangedException()
            }
            return value
        } catch (error: HttpException) {
            // The read went out under a session that has since moved on: its answer says nothing
            // about this site for this reader.
            if (apiClient.sessionGeneration() != generation) {
                throw SessionChangedException()
            }
            // Every refusal with session evidence was already classified; a plain 404 is the
            // site saying this route does not exist here.
            if (error.code() == 404) {
                throw DiscourseGifUnavailableException(error)
            }
            throw error
        }
    }
}
