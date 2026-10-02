package io.github.baba983.nativedo.core.web

import android.net.Uri
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

/**
 * Topic id of a same-origin `/t/<id>` URL, resolved against [baseUrl].
 */
internal fun parseSameOriginTopicId(value: String, baseUrl: String): Long? {
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    val resolved = base.resolve(value.trim()) ?: value.toHttpUrlOrNull() ?: return null
    if (!resolved.host.equals(base.host, ignoreCase = true)) return null
    val segments = resolved.pathSegments.filter(String::isNotBlank)
    if (segments.firstOrNull() != "t") return null
    return segments.drop(1).firstNotNullOfOrNull(String::toLongOrNull)
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
        val topicId = parseSameOriginTopicId(value, baseUrl) ?: return null
        return DiscourseTopicLink(topicId = topicId, floor = parseSameOriginFloor(value, baseUrl))
    }
}

/**
 * The floor of a same-origin `/t/<slug>/<id>/<floor>` link, or 0 when the link names none. The id
 * itself is the first number after `t`; a floor is the next one.
 */
internal fun parseSameOriginFloor(value: String, baseUrl: String): Int {
    val base = baseUrl.toHttpUrlOrNull() ?: return 0
    val resolved = base.resolve(value.trim()) ?: return 0
    if (!resolved.host.equals(base.host, ignoreCase = true)) return 0
    val segments = resolved.pathSegments.filter(String::isNotBlank)
    if (segments.firstOrNull() != "t") return 0
    val numbers = segments.drop(1).mapNotNull(String::toIntOrNull)
    return if (numbers.size >= 2) numbers.last() else 0
}
