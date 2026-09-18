package org.telegram.ui.Components

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MediaDataController
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class EditTextEffectsContractTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        AndroidUtilities.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun watcherSuppressionDetachesAndDispatchesRegisteredWatchersOnce() {
        val editText = EditTextBoldCursor(RuntimeEnvironment.getApplication())
        val changes = AtomicInteger()
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                changes.incrementAndGet()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        editText.setText("before")
        val beforeSuppressed = changes.get()
        editText.setTextWatchersSuppressed(true, false)
        editText.setText("suppressed")
        assertThat(changes.get()).isEqualTo(beforeSuppressed)

        editText.setTextWatchersSuppressed(false, true)
        assertThat(changes.get()).isEqualTo(beforeSuppressed + 1)
    }

    @Test
    fun textToUseStripsQuoteNewlineHack() {
        val editText = EditTextBoldCursor(RuntimeEnvironment.getApplication())
        val text = SpannableString("quoted\n")
        text.setSpan(QuoteSpan.QuoteButtonNewLineSpan(), 6, 7, 0)
        editText.setText(text)

        assertThat(editText.textToUse.toString()).isEqualTo("quoted")
    }

    private fun styleRuns(editable: SpannableStringBuilder): List<Triple<Int, Int, Int>> =
        editable.getSpans(0, editable.length, TextStyleSpan::class.java)
            .map { Triple(editable.getSpanStart(it), editable.getSpanEnd(it), it.textStyleRun.flags) }
            .sortedBy { it.first }

    private fun styleSpan(flag: Int): TextStyleSpan {
        val run = TextStyleSpan.TextStyleRun()
        run.flags = flag
        return TextStyleSpan(run)
    }

    @Test
    fun applyingAStyleOverAnExistingRunSplitsTheRun() {
        val editable = SpannableStringBuilder("hello world")
        editable.setSpan(styleSpan(TextStyleSpan.FLAG_STYLE_ITALIC), 0, editable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        MediaDataController.addStyleToText(styleSpan(TextStyleSpan.FLAG_STYLE_BOLD), 6, 11, editable, false)

        val runs = styleRuns(editable)
        assertThat(runs).containsExactly(
            Triple(0, 6, TextStyleSpan.FLAG_STYLE_ITALIC),
            Triple(6, 11, TextStyleSpan.FLAG_STYLE_BOLD),
        ).inOrder()
    }

    @Test
    fun clearingAStyleLeavesTheTailsAndNoSpanOverTheRange() {
        val editable = SpannableStringBuilder("hello world")
        editable.setSpan(styleSpan(TextStyleSpan.FLAG_STYLE_BOLD), 0, editable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        MediaDataController.addStyleToText(null, 3, 8, editable, false)

        val runs = styleRuns(editable)
        // The cleared range loses its run; the tails keep theirs.
        assertThat(runs).containsExactly(
            Triple(0, 3, TextStyleSpan.FLAG_STYLE_BOLD),
            Triple(8, 11, TextStyleSpan.FLAG_STYLE_BOLD),
        ).inOrder()
    }
}
