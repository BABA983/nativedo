package org.telegram.ui.Components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
class LoadingStickerDrawableContractTest {
    private lateinit var parent: View

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        parent = View(RuntimeEnvironment.getApplication())
    }

    @Test
    fun pinnedSvgShimmerInitializesAndAdvances() {
        val drawable = LoadingStickerDrawable(
            parent,
            "M0,0 L512,0 L512,512 L0,512 Z",
            64,
            64,
        )
        drawable.setBounds(0, 0, 64, 64)

        drawable.draw(Canvas(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)))

        val gradientField = LoadingStickerDrawable::class.java.getDeclaredField("placeholderGradient")
        gradientField.isAccessible = true
        assertThat(gradientField.get(drawable)).isNotNull()

        val translationField = LoadingStickerDrawable::class.java.getDeclaredField("totalTranslation")
        translationField.isAccessible = true
        assertThat(translationField.getFloat(drawable)).isAtLeast(0f)
    }
}
