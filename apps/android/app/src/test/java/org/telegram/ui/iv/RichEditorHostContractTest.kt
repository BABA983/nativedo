package org.telegram.ui.iv

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.ui.RichMessageMarkdown
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme

/**
 * The editor's host page - [RichEditorHostLayout], the pin's `ChatAttachAlertRichLayout` with the
 * chat glue dropped - which is what our sheet mounts. The page owns the editor's own behaviour and
 * leaves four things to its delegate; these tests pin the contract the `:app` sheet relies on: an
 * untouched page writes nothing, a loaded document reads back (and reads back *stably*), when the
 * page may send, and that the toolbar's own buttons reach the delegate.
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
        var attaches = 0
        var emoji = 0
        var sends = 0

        override fun onBackPressed() { back++ }
        override fun onAttachRequested() { attaches++ }
        override fun onEmojiRequested() { emoji++ }
        override fun onSendPressed() { sends++ }
    }

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun page(session: Session): RichEditorHostLayout =
        RichEditorHostLayout(RuntimeEnvironment.getApplication(), 0, provider).apply { setDelegate(session) }

    private fun on(page: RichEditorHostLayout, markdown: String) {
        page.loadRichMessage(RichMessageMarkdown.parse(markdown))
    }

    private fun markdown(page: RichEditorHostLayout): String =
        RichMessageMarkdown.blocksToMarkdown(page.collectBlocks())

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
        // A site image - a photo block that carries only a URL - is not a document the page can send:
        // upstream keeps media whose TLRPC object is ready, and remote media reaches the editor
        // through the upload bridge or an import step first (`docs/render-model-alignment.md`).
        assertThat(written).doesNotContain("![a caption]")

        // and the page is a fixed point: re-opening what it wrote writes the same again
        val again = page(Session())
        on(again, written)
        assertThat(markdown(again)).isEqualTo(written)
    }

    @Test
    fun theToolbarReachesTheDelegate() {
        val session = Session()
        val page = page(session)

        page.getToolbar().getAddButton().performClick()
        assertThat(session.attaches).isEqualTo(1)

        assertThat(page.onBackPressed()).isTrue()
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

    @Test
    fun undoAndRedoFollowThePage() {
        val page = page(Session())
        on(page, "# Title\n\nbody")

        assertThat(page.getEditorListView().canUndo()).isFalse()
        assertThat(page.getEditorListView().canRedo()).isFalse()
        page.getEditorListView().undo()
        page.getEditorListView().redo()
        assertThat(markdown(page)).contains("# Title")
    }
}
