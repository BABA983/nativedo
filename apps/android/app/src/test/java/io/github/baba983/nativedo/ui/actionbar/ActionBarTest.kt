package io.github.baba983.nativedo.ui.actionbar

import android.content.Context
import android.view.View
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ActionBarTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @Test
    fun actionBar_usesSimpleTextViewForTitleAndSubtitle() {
        val actionBar = ActionBar(context)

        actionBar.setTitle("Title")
        actionBar.setSubtitle("Subtitle")
        actionBar.measure(
            ViewMeasureSpec.exact(1080),
            ViewMeasureSpec.exact(144),
        )
        actionBar.layout(0, 0, actionBar.measuredWidth, actionBar.measuredHeight)

        val children = (0 until actionBar.childCount).map(actionBar::getChildAt)

        assertThat(children.filterIsInstance<SimpleTextView>()).hasSize(2)
        assertThat(children.filterIsInstance<TextView>()).isEmpty()
    }

    @Test
    fun actionBar_hidesSubtitleViewWhenSubtitleCleared() {
        val actionBar = ActionBar(context)

        actionBar.setTitle("Title")
        actionBar.setSubtitle("Subtitle")
        actionBar.setSubtitle(null)

        val subtitleView = (0 until actionBar.childCount)
            .map(actionBar::getChildAt)
            .filterIsInstance<SimpleTextView>()
            .single { it.text.isEmpty() }

        assertThat(subtitleView.visibility).isEqualTo(View.GONE)
    }

    private object ViewMeasureSpec {
        fun exact(size: Int): Int {
            return android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)
        }
    }
}
