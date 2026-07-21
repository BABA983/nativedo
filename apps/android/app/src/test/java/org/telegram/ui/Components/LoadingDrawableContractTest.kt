package org.telegram.ui.Components

import android.graphics.Bitmap
import android.graphics.Canvas
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
class LoadingDrawableContractTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun gradientAndRoundedPathDrawBeforeDisappear() {
        val drawable = LoadingDrawable()
        drawable.setColors(0xff1e7755.toInt(), 0xff64b5ef.toInt())
        drawable.setRadiiDp(6f)
        drawable.setBounds(0, 0, 120, 40)
        drawable.setAlpha(200)

        val bitmap = Bitmap.createBitmap(120, 40, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))

        assertThat(drawable.isDisappeared).isFalse()
        assertThat(drawable.color1).isEqualTo(0xff1e7755.toInt())
        assertThat(drawable.getPaintAlpha()).isEqualTo(200)
    }

    @Test
    fun disappearStartsTimedTransition() {
        val drawable = LoadingDrawable()

        drawable.disappear()

        assertThat(drawable.isDisappearing).isTrue()
        assertThat(drawable.timeToDisappear()).isGreaterThan(0L)
    }
}
