package io.github.baba983.nativedo.core.ui

import android.text.TextPaint
import android.text.style.DynamicDrawableSpan
import androidx.annotation.ColorInt
import org.telegram.messenger.Emoji

internal fun buildPostBodyInlineSpans(
    html: String,
    baseUrl: String,
    textSizePx: Float,
    @ColorInt accentColor: Int,
    @ColorInt chipBackgroundArgb: Int,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    svgSpriteMarkup: String = "",
): AppliedInlineImages {
    val cooked = cookInlineHtml(html)
    return applyInlineImagePlaceholders(
        text = buildPostBodyText(
            cooked = cooked,
            baseUrl = baseUrl,
            textSizePx = textSizePx,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            onOpenLink = onOpenLink,
            accentColor = accentColor,
            chipBackgroundColor = chipBackgroundArgb,
        ),
        inlineImages = cooked.inlineImages,
        inlineIcons = cooked.inlineIcons,
        inlineHashtagChips = cooked.inlineHashtagChips,
        baseUrl = baseUrl,
        onOpenLink = onOpenLink,
        textSizePx = textSizePx,
        accentColor = accentColor,
        chipBackgroundArgb = chipBackgroundArgb,
        svgSpriteMarkup = svgSpriteMarkup,
    )
}
