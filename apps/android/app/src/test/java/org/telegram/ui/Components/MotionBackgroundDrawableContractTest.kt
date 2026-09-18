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
import org.telegram.messenger.Utilities

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

    /**
     * The pattern tile ReplyMessageLine puts behind a rail comes from the Java stand-in
     * for the pinned `Utilities.drawReplyLinePattern` (Utilities is deliberately no-JNI).
     */
    @Test
    fun replyLinePatternWriterFillsTheSkewedTile() {
        val background = 0xff102030.toInt()
        val bar = 0xff405060.toInt()
        val tile = Bitmap.createBitmap(3, 24, Bitmap.Config.ARGB_8888)

        assertThat(Utilities.drawReplyLinePattern(tile, background, bar, 0, 6, false)).isTrue()

        // x = 0 reads the reference column: 24 - 6 = 18 rows of background, then the bar.
        assertThat(tile.getPixel(0, 16)).isEqualTo(background)
        assertThat(tile.getPixel(0, 18)).isEqualTo(bar)
        // The boundary row holds the per-byte average of the two colors.
        assertThat(tile.getPixel(0, 17)).isEqualTo(0xff283848.toInt())
        // The 45° skew: column x reads ref[(y + x) % period], so (2, 16) is row 18.
        assertThat(tile.getPixel(2, 16)).isEqualTo(bar)
    }

    @Test
    fun replyLinePatternWriterRejectsIllShapedTiles() {
        val background = 0xff102030.toInt()
        val bar = 0xff405060.toInt()
        val wide = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        assertThat(Utilities.drawReplyLinePattern(wide, background, bar, 0, 2, false)).isFalse()
        val tall = Bitmap.createBitmap(3, 24, Bitmap.Config.ARGB_8888)
        assertThat(Utilities.drawReplyLinePattern(tall, background, bar, 0, 0, false)).isFalse()
    }
}
