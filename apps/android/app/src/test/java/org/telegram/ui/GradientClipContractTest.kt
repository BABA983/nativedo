package org.telegram.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class GradientClipContractTest {
    @Test
    fun pinnedDirectionsInitializeGradientAndDestinationOutPaint() {
        val clip = GradientClip()
        val canvas = Canvas(Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888))
        clip.draw(canvas, RectF(0f, 0f, 120f, 80f), GradientClip.RIGHT, 0.5f)

        val paint = clip.getPaint(GradientClip.RIGHT, 0.75f)
        assertThat(paint.color).isEqualTo(0xFFFF0000.toInt())
        assertThat(paint.alpha).isEqualTo(191)
        assertThat(paint.xfermode).isNotNull()
    }

    @Test
    fun alphaZeroDoesNotCreateDirectionPaint() {
        val clip = GradientClip()
        clip.draw(
            Canvas(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)),
            RectF(0f, 0f, 32f, 32f),
            GradientClip.TOP,
            0f,
        )

        val paints = GradientClip::class.java.getDeclaredField("paint")
        paints.isAccessible = true
        val values = paints.get(clip) as Array<*>
        assertThat(values[GradientClip.TOP]).isNull()
    }
}
