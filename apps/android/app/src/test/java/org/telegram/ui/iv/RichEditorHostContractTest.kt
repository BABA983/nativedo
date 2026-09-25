package org.telegram.ui.iv

import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.ui.RichMessageMarkdown
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme

/**
 * The editor's host page - [RichEditorHostLayout], the pin's `ChatAttachAlertRichLayout` with the
 * chat glue dropped - which is what our sheet mounts. The page owns the editor's own behaviour and
 * leaves four things to its delegate; these tests pin the contract the `:app` sheet relies on: an
 * untouched page writes nothing, a loaded document reads back (and reads back *stably*), the page
 * sends only what it can send, and back clears the selection before it leaves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RichEditorHostContractTest {

    private val provider = object : Theme.ResourcesProvider {
        override fun getColor(key: Int): Int = 0xff000000.toInt()
        override fun getColorOrNull(key: Int): Int? = 0xff000000.toInt()
    }

    private class Session : RichEditorHostLayout.Delegate {
        var back = 0
        var sends = 0

        override fun onBackPressed() { back++ }
        override fun onAttachRequested() {}
        override fun onEmojiRequested() {}
        override fun onSendPressed() { sends++ }
    }

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun page(
        session: Session,
        layoutMode: RichEditorHostLayout.LayoutMode = RichEditorHostLayout.LayoutMode.STANDALONE_SHEET,
    ): RichEditorHostLayout =
        RichEditorHostLayout(RuntimeEnvironment.getApplication(), 0, provider, layoutMode)
            .apply { setDelegate(session) }

    private fun on(page: RichEditorHostLayout, markdown: String) {
        page.loadRichMessage(RichMessageMarkdown.parse(markdown))
    }

    private fun markdown(page: RichEditorHostLayout): String =
        RichMessageMarkdown.blocksToMarkdown(page.collectBlocks())

    private fun exactly(px: Int): Int = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)

    private fun measured(page: RichEditorHostLayout, width: Int, height: Int): RichEditorHostLayout {
        page.measure(exactly(width), exactly(height))
        page.layout(0, 0, width, height)
        return page
    }

    private fun toolbarBackButton(page: RichEditorHostLayout): View {
        val field = RichEditorToolbar::class.java.getDeclaredField("backButton")
        field.isAccessible = true
        return field.get(page.getToolbar()) as View
    }

    @Test
    fun anUntouchedPageWritesNothing() {
        val session = Session()
        val page = page(session)

        assertThat(page.hasContent()).isFalse()
        assertThat(page.canSend()).isFalse()
        assertThat(markdown(page)).isEmpty()

        page.getToolbar().getSendButton().performClick()
        assertThat(session.sends).isEqualTo(0)
    }

    @Test
    fun aLoadedDocumentIsWhatThePageHandsBack() {
        val document = """
            # Title

            A paragraph with **bold** and a [link](https://linux.do/t/x).

            - one
                - nested
            - [x] done

            1. first
            2. second

            > quoted line

            [quote="alice"]
            quoted text
            [/quote]

            [details="Summary"]

            inside details

            [/details]

            ```kotlin
            fun main() {}
            ```

            | Head | Center |
            | --- | :-: |
            | a | b |

            ---

            ${'$'}${'$'}x^2${'$'}${'$'}

            ![a caption](https://linux.do/uploads/1.png)

            *a caption*
        """.trimIndent()

        val page = page(Session())
        on(page, document)

        assertThat(page.hasContent()).isTrue()
        assertThat(page.canSend()).isTrue()
        // loading a document is the page's baseline, not an edit of it
        assertThat(page.hasChanges()).isFalse()

        val written = markdown(page)
        assertThat(written).contains("# Title")
        assertThat(written).contains("**bold**")
        assertThat(written).contains("- one")
        assertThat(written).contains("    - nested")
        assertThat(written).contains("- [x] done")
        assertThat(written).contains("1. first")
        assertThat(written).contains("> quoted line")
        assertThat(written).contains("[quote=\"alice\"]")
        assertThat(written).contains("[details=\"Summary\"]")
        assertThat(written).contains("```kotlin")
        assertThat(written).contains("| Head | Center |")
        assertThat(written).contains("${'$'}${'$'}x^2${'$'}${'$'}")
        // A site image - a photo block that carries only a URL - is imported into the editor's own
        // media (the URL is registered with the bridge and a photo fabricated around it), so the
        // page carries it and writes it back.
        assertThat(written).contains("![a caption](https://linux.do/uploads/1.png)")

        // and the page is a fixed point: re-opening what it wrote writes the same again
        val again = page(Session())
        on(again, written)
        assertThat(markdown(again)).isEqualTo(written)
    }

    @Test
    fun importedMediaKeepsItsUrlLabelAndKind() {
        val document = """
            ![a photo](https://linux.do/uploads/1/photo.png)

            [clip.mp4](https://linux.do/uploads/1/clip.mp4)

            [song.mp3](https://linux.do/uploads/1/song.mp3)

            [notes.pdf](https://linux.do/uploads/1/notes.pdf)
        """.trimIndent()

        val page = page(Session())
        on(page, document)
        val written = markdown(page)

        assertThat(written).contains("![a photo](https://linux.do/uploads/1/photo.png)")
        assertThat(written).contains("[clip.mp4](https://linux.do/uploads/1/clip.mp4)")
        assertThat(written).contains("[song.mp3](https://linux.do/uploads/1/song.mp3)")
        assertThat(written).contains("[notes.pdf](https://linux.do/uploads/1/notes.pdf)")

        // the import is stable: the canonical form is a fixed point
        val again = page(Session())
        on(again, written)
        assertThat(markdown(again)).isEqualTo(written)
    }

    @Test
    fun loadingADocumentDoesNotSeedTheEditor() {
        val page = page(Session())

        on(page, "# Title\n\nbody")

        // exactly the document's rows - the page's empty title/paragraph seed is not on top of them
        assertThat(page.getEditorListView().rows.size).isEqualTo(2)
        assertThat(markdown(page)).isEqualTo("# Title\n\nbody")
    }

    @Test
    fun aDocumentWithoutTextIsNotMistakenForAnEmptyOne() {
        val page = page(Session())

        on(page, "---")

        // a divider is content: the editor must keep it, not replace it with the empty seed
        assertThat(page.getEditorListView().rows.size).isEqualTo(1)
        assertThat(markdown(page)).isEqualTo("---")
    }

    @Test
    fun backClearsSelectionBeforeLeavingFromEitherEntryPoint() {
        val session = Session()
        val page = page(session)
        on(page, "selected text")
        measured(page, AndroidUtilities.dp(360f), AndroidUtilities.dp(600f))

        assertThat(page.editorListView.textSelectionHelper.selectAllBlocksRange(0, 0, 0, 8)).isTrue()
        toolbarBackButton(page).performClick()
        assertThat(page.editorListView.isInSelectionMode).isFalse()
        assertThat(session.back).isEqualTo(0)

        page.onBackPressed()
        assertThat(session.back).isEqualTo(1)
    }

    @Test
    fun theSendButtonReportsOnlyASendablePage() {
        val session = Session()
        val page = page(session)

        on(page, "text")
        page.getToolbar().getSendButton().performClick()
        assertThat(session.sends).isEqualTo(1)
    }
}
