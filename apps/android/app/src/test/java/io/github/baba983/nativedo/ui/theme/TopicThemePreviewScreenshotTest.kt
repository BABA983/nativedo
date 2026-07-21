package io.github.baba983.nativedo.ui.theme

import android.view.ViewGroup
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.SharedConfig

internal class TopicThemePreviewScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun topicDetailPreviewRendersHeaderAndPosts() {
        snapshotAtFontSize(16)
    }

    @Test
    fun topicDetailPreviewFitsMinimumFontSize() {
        snapshotAtFontSize(12)
    }

    @Test
    fun topicDetailPreviewFitsMaximumFontSize() {
        snapshotAtFontSize(30)
    }

    private fun snapshotAtFontSize(fontSize: Int) {
        val original = SharedConfig.fontSize
        SharedConfig.fontSize = fontSize
        try {
        paparazziRule.snapshot { context ->
            TopicThemePreviewContent(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(640f),
                )
            }
        }
        } finally {
            SharedConfig.fontSize = original
        }
    }
}
