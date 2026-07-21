package org.telegram.ui.Components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LineProgressViewContractTest {
    private lateinit var view: LineProgressView

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        view = LineProgressView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 100, 6)
    }

    @Test
    fun currentProgressReturnsTargetWhileAnimationCatchesUp() {
        view.setProgress(0f, false)
        view.setProgress(0.65f, true)

        assertThat(view.currentProgress).isEqualTo(0.65f)
    }

    @Test
    fun drawsProgressAndBackgroundAsRoundedBars() {
        view.setBackColor(0xff224466.toInt())
        view.setProgressColor(0xffcc3311.toInt())
        view.setProgress(0.5f, false)

        view.draw(Canvas(Bitmap.createBitmap(100, 6, Bitmap.Config.ARGB_8888)))

        val rectField = LineProgressView::class.java.getDeclaredField("rect")
        rectField.isAccessible = true
        val rect = rectField.get(view) as RectF
        assertThat(rect).isEqualTo(RectF(0f, 0f, 50f, 6f))

        val paintField = LineProgressView::class.java.getDeclaredField("progressPaint")
        paintField.isAccessible = true
        val paint = paintField.get(view) as Paint
        assertThat(paint.strokeCap).isEqualTo(Paint.Cap.ROUND)
        assertThat(paint.color).isEqualTo(0xffcc3311.toInt())
    }

    @Test
    fun completedProgressUsesPinnedFadeDuration() {
        view.setProgress(1f, false)
        val lastUpdateTime = LineProgressView::class.java.getDeclaredField("lastUpdateTime")
        lastUpdateTime.isAccessible = true
        lastUpdateTime.setLong(view, System.currentTimeMillis() - 250L)

        view.draw(Canvas(Bitmap.createBitmap(100, 6, Bitmap.Config.ARGB_8888)))

        val animatedAlpha = LineProgressView::class.java.getDeclaredField("animatedAlphaValue")
        animatedAlpha.isAccessible = true
        assertThat(animatedAlpha.getFloat(view)).isEqualTo(0f)
    }
}
