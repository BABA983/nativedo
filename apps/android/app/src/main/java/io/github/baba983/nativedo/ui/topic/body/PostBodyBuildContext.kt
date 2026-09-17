package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Typeface
import android.text.TextPaint
import android.view.View
import androidx.annotation.ColorInt
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.Theme

internal class PostBodyBuildContext @JvmOverloads constructor(
    val host: View,
    val baseUrl: String,
    val delegate: PostBodyDelegate,
    val hiddenImageUrlProvider: () -> String? = { null },
    val imagePool: PostBodyImagePool = PostBodyImagePool(host),
    @ColorInt textColor: Int,
    @ColorInt secondaryTextColor: Int,
    @ColorInt accentColor: Int,
    @ColorInt chipBackgroundColor: Int,
    @ColorInt dividerColor: Int,
    val svgSpriteMarkup: String = "",
    val selectionGeometryInvalidator: () -> Unit = { host.invalidate() },
) {
    @ColorInt var textColor: Int = textColor
        private set
    @ColorInt var secondaryTextColor: Int = secondaryTextColor
        private set
    @ColorInt var accentColor: Int = accentColor
        private set
    @ColorInt var chipBackgroundColor: Int = chipBackgroundColor
        private set
    @ColorInt var dividerColor: Int = dividerColor
        private set

    @ColorInt var subtleBackgroundColor: Int = Theme.multAlpha(secondaryTextColor, 0.10f)
        private set
    @ColorInt var mediaPlaceholderColor: Int = Theme.multAlpha(secondaryTextColor, 0.12f)
        private set
    @ColorInt var cardStrokeColor: Int = Theme.multAlpha(dividerColor, 0.55f)
        private set
    @ColorInt var pollBarColor: Int = Theme.multAlpha(accentColor, 0.16f)
        private set
    @ColorInt var spoilerOverlayColor: Int = Theme.multAlpha(textColor, 0.18f)
        private set

    /** Content colours the 12.10.2 palette defines for article-style blocks. */
    @ColorInt var articleDividerColor: Int = Theme.getColor(Theme.key_chat_inDivider)
        private set
    @ColorInt var tableBackgroundColor: Int = Theme.getColor(Theme.key_chat_inTableBackground)
        private set
    @ColorInt var tableBorderColor: Int = Theme.getColor(Theme.key_chat_inTableBorder)
        private set
    @ColorInt var articleCodeBackgroundColor: Int = Theme.getColor(Theme.key_chat_inArticleCodeBackground)
        private set
    @ColorInt var articleDetailsArrowColor: Int = Theme.getColor(Theme.key_chat_inArticleDetailsArrow)
        private set
    @ColorInt var articleDetailsLineColor: Int = Theme.getColor(Theme.key_chat_inArticleDetailsLine)
        private set

    val paragraphPaint: TextPaint = textPaint(sizeSp = bodyFontSize(), color = textColor)
    val quotePaint: TextPaint = textPaint(sizeSp = (bodyFontSize() - 1f).coerceAtLeast(12f), color = textColor)
    val mediaCaptionPaint: TextPaint = textPaint(sizeSp = (bodyFontSize() - 2f).coerceAtLeast(11f), color = secondaryTextColor)
    val headingPaint: TextPaint = textPaint(sizeSp = bodyFontSize() + 4f, color = textColor).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val monoPaint: TextPaint = textPaint(sizeSp = (bodyFontSize() - 2f).coerceAtLeast(11f), color = textColor).apply {
        typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO)
    }

    /**
     * Re-sample theme keys and push them into shared paints so attached body
     * blocks can recolor without rebuilding the full block tree / image pool.
     */
    fun applyTheme() {
        textColor = Theme.getColor(Theme.key_chat_messageTextIn)
        secondaryTextColor = Theme.getColor(Theme.key_chat_inTimeText)
        accentColor = Theme.getColor(Theme.key_chat_inReplyLine)
        chipBackgroundColor = Theme.multAlpha(accentColor, 0.15f)
        dividerColor = Theme.getColor(Theme.key_divider)
        subtleBackgroundColor = Theme.multAlpha(secondaryTextColor, 0.10f)
        mediaPlaceholderColor = Theme.multAlpha(secondaryTextColor, 0.12f)
        cardStrokeColor = Theme.multAlpha(dividerColor, 0.55f)
        pollBarColor = Theme.multAlpha(accentColor, 0.16f)
        spoilerOverlayColor = Theme.multAlpha(textColor, 0.18f)
        articleDividerColor = Theme.getColor(Theme.key_chat_inDivider)
        tableBackgroundColor = Theme.getColor(Theme.key_chat_inTableBackground)
        tableBorderColor = Theme.getColor(Theme.key_chat_inTableBorder)
        articleCodeBackgroundColor = Theme.getColor(Theme.key_chat_inArticleCodeBackground)
        articleDetailsArrowColor = Theme.getColor(Theme.key_chat_inArticleDetailsArrow)
        articleDetailsLineColor = Theme.getColor(Theme.key_chat_inArticleDetailsLine)
        applyPaint(paragraphPaint, textColor)
        applyPaint(quotePaint, textColor)
        applyPaint(mediaCaptionPaint, secondaryTextColor)
        applyPaint(headingPaint, textColor)
        applyPaint(monoPaint, textColor)
        applyTextSizes()
    }

    private fun applyTextSizes() {
        val bodySize = bodyFontSize()
        paragraphPaint.textSize = AndroidUtilities.dp(bodySize).toFloat()
        quotePaint.textSize = AndroidUtilities.dp((bodySize - 1f).coerceAtLeast(12f)).toFloat()
        mediaCaptionPaint.textSize = AndroidUtilities.dp((bodySize - 2f).coerceAtLeast(11f)).toFloat()
        headingPaint.textSize = AndroidUtilities.dp(bodySize + 4f).toFloat()
        monoPaint.textSize = AndroidUtilities.dp((bodySize - 2f).coerceAtLeast(11f)).toFloat()
    }

    private fun bodyFontSize(): Float = SharedConfig.fontSize.toFloat()

    private fun applyPaint(paint: TextPaint, @ColorInt color: Int) {
        paint.color = color
        paint.linkColor = accentColor
    }

    private fun textPaint(sizeSp: Float, @ColorInt color: Int): TextPaint {
        return TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = AndroidUtilities.dp(sizeSp).toFloat()
            linkColor = accentColor
        }
    }
}
