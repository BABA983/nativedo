package org.telegram.messenger

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.wallpaper.WallpaperGiftBitmapDrawable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WallpaperGiftBitmapDrawableContractTest {
    @Test
    fun createPreservesPinnedNullAndPatternSemantics() {
        assertThat(WallpaperGiftBitmapDrawable.create(null, null)).isNull()

        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val plain = WallpaperGiftBitmapDrawable.create(bitmap, emptyList())
        assertThat(plain).isInstanceOf(BitmapDrawable::class.java)
        assertThat(plain).isNotInstanceOf(WallpaperGiftBitmapDrawable::class.java)

        val positions = listOf(null)
        val withPatterns = WallpaperGiftBitmapDrawable.create(bitmap, positions)
        assertThat(withPatterns).isInstanceOf(WallpaperGiftBitmapDrawable::class.java)
        assertThat((withPatterns as WallpaperGiftBitmapDrawable).patternPositions).isSameInstanceAs(positions)
    }
}
