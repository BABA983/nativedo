package io.github.baba983.nativedo.core.web

import io.github.baba983.nativedo.core.storage.SvgSpriteStore

internal const val DISCOURSE_ASSETS_ICONS_SCRIPT = """
    (function() {
      var icons = document.querySelector('body > discourse-assets > discourse-assets-icons, discourse-assets-icons, #discourse-assets-icons');
      return icons ? icons.innerHTML : '';
    })();
"""

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