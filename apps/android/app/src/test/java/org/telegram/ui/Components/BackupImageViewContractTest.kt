package org.telegram.ui.Components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
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
class BackupImageViewContractTest {
    private lateinit var view: BackupImageView

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        view = BackupImageView(RuntimeEnvironment.getApplication())
        view.setSize(240, 64)
    }

    @Test
    fun blurredTextUsesPinnedTextDrawingPath() {
        view.setBlurredText("Private")

        assertThat(view.blurText).isNotNull()
        assertThat(view.blurText.currentWidth).isGreaterThan(0f)

        val bitmap = Bitmap.createBitmap(240, 64, Bitmap.Config.ARGB_8888)
        view.drawBlurredText(Canvas(bitmap), 1f)

        val clipPathField = BackupImageView::class.java.getDeclaredField("blurTextClipPath")
        clipPathField.isAccessible = true
        assertThat((clipPathField.get(view) as Path).isEmpty).isFalse()
    }

    @Test
    fun emptyBlurredTextClearsPreviousText() {
        view.setBlurredText("Private")
        view.setBlurredText("")

        assertThat(view.blurText).isNull()
    }
}
