package io.github.baba983.nativedo.core.web

import io.github.baba983.nativedo.core.storage.SvgSpriteStore
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val DISCOURSE_ASSETS_ICONS_SCRIPT = """
    (function() {
      var icons = document.querySelector('body > discourse-assets > discourse-assets-icons, discourse-assets-icons, #discourse-assets-icons');
      return icons ? icons.innerHTML : '';
    })();
"""

internal fun decodeJavascriptString(rawValue: String?): String {
    val normalized = rawValue.orEmpty()
    if (normalized.isBlank() || normalized == "null") {
        return ""
    }
    return runCatching {
        Json.decodeFromString<String>(normalized)
    }.getOrDefault("")
}

internal fun isDiscourseSameOrigin(currentUrl: String, baseUrl: String): Boolean {
    val current = currentUrl.toHttpUrlOrNull() ?: return false
    val base = baseUrl.toHttpUrlOrNull() ?: return false
    return current.scheme == base.scheme &&
        current.host == base.host &&
        current.port == base.port
}

internal fun looksLikeDiscourseSvgSpriteMarkup(markup: String): Boolean {
    val trimmed = markup.trim()
    return trimmed.isNotBlank() &&
        trimmed.contains("<symbol", ignoreCase = true) &&
        trimmed.contains("viewBox", ignoreCase = true)
}

internal fun saveDiscourseSvgSpriteMarkup(store: SvgSpriteStore, rawMarkup: String): Boolean {
    val markup = rawMarkup.trim()
    if (!looksLikeDiscourseSvgSpriteMarkup(markup)) {
        svgSpriteDebugLog("save: rejected ${summarizeDiscourseSvgSpriteMarkup(markup)}")
        return false
    }
    store.save(markup)
    svgSpriteDebugLog("save: ok ${summarizeDiscourseSvgSpriteMarkup(markup)}")
    return true
}