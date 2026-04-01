package io.github.baba983.nativedo.core.ui

import android.graphics.Color.TRANSPARENT
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import androidx.compose.material3.MaterialTheme

@Composable
fun HtmlPostBodyView(
    html: String,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val bodyStyle = MaterialTheme.typography.bodyLarge
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setBackgroundColor(TRANSPARENT)
                setTextIsSelectable(false)
                ellipsize = TextUtils.TruncateAt.END
            }
        },
        update = { textView ->
            textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            textView.setTextColor(colors.textPrimary.toArgb())
            textView.setLinkTextColor(linkColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, bodyStyle.fontSize.value)
            if (bodyStyle.lineHeight.value > 0f && bodyStyle.fontSize.value > 0f) {
                textView.setLineSpacing(0f, bodyStyle.lineHeight.value / bodyStyle.fontSize.value)
            }
        },
    )
}
