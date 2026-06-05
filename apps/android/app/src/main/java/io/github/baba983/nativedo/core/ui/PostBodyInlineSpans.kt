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
): AppliedInlineImages {
    val inlineContent = prepareInlineContent(html)
    return applyInlineImagePlaceholders(
        text = buildPostBodyText(
            html = inlineContent.html,
            baseUrl = baseUrl,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            onOpenLink = onOpenLink,
            accentColor = accentColor,
            chipBackgroundColor = chipBackgroundArgb,
        ),
        inlineImages = inlineContent.inlineImages,
        baseUrl = baseUrl,
        textSizePx = textSizePx,
    )
}
