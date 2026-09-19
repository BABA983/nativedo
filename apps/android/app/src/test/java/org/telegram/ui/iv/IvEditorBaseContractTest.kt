package org.telegram.ui.iv

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.Theme

/**
 * The editor base layer: the pin's [RichEditText] (the editing view of a rich block, with its own
 * style run API) plus the one attach-alert entry point this tree had to stub to compile it,
 * [ChatAttachAlertRichLayout.showEditLatexSheet]. The stub is a wire-out, so its contract is
 * "nothing happens, nobody is called back" - pinned here so a half-wired sheet cannot slip in
 * unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IvEditorBaseContractTest {

    private val provider = object : Theme.ResourcesProvider {
        override fun getColor(key: Int): Int = 0xff000000.toInt()
        override fun getColorOrNull(key: Int): Int = 0xff000000.toInt()
    }

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun addingAndRemovingAStyleMovesTheRunQuery() {
        val edit = RichEditText(RuntimeEnvironment.getApplication(), provider)
        edit.setTextSilently("bold")

        assertThat(edit.getCurrentStyle(0, 4) and RichTextStyle.BOLD).isEqualTo(0)

        edit.addStyle(RichTextStyle.BOLD, 0, 4)
        assertThat(edit.getCurrentStyle(0, 4) and RichTextStyle.BOLD).isNotEqualTo(0)

        edit.removeStyle(RichTextStyle.BOLD, 0, 4)
        assertThat(edit.getCurrentStyle(0, 4) and RichTextStyle.BOLD).isEqualTo(0)
    }

    @Test
    fun aStyleOnlyCountsWhenItCoversTheWholeRun() {
        val edit = RichEditText(RuntimeEnvironment.getApplication(), provider)
        edit.setTextSilently("plain")

        edit.addStyle(RichTextStyle.ITALIC, 0, 3)

        // The run [0, 5) is not fully italic, the run [0, 3) is.
        assertThat(edit.getCurrentStyle(0, 5) and RichTextStyle.ITALIC).isEqualTo(0)
        assertThat(edit.getCurrentStyle(0, 3) and RichTextStyle.ITALIC).isNotEqualTo(0)
    }

    @Test
    fun theLatexSheetWireOutDoesNothing() {
        var calledBack = false
        ChatAttachAlertRichLayout.showEditLatexSheet(
            RuntimeEnvironment.getApplication(),
            "x^2",
            Utilities.Callback<String> { calledBack = true },
            provider,
        )
        assertThat(calledBack).isFalse()
    }
}
