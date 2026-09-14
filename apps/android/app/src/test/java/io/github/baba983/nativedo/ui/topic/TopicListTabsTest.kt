package io.github.baba983.nativedo.ui.topic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

internal class TopicListTabsTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule()

    @Test
    fun relayoutPreservesThePagerDrivenIndicatorPosition() {
        paparazziRule.verify { context ->
            val strip = BubbleTabStrip(context)
            strip.setTabs(tabs, 0, false)
            layout(strip)
            val start = indicatorCenter(strip)
            strip.setPageProgress(0, 1, 1f)
            val end = indicatorCenter(strip)

            strip.setPageProgress(0, 1, 0.35f)
            val duringSwipe = indicatorCenter(strip)
            strip.requestLayout()
            layout(strip)

            assertThat(duringSwipe).isWithin(2f).of(start + (end - start) * 0.35f)
            assertThat(indicatorCenter(strip)).isWithin(1f).of(duringSwipe)

            strip.setPageProgress(0, 1, 0f)
            assertThat(indicatorCenter(strip)).isWithin(1f).of(start)
        }
    }

    @Test
    fun aTabClickWaitsForThePagerToMoveTheIndicator() {
        paparazziRule.verify { context ->
            val strip = BubbleTabStrip(context)
            var requestedTab = -1
            strip.setTabs(tabs, 0, false)
            strip.setDelegate { id, _ -> requestedTab = id }
            layout(strip)
            val start = indicatorCenter(strip)

            (strip.getChildAt(0) as ViewGroup).getChildAt(1).performClick()

            assertThat(requestedTab).isEqualTo(1)
            assertThat(indicatorCenter(strip)).isWithin(1f).of(start)
        }
    }

    @Test
    fun settledSelectionKeepsItsActiveTextColorAfterThemeUpdates() {
        paparazziRule.verify { context ->
            val strip = BubbleTabStrip(context)
            strip.setTabs(tabs, 1, false)
            layout(strip)
            strip.updateColors()
            val labels = strip.getChildAt(0) as ViewGroup

            assertThat((labels.getChildAt(1) as TextView).currentTextColor)
                .isEqualTo(Theme.getColor(Theme.key_actionBarTabActiveText))
            assertThat((labels.getChildAt(0) as TextView).currentTextColor)
                .isEqualTo(Theme.getColor(Theme.key_actionBarTabUnactiveText))
        }
    }

    private fun layout(strip: BubbleTabStrip) {
        val width = AndroidUtilities.dp(360f)
        val height = AndroidUtilities.dp(50f)
        strip.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        strip.layout(0, 0, width, height)
    }

    private fun indicatorCenter(strip: BubbleTabStrip): Float {
        val bitmap = Bitmap.createBitmap(strip.width, strip.height, Bitmap.Config.ARGB_8888)
        strip.draw(Canvas(bitmap))
        val y = AndroidUtilities.dp(13f)
        val pixels = (0 until bitmap.width).filter { Color.alpha(bitmap.getPixel(it, y)) > 0 }
        assertThat(pixels).isNotEmpty()
        val center = (pixels.first() + pixels.last()) / 2f
        bitmap.recycle()
        return center
    }

    private val tabs = listOf(BubbleTab(0, "最新"), BubbleTab(1, "新帖"), BubbleTab(2, "热门"))
}
