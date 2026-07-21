package org.telegram.ui.Components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
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
class BackgroundGradientDrawableContractTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun pinnedAngleMappingAndGradientPointsArePreserved() {
        assertThat(BackgroundGradientDrawable.getGradientOrientation(0))
            .isEqualTo(GradientDrawable.Orientation.BOTTOM_TOP)
        assertThat(BackgroundGradientDrawable.getGradientOrientation(45))
            .isEqualTo(GradientDrawable.Orientation.BL_TR)
        assertThat(BackgroundGradientDrawable.getGradientOrientation(135))
            .isEqualTo(GradientDrawable.Orientation.TL_BR)
        assertThat(BackgroundGradientDrawable.getGradientPoints(90, 120, 80))
            .isEqualTo(Rect(0, 40, 120, 40))
    }

    @Test
    fun exactDrawableFallsBackToGradientUntilDitheredBitmapIsReady() {
        val colors = intArrayOf(Color.RED, Color.BLUE)
        val drawable = BackgroundGradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
        drawable.setBounds(0, 0, 120, 80)

        drawable.draw(Canvas(Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)))

        assertThat(drawable.colorsList).isSameInstanceAs(colors)
        drawable.dispose()
    }
}
