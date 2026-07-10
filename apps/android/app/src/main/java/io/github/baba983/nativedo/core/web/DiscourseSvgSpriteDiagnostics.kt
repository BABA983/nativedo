package io.github.baba983.nativedo.core.web

import android.util.Log
import io.github.baba983.nativedo.BuildConfig

internal const val SVG_SPRITE_LOG_TAG = "NativeDoSvgSprite"

internal fun svgSpriteDebugLog(message: String) {
    if (BuildConfig.DEBUG) {
        Log.i(SVG_SPRITE_LOG_TAG, message)
    }
}

internal fun summarizeDiscourseSvgSpriteMarkup(markup: String): String {
    val trimmed = markup.trim()
    if (trimmed.isEmpty()) {
        return "empty"
    }
    val symbolCount = Regex("<symbol\\b", RegexOption.IGNORE_CASE).findAll(trimmed).count()
    val hasNoteIcon = trimmed.contains("id=\"far-pen-to-square\"", ignoreCase = true) ||
        trimmed.contains("id='far-pen-to-square'", ignoreCase = true)
    return "len=${trimmed.length} symbols=$symbolCount noteIcon=$hasNoteIcon valid=${looksLikeDiscourseSvgSpriteMarkup(trimmed)}"
}