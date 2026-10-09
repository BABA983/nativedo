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
 * The custom schemes this client owns for topic routing. They are aliases for one another and for
 * the site's own `/t/...` URL: `nativedo://topic/<id>[/<floor>]` and
 * `linuxdo://topic/<id>[/<floor>]`. They are *not* origins - the authority is a fixed app token,
 * not a host - so they cannot name a forum other than the one the app is configured for.
 */
private const val SCHEME_NATIVEDO = "nativedo"
private const val SCHEME_LINUXDO = "linuxdo"

/** The app-owned authority that carries a bare topic id: `scheme://topic/<id>[/<floor>]`. */
private const val TOPIC_AUTHORITY = "topic"

/**
 * The app's entry point for a topic link, in the shapes the app accepts:
 *
 *  * the site's own URL, same-origin with [baseUrl]: `/t/<slug>/<id>[/<floor>]`;
 *  * the app's own aliases: `nativedo://topic/<id>[/<floor>]` and `linuxdo://topic/<id>[/<floor>]`;
 *  * those same aliases carrying the `/t/<slug>/<id>[/<floor>]` shape, with the configured forum as
 *    the authority so no other origin can be named.
 *
 * Anything else - a foreign host, userinfo, another scheme such as `tg` or `tgb`, a zero, negative,
 * non-numeric or out-of-range id or floor - is not a link this client can open, so it answers null
 * rather than guessing.
 */
object DiscourseLinks {

    @JvmStatic
    fun topicLink(url: String?, baseUrl: String): DiscourseTopicLink? {
        val value = url?.trim().orEmpty()
        if (value.isEmpty()) return null
        return when (Uri.parse(value).scheme?.lowercase()) {
            "http", "https" -> parseSameOriginTopicLink(value, baseUrl)
            SCHEME_NATIVEDO, SCHEME_LINUXDO -> parseCustomSchemeTopicLink(value, baseUrl)
            // `tg`, `tgb`, `tonsite` and every other scheme are not this app's to open.
            else -> null
        }
    }
}

/**
 * A topic link under one of the app-owned custom schemes. The routing is fixed to this app and its
 * configured forum, so there is no origin a caller can smuggle in:
 *
 *  * `scheme://topic/<id>[/<floor>]` - the authority is the app token `topic`, the path is the id
 *    (with an optional floor);
 *  * `scheme://<forum>/t/<slug?>/<id>[/<floor>]` - the authority must be the configured forum host,
 *    and the path is read exactly as the site's own `/t/...` path is.
 *
 * Userinfo, an explicit port, an unknown authority, and ids/floors the site could not have are all
 * refused. Query and fragment are not part of the route and are ignored, as they are for the site's
 * URLs.
 */
private fun parseCustomSchemeTopicLink(value: String, baseUrl: String): DiscourseTopicLink? {
    val uri = Uri.parse(value)
    val scheme = uri.scheme?.lowercase()
    if (scheme != SCHEME_NATIVEDO && scheme != SCHEME_LINUXDO) return null
    if (uri.userInfo != null) return null
    if (uri.port != -1) return null
    val host = uri.host?.lowercase() ?: return null
    val segments = uri.pathSegments
    if (segments.any(String::isEmpty)) return null
    if (host == TOPIC_AUTHORITY) {
        return shortTopicLink(segments)
    }
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    if (host != base.host.lowercase()) return null
    return topicPathLink(segments)
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
    return topicPathLink(resolved.pathSegments)
}

/**
 * The `/t/<slug?>/<id>[/<floor>]` path, read the way Discourse routes it, for both the site's own
 * URLs and the app-owned `/t/...` shape carried by a custom scheme. Only the segments are read, so
 * query, fragment and origin are the caller's concern.
 */
private fun topicPathLink(segments: List<String>): DiscourseTopicLink? {
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

/** The app alias `//topic/<id>[/<floor>]`: the id, and the floor when the path names one. */
private fun shortTopicLink(segments: List<String>): DiscourseTopicLink? {
    val id: Long
    val floor: Int
    when (segments.size) {
        1 -> {
            id = topicId(segments[0]) ?: return null
            floor = 0
        }
        2 -> {
            id = topicId(segments[0]) ?: return null
            floor = floorNumber(segments[1]) ?: return null
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
