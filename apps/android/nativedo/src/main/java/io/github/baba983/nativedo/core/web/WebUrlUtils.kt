package io.github.baba983.nativedo.core.web

import android.net.Uri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * True when [currentUrl] and [baseUrl] share the same scheme, host, and port.
 */
internal fun isDiscourseSameOrigin(currentUrl: String, baseUrl: String): Boolean {
    val current = currentUrl.toHttpUrlOrNull() ?: return false
    val base = baseUrl.toHttpUrlOrNull() ?: return false
    return current.scheme == base.scheme &&
        current.host == base.host &&
        current.port == base.port
}

/**
 * Returns [value] unchanged when it is a valid absolute http(s) URL.
 */
internal fun normalizeHttpUrl(value: String): String? {
    val uri = Uri.parse(value.trim())
    val scheme = uri.scheme?.lowercase()
    return uri.toString().takeIf {
        value.isNotBlank() && (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }
}

/** A linux.do topic a link points at: the topic's id, and the floor when the link names one. */
data class DiscourseTopicLink(
    val topicId: Long,
    val floor: Int,
)

/**
 * The app's entry point for a linux.do link, the same way the site's own URLs address a topic
 * (`/t/<slug>/<id>` and `/t/<slug>/<id>/<floor>`). A link that is not a same-origin topic is not a
 * link this client can open, so it answers null rather than guessing.
 */
object DiscourseLinks {

    @JvmStatic
    fun topicLink(url: String?, baseUrl: String): DiscourseTopicLink? {
        val value = url?.trim().orEmpty()
        if (value.isEmpty()) return null
        return parseSameOriginTopicLink(value, baseUrl)
    }
}

/**
 * A same-origin topic link in Discourse's own URL shape, resolved against [baseUrl]:
 *
 *  * `/t/<id>`
 *  * `/t/<id>/<floor>`
 *  * `/t/<slug>/<id>`
 *  * `/t/<slug>/<id>/<floor>`
 *
 * Only the path is read. Discourse routes a topic by its path, so a query string (`?page=2`) or a
 * fragment (`#post_5`) is not part of the topic and is dropped; a relative link - the shape a post
 * body's own anchors carry - resolves against [baseUrl] first. The link must be same-origin with
 * [baseUrl]: same scheme, host and port, and no userinfo, so a lookalike host, a foreign scheme or
 * a different port is refused rather than followed.
 *
 * The id/floor split mirrors Discourse's route order, where `t/:topic_id/:post_number` is declared
 * before `t/:slug/:topic_id`. A two-segment path whose first segment is numeric is therefore
 * id/floor (`/t/2024/123` is topic 2024 floor 123 - the numeric slug is read as the id), while a
 * three-segment path is always slug/id/floor (`/t/2024/123/5` is topic 123 floor 5). Ids and floors
 * that are zero, negative, non-numeric or too large for their type, and paths with more components
 * than the four shapes allow, are refused.
 */
internal fun parseSameOriginTopicLink(value: String, baseUrl: String): DiscourseTopicLink? {
    val resolved = resolveSameOrigin(value, baseUrl) ?: return null
    val segments = resolved.pathSegments
    if (segments.size < 2 || segments.first() != "t") return null
    val after = segments.drop(1)
    if (after.any(String::isEmpty)) return null
    val id: Long
    val floor: Int
    when (after.size) {
        1 -> {
            id = topicId(after[0]) ?: return null
            floor = 0
        }
        2 -> {
            if (isAsciiDigits(after[0])) {
                id = topicId(after[0]) ?: return null
                floor = floorNumber(after[1]) ?: return null
            } else {
                id = topicId(after[1]) ?: return null
                floor = 0
            }
        }
        3 -> {
            id = topicId(after[1]) ?: return null
            floor = floorNumber(after[2]) ?: return null
        }
        else -> return null
    }
    return DiscourseTopicLink(topicId = id, floor = floor)
}

/**
 * [value] resolved against [baseUrl] when the result is same-origin: same scheme, host and port,
 * and carrying no userinfo. A link that only looks like the site's is not one.
 */
private fun resolveSameOrigin(value: String, baseUrl: String): HttpUrl? {
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    val resolved = base.resolve(value.trim()) ?: value.toHttpUrlOrNull() ?: return null
    if (resolved.scheme != base.scheme) return null
    if (!resolved.host.equals(base.host, ignoreCase = true)) return null
    if (resolved.port != base.port) return null
    if (resolved.username.isNotEmpty() || resolved.password.isNotEmpty()) return null
    return resolved
}

/** A positive topic id, or null when the segment is not an ASCII-decimal number in `Long` range. */
private fun topicId(segment: String): Long? =
    if (isAsciiDigits(segment)) segment.toLongOrNull()?.takeIf { it > 0 } else null

/** A positive floor, or null when the segment is not an ASCII-decimal number in `Int` range. */
private fun floorNumber(segment: String): Int? =
    if (isAsciiDigits(segment)) segment.toIntOrNull()?.takeIf { it > 0 } else null

/** ASCII digits only, so `+1`, `1.0`, `-1` and non-Latin numerals are not numbers here. */
private fun isAsciiDigits(segment: String): Boolean =
    segment.isNotEmpty() && segment.all { it in '0'..'9' }
