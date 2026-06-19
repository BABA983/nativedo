package io.github.baba983.nativedo.core.ui

import androidx.annotation.ColorInt

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
    val inlineContent = prepareInlineContent(html)
    return applyInlineImagePlaceholders(
        text = buildPostBodyText(
            html = inlineContent.html,
            baseUrl = baseUrl,
            inlineSpoilers = inlineContent.inlineSpoilers,
            inlineTextStyles = inlineContent.inlineTextStyles,
            textSizePx = textSizePx,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            onOpenLink = onOpenLink,
            accentColor = accentColor,
            chipBackgroundColor = chipBackgroundArgb,
        ),
        inlineImages = inlineContent.inlineImages,
        inlineIcons = inlineContent.inlineIcons,
        inlineHashtagChips = inlineContent.inlineHashtagChips,
        baseUrl = baseUrl,
        onOpenLink = onOpenLink,
        textSizePx = textSizePx,
        accentColor = accentColor,
        chipBackgroundArgb = chipBackgroundArgb,
        svgSpriteMarkup = svgSpriteMarkup,
    )
}
