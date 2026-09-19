package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.UserConfig
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme

/**
 * The block editor as the user sees it: [RichEditorSheet] with a reply draft in it, rendered in
 * the app's sheet idiom. The document is what the composer's own markdown bridge writes, so the
 * picture is also the proof that the two surfaces agree on the shape of a draft.
 */
internal class RichEditorSheetScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun theEditorRendersTheComposersDraft() {
        paparazziRule.snapshot { context ->
            val sheet = RichEditorSheet(
                context,
                themeProvider(),
                UserConfig.selectedAccount,
                onSend = {},
                onDraft = {},
            )
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
