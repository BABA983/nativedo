package io.github.baba983.nativedo.core.web

import kotlinx.serialization.json.Json

/**
 * Decodes a JavaScript `evaluateJavascript` result string.
 *
 * `WebView.evaluateJavascript` wraps string results in JSON quotes and
 * returns the literal `"null"` for null values. This helper unwraps them
 * back to a plain Kotlin string.
 */
internal fun decodeJavascriptString(rawValue: String?): String {
    val normalized = rawValue.orEmpty()
    if (normalized.isBlank() || normalized == "null") {
        return ""
    }
    return runCatching {
        Json.decodeFromString<String>(normalized)
    }.getOrDefault("")
}
