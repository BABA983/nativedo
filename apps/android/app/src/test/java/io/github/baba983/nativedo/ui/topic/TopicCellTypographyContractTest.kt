package io.github.baba983.nativedo.ui.topic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
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
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme
import java.util.ArrayDeque

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

    @Test
    fun telegramMediumTypefaceAsset_isPackagedAndLoadable() {
        val application = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(application)

        application.assets.open(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM).use { input ->
            assertThat(input.available()).isGreaterThan(0)
        }
        assertThat(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)).isNotNull()
    }

    @Test
    fun pinnedTimeBubble_usesDialogCellRightEdgeGeometry() {
        val pin = CapturingDrawable()
        Theme.dialogs_pinnedDrawable2 = pin
        val cell = TopicCell(RuntimeEnvironment.getApplication())
        cell.setTopic(
            TopicSummary(
                id = 2,
                title = "Pinned topic",
                authorUsername = "author",
                lastPosterUsername = "poster",
                lastPostedAt = "2020-01-01T00:00:00Z",
                isPinned = true,
            ),
            "https://linux.do",
        )

        val width = 1080
        cell.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        cell.layout(0, 0, width, cell.measuredHeight)
        val bitmap = Bitmap.createBitmap(width, cell.measuredHeight, Bitmap.Config.ARGB_8888)
        cell.draw(TrackingCanvas(bitmap))

        val timeLeft = intField(cell, "timeLeft")
        val timeWidth = intField(cell, "timeWidth")
        assertThat(timeLeft).isEqualTo(width - 15 - timeWidth - 24)
        assertThat(pin.canvasTranslateX + pin.bounds.left).isEqualTo((timeLeft + 4).toFloat())
    }

    private fun countLayout(cell: TopicCell): StaticLayout {
        return TopicCell::class.java.getDeclaredField("countLayout").run {
            isAccessible = true
            get(cell) as StaticLayout
        }
    }

    private fun intField(cell: TopicCell, name: String): Int {
        return TopicCell::class.java.getDeclaredField(name).run {
            isAccessible = true
            getInt(cell)
        }
    }

    private class CapturingDrawable : Drawable() {
        var canvasTranslateX = Float.NaN

        override fun draw(canvas: Canvas) {
            canvasTranslateX = (canvas as TrackingCanvas).translateX
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSPARENT

        override fun getIntrinsicWidth(): Int = 12

        override fun getIntrinsicHeight(): Int = 12
    }

    private class TrackingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        private val translateStack = ArrayDeque<Float>()
        var translateX = 0f
            private set

        override fun save(): Int {
            translateStack.addLast(translateX)
            return super.save()
        }

        override fun restore() {
            super.restore()
            translateX = translateStack.removeLast()
        }

        override fun translate(dx: Float, dy: Float) {
            super.translate(dx, dy)
            translateX += dx
        }
    }
}
