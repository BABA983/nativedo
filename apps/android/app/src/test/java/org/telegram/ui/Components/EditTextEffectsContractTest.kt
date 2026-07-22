package org.telegram.ui.Components

import android.text.SpannableString
import android.text.TextWatcher
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
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
}
