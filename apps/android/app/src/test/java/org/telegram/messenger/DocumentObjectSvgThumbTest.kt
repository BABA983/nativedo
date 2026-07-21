package org.telegram.messenger

import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DocumentObjectSvgThumbTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun photoPathSizeProducesDrawableSvgThumb() {
        val size = TLRPC.TL_photoPathSize().apply {
            svgPath = SvgHelper.doPath("M0,0 L512,0 L512,512 L0,512 Z")
        }

        val drawable = DocumentObject.getSvgThumb(
            arrayListOf<TLRPC.PhotoSize>(size),
            Theme.key_windowBackgroundWhiteGrayIcon,
            0.2f,
        )

        assertThat(drawable).isNotNull()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        drawable!!.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        assertThat(bitmap.width).isEqualTo(32)
    }
}
