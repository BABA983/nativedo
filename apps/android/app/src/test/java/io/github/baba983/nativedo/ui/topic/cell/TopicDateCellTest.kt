package io.github.baba983.nativedo.ui.topic.cell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.content.Context
import android.view.View
import com.google.common.truth.Truth.assertThat
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.Theme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TopicDateCellTest {
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        AndroidUtilities.density = 1f
        LocaleController.init(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun customDateUsesTelegramFormatterAndMeasurement() {
        val dateSeconds = System.currentTimeMillis() / 1000L - 24 * 60 * 60
        val expected = SimpleDateFormat("MMMM d", Locale.US).format(dateSeconds * 1000L)
        val cell = TopicDateCell(RuntimeEnvironment.getApplication(), false)

        cell.setCustomDate(dateSeconds)
        cell.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        assertThat(cell.measuredWidth).isEqualTo(400)
        assertThat(cell.measuredHeight).isGreaterThan(14)
        assertThat(cell.contentDescription.toString()).isEqualTo(expected)
    }

    @Test
    fun oldDateUsesTelegramFullDatePattern() {
        val dateSeconds = System.currentTimeMillis() / 1000L - 400L * 24 * 60 * 60
        val expected = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(dateSeconds * 1000L)

        assertThat(LocaleController.formatDateChat(dateSeconds)).isEqualTo(expected)
    }

    @Test
    fun tabletWidthKeepsSameTelegramDateRowHeight() {
        val dateSeconds = System.currentTimeMillis() / 1000L - 24 * 60 * 60
        val phone = TopicDateCell(RuntimeEnvironment.getApplication(), false)
        val tablet = TopicDateCell(RuntimeEnvironment.getApplication(), false)
        phone.setCustomDate(dateSeconds)
        tablet.setCustomDate(dateSeconds)

        phone.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        tablet.measure(
            View.MeasureSpec.makeMeasureSpec(2560, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        assertThat(tablet.measuredWidth).isEqualTo(2560)
        assertThat(tablet.measuredHeight).isEqualTo(phone.measuredHeight)
    }

    @Test
    fun wallpaperLifecycleInitializesAndDrawsServiceChipBackground() {
        val context = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(context)
        AndroidUtilities.init(context)
        AndroidUtilities.density = 1f
        Theme.createChatResources(context, false)
        Theme.reloadWallpaper(false)

        val backgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground)
        assertThat(Color.alpha(backgroundPaint.color)).isGreaterThan(0)

        val cell = TestableTopicDateCell(context)
        cell.setCustomDate(System.currentTimeMillis() / 1000L)
        cell.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        cell.layout(0, 0, cell.measuredWidth, cell.measuredHeight)
        val bitmap = Bitmap.createBitmap(cell.measuredWidth, cell.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = BackgroundRecordingCanvas(bitmap)
        cell.drawContents(canvas)

        assertThat(canvas.pathDrawCount).isGreaterThan(0)
        assertThat(canvas.drewVisiblePath).isTrue()
    }

    private class TestableTopicDateCell(context: Context) : TopicDateCell(context, false) {
        fun drawContents(canvas: Canvas) = super.onDraw(canvas)
    }

    private class BackgroundRecordingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        var pathDrawCount = 0
        var drewVisiblePath = false

        override fun drawPath(path: Path, paint: Paint) {
            pathDrawCount += 1
            drewVisiblePath = drewVisiblePath || Color.alpha(paint.color) > 0 || paint.shader != null
            super.drawPath(path, paint)
        }
    }
}
