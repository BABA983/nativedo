package org.telegram.ui.Components

import android.graphics.Bitmap
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
class MotionBackgroundDrawableContractTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun wallpaperDrawableInitializesGradientAndPatternPipeline() {
        val drawable = MotionBackgroundDrawable()
        val pattern = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)

        drawable.setColors(0xff102030.toInt(), 0xff304050.toInt(), 0xff506070.toInt(), 0xff708090.toInt())
        drawable.setPatternBitmap(80, pattern)
        drawable.setBounds(0, 0, 120, 80)

        assertThat(drawable.bitmap).isNotNull()
        assertThat(drawable.patternBitmap).isSameInstanceAs(pattern)
        assertThat(MotionBackgroundDrawable.isDark(0xff000000.toInt(), 0xff101010.toInt(), 0, 0)).isTrue()
        assertThat(MotionBackgroundDrawable.isDark(0xffffffff.toInt(), 0xffeeeeee.toInt(), 0, 0)).isFalse()
    }
}
