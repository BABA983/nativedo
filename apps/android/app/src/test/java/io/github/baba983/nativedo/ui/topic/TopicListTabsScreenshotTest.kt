package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

internal class TopicListTabsScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun latestSelected() {
        paparazziRule.snapshot { context -> tabs(context, progress = 0f) }
    }

    @Test
    fun swipeInProgress() {
        paparazziRule.snapshot { context -> tabs(context, progress = 0.4f) }
    }

    private fun tabs(context: Context, progress: Float): View {
        val strip = BubbleTabStrip(context).apply {
            setTabs(listOf(BubbleTab(0, "最新"), BubbleTab(1, "新帖"), BubbleTab(2, "热门")), 0, false)
            setPageProgress(0, 1, progress)
        }
        return PillTabContainer(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, AndroidUtilities.dp(50f))
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            addView(strip, FrameLayout.LayoutParams(-1, -1))
        }
    }
}
