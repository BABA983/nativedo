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
): AppliedInlineImages = buildPostBodyInlineSpans(
    cooked = cookInlineHtml(html),
    baseUrl = baseUrl,
    textSizePx = textSizePx,
    accentColor = accentColor,
    chipBackgroundArgb = chipBackgroundArgb,
    onTopicClick = onTopicClick,
    onUserClick = onUserClick,
    onOpenLink = onOpenLink,
    svgSpriteMarkup = svgSpriteMarkup,
)

internal fun buildPostBodyInlineSpans(
    cooked: CookedInline,
    baseUrl: String,
    textSizePx: Float,
    @ColorInt accentColor: Int,
    @ColorInt chipBackgroundArgb: Int,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    svgSpriteMarkup: String = "",
): AppliedInlineImages {
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
        inlineCheckboxes = cooked.inlineCheckboxes,
        inlineMath = cooked.inlineMath,
        baseUrl = baseUrl,
        onOpenLink = onOpenLink,
        textSizePx = textSizePx,
        accentColor = accentColor,
        chipBackgroundArgb = chipBackgroundArgb,
        svgSpriteMarkup = svgSpriteMarkup,
    )
}
