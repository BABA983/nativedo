package org.telegram.ui.ActionBar

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.CombinedDrawable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ThemeDrawableInfrastructureContractTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun circleIconFactoryPreservesPinnedGeometryAndLayerColoring() {
        val icon = android.graphics.drawable.ColorDrawable(Color.WHITE)
        val combined = Theme.createCircleDrawableWithIcon(48, icon, 1)

        assertThat(combined).isInstanceOf(CombinedDrawable::class.java)
        assertThat(combined.intrinsicWidth).isEqualTo(48)
        assertThat((combined.background as ShapeDrawable).paint.style)
            .isEqualTo(android.graphics.Paint.Style.STROKE)
        assertThat((combined.background as ShapeDrawable).paint.strokeWidth).isGreaterThan(0f)

        Theme.setCombinedDrawableColor(combined, 0xff102030.toInt(), false)
        Theme.setCombinedDrawableColor(combined, 0xff405060.toInt(), true)

        assertThat(icon.color).isEqualTo(0xff405060.toInt())
        assertThat((combined.background as ShapeDrawable).paint.colorFilter).isNotNull()
    }

    @Test
    fun themeHelperRestoresWallpaperAndGradientSemantics() {
        assertThat(Theme.getWallpaperColor(0)).isEqualTo(0)
        assertThat(Theme.getWallpaperColor(0x00112233)).isEqualTo(0xff112233.toInt())
        assertThat(Theme.createRoundRectGradientDrawable(8, Color.RED, Color.BLUE))
            .isInstanceOf(GradientDrawable::class.java)
    }

    @Test
    fun roundedSelectorKeepsBackgroundAndRippleAsIndependentLayers() {
        val selector = Theme.createRadSelectorDrawable(Color.RED, Color.BLUE, 15, 15) as RippleDrawable

        assertThat((selector.getDrawable(0) as ShapeDrawable).paint.color).isEqualTo(Color.RED)

        assertThat(Theme.setSelectorDrawableColor(selector, Color.GREEN, false)).isTrue()
        Theme.setSelectorDrawableColor(selector, Color.YELLOW, true)

        assertThat((selector.getDrawable(0) as ShapeDrawable).paint.color).isEqualTo(Color.GREEN)
    }

    @Test
    fun whiteBackgroundSelectorUsesProviderBackgroundColor() {
        val background = 0xff102030.toInt()
        val provider = object : Theme.ResourcesProvider {
            override fun getColor(key: Int): Int = when (key) {
                Theme.key_windowBackgroundWhite -> background
                Theme.key_listSelector -> 0x33112233
                else -> Theme.getColor(key)
            }
        }

        val selector = Theme.getSelectorDrawable(true, provider) as RippleDrawable

        assertThat((selector.getDrawable(0) as ColorDrawable).color).isEqualTo(background)
    }
}
