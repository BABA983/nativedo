package org.telegram.ui.ActionBar

import android.graphics.BitmapShader
import android.graphics.LinearGradient
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
class MessageDrawableGradientContractTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        val field = Theme::class.java.getDeclaredField("shouldDrawGradientIcons")
        field.isAccessible = true
        field.setBoolean(null, true)
        Theme.MessageDrawable.motionBackground.fill(null)
    }

    @Test
    fun staticGradientExposesShaderAndMatrixAfterSetTop() {
        val drawable = Theme.MessageDrawable(
            Theme.MessageDrawable.TYPE_TEXT,
            true,
            false,
            gradientProvider(animated = false),
        )

        drawable.setTop(24, 320, 640, false, false)

        assertThat(drawable.hasGradient()).isTrue()
        assertThat(drawable.gradientShader).isInstanceOf(LinearGradient::class.java)
        assertThat(drawable.matrix).isNotNull()
    }

    @Test
    fun animatedGradientExposesMotionBackgroundShaderAfterSetTop() {
        val drawable = Theme.MessageDrawable(
            Theme.MessageDrawable.TYPE_TEXT,
            true,
            false,
            gradientProvider(animated = true),
        )

        drawable.setTop(24, 320, 640, false, false)

        assertThat(drawable.hasGradient()).isTrue()
        assertThat(drawable.gradientShader).isInstanceOf(BitmapShader::class.java)
        assertThat(drawable.matrix).isNotNull()
        assertThat(Theme.MessageDrawable.motionBackground[0]).isNotNull()
    }

    private fun gradientProvider(animated: Boolean) = object : Theme.ResourcesProvider {
        override fun getColor(key: Int): Int = when (key) {
            Theme.key_chat_outBubble -> 0xff102030.toInt()
            Theme.key_chat_outBubbleGradient1 -> 0xff304050.toInt()
            Theme.key_chat_outBubbleGradient2 -> 0xff506070.toInt()
            Theme.key_chat_outBubbleGradient3 -> 0xff708090.toInt()
            Theme.key_chat_outBubbleGradientAnimated -> if (animated) 1 else 0
            else -> 0
        }
    }
}
