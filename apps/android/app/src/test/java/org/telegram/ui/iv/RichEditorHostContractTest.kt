package org.telegram.ui.iv

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
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
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.Theme

/**
 * The editor's host page - [RichEditorHostLayout], the pin's `ChatAttachAlertRichLayout` with the
 * chat glue dropped - which is what our sheet mounts. The page owns the editor's own behaviour and
 * leaves four things to its delegate; these tests pin the contract the `:app` sheet relies on: an
 * untouched page writes nothing, a loaded document reads back (and reads back *stably*), when the
 * page may send, that the toolbar's own buttons reach the delegate, and the standalone host's
 * layout contract (its top padding, its top row, its bottom padding and the strips it keeps out of
 * the selection's way).
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

    private fun down(x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0)

    /** The list's own `pressX` (the pin's touch bookkeeping), which its touch guard gates. */
    private fun listPressX(page: RichEditorHostLayout): Float {
        val field = RichEditorListView::class.java.getDeclaredField("pressX")
        field.isAccessible = true
        return field.getFloat(page.editorListView)
    }

    /** The offset the toolbar applied to its top row (`setTopButtonsOffset` -> `historyButtons`). */
    private fun toolbarTopControlsOffset(page: RichEditorHostLayout): Int {
        val field = RichEditorToolbar::class.java.getDeclaredField("historyButtons")
        field.isAccessible = true
        val buttons = field.get(page.getToolbar()) as android.view.ViewGroup
        return (buttons.layoutParams as FrameLayout.LayoutParams).topMargin
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
    fun anEmptyDocumentOpensAsTheEditorsSeed() {
        val page = page(Session())

        on(page, "")

        assertThat(page.getEditorListView().rows.size).isEqualTo(2)
        assertThat(page.hasContent()).isFalse()
        assertThat(page.hasChanges()).isFalse()
        assertThat(markdown(page)).isEmpty()
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
    fun theBottomInsetsLiftTheToolbar() {
        val page = page(Session())

        page.setBottomInsets(120, 0)

        assertThat(page.getToolbar().getBottomContainer().translationY).isEqualTo(-120f)
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

    @Test
    fun theStandaloneListStartsBelowItsOwnToolbarInEveryBottomState() {
        val page = page(Session())
        val width = AndroidUtilities.dp(360f)
        val height = AndroidUtilities.dp(1000f)
        val topInset = AndroidUtilities.dp(8f)
        val expectedTop = topInset + AndroidUtilities.dp((8 + 44 + 8).toFloat())

        measured(page, width, height)
        assertThat(page.editorListView.paddingTop).isEqualTo(expectedTop)

        page.setImeInset(AndroidUtilities.dp(800f))
        measured(page, width, height)
        assertThat(page.editorListView.paddingTop).isEqualTo(expectedTop)

        page.setImeInset(0)
        page.setBottomInsets(0, AndroidUtilities.dp(200f))
        measured(page, width, height)
        assertThat(page.editorListView.paddingTop).isEqualTo(expectedTop)
    }

    @Test
    fun attachPreviewKeepsThePinsFirstRowFormula() {
        val page = page(Session(), RichEditorHostLayout.LayoutMode.ATTACH_PREVIEW)
        val width = AndroidUtilities.dp(360f)
        val height = AndroidUtilities.dp(1000f)
        val topInset = AndroidUtilities.dp(8f)
        val actionBar = ActionBar.getCurrentActionBarHeight()

        measured(page, width, height)
        assertThat(page.editorListView.paddingTop).isEqualTo(
            height / 5 * 2 - AndroidUtilities.dp(52f) + topInset + actionBar,
        )

        page.setImeInset(AndroidUtilities.dp(320f))
        measured(page, width, height)
        assertThat(page.editorListView.paddingTop).isEqualTo(
            AndroidUtilities.dp(52f) + topInset + actionBar,
        )
    }

    @Test
    fun theTopControlsStartAtTheStandaloneSheetsTopInset() {
        val page = page(Session())
        val topInset = AndroidUtilities.dp(8f)
        val minOffset = topInset

        assertThat(page.toolbarTopControlsMinOffset).isEqualTo(minOffset)

        measured(page, AndroidUtilities.dp(360f), AndroidUtilities.dp(1000f))
        assertThat(toolbarTopControlsOffset(page)).isEqualTo(minOffset)

        // a deeper safe area lifts the floor with it
        page.setTopInset(AndroidUtilities.dp(30f))
        assertThat(page.toolbarTopControlsMinOffset)
            .isEqualTo(AndroidUtilities.dp(30f))
        assertThat(toolbarTopControlsOffset(page)).isEqualTo(AndroidUtilities.dp(30f))
    }

    @Test
    fun theListKeepsThePinsBottomPaddingAndTheToolbarRidesIt() {
        val page = page(Session())
        val width = AndroidUtilities.dp(360f)
        val height = AndroidUtilities.dp(1000f)
        // the pin's `dp(TOOLBAR_BOTTOM_INSET + 50)`: the toolbar's own 8 + 44 + 8 and the list's
        // last row's breathing room
        val belowContent = AndroidUtilities.dp((8 + 44 + 8 + 50).toFloat())

        page.setBottomInsets(AndroidUtilities.dp(48f), 0)
        measured(page, width, height)
        assertThat(page.editorListView.paddingBottom)
            .isEqualTo(AndroidUtilities.dp(48f) + belowContent)
        assertThat(page.getToolbar().getBottomContainer().translationY).isEqualTo(-AndroidUtilities.dp(48f))

        // an emoji panel replaces the navigation bar and adds its own height
        page.setBottomInsets(0, AndroidUtilities.dp(200f))
        measured(page, width, height)
        assertThat(page.editorListView.paddingBottom)
            .isEqualTo(AndroidUtilities.dp(200f) + belowContent)
        assertThat(page.getToolbar().getBottomContainer().translationY).isEqualTo(-AndroidUtilities.dp(200f))
    }

    @Test
    fun theToolbarsOwnStripsAreKeptOutOfThePagesTouchHandling() {
        val page = page(Session())
        val width = AndroidUtilities.dp(360f)
        val height = AndroidUtilities.dp(600f)
        measured(page, width, height)

        val contentX = width / 2f
        assertThat(page.selectionTouchBottomBoundary)
            .isEqualTo(height - AndroidUtilities.dp((8 + 44 + 8).toFloat()))

        // a down inside the bottom panel's own strip never reaches the list's touch bookkeeping
        page.dispatchTouchEvent(down(contentX, (height - AndroidUtilities.dp(10f)).toFloat()))
        assertThat(listPressX(page)).isEqualTo(0f)

        // one in the content area above it does
        page.dispatchTouchEvent(down(contentX, (height / 2).toFloat()))
        assertThat(listPressX(page)).isEqualTo(contentX)

        // and an emoji panel lifts the boundary over its own strip
        page.setBottomInsets(0, AndroidUtilities.dp(200f))
        assertThat(page.selectionTouchBottomBoundary)
            .isEqualTo(height - AndroidUtilities.dp(200f) - AndroidUtilities.dp((8 + 44 + 8).toFloat()))
    }

    @Test
    fun theToolbarsUnwiredKeysAreParked() {
        val page = page(Session())
        val toolbar = page.getToolbar()

        // the emoji key and the AI keys (the toolbar's own, and the format panel's) are disabled
        // rather than silently dead, and they keep the pin's geometry
        val emoji = toolbar.getEmojiButton()
        assertThat(emoji.isEnabled).isFalse()
        assertThat(emoji.isClickable).isFalse()
        assertThat(emoji.alpha).isEqualTo(0.5f)

        val ai = aiButtons(toolbar)
        assertThat(ai).isNotEmpty()
        for (button in ai) {
            // `RichEditor.Button` keeps its own `enabled` flag (the pin's `setEnabled` dims and
            // unclickables it); the plain AI ImageView flips the View's flag too
            assertThat(button.isClickable).isFalse()
            assertThat(button.alpha).isEqualTo(0.5f)
            if (button !is RichEditor.Button) {
                assertThat(button.isEnabled).isFalse()
            }
        }

        // and a tap on them reaches nothing: the page never reports them to its delegate
        val session = Session()
        page.setDelegate(session)
        emoji.performClick()
        for (button in ai) {
            button.performClick()
        }
        assertThat(session.emoji).isEqualTo(0)
    }

    private fun aiButtons(view: View): List<View> {
        val found = mutableListOf<View>()
        fun walk(v: View) {
            if (v is android.widget.ImageView && v.drawable is org.telegram.ui.Components.AiButtonDrawable) {
                found += v
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    walk(v.getChildAt(i))
                }
            }
        }
        walk(view)
        return found
    }
}
