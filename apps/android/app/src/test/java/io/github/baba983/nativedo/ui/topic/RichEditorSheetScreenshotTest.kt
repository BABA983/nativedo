package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme

/**
 * The block editor as the user sees it: [RichEditorSheet] with a reply draft in it, rendered in
 * the app's sheet idiom. The document is what the composer's own markdown bridge writes, so the
 * picture is also the proof that the two surfaces agree on the shape of a draft. The other states
 * are the ones the page is built around: the empty editor's own seed, the keyboard owning the
 * page's bottom, the format panel over a selection, and the undo row after an edit.
 */
internal class RichEditorSheetScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun theEditorRendersTheComposersDraft() {
        paparazziRule.snapshot { context ->
            val sheet = editorSheet(context)
            sheet.loadMarkdown(
                """
                # A reply

                Text with **bold**, *italic* and a [link](https://linux.do/t/x).

                - first point
                - second point

                > quoted from the topic

                ```kotlin
                fun main() {}
                ```

                | Head | Center |
                | --- | :-: |
                | a | b |
                """.trimIndent(),
            )
            sheetViewport(context, sheet)
        }
    }

    @Test
    fun theEmptyEditorRendersTheEditorsOwnSeed() {
        paparazziRule.snapshot { context ->
            val sheet = editorSheet(context)
            sheet.loadMarkdown("")
            sheetViewport(context, sheet)
        }
    }

    /**
     * The authoring loop's checklist half: `- [x]`/`- [ ]` load as the editor's own checklist rows
     * (its list menu creates the same shape), and the writer puts the markers back verbatim - the
     * round trip is what makes the site cook a task list out of what this sheet sends.
     */
    @Test
    fun theEditorShowsAChecklist() {
        paparazziRule.snapshot { context ->
            val sheet = editorSheet(context)
            sheet.loadMarkdown("# A reply\n\n- [x] done item\n- [ ] todo item\n\nText after the list.")
            sheetViewport(context, sheet)
        }
    }

    @Test
    fun theEditorRidesTheKeyboard() {
        paparazziRule.snapshot { context ->
            val sheet = editorSheet(context)
            sheet.loadMarkdown("# A reply\n\nText with the keyboard up.")
            // the sheet's own seam: the renderer reports what covers the page's bottom edge. The
            // page keeps its standalone top strip and lifts the toolbar over the keyboard.
            val host = sheet.editorHost()
            host.setImeInset(AndroidUtilities.dp(320f))
            host.setBottomInsets(AndroidUtilities.dp(320f), 0)
            sheetViewport(context, sheet)
        }
    }

    @Test
    fun theEditorShowsTheFormatPanel() {
        paparazziRule.snapshot { context ->
            val sheet = editorSheet(context)
            sheet.loadMarkdown("# A reply\n\nText with **bold**, *italic* and a [link](https://linux.do/t/x).")
            // the selection's format panel, which the page shows through the toolbar's own state
            sheet.editorHost().getToolbar().showFormattingPanel(true, false)
            sheetViewport(context, sheet)
        }
    }

    @Test
    fun theEditorLightsUpUndoAfterAnEdit() {
        paparazziRule.snapshot { context ->
            val sheet = editorSheet(context)
            sheet.loadMarkdown("# A reply\n\nText to edit.")
            // an edit: the list records it and reports it, so the toolbar's undo row lights up
            val message = TL_iv.RichMessage()
            message.blocks = arrayListOf<TL_iv.PageBlock>(TL_iv.pageBlockDivider())
            sheet.editorHost().getEditorListView().addRichMessage(message)
            sheetViewport(context, sheet)
        }
    }

    @Test
    fun theEditorShowsTheComposerEmojiPanel() {
        paparazziRule.snapshot { context ->
            val sheet = editorSheet(context)
            sheet.loadMarkdown("# A reply\n\nText with an emoji :smile: in it.")
            // the emoji key's glyph is an RLottie asset, which layoutlib does not paint, so this
            // golden pins the sheet's half of the seam rather than the key itself: the panel takes
            // the bottom edge and the page's toolbar rides above it. The panel view is the
            // composer's (built by the fragment, device-verified there), so it is stood in for here
            // at the same height.
            val panelHeight = AndroidUtilities.dp(260f)
            sheet.onEmojiPanelRequested = { _ -> stubEmojiPanel(context, panelHeight) to panelHeight }
            sheet.editorHost().getToolbar().getEmojiButton().performClick()
            sheetViewport(context, sheet)
        }
    }

    private fun editorSheet(context: Context): RichEditorSheet =
        RichEditorSheet(
            context,
            themeProvider(),
            UserConfig.selectedAccount,
            onSend = {},
            onDraft = {},
        )

    /**
     * The composer's emoji panel, stood in for: the real view belongs to the input island and is
     * device-verified there, so only the sheet's half of the seam is pinned here.
     */
    private fun stubEmojiPanel(context: Context, heightPx: Int): View =
        FrameLayout(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
            addView(
                TextView(context).apply {
                    text = "emoji panel"
                    gravity = Gravity.CENTER
                    setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    heightPx,
                ),
            )
        }

    private fun themeProvider(): Theme.ResourcesProvider = object : Theme.ResourcesProvider {
        override fun getColor(key: Int): Int = Theme.getColor(key)
        override fun getColorOrNull(key: Int): Int? = Theme.getColor(key)
    }

    /**
     * A `BottomSheet`'s content without showing the dialog: its container in a fixed viewport, the
     * way the app's other sheet goldens do it.
     */
    private fun sheetViewport(context: Context, sheet: BottomSheet): View {
        val width = AndroidUtilities.dp(360f)
        val height = AndroidUtilities.dp(560f)
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            addView(
                sheet.sheetContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.BOTTOM,
                ),
            )
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, width, height)
        }
    }
}
