// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The site's own GIF proxy.
 *
 * <p>Discourse core (and the `discourse-gifs` component it grew from) keeps the provider key on the
 * server and exposes two reads to a signed-in reader: the featured categories and a provider search.
 * The response bodies are the provider's own shape - the site is a proxy, not a translator - so the
 * DTOs here are deliberately loose: a missing `media_formats` entry or an unusable format is skipped
 * per result, not an error for the whole page. The provider key is never a client concern and is
 * never sent by this client.</p>
 *
 * <p>The routes are the site's relative paths, so they travel through the shared session-aware
 * OkHttp stack exactly like every other Discourse read. The provider CDN URLs inside a result are
 * loaded separately by [DiscourseGifClient]'s caller; they are public media and never carry the
 * site's cookies.</p>
 */
interface DiscourseGifService {
    /** `GET /gifs/categories.json`: the provider's featured categories. */
    @GET("gifs/categories.json")
    suspend fun categories(): GifCategoriesResponseDto

    /**
     * `GET /gifs/search.json?q=&pos=`: one page of provider results. `pos` is the provider's own
     * cursor from the previous page's `next`; `0` is the first page.
     */
    @GET("gifs/search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("pos") position: String?,
    ): GifSearchResponseDto
}

/** `{tags:[{name, searchterm, image}]}`: a category is a search term, not a GIF. */
@Serializable
data class GifCategoriesResponseDto(
    val tags: List<GifCategoryDto> = emptyList(),
)

@Serializable
data class GifCategoryDto(
    val name: String? = null,
    val searchterm: String? = null,
    val image: String? = null,
)

/** `{results:[{title, media_formats:{webp/gif:{url,dims:[w,h]}}}], next}`. */
@Serializable
data class GifSearchResponseDto(
    val results: List<GifResultDto> = emptyList(),
    /**
     * The provider's next cursor. Tenor/Klipy answer a string; a numeric or object cursor is read
     * defensively rather than failing the whole page. A blank value (or `"0"`) means no next page.
     */
    val next: JsonElement? = null,
)

@Serializable
data class GifResultDto(
    val title: String? = null,
    @SerialName("media_formats")
    val mediaFormats: Map<String, GifMediaFormatDto> = emptyMap(),
)

@Serializable
data class GifMediaFormatDto(
    val url: String? = null,
    val dims: List<Int>? = null,
)

internal fun GifSearchResponseDto.nextPosition(): String? {
    val element = next ?: return null
    val raw = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject ->
            element["pos"]?.jsonPrimitive?.contentOrNull
                ?: element["next"]?.jsonPrimitive?.contentOrNull
        else -> null
    }
    return raw?.trim()?.takeIf { it.isNotEmpty() && it != "0" }
}
