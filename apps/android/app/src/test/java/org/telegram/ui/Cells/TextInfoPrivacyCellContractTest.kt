package org.telegram.ui.Cells

import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.View.MeasureSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.LinkSpanDrawable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TextInfoPrivacyCellContractTest {
    private lateinit var cell: TextInfoPrivacyCell

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        cell = TextInfoPrivacyCell(RuntimeEnvironment.getApplication())
    }

    @Test
    fun keepsTelegramLinksTextViewAndMovementContract() {
        val textView = cell.textView

        assertThat(textView).isInstanceOf(LinkSpanDrawable.LinksTextView::class.java)
        assertThat(textView.movementMethod).isInstanceOf(LinkMovementMethod::class.java)
    }

    @Test
    fun preservesClickableSpansForInformationalRows() {
        val text = SpannableString("Read more")
        text.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) = Unit
        }, 0, text.length, 0)

        cell.setText(text)
        val width = AndroidUtilities.dp(320f)
        cell.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        cell.layout(0, 0, width, cell.measuredHeight)

        assertThat(cell.textView.hasLinks()).isTrue()
    }
}
