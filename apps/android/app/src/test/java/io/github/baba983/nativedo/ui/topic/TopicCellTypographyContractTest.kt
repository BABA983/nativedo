package io.github.baba983.nativedo.ui.topic

import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TopicCellTypographyContractTest {

    @Before
    fun setUp() {
        AndroidUtilities.density = 1f
        Theme.dialogs_namePaint = arrayOf(TextPaint(), TextPaint())
        Theme.dialogs_messagePaint = arrayOf(TextPaint(), TextPaint())
        Theme.dialogs_timePaint = TextPaint().apply { textSize = 12f }
        Theme.dialogs_countTextPaint2 = TextPaint().apply { textSize = 13f }
        Theme.dialogs_pinnedDrawable2 = null
    }

    @Test
    fun layoutRestoresPinnedDialogCellTypography() {
        Theme.dialogs_namePaint[0].textSize = 1f
        Theme.dialogs_messagePaint[0].textSize = 1f
        val cell = TopicCell(RuntimeEnvironment.getApplication())
        cell.setTopic(
            TopicSummary(
                id = 1,
                title = "Topic title",
                authorUsername = "author",
                lastPosterUsername = "poster",
                replyCount = 12,
            ),
            "https://linux.do",
        )

        cell.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        assertThat(Theme.dialogs_namePaint[0].textSize).isEqualTo(17f)
        assertThat(Theme.dialogs_messagePaint[0].textSize).isEqualTo(16f)
        assertThat(countLayout(cell).paint).isSameInstanceAs(Theme.dialogs_countTextPaint2)
        assertThat(countLayout(cell).paint.textSize).isEqualTo(13f)
    }

    private fun countLayout(cell: TopicCell): StaticLayout {
        return TopicCell::class.java.getDeclaredField("countLayout").run {
            isAccessible = true
            get(cell) as StaticLayout
        }
    }
}
