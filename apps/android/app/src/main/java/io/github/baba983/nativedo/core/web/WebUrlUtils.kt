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
