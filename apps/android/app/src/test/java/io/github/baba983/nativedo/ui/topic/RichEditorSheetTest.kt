package io.github.baba983.nativedo.ui.topic

import android.app.Activity
import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme

/**
 * The block editor as the reply composer opens it: [RichEditorSheet] is our sheet around the
 * editor page, and the document it edits is the same Discourse markdown the composer's own
 * [TopicReplyComposerMarkdown] bridge writes. These tests pin the two ends the caller relies on -
 * the draft in, the markdown out (on send, or as a draft when the sheet is dismissed) - with the
 * real editor page inside.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RichEditorSheetTest {

    private val provider = object : Theme.ResourcesProvider {
        override fun getColor(key: Int): Int = 0xff000000.toInt()
        override fun getColorOrNull(key: Int): Int? = 0xff000000.toInt()
    }

    private lateinit var activity: Activity

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    private fun sheet(
        onSend: (String) -> Unit = {},
        onDraft: (String) -> Unit = {},
    ): RichEditorSheet = RichEditorSheet(activity, provider, 0, onSend, onDraft)

    @Test
    fun theSheetEditsTheDraftTheComposerHandedIt() {
        val sheet = sheet()

        sheet.loadMarkdown("# Title\n\nA paragraph with **bold**.\n\n- one\n- two")
        val markdown = sheet.markdown()

        assertThat(markdown).contains("# Title")
        assertThat(markdown).contains("**bold**")
        assertThat(markdown).contains("- one")
        assertThat(markdown).contains("- two")
    }

    @Test
    fun anUntouchedSheetWritesNothing() {
        val sheet = sheet()

        assertThat(sheet.markdown()).isEmpty()
        assertThat(sheet.editorHost().hasContent()).isFalse()
    }

    @Test
    fun theSendButtonHandsTheMarkdownToTheCaller() {
        var sent: String? = null
        val sheet = sheet(onSend = { sent = it })

        sheet.loadMarkdown("hello **there**")
        sheet.editorHost().getToolbar().getSendButton().performClick()

        assertThat(sent).isNotNull()
        assertThat(sent!!).contains("**there**")
    }

    @Test
    fun dismissingKeepsTheEditsAsADraft() {
        var draft: String? = null
        val sheet = sheet(onDraft = { draft = it })

        sheet.loadMarkdown("kept **text**")
        sheet.reportDraftIfNotSent()

        assertThat(draft).isNotNull()
        assertThat(draft!!).contains("**text**")
    }

    @Test
    fun dismissingKeepsAMediaDraft() {
        var draft: String? = null
        val sheet = sheet(onDraft = { draft = it })

        sheet.loadMarkdown("![alt text](https://linux.do/uploads/1/x.png)")
        sheet.reportDraftIfNotSent()

        // a dismiss hands the image back to the composer instead of overwriting it with the text
        assertThat(draft).isNotNull()
        assertThat(draft!!).contains("![alt text](https://linux.do/uploads/1/x.png)")
    }

    @Test
    fun theContainerCarriesTheBottomSheetsOwnPadding() {
        val sheet = sheet()

        // `BottomSheet` pads the container it builds itself and a handed-in container has to carry
        // the same: `dp(8) + backgroundPaddingTop - 1` above and `dp(8)` below (`BottomSheet.java`).
        assertThat(sheet.sheetContainer.paddingBottom).isEqualTo(AndroidUtilities.dp(8f))
        val shadowTop = BottomSheet::class.java.getDeclaredField("backgroundPaddingTop")
            .apply { isAccessible = true }
            .getInt(sheet)
        assertThat(sheet.sheetContainer.paddingTop).isEqualTo(AndroidUtilities.dp(8f) + shadowTop - 1)

        // and the page inside keeps the toolbar's own strip below its content: dp(8 + 44 + 8 + 50)
        val host = sheet.editorHost()
        host.measure(
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(560f), View.MeasureSpec.EXACTLY),
        )
        assertThat(host.editorListView.paddingBottom)
            .isEqualTo(AndroidUtilities.dp((8 + 44 + 8 + 50).toFloat()))
    }

    @Test
    fun theScreenshotStatesAreRenderable() {
        val sheet = sheet()

        // the states `RichEditorSheetScreenshotTest` renders: the empty seed, the keyboard's own
        // insets, the format panel and an edit's history - none may throw on the way to the golden
        sheet.loadMarkdown("")
        val host = sheet.editorHost()
        host.setImeInset(AndroidUtilities.dp(320f))
        host.setBottomInsets(AndroidUtilities.dp(320f), 0)
        sheet.loadMarkdown("# A reply\n\nText with **bold**, *italic* and a [link](https://linux.do/t/x).")
        host.getToolbar().showFormattingPanel(true, false)
        val message = TL_iv.RichMessage()
        message.blocks = arrayListOf<TL_iv.PageBlock>(TL_iv.pageBlockDivider())
        host.getEditorListView().addRichMessage(message)

        assertThat(sheet.markdown()).isNotEmpty()
    }

    @Test
    fun anUntouchedSheetReportsNoDraft() {
        var draft: String? = null
        val sheet = sheet(onDraft = { draft = it })

        sheet.reportDraftIfNotSent()

        assertThat(draft).isNull()
    }

    @Test
    fun aSentSheetDoesNotReportADraft() {
        var draft: String? = null
        var sent = false
        val sheet = sheet(onSend = { sent = true }, onDraft = { draft = it })

        sheet.loadMarkdown("sent once")
        sheet.editorHost().getToolbar().getSendButton().performClick()
        sheet.reportDraftIfNotSent()

        assertThat(sent).isTrue()
        assertThat(draft).isNull()
    }
}
