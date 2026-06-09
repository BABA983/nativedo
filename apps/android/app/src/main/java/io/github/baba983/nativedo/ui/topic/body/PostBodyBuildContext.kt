package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Typeface
import android.text.TextPaint
import android.view.View
import androidx.annotation.ColorInt
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

internal class PostBodyBuildContext @JvmOverloads constructor(
    val host: View,
    val baseUrl: String,
    val delegate: PostBodyDelegate,
    val hiddenImageUrlProvider: () -> String? = { null },
    val imagePool: PostBodyImagePool = PostBodyImagePool(host),
    @ColorInt val textColor: Int,
    @ColorInt val secondaryTextColor: Int,
    @ColorInt val accentColor: Int,
    @ColorInt val chipBackgroundColor: Int,
    @ColorInt val dividerColor: Int,
    val selectionGeometryInvalidator: () -> Unit = { host.invalidate() },
) {
    @ColorInt val subtleBackgroundColor: Int = Theme.multAlpha(secondaryTextColor, 0.10f)
    @ColorInt val mediaPlaceholderColor: Int = Theme.multAlpha(secondaryTextColor, 0.12f)
    @ColorInt val pressedOverlayColor: Int = Theme.multAlpha(accentColor, 0.12f)
    @ColorInt val cardStrokeColor: Int = Theme.multAlpha(dividerColor, 0.55f)
    @ColorInt val pollBarColor: Int = Theme.multAlpha(accentColor, 0.16f)
    @ColorInt val spoilerOverlayColor: Int = Theme.multAlpha(textColor, 0.18f)

    val paragraphPaint: TextPaint = textPaint(sizeSp = 16f, color = textColor)
    val quotePaint: TextPaint = textPaint(sizeSp = 15f, color = textColor)
    val headingPaint: TextPaint = textPaint(sizeSp = 20f, color = textColor).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val monoPaint: TextPaint = textPaint(sizeSp = 14f, color = textColor).apply {
        typeface = Typeface.MONOSPACE
    }

    private fun textPaint(sizeSp: Float, @ColorInt color: Int): TextPaint {
        return TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = AndroidUtilities.dp(sizeSp).toFloat()
            linkColor = accentColor
        }
    }
}
